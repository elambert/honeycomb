#!/usr/bin/perl -w
#
# $Id: hcparser_all.sh 10845 2007-05-19 02:31:46Z bberndt $
#
# Copyright © 2008, Sun Microsystems, Inc.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are
# met:
#
#   # Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
#
#   # Redistributions in binary form must reproduce the above copyright
# notice, this list of conditions and the following disclaimer in the
# documentation and/or other materials provided with the distribution.
#
#   # Neither the name of Sun Microsystems, Inc. nor the names of its
# contributors may be used to endorse or promote products derived from
# this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
# IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
# TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
# PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
# OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
# PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
# PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
# LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
# SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.



#
use strict;

sub usage {
   print "to parse and view html pages use from the current directory:\n";
   print "./$0\n";
} 

my $MODULE_NAME = "ALL";
my $DTRACE_FILENAME = "hcprofile_all.dat";
my $dirpath = ".";
my $cluster = undef;
my $build = undef;
my $no_of_nodes = 16;
my %hadb_profile_times = ();

sub parse_dtrace_files {
   my $ifile = shift @_;
   open (IN, $ifile) or
      die "cant open file, $ifile";
   open (OUT, ">$ifile.tmp") or
      die "cant open file, $ifile.tmp";

   # Profiling Metrics
   my $disk_r  = undef;
   my $disk_w  = undef;
   my $load    = undef;
   my $pages   = undef;
   my $net_r   = undef;
   my $net_w   = undef;
   my $syscall = undef;
   my @tm_info = undef;

   my $line = undef; 
   while ($line = <IN>) {
      chomp $line;
      next if ($line =~ /^\s*$/);
      if ($line =~ /load:/) {
         #print "load : $line\n";
         $disk_r  = 0; 
         $disk_w  = 0; 
         $load    = 0; 
         $pages   = 0;
         $net_r   = 0;
         $net_w   = 0;
         $syscall = 0;
         @tm_info = split (/[,]/, $line);
         # Parse Load average
         if (defined $tm_info[1]) {
            my @l = split (/:/, $tm_info[1]);
            $load = $l[1] if (defined $l[1]); 
         }
         # Parse Read KB and Write KB for each process
         # @@@ This needs to evolve to include r/w bytes for each process
         # For now we only sum bytes for each process and output to the file
         chomp ($line = <IN>); # Ignore the Empty Line after the 'load:' line.
         my $data = undef;
         while ($data = <IN>) {
            chomp $data;
            next if ($data =~ /^PROC/); 
            if ($data =~ /^\S+\s*(\d+)\s*(\d+)\s*(\d+)\s*(\d+)\s*(\d+)\s*(\d+)\s*(\d+)\s*(\d+)\s*/) {
               #print "Data $data\n";
               $disk_r += sprintf ("%d", $2) if (defined $2);
               $disk_w += sprintf ("%d", $4) if (defined $4);
               $pages   += sprintf ("%d", $5) if (defined $5);
               $net_r   += sprintf ("%d", $6) if (defined $6);
               $net_w   += sprintf ("%d", $7) if (defined $7);
               $syscall += sprintf ("%d", $8) if (defined $8);
            }
            elsif ($data =~ /^\s*$/) {
               # convert in MB
               $disk_r /= 1024;
               $disk_w /= 1024;
               $net_r /= 1024;
               $net_w /= 1024;
               # number of pages
               $pages = $pages;
               $syscall += 0; 
               #print "empty line, breaking here $data\n";
               last;
            }
         }
      }

      if (defined $disk_r) {
         my @t = split (/ /, $tm_info[0]);
         # if date is a single digit, then dtrace script adds tab space
         if (defined $t[2]) {
            print OUT "$t[1]/$t[2]/$t[0]:$t[3] $disk_r $disk_w $net_r $net_w $syscall $load $pages\n"
         } else {
            print OUT "$t[1]/$t[3]/$t[0]:$t[4] $disk_r $disk_w $net_r $net_w $syscall $load $pages\n";
         }
       
         $disk_r  = undef; 
         $disk_w  = undef; 
         $pages   = undef;
         $net_r   = undef;
         $net_w   = undef;
         $syscall = undef;
         @tm_info = ();
      }
   }
   close (IN);
   close (OUT);
} # parse_dtrace_files #

sub create_gnu_graph {
   my ($ifile, $pngfile) = @_; 
   `gnuplot << EOF
      set xlabel "Date/Time"
      set ylabel "Read/Write in MB"
      set y2label "Load Average"
      set xdata time
      set timefmt "%b/%d/%Y:%H:%M:%S"
      set format x "%H:%M"
      set yrange [0:*]
      set y2tics
      set y2range [0:*]
      set grid
      set terminal png xffffff x000000 x00000000 x404040 xff0000 xffa500 x0000ff xd500ff x00ff00 xdda0dd x9500d3
      set output '$pngfile.png'
      plot '$ifile' using 1:7 smooth csplines axes x1y2 title 'global cpu avg','$ifile' using 1:2 smooth csplines title 'disk read' with line,'$ifile' using 1:3 smooth csplines title 'disk write','$ifile' using 1:4 smooth csplines title 'net read','$ifile' using 1:5 smooth csplines title 'net write','$ifile' using 1:8 smooth csplines title 'pages in'
EOF`;
} # create_profiling_graph

sub log_profile_time {
    my ($file, $node) = @_;
    my $start_profile_time = undef;
    my $stop_profile_time = undef;
    chomp ($start_profile_time = `head -n 1 $file | awk {'print \$1'}`);    
    chomp ($stop_profile_time = `tail -1 $file | awk {'print \$1'}`);    
    $hadb_profile_times{$node}{'start'} = $start_profile_time;
    $hadb_profile_times{$node}{'end'} = $stop_profile_time;
} # log_profile_time #

sub create_htmlpage {
   my $htmlfile = shift @_;
   open (HTML, "> $htmlfile") or 
      die "cant open, $!"; 
   print HTML "<HTML>";
   print HTML "<HEAD>";
   print HTML "<TITLE> $MODULE_NAME Profiling Metrics for $cluster </TITLE>";
   print HTML "<BODY>";
   print HTML "<H2 align=center> $MODULE_NAME Profiling Metrics for $cluster </H2>";
   print HTML "<H4 align=center> $build </H4>";
   for (my $i=1; $i<=$no_of_nodes; $i++) {
      my $node = 100 + $i;
      print HTML "<H3> Node $node </H3>";
      if (defined $hadb_profile_times{$node}{'start'} && 
          defined $hadb_profile_times{$node}{'end'}) {
          print HTML "<H5> Start Time :$hadb_profile_times{$node}{'start'}  -  Stop Time :$hadb_profile_times{$node}{'end'} </H5>";
      }
      ## @@@ FIXME need to give relative file paths
      my $imagefile = $dirpath."/".$node."/".$DTRACE_FILENAME.".png";  
      print HTML "<img src=\"$imagefile\">";
   }
   print HTML "</BODY>";
   print HTML "</HEAD>";
   print HTML "</HTML>";
   close (HTML);
} # create_htmlpage #

### main()

chomp($cluster = `head -n 3 ./version | grep '^dev'`);
chomp($build = `head -n 3 ./version | grep '^Honeycomb'`);

### Parse dtrace output file for each node
# During parsing write to a output file in gnuplot format
for (my $i=1; $i<=$no_of_nodes; $i++) {
   my $node = 100 + $i;
   #my $dtrace_file = $node."/".$DTRACE_FILENAME;
   my $fileDir = $dirpath . "/" . $node;
   my $file = $fileDir . "/$DTRACE_FILENAME";
   my $compressedFile = $file . ".gz";
   `gzip -d $compressedFile 2>/dev/null`;
   parse_dtrace_files($file);
   log_profile_time ($dirpath."/".$file.".tmp", $node);
   `gzip $file 2>/dev/null`;
}
print "Parsing Node Data ... DONE\n";

#### Generate graph for each node
for (my $i=1; $i<=$no_of_nodes; $i++) {
   my $node = 100 + $i;
   my $fileDir = $dirpath . "/" . $node;
   my $file = $fileDir . "/$DTRACE_FILENAME";
   create_gnu_graph ($file . ".tmp", $file);
}
print "Creating gnuplot graphs ... DONE\n";

#### Generate html page for the cluster
my $htmlfile = $dirpath . "/" . $cluster . $MODULE_NAME . ".html";
create_htmlpage $htmlfile;
print "Creating html page ... DONE\n";

#### Set r/x permission for everyone ####
`chmod -R ugo+rx $dirpath`;

exit 0

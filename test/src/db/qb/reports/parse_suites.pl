#!/usr/local/bin/perl -w
#
# $Id: parse_suites.pl 10856 2007-05-19 02:58:52Z bberndt $
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

#   Author: Larry Wall
# Filename: %M%
#  Version: %I%
# Modified: %H% %U%
#     SCCS: %W%
#  Created: 08/25/05

use strict;
use XML::Simple qw(:strict);
use Data::Dumper;
use Tie::IxHash;
use Mysql;

require "/home/ta144659/qb_reports/build_html.pl";

my %bugs;

my $dbh_S = DBI->connect( 'dbi:Oracle:NSQIS', 'web_app', 'web_app',) || die "Database connection not made: $DBI::errstr";
my $sth = $dbh_S->prepare('truncate table test_results');
$sth->execute();
$dbh_S->disconnect;

#***********************************************************************
# Get the bug data - hash of comma separated bugs
#***********************************************************************
 
my $dbh = Mysql->connect( 'hc-web.sfbay.sun.com','qb',
                         'nobody',
                          '',
                        ) || die "Database connection not made: $DBI::errstr";
  my $sql = qq{ select distinct result,bug from result_bug };

  $sth = $dbh->query( $sql );
  $sth->execute();

while ( my @row = $sth->fetchrow_array) {
    $bugs{$row[0]} .= ",$row[1]";
    $bugs{$row[0]} =~ s/^\,//;
}

#***********************************************************************
# Get the tag 
#***********************************************************************
my ($infile, $line, $tag, $flag, $fl, $first_tag);
#---
  $infile = "qbreports.xml";
  my %tag;

    open (INFILE, "< $infile");
    my $oldSuitDef = "";
    my $SuiteDef="";
    while (<INFILE>) {
	chomp ($line = $_) ;
	if ($line =~ /\<suitedef/) {
        $flag = 1;
        $line =~ s/\"//g;
        $line =~ s/\<suitedef name=//g;
        $SuiteDef = $line;
   }  
     
    if ($line =~ /\<tag>/) {
           $fl = 1; 
	    $line =~ s/\<tag\>//;
            $line =~ s/\<\/tag\>//;
            $tag = $line;
           
    } else { $fl = 0; }

    if (($oldSuitDef eq $SuiteDef) && ($fl == 1)) {
	    $tag{$SuiteDef} = join (",",$first_tag,$tag);
	 } elsif ($fl == 1) {
         $tag{$SuiteDef} = $tag; 
         $oldSuitDef = $SuiteDef;
         $first_tag = $tag;
     }
	
}
close INFILE;

#*********************************************************************************
# Create a Hash of reports level for each Test swuit for the $tpath in build html
#********************************************************************************
my %reports_def;
my $flag1 = 0;
# my $count = 0;
my ($drill, $drill1);

 open (INFILE, "< $infile");
 while (<INFILE>) {
     	chomp ($line = $_) ;
         $line =~ ~ s/[\s ]*\|[\s ]*/\|/g;
        if (($line =~ /<report name=/) && ($flag1 == 0)) {
	    $line =~ s/\"|>|^[\s]*//g;
            $line =~ s/\<report name=//g;
	    # $count=0;
            $drill = $line;
	    $flag1 = 1;
        }   elsif (($line =~ /<report name=/) && ($flag1 == 1)) {
	        $line =~ s/\"|>|^[\s]*//g;
                $line =~ s/\<report name=//g;
                $drill1 = $line;
                # $count++;
        }   elsif (($line =~ /<suite name=/) && ($flag1 == 1)) {
                $line =~ s/\"|>|^[\s]*//g;
		$line =~ s/\<suite name=//g;  
                $reports_def{$line} = join("/", $drill, $drill1);
	}   elsif ($line =~ /<\/report/) {
                $flag1 = 0;
        }
  } 


#***********************************************************************
#  Generate sql and call build_htmls
#***********************************************************************

open (OUTFILE,"> data_dumper.out");
select OUTFILE;

my $CONTENTKEY = "";
my $Reports;
# keep things in proper order
tie %$Reports, "Tie::IxHash";

 $Reports = XMLin("qbreports.xml", 
                KeepRoot => 1,
                ForceArray => 1,
                ForceContent => 1,
                #ContentKey => $CONTENTKEY,
                KeyAttr => [],
                NormaliseSpace => 2); 
print(Dumper($Reports));
close OUTFILE;

select STDOUT;
my @repkeys = keys %$Reports;
my $class = $repkeys[0];
my $data = @{$Reports->{$class}}[0];

my $next_level = $$data{suitedef};

# get the suites array
my $href;

my $stuff = "R.id, R.status, B.name, NULL, DATE_FORMAT(R.start_time,'%m/%d/%y %h:%i %p') START_TIME, DATE_FORMAT(R.end_time,'%m/%d/%y %h:%i %p') END_TIME, R.run, R.logs_url, S.name, R.testcase, TC.testproc";

my $qb_mul = "result R, testcase TC, tester S, build B, tag t1, tag t2, result_tag rt1, result_tag rt2";
my $qb = "result R, testcase TC, tester S, build B, tag t1, result_tag rt1";

my $otherstuff = "R.id = rt1.result and t1.id = rt1.tag and R.submitter = S.id and R.build = B.id and R.testcase = TC.id";
my $otherstuff_mul = "R.id = rt1.result and R.id = rt2.result and t1.id = rt1.tag and t2.id = rt2.tag and R.submitter = S.id and R.build = B.id and R.testcase = TC.id";	    

my $otherstuff_ext ='';
my $qb_ext='';

my $x = scalar(@$next_level);
print STDOUT "There are $x suitedefs\n\n";
foreach $href (@$next_level)
{
  print "$$href{name}\n";
}  

my @parent;
my $tree = '';
$class = 'suitedef';

my $a = '';

foreach $href (@$next_level)

{
   
   $a = $$href{name};
   foreach $_ (keys %tag) {

      if (($tag{$_} =~ /,/) && ($_ =~ /$a/)) {
         $tag{$_}=~ s/[\s ]*\|[\s ]*/\|/g;
         # @tags = split (",", $tag{$_} );
         # print "$tags[0] ->\n";
         # print $tags[1]."\n";
         $qb_ext = $qb_mul;
         $otherstuff_ext = $otherstuff_mul;
    
         } elsif ($_ =~ /$a/) {
	      # @tags =$tag{$_}; 
	      #print "$tags[0]\n";
	      $qb_ext = $qb;
	      $otherstuff_ext = $otherstuff;
             
              
         }
     }
     
    local ($main::sql) = "select $stuff from $qb_ext where $otherstuff_ext \n";
    handleResource(\$tree, \@parent, $class, $href);
    print STDOUT "$$href{name} =>\n$main::sql\n";
    
    my $temp_sql = "$main::sql";
    my $sql = qq{$temp_sql};
    my $sth = $dbh->query($sql);
    $sth->execute();
    my $cnt = 0;
    my $infile = join (".", $a, "csv");
    # my $dataline = 0;
    open (OUTFILE,"> $infile");
    select OUTFILE;

    while ( my @row = $sth->fetchrow_array) {
          $row[3] = exists($bugs{$row[0]}) ? $bugs{$row[0]} : '' ;

	  my $dataline = join("|", @row);
          #print STDOUT "$dataline \n";
          print OUTFILE "$dataline\n";
          $cnt++;
    }
 
    # &setDBConnection_mySQL("disconnect");
close OUTFILE;
my $tpath = $reports_def{$a};
&build_htmls($infile, $tpath) if (! -z $infile);
 

   
}  # for suitedefs



print STDOUT "All Done\n";




#***************************************************************************
# Subroutines
#***************************************************************************


sub handleResource
{
  my ($tree, $parent, $class, $data) = @_;
  # print STDOUT "\n-- sub data -- $class\t$data\n";
  my $notes = undef;
  my $id = '';
  my $line = undef;
  my @parent = @$parent;

  my @childClasses = ();
  my @childData = ();

  foreach my $key (keys(%$data))
  {
     unless ($class eq 'suitedef') # skip higher levels
     {
         push (@parent,$class);
         $id = join("|",@parent);
     }
    my $value = $data->{$key};
     # print STDOUT "  --- ID = $id\n  --- key = $key\n  --- value = $value\n";
     
    if (ref($value) eq "ARRAY")
    {
      # print STDOUT "---- hit array\n";
      foreach my $child (@$value)
      {
        push(@childClasses, $key);
        push(@childData, $child);   
      }
    }
    else
    {
      # properties stuff
      if ($key eq 'start_date')
      {
       $line = "AND R.start_time >= STR_TO_DATE(\'$value\', '%m/%d/%Y %H:%i:%s')";
         
      }
      elsif ($key eq 'end_date')
      {
        $line = "AND R.end_time <= STR_TO_DATE(\'$value\', '%m/%d/%Y %H:%i:%s')";
      }
      elsif ($key eq 'content')
      {
          # handle those things in the AND space
          $key = pop(@parent);
          my $sep = $tree == 1 ? ") order by R.id" : pop(@parent);
          $key = $tree == 1 ? "t1.name" : "t2.name";
          $line = "$key = \'$value\' $sep ";
      }
      else 
      {

          
         $line = " AND $key = \'$value\' ";
      }
    #build query string
    $main::sql .= "$line\n" unless ($key eq 'name');
    # print STDOUT "-- line = $line\n";
      
    }
  }
  my $childcnt = scalar(@childClasses);
  my $child = 0;
  while ($childcnt > 0)
  {
    # print STDOUT "++++ processing array\n";
      if ($childClasses[$child] eq 'and')
      {
          # prep for ANDing parameters
          $main::sql .= "AND (";
      }
    handleResource($childcnt, \@parent, $childClasses[$child], $childData[$child]);
    $child++;
    $childcnt--; 	
  }
  return 1;
}

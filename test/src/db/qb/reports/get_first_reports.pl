#!/usr/local/bin/perl -w
#
# $Id: get_first_reports.pl 10856 2007-05-19 02:58:52Z bberndt $
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

#   Author: Tatyana Alexeyev
# Filename: get_reports.pl
#  Version: 1.1
#  Created: 09/12/05
# Modified: 09/17/05 19:42:36
#     SCCS: @(#)get_reports.pl	1.1

$PgmDir = $0; $PgmDir =~ s#(.*)/.*$#$1#;  # extract the full path dirname
($PgmName = $0) =~ s@.*/@@;        # extract only the program name

use Cwd;
$dir = getcwd;
$PgmVersion = "1.1";
$PgmID = "$dir\/$PgmName-$PgmVersion";

$now =localtime(time);
use DBI;

$ENV{ORACLE_HOME} = "/usr/dist/pkgs/sqlplus";
$ENV{TNS_ADMIN} = "/home/Shared/prod/";
use lib "/home/Shared/prod/lib";
use Time::Local;
require "WWWAndHTMLRoutines.inc";
use Text::Autoformat;
use CGI::Carp qw(fatalsToBrowser);
use CGI qw(:standard);

my $infile = "qbreports.xml";
my      $env        = $ENV{'env'};
        $env        = "prod" if (! $env );
my      $Host;
my      $host = `/usr/bin/hostname`;


my %reports_def;
my $flag1 = 0;
my ($drill, $drill1);
my $dbh;
my %lines;


 open (INFILE, "< $infile");
 while (<INFILE>) {
     	chomp ($line = $_) ;
         $line =~ ~ s/[\s ]*\|[\s ]*/\|/g;
        if (($line =~ /<report name=/) && ($flag1 == 0)) {
	    $line =~ s/\"|>|^[\s]*//g;
            $line =~ s/\<report name=//g;
	    $drill = $line;
	    $flag1 = 1;
        }   elsif (($line =~ /<report name=/) && ($flag1 == 1)) {
	        $line =~ s/\"|>|^[\s]*//g;
                $line =~ s/\<report name=//g;
	        $drill1 = $line;
        }   elsif (($line =~ /<suite name=/) && ($flag1 == 1)) {
                $line =~ s/\"|>|^[\s]*//g;
		$line =~ s/\<suite name=//g;  
                $reports_def{$line} = join("/", $drill, $drill1);
	}   elsif ($line =~ /<\/report/) {
                $flag1 = 0;
        }
  } 

close INFILE;


#************************************************************************
# Build first level reports
#************************************************************************
 $dbh = DBI->connect( 'dbi:Oracle:NSQIS', 'web_app', 'web_app',
                   ) || die "Database connection not made: $DBI::errstr";
       my $last='';
       foreach $_ (keys %reports_def) {    
	     undef my @rows;
	     next if ($last eq $reports_def{$_});
	     my @reports = split("\/", $reports_def{$_});
	     my $sql = qq{select drill1, (sum(pass) + sum(fail)) * 100 / (sum(pass) + sum(fail) + sum(skip)), sum(pass), sum(fail), sum(skip) from test_results1 where drill= '$reports[0]' and drill1 = '$reports[1]' group by drill1 };

	     $sth = $dbh->prepare($sql);
	     $sth->execute();
             $last = $reports_def{$_};
             while (my @row = $sth->fetchrow_array) {
		  $row[1] = sprintf("%.2f", $row[1]);
	          $row[0] =~ s/ /_/g;
                  my $link = join("", $row[0], ".html");
		  $row[0] = "<A href = $link>$row[0]</A>";
                  push(@rows,td(\@row));
	  }
       
    $sth->finish();
    my @nHdr = ( "Drill1","Test Penetration","#PASS","#FAIL", "#NOT RUN");
    $reports[0] =~ s/ /_/g;
    my $outfile1 = join("","./html/", $reports[0], ".html");
    open (OUTFILE1, "> $outfile1");
    $reports[0] =~ s/_/ /g;
    $title = " Test Results for $reports[0]";
    $themetatag = &MakeExpirationMetaTag(24);
    select OUTFILE1;
    print '<!DOCTYPE html PUBLIC "-//IETF//DTD HTML 2.0//EN">',"\n";
    print "<HTML>\n";
    print "<HEAD>\n";
    print '<TITLE>',$title,'</TITLE>',"\n";
    print "$themetatag\n";
    print "<META NAME=\"Author\"	CONTENT=\"$PgmID\">\n";
    print '<META NAME="Date Created" CONTENT="$html_time">'."\n";
    print "</HEAD>\n";
    print "<BODY BGCOLOR='#FFFFFF'>\n";
    print h1($title);

    #my $x = join("\/",$reports[0],$reports[1]);
    print "/$reports[0] <BR><BR>";
    print table({-border=>''},
		    TR({-align=>CENTER},[th(\@nHdr),@rows])
	    );
    print '<p><font SIZE="1">Last updated ',$now,' - LW &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;</font>',"\n";
    print '<font SIZE="3">&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; Sun Confidential:Internal Only</font></p>',"\n";
    print OUTFILE1 end_html;
    close OUTFILE1;

}


$dbh->disconnect();

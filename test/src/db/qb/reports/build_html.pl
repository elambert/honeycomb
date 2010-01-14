#!/usr/local/bin/perl
#
# $Id: build_html.pl 10856 2007-05-19 02:58:52Z bberndt $
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
# Filename: build_html.pl
#  Version: 1.1
#  Created: 09/12/05
# Modified: 09/17/05 19:42:36
#     SCCS: @(#)build_html.pl	1.1

$PgmDir = $0; $PgmDir =~ s#(.*)/.*$#$1#;  # extract the full path dirname
($PgmName = $0) =~ s@.*/@@;        # extract only the program name

use Cwd;
$dir = getcwd;

$PgmVersion = "1.1";
$PgmID = "$dir\/$PgmName-$PgmVersion";

$now =localtime(time);
print "################ Executing $PgmID at $now\n";

use lib "/home/Shared/prod/lib";

# require "0MyIncludes";
# require "formdate.pl";
use Time::Local;
require "WWWAndHTMLRoutines.inc";
use Text::Autoformat;
use CGI::Carp qw(fatalsToBrowser);
use CGI qw(:standard);
use Mysql;
use DBI;

#***************************************************************************
# Routine to build Web Pages for a given test suite
#   Suite results are kept in an output file for this part of the development
#   to save time - rather than run a query each time. In the real program,
#   the code here would be executed on the fly
#***************************************************************************
# use strict;

sub build_htmls {


my ($infile,$tpath) = @_;
open (INFILE,"< $infile");
my @names = split('\.', $infile);
my $TS_name = $names[0];


# process the file - would be done during the query in the real program

my $line;

my (%testProcs, %testCases, %results, %penultimate, %lastResult);
my (%pass, %fail, %skip);
my $nSkip=0;
my $nFail=0;
my $nPass=0;


undef @fields;

# my $junk = (<INFILE>); #burn the header
while ($line = <INFILE>) {

    
    chomp($line);
    @fields = split(/\|/,$line);
    $TP = pop(@fields);	# test procedure
    $TC = pop(@fields);      # test case
    $junk = pop(@fields);	# extra field
    $testCases{$TC} = $TP;	# get the test proc associated with the test case
    $testProcs{$TP} = 1;	# get the list of test procs
    # save the results for html page
    $results{$fields[0]} = $line;
    $penultimate{$TC} = $lastResult{$TC};
    $lastResult{$TC} = $line;
}

close INFILE;

# my $tpath = "/Anza Phase I/Clustered Services/$TS_name";

my @drills = split("\/", $tpath);

$tpath = "/$tpath/$TS_name";

#goto test_case_summary;
#***************************************************************************
# Build the individual Test Case Results pages
#***************************************************************************

# process the test cases 
my @TCs = sort keys(%testCases);

foreach $TC (sort keys(%testCases)) {
    print STDOUT "-- $TC\n";
    my $TCname = getCaseName($TC);
    my $TPname = getProcName($testCases{$TC});
    undef my @rows;

    # go through the hash and build the table data
    foreach  (reverse sort keys(%results)) {
       $line = $results{$_};
        undef @fields;
        @fields = split(/\|/,$line);
        $fields[3] = $fields[3] eq '' ? '&nbsp;' : $fields[3];
        $TP = pop(@fields);	# test procedure
        my $TC1 = pop(@fields);      # test case
        $junk = pop(@fields);	# extra field
        push(@rows,td(\@fields)) if ($TC eq $TC1) ;
    }
      my @nHdr = ( "Result ID","Status","Build","Bugs","Start Time",
          "End Time","Run ID","Logs URL");

    # Make the page
    $outfile1 = join("","./html/", $TC, ".html");
    open (OUTFILE1, "> $outfile1");
    $title = " Test Results for Test Case: $TCname";
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
    my $x = join("\/",$tpath,$TPname,$TCname);
    print "$x <BR><BR>";
    print table({-border=>''},
		    TR({-align=>CENTER},[th(\@nHdr),@rows])
	    );
    print '<p><font SIZE="1">Last updated ',$now,' - LW &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;</font>',"\n";
    print '<font SIZE="3">&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; Sun Confidential:Internal Only</font></p>',"\n";
    
    print OUTFILE1 end_html;
    close OUTFILE1;
}

test_case_summary:
#***************************************************************************
# Build the Test Case Summaries for each Test Proc
#***************************************************************************
# process the test procs 

foreach $TP (sort keys(%testProcs)) {
   $TPname = getProcName($TP);

   undef @rows;
   undef @penFields;

    foreach $TC (sort keys(%testCases)) {
        undef @data;
        # get the test cases for for this testproc
        next unless ($testCases{$TC} eq $TP);	
        $TCname = getCaseName($TC);
        $link = join("", $TC, ".html");
        @fields = split(/\|/,$lastResult{$TC});
        $curStat = lc($fields[1]);		# current status
        @penFields = split(/\|/, $penultimate{$TC});
        
        $penStat = lc($penFields[1]);		# previous status
        if ($penStat eq 'fail' && $curStat eq 'pass')
        {
           $prefix = '* ' ;	# new pass
        }
        elsif ($penStat eq 'pass' && $curStat eq 'fail')         
        {
           $prefix = '! ' ;	# new fail
        }
        else
        {
           $prefix = '&nbsp;&nbsp;' ;	# no change
        }

	 $pass{$TP}++ if (lc($fields[1]) eq 'pass');
	 $fail{$TP}++ if (lc($fields[1]) eq 'fail');
	 $skip{$TP}++ if (lc($fields[1]) eq 'skipped');
	 $total{$TP}++ ;

        $fields[1] = join("",$prefix,$fields[1]);
        push @data,"<A href = $link>$TCname</A>" ;
        push @data,@fields[1,2,5] ;
        push(@rows,td(\@data));

     }
     # blank row
        undef @data;
        push @data,'&nbsp;';
        push @data,'&nbsp;';
        push @data,'&nbsp;';
        push @data,'&nbsp;';
        push(@rows,td(\@data));
     # Title row
        undef @data;
        push @data,'Test Penetration';
        push @data,'# PASS';
        push @data,'# FAIL';
        push @data,'# NOT RUN';
        push(@rows,td(\@data));
     # test proc summary
        undef @data;
        $nPass = $pass{$TP} eq '' ? '&nbsp;' : $pass{$TP} ;
        $nFail = $fail{$TP} eq '' ? "&nbsp;" : $fail{$TP} ;
        $nSkip = $skip{$TP} eq '' ? "&nbsp;" : $skip{$TP} ;
        $val = $pass{$TP} eq '' ? 0 : $pass{$TP};
        $val1 = $fail{$TP} eq '' ? 0 : $fail{$TP};
        $val2 = $skip{$TP} eq '' ? 0 : $skip{$TP};
        $testPen = sprintf("%.2f",($val1+$val)/($val + $val1 + $val2)*100);
        push @data,$testPen ;
        push @data,$nPass ;
        push @data,$nFail ;
        push @data,$nSkip ;
        push(@rows,td(\@data));
            
    my @nHdr = ( "Test Case","Status","Build","Time");

    # Make the page
    $outfile1 = join("","./html/", $TP, $TS_name, ".html");
    open (OUTFILE1, "> $outfile1");
    $title = "Test Results for Test Proc: $TPname";
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
    my $x = join("\/",$tpath,$TPname);
    print "$x <BR><BR>";
    print table({-border=>''},
		    TR({-align=>CENTER},[th(\@nHdr),@rows])
	    );
    print '<p><font SIZE="1">Last updated ',$now,' - LW &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;</font>',"\n";
    print '<font SIZE="3">&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; Sun Confidential:Internal Only</font></p>',"\n";
    
    print OUTFILE1 end_html;
    close OUTFILE1;
}

test_proc_summary:
#***************************************************************************
# Build the Test Proc Summaries for each Test Suite
#***************************************************************************
# process the test procs 
undef @rows;

my $dbh_S = DBI->connect( 'dbi:Oracle:NSQIS', 'web_app', 'web_app',) || die "Database connection not made: $DBI::errstr";

foreach $TP (sort keys(%testProcs)) {
   $TPname = getProcName($TP);

     # test proc summary
        undef @data;
        my $link = join("", $TP, $TS_name, ".html");
        $testPen = sprintf("%.2f",($pass{$TP}+$fail{$TP}) * 100/($pass{$TP}+$fail{$TP}+$skip{$TP}));
        $nPass = $pass{$TP} eq '' ? '&nbsp;' : $pass{$TP} ;
        $nFail = $fail{$TP} eq '' ? '&nbsp;' : $fail{$TP} ;
        $nSkip = $skip{$TP} eq '' ? '&nbsp;' : $skip{$TP} ;
        
        push @data,"<A href = $link>$TPname</A>" ;
        push @data,$testPen ;
        push @data,$nPass ;
        push @data,$nFail ;
        push @data,$nSkip ;
        push(@rows,td(\@data));
        $nPass = 0 if ($nPass eq '&nbsp;');
        $nFail = 0 if ($nFail eq '&nbsp;');
        $nSkip = 0 if ($nSkip eq '&nbsp;');

$sql = qq{insert into test_results values ('$drills[0]', '$drills[1]', '$TS_name', $nPass, $nFail, $nSkip)};
my $sth = $dbh_S->prepare($sql);
$sth->execute();
$sth->finish();
}   
$dbh_S->disconnect;

     my @nHdr = ( "Test Proc","Test Penetration","# PASS","# FAIL",
                 "# NOT RUN");

    # Make the page
    $outfile1 = join("","./html/", $TS_name, ".html");
    open (OUTFILE1, "> $outfile1");
    $title = " Test Results for Test Suite: $TS_name";
    $themetatag = &MakeExpirationMetaTag(24);
    select OUTFILE1;

    print '<!DOCTYPE html PUBLIC "-//IETF//DTD HTML 2.0//EN">',"\n";
    print "<HTML>\n";
    print "<HEAD>\n";
    print '<TITLE>',$title,'</TITLE>',"\n";
    print "$themetatag\n";
    print "<META NAME=\"Author\"	CONTENT=\"$PgmID\">\n";
    print '<META NAME="Date Created" CONTENT="$html_time">';
    print "</HEAD>\n";
    print "<BODY BGCOLOR='#FFFFFF'>\n";
    print h1($title);
    my $x = join("",$tpath);
    print "$x <BR><BR>";
    print table({-border=>''},
		    TR({-align=>CENTER},[th(\@nHdr),@rows])
	    );
    print "\n".'<p><font SIZE="1">Last updated ',$now,' - LW &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;</font>',"\n";
    print '<font SIZE="3">&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; Sun Confidential:Internal Only</font></p>',"\n";
    
    print OUTFILE1 end_html;
    close OUTFILE1;

# insert record into table




               
    
}    
print STDOUT "All Done!\n";

#} # end sub
#***************************************************************************
# Subroutine to get the testproc name
#***************************************************************************
sub getProcName
{
  my ($procId) = @_;
  my $dbh = Mysql->connect( 'hc-web.sfbay.sun.com','qb',
                          'nobody',
                          '',
                        ) || die "Database connection not made: $DBI::errstr";

  my $sql = qq{ select distinct name from testproc where id = $procId };    

   my $sth = $dbh->query( $sql );
   $sth->execute();

   my @row = $sth->fetchrow_array;
   #$dbh->disconnect;
   return $row[0];
   
}

#***************************************************************************
# Subroutine to get the testcase name
#***************************************************************************
sub getCaseName
{
  my ($caseId) = @_;
  my $dbh = Mysql->connect( 'hc-web.sfbay.sun.com','qb',
                          'nobody',
                          '',
                        ) || die "Database connection not made: $DBI::errstr";

  my $sql = qq{ select distinct parameters from testcase where id = $caseId };    

   my $sth = $dbh->query( $sql );
   $sth->execute();

   my @row = $sth->fetchrow_array; 
   #$dbh->disconnect;
   return $row[0];
   
}

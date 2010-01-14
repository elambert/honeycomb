#!/usr/bin/perl -w
#
# $Id: qb_reports.pl 10856 2007-05-19 02:58:52Z bberndt $
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
use XML::Simple qw(:strict);
use Getopt::Long qw(:config no_ignore_case bundling);
use Data::Dumper;
use Switch;
use POSIX;
use QBDB;
#use Tie::IxHash;

my $CONTENTKEY = "__CONTENT__";

my $REPORTSFILE = "qbreports.xml";
my $OUTPUTDIR = "/home/www/hc-web/qb/reports";

# external links using qb_query
my $CGI_BIN = "http://hc-web.sfbay.sun.com/cgi-bin/qb";
my $HTML_ROOT = "http://hc-web.sfbay.sun.com/qb/reports/";
my $QB_QUERY = ${CGI_BIN}."/qb_query.cgi";
my $QB_RESULT = ${QB_QUERY}."?target=result_details&result.id=";
my $QB_HISTORY = ${QB_QUERY}."?target=results&testcase.id=";
my $QB_UPDATE = ${CGI_BIN}."/qb_update_reports.cgi?cmd=suite";

# HTML effects
my $alt_one = "whitesmoke"; # alternating line background colors
my $alt_two = "white";     
my $update_text = "Update HTML based on current report definition"; # tooltip text
my $reload_text = "Update report definition and HTML (slower)"; # tooltip text

my %SUITE_ACTIONS = ();
my %REPORT_ACTIONS = ();

my %SUITEDEFS = ();


# Hash Table to save the suite results 
my %suite_testresults = ();

sub roundup 
{
  my ($_percent, $_no) = @_;
  my $_int;
  my $_fraction;
 
  if ($_percent =~ m/(\d+)\.(\d+)/) {
    $_int = $1;
    $_fraction = $2;
  } elsif ($_percent =~ m/(\d+)/) {
    $_int = $1;
    $_fraction = 0;
  }

  $_percent = (($_no > 0) && ($_int == 0) && ($_fraction < 1)) ? 1 : int ($_percent);
 
  return ($_percent);
} # roundup #


# Remove funny characters so given string can be used as filename
sub to_filename
{
    my ($name) = @_;
    $name =~ s/[: \/\\]/_/g;
    return $name;
}

sub handleTag
{
    my ($tag) = @_;
    #print("handling tag\n");
    #print(Dumper($tag));
    my $name = $tag->{name};
    return $name;
}

sub handleTags
{
    my ($tags) = @_;
    my @tagnames = ();
    #print("handling tags\n");
    #print(Dumper($tags));
    my $taglist = $tags->{tag};
    if (defined($taglist)) {
      foreach my $tag (@$taglist) {
        push(@tagnames, handleTag($tag));
      }
    }
  return \@tagnames;
}

sub handleTagspec
{
  my ($tagspec) = @_;
  my @tagconds = ();
  my $tagscond = undef;
  #print("handling tagspec\n");
  #print(Dumper($tagspec));

  if (defined($tagspec)) {
    my $tagnames = handleTags(@{$tagspec}[0]);
    foreach my $tagname (@$tagnames) {
      my $tagcond = "exists\n";
      $tagcond .= " (select * from testcase_tag, tag\n";
      $tagcond .= "  where testcase.id = testcase_tag.testcase and\n";
      $tagcond .= "        testcase_tag.tag = tag.id and\n";
      $tagcond .= "        tag.name = '${tagname}')\n";
      push(@tagconds, $tagcond);
    }
    if (scalar(@tagconds) > 0) {
      $tagscond = join("and\n", @tagconds);
    }
  }
  return $tagscond;
}

sub handleTestcase
{
    my ($testcaseid, $tags, $start_date, $end_date) = @_;

    my @tagconds = ();
    my $tagscond = undef;
    foreach my $tag (@$tags) {
      my $tagcond = "exists\n";
      $tagcond .= " (select * from result_tag, tag\n";
      $tagcond .= "  where result.id = result_tag.result and\n";
      $tagcond .= "        result_tag.tag = tag.id and\n";
      $tagcond .= "        tag.name = '${tag}')\n";
      push(@tagconds, $tagcond);
    }
    if (scalar(@tagconds) > 0) {
      $tagscond = join("and\n", @tagconds);
    }

    # select the latest result (max ID), its status and timestamp
    my $resultselect = "SELECT result.status, result.id, result.end_time, result.build FROM result\n";
    $resultselect .= "WHERE result.testcase = ${testcaseid}\n";
    $resultselect .= " and end_time >= '${start_date}'" unless !defined($start_date);
    $resultselect .= " and end_time <= '${end_date}'" unless !defined($end_date);
    $resultselect .= " and ${tagscond}" unless !defined($tagscond);
    $resultselect .= "ORDER BY id DESC LIMIT 1 \n";
    #print $resultselect;
    my $resulttuples = db_select_arrayref($resultselect);

    scalar (@$resulttuples) ? return @{$resulttuples->[0]} : return (undef, undef, undef, undef);
}

sub handleTestproc
{
    my ($suitename, $testprocname, $testcases, $tags, $start_date, $end_date) = @_;

    my $fname = "testproc.".to_filename("${suitename} ${testprocname}").".html";
    my $tmpfile = ${OUTPUTDIR}."/".${fname}.".tmp";
    open(TESTPROC, ">${tmpfile}") or die "unable to open ${tmpfile}\n";
    print("Handling Testproc: ${testprocname}\n");
    
    print(TESTPROC "<html>\n");
    print(TESTPROC "<head>\n");
    print(TESTPROC "<title>QB REPORT: ${testprocname}</title>\n");
    print(TESTPROC "</head>\n");
   
    my $timestamp = localtime();
    my $update_fname = $fname;
    $update_fname =~ s/\.tmp$//g;  
    my $update_link = "${QB_UPDATE}&target=${suitename}&page=${HTML_ROOT}/${update_fname}";
    #my $update_link = "${QB_UPDATE}&target=${suitename}&page=${fname}";
    print(TESTPROC "<body>\n");
    print(TESTPROC "<p><small><i>Last updated ${timestamp}</i>\t\n");
    print(TESTPROC "<b><a href=\"${update_link}&action=-H\" title=\"${update_text}\">[Update]</a>\t\n");
    #print(TESTPROC "<b><a href=\"${update_link}&action=-L\" title=\"${reload_text}\">[Reload]</a>");
    print(TESTPROC "</b></small></p>\n");
    print(TESTPROC "<h2>Test Procedure: ${testprocname}</h2>\n");

    print(TESTPROC "<table>\n");
    print(TESTPROC "<tr bgcolor=\"lightgray\">\n");
    print(TESTPROC "<td><strong><small>Test Case</strong></small></td>");
    print(TESTPROC "<td><strong><small>Status</strong></small></td>");
    print(TESTPROC "<td><strong><small>Last Run</strong></small></td>");
    print(TESTPROC "<td><strong><small>Build</strong></small></td>");
    print(TESTPROC "<td><strong><small>History</strong></small></td>");
    print(TESTPROC "<tr>\n");

    my ($pass, $fail, $notrun) = (0, 0, 0);
    my $color = 0;

    foreach my $testcase (@$testcases) {
      my ($status, $result_id, $result_time, $build) = handleTestcase($testcase, $tags, $start_date, $end_date);
      # If the result table is empty for that test case, DO NOT CHOKE
      next unless (defined $status);

      if (defined($build)) {
        my $build_select = "select name from build where id = ${build}";
        my $build_tuples = db_select_arrayref($build_select);
        foreach my $build_tuple (@$build_tuples) {
          ($build) = @$build_tuple;
          last;
        }
      }
       
      if ($color == 0) {
        print(TESTPROC "<tr bgcolor=\"${alt_one}\">\n");
        $color++;
      } else {
          print(TESTPROC "<tr bgcolor=\"${alt_two}\">\n");
          $color = 0;
        }

      print(TESTPROC "<td><small>");
      my $parameters = undef;
      my $caseselect = "select parameters from testcase where id = ${testcase}";
      my $casetuples = db_select_arrayref($caseselect);
      foreach my $casetuple (@$casetuples) {
        ($parameters) = @$casetuple;
        last;
      }

      $parameters = "Default Scenario" if ((!defined $parameters) || ($parameters eq ""));
      print(TESTPROC "${parameters}");
      print(TESTPROC "</small></td>");

      print(TESTPROC "<td><small>");
      print(TESTPROC "<a href=\"${QB_RESULT}${result_id}\">${status}</a>");
      print(TESTPROC "</small></td>");

      print(TESTPROC "<td><small>");
      $result_time = "" unless (defined $result_time);
      print(TESTPROC "${result_time}");
      print(TESTPROC "</small></td>");

      print(TESTPROC "<td><small>");
      $build = "" unless (defined $build);
      print(TESTPROC "${build}");
      print(TESTPROC "</small></td>");

      print(TESTPROC "<td><small>");
      print(TESTPROC "<a href=\"${QB_HISTORY}${testcase}\">history</a>");
      print(TESTPROC "</small></td>");

      if ($status eq 'pass') {
        $pass++;
      } elsif ($status eq 'fail') {
          $fail++;
      } else {
        $notrun++;
      }
    }

  print(TESTPROC "</tr>\n");
  print(TESTPROC "</table>\n");
  print(TESTPROC "</body>\n");
  print(TESTPROC "</html>\n");

  close(TESTPROC) or die "unable to close ${tmpfile}\n";

  # rename the tmp file
  my $file = $tmpfile;
  $file =~ s/\.tmp//g;
  rename ($tmpfile, $file) or die "unable to rename $tmpfile to $file\n";
  my $mode = 0664; 
  chmod ($mode, $file); 

  return ($pass, $fail, $notrun);
} # handleTestproc #

sub handleSuitedef
{
    my ($suitedef) = @_;
    #print(Dumper($suitedef));
    
    my $name = $suitedef->{name};
    my $lead = $suitedef->{lead};
    my $start_date = $suitedef->{start_date};
    my $end_date = $suitedef->{end_date};
    if (!defined($lead)) {
        $lead = " ";
    }
    my $tagnames = undef;
  		
    #print("handling suitedef: ${name}\n");
    my ($complete, $num_cases, $pass, $fail, $notrun) = (0, 0, 0, 0);

    my ($suite_complete, $suite_penetration, $suite_pass, $suite_fail) = (0, 0, 0);
    my ($agg_complete, $agg_penetration, $agg_pass, $agg_fail) = (0, 0, 0);

    my %testproc_tbl;
 
    my @actions = ();
    if (defined($SUITE_ACTIONS{$name})) {
	@actions = $SUITE_ACTIONS{$name};
    } elsif (defined($SUITE_ACTIONS{ALL})) {
	@actions = $SUITE_ACTIONS{ALL};
    }

    foreach my $action (@actions) {
      if ($action =~ /^html$/) {
        print("Handling suite: ${name}...\n");
            
        my $fname = "suite.".to_filename($name).".tmp.html";
        my $file = $OUTPUTDIR."/".$fname;
        open(SUITE, ">${file}") or die "unable to open ${file}\n";
        #print("Suite file: ${fname}\n");

        print(SUITE "<html>\n");
        print(SUITE "<head>\n");
        print(SUITE "<title>QB REPORT: ${name}</title>\n");
        print(SUITE "</head>\n");
            
        my $timestamp = localtime();
        # This is a hack only for fname, used only for web access.
        my $update_fname = $fname;
        $update_fname =~ s/tmp.//g;  
        my $update_link = "${QB_UPDATE}&target=${name}&page=${HTML_ROOT}/${update_fname}";
        print(SUITE "<body>\n");
        print(SUITE "<p><small><i>Last updated ${timestamp}</i>\t\n");
        print(SUITE "<b><a href=\"${update_link}&action=-H\" title=\"${update_text}\">[Update]</a>\t\n");
        #print(SUITE "<a href=\"${update_link}&action=-L\" title=\"${reload_text}\">[Reload]</a>");
        print(SUITE "</b></small></p>\n");
	print(SUITE "<h2>Suite: ${name}</h2>\n");
           
        print(SUITE "<p><small><strong>Tags:</strong>\n");
        if (defined $suitedef->{tagspec}) {
          $tagnames = handleTags(@{$suitedef->{tagspec}}[0]);
          my $taglist = join("] [", @$tagnames);
          print(SUITE "[$taglist]</small></p>\n");
        } else {
          print(SUITE "None. Suite definition is incomplete.</small></p>\n");
        }
        if (defined $suitedef->{comments}) {
            print(SUITE "<p><small><strong>Comments:</strong>\n");
            print(SUITE "$suitedef->{comments}</small></p>\n");
        }

	print(SUITE "<table>\n");
	print(SUITE "<tr bgcolor=\"lightgray\">\n");
	print(SUITE "<td><small><strong>Test Procedure</strong></small></td>");
        print(SUITE "<td><small><strong># Cases</strong></small></td>");
	print(SUITE "<td><small><strong># Pass</strong></small></td>");
	print(SUITE "<td><small><strong># Fail</strong></small></td>");
	print(SUITE "<td><small><strong># Not Run</strong></small></td>");
	print(SUITE "</tr>\n");

        my $testcases = undef;
        my @testcases_list = ();
      
        # Can do the following code with 1 sql query i.e. handleTagspec
        my $suiteselect  = "select distinct testcase.testproc, testcase.id, testproc.name\n";
        $suiteselect .= "from\n";
        $suiteselect .=    "testcase, testproc\n";
        $suiteselect .= "where\n";
        $suiteselect .=    "testcase.testproc = testproc.id and\n";
        $suiteselect .= handleTagspec ($suitedef->{tagspec});
        $suiteselect .= "order by testproc.name asc";
	$testcases = db_select_arrayref($suiteselect);
	my @proctestcases = ();
	my ($currentproc, $curprocname) = (undef, undef);
	my ($testprocid, $testcaseid, $testprocname) = (undef, undef, undef);
	my $color = 0;
        my $numtestcases = scalar(@$testcases); 

        foreach my $testcase (@$testcases) {
	  ($testprocid, $testcaseid, $testprocname) = @$testcase;
          push (@{$testproc_tbl{$testprocname}}, $testcaseid); 
        } 
             
        foreach my $testprocname (keys (%testproc_tbl)) {
          my ($_pass, $_fail, $_notrun) = handleTestproc ($name, $testprocname, $testproc_tbl{$testprocname}, $tagnames, $start_date, $end_date);  
          my $_total = $_pass + $_fail + $_notrun;
          $num_cases += $_total;
          my $_num_run = $_pass + $_fail;
          my $fname = "testproc.".to_filename("${name} ${testprocname}").".html";
          if ($color == 0) {
            print(SUITE "<tr bgcolor=\"${alt_one}\">\n");
            $color++;
          } else {
            print(SUITE "<tr bgcolor=\"${alt_two}\">\n");
            $color = 0;
          }
          print(SUITE "<td><small><a href=\"${fname}\">${testprocname}</small></a></td>");
          print(SUITE "<td><small>${_total}</small></td>");
          print(SUITE "<td><small>${_pass}</small></td>");
          print(SUITE "<td><small>${_fail}</small></td>");
          print(SUITE "<td><small>${_notrun}</small></td>");
          print(SUITE "</tr>\n");

          my $proc_complete = 0;
          my $proc_penetration = 0;
          my $proc_pass = 0;
          my $proc_fail = 0;
          if ($_num_run != 0) { 
            $proc_complete = (($_pass) / $_total);
            $proc_penetration = (($_pass + $_fail) / $_total);
            $proc_pass = ($_pass/$_num_run)*$proc_penetration; # weighted for aggregate stats
            $proc_fail = ($_fail/$_num_run)*$proc_penetration;
          }
          $suite_complete += $proc_complete;
          $suite_penetration += $proc_penetration;
          $suite_pass += $proc_pass;
          $suite_fail += $proc_fail;
        }
	print(SUITE "</table>\n");

        my $num_testprocs = scalar(keys(%testproc_tbl));
        $agg_complete = ($num_testprocs == 0) ? 0 : roundup(POSIX::floor(100*$suite_complete/$num_testprocs), $suite_complete);
        $agg_penetration = ($num_testprocs == 0) ? 0 : roundup(POSIX::floor(100*$suite_penetration/$num_testprocs), $suite_penetration);
        $agg_pass = ($suite_penetration == 0) ? 0 : roundup(POSIX::floor ($suite_pass*100/($suite_penetration)), $suite_pass);
	$agg_fail = ($agg_penetration == 0) ? 0 : 100 - $agg_pass;

	print(SUITE "<br>\n");
	print(SUITE "<table>\n");
	print(SUITE "<tr bgcolor=\"white\">\n");
	print(SUITE "<td colspan=3><small><strong>Aggregate Statistics<strong></small></td>");
	print(SUITE "</tr>\n");
	print(SUITE "<tr bgcolor=\"lightgray\">\n");
        print(SUITE "<td><small><strong># Cases</strong></small></td>");
        print(SUITE "<td><small><strong>% Complete</strong></small></td>");
        print(SUITE "<td><small><strong>% Penetration</strong></small></td>");
	print(SUITE "<td><small><strong>% Pass</strong></small></td>");
	print(SUITE "<td><small><strong>% Fail</strong></small></td>");
	print(SUITE "</tr>\n");

        print(SUITE "<tr bgcolor=\"${alt_one}\">\n");
        print(SUITE "<td><small><strong>${num_cases}</strong></small></td>");
        print(SUITE "<td><small><strong>${agg_complete}</strong></small></td>");
        print(SUITE "<td><small><strong>${agg_penetration}</strong></small></td>");
	print(SUITE "<td><small><strong>${agg_pass}</strong></small></td>");
	print(SUITE "<td><small><strong>${agg_fail}</strong></small></td>");
	print(SUITE "</tr>\n");
	    
	print(SUITE "</table>\n");
	print(SUITE "</body>\n");
	print(SUITE "</html>\n");
	    
	close(SUITE) or die "unable to close ${file}\n";
 
        # rename the tmp file
        my $oldfname = "suite.".to_filename($name).".tmp.html";
	my $oldfile = $OUTPUTDIR."/".$oldfname;
        my $newfname = "suite.".to_filename($name).".html";
	my $newfile = $OUTPUTDIR."/".$newfname;
        rename ($oldfile, $newfile) or die "unable to rename $oldfile to $newfile\n";
        my $mode = 0664; 
        chmod ($mode, $newfile); 
      }
    }

    return ($num_cases, $agg_complete, $agg_penetration, $agg_pass, $agg_fail, $lead);
}
    
sub handleSuitedefs
{
    my ($suitedefs) = @_;
    #print("handling suitedefs\n");
    #print(Dumper($suitedefs));
    foreach my $suitedef (@$suitedefs) {
        my $suitedef_name = $suitedef->{name};
        $SUITEDEFS{$suitedef_name} = $suitedef;
	#handleSuitedef($suitedef);
    }
}

sub handleSuite 
{
  my $suite = shift (@_);
  my $suite_name=$suite->{'name'};
  #print("handleSuite(${suite_name})");
  my $lead = $suite->{'lead'};
  if (!defined($lead)) {
    $lead = " ";
  }

  my ($num_cases, $complete, $penetration, $pass, $fail) = (0, 0, 0, 0, 0);

  my $doit = 0;
  if (exists ($SUITE_ACTIONS{$suite_name}) ||
      exists ($SUITE_ACTIONS{'ALL'})) {
    my $suitedef = $SUITEDEFS{$suite_name};
    if (!defined($suitedef)) {
      print(STDERR "error: suite undefined: ${suite_name}\n");
    }
    else {
      ($num_cases, $complete, $penetration, $pass, $fail) = handleSuitedef($suitedef);
    }
  }

  return ($num_cases, $complete, $penetration, $pass, $fail, $lead);
} # handleSuite

sub handleReport
{
    my ($report, $testreport) = @_;
    my $report_name=$report->{'name'};
    my $output=$report->{'output'};
    my $lead=$report->{'lead'};
    if (!defined($lead)) {
        $lead = " ";
    }
    my $flat = (defined($output) && $output =~ /^flat/);
    
    my ($complete, $penetration, $pass, $fail) = (0, 0, 0, 0);
    my ($num_cases, $agg_complete, $agg_penetration, $agg_pass, $agg_fail) = (0, 0, 0);
    my ($num_children) = 0;
    
    my $printIt=0;
    if (exists ($SUITE_ACTIONS{'ALL'})) {
        $printIt=1;
    }
    
    my $file=undef;
    if ($printIt) {
        #print (Dumper($report));
        $file = $OUTPUTDIR . "/report." . to_filename ($report->{'name'}) . ".tmp.html";
        if (!$flat) {
            open ($output, ">$file") or die "unable to open $file\n";
            $testreport=$output;
            print ($testreport "<html>\n");
            print ($testreport "<head>\n");
            print ($testreport "<title> $report->{'name'} Test Coverage</title>\n");
            print ($testreport "</head>\n");
        }
        
        my $timestamp = localtime();
        # This is a hack only for fname, used only for web access.
        if (!$flat) {
            print ($testreport "<body>\n");
            print ($testreport "<p><small><i>Last updated ${timestamp}</i></small>\t\n");
            print ($testreport "<h2>Test Penetration Report: $report->{'name'}</h2>\n");
            print ($testreport "<table>\n");
            print ($testreport "<tr bgcolor=\"lightblue\">\n");
            print ($testreport "<td><strong><small></strong></small></td>");
            print ($testreport "<td><strong><small># Cases</strong></small></td>");
            print ($testreport "<td><strong><small>% Complete</strong></small></td>");
            print ($testreport "<td><strong><small>% Penetration</strong></small></td>");
            print ($testreport "<td><strong><small>% Pass</strong></small></td>");
            print ($testreport "<td><strong><small>% Fail</strong></small></td>");
            print ($testreport "<tr>\n");
        }
        else {
            print ($testreport "<tr bgcolor=\"lightgray\">\n");
            print ($testreport "<td><strong><small>${report_name}</strong></small></td>");
            print ($testreport "<td><strong><small></strong></small></td>");
            print ($testreport "<td><strong><small></strong></small></td>");
            print ($testreport "<td><strong><small></strong></small></td>");
            print ($testreport "<td><strong><small></strong></small></td>");
            print ($testreport "<td><strong><small></strong></small></td>");
            print ($testreport "<tr>\n");
        }
    }
    
    my $color = 0;
    if (exists ($report->{'suite'})) {
        my $report_suites = $report->{'suite'};
        foreach my $report_suite (@$report_suites) {
            $num_children++;
            my ($suite_cases, $suite_complete, $suite_penetration, $suite_pass, $suite_fail, $lead) = handleSuite ($report_suite);
            $num_cases += $suite_cases;
            $complete += ($suite_complete/100);
            $penetration += ($suite_penetration/100);
            $pass += (($suite_pass/100) * ($suite_penetration/100));
            $fail += (($suite_fail/100) * ($suite_penetration/100));
            if ($printIt) {
                if ($color == 0) {
                    print($testreport "<tr bgcolor=\"${alt_one}\">\n");
                    $color++;
                } 
                else {
                    print($testreport "<tr bgcolor=\"${alt_two}\">\n");
                    $color = 0;
                }
                #my $href = $HTML_ROOT . "/suite." . $report_suite->{'name'} . ".html";
                my $href = "suite." . to_filename($report_suite->{'name'}) . ".html";
                
                print ($testreport "<td><small> <a href=$href> $report_suite->{'name'} </a></td></small>");
                print ($testreport "<td><small>${suite_cases}</td></small>\n");
                print ($testreport "<td><small>${suite_complete}</td></small>\n");
                print ($testreport "<td><small>${suite_penetration}</td></small>\n");
                print ($testreport "<td><small>${suite_pass}</td></small>\n");
                print ($testreport "<td><small>${suite_fail}</td></small>\n");
                print ($testreport "<td><small>${lead}</td></small>\n");
            }
        }
    }
    
    if (exists ($report->{'report'})) {
        my $sub_reports = $report->{'report'};
        foreach my $sub_report (@$sub_reports) {
            $num_children++;
            my ($report_cases, $report_complete, $report_penetration, $report_pass, $report_fail, $lead) = handleReport ($sub_report, $testreport);
            my $suboutput = $sub_report->{'output'};
            my $subflat = (defined($suboutput) && $suboutput =~ /flat/);
            $num_cases += $report_cases;
            $complete += ($report_complete/100);
            $penetration += ($report_penetration/100);
            $pass += (($report_pass/100) * ($report_penetration/100));
            $fail += (($report_fail/100) * ($report_penetration/100));
            if ($printIt && !$subflat) {
                if ($color == 0) {
                    print($testreport "<tr bgcolor=\"${alt_one}\">\n");
                    $color++;
                } 
                else {
                    print($testreport "<tr bgcolor=\"${alt_two}\">\n");
                    $color = 0;
                }
                my $href =  "report." . to_filename($sub_report->{'name'}) . ".html";
                
                print  ($testreport "<td><small> <a href=$href> $sub_report->{'name'} </a></td></small>");
                print ($testreport "<td><small>${report_cases}</td></small>\n");
                print ($testreport "<td><small>${report_complete}</td></small>\n");
                print ($testreport "<td><small>${report_penetration}</td></small>\n");
                print ($testreport "<td><small>${report_pass}</td></small>\n");
                print ($testreport "<td><small>${report_fail}</td></small>\n");
                print ($testreport "<td><small>${lead}</td></small>\n");
            }
        }
    }
    
    $agg_complete = $num_children == 0 ? 0 : roundup(POSIX::floor (100*$complete/$num_children), $complete);
    $agg_penetration = $num_children == 0 ? 0 : roundup(POSIX::floor (100*$penetration/$num_children), $penetration);
    $agg_pass = ($penetration == 0) ? 0 : roundup(POSIX::floor ($pass*100/$penetration), $pass);
    $agg_fail = ($agg_penetration == 0 ) ? 0 : 100 - $agg_pass;
    
    if ($printIt) {
        print($testreport "<tr bgcolor=\"lightgray\">\n");
        if (!$flat) {
            print($testreport "<td><small><strong>Totals</strong></small></td>");
        }
        else {
            print($testreport "<td><small><strong></strong></small></td>");
        }
        print($testreport "<td><small><strong>${num_cases}</strong></small></td>");
        print($testreport "<td><small><strong>${agg_complete}</strong></small></td>");
        print($testreport "<td><small><strong>${agg_penetration}</strong></small></td>");
        print($testreport "<td><small><strong>${agg_pass}</strong></small></td>");
        print($testreport "<td><small><strong>${agg_fail}</strong></small></td>");
        print($testreport "</tr>\n");

        if (!$flat) {
            print($testreport "</tr>\n");
            print($testreport "</table>\n");
            print($testreport "</body>\n");
            print($testreport "</html>\n");
        
            close ($testreport) or die "unable to close $file: $!\n";
        
            # rename the tmp file
            my $oldfname = "report.".to_filename($report->{'name'}).".tmp.html";
            my $oldfile = $OUTPUTDIR."/".$oldfname;
            my $newfname = "report.".to_filename($report->{'name'}).".html";
            my $newfile = $OUTPUTDIR."/".$newfname;
            rename ($oldfile, $newfile) or die "unable to rename $oldfile to $newfile\n";
            my $mode = 0664; 
            chmod ($mode, $newfile); 
        }
    }
    
    return ($num_cases, $agg_complete, $agg_penetration, $agg_pass, $agg_fail, $lead);
} # handleReport #

sub handleReports
{
  my ($reports) = @_;
  #print("handling reports\n");
  #print(Dumper($reports));
  
  if (exists ($reports->{'suitedef'})) {
    handleSuitedefs($reports->{suitedef});
  }

  # Only generate Report if -H ALL is specified
  #if (exists ($SUITE_ACTIONS{'ALL'}) && exists ($reports->{'report'})) {
  if (exists ($reports->{'report'})) {
    my $reports = $reports->{'report'};
    foreach my $report (@$reports) {
      my $color = 0;
      handleReport ($report, undef);
    }
  }
}

sub handleReportFile
{
    my ($file) = @_;
    my $root = XMLin($REPORTSFILE, 
		     KeepRoot => 1,
		     ForceArray => 1,
		     ForceContent => 1,
		     ContentKey => $CONTENTKEY,
		     KeyAttr => [],
		     NormaliseSpace => 2); 
    #print(Dumper($root));
    handleReports(@{$root->{reports}}[0]);
}

sub suite
{
    my $usage =
<<EOF
NAME

    $0 suite - manage report suites

SYNOPSIS

    $0 suite [OPTIONS]

OPTIONS

    -f, --file <reports file>
        The file to read the suite specs from.
        By default, ./qbreports.xml is used.

    -H, --html <suite name>
        Generate html for this suite.
	Multiple -H options can be specified.
	\"ALL\" may be specified.

    -d, --dir <output dir>
        Place output files in this directory.
        By default, ./${0}.out/ is used.

    -h, --help
        Print this message.

EOF
;
    
    my $file = undef;
    my $dir = undef;
    my @html = ();
    my $help = undef;
    
    if (!GetOptions("f|file=s" => \$file,
		    "d|dir:s" => \$dir,
		    "H|html:s" => \@html,
		    "h|help" => \$help))
    {
	print "${usage}\n";
	exit 1;
    }
    
    if (defined($help))
    {
      HELP:
	print "${usage}\n";
	exit 0;
    }
 
    if (defined($file))
    {
	$REPORTSFILE = $file;
    }

    if (defined($dir))
    {
	$OUTPUTDIR = $dir;
    }
    system("mkdir -p $OUTPUTDIR");
    my $rc = ($? >> 8);
    if ($rc != 0) {
	print("Unable to mkdir ${OUTPUTDIR}\n");
	exit($rc);
    }
 
    if (scalar(@html) > 0) {
	foreach my $html (@html) {
	    if (defined($SUITE_ACTIONS{$html})) {
		my @actions = $SUITE_ACTIONS{$html};
		push(@actions, "html");
	    } else {
		$SUITE_ACTIONS{$html} = ("html");
	    }
	}
    }

    handleReportFile($file);

    # This is hack as apache creates files with owner:nobody and group: build
    # For the current apache configuration group gets read-only permission
    # Because of this qb_report.pl -H quits as its unable to open html files.
}



#my @dockeys = keys %$doc;
#my $class = $dockeys[0];
#my $data = @{$doc->{$class}}[0];
#
#handleReports($data);
#handleElement($class, $data);
#db_commit();

#my $xml = XMLout($doc, KeyAttr => []);
#print("${xml}\n");

################################################################################
# main
################################################################################

my $usage =
<<EOF
NAME

    $0 - run the qb report command line utility

SYNOPSIS

    $0 <cmd>

DESCRIPTION

    the qb report utility allows you to manage qb reports.
    valid <cmd> arguments are listed below.  issuing `$0 <cmd> --help` will
    provide detailed help on each command.

    suite
        manage suite definitions
EOF
;

my $cmd = undef;
$cmd = shift(@ARGV);

if (!defined($cmd))
{
  print $usage;
  exit 1;
}

if ($cmd =~ /suite/)
{
  suite();
}
else
{
  print $usage;
  exit 1;
}


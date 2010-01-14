#!/usr/bin/perl -w
#
# $Id: qb_testcase_tags.pl 10856 2007-05-19 02:58:52Z bberndt $
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

#
# This was a special 1-time script used to seed the tuples in 
# the testcase_tag table.
# It was run like this...
# ./qb_testcase_tags.sh suite -L ALL
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
my $CGI_BIN = "http://hc-web/cgi-bin/qb";
my $HTML_ROOT = "http://hc-web/qb/reports";
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
    return "'${name}'";
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
    my $taglist = undef;
    #print("handling tagspec\n");
    #print(Dumper($tagspec));

    if (defined($tagspec)) {
	my $tagnames = handleTags(@{$tagspec}[0]);
	if (defined($tagnames)) {
            $taglist = "(" . join(", ", @$tagnames) . ")";
        }
    }
    return $taglist;
}

sub handleTestcase
{
    my ($testcaseid) = @_;

    # select the latest result (max ID), its status and timestamp
    my $resultselect = "SELECT status, id, end_time FROM result\n";
    $resultselect .= "WHERE testcase = ${testcaseid}\n";
    $resultselect .= "ORDER BY id DESC LIMIT 1 \n";
    my $resulttuples = db_select_arrayref($resultselect);

    return @{$resulttuples->[0]};
}

sub handleTestproc
{
    my ($suitename, $testprocid, $testcases) = @_;
    my $testprocname = undef;

    my $procselect = "select name\n";
    $procselect .= "from testproc\n";
    $procselect .= "where id = ${testprocid}\n";
    my $proctuples = db_select_arrayref($procselect);
    foreach my $proctuple (@$proctuples) {
	($testprocname) = @$proctuple;
	last;
    }

    my $fname = "testproc.".to_filename($testprocname).".html";
    my $file = ${OUTPUTDIR}."/".${fname};
    open(TESTPROC, ">${file}") or die "unable to open ${file}\n";
    print("Testproc file: ${fname}\n");
    
    print(TESTPROC "<html>\n");
    print(TESTPROC "<head>\n");
    print(TESTPROC "<title>QB REPORT: ${testprocname}</title>\n");
    print(TESTPROC "</head>\n");
   
    my $timestamp = localtime();
    my $update_link = "${QB_UPDATE}&target=${suitename}&page=${HTML_ROOT}/${fname}";
    print(TESTPROC "<body>\n");
    print(TESTPROC "<p><small><i>Last updated ${timestamp}</i>\t\n");
    print(TESTPROC "<b><a href=\"${update_link}&action=-H\" title=\"${update_text}\">[Update]</a>\t\n");
    print(TESTPROC "<b><a href=\"${update_link}&action=-L\" title=\"${reload_text}\">[Reload]</a>");
    print(TESTPROC "</b></small></p>\n");
    print(TESTPROC "<h2>Test Procedure: ${testprocname}</h2>\n");

    print(TESTPROC "<table>\n");
    print(TESTPROC "<tr bgcolor=\"lightgray\">\n");
    print(TESTPROC "<td><strong><small>Test Case</strong></small></td>");
    print(TESTPROC "<td><strong><small>Status</strong></small></td>");
    print(TESTPROC "<td><strong><small>Last Posted</strong></small></td>");
    print(TESTPROC "<td><strong><small>History</strong></small></td>");
    print(TESTPROC "<tr>\n");

    my ($pass, $fail, $notrun) = (0, 0, 0);
    my $color = 0;
    foreach my $testcase (@$testcases) {
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
	print(TESTPROC "${parameters}");
	print(TESTPROC "</small></td>");

	print(TESTPROC "<td><small>");
	my ($status, $result_id, $result_time) = handleTestcase($testcase);
	print(TESTPROC "<a href=\"${QB_RESULT}${result_id}\">${status}</a>");
	print(TESTPROC "</small></td>");

        print(TESTPROC "<td><small>");
        $result_time = "" unless (defined $result_time);
        print(TESTPROC "${result_time}");
        print(TESTPROC "</small></td>");

        print(TESTPROC "<td><small>");
        print(TESTPROC "<a href=\"${QB_HISTORY}${testcase}\">History</a>");
        print(TESTPROC "</small></td>");

	if ($status =~ /pass/) {
	    $pass++;
	} elsif ($status =~ /fail/) {
	    $fail++;
	} else {
	    $notrun++;
	}
    }

    print(TESTPROC "</tr>\n");
    print(TESTPROC "</table>\n");
    print(TESTPROC "</body>\n");
    print(TESTPROC "</html>\n");

    close(TESTPROC) or die "unable to close ${file}\n";

    return ($pass, $fail, $notrun);
}

sub handleSuitedef
{
    my ($suitedef) = @_;
    #print(Dumper($suitedef));

    my $name = $suitedef->{name};
    print("handling suitedef: ${name}\n");
    my ($pass, $fail, $notrun) = (0, 0, 0);

    my @actions = ();
    if (defined($SUITE_ACTIONS{$name})) {
	@actions = $SUITE_ACTIONS{$name};
    } elsif (defined($SUITE_ACTIONS{ALL})) {
	@actions = $SUITE_ACTIONS{ALL};
    }

    foreach my $action (@actions) {
	if ($action =~ /^load$/) {
	    print("loading suite def: ${name}...\n");
	    $SUITEDEFS{$name} = ();
	    
	    #my $suite_id = insert_db_entry("suite", { name => $name });
	    
	    # select distinct testproc.id, testcase.id
	    # from result, testcase, testproc 
	    # where 
	    # result.testcase = testcase.id and 
	    # testcase.testproc = testproc.id and 
	    # exists 
	    #  (select * from result_tag, tag 
	    #   where result.id = result_tag.result and 
	    #         result_tag.tag = tag.id and tag.name = 'cmm') 
	    # order by testcase.testproc;
	    
	    my $suiteinsert = "insert into testcase_tag (testcase, tag)\n";
	    $suiteinsert .= "select distinct\n";
	    $suiteinsert .= "  result.testcase, result_tag.tag\n";
	    $suiteinsert .= "from\n";
	    $suiteinsert .= "  result, result_tag, tag\n";
	    $suiteinsert .= "where\n";
            $suiteinsert .= "  result.id = result_tag.result\n";
            $suiteinsert .= "  and result_tag.tag = tag.id\n";
	    
	    my $start_date = $suitedef->{start_date};
	    if (defined($start_date)) {
		$suiteinsert .= "  and result.start_time >= STR_TO_DATE('$start_date', '%m/%d/%Y %H:%i:%s')\n";
	    }
	    my $end_date = $suitedef->{end_date};
	    if (defined($end_date)) {
		$suiteinsert .= "  and result.end_time <= STR_TO_DATE('$end_date', '%m/%d/%Y %H:%i:%s')\n";
	    }
	    
	    my $taglist = handleTagspec($suitedef->{tagspec});
            if (defined($taglist)) {
                $suiteinsert .= " and tag.name in ${taglist}\n";
            }
	    #if (defined($tagspec)) {
		#$suiteinsert .= "  and\n";
		#$suiteinsert .= "${tagspec}";
	    #} else {
                #print("No tags for suite $suitedef ?? \n");
            #}

            $suiteinsert .= "   and not exists (select 1 from testcase_tag where testcase = result.testcase and tag = result_tag.tag)";
	    
	    db_do($suiteinsert);
            print("${suiteinsert}\n");
            #print("${taglist}\n");
	    db_commit();
	    
	} elsif ($action =~ /^html/) {
	    print("generating html for suite: ${name}...\n");
            
            my $fname = "suite.".to_filename($name).".html";
	    my $file = $OUTPUTDIR."/".$fname;
            open(SUITE, ">${file}") or die "unable to open ${file}\n";
            print("Suite file: ${fname}\n");
	    
	    print(SUITE "<html>\n");
            print(SUITE "<head>\n");
            print(SUITE "<title>QB REPORT: ${name}</title>\n");
            print(SUITE "</head>\n");
            
            my $timestamp = localtime();
            my $update_link = "${QB_UPDATE}&target=${name}&page=${HTML_ROOT}/${fname}";
            print(SUITE "<body>\n");
            print(SUITE "<p><small><i>Last updated ${timestamp}</i>\t\n");
            print(SUITE "<b><a href=\"${update_link}&action=-H\" title=\"${update_text}\">[Update]</a>\t\n");
            print(SUITE "<a href=\"${update_link}&action=-L\" title=\"${reload_text}\">[Reload]</a>");
            print(SUITE "</b></small></p>\n");
	    print(SUITE "<h2>Suite: ${name}</h2>\n");
           
            print(SUITE "<p><small><strong>Tags:</strong>\n");
            if (defined $suitedef->{tagspec}) {
                my $tagnames = handleTags(@{$suitedef->{tagspec}}[0]);
                my $taglist = join("] [", @$tagnames);
                print(SUITE "[$taglist]</small></p>\n");
            } else {
                print(SUITE "None. Suite definition is incomplete.</small></p>\n");
            }

            print(SUITE "<p><small><strong>Comments:</strong>\n");
            if (defined $suitedef->{comments}) {
                print(SUITE "$suitedef->{comments}</small></p>\n");
            } else {
                print(SUITE "No comments. Ask the author to add some.</small></p>\n");
            }
            
	    print(SUITE "<table>\n");
	    print(SUITE "<tr bgcolor=\"lightgray\">\n");
	    print(SUITE "<td><small><strong>Test Case</strong></small></td>");
	    print(SUITE "<td><small><strong>% Run</strong></small></td>");
            print(SUITE "<td><small><strong>Total</strong></small></td>");
	    print(SUITE "<td><small><strong>Pass</strong></small></td>");
	    print(SUITE "<td><small><strong>Fail</strong></small></td>");
	    print(SUITE "<td><small><strong>Not Run</strong></small></td>");
	    print(SUITE "</tr>\n");
	    
	    my $suiteselect = "select distinct\n";
	    $suiteselect .= "  testcase.testproc, testcase.id, testproc.name\n";
	    $suiteselect .= "from\n";
	    $suiteselect .= "  suite, suite_testcase, testcase, testproc\n";
	    $suiteselect .= "where\n";
	    $suiteselect .= "  suite.name = '${name}'\n";
	    $suiteselect .= "  and suite.id = suite_testcase.suite\n";
	    $suiteselect .= "  and suite_testcase.testcase = testcase.id\n";
	    $suiteselect .= "  and testcase.testproc = testproc.id\n";
	    $suiteselect .= "order by testproc.name asc\n";
	    
	    my $testcases = db_select_arrayref($suiteselect);
	    my @proctestcases = ();
	    my ($currentproc, $curprocname) = (undef, undef);
	    my ($testprocid, $testcaseid, $testprocname) = (undef, undef, undef);
	    my $color = 0;
            my $numtestcases = $#{@$testcases}; # index of last element

          TESTCASE: for (my $i = 0; $i <= $numtestcases; $i++) {
                my $testcase = $testcases->[$i];
		($testprocid, $testcaseid, $testprocname) = @$testcase;
		if (!defined($currentproc)) {
                    # This is the very first testcase: start grouping.
		    ($currentproc, $curprocname) = ($testprocid, $testprocname);
		    push(@proctestcases, $testcaseid);
		    if ($i < $numtestcases) {
                        next TESTCASE;
                    } # else this is the last testcase - print entry.
		} 
                elsif ($currentproc == $testprocid) {
                    # This testcase belongs to the same testproc, keep processing.
		    if ($i < $numtestcases) { 
                        push(@proctestcases, $testcaseid);
                        next TESTCASE;
                    } # else this is the last testcase - print entry.
                }
		else { # this is a new testproc
		    if ($i == $numtestcases) { # and it's the very last testcase
			$i--; # hack to make the loop execute one more time
		    } # on next iteration, this testproc entry will be printed.
		}
                #
                # This is a new testproc, or the very last testcase: print entry.
                #
                my ($_pass, $_fail, $_notrun) = handleTestproc($name, $currentproc, \@proctestcases);
                my $_total = $_pass + $_fail + $_notrun;
                my $percent = POSIX::floor (100 * ($_pass + $_fail) / $_total);
                if ($color == 0) {
                    print(SUITE "<tr bgcolor=\"${alt_one}\">\n");
                    $color++;
                } else {
                    print(SUITE "<tr bgcolor=\"${alt_two}\">\n");
                    $color = 0;
                }
                my $href = $curprocname;
                $href =~ s/:/_/g;
                print(SUITE "<td><small><a href=\"testproc.${href}.html\">${curprocname}</small></a></td>");
                print(SUITE "<td><small>${percent}%</small></td>");
                print(SUITE "<td><small>${_total}</small></td>");
                print(SUITE "<td><small>${_pass}</small></td>");
                print(SUITE "<td><small>${_fail}</small></td>");
                print(SUITE "<td><small>${_notrun}</small></td>");
                print(SUITE "</tr>\n");
                $pass += $_pass;
                $fail += $_fail;
                $notrun += $_notrun;
                @proctestcases = ();
                ($currentproc, $curprocname) = ($testprocid, $testprocname);
                push(@proctestcases, $testcaseid);
            }
	    
	    print(SUITE "</tr>\n");
	    print(SUITE "</table>\n");
	    print(SUITE "</body>\n");
	    print(SUITE "</html>\n");
	    
	    close(SUITE) or die "unable to close ${file}\n";
	}
    }
    return ($pass, $fail, $notrun);
}
    
sub handleSuitedefs
{
    my ($suitedefs) = @_;
    #print("handling suitedefs\n");
    #print(Dumper($suitedefs));
    foreach my $suitedef (@$suitedefs) {
	handleSuitedef($suitedef);
    }
}

sub handleReport
{
  my ($class, $data) = @_;
  #print("handling reports: ${class}\n");
}

sub handleReports
{
  my ($reports) = @_;
  #print("handling reports\n");
  #print(Dumper($reports));
  handleSuitedefs($reports->{suitedef});
}

sub handleReportFile
{
    my ($file) = @_;
    my $root = XMLin("qbreports.xml", 
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

    -L, --load <suite name>
        Load the suite from the specified file into qb db.
	Multiple -L options can be specified.
	\"ALL\" may be specified.

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
    my @load = ();
    my @html = ();
    my $help = undef;
    
    if (!GetOptions("f|file=s" => \$file,
		    "d|dir:s" => \$dir,
		    "L|load:s" => \@load,
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

    if (scalar(@load) > 0) {
	foreach my $load (@load) {
	    if (defined($SUITE_ACTIONS{$load})) {
		my @actions = $SUITE_ACTIONS{$load};
		push(@actions, "load");
	    } else {
		$SUITE_ACTIONS{$load} = ("load");
	    }
	}
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


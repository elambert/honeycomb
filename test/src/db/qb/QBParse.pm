#!/usr/bin/perl -w

#
# $Id: QBParse.pm 10856 2007-05-19 02:58:52Z bberndt $
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

############################################
# Parser for QB CLI and Web interface
#
# Author: Daria Mehra
############################################

use strict;
use Tie::IxHash;
require Exporter;

our @ISA = qw(Exporter);
our @EXPORT_OK = qw( 
		     parse_run
		     parse_result
		     parse_metric
		     parse_bug
		     parse_build
                     parse_tag
		     
		     dump_run
		     dump_result
		     dump_metric
		     dump_bug
		     dump_build
                     dump_tag

		     parse_query_results
		     label_results
		     list_results
                     result_details
                     result_bugs
                     result_tags
		    );

####################################
# INPUT PARSING FOR DATA RECORDING #
####################################

# This parser takes a filehandle, reads the input,
# and transforms it into a hash expected by QBDB::post_ subs.
# Input format is (without the # signs):
#
# QB.<keyword>: value
# value can continue multi-line
# QB.<another-keyword>: another value
#
# Empty lines and commented-out lines (starting with #) are ignored.
# Keywords with empty values are also ignored.
#
# Example for test result:
#
# QB.testproc = DeleteEMD
# QB.parameters = refcount=5 filesize=1M
# QB.status = pass
# QB.timestamp = 2005-01-24 09:04:00
#
# Keywords differ depending on what is being parsed:
# a test run, result, metric, or bugs.
# See following templates for list of keywords and sample values.
# Call dump_ subs to print out each template.

our (%run_tmpl, %result_tmpl, %metric_tmpl, %bug_tmpl, %build_tmpl, %tag_tmpl);

our %template = ( run => \%run_tmpl,
		  result => \%result_tmpl,
		  metric => \%metric_tmpl,
		  bug => \%bug_tmpl,
		  build => \%build_tmpl,
                  tag => \%tag_tmpl,
		  );

# to retrieve keys in insertion order in dump_ subs
foreach my $table (keys %template) {
    no strict 'refs';
    my $tmpl = $table."_tmpl";
    tie %$tmpl, "Tie::IxHash";
}

%run_tmpl = ( 
	      id => "run id - if not set, insert new run",
	      command => "/foo/bar -duh",
	      bed => "some testbed name",
	      tester => "some engineer name",
	      logs_url => "http://here/are/run/logs",
	      logtag => "string to grep for in the logs",
	      start_time => "YYYY-MM-DD HH:MM:SS",
	      end_time => "YYYY-MM-DD HH:MM:SS",
	      exitcode => 25,
	      env => "describe run environment here",
	      comments => "comment on this run"
	      );

%result_tmpl = ( 
		 id => "result id - if not set, insert new result",
		 testproc => "some test procedure name",
		 parameters => "parameters of this test",
		 run => "run id, to reference a test run",
		 start_time => "YYYY-MM-DD HH:MM:SS",
		 end_time => "YYYY-MM-DD HH:MM:SS",
		 status => "running|pass|fail|skipped|unknown",
		 buglist => "space-separated Bugster IDs",
		 taglist => "space-separated test tags",
		 build => "build nametag",
		 submitter => "some engineer name",
		 logs_url => "http://here/are/test/logs",
		 notes => "comment on this result"
		 );

%metric_tmpl = (
                id => "metric id - if not set, insert new metric",
		result => "result id, to reference a test result",
                mgroup => "metric group id - if not set, metric is standalone",
		name => "metric name",
		units => "measurement units for this metric",
		datatype => "int|double|string|time",
		value => "data point",
		at_time => "YYYY-MM-DD HH:MM:SS (defaults to now)"
		);

%bug_tmpl = (
	     result => "result id, to reference a test result",
	     bug => "bug nametag, eg Bugster ID",
	     notes => "optional notes on how the bug relates to the result"
	     );

%build_tmpl = (
	       name => "build nametag like honeycomb-bin.12345-trunk.tar.gz",
	       revnum => "Latest subversion revision number",
	       revtime => "Timestamp of latest svn revision: YYYY-MM-DD HH:MM:SS",
	       branch => "Subversion branch name like trunk or solaris",
	       timestamp => "When build was created: YYYY-MM-DD HH:MM:SS",
	       description => "comments about this build"
	       );

%tag_tmpl = (
             name => "group nametag of the testcase, eg: regression, experimental, quick, ...",
             result => "result id, to reference a test result to which this tag applied",
             notes => "optional notes on why the test was tagged"
             );

# common wrapper to exit on failure
#
sub parse_fail
{
    my ($msg) = @_;
    die "[" . localtime(time) . "] PARSE ERROR: " . $msg;
    # Note: it's OK to die in CGI context because cgi script traps
}

# Public API: print template of expected input format
#
sub dump_run { return dump_template("run"); }
sub dump_result { return dump_template("result"); }
sub dump_metric { return dump_template("metric"); }
sub dump_bug { return dump_template("bug"); }
sub dump_build { return dump_template("build"); }
sub dump_tag { return dump_template("tag"); }

sub dump_template
{
    my ($table) = @_; # which template to print out
    my $tmpl = $template{$table};
    foreach my $keyword (keys %$tmpl) {
	printf "%-14s %s %s", "QB.".$keyword, ":", $tmpl->{$keyword}."\n"; 
    }
}

# Public API: parse input with keyword:value pairs 
# for posting of test run, result, metric, bug

sub parse_run 
{ 
    my $run = parse_input("run", @_); 
    unless (defined $run->{id} or defined $run->{command}) {
	parse_fail("Missing both run id and command");
    }
    return $run;
}

sub parse_result 
{ 
    my $result = parse_input("result", @_); 
    unless (defined $result->{id} or
	    (defined $result->{testproc} and defined $result->{parameters})) {
	parse_fail("Missing both result id and testproc+parameters");
    }
    return $result;
}

sub parse_metric 
{ 
    my $metric =  parse_input("metric", @_); 
    unless (defined $metric->{result} and defined $metric->{name} and
	    defined $metric->{units} and defined $metric->{datatype} and
	    defined $metric->{value}) {
	parse_fail("Missing some required property: result id, metric name, units, datatype, value");
    }
    if (defined $metric->{id} and !defined $metric->{mgroup}) {
        parse_fail("Metric ID is present, must be for group ID update, but group ID is missing");
    }
    return $metric;
}

sub parse_bug 
{ 
    my $bug = parse_input("bug", @_); 
    unless (defined $bug->{result} and defined $bug->{bug}) {
	parse_fail("Missing some required property: result id, bug nametag");
    } # notes are optional
    return $bug;
}

sub parse_build
{
    my $build = parse_input("build", @_);
    unless (defined $build->{id} or defined $build->{name}) {
	parse_fail("Missing both build id and name");
    }
    return $build;
}

sub parse_tag
{
    my $tag = parse_input("tag", @_);
    unless (defined $tag->{name} and defined $tag->{result}) {
        parse_fail("Missing some required property: result id, tag name");
    } # notes are optional
    return $tag;
}

# Take template and input filehandle, return hash ref to use in QBDB 
#
sub parse_input
{
    my ($table, $fh) = @_; # template to use, and input filehandle
    my %data; # keyword => value pairs
    my ($key, $val); # support for multi-line values
    my $lines = 0; # recognize empty input as an error
    # needed because CGI file upload turns non-existing files into empty input

    while (<$fh>) {
	next if ($_ =~ /^\s*$/); # skip empty lines
	next if ($_ =~ /^\#/); # skip commented-out lines
	$lines++;

	if ($_ =~ /^QB\.(.*?)\s*:\s*(.*)$/) {
	    ($key, $val) = ($1, $2); # new parameter
	    if (defined $template{$table}{$key}) {
		$data{$key} = "$val" unless ($val =~ /^\s*$/);
	    } else {
		parse_fail("Not a $table keyword: $key");
	    }
	} elsif (defined $key) {
	    $data{$key} .= " $_"; # value continues
	} else {
	    parse_fail("Missing QB.<keyword>: $_");
	}
    }
    parse_fail("Missing or empty input") unless $lines;

    use Scalar::Util qw (looks_like_number);
    if (defined $data{id} && !looks_like_number($data{id})) {
	parse_fail("Non-numeric ID = $data{id}");
    }
    return \%data;
}

#######################################
# QUERY PARSING FOR REPORT GENERATION #
#######################################

# These hashes correlate DB fields to report labels

our (%result_report, %run_report, %metric_report, %bug_report, %tag_report);

tie %result_report, "Tie::IxHash";
tie %run_report, "Tie::IxHash";
tie %metric_report, "Tie::IxHash";
tie %bug_report, "Tie::IxHash";
tie %tag_report, "Tie::IxHash";


%result_report = ( 'result.id' => 'ID',
		   'testproc.id' => 0,
                   'testcase.id' => 0, 
		   'testproc.name' => 'Test Procedure',
		   'testcase.parameters' => 'Parameters',
		   'result.run' => 'Run ID',
                   'run.command' => 'Command Line',
		   'result.start_time' => 'Start Time',
		   'result.end_time' => 'End Time',
		   'result.status' => 'Status',
		   'build.name' => 'Build',
		   'tester.name' => 'Tester',
		   'result.logs_url' => 'Logs',
                   'result.notes' => 'Notes'
		   );

%bug_report =    ( 'result_bug.bug' => 'Bugster ID',
                   'result_bug.notes' => 'Reason'
                   );

%tag_report =    ( 'tag.name' => 'Tag',
                   'result_tag.notes' => 'Comments'
                  );

%metric_report = ( 'result_metric.id' => 0, # do not display
                   'metric.name' => 'Metric',
                   'result_metric.value' => 'Value',
                   'metric.units' => 'Units',
                   'result_metric.at_time' => 'At Time'
                  );
                   

# validate user-supplied query conditions
# takes a hash ref to key=value params, returns same type
#
sub parse_query_results
{
    my ($conds) = @_; # hash ref to user-specified query conditions
    my %where = ();

    foreach my $field (keys %$conds) {
	if (defined $conds->{$field} && $conds->{$field} && 
	    defined $result_report{$field}) { # valid condition	
	    $where{$field} = $conds->{$field};
	}
    }
    
    # special time condition: result over last X time units
    my $elapsed = $conds->{'time_period'};
    my $units = $conds->{'time_units'};
    if (defined $elapsed && $elapsed && defined $units && $units) {
	$where{'result.start_time'} = over_time_period($elapsed, $units);
    } # translates into start_time > Y
    
    return \%where;
}

# no parsing is needed for 'result details' query
# because the select is simply made on result.id=<num>

# what DB fields appear in result report
# this is a short description of a test result, details have more
#
sub list_results
{
    my @fields = ();
    foreach my $field (keys %result_report) {
        # skip info that's too detailed
        next if ($field eq 'run.command');
        next if ($field eq 'result.notes');
        push @fields, $field;
    }
    return @fields;
}

# what DB fields describe a given test result
# more detailed than info in list_results
#
sub result_details {
    return (keys %result_report);
}

# describe test result-related bug
sub result_bugs {
    return (keys %bug_report);
}

# describe tags which apply to this test result
sub result_tags {
    return (keys %tag_report);
}

# describe metrics collected for this test result
sub result_metrics {
    return (keys %metric_report);
}

# given DB field names, return human-readable labels
# used to display report table headers
#
sub label_fields
{
    my ($hash, $fields) = @_; 
    # hash name, array ref to set of fields describing results
    no strict 'refs';
    my %report = %$hash;
    my @labels = ();

    foreach my $field (@$fields) {
        my $header = $report{$field};
	if (!defined $header) {
	    push @labels, $field; # no special label 
	} elsif ($header) {
	    push @labels, $header;
	} else {
           # note: header==0 means don't print at all
	}
    }
    return \@labels;
}

# helper sub to calculate starting timestamp 
# given how many time periods ago it happened
# e.g. over last 30 minutes, over last 5 days etc.
# returns DB-formatted "YYYY-MM-DD HH:MM:SS"
#
sub over_time_period {
    my ($period, $units) = @_;
    my $seconds_since = 0;
    if ($units eq "minutes") {
	$seconds_since = $period * 60;
    } elsif ($units eq "hours") {
	$seconds_since = $period * 60 * 60;
    } elsif ($units eq "days") {
	$seconds_since = $period * 60 * 60 * 24;
    } else { # error
	$seconds_since = time() - 1; # max int?
    }
    my $start_time = time() - $seconds_since;
    my @t = localtime($start_time);
    my $db_time = ($t[5]+1900)."-".($t[4]+1)."-".$t[3]." ".$t[2].":".$t[1].":".$t[0];
    return $db_time; # YYYY-MM-DD HH:MM:SS
}


1; # end of module

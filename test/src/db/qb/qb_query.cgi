#!/usr/bin/perl -w

#
# $Id: qb_query.cgi 10856 2007-05-19 02:58:52Z bberndt $
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
# CGI interface to query QB database
# 
# Use to obtain HTML reports of test results, runs etc.
#
# Author: Daria Mehra
#############################################

use strict;
use QBParse;
use QBDB;
use CGI;
use CGI::Carp qw( fatalsToBrowser );
use Tie::IxHash;

my @targets = ('runs', 'results', 'metrics', 'result_details');

my $cgi = new CGI();
my %cgi_params = $cgi->Vars;
print $cgi->header;
print $cgi->start_html('QB QUERY');

my $target = $cgi->param('target');
$target = "query" unless defined $target;

foreach (@targets, 'query') {
    if ($target eq $_) {
	my $do_query = "query_".$target;
	no strict 'refs';
	&$do_query();
    }
}

print $cgi->hr;
print $cgi->end_html;

#############################################
our $sp = '&nbsp;&nbsp;';

# display Web form with query options
sub query_query {
    print $cgi->hr;
    print $cgi->start_form( -method => "GET" );
    print "Query test results using these filters:".$cgi->br;
    print "Result ID and run ID are numeric. "
	. "Owner and test procedure are strings for exact match, or "
	. "%substring% for wildcard search (enclosed in % symbols). "
	. "Start and end time must be in format YYYY-MM-DD HH:MM:SS";
    print $cgi->hr;
    print "Result ID = ".$cgi->textfield( -name => 'result.id', -size => 4);
    print "Run ID = " .$cgi->textfield( -name => 'result.run', -size => 4);
    print "Owner = " .$cgi->textfield( -name => 'tester.name', -size => 20);
    print "Test procedure = ".$cgi->textfield( -name => 'testproc.name', -size => 20);
    print $cgi->hr;
    print "Start time > " .$cgi->textfield( -name => 'result.start_time', -size => 20);
    print "End time < " .$cgi->textfield( -name => 'result.end_time', -size => 20);
    print "OR over last " .$cgi->textfield( -name => 'time_period', -size => 2);
    print $cgi->popup_menu( -name => 'time_units',
				 -values => ['minutes', 'hours', 'days']);

    print $cgi->hidden( -name => 'target', -default => ['results'] );
    print $cgi->submit('QUERY RESULTS');
    print $cgi->end_form;
}


# query test results
sub query_results {

    my @what = list_results(); # what to select from DB
    my $where = parse_query_results(\%cgi_params); # query conditions
    my @raw_data = select_results(\@what, $where);
    my $headers = label_fields('result_report', \@what); # table headings
    push @$headers, 'Details'; # extra column for the link to more info
    my @out_data = $cgi->th($headers);

    foreach my $entry (@raw_data) { # each entry is a row of data
	my @output = pretty_print_result(\@what, $entry, 1); 
        # output has details button for each result row
	push @out_data, $cgi->td(\@output);
    }

    print $cgi->table({-border => undef},
		      $cgi->caption( report_cap('TEST RESULTS REPORT')),
		      $cgi->Tr( \@out_data));

}

# details of a given test result (by ID)
# XXX: if a test posted tons of metrics, this will be very slow
# TODO: add LIMIT clause and cookie support to QBDB::select_db_entries
#
sub query_result_details {
    my @out_data = ();

    my @what = result_details();
    my $id = $cgi_params{'result.id'};
    my @raw_data = select_results(\@what, {'result.id' => $id} );
    # there is only one data row - format the output
    my @result_data = pretty_print_result(\@what, $raw_data[0], 0); 
    my $result_headers = label_fields('result_report', \@what);

    for (my $i=0; $i<@result_data; $i++) {
        push @out_data, $cgi->th($result_headers->[$i]).$cgi->td($result_data[$i]);
    }

    my @bugs = result_bugs();
    my @bug_data = select_bugs(\@bugs, {'result_bug.result' => $id} );
    push @out_data, $cgi->th( {-colspan => '2'}, "RELATED BUGS"); # section header
    push @out_data, $cgi->th( label_fields('bug_report', \@bugs));

    foreach my $entry (@bug_data) {
        my @bug_row = pretty_print_bug(\@bugs, $entry);
        push @out_data, $cgi->td(\@bug_row);
    }

    my @tags = result_tags();
    my @tag_data = select_tags(\@tags, {'result_tag.result' => $id});
    push @out_data, $cgi->th( {-colspan => '2'}, "DECLARED TAGS"); # section header
    push @out_data, $cgi->th( label_fields('tag_report', \@tags));

    foreach my $entry (@tag_data) {
	my @tag_row = pretty_print_tag(\@tags, $entry);
        push @out_data, $cgi->td(\@tag_row);
    }
    
    push @out_data, $cgi->th( {-colspan => '2'}, "COLLECTED METRICS"); # section header    
    my @metrics = result_metrics();
    my $labels = label_fields('metric_report', \@metrics);
    my @revised_labels = pretty_label_metric($labels);
    push @out_data, $cgi->th( \@revised_labels);

    my @metric_data = select_metrics(\@metrics, {'result_metric.result' => $id});
    foreach my $entry (@metric_data) {
        my @metric_row = pretty_print_metric(\@metrics, $entry);
        push @out_data, $cgi->td(\@metric_row);
    }

    print $cgi->table({-border => undef},
		      $cgi->caption( report_cap("TEST RESULT $id")),
		      $cgi->Tr( \@out_data));

    # html tips:
    # $cgi->table({-border => 10, -width => '90%'}, ...);
    # $cgi->td({-colspan => '2', -rowspan => '3'}, ...);
}

# query test runs
sub query_runs {
    print $cgi->p(report_cap("TEST RUNS REPORT: WORK IN PROGRESS"));
}

sub query_metrics {
    print $cgi->p(report_cap("TEST METRICS REPORT: WORK IN PROGRESS"));
}

# helper sub to turn a field name into an URL based on ID
#
sub urlify_name {
    my ($table, $id, $name) = @_;
    my $id_url = $cgi->url()."?target=${table}&${table}.id=${id}";
    my $html = "<a href=\"${id_url}\">${name}</a>";
    return $html;
}

# helper to generate a URL based on a run ID
#
sub urlify_run {
    my ($id) = @_;
    my $html = "";
    if (defined $id) { 
      my $id_url = $cgi->url()."?target=results&result.run=${id}";
      $html = "<a href=\"${id_url}\">${id}</a>";
    }
    return $html;
}

# given the status of a test (pass/fail etc.), 
# return HTML code to display that status in color
#
sub color_status {
    my ($status) = @_;
    my $color = "white";
    $color = "red" if ($status =~ /fail/); 
    $color = "green" if ($status =~ /pass/); 
    $color = "gray" if ($status =~ /skipped/); 
    $color = "orange" if ($status =~ /running/);
    my $html = '<font color="'.$color.'">'.$status.'</color>';
    return $html;
}

# show given text on tooltip for given line, return HTML
#
sub add_tooltip {
    my ($line, $text) = @_;
    my $html = '<a title="'.$text.'">'.$line.'</a>';
    return $html;
}

# output formatting of test tag
#
sub pretty_print_tag {
    my ($fields, $data) = @_;
    my %row;
    array_to_named_hash($fields, $data, \%row);

    # no special formatting is added, but undef values turn to ""
    return (values %row);
}

# output formatting of test result data
# inputs: list of field names, list of data values, boolean details
#
sub pretty_print_result {
    my ($fields, $data, $details) = @_;    
    my %row;
    array_to_named_hash($fields, $data, \%row);

# turn this back on when the URL actually goes somewhere
#	$row{'testproc.name'} = urlify_name('testproc', $row{'testproc.id'}, $row{'testproc.name'});
    delete $row{'testproc.id'} if (defined $row{'testproc.id'}); # will not display
    delete $row{'testcase.id'} if (defined $row{'testcase.id'}); # will not display
    $row{'result.status'} = color_status($row{'result.status'});
    $row{'result.logs_url'} = urlify_logs($row{'result.logs_url'});
    $row{'result.run'} = urlify_run($row{'result.run'});
    $row{'result.notes'} = preserve_spacing($row{'result.notes'}) if (defined $row{'result.notes'});
    if ($details) {
        $row{'details'} = set_details($row{'result.id'});
    }
            
    return (values %row);
}

# display metric value/units/at-time labels inside the righthand wide cell
#
sub pretty_label_metric
{
    my ($labels) = @_; # array ref
    my @revised_labels = ();
    my $metric = shift @$labels;
    push @revised_labels, $metric;
    my $tinytable = $cgi->table( {-border => undef, -width => '99%'},
                                 $cgi->Tr( $cgi->td( {-width => '33%'}, $labels)));
    push @revised_labels, $tinytable;
    return @revised_labels;
}

# output formatting of metric data
#
sub pretty_print_metric
{
    my ($fields, $data) = @_; # array ref to field list and data row    
    my %row;
    array_to_named_hash($fields, $data, \%row);

    delete $row{'result_metric.id'}; # will not display
    $row{'result_metric.at_time'} = convert_db_time($row{'result_metric.at_time'});
    
    # display metric value/units/at-time data inside the righthand wide cell
    my @columns = values %row;
    my @revised_row = ();
    my $metric = shift @columns;
    push @revised_row, $metric;
    my $tinytable = $cgi->table( {-border => undef, -width => '99%'},
                                 $cgi->Tr( $cgi->td( {-width => '33%'},\@columns)));
    push @revised_row, $tinytable;

    return @revised_row;
}

# output formatting of bug data
#
sub pretty_print_bug
{
    my ($fields, $data) = @_; # array ref to field list and data row
    my %row;
    array_to_named_hash($fields, $data, \%row);

    my $bugster_url = "http://bt2ws.central.sun.com/CrPrint?id="; # add bug ID
    my $id = $row{'result_bug.bug'};
    $row{'result_bug.bug'} = "<a href=\"".$bugster_url.$id."\">".$id."</a>";
    return (values %row);
}

# html for a button to see test result's details
#
sub set_details {
    my ($id) = @_;
    # ignore log links for now, they aren't in the DB
    my $html = $cgi->start_form( -method => "GET" );    
    $cgi->param('target', 'result_details'); # overwrite sticky value
    $html .= $cgi->hidden('target');
    $cgi->param('result.id', $id);
    $html .= $cgi->hidden('result.id');
    $html .= $cgi->submit( -name => "${id}_details", -label => "DETAILS OF $id");
    $html .= $cgi->end_form;
    return $html;
}

# turn logs field (relative dir name) into a complete URL, if needed
# expects the log archive to be mounted at <doc-root>/testlogs
#
sub urlify_logs {
    my ($dir) = @_;
    if (!defined $dir || !$dir) { 
        return ""; # log field is optional, can be empty
    }
    my ($linkurl, $linkname);
    if ($dir =~ /^http|^ftp/) {  # full URL provided
        $linkurl = $dir;
        $linkname = "...".substr($dir, -12); # last 12 chars
    } else { # assume it's a run dir name on our logserver
        $linkurl = "/testlogs/".$dir;
        $linkname = "RUN_".$dir;
    }
    return "<a href=\"$linkurl\">$linkname</a>";
}

# transform SQL timestamp value (YYYYMMDDHHMMSS) into human-readable
#
sub convert_db_time
{
    my ($sql_time) = @_;
    if ($sql_time =~ /^(\d\d\d\d)(\d\d)(\d\d)(\d\d)(\d\d)(\d\d)$/) {
        return $1."-".$2."-".$3." ".$4.":".$5.":".$6;
    } else {
        return $sql_time; # couldn't parse
    }
}

# format the report table caption nicely
#
sub report_cap
{
    my ($caption) = @_;
    return $cgi->hr.$cgi->h2($caption).$cgi->hr;
}

# convert two arrays into a hash (used to associate field names and values)
#
# inputs: list of field names, list of data values (both as array ref), hash ref
# output: hash{field}=data with preserved order of fields
#
sub array_to_named_hash {
    my ($fields, $data, $row) = @_; 
    tie %$row, "Tie::IxHash";
    @{%$row}{@$fields} = @$data;
    foreach (keys %$row) { # prevent undef values
	$row->{$_} = "" unless defined $row->{$_};
    }
    return \$row;
}

# Multi-line notes lose their newlines when displayed on the page.
# Replace newlines and spaces with proper HTML to preserve formatting.
#
sub preserve_spacing {
    my ($data) = @_;
    $data =~ s/\n/<br>/g;
    $data =~ s/ /&nbsp\;/g;
    return $data;
}

#!/usr/bin/perl

#
# Flamebox is a reimplementation of Tinderbox, roughly based on 
# Tinderbox 2 code by Ken Estes.
#
# Rewritten by Riverbed Technology, Inc.
# (C) 2003-2004 Riverbed Technology, Inc. All Rights Reserved.
#
# David Wu (davidwu@riverbed.com)
#
# This code contains portions copied from the Tinderbox 2 tool, which
# carries the following license:
#
## The contents of this file are subject to the Mozilla Public
## License Version 1.1 (the "License"); you may not use this file
## except in compliance with the License. You may obtain a copy of
## the License at http://www.mozilla.org/NPL/
##
## Software distributed under the License is distributed on an "AS
## IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
## implied. See the License for the specific language governing
## rights and limitations under the License.
##
## The Original Code is the Tinderbox build tool.
##
## The Initial Developer of the Original Code is Netscape Communications
## Corporation. Portions created by Netscape are
## Copyright (C) 1998 Netscape Communications Corporation. All
## Rights Reserved.
##
## Complete rewrite by Ken Estes, Mail.com (kestes@staff.mail.com).
##

use CGI qw(-debug :standard escapeHTML);
use DBI;
use Date::Format;
use Date::Parse;

use lib './../config';
use lib '.';
require 'params.pl';
require 'db.pl';

# Default refresh in seconds... 0 for no refresh.
$refresh = 300;
if (defined param('refresh')) {
    $refresh = param('refresh');
}

if (param('period')) {
    $period = param('period');
}

if (param('length')) {
    $length = param('length');
}

$endtime = time; #int(time() / $period) * $period;
if (param('datetime')) {
    $endtime = str2time(param('datetime'));
}

$starttime = $endtime - $length;

if (defined user_agent()) {
    print header;
}
if (param('repository')) {
    $repository = param('repository');
}
if (param('module')) {
    $module = param('module');
}
if (param('branch')) {
    $branch = param('branch');
}
if (param('endtime')) {
    $endtime = param('endtime');
    if ($endtime =~ /[:\/]/) {
	$endtime = str2time($endtime);
    }
    $starttime = $endtime - $length;
}
if (param('starttime')) {
    $starttime = param('starttime');
    if ($starttime =~ /[:\/]/) {
	$starttime = str2time($starttime);
    }
}
if (param('show_level')) {
    $show_level = param('show_level');
}

$taskpat = "";
if (param('taskpat')) {
    $taskpat = param('taskpat');
}
$statuspred = "";
if (param('statustype')) {
    my $type = param('statustype');
    if ($type eq 'all') {
	# default
    } elsif ($type eq 'failing') {
	$statuspred = '> 1';
    } elsif ($type eq 'nonpass') {
	$statuspred = '> 0';
    } elsif ($type eq 'core') {
	$statuspred = '= 3';
    }
}
my $col_per_group = 0;
if (param('group')) {
    $col_per_group = param('group');
}

if (param('pasttimeunit') && !param('startime') && !param('endtime')) {
    # If they didn't explicitly specify start/end time, try &pasttimeunit
    my $unit = param('pasttimeunit');
    my $val = 0;
    if ($unit eq 'hours' && param('pasthours')) {
	$val = param('pasthours') * 60*60;
    } elsif ($unit eq 'days' && param('pastdays')) {
	$val = param('pastdays') * 24*60*60;
    }
    if ($val != 0) {
	# They specified pasthours or pastdays
	$starttime = $endtime - $val;
    }
}

if (($endtime - $starttime) > 7*24*60*60) {
    # range is more than a week, push the period to a day.
    $period = 24*60*60;
} elsif (($endtime - $starttime) > 24*60*60) {
    # range is from one day to a week, push the period to six hours.
    $period = 6*60*60;
} else {
    # range is under a day, push the period to an hour.
    $period = 60*60;
}

$vctype = "cvs" unless defined $vctype; # [dmehra] hack for compatibility with old config files

$bonsai_dbi = DBI->connect($bonsai_dburl, $bonsai_dbuser, $bonsai_dbpasswd) or
    die "Can't open DB.";

@checkins = get_checkins($repository, $module, $branch, $starttime, $endtime);

@runs = get_runs($repository, $module, $branch, $starttime, $endtime,
		 $taskpat, $statuspred, $col_per_group);

output_html($refresh, $endtime, $starttime, $period, \@checkins, \@runs);

# [dmehra] time conversion helpers
# i know this is ugly. got a better way to convert between time zones? fix this!

sub local_to_utc_sql ($) {
    my $local_epoch = shift; # takes time in seconds since epoch
    my $utc_string = gmtime($local_epoch);
    my $utc_epoch = str2time($utc_string);
    my $utc_sql = time2str("%Y-%m-%d %T", $utc_epoch); 
#    print "local: $local_epoch ----> UTC Sql: $utc_sql <br> \n";
    return $utc_sql; # returns UTC time in SQL string format
}

sub utc_to_local ($) {
    my $utc_epoch = shift; # takes UTC time in seconds since epoch
    my $utc_sql = time2str("%Y-%m-%d %T", $utc_epoch); 
    my $local_epoch = str2time($utc_sql, "UTC");
#    my $local_sql = time2str("%Y-%m-%d %T", $local_epoch); 
#    print "utc: $utc_epoch ----> local: $local_epoch <br> \n";
    return $local_epoch; # returns local time in seconds since epoch
}

# [dmehra] dates in viewcvs db are in UTC format, but local time in bonsai db
sub epoch_to_sql ($) {
    my $local_epoch = shift;
    return ($vctype eq "svn") ? 
	local_to_utc_sql($local_epoch) : 
	time2str("%Y-%m-%d %T", $local_epoch);
}
   
# make text suitable for including in HTML output
sub htmlify ($) {
    my $text = shift;
    $text =~ s/</&lt;/g; 
    $text =~ s/>/&gt;/g;
    $text =~ s/\"/&quot;/g;
    return $text;
}
 
#
# Returns the checkins in a list.
#
# The list has items of hashes, with each hash containing a
# "time" and a "name" entry.
#
sub get_checkins
{
    my($repository, $module, $branch, $starttime, $endtime) = (@_);
    
# [dmehra] branches are currently ignored with Subversion
    if ($branch eq "HEAD" || $branch eq "trunk") {
	$branch = "";
    }

# [dmehra] let's get revision number for each selected checkin - useful for later querying via viewcvs
# also get changeset description to display on mouseover on the checkin link: a href="who:rev" title="desc"

    my $stmt = $bonsai_dbi->prepare('SELECT distinct 
UNIX_TIMESTAMP(ci_when), people.who, dirs.dir, checkins.revision, descs.description 
FROM checkins, people, repositories, dirs, branches, descs 
WHERE people.id=whoid AND repositories.id=repositoryid AND dirs.id=dirid AND branches.id=branchid AND descs.id=descid
AND repositories.repository = ? AND branches.branch = ? AND ci_when > ? AND ci_when <= ? order by ci_when desc');

    $stmt->execute($repository, $branch, epoch_to_sql($starttime), epoch_to_sql($endtime));

    my @ret;
    my ($last_who, $last_time, $last_rev, $last_desc) = ("", 0, 0, "");
    my $pat = "^$module";

    while (my (@result) = $stmt->fetchrow()) {
	my ($ci_when, $who, $dir, $rev, $desc) = (@result);
        # [dmehra] convert timestamp back to local if coming from viewcvs/svn; bonsai's time is fine
	$ci_when = utc_to_local($ci_when) if ($vctype eq "svn"); 
	if (($last_time == $ci_when) && 
	    ($last_who eq $who)) {
	    next;
	} else {
	    ($last_time, $last_who, $last_rev, $last_desc) = ($ci_when, $who, $rev, $desc);
	    push(@ret, {time=>"$last_time", name=>"$last_who", rev=>"$last_rev", desc=>"$last_desc"});
	    #print STDERR "$last_who $last_time\n";
	}
    }
    
    return @ret;
}

sub output_html
{
    my($refresh, $latest_time, $earliest_time, $period, $checkins, $list) = @_;

    my($total_time) = $latest_time - $earliest_time;
    my($total_rows) = $total_time / $period;

    #
    # Calculate all the columns.
    # For each column, set the image url in $imgurl hash.
    #
    my $imgurls;
    my $names;
    my($total_columns) = 0;
    foreach my $item (@$list) {
	if ($item->{col} > $total_columns) {
	    $total_columns = $item->{col};
	}
	$imgurls{$item->{col}} = $item->{imgurl};
	$names{$item->{col}} = $item->{name};
    }
    if (@$list > 0) {
	$total_columns += 1;
    }

    #
    # Sort all the entries by time.
    # Then, put each entry in the item_map, which is indexed by
    # the column and by the period. Each bucket contains all the
    # run entries that need to go in to that column/time period.
    #
    @$list = sort {$b->{time} <=> $a->{time}} @$list;
    foreach my $item (@$list) {
	my $x = $item->{col};
	my $y = ($latest_time - $item->{time}) / $period;
	if ($y > $total_rows) {
	    $y = $total_rows;
	}
	push(@{$item_map[$x][$y]}, $item);
    }

    #
    # Go through each checkin and put them in the time periods.
    #
    @$checkins = sort {$b->{time} <=> $a->{time}} @$checkins;
    foreach my $item (@$checkins) {
	my $y = ($latest_time - $item->{time}) / $period;
	if ($y >= 0) {
	    push(@{$block_checkins[$y]}, $item);
	}
    }

    #
    # For each time period, calculate the max number of divisions
    # for the time period and store them in $block_division array.
    # While doing this, calculate the total number of divisions
    # in to $total_data_rows.
    #
    my $total_data_rows = 0;
    for my $y (0..$total_rows-1) {
	$block_division[$y] = 1;
	for $x (0..$total_columns-1) {
	    if (@{$item_map[$x][$y]} > $block_division[$y]) {
		$block_division[$y] = @{$item_map[$x][$y]};
	    }
	}

	$total_data_rows += $block_division[$y];
    }
    $block_division[$total_rows] = 1;
    
    for my $x (0..$total_columns-1) {

	#
	# For each period, evenly distribute any extra divisions among
	# the items in that item_map bucket.
	#
	for my $y (0..$total_rows-1) {
	    if (@{$item_map[$x][$y]} == 0) {
		# Do nothing.
	    } else {
		my $i = 0;
		while ($i < $block_division[$y]) {
		    foreach my $z (@{$item_map[$x][$y]}) {
			if ($i < $block_division[$y]) {
			    $z->{rows} += 1;
			    $i += 1;
			}
		    }
		}
	    }
	}

	#
	# Now, go find the empty rows and fill in.
	#
	my $current_row = 0;
	my $additional_rows = 0;
	for my $y (0..$total_rows) {
	    if (@{$item_map[$x][$y]} == 0) {
		$additional_rows += $block_division[$y];
	    } else {
		foreach my $z (@{$item_map[$x][$y]}) {
		    $z->{rows} += $additional_rows;
		    push(@{$output[$current_row]}, $z);
		    $current_row += $z->{rows};
		    $additional_rows = 0;
		}
	    }
	}

	push(@{$output[$current_row]}, {rows=>$additional_rows, name=>'&nbsp'});
    }

    my $y = 0;
    while ($y < $total_rows) {
	if (@{$block_checkins[$y]} == 0) {
	    my $current_row = $y;
	    my $rows = $block_division[$y];
	    $y += 1;
	    while ($y < $total_rows) {
		if (@{$block_checkins[$y]} == 0) {
		    $rows += $block_division[$y];
		    $y += 1;
		} else {
		    last;
		}
	    }

	    $output_checkin{$current_row}  = $rows;
	} else {
	    $y += 1;
	}
    }

    print("<html>\n");
    if ($refresh > 0) {
	print("<meta http-equiv=\"refresh\" content=\"$refresh\">\n");
    }

    my @results;
    for my $x (0..$total_columns-1) {
        # Find what color to use here.
        foreach my $y (@{$output[0]}) {
            if ($y->{col} == $x) {
                        my $status = $y->{status};
                        $results[$status]++;
            }           
        }   
    }   
    
    my $non_failing=$results[0]+$results[1];
    if ($total_columns == 0) {
	$total_columns = 1;
    }
    my $non_fail_perc=($non_failing/$total_columns);
    my $green_level=$non_fail_perc>=.5?(($non_fail_perc-.5)*2)*255:0;
    my $blue_level=int($green_level/4);
    my $red_level=255-$green_level;

    printf("<body bgcolor=\"%02X%02X%02X\">\n",$red_level,$green_level,$blue_level);
    print("<title>Flamebox Status Page: $module ($branch)</title>\n");
    print("<h4>Flamebox Status Page: $module ($branch)</h4>\n");
    print("<table><tr><td bgcolor='white'>");
    # print time range and repository
    print("<h5>End: " . ctime($endtime) . "<br>Start:" . ctime($starttime) . "<br>\n");
    print("Repository: $repository<br>Current time: " . ctime(time()) . "<br>\n");
    print("</td><td bgcolor=white><h5>");
    # print overall status of pass / warn / fail / core percentages

    printf "<font color=\"%02X%02X00\"> $non_failing non-fail / $total_columns total = %.2f %</font><br/>\n",$red_level,$green_level,($non_failing/$total_columns)*100;
    my $pass_perc=($results[0]/$total_columns);
    $green_level=($pass_perc>=.5?(($pass_perc-.5)*2)*255:0);
    $red_level=255-$green_level;
    printf "<font color=\"%02X%02X00\"> $results[0] pass / $total_columns total = %.2f %</font><br/>\n",$red_level,$green_level,($results[0]/$total_columns)*100;
    printf "$results[1] warn / $total_columns total = %.2f %<br/>\n",($results[1]/$total_columns)*100;
    printf "$results[2] fail / $total_columns total = %.2f %<br/>\n",($results[2]/$total_columns)*100;
    if($results[3]>0){
        print "<font color=red>";
    }
    printf "$results[3] core / $total_columns total = %.2f %<br/>\n",($results[3]/$total_columns)*100;
    if($results[3]>0){
        print "</font>";
    }
    print("</h5></td></tr></table>");
    print "<br>\n";

    # show the various colored columns for the test runs
    print("<table border=1 cellspacing=1 cellpadding=1>\n");
    print("<tr>\n<td bgcolor='white'><b>Time</b></td><td bgcolor='white'><b>Who</b></td>\n");

    for my $x (0..$total_columns-1) {
	# Find what color to use here.
	foreach my $y (@{$output[0]}) {
	    if ($y->{col} == $x) {
		if($y->{status} >= $show_level){
			$show_col[$x]=1;
			#print("<a href=\"$y->{url}\">");
			my $bgcolor = $y->{bgcolor};
			if ($bgcolor eq "orange") {
			    # use a red / yellow striped background for core dumps
			    print("<td background=\"/flamebox/stripe.gif\"><img src=\"$imgurls{$x}\" title=\"$names{$x}\"></td>\n");
			} else {
			    print("<td bgcolor=\"$bgcolor\"><img src=\"$imgurls{$x}\" title=\"$names{$x}\"></td>\n");
			}
			#print "</a>\n";
			#print "<font color=red>";
		}
	    }
		#print "x=$x,y=$yi,col=$y->{col}</font>\n";
	}
    }

    print("<td></td></tr>\n");

    my $next_row = 0;
    my $next_division = 0;

    my $tm = int($latest_time / $period) * $period;

    for my $y (0..$total_data_rows-1) {
	
	print("<tr>\n");    
	if ($y == $next_row) {
	    my $div = $block_division[$next_division];

            # time labels on the Y axis
	    print("<td rowspan=$div bgcolor='white'>");
            printf(time2str("%m/%d %H:%M", $tm));
            print("</td>\n");
	    $tm -= $period;
	    
            if (@{$block_checkins[$next_division]} != 0) {
		print("<td rowspan=$div bgcolor='white'>");
		foreach my $z (@{$block_checkins[$next_division]}) {
		    my $mindate = int($z->{time} / $period) * $period;
		    my $maxdate = (int($z->{time} / $period) * $period) + $period;
		    my $print_desc = htmlify($z->{desc});
		    my $print_name = htmlify($z->{name});
                    # [dmehra] use different URLs for Subversion vs. ViewCVS
                    # [dmehra] also add change description text on mouseover
		    if ($vctype eq "svn") { # ViewCVS/Subversion => use direct link
			my $svnroot = $repository;
			$svnroot =~ s/.*\///g; # this assumes svn_roots in viewcvs.conf same as repo name!
			print "<small><a href=\"$bonsaiurl?root=$svnroot&rev=$z->{rev}&view=rev\" " .
			    "title=\"$print_desc\">$print_name:$z->{rev}</small><br>";
		    } else { # Bonsai/CVS => use complicated query
			print "<small><a href=\"$bonsaiurl&repository='${repository}'&module=$module" .
			    "&branch=$branch&who=$z->{name}&date=explicit&mindate=$mindate&maxdate=$maxdate\" " .
			    "title=\"$print_desc\">$print_name</small><br>";
		    }
		}
		print("</td>\n");
	    } else {
		if (exists $output_checkin{$next_division}) {
		    print("<td rowspan=$output_checkin{$next_division} bgcolor='white'>&nbsp</td>\n");
		}
	    }
	    $next_row += $div;
	    $next_division += 1;
	}
	
        $align = "align=center valign=top"; # [dmehra] alignment of test log link within a cell

	foreach my $z (@{$output[$y]}) {
		if($show_col[$z->{col}]){
		    if ($z->{rows} == 1) {
			if (exists $z->{bgcolor}) {
			    if ($z->{bgcolor} eq "orange") {
			        # use a red / yellow striped background for core dumps
				print("<td $align background=\"/flamebox/stripe.gif\">" . $z->{data} . "</td>\n");
			    } else {
				print("<td $align bgcolor=\"" . $z->{bgcolor} . "\">" . $z->{data} . "</td>\n");
			    }
			} else {
			    print("<td $align bgcolor='white'>" . $z->{data} . "</td>\n");
			}
		    } else {
			if (exists $z->{bgcolor}) {
			    if ($z->{bgcolor} eq "orange") {
			        # use a red / yellow striped background for core dumps
				print("<td $align rowspan=\"" . $z->{rows} . "\" background=\"/flamebox/stripe.gif\">" . $z->{data} ."</td>\n");
			    } else {
				print("<td $align rowspan=\"" . $z->{rows} . "\" bgcolor=\"" . $z->{bgcolor} . "\">" . $z->{data} ."</td>\n");
			    }
			} else {
			    print("<td $align rowspan=\"" . $z->{rows} . "\">" . $z->{data} ."</td>\n");
			}
		    }
		}
	}
	print("<td bordercolor=white width=0%>&nbsp;</td></tr>\n");
    }

    my $branch_opt = "";
    if (!(($branch eq "HEAD") || ($branch eq ""))) {
	$branch_opt = "&branch=$branch";
    }

    print("</table>\n");
    print("<br>\n");
    print("<table>\n");
    print("<tr>\n");
    my $time_opt;
    if(param('starttime') || param('endtime')){
	$time_opt="&endtime=$endtime&starttime=$starttime";
    }
    print("<td bgcolor='white'><a href=\"getresult.cgi?show_level=1&refresh=$refresh"."$time_opt"."$branch_opt\" bgcolor='white'>Display only non-passing</a></td>\n");
    print("<td bgcolor='white'><a href=\"getresult.cgi?show_level=2&refresh=$refresh"."$time_opt"."$branch_opt\" bgcolor='white'>Display only failures</a></td>\n");
    print("<td bgcolor='white'><a href=\"getresult.cgi?group=1\">Group common tests</a></td>\n");
    print("</tr>\n");

    print("<tr>\n");
    print("<td bgcolor='white'><a href=\"getresult.cgi?refresh=0&endtime=$starttime&starttime=" . ($starttime - (6 * 60 * 60)) . "$branch_opt\">Display results 6 hours back</a></td>\n");
    print("<td bgcolor='white'><a href=\"getresult.cgi?refresh=0&endtime=" . ($endtime + (6 * 60 * 60)). "&starttime=$endtime$branch_opt\">Display results 6 hours forward</a></td>\n");
    print("<td bgcolor='white'><a href=\"getresult.cgi?group=0\">Seperate grouped tests</a></td>\n");
    print("</tr>\n");

    print("<tr>\n");
    print("<td bgcolor='white'><a href=\"getresult.cgi?refresh=0&endtime=$starttime&starttime=" . ($starttime - (12 * 60 * 60)) . "$branch_opt\">Display results 12 hours back</a></td>\n");
    print("<td bgcolor='white'><a href=\"getresult.cgi?refresh=0&endtime=" . ($endtime + (12 * 60 * 60)). "&starttime=$endtime$branch_opt\">Display results 12 hours forward</a></td>\n");
    print("</tr>\n");

    print("<tr>\n");
    print("<td bgcolor='white'><a href=\"getresult.cgi?refresh=0&endtime=$starttime&starttime=" . ($starttime - (24 * 60 * 60)) . "$branch_opt\">Display results 24 hours back</a></td>\n");
    print("<td bgcolor='white'><a href=\"getresult.cgi?refresh=0&endtime=" . ($endtime + (24 * 60 * 60)). "&starttime=$endtime$branch_opt\">Display results 24 hours forward</a></td>\n");
    print("</tr>\n");

    print("</table>\n");    
    print("<form method=\"get\" action=\"getresult.cgi\">\n");

    my $branch_hidden = "";
    if (!(($branch eq "HEAD") || ($branch eq ""))) {
	$branch_hidden = "<input type=\"hidden\" name=\"branch\" value=\"$branch\">";
    }
    print("Show me results from: <input type=\"text\" name=\"datetime\"><input type=\"hidden\" name=\"refresh\" value=\"0\">$branch_hidden<input type=\"submit\" value=\"Submit\"></form>");
    print("</body>\n");
    print("</html>\n\n");
}


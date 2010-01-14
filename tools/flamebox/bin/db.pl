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

use GD;
use DBI;
use Date::Format;
use CGI qw(:standard escapeHTML);

use lib './../config';
use lib '.';
require 'params.pl';

$dbi = DBI->connect($dburl, $dbuser, $dbpasswd) or die "Can't open DB.";

#
# Testing.
#
sub do_db_test_insert
{    
    insert_run("/repository", "sport", "", "linux2 build-dbg-pstore", 
	       1059152000, 0);
    insert_run("/repository", "sport", "", "linux2 build-dbg-pstore", 
	       1059152100, 0);
    insert_run("/repository", "sport", "", "linux2 build-dbg-pstore", 
	       1059152200, 0);
    insert_run("/repository", "sport", "", "linux2 build-dbg-pstore", 
	       1059152300, 0);

    insert_run("/repository", "sport", "", "linux1 build-dbg-pstore", 
	       1059152090, 1);
    insert_run("/repository", "sport", "", "linux1 build-dbg-pstore", 
	       1059152190, 0);
    insert_run("/repository", "sport", "", "linux1 build-dbg-pstore", 
	       1059152290, 2);
    insert_run("/repository", "sport", "", "linux1 build-dbg-pstore", 
	       1059152390, 0);
}

sub do_db_test_fetch
{
    my(@runs) = get_runs("/repository", "sport", "HEAD", 1059152000, 
			 1059155000);

    foreach my $item (@runs) {
	print "$item->{name} $item->{col} $item->{time}\n";
    }
}

#============================================================================
#
# Subroutines
#

#
# Gets page id from database.
#
sub get_page_id
{
    my ($repository, $module, $branch) = (@_);

    if ($branch eq "HEAD") {
	$branch = "";
    }

    my $stmt = $dbi->prepare('SELECT id FROM page WHERE repository = ? AND module = ? AND branch = ?');
    $stmt->execute($repository, $module, $branch);
    
    my ($id) = $stmt->fetchrow();
    
    unless ($id) {
	$stmt = $dbi->prepare('INSERT INTO page (repository, module, branch) values (?, ?, ?)');
	$stmt->execute($repository, $module, $branch);
	$stmt = $dbi->prepare('SELECT LAST_INSERT_ID()');
	$stmt->execute();
	($id) = $stmt->fetchrow();
    }

    return $id;
}

#
# Gets task id from database.
#
sub get_task_id
{
    my ($task) = (@_);

    my $stmt = $dbi->prepare('SELECT id FROM task WHERE name = ?');
    $stmt->execute($task);
    
    my ($id) = $stmt->fetchrow();
    
    unless ($id) {
	$stmt = $dbi->prepare('INSERT INTO task (name) values (?)');
	$stmt->execute($task);
	$stmt = $dbi->prepare('SELECT LAST_INSERT_ID()');
	$stmt->execute();
	($id) = $stmt->fetchrow();
	
	if ((defined $imagepath) && !($imagepath eq "")) {
	    output_vertical_text("$imagepath/$id.$imagetype", $task);
	}
    }

    return $id;
}

#
# Rebuilds all the images for all the tasks.
#
sub redraw_all_tasks
{
    my $stmt = $dbi->prepare('SELECT name, id FROM task');
    $stmt->execute();
    
    while (my (@result) = $stmt->fetchrow()) {
	my ($name, $id) = (@result);
    
	if ((defined $imagepath) && !($imagepath eq "")) {
	    output_vertical_text("$imagepath/$id.$imagetype", $name);
	}
    }
}

#
# Draws vertical text image.
#
sub output_vertical_text
{
    my ($filename, $text) = @_;

    $font = gdMediumBoldFont;

    $width = $font->height;
    $height = $font->width * (length($text) + 2);

    $image = new GD::Image($width, $height);
    
    $black = $image->colorAllocate(0, 0, 0);
    $white = $image->colorAllocate(255, 255, 255);
    $blue = $image->colorAllocate(0, 0, 255);
    
    $image->transparent($white);
    $image->interlaced('true');
    $image->filledRectangle(0, 0, $width, $height, $white);

    $image->stringUp($font, 0, $height-($font->width), $text, $black);

    open(PIC, ">$filename");
    binmode PIC;
    print PIC $image->$imagetype;
    close PIC;
}

#
# Inserts a new run entry.
#
sub add_run
{
    my($starttime, $pageid, $taskid, $status, $groupid) = (@_);

    my $stmt = 	$stmt = $dbi->prepare('INSERT INTO run (starttime, pageid, taskid, status, groupid) '.
                                             'VALUES   (?, ?, ?, ?, ?)');

    my $starttime_sql = time2str("%Y-%m-%d %T", $starttime);

    $stmt->execute($starttime_sql, $pageid, $taskid, $status, $groupid);
    $stmt = $dbi->prepare('SELECT LAST_INSERT_ID()');
    $stmt->execute();
    ($id) = $stmt->fetchrow();

    return $id;
}

#
# Inserts a new run in to the database. This in turn makes sure all 
# the page and task entries are created, if necessary, and then calls
# add_run.
#
sub insert_run
{
    my ($repository, $module, $branch, $task, $starttime, $status, $groupname) = (@_);

    my $pageid  = get_page_id($repository, $module, $branch);
    my $taskid  = get_task_id($task);
    my $groupid = 0;
    if ($groupname) {
      $groupid = get_task_id($groupname);
    }
    
    return add_run($starttime, $pageid, $taskid, $status, $groupid);
}

#
# Returns the status of runs with name (name of the task), 
# time (start time of the run), col (unique column number starting from 0),
# imgurl (image for the col), data (html data to be displayed in the cell),
# bgcolor (html color values).
#
sub get_runs
{
    my($repository, $module, $branch, $starttime, $endtime,
       $taskpat, $statuspred, $col_per_group) = (@_);

    if ($branch eq "HEAD") {
	$branch = "";
    }
    if ($taskpat ne "") {
	$taskpat = "AND task.name RLIKE '$taskpat'";
    }
    if ($statuspred ne "") {
	# statuspred comes in like "> 2"
	$statuspred = "AND run.status $statuspred";
    }

    my $starttime_sql = time2str("%Y-%m-%d %T", $starttime);
    my $endtime_sql = time2str("%Y-%m-%d %T", $endtime);

    my $stmt = $dbi->prepare("
      SELECT DISTINCT
       UNIX_TIMESTAMP(run.starttime), run.id, run.status, task.name, task.id, grp.name, grp.id
      FROM run
       LEFT OUTER JOIN task grp ON run.groupid = grp.id 
       LEFT JOIN task ON run.taskid = task.id 
       LEFT JOIN page ON run.pageid = page.id 
      WHERE
       page.repository = ?
       AND page.module = ?
       AND page.branch = ?
       AND run.starttime > ?
       AND run.starttime <= ?
       $taskpat
       $statuspred
      ORDER BY
       run.starttime DESC");
    $stmt->execute($repository, $module, $branch, $starttime_sql, $endtime_sql);

    my @ret;
    my %columns;
    my %taskids;
    my %groupids;
    while (my (@result) = $stmt->fetchrow()) {
	my ($time, $runid, $status, $name, $taskid, $groupname, $groupid) = (@result);
	my $column_name;
	if ($col_per_group && $groupid) {
	  $taskid = $groupid;
	  $column_name = $groupname;
	  $groupids{$column_name} = $groupid;
	}
	else {
	  $column_name = $name;
	  $taskids{$name} = $taskid;
	}
	$columns{$column_name} = 0;

	my $imgurl = "";
	my $lgurl = "";

	if ((defined $imageurl) && !($imageurl eq "")) {
	    $imgurl = "$imageurl/$taskid.$imagetype";
	}

	if ((defined $logurl) && !($logurl eq "")) {
	    my $dir=$runid%256;
            $lgurl = "$logurl?file=$dir/$runid.log.gz";
	}

	my $bgcolor = "00ff40"; # green with a bit of blue
	my $log_letter = "L";
	my $log_status = "pass";
	if ($status == 1) {
	    $bgcolor = "ffff00"; # yellow for warning
	    $log_letter = "W";
	    $log_status = "warning";
	} elsif ($status == 2) {
	    $bgcolor = "ff0000"; # red for error
	    $log_letter = "E";
	    $log_status = "error";
	} elsif ($status >= 3) {
	    $bgcolor = "orange"; # orange for core
	    $log_letter = "!!";
	    $log_status = "core dump";
	}
        $log_status = $name . " " . $log_status . " " . time2str("%m/%d %H:%M", $time);

	push(@ret, 
	     {time=>"$time", name=>"$column_name", imgurl=>"$imgurl",
	      data=>"<a href=\"$lgurl\" title=\"$log_status\">$log_letter</a>", bgcolor=>"$bgcolor", status=>"$status"}, url=>"$lgurl");
    }

    # Need to fetch the last run that finished before the requested
    # start time. This allows us to make the columns continuous.
    my $col = 0;
    foreach my $column (sort keys %columns) {
	$columns{$column} = $col;
	$col += 1;	

	my $stmt;
	my $join;

	if ($taskids{ $column }) {
	  $stmt = $dbi->prepare("
	  SELECT DISTINCT
	   UNIX_TIMESTAMP(run.starttime), run.id, run.status, task.name, task.id, NULL, 0
	  FROM
           run LEFT JOIN  task ON task.id = run.taskid 
               LEFT JOIN  page ON page.id = run.pageid 
	  WHERE
	   page.repository = ?
	   AND page.module = ?
	   AND page.branch = ?
	   AND task.id = ?
	   AND run.starttime < ?
	  ORDER BY
	   run.starttime DESC
	  LIMIT 1");
	  $stmt->execute($repository, $module, $branch, $taskids{$column}, 
			 $starttime_sql);
	}
	elsif ($groupids{ $column }) {
	  $stmt = $dbi->prepare("
	  SELECT DISTINCT
	   UNIX_TIMESTAMP(run.starttime), run.id, run.status, task.name, task.id, grp.name, grp.id
	  FROM
           run LEFT JOIN  task grp ON run.groupid = grp.id 
               LEFT JOIN  task     ON task.id = run.taskid 
               LEFT JOIN  page     ON page.id = run.pageid 
	  WHERE
	   page.repository = ?
	   AND page.module = ?
	   AND page.branch = ?
	   AND grp.id = ?
	   AND run.starttime < ?
	  ORDER BY
	   run.starttime DESC
	  LIMIT 1");
	  $stmt->execute($repository, $module, $branch, $groupids{$column}, 
			 $starttime_sql);
	}
	else {
	  die "$column should be known as a separate task or group";
	}

	while (my (@result) = $stmt->fetchrow()) {
	    my ($time, $runid, $status, $name, $taskid, $groupname, $groupid) = (@result);
	    my $column_name;
	    if ($col_per_group && $groupid) {
	      $taskid = $groupid;
	      $column_name = $groupname;
	    }
	    else {
	      $column_name = $name;
	    }
	    my $imgurl = "";
	    my $lgurl = "";

	    if ((defined $imageurl) && !($imageurl eq "")) {
		$imgurl = "$imageurl/$taskid.$imagetype";
	    }
	    
	    if ((defined $logurl) && !($logurl eq "")) {
	        my $dir=$runid%256;
            	$lgurl = "$logurl?file=$dir/$runid.log.gz";
	    }

	    my $bgcolor = "00ff40"; # green for ok
	    my $log_letter = "L";
	    my $log_status = "pass";
	    if ($status == 1) {
		$bgcolor = "ffff00"; # yellow for warning
		$log_letter = "W";
	        $log_status = "warning";
	    } elsif ($status == 2) {
		$bgcolor = "ff0000"; # red for error
		$log_letter = "E";
	        $log_status = "error";
	    } elsif ($status == 3) {
		$bgcolor = "orange"; # orange for core
	    	$log_letter = "!!";
	        $log_status = "core dump";
	    }
            $log_status = $name . " " . $log_status;

	    push(@ret, 
		 {time=>"$time", name=>"$column_name", imgurl=>"$imgurl",
		  data=>"<a href=\"$lgurl\" title=\"$log_status\">$log_letter</a>", bgcolor=>"$bgcolor", status=>"$status"},
		  url=>"$lgurl");
	}	
    }

    foreach my $item (@ret) {
	$item->{col} = $columns{$item->{name}};
    }

    return @ret;
}

#
# Parse the logfile and generates the html'ified log and inserts the
# run in to the database.
#
# The logfile should begin with a parameter section with these
# parameters:
# 
# flamebox: hostname:
# flamebox: taskname:
# flamebox: repository:
# flamebox: module:
# flamebox: branch:
# flamebox: starttime:
#
sub parse_logfile
{
    my($srcfile) = @_;
    my $params;
    my $line;

    open(SRC, "<$srcfile");
    open(DST, ">/tmp/runlog-$$");
    my $for_mail_file = "/tmp/warn-err-for-mail-$$";
    open(FOR_MAIL, ">$for_mail_file");

    # First, parse out the params.
    while (<SRC>) {
	$line = $_;
	if ($line =~ m/flamebox: END/) {
	    last;
	} elsif ($line =~ m/flamebox:\s+([^:]+):\s+(\S+)/) {
	    $params{$1} = $2;
	}
    }

    my $nmsgs = 0;
    my $ninfos = 0;
    my $nerrors = 0;
    my $nerrors_for_email = 0;
    my $nwarnings = 0;
    my $nwarnings_for_email = 0;
    my $core_found = 0;
    my $msgbody = "";

    $msgbody .= "<h2>Messages:</h2>\n<pre>\n";

    # Now, build the messages section by appending to the $msgbody string.
    # At the same time, build the log file with the links embedded.
    while (<SRC>) {
	$line = $_;
	if ($line =~ m/flamebox: BEGIN/) {
	    while (<SRC>) {
		$line = $_;
		if ($line =~ m/flamebox: END/) {
		    last;
		} elsif ($line =~ m/flamebox:\s+([^:]+):\s+(\S+)/) {
		    $params{$1} = $2;
		}
	    }
	    next;
	}
	my $linetype = parse_line($line);
	my ($errtype, $do_email) = split (/[-\s]+/, $linetype);
	$line = escapeHTML($line);
	my $cline = $line;
	my $img;
	chomp($cline);
	$warn_or_err = undef;
	if ($errtype eq "OK") {	
	    $outline = "      ";
	} elsif ($errtype eq "CORE") {
	    $img = "<img align=\"middle\" border=\"0\" src=\"$htmlroot/core.gif\">";
	    $msgbody .= "<a href=\"#msg$nmsgs\">${img}Core: " . $cline . "</a>\n";
	    $next = $nmsgs + 1;
	    $outline = "<a name=\"msg$nmsgs\" href=\"#msg$next\">NEXT:</a> $img";
	    $core_found = 1;
	    $nerrors += 1;
	    $nerrors_for_email += 1 if $do_email ne "";
	    $nmsgs = $next;
	    $warn_or_err = "Core: " . $line;
	} elsif ($errtype eq "ERROR") {
	    $img = "<img align=\"middle\" border=\"0\" src=\"$htmlroot/error.gif\">";
	    $msgbody .= "<a href=\"#msg$nmsgs\">${img}Error: " . $cline . "</a>\n";
	    $next = $nmsgs + 1;
	    $outline = "<a name=\"msg$nmsgs\" href=\"#msg$next\">NEXT:</a> $img";
	    $nerrors += 1;
	    $nerrors_for_email += 1 if $do_email ne "";
	    $nmsgs = $next;
	    $warn_or_err = "Error: " . $line;
	} elsif ($errtype eq "WARNING") {
	    $img = "<img align=\"middle\" border=\"0\" src=\"$htmlroot/warning.gif\">";
	    $msgbody .= "<a href=\"#msg$nmsgs\">${img}Warning: " . $cline . "</a>\n";
	    $next = $nmsgs + 1;
	    $outline = "<a name=\"msg$nmsgs\" href=\"#msg$next\">NEXT:</a> $img";
	    $nwarnings += 1;
	    $nwarnings_for_email += 1 if $do_email ne "";
	    $nmsgs = $next;
	    $warn_or_err = "Warning: " . $line;
	} elsif ($errtype eq "INFO") {
	    $msgbody .= "<h3><b><a href=\"#info$ninfos\">" . $cline . "</a></b></h3>\n";
	    $next = $ninfos + 1;
	    $outline = "<a name=\"info$ninfos\" href=\"#info$next\">NEXT:</a> ";
	    $ninfos = $next;
	} elsif ($errtype eq "LINK") {
            $_ =~ /^LINK\b(.*)/;
	    $msgbody .= "<h3><b>".$1."</b></h3>\n";
	    $outline = "";
	} else {
	    die("unknown error type: $errtype");
	}

	print DST $outline . $line;
	if (defined($warn_or_err)) {
	    print FOR_MAIL $warn_or_err;
	}
    }

    $msgbody .= "</pre>\n";

    close SRC;
    close DST;
    close FOR_MAIL;

    # At this point, we are done parsing, so update the database.
    if (!(defined $params{"hostname"}) ||
	!(defined $params{"taskname"}) ||
	!(defined $params{"repository"}) ||
	!(defined $params{"module"}) ||
	!(defined $params{"branch"}) ||
	!(defined $params{"starttime"})) {

	unlink("/tmp/runlog-$$");

	die("Missing flamebox parameters\n");
    }

    if (!(defined $params{"groupname"})) {
      $params{"groupname"} = '';
    }
	
    $status = 0;
    if ($nwarnings > 0) {
	$status = 1;
    }
    if ($nerrors > 0) {
	$status = 2;
    }
    if ($core_found) {
	$status = 3;
    }
    $runid = insert_run($params{"repository"}, 
			$params{"module"}, 
			$params{"branch"}, 
			$params{"hostname"} . " " . $params{"taskname"},
			$params{"starttime"}, 
			$status,
		        $params{"groupname"});
    
    open(SRC, "</tmp/runlog-$$");
    open(DST, ">$logpath/$runid.log");

    print DST "<html><title>Full Log</title><body>\n";
    print DST "<h2>Run Data:</h2><pre>\n";

    foreach $param (keys %params) {
	print DST "flamebox: $param: $params{$param}\n";
    }

    print DST "\n";

    print DST "Start time    : " . ctime($params{"starttime"});
    print DST "Local time    : " . ctime(time());
    print DST "Status        : ";
    if ($status == 0) {
	print DST "SUCCESS\n";
    } elsif ($status == 1) {
	print DST "WARNING\n";
    } else {
	print DST "ERROR\n";
    }
    print DST "Total Errors  : " . $nerrors . "\n";
    print DST "Total Warnings: " . $nwarnings . "\n";

    print DST "</pre>\n";

    print DST $msgbody;

    print DST "<h2>Run Log:</h2><pre>\n";

    while (<SRC>) {
	print DST $_;
    }
    
    print DST "\n</pre>\n";

    close SRC;
    close DST;

    unlink("/tmp/runlog-$$");

    # If we want email for warnings or errors, issue it
    my $email = "";
    my $email_type = undef;
    if ($params{warning_email} ne "" && ($nwarnings_for_email > 0 || $nerrors_for_email > 0)) {
	$email = $params{warning_email};
	$alert_type = "Warning";
	$alert_type2 = "a warning";
    }
    if ($params{error_email} ne "" && $nerrors_for_email > 0) {
	$email = $email . "," . $params{error_email};
	$alert_type = "Error";
	$alert_type2 = "an error";
    }
    if ($email ne "") {
        my $local_starttime = ctime($params{starttime});
	chomp $local_starttime;
        my $mail_subject = "Flamebox $alert_type in $params{hostname}-$params{taskname} $local_starttime";
	my $rc = open (MAIL, "| mail -s \"$mail_subject\" $email");
    	my $dir=$runid%256;
        my $lgurl = "${flamebox_home}${logurl}?file=${dir}/${runid}.log.gz";

	if ($rc) {
	    print MAIL "The flamebox run $params{hostname}-$params{taskname} starting at\n";
	    print MAIL "$local_starttime encountered $alert_type2.\n";
	    print MAIL "See $lgurl for details.\n";
	    print MAIL "\n";
	    open (FOR_MAIL, $for_mail_file);
	    while (<FOR_MAIL>) {
		print MAIL $_;
	    }
	    close (FOR_MAIL);
	    close (MAIL);
	}
    }
    unlink($for_mail_file);

    return "$logpath/$runid.log";
}

1;

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
# $Id: flamebox-client.pl 88 2004-11-19 23:14:14Z cameron $

use Getopt::Long;
use LWP::UserAgent;
use Sys::Hostname;
use Symbol;
use IPC::Open3;
use POSIX;
use IO::Handle;

use lib './../config';
use lib '.';
require 'tasks.pl';

umask 0022;

$HOSTNAME = Sys::Hostname::hostname();
$HOSTNAME =~ s/\..*//;

$DAEMONIZE = 0;
$ONCE = 0;
$VERBOSE = 1;
$DUMPRESULT = 0;
$NOPOST = 0;
$CONFIG = "";
$TASK = '';
$TASKDIR = '';
$POSTURL = "";

parse_args();

if (!($CONFIG eq "")) {
    require $CONFIG;
}

if ($POSTURL eq "") {
    $POSTURL = $posturl;
}

if (!$TASK) {
  foreach $name (@ARGV) {
    if (!(exists $tasks{$name})) {
      print "$name not defined.\n";
      exit;
    }
    push(@todo, $name);
  }
} else {
  ($VERBOSE) && print "Creating temporary task: $TASK\n";
  $dir = `pwd`;
  chomp $dir;
  $tasks{$TASK} = 
    {
    group=>"once",
    repository=>"/repository", 
    module=>"sport",
    branch=>"HEAD",
    dir=>$dir,
    commands => \@ARGV,
    };
  push(@todo, $TASK);
}

# [dmehra] commenting out next block to get to do()
#if (@todo == 0 && $TASKDIR eq '') {
#    print "nothing to do (no tasks defined).\n";
#    exit;
#}

if ($DAEMONIZE) {
    daemonize();
}

$once_time = 0;

sub get_tasks_from_files {
  my ($TASKDIR) = @_;
  my @todo = ();
  my $once_task_file = $TASKDIR . "/once";
  my $repeat_task_file = $TASKDIR . "/tasks";

  # Do the tasks in $TASKDIR/once only if its mtime is greater than the
  # at last time we read the file.
  if (-f $once_task_file) {
    my @once_stat = stat ($once_task_file);
    if ($once_stat[9] > $once_time) {
      my $rc = open (TASKFILE, $once_task_file);
      if ($rc) {
        while (<TASKFILE>) {
	  s,#.*$,,;
  	  chomp;
	  if ( m/\S/ ) {
	    push (@todo, $_);
	  }
	}
        close (TASKFILE);
      }
      $once_time = $once_stat[9];
      rename ($once_task_file, "$once_task_file.$once_time");
    }
  }

  # Do the tasks in $TASKDIR/tasks every iteration.
  if (-f $repeat_task_file) {
    my $rc = open (TASKFILE, $repeat_task_file);
    if ($rc) {
      while (<TASKFILE>) {
        s,#.*$,,;
	chomp;
	if ( m/\S/ ) {
	  push (@todo, $_);
	}
      }
      close (TASKFILE);
    }
  }
  return @todo;
}

do {
    do "tasks.pl" unless $TASK;
    if ($TASKDIR ne '') {
	@todo = get_tasks_from_files ($TASKDIR);
    }
    # [dmehra] by default, read all tasks from tasks file
    if (!@todo) {
	@todo = keys %tasks; # note that the order of execution is undefined
	print "Will run tasks: @todo \n";
    } # [/dmehra]

    foreach $name (@todo) {
	$task = $tasks{$name};

	# allow per-task email notifications, default to flamebox-wide address
	my $task_error_email = (defined $task->{error_email}) ? $task->{error_email} : $flamebox_email;
	my $task_warning_email = (defined $task->{warning_email}) ? $task->{warning_email} : $flamebox_email;

	($VERBOSE) && print "Running task: $name\n";

	open(DST, ">/tmp/flameboxlog-$$");
	DST->autoflush(1);
	print DST "flamebox: hostname: " . $HOSTNAME . "\n";
	print DST "flamebox: taskname: " . $name ."\n";
	print DST "flamebox: groupname: " . $task->{group} ."\n"
	  if $task->{group};
	print DST "flamebox: repository: " . $task->{repository} ."\n";
	print DST "flamebox: module: ". $task->{module} ."\n";
	print DST "flamebox: branch: " . $task->{branch} ."\n";
	print DST "flamebox: error_email: " . $task_error_email ."\n";
	print DST "flamebox: warning_email: " . $task_warning_email ."\n";
	print DST "flamebox: starttime: " . time() . "\n";
	print DST "flamebox: END\n\n";

	safe_run_task($task);

	close DST;       

	($VERBOSE) && print "Run done.\n";

	if (!($NOPOST)) {
	    if ($VERBOSE) {
		system ("/bin/ls -l /tmp/flameboxlog-$$");
		print "Posting result /tmp/flameboxlog-$$ to $POSTURL: ";
	    }
	    my $err = post_results($POSTURL, "/tmp/flameboxlog-$$");

	    if ($VERBOSE) {
		if ($err->is_success()) {
		    print "Success.\n";
		} else {
		    print "Failed.\n";
		}
		system ("/bin/ls -l /tmp/flameboxlog-$$");
	    }
	}

	if ($DUMPRESULT) {
	    print "--------------------------------------------------------------------------------\n";
	    print "Log for: $name\n\n";
	    open(SRC, "</tmp/flameboxlog-$$");
	    while(<SRC>) {
		print $_;
	    }
	    close SRC;
	}
	unlink("/tmp/flameboxlog-$$");
    }
    
} while(!$ONCE);

($VERBOSE) && print "Done.\n";

##############################################################################
#
# Subroutines.

sub usage
{
    my ($usage) = <<"__EOF__";
    
    flamebox-client.pl [options] task [task]

Global Options:

	--daemonize		Run program in the background.
	--once			Run only one loop.
	--verbose		Verbose debugging.
	--posturl URL		Use URL instead of url defined in tasks.pl.
	--dumpresult		Dump result log output to stdout.
	--nopost		Do not post the results to flamebox.
	--config CONFIG		Read in additional CONFIG file.
	--task NAME		This task is NOT described in tasks.pl.
	--taskdir		Directory where task file are.
__EOF__
;

    print $usage;
    exit 0;
}

sub parse_args
{
    %option_linkage = (
		       "daemonize" => \$DAEMONIZE,
		       "once" => \$ONCE,
		       "verbose" => \$VERBOSE,
		       "posturl" => \$POSTURL,
		       "dumpresult" => \$DUMPRESULT,
		       "nopost" => \$NOPOST,
		       "config" => \$CONFIG,
		       "task" => \$TASK,
		       "taskdir" => \$TASKDIR,
		       );
    Getopt::Long::config('require_order', 'auto_abbrev', 'ignore_case');

    if( !GetOptions (\%option_linkage,
                     "daemonize!",
		     "once!",
		     "verbose!",
		     "posturl=s",
		     "dumpresult!",
		     "nopost!",
		     "config=s",
		     "task=s",
		     "taskdir=s",
		     ) ) {	
	print("Illegal options in \@ARGV: '@ARGV'\n");
	usage();
	exit 1 ;
    }
}

# daemonize a process taken from "Advanced Programming in the UNIX
# Environment" by W. Richard Stevens.
sub daemonize {

    my $pid = fork();

    defined($pid) ||
        die("Could not fork. $!\n");

    # parent stops.
    ($pid == 0) ||
        exit 0;

    # child continues
    chdir("/") ||
        die("Could not change to directory '/'. $!\n");

    setsid;
    umask 0;

    print "\npid: $$\n";

    return ;
}


sub safe_run_task
{
    my ($task) = @_;

    my $err = "";

    eval {
	local $SIG{'PIPE'} = sub { die "@_\n"; };
	local $SIG{'__DIE__'} = sub { die "@_\n"; };
	
	$err = run_task($task);
    };

    if ($@ || $err) {
    	my $exit_value = $err >> 8;
    	my $signal_num = $err & 127;
    	my $dumped_core = $err & 128;
	print DST "Error: $@ -- exit value = $exit_value, signal = $signal_num, core = $dumped_core\n";
    }
}

sub run_task
{
    my ($task) = @_;

    chdir($task->{dir}) || die("Can't chdir to $task->{dir}: $!\n");
    foreach my $cmd (@{$task->{commands}}) {
	$cmd =~ s/\#.*//;       
	$cmd =~ s/\s+/ /g;
	$cmd =~ s/^\s+//;
	$cmd =~ s/\s+$//;
	($cmd =~ m/^\s+$/) && next;
	
	my $err = 0;
	print DST "> $cmd\n";
	($VERBOSE) && print "> $cmd\n";
	if ($cmd =~ m/^cd\s+(.+)$/) {
	    chdir($1) || die("Can't chdir to $1: $!\n");
	} else {
	    $err = system3($cmd);		
	}

	if ($err != 0) {
	    return $err;
	}
    }
}

sub system3
{
    my($cmd) = @_;
    my $fh_in = Symbol::gensym();
    my $fh_out = Symbol::gensym();
    my $fh_err = Symbol::gensym();
    ($fh_in && $fh_out && $fh_err) || 
	die ("Could not create new symbol, 'gensym()' object.\n");

    my $child_pid = IPC::Open3::open3($fh_in, $fh_out, $fh_err, $cmd);
    ($child_pid) || die ("Open3() did not start: '$cmd'. $!\n");
    close($fh_in) || die("Could not close child stdin: $!\n");

    nonblock($fh_out);
    nonblock($fh_err);

    my $reaped_pid = 0;
    my $wait_status = 0;

    while ($reaped_pid != $child_pid) {

	sleep 1;

	my $new_out = '';
	my $new_err = '';

	my $rc = '';

	$reaped_pid = waitpid(-1, POSIX::WNOHANG);

	if ($reaped_pid == $child_pid) {
	    $wait_status = $?;
	    if (!(POSIX::WIFEXITED($wait_status))) {
		$reaped_pid = -1;
	    }
	    
	}

	do {
	    my $data_out = '';
	    $rc = sysread($fh_out, $data_out, POSIX::BUFSIZ, 0);
	    $new_out .= $data_out;
	} until ($rc <= 0);
	
	do {
	    my $data_err = '';
	    $rc = sysread($fh_err, $data_err, POSIX::BUFSIZ, 0);
	    $new_err .= $data_err;
	} until ($rc <= 0);
	
	print DST $new_out.$new_err;
	
    } # while pid

    ($reaped_pid != $child_pid) &&
	warn("No Child pid received. ".
	     "reaped_pid: $reaped_pid, ".
	     "child_pid: $child_pid, ".
	     "wait_status: $wait_status, ".
	     "cmd: $cmd\n");
    
    close($fh_out);    
    close($fh_err);

    return ($wait_status);
}


sub nonblock 
{
    # unbuffer a fh so we can select on it
    my ($fh) = shift;
    my $rc = '';
    my $flags = '';
    
    $flags = fcntl($fh, F_GETFL, 0) ||
	die("Could not get flags of socket: $fh : $!\n");

    $flags |= O_NONBLOCK;
    
    $rc = fcntl($fh, F_SETFL, $flags) ||
	die("Could not set flags of socket: $fh : $!\n");
    
    return 1;
}

    
sub post_results
{
    my ($url, $filename) = @_;
    
    my $www = new LWP::UserAgent();

    return $www->post($url,
		      Content_Type => 'form-data',
		      Content => [ logfile => [$filename]]);
}

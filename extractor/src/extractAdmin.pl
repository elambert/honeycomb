#!/usr/bin/perl -w -I/opt/honeycomb/extractor

################################################################################
#
# $Id: extractAdmin.pl 11349 2007-08-13 21:49:33Z ks202890 $
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
# Explorer like extractor tool for the Admin CLI
#
# Typically run by the extractor.pl script after a system failure
# to capture status and log information for the Admin CLI.
#
################################################################################

use strict;
use Getopt::Std;
use File::Basename;
use English;
use ExtractLibs;

################################################################################
### Extractor Variables
################################################################################

### Unix commands
my $PING = "/usr/sbin/ping";
my $SSH = "/usr/bin/ssh";
my $DATE = "/usr/bin/date";

### General extractor variables
my $MYROOT = File::Basename::dirname($PROGRAM_NAME);
my $MYNAME = basename($PROGRAM_NAME);
my $THISDATE = undef;
my $SIGNUM = 0;
my $MESSAGE = undef;
my $REMOTE_HOSTNAME = undef;
my $REMOTE_IP = undef;
my $PING_CMD = undef;
my $SSH_CMD  = undef;
my $LOGDIR  = undef;
my $LEVEL = 0;
my $CMD_STATUS = 0;
my $RUN_CMDS = 1;

### Cluster Variables
my $MINIMUM_VERSION = "1.1";

### Cli Variables
my $CLI_ADMIN_IP = "10.123.45.200";
my $SSH_CLI = "$SSH root\@$CLI_ADMIN_IP -o StrictHostKeyChecking=no /opt/honeycomb/bin/hcsh -c";
my $CLI_TESTCMD = "date";

my $PATH = $ENV{'PATH'};
$ENV{PATH} = "${MYROOT}:${PATH}";

################################################################################
### Extractor Subroutines
################################################################################

sub print_usage() {
   print <<EOF;
NAME
    $MYNAME - Service Processor subsystem extractor utility

SYNOPSIS
    $MYNAME - Admin CLI subsystem extractor utility

DESCRIPTION
    TBD

USAGE
    TBD

EXAMPLES
    TBD
EOF
} # print_usage #

sub sigHandler () {
    my $signame = shift;
    our $SIGNUM++;
    local $SIG{HUP} = 'IGNORE';
    local $SIG{INT} = 'IGNORE';
    local $SIG{TERM} = 'IGNORE';
    ExtractLibs->print_msg("\nWARNING: Signal SIG$signame recieved. $MYNAME cleanup and exit!\n");
    exit(2);
} # sigHandler #

################################################################################
### Start of Main 
################################################################################

# Get commandline options
my @commandline = @ARGV;
my (%opts);
if (!getopts("r:i:p:s:o:l:S:CLMDA?h", \%opts)) {
    ExtractLibs->print_msg("ERROR: A getopts error occurred. Check usage.\n");
    exit(1);
}
if ($opts{h} || $opts{'?'}) {
   print_usage();
   exit(0);
}
$REMOTE_HOSTNAME = $opts{r} || "admin";
$REMOTE_IP = $opts{i} || "$CLI_ADMIN_IP";
$PING_CMD= $opts{p} || "$PING $REMOTE_IP 1 >> /dev/null 2>&1";
$SSH_CMD = $opts{s} || "$SSH_CLI";
$LOGDIR = $opts{o} || "/var/adm/extractor/$REMOTE_HOSTNAME";
$LEVEL = $opts{l} if ($opts{l});
if ($opts{C} || $opts{L} || $opts{M} || $opts{D}) {
    $RUN_CMDS = $opts{C} || 0;
}

# Print header to STDOUT
$THISDATE = `$DATE`;
my $commandline = "$MYNAME";
while (@commandline) {
    $commandline .= " ".shift(@commandline);
}
$MESSAGE = "Starting Admin CLI $REMOTE_HOSTNAME extraction on: $THISDATE\n".
           "Command Line: $commandline\n";
ExtractLibs->print_header($MESSAGE);

# Create the LOGDIR if it doesn't exist
my $rc = `mkdir -p $LOGDIR`;
if ($rc) {
    ExtractLibs->print_msg("ERROR: Cannot create $LOGDIR, error: $rc\n");
    exit(2);
}

# Define the signals we want to catch.
$SIG{HUP} = \&sigHandler;
$SIG{INT} = \&sigHandler;
$SIG{TERM} = \&sigHandler;

# Check your connection to the remote subsystem
ExtractLibs->check_connection($REMOTE_HOSTNAME,$PING_CMD, $SSH_CMD, $CLI_TESTCMD);

# Verify the version is at the minimum level
ExtractLibs->check_version($REMOTE_HOSTNAME, $SSH_CMD, "version", $MINIMUM_VERSION);

# Process all commands for this subsystem at the assigned LEVEL
$CMD_STATUS = ExtractLibs->process_commands($REMOTE_HOSTNAME, $SSH_CMD, $LEVEL, $LOGDIR) if ($RUN_CMDS);

# Output the completion status and exit
$THISDATE = `$DATE`;
if ($CMD_STATUS) {
    $MESSAGE = "$MYNAME reported $CMD_STATUS WARNING messages for $REMOTE_HOSTNAME on: $THISDATE";
} else {
    $MESSAGE = "Normal completion of $MYNAME for $REMOTE_HOSTNAME on: $THISDATE";
}
ExtractLibs->print_header($MESSAGE);

exit(0);

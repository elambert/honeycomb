#!/usr/bin/perl -w
#
# $Id$
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
# Written by Matt Coneybeare - QA Intern - July 23rd, 2007
# Please send death threats to matthew.coneybeare@sun.com
#
# CLUSTER MONITORING TOOL
# *NOTE: only for version 1.1
# cluster_monitor.pl is a script that is designed to be run in parallel with other
#  tests and monitor the status of the cluster being tested.  If something goes
#  wrong with the cluster (nodes or disks fail etc...) then the tester will be
#  notified of this event.  The script is organized to easily add/remove CLI commands
#  whenever necessary.  The script take 3 mandatory arguments at the command line:
#   1) the admin vip to run the script on,
#   2) an email address to send alerts to,
#   3) a time interval (in seconds) for which the script will sleep between iterations.
#  The script also takes two optional arguments:
#   1) an ip address for a smtp server to send the emails
#   2) a name to replace the ip address in alerts (ex dev305, my-8-node-cluster...)
#
# Command Line Format
# *NOTE: argument can be in any order
#
# /cluster_monitor.pl [-h] -a <ADMIN_VIP> -e <EMAIL> -t <TIME_INTERVAL> [-s SMTP_SERVER] [-n NAME]
#   MANDATORY
#   - ADMIN VIP: 10.7.226.XX or devXXX-admin
#   - EMAIL  : xxx@yyy.zzz or xxx@yyy.zzz.qqq
#   - TIME_INTERVAL:  XXXX (seconds)
#   OPTIONAL
#   - SMTP_SERVER: ip of an smtp server to use other than default.
#   - NAME: Name to distingish cluster in alerts
#
# EXAMPLES:
# /cluster_monitor.pl -a 10.70.226.61 -e matthew.coneybeare@sun.com -t 60
# /cluster_monitor.pl -a dev325-admin -e matthew.coneybeare@sun.com -t 60
# /cluster_monitor.pl -a 10.70.226.61 -e matthew.coneybeare@sun.com -t 60 -s 192.168.1.1 -n dev325
#
# Adding CLI Commands
# To add commands you must add in three (3) places:
#  1) The @CLI_COMMANDS array
#  2) The %COMMAND_VERIFIER_TABLE mapping the command to your...
#  3) Comparison subroutine
#
# *NOTE: If running a command that DOES NOT take the -c CELL option,
#        you must add it to the check in run_cli_commands.

use strict;
use Getopt::Std;
use Net::SMTP;

###########################################
# Unix commands
my $PING = "ping";
my $SSH  = "ssh";

###########################################
# Test Script Variables

# Hash of commands sent to the CLI and frequency to call them
# Commands must be paired with a comparison subroutine
#  as well as an entry in the Dispatch Table
#  frequency = 1 means call every iteration,
#  frequency = 5 means call every 5th iteration
my %CLI_COMMANDS = (

    #COMMAND            FREQUENCY
    "date"           => 1,
    "sysstat"        => 1,
    "sysstat -v"     => 1,
    "cellcfg"        => 1,
    "hivecfg"        => 1,
    "mdconfig -l"    => 1,
    "version"        => 1,
    "version -v"     => 1,
    "df -h"          => 1,
    "perfstats -t 1" => 5,
    "ddcfg --force"  => 1,
    "hwstat"         => 1,
    "sensors"        => 1
);

# Hash of mapping from commands to subroutines
my %COMMAND_VERIFIER_TABLE = (

    #COMMAND              NAME OF SUBROUTINE
    "sysstat"        => \&compare_sysstat,
    "date"           => \&compare_date,
    "sysstat -v"     => \&compare_sysstat_v,
    "cellcfg"        => \&compare_cellcfg,
    "hivecfg"        => \&compare_hivecfg,
    "mdconfig -l"    => \&compare_mdconfig_l,
    "version"        => \&compare_version,
    "version -v"     => \&compare_version_v,
    "df -h"          => \&compare_df_h,
    "hwstat"         => \&compare_hwstat,
    "perfstats -t 1" => \&compare_perfstats,
    "ddcfg --force"  => \&compare_ddcfg,
    "sensors"        => \&compare_sensors

);

###########################################
# Globals

my $LOGFILE          = "/tmp/cluster_monitor_$$.out";
#my $SSH_OPTIONS     = " -i trunk/hctest/etc/ssh/id_dsa"; # Location from my workspace
my $SSH_OPTIONS      = ' -q -o StrictHostKeyChecking=no';    # No Passwords
my $SSH_IDENTITY     = ' -i ~/.ssh/id_dsa';
my $SMTP_SERVER      = '129.147.62.198';    # Default smtp server, can be overridden by input args
my $INTERVAL         = 300;# ~= Time to reboot a node and disks, can be overridden by input args
my $ADMIN_VIP        = 0;  # Admin Vip is set by mandatory input arguments
my $EMAIL            = ''; # Email is set by mandatory input arguments
my $NAME             = ''; # Name to send out in alerts
my %INITIAL_STATUS   = (); # Hash used to store the initial cluster configuration
my %PREVIOUS_STATUS  = (); # Hash used to store the last iterations status
my %RETURNED_STATUS  = (); # Hash used to store the current iterations status
my %INITIAL_NODES    = (); # Stores initial number of nodes
my %INITIAL_DISKS    = (); # Stores initial number of disks
my %INITIAL_QUERY    = (); # Stores initial Query Engine Status
my %PREVIOUS_NODES   = (); # Stores previous iteration's nodes
my %PREVIOUS_DISKS   = (); # Stores previous iteration's disks
my %PREVIOUS_QUERY   = (); # Stores previous iteration's Query Engine Status
my %RETURNED_NODES   = (); # Stores current iteration's nodes
my %RETURNED_DISKS   = (); # Stores current iteration's disks
my %RETURNED_QUERY   = (); # Stores current iteration's Query Engine Status
my %IS_FULL          = (); # Stores current capacity status
my @CELL_IDS         = (); # Array stores cell numbers from hiveadm
my $IS_MULTI_CLUSTER = 0; # Boolean - True: Multi-Cluster, False: Single-Cluster
my $IS_OK_TO_PROCEED = 1; # Boolean to stop script
my $IS_INITIALIZED   = 0; # Boolean - False: 1st iteration, True: nth iteration
my $IS_CLI_RESPONSIVE= 1; # Boolean - State variable set by cli_test
my $CAN_PING_ADMIN   = 1; # Boolean - True on successful ping
my $IS_INITIAL_STATE = 1; # Boolean - State variable for when current equals initial status
my $ITERATIONS       = 1; # Iteration Counter
my $PING_TIMEOUT     = 5; # Timeout in seconds between ping requests
my $PING_ATTEMPTS    = 5; # Number of times to try an unresponsive ping
my $MASTER_CELL      = 0; # Mastercell number, set from hiveadm

####################################################################################
# MAIN

# Get Inputs and Options
my %opts       = ();
my $opt_string = 'ha:s:e:t:n:';
getopts( "$opt_string", \%opts );
$ADMIN_VIP = $opts{'a'};
$EMAIL     = $opts{'e'};
$NAME      = $opts{'n'};
my $interval    = $opts{'t'};
my $smtp_server = $opts{'s'};

$NAME ||= $ADMIN_VIP;
$SMTP_SERVER = $smtp_server if ( defined $smtp_server );
$INTERVAL    = $interval    if ( defined $interval );
usage() if !( defined $ADMIN_VIP && defined $EMAIL );

# Input Error Checking Here
if ( $EMAIL !~ /^\w+[\+\.\w-]*@([\w-]+\.)*\w+[\w-]*\.([a-z]{2,4}|\d+)\.??([a-z]{2,4}|\d+)*?$/i ) {
    print
      "\nERR: $EMAIL is not a valid email address.\n";
    exit -1;
}
if ( $INTERVAL !~ /^\d+$/ ) {
    print "\nERR: $INTERVAL is not valid, should be a positive integer.\n";
    exit -1;
}


# See if SSH file exists
my $ssh_exists = `if [ -e ~/.ssh/id_dsa ]; then echo 1; else echo 0; fi`;
chomp $ssh_exists;
if ($ssh_exists ne 1) {
    print "CAUTION: Unable to find ~/.ssh/id_dsa. Using default ssh locations\n";
    $SSH_IDENTITY = "";
}

# Setup Variables
my $SSH_ADMIN = $SSH . $SSH_OPTIONS . $SSH_IDENTITY . " admin\@$ADMIN_VIP";

# Initialize
open( LOG, ">$LOGFILE" )
  or die "ERR: Cannot Open $LOGFILE, error $!";

print_start_msg();

print_both("INFO: Logfile created at $LOGFILE");

print_both("INFO: Testing capability to send email...");
if (sendmail_test($EMAIL)) {
    print_both("ERR: UNABLE TO SEND EMAILS. EXITING SCRIPT");
    exit -1;
}

print_both("INFO: Pinging cluster...");
if ( !ping_test($ADMIN_VIP) ) {
    $CAN_PING_ADMIN = 0;
}

print_both("INFO: Detecting hive status...");
if ( detect_hive($ADMIN_VIP) ) {
    $IS_MULTI_CLUSTER = 1;
}

print_both("INFO: Getting initial status from $ADMIN_VIP...");
foreach (@CELL_IDS) {
    run_initial_cli_commands($_);
}

$IS_INITIALIZED = 1;

print_log("INFO: Sleeping for $INTERVAL seconds...");
sleep $INTERVAL;

# Run Tests on Cluster
while ($IS_OK_TO_PROCEED) {

    # To stop the script externally, create the empty file /tmp/stop_cluster_monitor
    if ( -e "/tmp/stop_cluster_monitor" ) {
        print_both("INFO: Exiting Cluster Monitor");
        exit 0;
    }

    # Display the iteration count
    my $date = `date`;
    chomp $date;
    print_header("$date\nStarting iteration $ITERATIONS");
    
    # Loop through each cell in the cluster
    foreach (@CELL_IDS) {

        # Make sure it is still alive
        $CAN_PING_ADMIN = (ping_test($ADMIN_VIP)) ? 1 : 0;
        if ($CAN_PING_ADMIN) {
            # Make sure CLI is still responsive
            cli_test($_);
        
            if ($IS_CLI_RESPONSIVE) {
            
                # Run all CLI commands on the cell
                run_cli_commands($_);

                # Compare results with the previous iteration
                compare_results($_);

                # Save the results for the next iteration
                while ( my ( $cell, $cellhash ) = each(%RETURNED_STATUS) ) {
                    while ( my ( $key, $value ) = each( %{$cellhash} ) ) {
                        $PREVIOUS_STATUS{$cell}{$key} = $value;
                    }
                }
            }
        }
    }
    
    if ($CAN_PING_ADMIN) {  
        if ($IS_CLI_RESPONSIVE) {
            print_status();
        } else {
            print_both("CLI is unresponsive");
        }
    } else {
        print_both("Cluster is unpingable");
    }
    # Sleep for the user-specified interval and iterate
    print_log("INFO: Sleeping for $INTERVAL seconds...");
    sleep $INTERVAL;
    $ITERATIONS++;
}

close LOG;
return 0;

##########################################################################
# Subroutines

# SENDMAIL_TEST
# Input: STRING of a valid email address
# Function: Sends a test email to the address specified by the input string
#  and also tests the SMTP_SERVER for responsiveness
sub sendmail_test {
    my $smtp =
      Net::SMTP->new( "$SMTP_SERVER", Hello => 'sun.com', Timeout => 30 );
    if ( !( defined $smtp ) ) {
        print_both(
            "WARN: Unable to send email using SMTP Server: $SMTP_SERVER");
        print_both("INFO: Using default SMTP Server...");
        $SMTP_SERVER = '129.147.62.198';
    }
    print_both("INFO: Sending test email to @_...");
    if (send_email( "TEST: Cluster Monitor Test Email  - $NAME [EOM]", "" )) {
        return -1; # Problem
    }
    return 0; # OK
}

# PING TEST
# Input: STRING of a machine to ping
# Function: Attempts to ping the machine specified by the input string.
#  It has 2 global settings, $PING_ATTEMPTS and $PING_TIMEOUT, both settable
#  in the global variables section above.  If the machine cannot be pinged,
#  an email is sent out and the script exits
sub ping_test {
    for ( my $attempt = $PING_ATTEMPTS ; $attempt > 0 ; $attempt-- ) {

        #If we can ping, return.
        system("$PING -c 1 $_[0] 2>&1 >> /dev/null");
        if ( !$? ) {
            # Only send email if cluster was not pingable
            if (!$CAN_PING_ADMIN) {
                send_email( "INFO: Cluster Monitor: Now able to ping cluster - $NAME [EOM]");
            }
            return 1; # OK
        }

        #If not, sleep then try again
        sleep $PING_TIMEOUT;
    }

    #Else, ping is unsuccessful
    print_both("ERR: UNABLE TO PING CLUSTER");
    my $msg = "ERR: Cluster monitor is unable to ping cluster - $ADMIN_VIP\n\n";
    if ($IS_INITIALIZED) {
        $msg .= "PREVIOUS STATUS: $PREVIOUS_STATUS{$MASTER_CELL}{'date'}\n";
        $msg .= "-------------------------------------------------------\n";
        $msg .= "$PREVIOUS_STATUS{$MASTER_CELL}{'sysstat'}\n";
    }
    # Only send email if this is the first time we went down
    if ($CAN_PING_ADMIN) { 
        send_email( "ERR: Cluster Monitor: Unable to ping cluster - $NAME",
            $msg );
    }
    return 0; # PROBLEM
}


# CLI TEST
# Input: INTEGER of a cell number
# Function: Attempts to run 'date' on the admin vip.
#  If the command is unresponsive, a state variable is set to stop further
#  testing until cli is back up.
# 
sub cli_test {
    my $cell = $_[0];
    my $date = system("$SSH_ADMIN date -c $cell 2>&1 >> /dev/null");
    if ($?) {
        send_email( "ERR: CLI is not responsive on cell $cell - $NAME [EOM]", "" );
        $IS_CLI_RESPONSIVE = 0;
    } else {
        if ($IS_CLI_RESPONSIVE == 0) {
            send_email( "INFO: CLI is responsive on cell $cell - $NAME [EOM]", "" );
        }
        $RETURNED_STATUS{$cell}{'date'} = $date;
        $IS_CLI_RESPONSIVE = 1;
    }
}

# DETECT_HIVE
# Input: STRING of a cluster name
# Funtion: First checks to see if the CLI is responsive.  If not, an email is sent
#  and the script exits.  If responsive, hiveadm is called to detect a multi-cell
#  configuration and to obtain cell numbers for the hashing of results.
sub detect_hive {
    my $hiveadm = `$SSH_ADMIN hiveadm`;
    if ($?) {
        send_email( "ERR: CLI is not functioning on $NAME [EOM]", "" );
        exit -1;
    }
    foreach ( split( /\n/, $hiveadm ) ) {
        if (/Cell (\d+)/) { push( @CELL_IDS, $1 ) }
    }
    foreach (@CELL_IDS) { print_log("- Detected cell $_") }
    $MASTER_CELL = $CELL_IDS[0];
    if ( $#CELL_IDS > 0 ) {
        return 1;
    }
    return 0;
}

# RUN_CLI_COMMANDS
# Input: INTEGER of a cell number
# Function: Runs the commands specified in @CLI_COMMANDS on the cell
#  specified by the input and stores the results in the %RETURNED_STATUS
#  hash.
sub run_cli_commands {
    my $cell = $_[0];

    # Loop through all the cli commands
    while ( my ( $command, $frequency ) = each(%CLI_COMMANDS) ) {
        if ( $ITERATIONS % $frequency == 0 ) {

            # Add cell argument to commands except where it is not supported
            my $cell_command =
              ( $command ne "hivecfg" && $command ne "mdconfig -l" )
              ? "-c $cell"
              : "";

            # Run command on cluster and save results
            print_log("INFO: Running $command $cell_command on $ADMIN_VIP...");
            $RETURNED_STATUS{$cell}{$command} =
              `$SSH_ADMIN $command $cell_command`;
            chomp $RETURNED_STATUS{$cell}{$command};

            print_log( $RETURNED_STATUS{$cell}{$command} );
        }
    }
}

# RUN_INITIAL_CLI_COMMANDS
# Input: INTEGER of a cell number
# Function: Runs the commands specified in @CLI_COMMANDS on the cell
#  specified by the input and stores the results in the %INITAL_STATUS
#  hash and %PREVIOUS_STATUS hash
sub run_initial_cli_commands {
    my $cell = $_[0];

    # Loop through all the cli commands
    while ( my ( $command, $frequency ) = each(%CLI_COMMANDS) ) {

        # Add cell argument to commands except where it is not supported
        my $cell_command =
          ( $command ne "hivecfg" && $command ne "mdconfig -l" )
          ? "-c $cell"
          : "";

        # Run command on cluster and save results
        print_log("INFO: Running $command $cell_command on $ADMIN_VIP...");
        $INITIAL_STATUS{$cell}{$command} = `$SSH_ADMIN $command $cell_command`;
        chomp $INITIAL_STATUS{$cell}{$command};

        # Also store in %PREVIOUS_STATUS
        $PREVIOUS_STATUS{$cell}{$command} = $INITIAL_STATUS{$cell}{$command};
        print_log( $INITIAL_STATUS{$cell}{$command} );
    }

    # Store the initial nodes, disks and query engine status
    if ( $INITIAL_STATUS{$cell}{"sysstat"} =~ /(\d+) nodes online/ ) {
        ( $INITIAL_NODES{$cell}, $PREVIOUS_NODES{$cell} ) = ( $1, $1 );
    }
    if ( $INITIAL_STATUS{$cell}{"sysstat"} =~ /(\d+) disks online/ ) {
        ( $INITIAL_DISKS{$cell}, $PREVIOUS_DISKS{$cell} ) = ( $1, $1 );
    }
    if ( $INITIAL_STATUS{$cell}{"sysstat"} =~ /Query Engine Status: (\w+)/ ) {
        ( $INITIAL_QUERY{$cell}, $PREVIOUS_QUERY{$cell} ) = ( $1, $1 );
    }
}

# COMPARE_RESULTS
# Input: INTEGER of a cell number
# Function: Loops over all of the keys (commands) in the %RETURNED_STATUS
#  hash and calls the subroutine written to compare that specific key (command)
sub compare_results {
    my $cell = $_[0];
    my $is_initial_state = 1;
    # Loop over all keys (commands)
    while ( ( my $key, my $value ) = each( %{ $RETURNED_STATUS{$cell} } ) ) {

        # Call comparators on the key (command)
        my $return = &{ $COMMAND_VERIFIER_TABLE{$key} }($cell);
        
        # Check the return code and set local state
        $is_initial_state = 0 if $return;
    }
    
    # Check if things are back to normal and send an email if so
    if (!$IS_INITIAL_STATE && $is_initial_state) {
        send_email("INFO: $NAME has recovered, initial state reached. [EOM]", "");
    }
    # Set Global State
    $IS_INITIAL_STATE = $is_initial_state;
}

# SEND_EMAIL
# Input: 2 STRINGS.  1st is the subject, 2nd is the body of the email
# Function: Opens sendmail, applies the scripts settings, and sends the email.
sub send_email {
    my ( $subject, $body ) = @_;
    my $smtp = Net::SMTP->new( "$SMTP_SERVER", Hello => 'sun.com' );
    if ( !( defined $smtp ) ) {
        system("$PING -c 1 $SMTP_SERVER 2>&1 >> /dev/null");
        if ($?) {    # Server is responsive but unable to send emails
            print_both("ERR: Unable to send emails using $SMTP_SERVER. (Not a SMTP Server?)");
            exit -1;
        }
        else {       # Server is unresponsive
            print_both(
                "CAUTION: SMTP_SERVER $SMTP_SERVER is unresponsive. (Server down?)"
            );
            print_both("CAUTION: Could not send email to $EMAIL.");
            return -1;
        }
    }
    
    $smtp->mail("CLUSTER_MONITOR");
    $smtp->to($EMAIL);
    $smtp->data();
    $smtp->datasend("TO: $EMAIL");
    $smtp->datasend("\n");
    $smtp->datasend("SUBJECT: $subject");
    $smtp->datasend("\n");
    $smtp->datasend($body);
    $smtp->dataend();
    $smtp->quit;
    return 0;
}

##############################################################################
# Test Comparators

sub compare_date      { }    # Output Only
sub compare_version   { }    # Output Only
sub compare_df_h      { }    # Output Only
sub compare_sensors   { }    # Output Only
sub compare_perfstats { }    # Output Only

# COMPARE_SYSSTAT
# Input: INTEGER of the cell number
# Function: Looks at the number of Nodes, Disks, HADB status and Free Space,
#  compares it with the previous iterations results and sends an email if anything
#  is alarming.
sub compare_sysstat {
    my $cell = $_[0];
    my $status = 0;

    # Collect Info

    if ( $RETURNED_STATUS{$cell}{"sysstat"} =~ /(\d+) nodes online/ ) {
        $RETURNED_NODES{$cell} = $1;
    }
    if ( $RETURNED_STATUS{$cell}{"sysstat"} =~ /(\d+) disks online/ ) {
        $RETURNED_DISKS{$cell} = $1;
    }
    if ( $RETURNED_STATUS{$cell}{"sysstat"} =~ /Query Engine Status: (\w+)/ ) {
        $RETURNED_QUERY{$cell} = $1;
    }
    if ( $RETURNED_STATUS{$cell}{"sysstat"} =~ /Free Space 0.00/ ) {
        $IS_FULL{$cell} = 1;
    }
    else {
        $IS_FULL{$cell} = 0;
    }

    # Have we lost any nodes?
    if ( $RETURNED_NODES{$cell} < $INITIAL_NODES{$cell} ) {
        
        # Mark the status to return a non-zero code
        $status = 1;
        # Don't send email if we have already seen the problem
        if ( $RETURNED_NODES{$cell} < $PREVIOUS_NODES{$cell} ) {
            my @failed_nodes = ();
            foreach ( split( /\n/, $RETURNED_STATUS{$cell}{'sysstat -v'} ) ) {
                chomp;
                if (/^NODE.*\[OFFLINE\]/) { push( @failed_nodes, $_ ); }
            }
            @failed_nodes = join( "\n", @failed_nodes );
            send_email(
                "CAUTION: Cluster Monitor: Offline Node Detected - CELL $cell - $NAME",
                    "Cluster monitor has detected that one or more nodes have gone offline.\n\n"
                .   "@failed_nodes\n\n"
                .   "PREVIOUS STATUS: $PREVIOUS_STATUS{$cell}{'date'}\n"
                .   "-----------------------------------------------------\n"
                .   "$PREVIOUS_STATUS{$cell}{'sysstat'}\n\n"
                .   "CURRENT STATUS: $RETURNED_STATUS{$cell}{'date'}\n"
                .   "-----------------------------------------------------\n"
                .   "$RETURNED_STATUS{$cell}{'sysstat'}"
            );
        }
    }

    # Have we lost any disks?
    if ( $RETURNED_DISKS{$cell} < $INITIAL_DISKS{$cell} ) {

        # Mark the status to return a non-zero code
        $status = 1;
        # Dont send email if we al{ready know about the problem
        if ( $RETURNED_DISKS{$cell} < $PREVIOUS_DISKS{$cell} ) {
            my @failed_disks = ();
            foreach ( split( /\n/, $RETURNED_STATUS{$cell}{'sysstat -v'} ) ) {
                chomp;
                if (/^Disk.*\[OFFLINE\]/) { push( @failed_disks, $_ ); }
            }
            @failed_disks = join( "\n", @failed_disks );
            send_email(
                "CAUTION: Cluster Monitor: Offline Disk Detected - CELL $cell - $NAME",
                    "Cluster monitor has detected that one or more disks have gone offline.\n\n"
                .   "@failed_disks\n\n"
                .   "PREVIOUS STATUS: $PREVIOUS_STATUS{$cell}{'date'}\n"
                .   "-----------------------------------------------------\n"
                .   "$PREVIOUS_STATUS{$cell}{'sysstat'}\n\n"
                .   "CURRENT STATUS: $RETURNED_STATUS{$cell}{'date'}\n"
                .   "-----------------------------------------------------\n"
                .   "$RETURNED_STATUS{$cell}{'sysstat'}"
            );
        }
    }

    # Has the HADB Status changed?
    if ( $RETURNED_QUERY{$cell} ne $INITIAL_QUERY{$cell} ) {

        # Mark the status to return a non-zero code
        $status = 1;
        # Dont send email if we already know about the problem
        if ( $RETURNED_QUERY{$cell} ne $PREVIOUS_QUERY{$cell} ) {
            send_email(
                "CAUTION: Cluster Monitor: HADB Status Changed - CELL $cell - $NAME",
                    "Cluster monitor has detected that there has been a change in the Query Engine Status.\n\n"
                .   "PREVIOUS STATUS: $PREVIOUS_STATUS{$cell}{'date'}\n"
                .   "-----------------------------------------------------\n"
                .   "$PREVIOUS_STATUS{$cell}{'sysstat'}\n\n"
                .   "CURRENT STATUS: $RETURNED_STATUS{$cell}{'date'}\n"
                .   "-----------------------------------------------------\n"
                .   "$RETURNED_STATUS{$cell}{'sysstat'}"
            );
        }
    }

    # Is the cluster full?
    if ( $IS_FULL{$cell} ) {
        send_email(
            "INFO: $NAME is full",
            "Cluster monitor has detected that the cluster is at full capacity."
        );
    }

    # Store the RETURNED values into the previous
    $PREVIOUS_NODES{$cell} = $RETURNED_NODES{$cell};
    $PREVIOUS_DISKS{$cell} = $RETURNED_DISKS{$cell};
    $PREVIOUS_QUERY{$cell} = $RETURNED_QUERY{$cell};
    
    return 1 if ($status); # Non Initial State
    return 0; # Initial State
}

# COMPARE_SYSSTAT_V
# Input: INTEGER of the cell number
# Function: Looks at the output of sysstat -v and compares it with the output
#  from the previous iteration.  Script send an email if anything is different
sub compare_sysstat_v {
    my $cell = $_[0];
    if ( $INITIAL_STATUS{$cell}{"sysstat -v"} ne
        $RETURNED_STATUS{$cell}{"sysstat -v"} )
    {

        # Don't send an email if we already have seen the problem
        if ( $PREVIOUS_STATUS{$cell}{"sysstat -v"} eq
            $RETURNED_STATUS{$cell}{"sysstat -v"} )
        {
            return 1;
        }
        send_email(
            "CAUTION: Cluster Monitor: System Status has changed - CELL $cell - $NAME",
                "Cluster Monitor had detected a change in the system status (sysstat -v).\n\n"
            .   "PREVIOUS STATUS: $PREVIOUS_STATUS{$cell}{'date'}\n"
            .   "-----------------------------------------------------\n"
            .   "$PREVIOUS_STATUS{$cell}{'sysstat -v'}\n\n"
            .   "CURRENT STATUS: $RETURNED_STATUS{$cell}{'date'}\n"
            .   "-----------------------------------------------------\n"
            .   "$RETURNED_STATUS{$cell}{'sysstat -v'}"
        );
        return 1; # Non Initial State
    }
    return 0; # Initial State
}

# COMPARE_CELLCFG
# Input: INTEGER of the cell number
# Function: Looks at the output of cellcfg and compares it with the output
#  from the previous iteration.  Script send an email if anything is different
sub compare_cellcfg {
    my $cell = $_[0];
    if ( $INITIAL_STATUS{$cell}{"cellcfg"} ne
        $RETURNED_STATUS{$cell}{"cellcfg"} )
    {

        # Don't send an email if we already have seen the problem
        if ( $PREVIOUS_STATUS{$cell}{"cellcfg"} eq
            $RETURNED_STATUS{$cell}{"cellcfg"} )
        {
            return 1;
        }
        send_email(
            "CAUTION: Cell Configuration Changed - CELL $cell - $NAME",
                "Cluster monitor had detected a change in the cell configuration (cellcfg).\n\n"
            .   "PREVIOUS STATUS: $PREVIOUS_STATUS{$cell}{'date'}\n"
            .   "-----------------------------------------------------\n"
            .   "$PREVIOUS_STATUS{$cell}{'cellcfg'}\n\n"
            .   "CURRENT STATUS: $RETURNED_STATUS{$cell}{'date'}\n"
            .   "-----------------------------------------------------\n"
            .   "$RETURNED_STATUS{$cell}{'cellcfg'}"
        );
        return 1; # Non Initial State
    }
    return 0; # Initial State
}

# COMPARE_VERSION_V
# Input: INTEGER of the cell number
# Function: Looks at the output of version -v and compares it with the output
#  from the previous iteration.  Script send an email if anything is different
sub compare_version_v {
    my $cell = $_[0];
    if ( $INITIAL_STATUS{$cell}{"version -v"} ne
        $RETURNED_STATUS{$cell}{"version -v"} )
    {

        # Don't send an email if we already have seen the problem
        if ( $PREVIOUS_STATUS{$cell}{"version -v"} eq
            $RETURNED_STATUS{$cell}{"version -v"} )
        {
            return 1;
        }
        send_email(
            "CAUTION: Version has changed - CELL $cell - $NAME",
                "Cluster monitor had detected a change in the cell version (version -v).\n\n"
            .   "PREVIOUS STATUS: $PREVIOUS_STATUS{$cell}{'date'}\n"
            .   "-----------------------------------------------------\n"
            .   "$PREVIOUS_STATUS{$cell}{'version -v'}\n\n"
            .   "CURRENT STATUS: $RETURNED_STATUS{$cell}{'date'}\n"
            .   "-----------------------------------------------------\n"
            .   "$RETURNED_STATUS{$cell}{'version -v'}"
        );
        return 1; # Non Initial State
    }
    return 0; # Initial State
}

# COMPARE_HIVECFG
# Input: INTEGER of the cell number
# Function: Looks at the output of hivecfg and compares it with the output
#  from the previous iteration.  Script send an email if anything is different
sub compare_hivecfg {
    my $cell = $_[0];
    if ( $INITIAL_STATUS{$cell}{"hivecfg"} ne
        $RETURNED_STATUS{$cell}{"hivecfg"} )
    {

        # Don't send an email if we already have seen the problem
        if ( $PREVIOUS_STATUS{$cell}{"hivecfg"} eq
            $RETURNED_STATUS{$cell}{"hivecfg"} )
        {
            return 1;
        }
        send_email(
            "CAUTION: Cluster monitor: Hive Configuration Changed - CELL $cell - $NAME",
                "Cluster monitor had detected a change in the hive configuration (hivecfg).\n\n"
            .   "PREVIOUS STATUS: $PREVIOUS_STATUS{$cell}{'date'}\n"
            .   "-----------------------------------------------------\n"
            .   "$PREVIOUS_STATUS{$cell}{'hivecfg'}\n\n"
            .   "CURRENT STATUS: $RETURNED_STATUS{$cell}{'date'}\n"
            .   "-----------------------------------------------------\n"
            .   "$RETURNED_STATUS{$cell}{'hivecfg'}"
        );
        return 1; # Non Initial State
    }
    return 0; # Initial State
}

# COMPARE_HWSTAT
# Input: INTEGER of the cell number
# Function: Looks at the output of HWSTAT and compares it with the output
#  from the previous iteration.  Script send an email if anything is different
sub compare_hwstat {
    my $cell = $_[0];
    if ( $INITIAL_STATUS{$cell}{"hwstat"} ne $RETURNED_STATUS{$cell}{"hwstat"} )
    {

        # Don't send an email if we already have seen the problem
        if ( $PREVIOUS_STATUS{$cell}{"hwstat"} eq
            $RETURNED_STATUS{$cell}{"hwstat"} )
        {
            return 0;
        }
        send_email(
            "CAUTION: Cluster monitor: Hardware Statistics has changed - CELL $cell - $NAME",
                "Cluster monitor had detected a change in the hardware (hwstat).\n\n"
            .   "PREVIOUS STATUS: $PREVIOUS_STATUS{$cell}{'date'}\n"
            .   "-----------------------------------------------------\n"
            .   "$PREVIOUS_STATUS{$cell}{'hwstat'}\n\n"
            .   "CURRENT STATUS: $RETURNED_STATUS{$cell}{'date'}\n"
            .   "-----------------------------------------------------\n"
            .   "$RETURNED_STATUS{$cell}{'hwstat'}"            
        );
        return 1; # Non Initial State
    }
    return 0; # Initial State
}

# COMPARE_DDCFG
# Input: INTEGER of the cell number
# Function: Looks at the output of ddcfg --force and compares it with the output
#  from the previous iteration.  Script send an email if anything is different
sub compare_ddcfg {
    my $cell = $_[0];
    if ( $INITIAL_STATUS{$cell}{"ddcfg --force"} ne $RETURNED_STATUS{$cell}{"ddcfg --force"} ) {

        # Don't send an email if we already have seen the problem
        if ( $PREVIOUS_STATUS{$cell}{"ddcfg --force"} eq
            $RETURNED_STATUS{$cell}{"ddcfg --force"} )
        {
            return 1;
        }
        send_email(
            "CAUTION: Cluster monitor: Data Doctor configuration has changed - CELL $cell - $NAME",
                "Cluster monitor had detected a change in the data doctor configuration (ddcfg --force).\n\n"
            .   "PREVIOUS STATUS: $PREVIOUS_STATUS{$cell}{'date'}\n"
            .   "-----------------------------------------------------\n"
            .   "$PREVIOUS_STATUS{$cell}{'ddcfg --force'}\n\n"
            .   "CURRENT STATUS: $RETURNED_STATUS{$cell}{'date'}\n"
            .   "-----------------------------------------------------\n"
            .   "$RETURNED_STATUS{$cell}{'ddcfg --force'}"
        );
        return 1; # Non Initial State
    }
    return 0; # Initial State
}

# COMPARE_MDCONFIG_L
# Input: INTEGER of the cell number
# Function: Looks at the output of mdconfig -l and compares it with the output
#  from the previous iteration.  Script send an email if anything is different
sub compare_mdconfig_l {
    my $cell = $_[0];
    if ( $INITIAL_STATUS{$cell}{"mdconfig -l"} ne
        $RETURNED_STATUS{$cell}{"mdconfig -l"} )
    {

        # Don't send an email if we already have seen the problem
        if ( $PREVIOUS_STATUS{$cell}{"mdconfig -l"} eq
            $RETURNED_STATUS{$cell}{"mdconfig -l"} )
        {
            return 1;
        }
        send_email(
            "CAUTION: Cluster monitor: Metadata Configuration Changed - CELL $cell - $NAME",
                "Cluster monitor had detected a change in the metadata configuration (mdconfig -l).\n\n"
            .   "PREVIOUS STATUS: $PREVIOUS_STATUS{$cell}{'date'}\n"
            .   "-----------------------------------------------------\n"
            .   "$PREVIOUS_STATUS{$cell}{'mdconfig -l'}\n\n"
            .   "CURRENT STATUS: $RETURNED_STATUS{$cell}{'date'}\n"
            .   "-----------------------------------------------------\n"
            .   "$RETURNED_STATUS{$cell}{'mdconfig -l'}"
        );
        return 1; # Non Initial State
    }
    return 0; # Initial State
}

###############################################################################
# Printing and Usage

sub print_log {
    print LOG "@_\n";
}

sub print_both {
    print "@_\n";
    print_log("@_");
}

sub print_header {
    print_both("");
    print_both( "=" x 80 );
    print_both("");
    print_both("@_");
    print_both("");
    print_both( "=" x 80 );
    print_both("");
}

sub print_start_msg {
    my $date = `date`;
    chomp $date;
    my $msg =
        "$date\n"
      . "STARTING CLUSTER MONITORING UTILITY\n"
      . "ADMIN VIP: $ADMIN_VIP\n"
      . "EMAIL ADDRESS: $EMAIL\n"
      . "INTERVAL (in seconds): $INTERVAL\n"
      . "SMTP SERVER: $SMTP_SERVER\n"
      . "NAME: $NAME";
    print_header($msg);
}

sub print_status {
    my ( $cell1, $nodes1, $disks1, $query1, $cell2, $nodes2, $disks2, $query2 );
    $cell1 = $CELL_IDS[0];
    ( $nodes1, $disks1, $query1 ) = (
        $RETURNED_NODES{$cell1}, $RETURNED_DISKS{$cell1},
        $RETURNED_QUERY{$cell1}
    );
    ( $cell2, $nodes2, $disks2, $query2 ) = ( "N/A", "N/A", "N/A", "N/A" );
    if ($IS_MULTI_CLUSTER) {
        $cell2 = $CELL_IDS[1];
        ( $nodes2, $disks2, $query2 ) = (
            $RETURNED_NODES{$cell2}, $RETURNED_DISKS{$cell2},
            $RETURNED_QUERY{$cell2}
        );
    }

    format STDOUT =     
CELL                | @<<<<<<<<<<<<<< | @<<<<<<<<<<<<<<
                      $cell1,            $cell2
-------------------------------------------------------
Nodes Online        | @<<<<<<<<<<<<<< | @<<<<<<<<<<<<<<
                      $nodes1,           $nodes2
Disks Online        | @<<<<<<<<<<<<<< | @<<<<<<<<<<<<<<
                      $disks1,           $disks2
Query Engine Status | @<<<<<<<<<<<<<< | @<<<<<<<<<<<<<<
                      $query1,           $query2
.
    write;
}

# Usage prints if input is wrong
sub usage {
    print "\n";
    print "NAME\n";
    print "\n";
    print "    cluster_monitor.pl\n";
    print "\n";
    print "SYNOPSIS\n";
    print "\n";
    print "    Automated Cluster Monitoring Tool\n";
    print "\n";
    print "DESCRIPTION\n";
    print "\n";
    print "    cluster_monitor.pl is a script that is designed to be run in parallel with other\n";
    print "    tests and monitor the status of the cluster being tested.  If something goes\n";
    print "    wrong with the cluster (nodes or disks fail etc...) then the tester will be\n";
    print "    notified of this event.  The script is organized to easily add/remove CLI commands\n";
    print "    whenever necessary.  The script take 3 mandatory arguments at the command line:\n";
    print "     1) the admin vip to run the script on,\n";
    print "     2) an email address to send alerts to,\n";
    print "     3) a time interval (in seconds) for which the script will sleep between iterations.\n";
    print "    The script also takes one optional argument: an ip address for a smtp server to send the emails\n";
    print "\n";
    print "USAGE\n";
    print "\n";
    print "    ./cluster_monitor.pl [-h] -a <ADMIN_VIP> -e <EMAIL> -t <TIME_INTERVAL> -s <SMTP_SERVER (optional)>\n";
    print "\n";
    exit 0;
}


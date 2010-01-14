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
# This test can only be run on clusters in SFO07 test lab
#
# FEATURE SPECS REFERENCED
#  https://hc-twiki.sfbay.sun.com/twiki/bin/view/Main/WebHome?topic=AlertList
#
# DESCRIPTION:
# run_alert_tests.pl is a script designed to test the email alert system.
#  It runs a series of commands that will trigger email alerts, then tests
#  that the system sent out the alert.  The clusters alert- email will be
#  changed to a temp email box, then back to the original value at the end
#  of the script, along with any other settings changed by the script. The
#  script takes 3 mandatory argument: the admin_vip, cheat_vip and the mailbox
#  we are scraping.

#  COMMAND LINE
#  /run_alert_tests.pl [hql] -a <ADMIN_VIP> -c <CHEAT_VIP> -m <MAILBOX> [-o TEST_NUMBER] [-s TEST_NUMBER] [-r ITERATIONS]
#
#  ADMIN_VIP: ip address for your admin node
#   EXAMPLE: 10.7.226.61
#
#  CHEAT_VIP: ip address for your service processor
#   EXAMPLE: 10.7.226.60
#
#  MAILBOX: the name of your cluster
#   EXAMPLE: dev325 (if dev325 corresponds to th ip's you entered in admin
#            and cheat)
#
#  OPTIONS:
#  -h: help => usage
#  -q: quick => Don't run upgrade
#  -l: list => Gets list of currently run tests and test numbers
#  -o TEST_NUMBER: only => Only run the test that corresponds to the test number
#  -s TEST_NUMBER: start => Start the testing from this test.
#  -r INTEGER: repeat => Run the test <repeat> times (Most useful with -o) '-1' for infinite.
#  *Note options -o and -s cannot be used together
#  *Note: when using -o or -s, make sure alertcfg is configured to send emails to <MAILBOX>.
#
#  EXAMPLE CALL:
#  /run_alert_tests.pl -a 10.7.226.61 -c 10.7.226.60 -m dev325
#
#
#  MAINTENANCE:
#  To add future tests you must do 6 things:
#  1) Add a line to the ALERT_TRIGGER_TABLE, with references to subroutines
#  2) If non-multicell command, put title in MULTICELL_EXCLUDE_HASH
#  3) Add TRIGGER_COMMAND_SUBROUTINE*
#  4) Add PRE_ALERT_SUBROUTINE*
#  5) Add POST_ALERT_SUBROUTINE*
#  6) Add CLEANUP_SUBROUTINE*
#  *NOTE: If no subroutine is necessary, use \&null_subroutine

use strict;
use Getopt::Std;
use Net::SMTP;

###########################################
# Unix commands
my $PING = "ping";
my $SSH  = "ssh";
my $WGET = 'wget';

###########################################
# Globals

# Set File Locations and Commands
my $LOGFILE             = "/tmp/run_alert_tests_$$.out";
my $SWITCH_CONF_FILE    = "/etc/honeycomb/switch.conf";
my $IPMI_PASS_LOCATION  = "/opt/honeycomb/share/ipmi-pass";
my $IPMI_PASS_DIRECTORY = "/opt/honeycomb/share";
my $FSCK1               = "/config/clean_unmount__dev_dsk_c0t0d0s4";
my $FSCK2               = "/config/clean_unmount__dev_dsk_c0t1d0s4";
my $FSCK3               = "/config/clean_unmount__dev_dsk_c1t0d0s4";
my $FSCK4               = "/config/clean_unmount__dev_dsk_c1t1d0s4";
my $NOHONEYCOMB         = "/config/nohoneycomb";
my $NOREBOOT            = "/config/noreboot";
my $MESSAGES            = "/var/adm/messages";
my $MESSAGES_0          = "/var/adm/messages.0";
my $DEV_NULL            = "2>&1 >> /dev/null";
my $PING_SW1            = "ping -c 1 10.123.45.1";
my $SWITCH2_DATE        = "/usr/bin/run_cmd_other.sh date";

# SSH
my $SSH_OPTIONS = ' -q -i ~/.ssh/id_dsa -o StrictHostKeyChecking=no';
my $MAIL_SERVER = "hclog301.sfbay.sun.com";
my $MAIL_BOX    = "";
my $MAIL_CHECK  = "";
my $MAIL_ERASE  = "";
my $MAIL_TOUCH  = "";
my $SSH_EMAIL   = $SSH . $SSH_OPTIONS . " root\@$MAIL_SERVER";
my $SSH_QUIET   = "2>&1 >> /dev/null";
my $SSH_SWITCH  = 'ssh -p 2222 -l nopasswd 10.123.45.1';

# Variables for switching IP's
my $HONEYSTOR_CHEAT     = "10.7.226.220";
my $HONEYSTOR_ADMIN     = "10.7.226.221";
my $HONEYSTOR_DATA      = "10.7.226.222";
my $SSH_HONEYSTOR_CHEAT = $SSH . $SSH_OPTIONS . " root\@$HONEYSTOR_CHEAT";
my $SSH_HONEYSTOR_ADMIN = $SSH . $SSH_OPTIONS . " admin\@$HONEYSTOR_ADMIN";
my $SSH_HONEYSTOR_DATA  = $SSH . $SSH_OPTIONS . " root\@$HONEYSTOR_DATA";

# User Set
my $SSH_ADMIN           = "";
my $SSH_CHEAT           = "";
my $ADMIN_VIP           = "";
my $CHEAT_VIP           = "";
my $ORIGINAL_ADMIN      = "";
my $ORIGINAL_CHEAT      = "";
my $ORIGINAL_ADMIN_2    = "";
my $ORIGINAL_CHEAT_2    = "";
my $SSH_MASTER          = "";
my $MASTER_NODE_1       = "";
my $MASTER_NODE_2       = "";
my $NO_UPGRADE          = 0;
my $ONLY_TEST           = -1;
my $START_FROM          = -1;
my $REPEAT              = 0;

# Fixed Variables
my $NODE_NUM_OFFSET  = 101;
my $NODE_PORT_OFFSET = 2001;
my $MAX_NUMBER_DISKS = 4;
my %APC_HASH = (
    "dev309-admin"  => "10.7.224.186",
    "10.7.224.181"  => "10.7.224.186",
    "dev308-admin"  => "10.7.224.166",
    "10.7.224.161"  => "10.7.224.166",
    "dev319-admin"  => "10.7.225.166",
    "10.7.225.161"  => "10.7.225.166"
);

# Boolean
my $CAN_PING_CHEAT    = 0;
my $CAN_PING_ADMIN    = 0;
my $IS_CLI_RESPONSIVE = 0;
my $IS_MULTI_CLUSTER  = 0;

# Time Values (seconds)
my $DEFAULT_EVAL_TIMEOUT    = 60;
my $SLEEP_DOT               = 30;
my $SLEEP_ONLINE            = 60;
my $SLEEP_EMAIL             = 300;
my $SLEEP_NODE_DOWN         = 60;
my $SLEEP_DISK_DOWN         = 30;
my $SLEEP_SWITCH            = 60;
my $SLEEP_SWITCH_FAILOVER   = 600;
my $SLEEP_SWITCH_FAILBACK   = 600;
my $SLEEP_ALT_SWITCH_REBOOT = 60;
my $SLEEP_SHUTDOWN          = 180;
my $SLEEP_REBOOT            = 180;
my $SLEEP_REBOOT_ALL        = 300;
my $SLEEP_RECOVER           = 300;
my $SLEEP_CAPACITY_SHUTDOWN = 180;
my $SLEEP_PING              = 5;
my $SLEEP_WIPE              = 1800;
my $SLEEP_CHEAT_DOWN        = 600;
my $PING_TIMEOUT            = 5;
my $WIPE_TIMEOUT            = 600;
my $REBOOT_TIMEOUT          = 600;
my $SHUTDOWN_TIMEOUT        = 600;
my $CHANGE_IPS_TIMEOUT      = 600;
my $NEW_SCHEMA_TIMEOUT      = 600;
my $UPGRADE_TIMEOUT         = 4400;
my $WGET_TIMEOUT            = 3600;
my $SMTP_TIMEOUT            = 600;
my $DATE_TIMEOUT            = 30;
my $TIME_STARTED            = time();

# Iterations
my $MAX_SYSSTAT_ONLINE_ITERATIONS   = 60;
my $MAX_HC_DOWN_ITERATIONS          = 20;
my $MAX_HC_UP_ITERATIONS            = 20;
my $MAX_NODE_ENABLE_ITERATIONS      = 20;
my $MAX_HADB_RECOVERY_ITERATIONS    = 30;
my $MAX_CHEAT_DOWN_ITERATIONS       = 20;
my $MAX_CHEAT_UP_ITERATIONS         = 20;
my $MAX_CHEAT_RESPONSIVE_ITERATIONS = 30;
my $MAX_SWITCH_DOWN_ITERATIONS      = 20;
my $MAX_PING_ITERATIONS             = 10;
my $MAX_WIPE_ITERATIONS             = 2;
my $MAX_REBOOT_ITERATIONS           = 2;
my $MAX_SHUTDOWN_ITERATIONS         = 2;
my $MAX_CHANGE_IPS_ITERATIONS       = 2;
my $MAX_NEW_SCHEMA_ITERATIONS       = 2;
my $MAX_UPGRADE_ITERATIONS          = 1;
my $MAX_SMTP_ITERATIONS             = 5;
my $PING_ATTEMPTS                   = 1;
my $DEFAULT_EVAL_ITERATIONS         = 20;

# Status Counters
my $TEST_QUANTITY   = 0;
my $TEST_PASSED     = 0;
my $TEST_FAILED     = 0;
my $TEST_MANUAL     = 0;
my @PASS_FAIL_ARRAY = ();
my $WARNING_FLAG    = 0;
my $IS_WARNING      = 0;

# System State
my $NODES               = 16;
my $DISKS               = 64;
my $QUERY               = "";
my $SWITCH              = 1;
my @CELL_IDS            = ();
my $MASTER_CELL         = 0;
my $MASTER_NODE         = 0;
my $CURRENT_CELL        = 0;
my $SAVED_LOG_LINE_NUM  = 0;
my $PREV_LOG_LINE_NUM   = 0;
my $ORIGINAL_CHEAT_IP   = "";
my $ORIGINAL_ADMIN_IP   = "";
my $ORIGINAL_DATE_IP    = "";
my $ORIGINAL_ALERTCFG   = "";

# Alerts and Restore
# This will hold the mail file /var/spool/mail/dev325 unsplit;
my $EMAIL                 = "";
# This will hold the mail $ALERTS{<TITLE>} => ((date1, subject1, msg1)...)
my %ALERTS                = ();
# This will hold commands that need to be run in cleanup
my %RESTORE_COMMANDS      = ();
my $DISK_STRING           = "";
my $NODE_STRING           = "";
my $ALTERNATE_SMTP_SERVER = "129.145.155.42";
my $ORIGINAL_SMTP_SERVER  = "";

# IPMI
my $IPMI_SP_POWER_OFF = "/usr/sfw/bin/ipmitool -I lan -f $IPMI_PASS_LOCATION"
  . " -H hcb100-sp -U Admin chassis power off";
my $IPMI_SP_POWER_ON = "/usr/sfw/bin/ipmitool -I lan -f $IPMI_PASS_LOCATION"
  . " -H hcb100-sp -U Admin chassis power on";

# New Schema File
my $NEW_SCHEMA = 
'<?xml version="1.0" encoding="UTF-8"?>

<!-- 

  $Id: metadata_config_mp3demo.xml 10855 2007-05-19 02:54:08Z bberndt $

  Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
  Use is subject to license terms.

For the emulator: use /opt/honeycomb/bin/metadata_merge_config.sh 
to add this config to the existing one -->

<metadataConfig>
  <schema>
    
    <namespace name="mp3" writable="true" extensible="true">
      <field name="title" type="string" length="150" indexable="true"/>
      <field name="artist" type="string" length="150" indexable="true"/>
      <field name="album" type="string" length="150" indexable="true"/>
      <field name="date" type="long" length="150" indexable="true"/>
      <field name="type" type="string" length="150" indexable="true"/>
    </namespace>
    
  </schema>
  
  <fsViews>
    <fsView name="byArtist" filename="${title}.${type}" namespace="mp3"
            fsattrs="true">
      <attribute name="artist"/>
      <attribute name="album"/>
    </fsView>
  </fsViews>

  <tables>
    <table name="mp3">
      <column name="mp3.title" />
      <column name="mp3.artist" />
      <column name="mp3.album" />
      <column name="mp3.date" />
      <column name="mp3.type" />
    </table>
  </tables>

</metadataConfig>';

###############################################################################
###############################################################################
###############################################################################

# Test Script Variables

# Alert Trigger Table holds rows of commands.  Each command has a
#  reference to a corresponding pre-command, pre-alert, post-alert
#  and cleanup subroutine. The PRE COMMAND SBOUTINE is called to
#  store previous values if any will be changed by the command. The
#  PRE ALERT SUBROUTINE is called to determine if the command should
#  have triggered the event (sleep(XX), cli checking etc...). The
#  POST ALERT SUBROUTINE is called after the mailbox has been scraped
#  and will verify that the alert was sent. The CLEANUP SUBROUTINE is
#  called after all tests and will restore the cluster to its initial
#  configuration.

my @ALERT_TRIGGER_TABLE = (

#["<TITLE>", \&<TRIGGER_ALERT_SUBROUTINE>, \&<PRE_ALERT_SUBROUTINE>, \&<POST_ALERT_SUBROUTINE>, \&<CLEANUP_SUBROUTINE>]

    # This works, but only for 1 to address and one cc address
    ["Change alert email address", \&alertcfg, \&null_subroutine, \&null_subroutine, \&cleanup_alertcfg], # Mandatory

    # Works... should do first, needs a HAFaultTolerant query engine status
    ["Update Schema", \&update_schema, \&pre_alert_update_schema, \&post_alert_update_schema, \&null_subroutine],

    # Works
    ["Disable Disk", \&disable_disk, \&pre_alert_disable_disk, \&post_alert_disable_disk, \&null_subroutine],

    # Works
    ["Reboot Single Node", \&node_down_up, \&pre_alert_node_down_up, \&post_alert_node_down_up, \&null_subroutine],

    # Works
    ["Reboot Master Node", \&master_down_up, \&pre_alert_master_down_up, \&post_alert_master_down_up, \&null_subroutine],

    # Works
    ["Power Node Off", \&power_node, \&pre_alert_power_node, \&post_alert_power_node, \&null_subroutine],

    # Works
    #["Reboot Switch 1", \&reboot_sw1, \&pre_alert_reboot_sw1, \&post_alert_reboot_sw1, \&null_subroutine],
    
    # Works
    #["Reboot Switch 2", \&reboot_sw2, \&pre_alert_reboot_sw2, \&post_alert_reboot_sw2, \&null_subroutine],
    
    # Works
    ["Switch 1 Failover", \&sw1_failover, \&pre_alert_sw1_failover, \&post_alert_sw1_failover, \&null_subroutine],

    # Works
    ["Switch 2 Failover", \&sw2_failover, \&pre_alert_sw2_failover, \&post_alert_sw2_failover, \&null_subroutine],
     
    # Works
    ["Reboot Service Processor", \&reboot_cheat, \&pre_alert_reboot_cheat, \&post_alert_reboot_cheat, \&null_subroutine],

    # Works
    ["Reboot Honeycomb", \&reboot_hc, \&pre_alert_reboot_hc, \&post_alert_reboot_hc, \&null_subroutine],

    # Works
    ["Reboot All", \&reboot_all, \&pre_alert_reboot_all, \&post_alert_reboot_all, \&null_subroutine],

    # Works
    ["Capacity Reached", \&change_capacity, \&pre_alert_change_capacity, \&post_alert_change_capacity, \&null_subroutine],

    # Works but fails... No command works to run it.
    ["Clear Schema", \&clear_schema, \&null_subroutine, \&post_alert_clear_schema, \&null_subroutine],

    # Works
    ["Shutdown Cluster", \&shutdown_cluster, \&pre_alert_shutdown_cluster, \&post_alert_shutdown_cluster, \&null_subroutine],

    # Works
    ["Wipe Honeycomb", \&wipe_hc, \&pre_alert_wipe_hc, \&post_alert_wipe_hc, \&null_subroutine],

    # Works but fails... No email sent... Should be near the end: alternate smtp server is unreliable
    ["Change SMTP Server", \&change_smtp, \&pre_alert_change_smtp, \&post_alert_change_smtp, \&cleanup_smtp],

    # Works but fails... Get all alerts except for Service Processor changed
    ["Change IP Addresses", \&change_ips, \&pre_alert_change_ips, \&post_alert_change_ips, \&cleanup_ips],
    
    # Works... should be near the end so we are testing on the desired version
    ["Upgrade Cell", \&upgrade_cell, \&pre_alert_upgrade_cell, \&post_alert_upgrade_cell, \&null_subroutine],
    
);

# Expected Failure Hash
my %EXPECTED_FAILURE_HASH = (
    "Change SMTP Server" => "No bug filed yet... Does alert even exist?",
    "Clear Schema" => "CLI command to trigger alert was removed",
    "Reboot Switch 1" => "Satish is working on it.  No alerts sent while on Switch 2",
    "Reboot Switch 2" => "5 Minute issue, no fix coming, need to test on APC or manually"
);

# Omitted / Impossible to test
# -----------------------------
# smtp port - NOT TESTED  --  There is no other port to use on hclog301
# subnet - NOT TESTED -- Too destructive to test
# gateway - NOT TESTED -- Too destructive to test
# password - WORKED (1.1-69) -- no way to test w/o user input
# cluster is no longer full -- no way to test by changing the property values.
# cluster is becoming full -- no way to test besides filling up the cell
# NTP alerts (time compliance code is commented out)

# Don't run these on the other cell (if multicell)
my %MULTICELL_EXCLUDE_HASH = (
    "Wipe Honeycomb"             => 1,
    "Change IP Addresses"        => 1,
    "Change SMTP Server"         => 1,
    "Update Schema"              => 1,
    "Clear Schema"               => 1,
);
###############################################################################
###############################################################################
###############################################################################

# MAIN
open( LOG, ">$LOGFILE" )
  or die "Cannot Open $LOGFILE, error $!";

# Get Inputs and Options
my %opts = ();
getopts( "hqlr:o:a:c:m:s:", \%opts );

# Error Check Inputs
list_tests() if ( defined $opts{'l'} );
usage()      if ( defined $opts{'h'} );
usage()
  if ( !( defined $opts{'a'} && defined $opts{'c'} && defined $opts{'m'} ) );
$ADMIN_VIP  = $opts{'a'};
$CHEAT_VIP  = $opts{'c'};
$MAIL_BOX   = $opts{'m'};
$START_FROM = ( defined $opts{'s'} ) ? $opts{'s'} : -1 ;
$NO_UPGRADE = ( defined $opts{'q'} ) ? 1 : 0;
$ONLY_TEST  = ( defined $opts{'o'} ) ? $opts{'o'} : -1;
$REPEAT     = ( defined $opts{'r'} ) ? $opts{'r'} : 0;
( $ORIGINAL_ADMIN, $ORIGINAL_CHEAT ) = ( $ADMIN_VIP, $CHEAT_VIP );

if ($START_FROM != -1 && $ONLY_TEST != -1) {
    usage();
    exit 1;
}
# Setup Variables
$SSH_ADMIN      = $SSH . $SSH_OPTIONS . " admin\@$ADMIN_VIP";
$SSH_CHEAT      = $SSH . $SSH_OPTIONS . " root\@$CHEAT_VIP";
$MASTER_NODE_1  = " root\@$ADMIN_VIP";
$SSH_MASTER     = $SSH . $SSH_OPTIONS . $MASTER_NODE_1;
$MAIL_CHECK     = "cat /var/spool/mail/$MAIL_BOX";
$MAIL_ERASE     = "rm -f /var/spool/mail/$MAIL_BOX";
$MAIL_TOUCH     = "touch /var/spool/mail/$MAIL_BOX";

# Initialize
open( LOG, ">$LOGFILE" )
  or die "ERR: Cannot Open $LOGFILE, error $!";

print_start_msg();

print_both("INFO: Logfile created at $LOGFILE");

save_log_position();
$PREV_LOG_LINE_NUM = $SAVED_LOG_LINE_NUM;
print_both("INFO: Initial Log Line Number: $PREV_LOG_LINE_NUM");

print_both("INFO: Pinging admin...");
if ( ping_test($ADMIN_VIP) ) {
    $CAN_PING_ADMIN = 1;
    print_both(" - $ADMIN_VIP is online");
}
print_both("INFO: Pinging cheat...");
if ( ping_test($CHEAT_VIP) ) {
    $CAN_PING_CHEAT = 1;
    print_both(" - $CHEAT_VIP is online");
}

print_both("INFO: Checking CLI status...");
if ( !cli_test() ) {
    if ( !can_heal_cluster() ) {
        print_both("UNABLE TO RUN TEST - CHECK CLUSTER");
        print_final_status();
        exit 1;    # BIG PROBLEM
    }
}
print_both(" - CLI is responsive");

print_both("INFO: Detecting hive status...");
if ( detect_hive($ADMIN_VIP) ) {
    $IS_MULTI_CLUSTER = 1;
    my $cellcfg = run_system_command("$SSH_ADMIN cellcfg -c $CELL_IDS[1]");
    my $not_ok = 0;
    if ( $cellcfg =~ /Admin IP Address = (.*)\n/ ) {
        $ORIGINAL_ADMIN_2= $1;
        $MASTER_NODE_2 = " root\@$ORIGINAL_ADMIN_2";
        print_both(" - Storing $1 as the Admin IP address: Cell $CELL_IDS[1]");
    } else { $not_ok = 1 }

    if ( $cellcfg =~ /Service Node IP Address = (.*)\n/ ) {
        $ORIGINAL_CHEAT_2 = $1;
        print_both(" - Storing $1 as the Cheat IP address: Cell $CELL_IDS[1]");
    } else { $not_ok = 1}
    if ($not_ok) {
        print_both("UNABLE TO DETECT SECOND CELL IP ADDRESSES");
        exit 1;
    }
}

print_both("INFO: Getting System Information...");
get_cluster_status();

print_both("INFO: Clearing old messages...");
erase_email();

if ( $ONLY_TEST != -1 ) {
    @ALERT_TRIGGER_TABLE = ( $ALERT_TRIGGER_TABLE[$ONLY_TEST] );
}
if ( $START_FROM != -1) {
    @ALERT_TRIGGER_TABLE = 
        splice(@ALERT_TRIGGER_TABLE, $START_FROM);
}
if (!(exists $APC_HASH{$ADMIN_VIP})) {
    for ( my $i = 0; $i <= $#ALERT_TRIGGER_TABLE; $i++) {
        if (($ALERT_TRIGGER_TABLE[$i][0] eq "Switch 1 Failover") ||
            ($ALERT_TRIGGER_TABLE[$i][0] eq "Switch 2 Failover"))
        {
            @ALERT_TRIGGER_TABLE =
                @ALERT_TRIGGER_TABLE[0..($i-1),($i+1)..$#ALERT_TRIGGER_TABLE];
            $i--;
        }
    }
} else {
    print_both("INFO: Setting up the APC Script...");
    my $a = run_system_command("wget -O /JAVAabt.tar.gz "
      . "http://stohome.central.sun.com/systems_test/auto/abt/JAVAabt.tar.gz"
      . " $DEV_NULL");
    print_log($a);
    $a = run_system_command("gzip -d -f /JAVAabt.tar.gz");
    print_log($a);
    $a = run_system_command("cd /; tar -xvf /JAVAabt.tar");
    print_log($a);
    $a = run_system_command("chmod +x /etc/init.d/abt /opt/abt/bin/abt");
    print_log($a);
    $a = run_system_command("/etc/init.d/abt start");
    print_log($a);
    $a = run_system_command("wget -O /JAVAjist.tar.gz "
      . "http://stohome.central.sun.com/systems_test/auto/jist/JAVAjist.tar.gz"
      . " $DEV_NULL");
    print_log($a);
    $a = run_system_command("gzip -d -f /JAVAjist.tar.gz");
    print_log($a);
    $a = run_system_command("cd /; tar xvf /JAVAjist.tar");
    print_log($a);
}

print_both("INFO: Making sure noreboot and nohoneycomb don't exist...");
my @nodes = ( 0 .. ( $NODES - 1 ) );
remove_nohoneycomb_noreboot(@nodes);
print_both("\n");

# Main Loop
#@CELL_IDS = ($CELL_IDS[1]); # debug! Only runs commands on second cell
foreach (@CELL_IDS) {    # debug: NOT TESTED ON MULTICELL YET
    $CURRENT_CELL = $_;
    if ($CURRENT_CELL == $MASTER_CELL) {
        $CHEAT_VIP = $ORIGINAL_CHEAT;
        $SSH_CHEAT = $SSH . $SSH_OPTIONS . " root\@$CHEAT_VIP";
        $SSH_MASTER = $SSH . $SSH_OPTIONS . $MASTER_NODE_1;
    } else {
        $CHEAT_VIP = $ORIGINAL_CHEAT_2;
        $SSH_CHEAT = $SSH . $SSH_OPTIONS . " root\@$CHEAT_VIP";
        $SSH_MASTER = $SSH . $SSH_OPTIONS . $MASTER_NODE_2;        
    }

    foreach (@ALERT_TRIGGER_TABLE) {
        my ( $title, $command, $pre_alert, $post_alert, $cleanup ) = @{$_};
        if ( !is_cluster_ok() ) {
            if ( !can_heal_cluster() ) {
                print_both("UNABLE TO RUN FURTHER TESTS - CHECK CLUSTER");
                print_final_status();
                exit 1;    # BIG PROBLEM
            }
        }

        unless ( ( $CURRENT_CELL ne $MASTER_CELL )
            && exists $MULTICELL_EXCLUDE_HASH{$title} )
        {
            
            my $status = 'not ok';
            my $date   = run_system_command("$SSH_CHEAT date");
            $/ = "\n"; chomp $date; chomp $date;

            print_both("RUNNING: $title");
            print_both("-" x (9 + length $title));
            print_both(" - CELL: $CURRENT_CELL");
            print_both(" - Service processor date: $date");

            print_both(" - Getting System Information...");
            get_cluster_status();
            
            # Save the log position for log scraping
            save_log_position();
            
            print_both(" - Trigger-alert subroutine...");
            # Run Command Subroutine
            if ( !&{$command}($title) ) {
                
                print_both(" - Pre-alert subroutine...");
                # Run Pre Alert Subroutine
                if ( !&{$pre_alert}($title) ) {

                    print_both(" - Retrieving Email Alerts");

                    # Check and Parse Email
                    check_email($title);

                    print_both(" - Post-alert subroutine...");

                    # Run Post Alert Subroutine
                    if ( !&{$post_alert}($title) ) { # Passed
                        $status = 'ok';
                    }
                    # Print Bogus Mail (if any)
                    my $bogus = 0;
                    print_both("");
                    while ( $#{ $ALERTS{$title} } >= 0 ) {
                        $bogus++;
                        my @array = pop @{ $ALERTS{$title} };
                        my ( $date, $subject, $message ) =
                          ( $array[0][0], $array[0][1], $array[0][2] );
                        print_bogus_mail( $date, $subject, $message );
                    }
                    if ($bogus) {
                        print_status( "$bogus BOGUS EMAIL(S) ",
                            "manual" );
                    }

                }    #pre_alert

            }    #command

            # Print Status of test (Pass or Fail)
            if (exists $EXPECTED_FAILURE_HASH{$title}) {
                my $reason = $EXPECTED_FAILURE_HASH{$title};
                print_both("\nTest: \"$title\" is expected to fail...");
                print_both(" - $reason");
                print_status( "$title - CELL $CURRENT_CELL ", "manual");
            } else {
                print_status( "$title - CELL $CURRENT_CELL ", "$status" );
            }
            # Let Honeycomb recover a bit before the next test
            sleep $SLEEP_RECOVER unless ($ONLY_TEST != -1); 

        }    # Exclude multicell

    }    #foreach command

    # Cleanup Loop
    print_both("CLEANING UP SCRIPT\n");

    foreach ( reverse @ALERT_TRIGGER_TABLE ) {
        my ( $title, $command, $pre_alert, $post_alert, $cleanup ) = @{$_};
        unless ( ( $CURRENT_CELL ne $MASTER_CELL )
            && exists $MULTICELL_EXCLUDE_HASH{$title} )
        {
            print_both("\n - Cleaning up $title");

            # Run the cleanup subroutine
            if ( &{$cleanup}($title) ) {
                print_both("   ERROR: PROBLEM WITH CLEANUP: $title");
                print_both("   ERROR: PLEASE CHECK LOG AND CLUSTER SETTINGS");
            }
            else {
                print_both("   Cleanup successful");
            }
        }
    }

    # Clear restore commands for next cell's commands
    %RESTORE_COMMANDS = ();
    
    if ($REPEAT != 0) {
        $REPEAT--;
        redo;
    }
}   # foreach cluster


# Print Results
print_final_status();

if ($IS_WARNING == 1) {
    exit 2; # WARNING
}
if ($TEST_FAILED == 0) {
    exit 0; # PASS
}
exit 1; # FAIL

###############################################################################
###############################################################################
###########################                           #########################
###########################        Subroutines        #########################
###########################                           #########################
###############################################################################
###############################################################################

###############################################################################
# Trigger Alert Subroutines

# Store the initial emails of alertcfg
sub alertcfg {
    my $title    = $_[0];
    my $alertcfg = run_system_command("$SSH_ADMIN alertcfg");
    if ( ($alertcfg =~ /To:\s(.+)\n/) && ($1 !~ /Cc:/) ) {
        push( @{ $RESTORE_COMMANDS{$title} }, "alertcfg add to $1" );
        run_system_command("$SSH_ADMIN alertcfg del to $1");
        print_both(" - Stored $1 as the \"to\" email address");
        $ORIGINAL_ALERTCFG = $1;
    }
    if ( $alertcfg =~ /Cc:\s(.+)\n/ ) {
        push( @{ $RESTORE_COMMANDS{$title} }, "alertcfg add cc $1" );
        run_system_command("$SSH_ADMIN alertcfg del cc $1");
        print_both(" - Stored $1 as the \"cc\" email address");
    }

    print_both(" - Running: alertcfg add to $MAIL_BOX\@$MAIL_SERVER");

    # Run Command
    $alertcfg =
      run_system_command("$SSH_ADMIN alertcfg add to $MAIL_BOX\@$MAIL_SERVER");
    if ( $? || ( $alertcfg =~ /The CLI session is read-only./ ) ) {
        if ( $alertcfg =~ /The CLI session is read-only./ ) {
            print_both(" - The CLI session is read-only");
        }
        print_both("UNABLE TO EXECUTE COMMAND");
        return 1;    #Problem
    }
    return 0;
}

sub reboot_hc {
    print_both(" - Running: reboot -c $CURRENT_CELL -F");
    add_no_fsck_flags();
    my $reboot = run_system_command("$SSH_ADMIN reboot -c $CURRENT_CELL -F",
                                    $REBOOT_TIMEOUT,
                                    $MAX_REBOOT_ITERATIONS
                                    );
    if ( $? || ( $reboot =~ /The CLI session is read-only./ ) ) {
        if ( $reboot =~ /The CLI session is read-only./ ) {
            print_both(" - The CLI session is read-only");
        }
        print_both("UNABLE TO EXECUTE COMMAND");
        return 1;    #Problem
    }
    return 0;
}

sub reboot_all {
    print_both(" - Running: reboot -c $CURRENT_CELL --all -F");
    add_no_fsck_flags();
    my $reboot =
      run_system_command("$SSH_ADMIN reboot -c $CURRENT_CELL --all -F",
                         $REBOOT_TIMEOUT,
                         $MAX_REBOOT_ITERATIONS
                         );
    if ( $? || ( $reboot =~ /The CLI session is read-only./ ) ) {
        if ( $reboot =~ /The CLI session is read-only./ ) {
            print_both(" - The CLI session is read-only");
        }
        print_both("UNABLE TO EXECUTE COMMAND");
        return 1;    #Problem
    }
    return 0;
}

sub node_down_up {
    my ($node_num, $port, $node);

    do {
        $node_num = ( int rand($NODES) );
        $port     = $NODE_PORT_OFFSET + $node_num;
        $node     = $NODE_NUM_OFFSET + $node_num;
    } while ($node == $MASTER_NODE);
    $NODE_STRING = $node;
    
    if ($CURRENT_CELL == $MASTER_CELL) {
        print_both(" - Randomly chose NODE-$node to disable");
        add_no_fsck_flags();
        print_both(" - Running: $SSH -p $port -l root $ADMIN_VIP reboot");
        my $reboot = run_system_command(
            "$SSH $SSH_OPTIONS -p $port -l root $ADMIN_VIP reboot");
        if ($?) {
            return 0;  # Expected to get here, node kicks us out when rebooted
        }
        print_both(" - UNABLE TO EXECUTE COMMAND");
        return 1;      # Problem
    } else {
        print_both(" - Randomly chose NODE-$node to disable");
        add_no_fsck_flags();
        print_both(" - Running: $SSH -p $port -l root $ORIGINAL_ADMIN_2 reboot");
        my $reboot = run_system_command(
            "$SSH $SSH_OPTIONS -p $port -l root $ORIGINAL_ADMIN_2 reboot");
        if ($?) {
            return 0;  # Expected to get here, node kicks us out when rebooted
        }
        print_both(" - UNABLE TO EXECUTE COMMAND");
        return 1;      # Problem  
    }
}

sub master_down_up {
    $NODE_STRING = $MASTER_NODE;
    print_both(" - Running: $SSH_MASTER reboot");
    add_no_fsck_flags();
    my $reboot = run_system_command("$SSH_MASTER reboot");
    if ($?) {
        return 0;  # Expected to get here, node kicks us out when rebooted
    }
    print_both(" - UNABLE TO EXECUTE COMMAND");
    return 1;      # Problem
}

sub wipe_hc {
    print_both(" - Running: wipe -F");
    my $wipe = run_system_command("$SSH_ADMIN wipe -F",
                                  $WIPE_TIMEOUT,
                                  $MAX_WIPE_ITERATIONS
                                  );
    if ( $? || ( $wipe =~ /The CLI session is read-only./ ) ) {
        if ( $wipe =~ /The CLI session is read-only./ ) {
            print_both(" - The CLI session is read-only");
        }
        print_both(" - UNABLE TO EXECUTE COMMAND");
        return 1;    #Problem
    }
    return 0;
}

sub disable_disk {
    my $title = $_[0];
    my $node  = ( int rand($NODES) ) + $NODE_NUM_OFFSET;
    my $disk  = ( int rand($MAX_NUMBER_DISKS) );
    print_both(" - Randomly chose DISK-$node:$disk to disable");
    $DISK_STRING = "DISK-$node:$disk";

    print_both(" - Running: hwcfg -c $CURRENT_CELL -F -D DISK-$node:$disk");
    my $hwcfg_d = run_system_command(
        "$SSH_ADMIN hwcfg -c $CURRENT_CELL -F -D DISK-$node:$disk");
    if ( !$? && ( $hwcfg_d !~ /The CLI session is read-only./ ) ) {
        print_both(" - Waiting for disk to go offline...");
        my $sysstat_v =
          run_system_command("$SSH_ADMIN sysstat -v -c $CURRENT_CELL");
        until ( $sysstat_v =~ /OFFLINE/i ) {
            print ".";
            sleep $SLEEP_DOT;
            $sysstat_v =
              run_system_command("$SSH_ADMIN sysstat -v -c $CURRENT_CELL");
        }
        print("\n");
        
        sleep $SLEEP_DISK_DOWN;
        print_both(" - Running: hwcfg -c $CURRENT_CELL -F -E DISK-$node:$disk");
        my $i = $MAX_NODE_ENABLE_ITERATIONS;
        my $hwcfg_e;
        do {
            $hwcfg_e = run_system_command(
                "$SSH_ADMIN hwcfg -c $CURRENT_CELL -F -E DISK-$node:$disk");
        } while ( ($i > 0) && ( $hwcfg_e =~ /The CLI session is read-only./ ));
        
        if ($i) { # If there are iterations left
            return 0; # OK
        }
        if ( $hwcfg_e =~ /The CLI session is read-only./ ) {
            print_both(" - The CLI session is read-only");
        }
    }
    if ( $hwcfg_d =~ /The CLI session is read-only./ ) {
        print_both(" - The CLI session is read-only");
    }
    print_both(" - UNABLE TO EXECUTE COMMAND");
    return 1;    # Problem
}

sub reboot_sw1 {
    print_both(" - Detecting current switch...");
    my $cat =
      run_system_command("$SSH_MASTER $SSH_SWITCH cat $SWITCH_CONF_FILE");
    if ( !$? ) {
        if ( $cat =~ /SWITCH_ID=(\d{1})/ ) {
            $SWITCH = $1;
            print_both(" - Using switch: $1");
            print_both(" - Running: $SSH_SWITCH reboot");
            my $reboot = run_system_command("$SSH_MASTER $SSH_SWITCH reboot");
            if ($?) {
                return 0; # Expect to get here, node kicks us out when rebooted
            }
            else {
                print_both("UNABLE TO EXECUTE COMMAND");
            }
        }
        else {
            print_both("UNABLE TO DETERMINE CURRENT SWITCH ID");
        }
    }
    else {
        print_both("UNABLE TO EXECUTE COMMAND");
    }
    return 1;    # Problem
}

sub sw_failover {
    my $switch = $_[0];
    print_both(" - Detecting current switch...");
    my $cat =
      run_system_command("$SSH_MASTER $SSH_SWITCH cat $SWITCH_CONF_FILE");
    if ( !$? ) {
        if ( $cat =~ /SWITCH_ID=(\d{1})/ ) {
            $SWITCH = $1;
            print_both(" - Using switch: $1");
        }
    }   
    print_both(" - Powering off switch $switch");
    my $apc_ip = $APC_HASH{$ADMIN_VIP};
    $cat = run_system_command(
        "/opt/abt/bin/abt PowerOffPduPort "
      . "on=apc:apc\@$apc_ip:$switch logDir=/mnt/test/ $DEV_NULL"
    );
    print_log($cat);
    if ($?) {
        print_both("UNABLE TO EXECUTE COMMAND");
        return 1; # Problem
    }
    print_both(" - Sleeping for $SLEEP_SWITCH_FAILOVER seconds...");
    sleep($SLEEP_SWITCH_FAILOVER);
    print_both(" - Powering on switch $switch");
    $cat = run_system_command(
        "/opt/abt/bin/abt PowerOnPduPort "
      . "on=apc:apc\@$apc_ip:$switch logDir=/mnt/test/ $DEV_NULL"
    );
    print_log($cat);
    if (!$?) {
        return 0; # OK
    }
    print_both("UNABLE TO EXECUTE COMMAND");
    return 1;    # Problem
}

sub sw1_failover {
    sw_failover(1);
}
sub sw2_failover {
    sw_failover(2);
}
sub reboot_sw2 {
    print_both(" - Detecting current switch...");
    my $cat =
      run_system_command($SSH_MASTER . " $SSH_SWITCH cat $SWITCH_CONF_FILE");
    if ( !$? ) {
        if ( $cat =~ /SWITCH_ID=(\d{1})/ ) {
            $SWITCH = $1;
            print_both(" - Using switch: $1");
            print_both(
                " - Running: $SSH_SWITCH /usr/bin/run_cmd_other.sh reboot");
            my $reboot = run_system_command(
                "$SSH_MASTER $SSH_SWITCH /usr/bin/run_cmd_other.sh reboot");
            if ( !$? ) {
                return 0;    # OK
            }
            else {
                print_both("UNABLE TO EXECUTE COMMAND");
            }
        }
        else {
            print_both("UNABLE TO DETERMINE CURRENT SWITCH ID");
        }
    }
    else {
        print_both("UNABLE TO EXECUTE COMMAND");
    }
    return 1;    # Problem
}

sub change_ips {
    my $title = $_[0];
    my ( $cheat, $admin, $data ) = ( "", "", "" );
    
    my $cellcfg = run_system_command("$SSH_ADMIN cellcfg -c $CURRENT_CELL");
    my $ok = 1;
    if ( $cellcfg =~ /Data IP Address = (.*)\n/ ) {
        $data = $1;
        print_both(" - Storing $data as the Data IP address");
    }
    else { $ok = 0 }

    if ( $cellcfg =~ /Admin IP Address = (.*)\n/ ) {
        $admin = $1;
        print_both(" - Storing $admin as the Admin IP address");
    }
    else { $ok = 0 }

    if ( $cellcfg =~ /Service Node IP Address = (.*)\n/ ) {
        $cheat = $1;
        print_both(" - Storing $cheat as the Cheat IP address");
    }
    else { $ok = 0 }

    push(
        @{ $RESTORE_COMMANDS{$title} },
        " cellcfg -c $CURRENT_CELL -F -d $data -a $admin -n $cheat"
    );

    if ($ok) {
        add_no_fsck_flags();
        print_both(
            " - Running: cellcfg -F -d "
          . "$HONEYSTOR_DATA -a $HONEYSTOR_ADMIN -n $HONEYSTOR_CHEAT"
        );
        $cellcfg = run_system_command(
                    "$SSH_ADMIN cellcfg -c $CURRENT_CELL -F -d "
                  . "$HONEYSTOR_DATA -a $HONEYSTOR_ADMIN -n $HONEYSTOR_CHEAT",
                  $CHANGE_IPS_TIMEOUT,
                  $MAX_CHANGE_IPS_ITERATIONS
                   );
        if ( !$? && ( $cellcfg !~ /The CLI session is read-only./ ) ) {
            $SSH_ADMIN = $SSH_HONEYSTOR_ADMIN;
            $SSH_CHEAT = $SSH_HONEYSTOR_CHEAT;
            $ADMIN_VIP = $HONEYSTOR_ADMIN;
            $CHEAT_VIP = $HONEYSTOR_CHEAT;
            return 0;    # OK
        }
        else {
            print_both("UNABLE TO EXECUTE COMMAND");
        }
    }
    else {
        print_both("UNABLE TO RETRIEVE IP ADDRESSES");
    }
    return 1;            # Problem
}

sub change_smtp {
    my $title = $_[0];

    my $smtp = run_system_command("$SSH_ADMIN hivecfg");
    if ( !$? ) {
        if ( $smtp =~ /SMTP Server = (.*)\n/ ) {
            $ORIGINAL_SMTP_SERVER = $1;
            print_both(" - Running: hivecfg -s $ALTERNATE_SMTP_SERVER");
            $smtp = run_system_command(
                "$SSH_ADMIN hivecfg -s $ALTERNATE_SMTP_SERVER",
                $SMTP_TIMEOUT,
                $MAX_SMTP_ITERATIONS
                );
            push(
                @{ $RESTORE_COMMANDS{$title} },
                "hivecfg -s $ORIGINAL_SMTP_SERVER"
            );
            if ( !$? && ( $smtp !~ /The CLI session is read-only./ ) ) {
                add_no_fsck_flags();
                print_both(" - Rebooting");
                $smtp = run_system_command(
                    "$SSH_ADMIN reboot -c $CURRENT_CELL --all -F",
                    $REBOOT_TIMEOUT,
                    $MAX_REBOOT_ITERATIONS
                );
                push( @{ $RESTORE_COMMANDS{$title} },
                      "reboot -c $CURRENT_CELL --all -F" );
                return 0;    #OK
            }
            else {
                if ( $smtp =~ /The CLI session is read-only./ ) {
                    print_both(" - The CLI session is read-only");
                }
                print_both("UNABLE TO EXECUTE COMMAND");
            }
        }
        else {
            print_both("UNABLE TO RETRIEVE ORIGINAL SMTP ADDRESS");
        }
    }
    return 1;
}

sub reboot_cheat {    
    my $password = is_password_file_ok($SSH_MASTER);
    if ( !$password ) {
        return 1;    # Problem
    }

    print_both( " - Running: $IPMI_SP_POWER_OFF" );
    my $reboot = run_system_command("$SSH_MASTER $IPMI_SP_POWER_OFF");
    if ( !$? ) {
        sleep $SLEEP_CHEAT_DOWN;
        print_both( " - Running: $IPMI_SP_POWER_ON" );
        my $reboot = run_system_command("$SSH_MASTER $IPMI_SP_POWER_ON");
        if ( !$? ) {
            return 0;    # OK
        }
    }
    print_both("UNABLE TO EXECUTE COMMAND");
    return 1;        # Problem
}

sub power_node {
    my $title = $_[0];
    my $node;
    do {
        $node     = $NODE_NUM_OFFSET + ( int rand($NODES) );
    } while ($node == $MASTER_NODE);
    $NODE_STRING = $node;
    
    print_both(" - Randomly chose Node $node to disable");
    $NODE_STRING = $node;
    # Checking passwrod file
    my $password = is_password_file_ok($SSH_CHEAT);
    if ( !$password ) {
        return 1;    # Problem
    }

    add_no_fsck_flags();
    print_both(
        " - Running: /usr/sfw/bin/ipmitool -I lan -f "
      . "$IPMI_PASS_LOCATION -H hcb$node-sp -U Admin chassis power off"
    );
    my $return = run_system_command(
                "$SSH_CHEAT /usr/sfw/bin/ipmitool -I lan  -f "
              . "$IPMI_PASS_LOCATION -H hcb$node-sp -U Admin chassis power off"
                 );
    if ( !$? ) {
        print_both(" - Sleeping for $SLEEP_NODE_DOWN seconds...");
        sleep $SLEEP_NODE_DOWN;
        print_both(
            " - Running: /usr/sfw/bin/ipmitool -I lan -f "
          . "$IPMI_PASS_LOCATION -H hcb$node-sp -U Admin chassis power on"
        );
        $return = run_system_command(
                "$SSH_CHEAT /usr/sfw/bin/ipmitool -I lan  -f "
              . "$IPMI_PASS_LOCATION -H hcb$node-sp -U Admin chassis power on"
                  );
        if ( !$? ) {
            return 0;    # OK
        }
    }
    print_both("UNABLE TO EXECUTE COMMAND");
    return 1;            # Problem
}

sub shutdown_cluster {

    # Make sure password file is there
    my $password = is_password_file_ok($SSH_CHEAT);
    if ( !$password ) {
        return 1;        # Problem
    }

    # Shutdown
    add_no_fsck_flags();
    print_both(" - Running: shutdown -F");
    my $return = run_system_command(
        "$SSH_ADMIN shutdown -c $CURRENT_CELL -F",
        $SHUTDOWN_TIMEOUT,
        $MAX_SHUTDOWN_ITERATIONS
    );
    if ($?) {
        print_both("UNABLE TO EXECUTE COMMAND");
        return 1;        # Problem
    }

    sleep $SLEEP_SHUTDOWN;

    # Make sure password file is there
    $password = is_password_file_ok($SSH_CHEAT);
    if ( !$password ) {
        return 1;    # Problem
    }

    # Bring back the cluster
    my $attempts = 0;
    my $status   = 0;
    foreach ( 0 .. ( $NODES - 1 ) ) {
        my $node = $_ + $NODE_NUM_OFFSET;
        print_both(" - Powering on hcb$node");
        $return = run_system_command(
                    "$SSH_CHEAT ipmitool -I lan -f $IPMI_PASS_LOCATION "
                  . "-U Admin -H hcb$node-sp chassis power on"
                  );
        if ($?) {
            $attempts++;
            if ( $attempts > 5 ) {
                print_both("UNABLE TO BRING NODE $node BACK ONLINE");
                $attempts = 0;
                $status   = 1;
                next;    # Problem
            }
            redo;
        }
        else {
            $attempts = 0;
        }
    }
    return $status;
}

sub change_capacity {
    my $cmd;
    if ($CURRENT_CELL == $MASTER_CELL) {
        $cmd = "$MASTER_NODE_1";
    } else {
       $cmd = "$MASTER_NODE_2"; 
    }
    my $title = $_[0];
    my @nodes = reverse( 0 .. ( $NODES - 1 ) );
    my $return;
    
    print_both(" - Getting cluster_config.properties...");
    my $scp = run_system_command(
        "scp $cmd:/opt/honeycomb/share/cluster_config.properties"
      . " /tmp/alert_tests_scp.tmp"
              );
    if ($?) {
        print_both("UNABLE TO CHANGE CAPACITY THRESHOLD");
        return 1;    # PROBLEM
    }
    print_both(" - Altering local copy of cluster_config.properties...");
    run_system_command(
        "sed s/'honeycomb.disks.usage.cap = 80'/'honeycomb.disks.usage.cap "
      . "= 1'/g < /tmp/alert_tests_scp.tmp > /tmp/cluster_config.properties"
    );
    if ($?) {
        print_both("UNABLE TO CHANGE CAPACITY THRESHOLD");
        return 1;    # PROBLEM
    }

    # Create nohoneycomb and noreboot files
    create_nohoneycomb_noreboot(@nodes);
    add_no_fsck_flags();
    
    # Check to see if we can reboot
    my $password = is_password_file_ok($SSH_CHEAT);
    if ( !$password ) {
        print_both("UNABLE TO VERIFY CORRECT IPMI PASSWORD FILE");
        return 1;    # Problem
    }

    # Do a cli reboot
    print_both(" - Rebooting all nodes...");
    foreach (@nodes) {
        my $node = $_ + $NODE_NUM_OFFSET;
        $return = run_system_command(
                    "$SSH_CHEAT ipmitool -I lan -f $IPMI_PASS_LOCATION -U "
                  . "Admin -H hcb$node-sp chassis power cycle"
                  );
    }

    sleep $SLEEP_CAPACITY_SHUTDOWN;

    # Remove nohoneycomb and noreboot files
    remove_nohoneycomb_noreboot(@nodes);
    add_no_fsck_flags();

    # Change Capacity Value for each node when they come back online
    print_both(" - Changing capacity value on all nodes...");
    foreach (@nodes) {
        my $port = $NODE_PORT_OFFSET + $_;
        $scp = run_system_command(
                 "scp -P $port /tmp/cluster_config.properties "
               . "$cmd:/opt/honeycomb/share"
               );
    }

    # Start Honeycomb manually
    print_both(" - Starting Honeycomb on all nodes...");
    foreach (@nodes) {
        my $port = $NODE_PORT_OFFSET + $_;
        $return = run_system_command(
                    "$SSH $SSH_OPTIONS -p $port $cmd "
                  . "/opt/honeycomb/etc/init.d/honeycomb start"
                  );
    }
    return 0;    # OK
}

sub update_schema {
    my $title = $_[0];

    # Make sure we have a HAFaultTolerant Query Engine
    my $sysstat = run_system_command("$SSH_ADMIN sysstat -c $MASTER_CELL");
    if ($?) {
        print_both("UNABLE TO GET QUERY ENGINE STATUS");
        return 1;    # Problem
    }
    if ( $sysstat !~ /HAFaultTolerant/ ) {
        print_both(" - Waiting for HADB to be HAFaultTolerant...");
    }
    
    # Make sure we have a HAFaultTolerant Query Engine on the other Cell
    if ($IS_MULTI_CLUSTER) {
        my $cell = $CELL_IDS[1];
        $sysstat = run_system_command("$SSH_ADMIN sysstat -c $cell");
        if ($?) {
            print_both("UNABLE TO GET QUERY ENGINE STATUS");
            return 1;    # Problem
        }
        if ( $sysstat !~ /HAFaultTolerant/ ) {
            print_both(" - Waiting for HADB to be HAFaultTolerant...");
        }
    }
    
    my $i = $MAX_HADB_RECOVERY_ITERATIONS;
    until ( ( $i <= 0 ) || ( $sysstat =~ /HAFaultTolerant/ ) ) {
        print ".";
        sleep $SLEEP_DOT;
        $sysstat = run_system_command("$SSH_ADMIN sysstat -c $CURRENT_CELL");
        if ( $sysstat =~ /Query Engine Status: (\w+)/ ) {
            print_log("Query Engine Status: $1");
        }
        $i--;
    }
    print "\n";
    if ( !$i ) {
        $WARNING_FLAG = 1;
        $IS_WARNING = 1;
        print_both(" - WARNING: Query engine status is not HAFaultTolerant");
        print_both("UNABLE TO RUN TEST: $title");
        return 1;    # Problem
    }

    # Update the schema
    print_both(" - Running: mdconfig -a");
    my $update =
      run_system_command("echo \'$NEW_SCHEMA\' | $SSH_ADMIN 'mdconfig -a'",
                         $NEW_SCHEMA_TIMEOUT, #timeout (sec)
                         $MAX_NEW_SCHEMA_ITERATIONS #iterations
                         );
    if ( !$? ) {
        if ( $update !~ /failed/i ) {
            return 0;    # OK
        }
        print_both(" - Schema failed to propagate on all cells");
        print_both("UNABLE TO UPDATE SCHEMA");
        return 1;        # Problem
    }
    print_both("UNABLE TO EXECUTE COMMAND");
    return 1;            # Problem
}

sub clear_schema {
    print_both(" - Running: mdconfig -w -F");
    my $mdconfig = run_system_command("$SSH_ADMIN mdconfig -w -F");
    if ( !$? ) {
        if ( $mdconfig !~ /failed/i ) {
            return 0;    # OK
        }
        print_both("UNABLE TO CLEAR SCHEMA");
    }
    print_both("UNABLE TO EXECUTE COMMAND");
    return 1;            # PROBLEM
}

sub upgrade_cell {
    if ($NO_UPGRADE) {
        print_both(" - Quick option detected in command line, skipping test");
        return 0;        #OK
    }

    # Figure out newest version
    print_both(" - Determining latest stable version");
    my $minor_version = 
        run_system_command(
            "curl http://10.7.228.10/~hcbuild/repository/releases/1.1/"
          . " | awk {\'print \$3\'} | cut -d\'/\' -f1,1 | egrep -v "
          . "\'definition|junk\' | grep -v \' \' | cut -d\'-\' -f2,2 | sort -n"
          . " | tail -1"
        );
    $/ = "\n"; chomp $minor_version;
    my $current_version =
      run_system_command(
          "curl -q http://10.7.228.10/~hcbuild/repository/releases/1.1/"
        . " | awk {\'print \$3\'} | cut -d\'/\' -f1,1 | egrep -v "
        . "\'definition|junk\' | grep -v \' \' | grep $minor_version | tail -1"
      );
    $/ = "\n"; chomp $current_version;
    print_both(" - Will upgrade to version: $current_version");
    
    print_both(" - Checking that iso exists...");
    my $url =
        "http://10.7.228.10/~hcbuild/repository/releases/1.1/"
      . "$current_version/AUTOBUILT/pkgdir/st5800_$current_version.iso";
    print_both(" - URL: $url");
    my $wget = run_system_command("$WGET -O - -q $url $DEV_NULL",
                                  $WGET_TIMEOUT,
                                  1 #iteration
                                  );
    if ($?) {
        $minor_version = 
            run_system_command(
                "curl http://10.7.228.10/~hcbuild/repository/releases/1.1/"
              . " | awk {\'print \$3\'} | cut -d\'/\' -f1,1 | egrep -v "
              . "\'definition|junk\' | grep -v \' \' | cut -d\'-\' -f2,2 | "
              . "sort -n | tail -1"
            );
        $minor_version--;
        $current_version =
          run_system_command(
              "curl -q http://10.7.228.10/~hcbuild/repository/releases/1.1/"
            . " | awk {\'print \$3\'} | cut -d\'/\' -f1,1 | egrep -v "
            . "\'definition|junk\' | grep -v \' \' | grep $minor_version |"
            . " tail -1"
          );
        print_both(" - URL does not exist, will try $current_version");
        print_both(" - Will upgrade to version: $current_version");
        print_both(" - Checking that iso exists (may take about 1/2 hour)...");
        $url =
            "http://10.7.228.10/~hcbuild/repository/releases/1.1/"
          . "$current_version/AUTOBUILT/pkgdir/st5800_$current_version.iso";
        print_both(" - URL: $url");
        my $wget = run_system_command("$WGET -O - -q $url $DEV_NULL",
                                      $WGET_TIMEOUT,
                                      1 #iteration
                                      );
        if ($?) {
            print_both(" - URL does not exist");
            print_both("UNABLE TO UPGRADE CELL");
            return 1; # Problem
        }
    }
    
                     
    print_both(" - Will upgrade to version: $current_version");
    # Run upgrade command
    add_no_fsck_flags();
    print_both(" - Running upgrade -F -c $CURRENT_CELL -b $url");
    my $upgrade =
      run_system_command("$SSH_ADMIN upgrade -F -c $CURRENT_CELL -b $url",
                         $UPGRADE_TIMEOUT,
                         $MAX_UPGRADE_ITERATIONS
                         );
    if ($?) {
        print_both("UNABLE TO UPGRADE CELL");
        print_both("UNABLE TO EXECUTE COMMAND");
        return 1;    # Problem
    }
    return 0;        # OK
}

###############################################################################
# Pre Alert Subroutines - Called after the command to determine
#  if it is ok to scrape the inbox. Should be read-only commands

sub pre_alert_reboot_hc {
    my $all = $_[0];

    # Waiting for HC to go down
    is_all_down();

    # Waiting for hc to come back
    is_all_up();

    sleep $SLEEP_REBOOT;
    if ( defined $all ) {
        sleep $SLEEP_REBOOT_ALL;
    }

    # Waiting for everything to come back online
    print_both(" - Waiting for the all disks/nodes to come online...");
    my $online = is_all_online();
    sleep $SLEEP_EMAIL;
    return !($online);
}

sub pre_alert_reboot_all {
    return pre_alert_reboot_hc(1);
}

sub pre_alert_node_down_up {
    sleep $SLEEP_NODE_DOWN;
    print_both(" - Waiting for the all disks/nodes to come online...");
    my $online = is_all_online();
    sleep $SLEEP_EMAIL;
    return !($online);
}

sub pre_alert_master_down_up {
    return pre_alert_node_down_up();
}

sub pre_alert_wipe_hc {
    print_both(" - Waiting for the all disks/nodes to come online...");
    my $online = is_all_online();
    print_both(" - Sleeping for $SLEEP_WIPE seconds");
    sleep $SLEEP_WIPE;
    sleep $SLEEP_EMAIL;
    return !($online);
}

sub pre_alert_disable_disk {
    sleep $SLEEP_DISK_DOWN;
    print_both(" - Waiting for the all disks/nodes to come online...");
    my $online = is_all_online();
    sleep $SLEEP_EMAIL;
    return !($online);
}

sub pre_alert_sw1_failover {
    my ( $cat, $original_switch ) = ( "", 0 );
    print_both(" - Sleeping for $SLEEP_SWITCH_FAILBACK seconds");
    sleep($SLEEP_SWITCH_FAILBACK);
    print_both(" - Waiting for original switch to come online...");
    my $i = $MAX_SWITCH_DOWN_ITERATIONS;
    do {
        $i--;
        $cat =
          run_system_command("$SSH_MASTER $SSH_SWITCH cat $SWITCH_CONF_FILE");
        if ( $cat =~ /SWITCH_ID=(\d{1})/ ) {
            if ( $SWITCH eq $1 ) {
                print "\n";
                print_both(" - Using switch: $1");
                $original_switch = 1;
            }
        }
      } while ( ( $i > 0 )
        && ( ( $cat =~ /Connection refused/i ) || !$original_switch ) );

    if ( !$i ) {
        print_both(" - WARNING: unable to detect switch $SWITCH coming up...");
    }

    print_both(" - Waiting for alerts to generate and send...");
    sleep $SLEEP_EMAIL;
    return 0;
}

sub pre_alert_sw2_failover {
    print_both(" - Sleeping for $SLEEP_SWITCH_FAILBACK seconds");
    sleep($SLEEP_SWITCH_FAILBACK);
    print_both(" - Waiting for alerts to generate and send...");
    sleep $SLEEP_EMAIL;
    return 0;
}

sub pre_alert_reboot_sw1 {
    my ( $cat, $original_switch ) = ( "", 1 );
    print_both(" - Waiting for other switch to come online...");
    my $i = $MAX_SWITCH_DOWN_ITERATIONS;
    do {
        $i--;
        $cat =
          run_system_command("$SSH_MASTER $SSH_SWITCH cat $SWITCH_CONF_FILE");
        if ( $cat =~ /SWITCH_ID=(\d{1})/ ) {
            if ( $SWITCH ne $1 ) {
                print "\n";
                print_both(" - Using switch: $1");
                $original_switch = 0;
            }
        }
      } while ( ( $i > 0 )
        && ( ( $cat =~ /Connection refused/i ) || $original_switch ) );

    if ( !$i ) {
       print_both(" - WARNING: unable to detect switch $SWITCH going down...");
    }

    print_both(" - Waiting for original switch to come online...");
    $i = $MAX_SWITCH_DOWN_ITERATIONS;
    do {
        $i--;
        $cat =
          run_system_command("$SSH_MASTER $SSH_SWITCH cat $SWITCH_CONF_FILE");
        if ( $cat =~ /SWITCH_ID=(\d{1})/ ) {
            if ( $SWITCH eq $1 ) {
                print "\n";
                print_both(" - Using switch: $1");
                $original_switch = 1;
            }
        }
      } while ( ( $i > 0 )
        && ( ( $cat =~ /Connection refused/i ) || !$original_switch ) );

    if ( !$i ) {
        print_both(" - WARNING: unable to detect switch $SWITCH coming up...");
    }

    print_both(" - Waiting for alerts to generate and send...");
    sleep $SLEEP_EMAIL;
    return 0;
}

sub pre_alert_reboot_sw2 {
    # Wait for switch to reboot
    print_both(" - Waiting for alternate switch to reboot...");
    sleep $SLEEP_ALT_SWITCH_REBOOT;
    return 0;
}

sub pre_alert_change_ips {
    sleep $SLEEP_REBOOT_ALL;
    # Wait for cell to reboot
    return pre_alert_reboot_hc();
}

sub pre_alert_change_smtp {
    sleep $SLEEP_REBOOT_ALL;
    # Wait for cell to reboot
    return pre_alert_reboot_hc(1);
}

sub pre_alert_reboot_cheat {

    # Wait for it to come up
    my $i = $MAX_CHEAT_UP_ITERATIONS;
    print_both(" - Waiting for service processor to come up...");
    do {
        $i--;
        run_system_command(
            "$PING -c $PING_ATTEMPTS -t $PING_TIMEOUT $CHEAT_VIP",
            $PING_TIMEOUT,
            $PING_ATTEMPTS
        );
        print ".";
        sleep $SLEEP_DOT;
    } while ( $? && ( $i > 0 ) );
    print "\n";

    # Wait for it to become responsive
    print_both(" - Waiting for service processor to become responsive...");
    my $response;
    $i = $MAX_CHEAT_RESPONSIVE_ITERATIONS;
    do {
        $i--;
        $response = run_system_command("$SSH_CHEAT date");
        print ".";
        sleep $SLEEP_DOT;
    } while ( $? && ( $i > 0 ) );
    print "\n";
    
    sleep $SLEEP_EMAIL;
    ( $i == 0 ) ? return 1 : return 0;
                # Problem  # OK
}

sub pre_alert_power_node {
    return pre_alert_node_down_up();
}

sub pre_alert_shutdown_cluster {

    # Waiting for hc to come back
    is_all_up();

    # Waiting for disks to come up.
    print_both(" - Waiting for the all disks/nodes to come online...");
    my $online = is_all_online();
    sleep $SLEEP_EMAIL;    
    return !($online);
}

sub pre_alert_change_capacity {
    sleep $SLEEP_REBOOT;
    # Waiting for hc to come back
    is_all_up();

    print_both(" - Waiting for the all disks/nodes to come online...");
    my $online = is_all_online();
    sleep $SLEEP_EMAIL;
    return !($online);
}

sub pre_alert_update_schema {
    sleep $SLEEP_EMAIL;
    return 0;
}

sub pre_alert_upgrade_cell {
    if ($NO_UPGRADE) {
        print_both(" - Quick option detected in command line, skipping test");
        return 0;    #OK
    }
    return pre_alert_reboot_hc();
}

###############################################################################
# Post Alert Subroutines - Called after the command to determine
#  whether the test passes or fails

sub post_alertcfg {
    my $alertcfg = run_system_command("$SSH_ADMIN alertcfg");
    if ( $alertcfg =~ /To:\s+$MAIL_BOX\@$MAIL_SERVER/i ) {
        return 0;
    }
    print_both("UNABLE TO VERIFY CHANGE IN ALERTCFG");
    return 1;
}

sub post_alert_reboot_hc {
    my $title                             = $_[0];
    my $no_about_to_reboot_email_expected = $_[1];
    $no_about_to_reboot_email_expected ||= 0;
    my $status = 1;
    my ( $count, $count_ok, $about_to_reboot, $rebooting ) = ( 0, 0, 0, 0 );
    my $expected_count = 2 + $DISKS + $NODES;
    if ($no_about_to_reboot_email_expected) {
       $expected_count--;
       $about_to_reboot = 1;
    }

    # Set up an array that is initialized to all 0's (offline)
    my @online_array = ( 0 .. ( $DISKS + $NODES - 1 ) );
    for ( my $i = 0 ; $i <= $#online_array ; $online_array[$i] = 0, $i++ ) { }

    # For each email recieved
    my $count_total = $#{ $ALERTS{$title} };
    for ( my $i = 0 ; $i <= $count_total ; $i++ ) {
        my @array = pop @{ $ALERTS{$title} };
        my ( $date, $subject, $message ) =
          ( $array[0][0], $array[0][1], $array[0][2] );
        $count++;

        # See if it is a node online email
        if ( $subject =~ /The NODE-(\d{3}) has joined the cell./i ) {
            if ($online_array[ $1 - $NODE_NUM_OFFSET ] == 1) {
                print_both(
                    " - WRN: DUPLICATE EMAIL - NODE $1 joined email received");
            } else {
                $online_array[ $1 - $NODE_NUM_OFFSET ] = 1;
                print_both(" - NODE $1 joined email received");
            }
        }

        # See if it is a disk online email
        elsif ( $subject =~ /DISK-(\d{3}):\d{1} is now enabled./i ) {
            my $node = $1;
            if ( $subject =~ /DISK-\d{3}:(\d{1}) is now enabled./i ) {
                my $disk = $1;
                my $array_index = $NODES +
                  ( ( $node - $NODE_NUM_OFFSET ) * $MAX_NUMBER_DISKS ) +
                  $disk;
                if ($online_array[$array_index] == 1) {
                    print_both(" - WRN: DUPLICATE EMAIL - DISK $node:$disk "
                      . "enabled email received"
                    );
                } else {
                    print_both(" - DISK $node:$disk enabled email received");
                    $online_array[$array_index] = 1;
                }
            }
        }

        # See if it is an "About to Reboot" email
        elsif ( $subject =~ /The cell is about to be rebooted/i ) {
            $about_to_reboot = 1;
            print_both(" - CELL about to reboot email received");
        }

        # See if it is a "Rebooting" email
        elsif ( $subject =~ /Cell is booting or master failed over./i ) {
            $rebooting = 1;
            print_both(" - CELL booting email received");
        }

        # Must be a bogus email
        else
        { # Insert it back in the beginning of the array so it won't be popped
            @{ $ALERTS{$title} } = ( @array, @{ $ALERTS{$title} } );
            $count--;
        }

    }    # for

    # Check all emails received
    for ( my $i = 0 ; $i <= $#online_array ; $i++ ) {
        if ( $online_array[$i] == 0 ) {
            if ( $i <= ( $NODES - 1 ) ) {
                my $node = $i + $NODE_NUM_OFFSET;
                if (!tried_to_send("The NODE-$node has joined the cell.")){
                    $status = 0;
                    print_both(" - MISSING EMAIL: Node $node");
                } else {
                    $count++;
                }
            }
            else {
                my $disk = ( ( $i - $NODES ) % $MAX_NUMBER_DISKS );
                my $node =
                  ( int( ( $i - $NODES ) / $MAX_NUMBER_DISKS ) ) +
                  $NODE_NUM_OFFSET;
                if (!tried_to_send("DISK-$node:$disk is now enabled")) {
                    $status = 0;
                    print_both(" - MISSING EMAIL: Disk $node:$disk");   
                } else {
                    $count++;
                }
            }
        }
    }
    if ( !$about_to_reboot && !$no_about_to_reboot_email_expected ) {
        if (tried_to_send("The cell is about to be rebooted")) {
            $about_to_reboot = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: About to reboot");
        }
    }
    if ( !$rebooting ) {
        if (tried_to_send("Cell is booting or master failed over")) {
            $rebooting = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: Cell is rebooting");
        }
    }

    # Check count == expected
    $count_ok = ( $expected_count == $count ) ? 1 : 0;
    print_both(" - Expected $expected_count email(s), got $count");

    return !( $status && $about_to_reboot && $rebooting && $count_ok );
}

sub post_alert_reboot_all {
    my $title = $_[0];
    return post_alert_reboot_hc($title);
}

sub post_alert_node_down_up {
    my $title = $_[0];
    my ($left_cell, $joined_cell, $node, $count, $count_ok, $status) =
      ( 0, 0, $NODE_STRING, 0, 0, 1 );
    my $expected_count = 6;

    # Set up an array that is initialized to all 0's (offline)
    my @disk_array = ( 0 .. ( $MAX_NUMBER_DISKS - 1 ) );
    for ( my $i = 0 ; $i <= $#disk_array ; $disk_array[$i] = 0, $i++ ) { }

    # For each email recieved
    my $count_total = $#{ $ALERTS{$title} };
    for ( my $i = 0 ; $i <= $count_total ; $i++ ) {
        my @array = pop @{ $ALERTS{$title} };
        my ( $date, $subject, $message ) =
          ( $array[0][0], $array[0][1], $array[0][2] );
        $count++;

        # See if it is a node left cell
        if ( $subject =~ /The (NODE-(\d{3})|node (\d{3})) has left the cell./i)
        {
            $left_cell = 1;
            print_both(" - $1 left cell email received");
        }

        # See if it is was master that left cell
        elsif ( $subject =~ /master failed over/i ) {
            $left_cell = 1;
            print_both(" - Master left cell email received");
        }

        # See if it is a node joined cell
        elsif ( $subject =~
            /The (NODE-(\d{3})|node (\d{3})) has joined the cell./i )
        {
            $joined_cell = 1;
            print_both(" - $1 joined cell email received");
        }

        # See if it is a disk online email
        elsif ( $subject =~ /DISK-(\d{3}):\d{1} is now enabled./i ) {
            if ( $subject =~ /DISK-\d{3}:(\d{1}) is now enabled./i ) {
                print_both(" - DISK $node:$1 enabled email received");
                $disk_array[$1] = 1;
            }
        }

        # Must be a bogus email
        else
        { # Insert it back in the beginning of the array so it won't be popped
            @{ $ALERTS{$title} } = ( @array, @{ $ALERTS{$title} } );
            $count--;
        }
    }

    if ( !$left_cell ) {
        if ($node == $MASTER_NODE) {
            if (tried_to_send("master failed over.")) {
                $left_cell = 1;
                $count++;
            } else { 
                print_both(" - MISSING EMAIL: Master has left cell");
            } 
        } else {
            if (tried_to_send("The NODE-$node has left the cell")) {
                $left_cell = 1;
                $count++;
            } else {
                print_both(" - MISSING EMAIL: Node $node has left cell");
            }
        }
    }
    if ( !$joined_cell ) {
        if (tried_to_send("The NODE-$node has joined the cell")) {
            $joined_cell = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: Node $node has joined the cell");
        }
    }
    for ( my $i = 0 ; $i < $MAX_NUMBER_DISKS ; $i++ ) {
        if ( !$disk_array[$i] ) {
            if (!tried_to_send("DISK-$node:$i is now enabled")) {
                $status = 0;
                print_both(" - MISSING EMAIL: Disk $node:$i is now enabled");
            } else {
                $count++;
            }
        }
    }

    # Check count == expected
    $count_ok = ( $expected_count == $count ) ? 1 : 0;
    print_both(" - Expected $expected_count email(s), got $count");

    return !( $left_cell && $joined_cell && $status && $count_ok );
}

sub post_alert_master_down_up {
    my $title = $_[0];
    return post_alert_node_down_up($title);
}

sub post_alert_wipe_hc {
    my $title = $_[0];
    my ( $count, $count_ok, $about_to_wipe, $success, $lost_quorum, $gained_quorum, $status ) =
      ( 0, 0, 0, 0, 0, 0, 1 );
    my $expected_count = 4 + ( $DISKS * 2 );

    # Set up 2 arrays that are initialized to all 0's (false)
    my @offline_array = ( 0 .. ( ( $DISKS ) - 1 ) );
    for ( my $i = 0 ; $i <= $#offline_array ; $offline_array[$i] = 0, $i++ ) {}
    my @online_array = @offline_array;

    # For each email recieved
    my $count_total = $#{ $ALERTS{$title} };
    for ( my $i = 0 ; $i <= $count_total ; $i++ ) {
        my @array = pop @{ $ALERTS{$title} };
        my ( $date, $subject, $message ) =
          ( $array[0][0], $array[0][1], $array[0][2] );
        $count++;

        # See if it is an "about to wipe" email
        if ( $subject =~ /About to wipe disks and schema/i ) {
            $about_to_wipe = 1;
            print_both(" - About to wipe email received");
        }

        # See if it is a "successfully wiped" email
        elsif (
            $subject =~ /The disks and schema have been wiped successfully/i )
        {
            $success = 1;
            print_both(" - Successfully wiped email received");
        }

        # See if it is the "Lost quorum" email
        elsif (
            $subject =~ /Lost quorum/i )
        {
            $lost_quorum = 1;
            print_both(" - Lost quorum email received");
        }

        # See if it is the "Gained quorum" email
        elsif (
            $subject =~ /Gained quorum/i )
        {
            $gained_quorum = 1;
            print_both(" - Gained quorum email received");
        }
           
        # See if it is a disk disabled email
        elsif ( $subject =~ /DISK-(\d{3}):\d{1} is now disabled./i ) {
            my $node = $1;
            if ( $subject =~ /DISK-\d{3}:(\d{1}) is now disabled./i ) {
                print_both(" - DISK $node:$1 disabled email received");
                $offline_array[ (
                      ( $node - $NODE_NUM_OFFSET ) * $MAX_NUMBER_DISKS ) + $1 ]
                  = 1;
            }
        }

        # See if it is a disk enabled email
        elsif ( $subject =~ /DISK-(\d{3}):\d{1} is now enabled./i ) {
            my $node = $1;
            if ( $subject =~ /DISK-\d{3}:(\d{1}) is now enabled./i ) {
                print_both(" - DISK $node:$1 enabled email received");
                $online_array[ (
                      ( $node - $NODE_NUM_OFFSET ) * $MAX_NUMBER_DISKS ) + $1 ]
                  = 1;
            }
        }

        # Must be a bogus email
        else
        { # Insert it back in the beginning of the array so it won't be popped
            @{ $ALERTS{$title} } = ( @array, @{ $ALERTS{$title} } );
            $count--;
        }
    }

    # Check if all emails received

    for ( my $i = 0 ; $i <= $#online_array ; $i++ ) {
        if ( !$online_array[$i] ) {
            my $disk = ( $i % $MAX_NUMBER_DISKS );
            my $node = ( int( $i / $MAX_NUMBER_DISKS ) ) + $NODE_NUM_OFFSET;
            if (!tried_to_send("DISK-$node:$disk is now enabled")) {
                $status = 0;
                print_both(" - MISSING EMAIL: Disk $node:$disk is now enabled");
            } else {
                $count++;
            }
        }
        if ( !$offline_array[$i] ) {
            my $disk = ( $i % $MAX_NUMBER_DISKS );
            my $node = ( int( $i / $MAX_NUMBER_DISKS ) ) + $NODE_NUM_OFFSET;
            if (!tried_to_send("DISK-$node:$disk is now disabled")) {
                $status = 0;
                print_both(" - MISSING EMAIL: Disk $node:$disk is now disabled");
            } else {
                $count++;
            }
        }
    }
    if ( !$about_to_wipe ) {
        if (tried_to_send("About to wipe disks and schema")) {
            $about_to_wipe = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: Cell is about to wipe");
        }
    }
    
    if ( !$success ) {
        if (tried_to_send("The disks and schema have been wiped successfully"))
        {
            $success = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: Cell has been successfully wiped");
        }
    }

    if ( !$lost_quorum ) {
        if (tried_to_send("Lost quorum")) {
            $lost_quorum = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: Lost quorum");
        }
    }

    if ( !$gained_quorum ) {
        if (tried_to_send("Gained quorum")) {
            $gained_quorum = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: Gained quorum");
        }
    }

    if ($IS_MULTI_CLUSTER) {
        $expected_count *= 2;
    }
    # Check count == expected
    $count_ok = ( $expected_count == $count ) ? 1 : 0;
    print_both(" - Expected $expected_count email(s), got $count");

    return !( $status && $about_to_wipe && $success && $count_ok );
}

sub post_alert_disable_disk {
    my $title = $_[0];
    my ( $disabled, $enabled, $count, $count_ok, $expected_count ) =
      ( 0, 0, 0, 0, 2 );

    # For each email recieved
    my $count_total = $#{ $ALERTS{$title} };
    for ( my $i = 0 ; $i <= $count_total ; $i++ ) {
        my @array = pop @{ $ALERTS{$title} };
        my ( $date, $subject, $message ) =
          ( $array[0][0], $array[0][1], $array[0][2] );
        $count++;

        # See if it is a disk disabled email
        if ( $subject =~ /DISK-(\d{3}):\d{1} is now disabled./i ) {
            my $node = $1;
            if ( $subject =~ /DISK-\d{3}:(\d{1}) is now disabled./i ) {
                print_both(" - DISK $node:$1 disabled email received");
                if ( $DISK_STRING eq "DISK-$node:$1" ) {
                    $disabled = 1;
                }
            }
        }

        # See if it is a disk enabled email
        elsif ( $subject =~ /DISK-(\d{3}):\d{1} is now enabled./i ) {
            my $node = $1;
            if ( $subject =~ /DISK-\d{3}:(\d{1}) is now enabled./i ) {
                print_both(" - DISK $node:$1 enabled email received");
                if ( $DISK_STRING eq "DISK-$node:$1" ) {
                    $enabled = 1;
                }
            }
        }

        # Must be a bogus email
        else
        { # Insert it back in the beginning of the array so it won't be popped
            @{ $ALERTS{$title} } = ( @array, @{ $ALERTS{$title} } );
            $count--;
        }
    }

    if ( !$disabled ) {
        if (tried_to_send("$DISK_STRING is now disabled")) {
            $disabled = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: $DISK_STRING is now disabled");
        }
    }
    if ( !$enabled ) {
        if (tried_to_send("$DISK_STRING is now enabled")) {
            $enabled = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: $DISK_STRING is now enabled");
        }
    }

    # Check count == expected
    $count_ok = ( $expected_count == $count ) ? 1 : 0;
    print_both(" - Expected $expected_count email(s), got $count");
    return !( $disabled && $enabled && $count_ok );
}

sub post_alert_sw1_failover {
    my $title = $_[0];
    return post_alert_reboot_sw1($title);
}

sub post_alert_sw2_failover {
    my $title = $_[0];
    return post_alert_reboot_sw2($title);
}
sub post_alert_reboot_sw1 {
    my $title = $_[0];
    my ( $switch1, $switch2, $count, $count_ok, $expected_count ) =
      ( 0, 0, 0, 0, 2 );

    # For each email recieved
    my $count_total = $#{ $ALERTS{$title} };
    for ( my $i = 0 ; $i <= $count_total ; $i++ ) {
        my @array = pop @{ $ALERTS{$title} };
        my ( $date, $subject, $message ) =
          ( $array[0][0], $array[0][1], $array[0][2] );
        $count++;

        # See if it is a switch 1 email
        if (
            $subject =~ /Switch failover: Switch 1 is now the active switch./i)
        {
            print_both(" - Switch 1 active email received");
            $switch1 = 1;
        }

        # See if it is a switch 2 email
        elsif (
            $subject =~ /Switch failover: Switch 2 is now the active switch./i)
        {
            print_both(" - Switch 2 active email received");
            $switch2 = 1;
        }

        # Must be a bogus email
        else
        { # Insert it back in the beginning of the array so it won't be popped
            @{ $ALERTS{$title} } = ( @array, @{ $ALERTS{$title} } );
            $count--;
        }
    }

    if ( !$switch1 ) {
        if (tried_to_send("Switch 1 is now the active switch")) {
            $switch1 = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: Switch 1 is now the active switch");
        }
    }
    if ( !$switch2 ) {
        if (tried_to_send("Switch 2 is now the active switch")) {
            $switch2 = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: Switch 2 is now the active switch");
        }
    }

    # Check count == expected
    $count_ok = ( $expected_count == $count ) ? 1 : 0;
    print_both(" - Expected $expected_count email(s), got $count");
    return !( $switch1 && $switch2 && $count_ok );
}

sub post_alert_reboot_sw2 {
    my $title = $_[0];
    my ( $offline, $online, $count, $count_ok, $expected_count ) = 
       ( 0, 0, 0, 0, 2 );

    # For each email recieved
    my $count_total = $#{ $ALERTS{$title} };
    for ( my $i = 0 ; $i <= $count_total ; $i++ ) {
        my @array = pop @{ $ALERTS{$title} };
        my ( $date, $subject, $message ) =
          ( $array[0][0], $array[0][1], $array[0][2] );
        $count++;

        # See if it is an offline email
        if ( $subject =~ /Backup Switch is now offline/i ) {
            print_both(" - Switch 2 offline email received");
            $offline = 1;
        }
        # See if it is an offline email
        elsif ( $subject =~ /Backup Switch is now online/i ) {
            print_both(" - Switch 2 online email received");
            $online = 1;
        }

        # Must be a bogus email
        else
        { # Insert it back in the beginning of the array so it won't be popped
            @{ $ALERTS{$title} } = ( @array, @{ $ALERTS{$title} } );
            $count--;
        }
    }

    if ( !$offline ) {
        if (tried_to_send("Backup Switch is now offline")) {
            $offline = 1;
            $count++;
        }
        print_both(" - MISSING EMAIL: Switch 2 is now offline");
    }
    if ( !$online ) {
        if (tried_to_send("Backup Switch is now online")) {
            $online = 1;
            $count++;
        }
        print_both(" - MISSING EMAIL: Switch 2 is now online");
    }

    # Check count == expected
    $count_ok = ( $expected_count == $count ) ? 1 : 0;
    print_both(" - Expected $expected_count email(s), got $count");
    return !( $offline && $online && $count_ok );
}

sub post_alert_change_ips {
    my $title = $_[0];
    my ( $data, $admin, $cheat, $count, $count_ok, $expected_count ) =
      ( 0, 0, 0, 0, 0, 3 );

    # Get rid of all the reboot emails
    my $reboot = !( post_alert_reboot_hc($title) );
    print_both("");

    # For each email recieved
    my $count_total = $#{ $ALERTS{$title} };
    for ( my $i = 0 ; $i <= $count_total ; $i++ ) {
        my @array = pop @{ $ALERTS{$title} };
        my ( $date, $subject, $message ) =
          ( $array[0][0], $array[0][1], $array[0][2] );
        $count++;

        # See if it is a data email
        if ( $subject =~ /The dataVIP has been changed to/i ) {
            print_both(" - dataVIP changed email received");
            $data = 1;
        }

        # See if it is a admin email
        elsif ( $subject =~ /The adminVIP has been changed to/i ) {
            print_both(" - adminVIP changed email received");
            $admin = 1;
        }

        # See if it is a cheat email
        elsif ( $subject =~ /The service processor IP has been changed to/i ) {
            print_both(" - cheatVIP changed email received");
            $cheat = 1;
        }

        # Must be a bogus email
        else
        { # Insert it back in the beginning of the array so it won't be popped
            @{ $ALERTS{$title} } = ( @array, @{ $ALERTS{$title} } );
            $count--;
        }
    }

    if ( !$data ) {
        if (tried_to_send("The dataVIP has been changed to")) {
            $data = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: DataVIP has been changed");
        }
    }
    if ( !$admin ) {
        if (tried_to_send("The adminVIP has been changed to")) {
            $admin = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: AdminVIP has been changed");
        }
    }
    if ( !$cheat ) {
        if (tried_to_send("The service processor IP has been changed to")) {
            $cheat = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: CheatVIP has been changed");
        }
    }

    # Check count == expected
    $count_ok = ( $expected_count == $count ) ? 1 : 0;
    print_both(" - Expected $expected_count email(s), got $count");
    return !( $data && $admin && $cheat && $reboot && $count_ok );
}

sub post_alert_change_smtp {
    my $title = $_[0];
    my ( $smtp, $count, $count_ok, $expected_count ) = ( 0, 0, 0, 1 );

    # Get rid of all the reboot emails
    my $reboot = !( post_alert_reboot_hc($title) );
    print_both("");
    
    # For each email recieved
    my $count_total = $#{ $ALERTS{$title} };
    for ( my $i = 0 ; $i <= $count_total ; $i++ ) {
        my @array = pop @{ $ALERTS{$title} };
        my ( $date, $subject, $message ) =
          ( $array[0][0], $array[0][1], $array[0][2] );
        $count++;

        # See if it is a smtp email
        if ( $subject =~ /The SMTP IP has been changed/i ) {
            print_both(" - SMTP server changed email received");
            $smtp = 1;
        }

        # Must be a bogus email
        else
        { # Insert it back in the beginning of the array so it won't be popped
            @{ $ALERTS{$title} } = ( @array, @{ $ALERTS{$title} } );
            $count--;
        }
    }

    if ( !$smtp ) {
        if (tried_to_send("\?\?\?\?\?\?\?")) {
            $smtp = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: SMTP server has changed");
        }

    }

    # Check count == expected
    $count_ok = ( $expected_count == $count ) ? 1 : 0;
    print_both(" - Expected $expected_count email(s), got $count");
    return !( $smtp && $reboot && $count_ok );
}

sub post_alert_reboot_cheat {
    my $title = $_[0];
    my ( $cheat_down, $cheat_up, $count, $count_ok, $expected_count ) =
      ( 0, 0, 0, 0, 2 );

    # For each email recieved
    my $count_total = $#{ $ALERTS{$title} };
    for ( my $i = 0 ; $i <= $count_total ; $i++ ) {
        my @array = pop @{ $ALERTS{$title} };
        my ( $date, $subject, $message ) =
          ( $array[0][0], $array[0][1], $array[0][2] );
        $count++;

        # See if it is a cheat down email
        if ( $subject =~ /Service node is now offline/i ) {
            print_both(" - Service node offline email received");
            $cheat_down = 1;
        }

        # See if it is a cheat up email
        elsif ( $subject =~ /Service node is now online/i ) {
            print_both(" - Service node online email received");
            $cheat_up = 1;
        }

        # Must be a bogus email
        else
        { # Insert it back in the beginning of the array so it won't be popped
            @{ $ALERTS{$title} } = ( @array, @{ $ALERTS{$title} } );
            $count--;
        }
    }

    if ( !$cheat_down ) {
        if (tried_to_send("Service node is now offline")) {
            $cheat_down = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: Service processor is offline");
        }
    }
    if ( !$cheat_up ) {
        if (tried_to_send("Service node is now online")) {
            $cheat_up = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: Service processor is online");
        }
    }

    # Check count == expected
    $count_ok = ( $expected_count == $count ) ? 1 : 0;
    print_both(" - Expected $expected_count email(s), got $count");
    return !( $cheat_down && $cheat_up && $count_ok );
}

sub post_alert_power_node {
    my $title = $_[0];
    return post_alert_node_down_up($title);
}

sub post_alert_shutdown_cluster {
    my $title = $_[0];
    my ( $shutdown, $status, $count, $count_ok, $expected_count ) =
      ( 0, 1, 0, 0, 1 );

    # Get rid of all the reboot emails
    my $reboot = !( post_alert_reboot_hc($title, 1) );
    print_both("");
    
    # For each email recieved
    my $count_total = $#{ $ALERTS{$title} };
    for ( my $i = 0 ; $i <= $count_total ; $i++ ) {
        my @array = pop @{ $ALERTS{$title} };
        my ( $date, $subject, $message ) =
          ( $array[0][0], $array[0][1], $array[0][2] );
        $count++;

        # See if it is an about to be shutdown email
        if ( $subject =~ /The cell is about to be shut down/i ) {
            print_both(" - Cell about to shutdown email received");
            $shutdown = 1;
        }

        # Must be a bogus email
        else
        { # Insert it back in the beginning of the array so it won't be popped
            @{ $ALERTS{$title} } = ( @array, @{ $ALERTS{$title} } );
            $count--;
        }
    }

    if ( !$shutdown ) {
        if (tried_to_send("The cell is about to be shut down")) {
            $shutdown = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: Cell about to shutdown");
        }
    }

    # Check count == expected
    $count_ok = ( $expected_count == $count ) ? 1 : 0;
    print_both(" - Expected $expected_count email(s), got $count");
    return !( $shutdown && $status && $reboot && $count_ok );
}

sub post_alert_change_capacity {
    my $title = $_[0];
    my ( $capacity, $count, $count_ok, $expected_count ) = ( 0, 0, 0, 1 );
    my @nodes = reverse( 0 .. ( $NODES - 1 ) );

    # Get rid of all the reboot emails
    my $reboot = !( post_alert_reboot_hc( $title, 1 ) );
    print_both("");

    # Set up an array that is initialized to all 0's (offline)
    my @disabled_array = ( 0 .. ( $NODES - 1 ) );
    for (my $i = 0 ; $i <= $#disabled_array ; $disabled_array[$i] = 0, $i++ ){}

    # For each email recieved
    my $count_total = $#{ $ALERTS{$title} };
    for ( my $i = 0 ; $i <= $count_total ; $i++ ) {
        my @array = pop @{ $ALERTS{$title} };
        my ( $date, $subject, $message ) =
          ( $array[0][0], $array[0][1], $array[0][2] );
        $count++;

        # See if it is a capacity email
        if ( $subject =~ /is full and is not accepting store requests/i ) {
            print_both(" - Capacity full email received");
            $capacity = 1;
        }

        # Must be a bogus email
        else
        { # Insert it back in the beginning of the array so it won't be popped
            @{ $ALERTS{$title} } = ( @array, @{ $ALERTS{$title} } );
            $count--;
        }
    }

    if ( !$capacity ) {
        if (tried_to_send("is full and is not accepting store requests")) {
            $capacity = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: Capacity full email");
        }
    }

    # Check count == expected
    $count_ok = ( $expected_count == $count ) ? 1 : 0;
    print_both(" - Expected $expected_count email(s), got $count");
    
    # Make sure the nohoneycomb and noconfig files now before other tests
    print_both(" - Making sure noreboot and nohoneycomb are removed...");
    remove_nohoneycomb_noreboot(@nodes);

    # Do a cli reboot
    add_no_fsck_flags();
    print_both(" - Rebooting all nodes...");
    foreach (@nodes) {
        my $node = $_ + $NODE_NUM_OFFSET;
        my $return = run_system_command(
                       "$SSH_CHEAT ipmitool -I lan -f $IPMI_PASS_LOCATION "
                     . "-U Admin -H hcb$node-sp chassis power cycle"
                     );
    }

    # Wait for cluster to reboot
    pre_alert_reboot_hc();

    # Delete new emails
    erase_email();
    
    return !( $capacity && $reboot && $count_ok );
}

sub post_alert_update_schema {
    my $title = $_[0];
    my ( $update, $count, $count_ok) = ( 0, 0, 0);
    my $expected_count = ( $IS_MULTI_CLUSTER ) ? 2 : 1; 
    
    # For each email recieved
    my $count_total = $#{ $ALERTS{$title} };
    for ( my $i = 0 ; $i <= $count_total ; $i++ ) {
        my @array = pop @{ $ALERTS{$title} };
        my ( $date, $subject, $message ) =
          ( $array[0][0], $array[0][1], $array[0][2] );
        $count++;

        # See if it is a update email
        if ( $subject =~ /The schema has been updated/i ) {
            print_both(" - Schema updated email received");
            $update = 1;
        }

        # Must be a bogus email
        else
        { # Insert it back in the beginning of the array so it won't be popped
            @{ $ALERTS{$title} } = ( @array, @{ $ALERTS{$title} } );
            $count--;
        }
    }

    if ( !$update ) {
        if (tried_to_send("The schema has been updated")) {
            $update = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: Schema updated");
        }
    }

    # Check count == expected
    $count_ok = ( $expected_count == $count ) ? 1 : 0;
    print_both(" - Expected $expected_count email(s), got $count");
    return !( $update && $count_ok );
}

sub post_alert_clear_schema {
    my $title = $_[0];
    my ( $clear, $count, $count_ok, $expected_count ) = ( 0, 0, 0, 1 );

    # For each email recieved
    my $count_total = $#{ $ALERTS{$title} };
    for ( my $i = 0 ; $i <= $count_total ; $i++ ) {
        my @array = pop @{ $ALERTS{$title} };
        my ( $date, $subject, $message ) =
          ( $array[0][0], $array[0][1], $array[0][2] );
        $count++;

        # See if it is a clear email
        if ( $subject =~ /The schema has been cleared/i ) {
            print_both(" - Schema cleared email received");
            $clear = 1;
        }

        # Must be a bogus email
        else
        { # Insert it back in the beginning of the array so it won't be popped
            @{ $ALERTS{$title} } = ( @array, @{ $ALERTS{$title} } );
            $count--;
        }
    }
    if ( !$clear ) {
        if (tried_to_send("The schema has been cleared")) {
            $clear = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: Schema cleared");
        }
    }

    # Check count == expected
    $count_ok = ( $expected_count == $count ) ? 1 : 0;
    print_both(" - Expected $expected_count email(s), got $count");
    return !( $clear && $count_ok );
}

sub post_alert_upgrade_cell {
    if ($NO_UPGRADE) {
        print_both(" - Quick option detected in command line, skipping test");
        return 0;    #OK
    }
    my $title = $_[0];
    my ( $about, $succeed, $count, $count_ok, $expected_count ) =
      ( 0, 0, 0, 0, 2 );

    # Get rid of all the reboot emails
    my $reboot = !( post_alert_reboot_hc($title) );
    print_both("");
    
    # Set up an array that is initialized to all 0's (offline)
    my @disabled_array = ( 0 .. ( $NODES - 1 ) );
    for (my $i = 0 ; $i <= $#disabled_array ; $disabled_array[$i] = 0, $i++ ){}

    # For each email recieved
    my $count_total = $#{ $ALERTS{$title} };
    for ( my $i = 0 ; $i <= $count_total ; $i++ ) {
        my @array = pop @{ $ALERTS{$title} };
        my ( $date, $subject, $message ) =
          ( $array[0][0], $array[0][1], $array[0][2] );
        $count++;

        # See if it is an about to upgrade email
        if ( $subject =~ /About to upgrade the cell/i ) {
            print_both(" - About to upgrade email received");
            $about = 1;
        }

        # See if it is an upgraded succeded email
        elsif ( $subject =~ /Cell upgrade succeeded/i ) {
            print_both(" - Upgrade succeeded email received");
            $succeed = 1;
        }

        # Must be a bogus email
        else
        { # Insert it back in the beginning of the array so it won't be popped
            @{ $ALERTS{$title} } = ( @array, @{ $ALERTS{$title} } );
            $count--;
        }
    }

    if ( !$about ) {
        if (tried_to_send("About to upgrade the cell")) {
            $about = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: About to upgrade");
        }    
        
    }
    if ( !$succeed ) {
        if (tried_to_send("Cell upgrade succeeded")) {
            $succeed = 1;
            $count++;
        } else {
            print_both(" - MISSING EMAIL: Upgrade succeeded");
        }
    }

    # Check count == expected
    $count_ok = ( $expected_count == $count ) ? 1 : 0;
    print_both(" - Expected $expected_count email(s), got $count");
    return !( $about && $succeed && $reboot && $count_ok );

}

###############################################################################
# Cleanup Subroutines - Called to restore the initial status

# Restore the initial config of alertcfg
sub cleanup_alertcfg {
    my $title = $_[0];
    my $alertcfg =
      run_system_command("$SSH_ADMIN alertcfg del to $MAIL_BOX\@$MAIL_SERVER");
    foreach ( @{ $RESTORE_COMMANDS{$title} } ) {
        print_both(" - Restoring: $_");
        $alertcfg = run_system_command("$SSH_ADMIN $_");
        if ($?) {
            print_both(" - UNABLE TO EXECUTE COMMAND");
            return 1;    # Problem
        }
    }
    return 0;
}

sub cleanup_ips {
    my $title = $_[0];
    foreach ( @{ $RESTORE_COMMANDS{$title} } ) {
        print_both(" - Restoring: $_");
        my $ip = run_system_command("$SSH_ADMIN $_",
                                    $CHANGE_IPS_TIMEOUT,
                                    $MAX_CHANGE_IPS_ITERATIONS
                                    );
        if ($?) {
            print_both(" - UNABLE TO EXECUTE COMMAND");
            return 1;    # Problem
        }
    }
    $ADMIN_VIP = $ORIGINAL_ADMIN;
    $CHEAT_VIP = $ORIGINAL_CHEAT;
    $SSH_ADMIN = $SSH . $SSH_OPTIONS . " admin\@$ADMIN_VIP";
    $SSH_CHEAT = $SSH . $SSH_OPTIONS . " root\@$CHEAT_VIP";

    if ( pre_alert_reboot_hc() ) {
        return 1;        # Problem
    }
    return 0;
}

sub cleanup_smtp {
    my $title = $_[0];
    foreach ( @{ $RESTORE_COMMANDS{$title} } ) {
        print_both("Restoring: $_");
        my $smtp = run_system_command("$SSH_ADMIN $_",
                                      $SMTP_TIMEOUT,
                                      $MAX_SMTP_ITERATIONS
                                      );
        if ($?) {
            print_both(" - UNABLE TO EXECUTE COMMAND");
            return 1;    # Problem
        }
    }

    # Is there a reboot command?
    if ( $#{ $RESTORE_COMMANDS{$title} } > 0 ) {
        if ( pre_alert_reboot_hc(1) ) {
            return 1;    # Problem
        }
    }
    return 0;            # OK
}

###############################################################################
###############################################################################
########################                               ########################
########################  Other Subroutines / Helpers  ########################
########################                               ########################
###############################################################################
###############################################################################

# NULL SUBROUTINE
#  For the purposes of filling out the table correctly,
#  if a command does not need a pre or post test, it can
#  reference this routine instead
sub null_subroutine {
    return 0;
}

# PING TEST
# Input: STRING of a machine to ping
# Function: Attempts to ping the machine specified by the input string.
#  It has 2 global settings, $PING_ATTEMPTS and $PING_TIMEOUT, both settable
#  in the global variables section above.
sub ping_test {
    for ( my $attempt = $MAX_PING_ITERATIONS ; $attempt > 0 ; $attempt-- ) {

        #If we can ping, return.
        run_system_command("$PING -c $PING_ATTEMPTS -t $PING_TIMEOUT @_",
                            $PING_TIMEOUT,
                            $PING_ATTEMPTS
                            );
        if ( !$? ) {
            return 1;    # OK
        }

        #If not, sleep then try again
        sleep $SLEEP_PING;
    }

    #Else, ping is unsuccessful
    print_both(" - WARNING: UNABLE TO PING CLUSTER");
    return 0;            # PROBLEM
}

sub can_ping_switch_1_from_cheat {
    my $ping = run_system_command("$SSH_CHEAT $PING_SW1");
    if ($?) {
        print_both("ERR - Cannot ping switch 1 from service processor");
        return 0; # PROBLEM
    }
    return 1; # OK
}
sub can_ping_switch_2_from_switch_1 {
    my $ping = run_system_command("$SSH_CHEAT $SSH_SWITCH $SWITCH2_DATE");
    if ($?) {
        print_both("ERR - Cannot ping switch 2 from switch 1");
        return 0; # PROBLEM
    }
    return 1; # OK
}


# DETECT_HIVE
# Input: STRING of an admin vip
# Funtion: First checks to see if the CLI is responsive.  If not, an email is
#  sent and the script exits.  If responsive, hiveadm is called to detect a
#  multi-cell configuration and to obtain cell numbers for the hashing of
#  results.
sub detect_hive {
    my $hiveadm = run_system_command("$SSH_ADMIN hiveadm");
    if ($?) {
        print_both("ERR: CLI is not functioning on @_");
        exit -1;
    }
    foreach ( split( /\n/, $hiveadm ) ) {
        if (/Cell (\d+)/) { push( @CELL_IDS, $1 ) }
    }
    foreach (@CELL_IDS) {
        print_both(" - Detected cell $_");
    }
    $MASTER_CELL = $CELL_IDS[0];
    if ( $#CELL_IDS > 0 ) {
        return 1;
    }
    return 0;
}

# CLI TEST
# Input: INTEGER of a cell number
# Function: Attempts to run 'date' on the admin vip.
#  If the command is unresponsive, a state variable is set to stop further
#  testing until cli is back up.
#
sub cli_test {
    my $date = run_system_command("$SSH_ADMIN date -c $CURRENT_CELL");
    if ($?) {
        print_both(" - WARNING: CLI is not responsive.");
        return 0;    # PROBLEM
    }
    return 1;        # OK
}

sub is_all_down {

    # Waiting for HC to go down
    print_both(" - Waiting for honeycomb to go offline...");
    my $i = $MAX_HC_DOWN_ITERATIONS;
    my ( $date, $offline, $ret ) = ( "", 0, 0 );
    do {
        eval {
            print ".";
            local $SIG{ALRM} = sub { die "timeout\n" };
            $i--;
            $ret = run_system_command(
                "$PING -c $PING_ATTEMPTS -t $PING_TIMEOUT $ADMIN_VIP",
                $PING_TIMEOUT,
                $PING_ATTEMPTS
            );
            if ($? || $ret) {

                # We can't ping so it must be down
                $offline = 1;
            }
            alarm $DEFAULT_EVAL_TIMEOUT;
            $date = run_system_command("$SSH_ADMIN date -c $CURRENT_CELL",
                                        $DATE_TIMEOUT,
                                        1 # iteration
                                        );
            alarm 0;
            sleep $SLEEP_DOT;
          }

    # while still iterations,online and we got a response to date (no timeout))
    } while ( ( $i > 0 ) && !$offline && !$@ );

    print("\n");
    return 1 if $i;    # If we did not time out, return
    print_both(" - WARNING: Unable to detect Honeycomb offline");
    return 0;          # PROBLEM
}

sub is_all_up {

    # Waiting for HC to come up
    print_both(" - Waiting for honeycomb to come online...");
    my $i = $MAX_HC_UP_ITERATIONS;
    my ( $date, $online ) = ( "", 0 );
    do {
        eval {
            print ".";
            local $SIG{ALRM} = sub { die "timeout\n" };
            $i--;
            run_system_command(
                "$PING -c $PING_ATTEMPTS -t $PING_TIMEOUT $ADMIN_VIP",
                $PING_TIMEOUT,
                $PING_ATTEMPTS
            );
            if ( !$? ) {

                # We can ping so it must be up again (except cli)
                $online = 1;
            }
            alarm $DEFAULT_EVAL_TIMEOUT;
            $date = run_system_command("$SSH_ADMIN date -c $CURRENT_CELL",
                                        $DATE_TIMEOUT,
                                        1 # iteration
                                        );
            alarm 0;
            sleep $SLEEP_DOT;
          }

    # while still iterations, offline and timeout occured)
    } while ( ( $i > 0 ) && !$online && $@ );

    print("\n");
    return 1 if $i;    # If we did not time out, return
    print_both(" - WARNING: Unable to detect Honeycomb online");    
    return 0;          # PROBLEM
}

sub is_all_online {
    my $i         = $MAX_SYSSTAT_ONLINE_ITERATIONS;
    my $sysstat_v = "";
    do {
        $i--;
        $sysstat_v = run_system_command("$SSH_ADMIN sysstat -c $CURRENT_CELL -v");
        unless ($i == ($MAX_SYSSTAT_ONLINE_ITERATIONS - 1)) {
            print "."; sleep $SLEEP_ONLINE;
        }
    # while still iterations and timeout, or iterations and offline/failed
    } while ( ( $i > 0 )
        && ( $@ || ( $sysstat_v =~ /OFFLINE|failed|communication problem/i ) )
        );

    print("\n");
    return is_HAFaultTolerant() if $i;    # If we did not time out, check HADB
    print_both(" - WARNING: Unable to detect all disks online");
    return 0;          # PROBLEM

}

sub is_HAFaultTolerant {
    my $i         = $MAX_SYSSTAT_ONLINE_ITERATIONS;
    my $sysstat   = "";
    do {
        $i--;
        $sysstat = run_system_command("$SSH_ADMIN sysstat -c $CURRENT_CELL");
        unless ($i == ($MAX_SYSSTAT_ONLINE_ITERATIONS - 1)) {
            print "."; sleep $SLEEP_ONLINE;
        }
    # while still iterations and timeout, or iterations and offline/failed
    } while ( ( $i > 0 )
        && ( $@ || ( $sysstat !~ /HAFaultTolerant/ ) )
        );

    print("\n");
    return 1 if $i;    # If we did not time out, return
    print_both(" - WARNING: Unable to detect HAFaultTolerance");
    return 0;          # PROBLEM
}

sub is_password_file_ok {
    my $ssh_location = $_[0];
    print_both(" - Checking password file...");
    my $exists = run_system_command(
                   "$ssh_location \'if [ -e $IPMI_PASS_LOCATION ];"
                 . " then echo 1; else echo 0; fi\'"
                 );
    if ( ( $exists eq 0 ) || $? ) {
        run_system_command("$ssh_location mkdir $IPMI_PASS_DIRECTORY");
        run_system_command(
            "$ssh_location \'echo honeycomb > $IPMI_PASS_LOCATION\'");
    }
    my $cat = run_system_command("$ssh_location cat $IPMI_PASS_LOCATION");
    if ( $cat !~ /honeycomb/ ) {
        print_both("INCORRECT IPMI PASSWORD: $cat");
        return 0;    # Problem
    }
    return 1;        #OK
}

sub add_no_fsck_flags {
	print_both(" - Adding no FSCK flags...");
	for (my $i = 0; $i < $NODES; $i++) {
    	my $node = $i + $NODE_NUM_OFFSET;
    	my $port = $i + $NODE_PORT_OFFSET;
    	print_log(" - node hcb$node");
    	run_system_command(
    	    "$SSH $SSH_OPTIONS -p $port -l root $ADMIN_VIP \"touch $FSCK1\"");
    	run_system_command(
    	    "$SSH $SSH_OPTIONS -p $port -l root $ADMIN_VIP \"touch $FSCK2\"");
    	run_system_command(
    	    "$SSH $SSH_OPTIONS -p $port -l root $ADMIN_VIP \"touch $FSCK3\"");
    	run_system_command(
    	    "$SSH $SSH_OPTIONS -p $port -l root $ADMIN_VIP \"touch $FSCK4\"");    	
	}
}

sub create_nohoneycomb_noreboot {
    my @nodes = @_;
    print_both(" - Creating nohoneycomb and noreboot on all nodes...");
    my $return;
    foreach (@nodes) {
        my $port = $_ + $NODE_PORT_OFFSET;
        my $node = $_ + $NODE_NUM_OFFSET;
        print_log("NODE: $_");
        print_log(
           "$SSH $SSH_OPTIONS -p $port -l root $ADMIN_VIP touch $NOHONEYCOMB");
        $return = run_system_command(
           "$SSH $SSH_OPTIONS -p $port -l root $ADMIN_VIP touch $NOHONEYCOMB");
        print_log(
           "$SSH $SSH_OPTIONS -p $port -l root $ADMIN_VIP touch $NOREBOOT");
        $return = run_system_command(
           "$SSH $SSH_OPTIONS -p $port -l root $ADMIN_VIP touch $NOREBOOT");
    }
    if ($IS_MULTI_CLUSTER) {
        foreach (@nodes) {
            my $port = $_ + $NODE_PORT_OFFSET;
            my $node = $_ + $NODE_NUM_OFFSET;
            print_log("NODE: $node");
            print_log(
               "$SSH $SSH_OPTIONS -p $port -l root $ORIGINAL_ADMIN_2 rm -f $NOHONEYCOMB");
            my $return = run_system_command(
               "$SSH $SSH_OPTIONS -p $port -l root $ORIGINAL_ADMIN_2 rm -f $NOHONEYCOMB");

            print_log(
                "$SSH $SSH_OPTIONS -p $port -l root $ORIGINAL_ADMIN_2 rm -f $NOREBOOT");
            $return = run_system_command(
                "$SSH $SSH_OPTIONS -p $port -l root $ORIGINAL_ADMIN_2 rm -f $NOREBOOT");

        }
    }
    return 0;
}
sub remove_nohoneycomb_noreboot {
    my @nodes = @_;
    print_both(" - Removing nohoneycomb and noreboot on all nodes...");
    foreach (@nodes) {
        my $port = $_ + $NODE_PORT_OFFSET;
        my $node = $_ + $NODE_NUM_OFFSET;
        print_log("NODE: $node");
        print_log(
           "$SSH $SSH_OPTIONS -p $port -l root $ADMIN_VIP rm -f $NOHONEYCOMB");
        my $return = run_system_command(
           "$SSH $SSH_OPTIONS -p $port -l root $ADMIN_VIP rm -f $NOHONEYCOMB");

        print_log(
            "$SSH $SSH_OPTIONS -p $port -l root $ADMIN_VIP rm -f $NOREBOOT");
        $return = run_system_command(
            "$SSH $SSH_OPTIONS -p $port -l root $ADMIN_VIP rm -f $NOREBOOT");

    }
    
    if ($IS_MULTI_CLUSTER) {
        foreach (@nodes) {
            my $port = $_ + $NODE_PORT_OFFSET;
            my $node = $_ + $NODE_NUM_OFFSET;
            print_log("NODE: $node");
            print_log(
               "$SSH $SSH_OPTIONS -p $port -l root $ORIGINAL_ADMIN_2 rm -f $NOHONEYCOMB");
            my $return = run_system_command(
               "$SSH $SSH_OPTIONS -p $port -l root $ORIGINAL_ADMIN_2 rm -f $NOHONEYCOMB");

            print_log(
                "$SSH $SSH_OPTIONS -p $port -l root $ORIGINAL_ADMIN_2 rm -f $NOREBOOT");
            $return = run_system_command(
                "$SSH $SSH_OPTIONS -p $port -l root $ORIGINAL_ADMIN_2 rm -f $NOREBOOT");

        }
    }
    return 0;
}

sub run_system_command {
    my ( $command, $timeout, $iterations) = @_;
    my $return;
    $timeout    ||= $DEFAULT_EVAL_TIMEOUT;
    $iterations ||= $DEFAULT_EVAL_ITERATIONS;
    do {
        eval {
            $iterations--;
            local $SIG{ALRM} = sub { die "timeout\n" };
            alarm $timeout;
            $return = `$command`;
            $/ = "\n"; chomp $return; chomp $return; # Sometimes 2 "\n";
            foreach (split /\n/, $return) {
                print_log("---> $_");
            }
            alarm 0;
          }

    # while there are iterations left and an alarm happened
    } while ( ( $iterations > 0 ) && $@ );

    if ( ($iterations <= 0) && $@ ) {    # We timed out on all iterations
        print_both(" - Command timed out");
        print_both("UNABLE TO EXECUTE COMMAND");
        return 1;                # Problem
    }
    else {                       # We did not timeout
        if ($?) {                # We did not get a successful system return
            #print_both(" - WARNING: Command returned non-zero value...");
        }
        return $return;
    }
}

sub is_cluster_ok {
    return ( ping_test($ADMIN_VIP)
          && ping_test($CHEAT_VIP)
          && can_ping_switch_1_from_cheat()
          && can_ping_switch_2_from_switch_1()
          && cli_test()
          && is_all_online() );
}

sub can_heal_cluster {
    if ( !ping_test($ADMIN_VIP) && !ping_test($CHEAT_VIP)) {
        print_both("UNABLE TO FIX CLUSTER");
        print_final_status();
        exit 1;
    }
    if ( !ping_test($ADMIN_VIP) ) {
        run_system_command("$SSH_CHEAT \'$SSH 10.123.45.20 reboot\'");
        sleep $SLEEP_REBOOT;
    }
    if ( !ping_test($CHEAT_VIP) ) {
        reboot_cheat();
        sleep $SLEEP_REBOOT;
    }
    if ( !cli_test() ) {
        run_system_command("$SSH_CHEAT \'$SSH 10.123.45.20 reboot\'");
        sleep $SLEEP_REBOOT;
    }
    
    if (!can_ping_switch_1_from_cheat()) {
        print_both("UNABLE TO FIX CLUSTER");
        print_final_status();
        exit 1;
    }
    
    if (!can_ping_switch_2_from_switch_1()) {
        print_both("UNABLE TO FIX CLUSTER");
        print_final_status();
        exit 1;
    }
    
    my $sysstat_v = run_system_command(
        "$SSH_ADMIN sysstat -v -c $CURRENT_CELL",
        $DEFAULT_EVAL_TIMEOUT,
        1 #iteration
    );
    if (!is_password_file_ok($SSH_CHEAT)) {
        print_both("UNABLE TO FIX CLUSTER");
        print_final_status();
        exit 1;
    }
    foreach (split "\n", $sysstat_v) {
        if ( $_ =~ /OFFLINE/ ) {
            if ( $_ =~ /NODE-(\d{3})/) {
                print_both(" - Trying to turn on NODE $1");
                run_system_command(
                  "$SSH_CHEAT /usr/sfw/bin/ipmitool -I lan  -f "
                . "$IPMI_PASS_LOCATION -H hcb$1-sp -U Admin chassis power on");
            } 
            if ( $_ =~ /DISK-(\d{3}):/) {
                my $node = $1;
                foreach (0..($MAX_NUMBER_DISKS - 1)) {
                    print_both(" - Trying to turn on DISK-$node:$_");
                    run_system_command(
                      "$SSH_ADMIN hwcfg -c $CURRENT_CELL -F -E DISK-$node:$_");
                }
            }
        }
    }
    sleep $SLEEP_REBOOT;

    return is_cluster_ok();
}

sub save_log_position {
    my $position = run_system_command("$SSH_CHEAT wc -l $MESSAGES");
    if ($position =~ /^\s*(\d*) $MESSAGES/) {
        $PREV_LOG_LINE_NUM = $SAVED_LOG_LINE_NUM;
        $SAVED_LOG_LINE_NUM = $1;
        print_both(" - Saving log line number: $1");
        return 0;
    }
}


sub tried_to_send {
    my $grep_string = $_[0];
    print_log(" - Grep'ing $MESSAGES for: $grep_string");
    my $grep = run_system_command(
        "$SSH_CHEAT grep -n -i alertmail $MESSAGES | grep \"$grep_string\""
    );
    print_log(" - Most recent pertinent log message:");
    my $line = run_system_command(
        "echo \"$grep\" | tail -1");
    if ($line =~ /^(\d*)\:/) {
        if ($1 > $SAVED_LOG_LINE_NUM) {
            print_both(" - WARNING: Email not received but generated. Log line $1: Tried to send: $grep_string");
            $WARNING_FLAG = 1;
            $IS_WARNING = 1;
            return 1; # OK
        }  
    } 
    my $position = run_system_command("$SSH_CHEAT wc -l $MESSAGES");
    if ($position =~ /^\s*(\d*) $MESSAGES/) {
        $position = $1;
    }
    if ($position < $SAVED_LOG_LINE_NUM) {
        # The logs have rotated..
        print_log(" - The logs have rotated during the test...");
        if ($line =~ /^(\d*)\:/) {
            if ($1 < $position) {
                print_both(" - Log line $1: Tried to send: $grep_string");
                $WARNING_FLAG = 1;
                $IS_WARNING = 1;
                return 1; # OK
            }  
        } else {
            # It doesnt exist in this log file
            print_log(" - Grep'ing $MESSAGES_0 for messages that attempted to send");
            my $grep = run_system_command(
                "$SSH_CHEAT grep -n -i alertmail $MESSAGES_0 | grep \"$grep_string\""
             );
             print_log(" - Most recent pertinent log message:");
             my $line = run_system_command(
                 "echo \"$grep\" | tail -1");
            if ($line =~ /^(\d*)\:/) {
                if ($1 > $SAVED_LOG_LINE_NUM) {
                    print_both(" - Log line $1: Tried to send: $grep_string");
                    $WARNING_FLAG = 1;
                    $IS_WARNING = 1;
                    return 1; # OK
                }
            }
        }
    }
    return 0; # Problem
}

sub get_cluster_status {
    foreach (@CELL_IDS) {
        my $sysstat = run_system_command("$SSH_ADMIN sysstat -c $_");
        if ( $sysstat =~ /(\d+) nodes online/ ) {
            $NODES = $1;
            print_both(" - Cell $_ - Nodes: $NODES");
        }
        if ( $sysstat =~ /(\d+) disks online/ ) {
            $DISKS = $1;
            print_both(" - Cell $_ - Disks: $DISKS");
        }
        if ( $sysstat =~ /Query Engine Status: (\w+)/ ) {
            $QUERY = $1;
            print_both(" - Cell $_ - Query Engine Status: $QUERY");
        }
        my $master = run_system_command("$SSH_MASTER hostname");
        if ($master =~ /hcb(\d*)/) {
            $MASTER_NODE = $1;
            print_both (" - Master Node: $MASTER_NODE");
        }
    }
    return 0;
}
###############################################################################
# Email Subroutines

sub check_email {

    # Check Inbox
    check_inbox();

    # Parse Email
    parse_email(@_);

    # Erase Email
    erase_email();
    
    return 0;
}

sub check_inbox {
    print_log(
        "------------------- Email Pulled Off Of Server ------------------\n");
    $EMAIL = run_system_command("$SSH_EMAIL $MAIL_CHECK");
    if ($?) {
        print_both("ERR: Mailbox could not be read.");
        print_final_status();
        exit 1;
    }
    print_log(
        "-----------------------------------------------------------------\n");
    return 0;
}

sub parse_email {
    my $command = $_[0];
    $ALERTS{$command} = ();

    my @split_emails = split( m/From st5800-noreply\@sun.com/, $EMAIL );
    while ( $#split_emails > 0 ) {
        my $email = pop @split_emails;
        my ( $subject, $message, $date );
        if ( $email =~ /Subject: (.*)\n/ )       { $subject = $1 }
        if ( $email =~ /Date: (.*)\n/ )          { $date    = $1 }
        if ( $email =~ /\n\n((.*|\s*|\n)*)\z/i ) { $message = $1 }
        my $read = 0;
        push( @{ $ALERTS{$command} }, [ $date, $subject, $message, $read ] );
    }
}

sub erase_email {
    my $erase = run_system_command("$SSH_EMAIL $MAIL_ERASE");
    my $touch = run_system_command("$SSH_EMAIL $MAIL_TOUCH");
    if ($erase) {
        print_both("ERR: Mailbox could not be erased.");
        print_final_status();
        exit 1;
    }
    if ($touch) {
        print_both("ERR: Mailbox could not be re-created.");
        print_final_status();
        exit 1;
    }
    return 0;
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
    print_both( "=" x 100 );
    print_both("");
    print_both("@_");
    print_both("");
    print_both( "=" x 100 );
    print_both("");
}

sub print_start_msg {
    my $date = run_system_command("date");
    $/ = "\n"; chomp $date;
    my $msg =
        "$date\n"
      . "STARTING ALERT TESTING UTILITY\n"
      . "ADMIN VIP: $ADMIN_VIP\n"
      . "CHEAT VIP: $CHEAT_VIP\n"
      . "MAILBOX  : $MAIL_BOX";
    if ( $ONLY_TEST != -1 ) {
        my $title = $ALERT_TRIGGER_TABLE[$ONLY_TEST][0];
        $msg = $msg . "\n" . "ONLY TESTING: $title";
    }
    if ( $START_FROM != -1 ) {
        my $title = $ALERT_TRIGGER_TABLE[$START_FROM][0];
        $msg = $msg . "\n" . "STARTING FROM: $title";
    }
    if ($NO_UPGRADE) {
        $msg = $msg . "\n" . "QUICK OPTION DETECTED (No Upgrade)";
    }
    if ($REPEAT != 0) {
        if ($REPEAT == -1) {
            $msg = $msg . "\n" . "REPEAT: Infinite Loop";
        } else {
            $msg = $msg . "\n" . "REPEAT: $REPEAT";
        }
    }
    print_header($msg);
}

sub print_line {
    print_both("");
    print_both( "." x 100 );
    print_both("");
}

sub print_bogus_mail {
    my ( $date, $subject, $message ) = @_;
    print_log( "\n"
          . "BOGUS EMAIL DETECTED:\n"
          . " - Date: $date\n"
          . " - Subject: $subject\n"
          . " - Message:\n"
          . "$message" );
    print " - BOGUS EMAIL DETECTED: $subject\n";
}

sub print_status {
    my ( $msg, $status ) = @_;

    if ($WARNING_FLAG) {
        $msg = "WRN " . $msg;
        $WARNING_FLAG = 0; 
    }

    print_both("");
    my $dots = undef;
    if ( $status eq "ok" ) {
        $dots = 100 - ( length($msg) + 6 );
        print_both( $msg . "." x $dots . "[PASS]" );
        $TEST_PASSED++;
        $TEST_QUANTITY++;
        push( @PASS_FAIL_ARRAY, [ $msg, "[PASS]" ] );
    }
    elsif ( $status eq "not ok" ) {
        $dots = 100 - ( length($msg) + 6 );
        print_both( $msg . "." x $dots . "[FAIL]" );
        $TEST_FAILED++;
        $TEST_QUANTITY++;
        push( @PASS_FAIL_ARRAY, [ $msg, "[FAIL]" ] );
    }
    else {
        $dots = 100 - ( length($msg) + 8 );
        print_both( $msg . ". " x ( $dots / 2 ) . "[MANUAL]" );
        $TEST_MANUAL++;
    }
    print_both("");
}

sub print_final_status {
    my $percent_pass =
      int( $TEST_QUANTITY == 0 ) ? 0 : ( $TEST_PASSED / $TEST_QUANTITY );
    $percent_pass = $percent_pass;

    print_both("\n");
    print_both("TEST RESULTS");
    print_both("--------------------------------------");
    foreach (@PASS_FAIL_ARRAY) {
        my ( $test, $pass ) = @{$_};
        print_both("$pass - $test");
    }

    format STDOUT =
    
    
    -------------------------------
    |Bogus Emails        | @<<<<<<|
                           $TEST_MANUAL
    |Tests Passed        | @<<<<<<|
                           $TEST_PASSED
    |Tests Failed        | @<<<<<<|
                           $TEST_FAILED
    |Total Tested        | @<<<<<<|
                           $TEST_QUANTITY
    -------------------------------
     PERCENTAGE PASSED   | @.##   |
                           $percent_pass
                         ----------
                        
                        
                        
.
    write;
    print_log("PASSED: $TEST_PASSED");
    print_log("FAILED: $TEST_FAILED");
    print_log("TOTAL:  $TEST_QUANTITY");
    print_log("------------------------");
    print_log("PERCENTAGE PASSED: $percent_pass");
    print_log my $date = run_system_command("date");
    $/ = "\n"; chomp $date;

    my $elapsed = time() - $TIME_STARTED;
    printf "    Time Elapsed: %d hours, %d minutes, %d seconds",
      ( gmtime $elapsed )[ 2, 1, 0 ];
    printf LOG "TIME ELAPSED: %d hours, %d minutes, %d seconds",
      ( gmtime $elapsed )[ 2, 1, 0 ];
    my $msg = "$date\n" . "FINISHED ALERT TESTING UTILITY";
    print_header($msg);
}

# Usage prints if input is wrong
sub usage {
    print "\n";
    print "NAME\n";
    print "\n";
    print "    run_alert_tests.pl\n";
    print "\n";
    print "SYNOPSIS\n";
    print "\n";
    print "    Automated email alert testing tool\n";
    print "\n";
    print "DESCRIPTION\n";
    print "\n";
    print "    run_alert_tests.pl is a script that is designed to test the "
        . "email alert system\n";
    print "    It runs a series of commands that will trigger email alerts, "
        . "then tests that the\n";
    print "    system sent out the alert.  The clusters alert-email will be "
        . "changed to a temp\n";
    print "    email box, then back to the original value at the end of the "
        . "script, along with\n";
    print "    any other settings changed by the script. The script takes "
        . "three mandatory argument:\n";
    print "\n";
    print "     1) the admin vip to run the script on,\n";
    print "     2) the cheat vip to run the script on,\n";
    print "     3) the corresponding cluster/mailbox name,\n";
    print "\n";
    print "OPTIONS\n";
    print "\n";
    print " -h: help => usage\n";
    print " -q: quick => Don't run upgrade\n";
    print " -l: list => Gets list of currently run tests and test numbers\n";
    print " -o TEST_NUMBER: only => Only run the test that corresponds to the test number\n";
    print " -s TEST_NUMBER: start => Run the tests, starting from test number\n";
    print " -r ITERATIONS: repeat => Run the test ITERATIONS times (Most useful with -o) '-1' for infinite.\n";
    print " *Note: options -s and -o cannot be used together\n";
    print " *Note: when using -o or -s, make sure alertcfg is configured to send emails to <MAILBOX>.\n";
    print "\n";
    print "USAGE\n";
    print "\n";
    print "    ./run_alert_tests.pl [-hlq] -a <ADMIN_VIP> -c <CHEAT_VIP> -m <MAILBOX> [-o TEST_NUMBER] [-s TEST_NUMBER] [-r ITERATIONS]";
    print "\n";
    print "EXAMPLE\n";
    print "\n";
    print "    ./run_alert_tests.pl -a 10.7.226.61 -c 10.7.226.60 -m dev325\n";
    print "\n";
    exit 0;
}

sub list_tests {
    print "\n";
    print "Current Tests\n";
    print "\n";
    print " NO.   Title\n";
    print "-------------------------------------\n";

    my $i = 0;
    foreach (@ALERT_TRIGGER_TABLE) {
        my ( $title, $command, $pre_alert, $post_alert, $cleanup ) = @{$_};
        print " $i     $title\n";
        $i++;
    }
    print "--------------------------------------\n";
    print "\n";
    print " If only a single test is desired, enter -o <NUMBER FROM ABOVE>\n";
    print "\n";
    exit 0;
}

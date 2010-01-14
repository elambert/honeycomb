#!/usr/bin/perl -w
#
# $Id: shutdowninloop.sh 11512 2007-09-14 20:53:51Z jk142663 $
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
# shutdowninloop.sh script is designed to run shutdown in a loop and
# will generate test log file under /tmp directory.
#
# This script uses to shut down all nodes not service processor 
# otherwise automated power-on will not be possible using ipmitool.
#
# usage: ./shutdowninloop.sh -a ADMIN_VIP -i num_iteration
# ADMIN_VIP: it is admin ip of master cell (required argument).
# num_iteration: it is the number of times you want to run shutdown test (optional argument).
#                default is set to 10.
#
# examples: 
# ./shutdowninloop.sh -a dev313-admin -i 5
# ./shutdowninloop.sh -a dev313-admin
#

use strict;
use Getopt::Std;

####
# commands
####
my $SSH = "ssh";
my $SSH_ARGS = " -q -o StrictHostKeyChecking=no ";
my $SCP = "scp";
my $SHUTDOWN = "shutdown -F -c";
my $HADB = "hadb -F status -c";
my $HIVEADM = "hiveadm -s";
my $CELLCFG = "cellcfg -c";
my $HWSTAT = "hwstat -c";
my $PING = "ping";

my $LOG_FILE = "/tmp/shutdown_$$.out";
my $SLEEP_TIME = 1800;
my $SLEEP_INTERVAL = 300;
my $HADB_UP_STATE = "HAFaultTolerant";

my $SSH_ADMIN = undef;
my $ADMIN_VIP = undef;
my $ITERATION = undef;

my $IS_MULTICELL = 0;
my @cellids = ();
my %hive = ();
my $TOTAL_NO_OF_NODES = 0;

my $ADMIN_IP_KEY = "Admin";
my $DATA_IP_KEY = "Data";
my $SP_IP_KEY = "Service";
my $HADB_COUNT_KEY = "Count";

my %args = ();
my $arg_string = 'ha:i:';
getopts("$arg_string", \%args);

$ADMIN_VIP = $args{'a'};
$ITERATION = $args{'i'};
$ITERATION = 10 if !(defined $ITERATION);

&usage() if !(defined $ADMIN_VIP);

$SSH_ADMIN = $SSH.$SSH_ARGS." admin\@".$ADMIN_VIP;

if ($ITERATION !~ /^\d+$/ ) {
  print "\nERR: $ITERATION is not valid iteration number, should be a positive integer.\n";
  exit -1;
}

open(LOG, ">$LOG_FILE")
  or die "ERR: Cannot Open $LOG_FILE due to error $!";

&print_start_message();

# verify admin vip is pingable
if (!(&isPing($ADMIN_VIP))) {
 print LOG "ERR: Admin VIP $ADMIN_VIP is not pingable\n";
 &print_err_message();
}

# get all cellids & ips
&assign_hive_info();

# verify all nodes/disks are up
&verify_hwstat();

# verify hadb state
&verify_hadb();

for(my $i=0; $i<$ITERATION; $i++) {
  print LOG "INFO: ***************************\n";
  print LOG "INFO: Iteration# $i\n";

  &set_hadb_count();

  my @reverse_cellids = reverse @cellids;
  foreach my $cellid (@reverse_cellids) {
    print LOG "INFO: Start shutting down cell $cellid\n";

    my $shutdown_output = `$SSH_ADMIN $SHUTDOWN $cellid`; 
    chomp $shutdown_output;

    print LOG "INFO: shutdown output ***\n";
    print LOG "$shutdown_output\n";    
    print LOG "INFO: *** end shutdown output\n";
  }

  # sleep 120 seconds
  sleep(120);

  # verify data ip is not pingable, but admin & sp ips are pingable
  &verify_ips(0);
 
  # verify all nodes are down
  &verify_nodes();

  # use ipmi to power up all nodes
  &ipmi_power_up();

  # wait until hadb comes up
  print LOG "INFO: BEGIN sleep $SLEEP_TIME\n"; 
  sleep($SLEEP_TIME);
  print LOG "INFO: END sleep $SLEEP_TIME\n";

  # verify all ips are pingable
  &verify_ips(1);

  # verify all nodes/disks are up
  &verify_hwstat();

  # verify hadb state
  &verify_hadb();

  # verify no of hadb entries
  &verify_hadb_count();

}

print LOG "INFO: Test Passed!\n";
close LOG;
exit 0;

####
# all methods
####

sub usage() {
  print "Usage:\n";
  print "NAME\n";
  print "    $0 - shutdown in a loop\n";
  print "\n";
  print "SYNOPSIS\n";
  print "    $0 [-h] -a <ADMIN_VIP> -i [num_iterations]\n\n";
  print "DESCRIPTION\n";
  print "    Run shutdown in a loop\n";
  print "    num_iterations defaults to 10 if not specified.\n";
  print "\n";
  print "EXAMPLE\n";
  print "    $0 -a dev313-admin -i 5";
  print "\n";

  exit 0;
}

sub print_start_message() {
  $_ = `date`; chomp;

  print LOG "INFO: $_\n".
            "INFO: Starting Shutdown test in Loop\n".
            "INFO: ADMIN VIP: $ADMIN_VIP\n".
            "INFO: Total No of Iteration: $ITERATION\n";
}

sub print_err_message() {
  print LOG "INFO: Test Failed!\n";
  close LOG;

  exit -1;
}

sub isPing() {
  $_ = $_[0];
  system("$PING -c 1 $_ 2>&1 >> /dev/null");
  if ($? ) {
      return 0;
  }
  else {
    print LOG "INFO: $_ is pingable\n";
    return 1;
  }
}

sub verify_nodes() {
  foreach my $cellid (@cellids) {
    print LOG "INFO: verify all nodes of cell $cellid are down\n";
    my $sp_ip = $hive{$cellid}{$SP_IP_KEY};

    for (my $i=101; $i<=(100+$TOTAL_NO_OF_NODES); $i++) {
      my $node = "hcb".$i;
      system("$SSH $SSH_ARGS root\@$sp_ip $PING -c 1 $node 2>&1 >> /dev/null");
      if ($?) {
        print LOG "INFO: Node $node shuts down properly\n";
      }
      else {
        print LOG "ERR: Node $node is alive!\n";
        &print_err_message();     
      }
    }
  }
}

sub assign_hive_info() {
  # get all cellids
  $_ = `$SSH_ADMIN $HIVEADM`; chomp;
  foreach (split(/\n/, $_)) {
    chomp;
    if (/Cell (\d+):/) {
      push @cellids, $1;
    }
  }

  if ( $#cellids > 0 ) {
    $IS_MULTICELL = 1;
    $TOTAL_NO_OF_NODES = 16; 
  }
  else {
    $_ = `$SSH_ADMIN $HWSTAT $cellids[0]`;
    foreach (split(/\n/, $_)) {
      if (/NODE/) {
        $TOTAL_NO_OF_NODES ++;
      }
    }
  }

  # get all ips
  foreach my $cellid (@cellids) {
    $_ = `$SSH_ADMIN $CELLCFG $cellid`; chomp;
    foreach (split(/\n/, $_)) {
      chomp;
      if (/$ADMIN_IP_KEY/) {
        $hive{$cellid}{$ADMIN_IP_KEY} = (split(/= /, $_))[1];
      }
      elsif (/$DATA_IP_KEY/) {
        $hive{$cellid}{$DATA_IP_KEY} = (split(/= /, $_))[1];
      }
      elsif (/$SP_IP_KEY/) {
        $hive{$cellid}{$SP_IP_KEY} = (split(/= /, $_))[1];
      }
    }
  }
}

sub verify_hwstat() {
  foreach my $cellid (@cellids) {
    print LOG "INFO: start verifying hwstat of cell $cellid\n";
    $_ = `$SSH_ADMIN $HWSTAT $cellid`;
     foreach (split(/\n/, $_)) {
       if ((/OFFLINE/) || (/DISABLEd/)) {
         print LOG "ERR: Unexpected state\n";
         print LOG "INFO: $_";
         &print_err_message();
       }
     }
     print LOG "INFO: All nodes/disks are online\n";
   }
}

sub verify_hadb() {
  foreach my $cellid (@cellids) {
    my $HAFaultTolerant = 0;
    my $current_hadb_state = undef;

    print LOG "INFO: start verifying hadb state of cell $cellid\n";
    my $WAIT_TIME = $SLEEP_INTERVAL;

    do {
      # wait until hadb comes up
      print LOG "INFO: BEGIN sleep $SLEEP_INTERVAL\n"; 
      sleep($SLEEP_INTERVAL);
      $WAIT_TIME += $SLEEP_INTERVAL;

      $_ = `$SSH_ADMIN $HADB $cellid`; chomp;

      my @haf = split/\n/, $_; 
      foreach (@haf) {
        chomp;
        $current_hadb_state = $_;
        if (/$HADB_UP_STATE/) {
            $HAFaultTolerant = 1;
        }
      } 
      
      print LOG "INFO: HADB state: $current_hadb_state\n";

    } while (($WAIT_TIME <= $SLEEP_TIME) && ($HAFaultTolerant != 1));

    if (!($HAFaultTolerant)) {
      print LOG "ERR: Unexpected HADB state - $current_hadb_state\n";
      &print_err_message();  
    }
  }
}

sub verify_hadb_count() {
  foreach my $cellid (@cellids) {
    my $admin_ip = $hive{$cellid}{$ADMIN_IP_KEY};
    my $before_count = $hive{$cellid}{$HADB_COUNT_KEY};
    my $after_count = &get_hadb_count($admin_ip);

    if ($after_count < $before_count) {
      print LOG "ERR: HADB wiped!\n";
      print LOG "INFO: HADB count before shutdown = $before_count\n";
      print LOG "INFO: HADB count after shutdown = $after_count\n";
      &print_err_message();
    }
  }
}

sub set_hadb_count() {
  foreach my $cellid (@cellids) {
    my $admin_ip = $hive{$cellid}{$ADMIN_IP_KEY}; 
    my $count = &get_hadb_count($admin_ip);
    
    $hive{$cellid}{$HADB_COUNT_KEY} = $count;
    print LOG "INFO: Total no of entries in hadb before shutdown = $count\n";
  }
}

sub get_hadb_count() {
  my $admin_ip = $_[0];

  `echo "select count(*) from t_system;" > hadb_fullness.in`;

  `$SCP -P 2001 $SSH_ARGS hadb_fullness.in root\@$admin_ip:hadb_fullness`;

   $_ = `$SSH root\@$admin_ip -p 2001 $SSH_ARGS /opt/SUNWhadb/4/bin/clusql -nointeractive localhost:15005 system+superduper -command=hadb_fullness | tail -2 | tr -d "[:space:]"`;
  chomp;

  return $_;
}

sub verify_ips() {
  my $is_all_up = $_[0];

  foreach my $cellid (@cellids) {
    my $admin_ip = $hive{$cellid}{$ADMIN_IP_KEY}; 
    my $data_ip = $hive{$cellid}{$DATA_IP_KEY}; 
    my $sp_ip = $hive{$cellid}{$SP_IP_KEY}; 
    
    if (!(&isPing($admin_ip))) {
      print LOG "ERR: Admin ip $admin_ip of cell $cellid is not pingable!\n";
      &print_err_message();
    }

    if (!(&isPing($sp_ip))) {
      print LOG "ERR: SP ip $sp_ip of cell $cellid is not pingable!\n";
      &print_err_message();
    }

    if ($is_all_up) {
      if (!(&isPing($data_ip))) {
        print LOG "ERR: Data ip $data_ip of cell $cellid is not pingable!\n";
        &print_err_message();
      }
    }
    else {
      if (&isPing($data_ip)) {
	print LOG "ERR: cell $cellid does not not shutdown properly!\n";
        print LOG "ERR: Date ip $data_ip of cell $cellid is pingable!\n";
        &print_err_message();
      }
      else {
        print LOG "INFO: Data ip $data_ip of cell $cellid is not pingable!\n";
      }
    }
  }
}

sub ipmi_power_up() {
  `echo "honeycomb" > pass`; 
  foreach my $cellid (@cellids) {
  print LOG "INFO: powering up all nodes of cell $cellid using ipmi tool\n";
    my $sp_ip = $hive{$cellid}{$SP_IP_KEY};
    `$SCP $SSH_ARGS pass root\@$sp_ip:/export/`;
 
    for (my $i=101; $i<=(100+$TOTAL_NO_OF_NODES); $i++) {
      my $node = "hcb".$i;
      my $ipmi_output = `$SSH $SSH_ARGS root\@$sp_ip /usr/sfw/bin/ipmitool -I lan -H $node-sp -U Admin -f /export/pass chassis power on`;
      chomp $ipmi_output;
      print LOG "INFO: IPMI output: $node: $ipmi_output\n";
    }
  }
}


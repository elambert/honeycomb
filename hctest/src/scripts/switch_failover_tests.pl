#!/usr/bin/perl
#
# $Id: switch_failover_tests.pl 10858 2007-05-19 03:03:41Z bberndt $
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

use strict;

### Global variables ### 
my $cheatnode = 'dev308-cheat'; 
my $sshcheat = "ssh -q -o StrictHostKeyChecking=no $cheatnode";
my $cluster = "dev308";
my $switch_ip = "10.123.45.1";
my $sshswitch = "ssh -q -o StrictHostKeyChecking=no -p 2222 -l nopasswd $switch_ip";  

my $thttpd_pid_file = "/var/run/thttpd.pid";

my $scriptpath = "/tmp";
my $logpath = "/root";
my $old_id = undef; 
my $new_id = undef;
my %test_tbl = ();
my $start_time = undef;
my $end_time = undef;
my $old_thttpd_pid = undef;
my $result = "skipped";

#### primary_failover Test Cases ####
$test_tbl{'primary_failover'} = { 
               'failover w/o clients' => {
                                            failover => "primary",
                                            no_of_iter => 1,
                                            verify => [ \&verify_switch_status, 
                                                        \&verify_node_interfaces,
                                                        \&verify_switch_rules,
                                                        \&verify_nodemgr_mailbox 
                                                      ],
                                            run_mode => "sequential",
                                            exclude => "yes",
                                            },
               'failover wth store and retrieve' => {
                                            failover => "primary",
                                            no_of_iter => 1,
                                            verify => [ \&verify_switch_status,
                                                        \&verify_node_interfaces,
                                                        \&verify_switch_rules,
                                                        \&verify_nodemgr_mailbox 
                                                       ], 
                                            parallel_procs => [ \&store_and_retrieve ],
                                            run_mode => "parallel",
                                            exclude => "yes" ,
                                            },
               'kill_thttpd w/o clients' => {
                                            failover => "kill_thttpd",
                                            no_of_iter => 1,
                                            verify => [ \&verify_thttpd_restart, 
                                                      ],
                                            run_mode => "sequential",
                                            },
               'kill_thttpd with store and retrieve' => {
                                            failover => "kill_thttpd",
                                            no_of_iter => 1,
                                            verify => [ \&verify_thttpd_restart, 
                                                      ],
                                            run_mode => "sequential",
                                            exclude => "yes" ,
                                            },
               };


#### secondary_failover Test Cases ####
$test_tbl{'secondary_failover'} = (
       'failover with store and retrieve' => {
               failover => "secondary",
               no_of_iter => 1,
               verify => [ \&verify_switch_status, 
                           \&verify_node_interfaces,
                           \&verify_switch_rules,
                           \&verify_nodemgr_mailbox
                         ], 
               run_mode => "parallel",
               exclude => "yes",
               } 
       );

#### primary_and_secondary_failover Test Cases ####
$test_tbl{'primary_and_secondary_failover'} = (
       'failover with store and retrieve' => {
               failover => "primary_and_secondary",
               no_of_iter => 1,
               verify => [ \&verify_switch_status,
                           \&verify_node_interfaces,
                           \&verify_switch_rules,
                           \&verify_nodemgr_mailbox
                         ], 
               run_mode => "parallel",
               exclude => "yes",
               } 
       );

sub verify_thttpd_restart {
   print "*** Entering verify_thttpd_Restart ***\n"; 
   # Service that starts httpd is root and its ppid should be 1   
   my $ppid = `$sshcheat $sshswitch ps -ef | grep httpd | awk {'print \$3'} | head -n 1`; 
   chomp ($ppid);
  
   my $new_thttpd_pid = `$sshcheat $sshswitch cat $thttpd_pid_file`;
   print "old thttpd pid :$old_thttpd_pid new thttpd pid :$new_thttpd_pid\n"; 

   ($ppid == 1) && ($old_thttpd_pid != $new_thttpd_pid) ? return 0 : return 1; 
} # verify_thttpd_restart # 

sub verify_switch_status {
   print "*** Entering verify_switch_status ***\n"; 
   my $no_of_iter = shift @_;
  
   $new_id = get_switch_id ();
   chomp ($new_id);
   print "old id $old_id, new id $new_id, iters $no_of_iter \n"; 
   
   my $up = `$sshcheat \"cd /utils; \./do-servers.sh hostname\"`; 
   return ($@) if ($@ != 0); 
   if ($no_of_iter % 2) { # no. is odd 
      ($up == 0 && ($old_id != $new_id)) ? return 0 : return 1;
   } 
   else { # no. is even
      ($up == 0 && ($old_id == $new_id)) ? return 0 : return 1;
   } 
} # verify_switch_status #

sub get_switch_id {
   my $id = `$sshcheat $sshswitch grep -i switch_id /etc/honeycomb/switch.conf | awk -F= {'print \$2'}`;
   #my $out = `$sshcheat $sshswitch cat /etc/honeycomb/switch.conf`; 
   exit $@ if ($@ != 0); 
   return ($id); 
} # get_switch_id #


sub verify_test {
   my ($verify_procs, $no_of_iter) = @_; 
   my $rc;
 
   foreach my $proc (@$verify_procs) { 
      $rc = &$proc($no_of_iter);
      if ($rc == 0) { 
         print "verification routine .... PASSED\n";
      } 
      else { 
         print "verification routine .... FAILED\n"; 
         last; 
      } 
   }  

   return $rc;
} # verify_test # 

sub verify_connectivity { 
    print "verifying n/w connectivity from client=>$cheatnode, client=>$cheatnode=>$switch_ip\n";
  
   `$sshcheat hostname`; 
   return $@ if ($@ != 0);
   
   `$sshcheat ssh -q -p 2222 -o StrictHostKeyChecking=no $switch_ip hostname`;
   return $@;
} # verify_connectivity #

 
sub run_regressiontest {
   `/opt/test/bin/runtest -ctx cluster=$cluster:explore:include=regression 2>&1 1>>/dev/null`;
   $@ == 0 ? return 0 : return $@; 
} # run_regressiontest #


sub runtest {
   my ($testcase, $failover_opt, $no_of_iter) = @_;
   my $s = 0; 
   $start_time = `date \'+%Y-%m-%d %H:%M:%S\'`;
   chomp ($start_time);
   for (my $loop=0; $loop < $no_of_iter; $loop++) { 
      # before doing the next iteration, give time to the switch to recover  
      sleep 60 if ($loop >= 1);
  
      if ($failover_opt =~ /primary/) { 
         print "doing primary failover\n"; 
         `$sshcheat $scriptpath/fail_switch.sh -failover p`; 
      }
      elsif ($failover_opt =~ /secondary/) {
         print "doing secondary failover\n"; 
         `$sshcheat $scriptpath/fail_switch.sh -failover s`; 
      } 
      elsif ($failover_opt =~ /primary_and_secondary/) {
         print "doing primary and secondary failover\n"; 
         `$sshcheat $scriptpath/fail_switch.sh -failover sp`; 
      } 
      elsif ($failover_opt =~ /kill_thttpd/) {
          $old_thttpd_pid = `$sshcheat $sshswitch cat $thttpd_pid_file`;
          chomp ($old_thttpd_pid);
          print "thttpd pid $old_thttpd_pid\n"; 
          last if ($@ != 0);
          #print "pid = $old_thttpd_pid\n";
          `$sshcheat $sshswitch kill -9 $old_thttpd_pid`;
          last if ($@ != 0); 
      }
      else { 
         print "Unknown switch id for testcase $testcase\n";
         $s = 1; 
         last; 
      } 
   }  
   $end_time = `date \'+%Y-%m-%d %H:%M:%S\'`; 
   chomp ($end_time);
   return $s; 
} # runtest #


sub to_filename {
   my $name = shift @_;
   $name =~ s/[: \/\\]/_/g;
   return $name;
} # to_filename #

sub log_stats {
   my ($testcase, $state) = @_; 
   my $name = $logpath . "/" . to_filename($testcase);  
   print "file name $name\n"; 
   
   open (LOG, ">$name") or die "Unable to open $name: $!";
   print LOG "=" x 20; 
   print LOG "\n\n";
   print LOG "\n$state test\n";
   print LOG "=" x 20; 
   print LOG "\n\n";
   printf LOG "\nPrimary Switch id: %s\n", get_switch_id();  
   print LOG "Primary \'vrrpconfig -a\'\n";
   foreach (`$sshcheat $sshswitch vrrpconfig -a`) {
      print LOG "$_\n";
   } 
   my $cmdline = "$sshcheat $sshswitch run_cmd_other.sh";
   $cmdline += "grep -i switch_id /etc/honeycomb/switch.conf";
   $cmdline += " | awk -F= {'print \$2'}";
   printf LOG "Sec. Switch id: %s\n", `$cmdline`;
   print LOG "Sec. \'vrrpconfig -a\'\n";
   foreach (`$sshcheat $sshswitch run_cmd_other.sh vrrpconfig -a`) {
      print LOG "$_\n";
   } 
   printf LOG "thttpd pid: %s\n", `$sshcheat $sshswitch cat $thttpd_pid_file`;  
   print LOG "\n\n";
   close (LOG); 
} # log_stats #


sub post_result {  
   my ($testproc, $testcase, $result) = @_; 
   my $name = $logpath . "/" . to_filename ($testcase) . ".result";  
   open (LOG, ">$name") or die "unable to open $name: $!\n";
   print LOG "QB.testproc    : $testproc\n";
   print LOG "QB.parameters  : $testcase\n";
   print LOG "QB.start_time  : $start_time\n";
   print LOG "QB.end_time    : $end_time\n";
   print LOG "QB.status      : $result\n";
   print LOG "QB.taglist     : switch-failover\n";
   print LOG "qb.Submitter   : sm193776\n";  
   close (LOG); 
   `/opt/test/cur/bin/qb_cli.sh result $name`; 
   #unlink ($name) or die "unable to delete $name\n";
} # post_result # 


sub store_and_retrive {
   `dd if=/dev/zero of=testfile bs=1M count=1`; 
   my $oid = `/opt/test/cur/bin/store $cluster-data testfile`;
   chomp ($oid);
   `/opt/test/cur/bin/retrieve $cluster-data $oid`;
} # store_and_retrieve #

sub fork_and_runtest {
   my ($testcase, $testargs) = @_;
   my @child_pids = ();  
   defined (my $child_pid = fork ()) or die "Unable to fork:$!\n";
   if ($child_pid) { # parent loop 
      push (@child_pids, $child_pid);
      sleep 30; 
      my $rc = runtest ($testcase, $testargs->{'failover'}, $testargs->{'no_of_iter'});
      print "FAILED to run testcase $testcase\n" if($rc != 0);
   
      sleep 60;

      $rc = verify_test ($testargs->{'verify'}, $testargs->{'no_of_iter'});  
      $rc == 0 ? print "testcase $testcase ... PASSED\n":
		 print "testcase $testcase ... FAILED\n";

      print "Killing all child pids i.e. @child_pids\n"; 
      my $deadpids = undef; 
      $deadpids = kill (9, @child_pids);
      `killall java`;
      `kill -9 \`ps -ef | grep syscfg | awk {'print \$2'}\``; 
      printf "killed $deadpids out of %d\n", scalar (@child_pids); 
   }  
   else { # child loop 
      my $procs = $testargs->{'parallel_procs'}; 
      foreach my $proc (@$procs) {  
	 my $rc = &$proc();
	 #print "FAILED to run $proc\n" if($rc != 0);
      }  
      exit 0; 
   }
} # fork_and_runtest #

sub iterate_testcases {
   my ($testproc, $tbl) = @_; 
   foreach my $testcase (keys %$tbl) {
      my $testargs = $tbl->{$testcase};
      next if (defined ($testargs->{'exclude'})); 
      my $rc = 1;
      do {
	 $rc = verify_connectivity; 
	 sleep 5; 
      } while ($rc != 0);
  
      print "n/w connectivity fine\n";
      
      log_stats ($testcase, "BEFORE");
    
      print "Running testcase $testcase ...\n";
      $old_id = get_switch_id (); 
      chomp ($old_id); 
      print "old id = $old_id\n";
      if ($testargs->{'run_mode'} =~ /parallel/) { 
         fork_and_runtest ($testcase, $testargs);
      } 
      else {  # sequential  
	 my $rc = runtest ($testcase, $testargs->{'failover'}, $testargs->{'no_of_iter'});
	 print "FAILED to run testcase $testcase\n" if($rc != 0);
	 
	 sleep 30;
      
	 $rc = verify_test ($testargs->{'verify'}, $testargs->{'no_of_iter'});  
	 $rc == 0 ? print "testcase $testcase ... PASSED\n":
		    print "testcase $testcase ... FAILED\n"; 
	 
	 $result = ($rc == 0) ? "pass" : "fail";
      }
      post_result ($testproc, $testcase, $result); 
      
      log_stats ($testcase, "AFTER"); 
      # wait between test cases 
      sleep 30;
   }
}

iterate_testcases ('primary_failover', $test_tbl{'primary_failover'});
#iterate_testcases ($test_tbl{'secondary_failover'});
#iterate_testcases ($test_tbl{'primary_and_secondary_failover'});
exit 0;


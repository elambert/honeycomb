#!/usr/bin/perl -w
#
# $Id: enter_cm_testcases.pl 10858 2007-05-19 03:03:41Z bberndt $
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

# This is a helper script to submit skipped results for CM testcases to QB DB.
# 
# If you want to add new testcases, copy NEW TESTCASES section, uncomment, modify values.
# If you want to add/modify testcases in existing sections, move the section up (right after NEW TESTCASES), modify values. 
# Keep exit(1) after the call to enter_testcases() so the script doesn't go on to re-enter skipped results for existing testcases.
#

use strict;

my $user = "dm155201"; # CHANGE ME!
my $build = "anza_1";  # CHANGE ME!
my $svn = "/export/home/${user}/trunk";
my $result = "skipped";
my $postResult = "${svn}/suitcase/src/script/postResult.sh";
my $postcmd = "$postResult -d $svn -u $user -b $build -r $result -n 'PLACEHOLDER RESULT'";

my ($testcases, $scenarios, $tags); # defined in each section

# Permute over all combinations of testcase, scenario, tag values.
# Enter skipped results into QB, using postResult script.
#
sub enter_testcases {
    my ($testcases, $scenarios, $tags) = @_;

    foreach my $testcase (@$testcases) {
        foreach my $scenario (@$scenarios) {
            foreach my $tagset (@$tags) {
                print "+++ TESTCASE=".$testcase." SCENARIO=".$scenario." TAGS=".$tagset."\n";
                # important: tags must be a space-separated list, quoted as single 2nd arg
                system("$postcmd -p $scenario $testcase \"$tagset\"");
            }
        }
    }
}

### NEW TESTCASES HERE

# Copy, uncomment, modify.

#$testcases = ["NewTest1", "NewTest2"];
#$scenarios = ["NewScenario1", "NewScenario2"];
#$tags = ["tag1 tag2", "tag2 tag3"];

#enter_testcases($testcases, $scenarios, $tags);

# Do not go on to re-enter skipped results for already existing testcases.

exit(1);

### CM UNIT_TESTS

$testcases = ["ServiceMailbox", "CMMCallbacks"];
$scenarios = ["DefaultScenario"];
$tags = ["svc-mgmt node-mgr"];

enter_testcases($testcases, $scenarios, $tags);

### CONFIG, VERSIONING

$testcases = ["ConfigUpdate"];
$scenarios = ["PositiveTest",
              "StickyAfterRestart",
              "ReliableOnDiskFailure",
              "WithSomeNodesDown",
              "NegativeTest,NoQuorum",
              "StressTest",
              "VersionReconciliation"];
$tags = ["config full-hc"];

enter_testcases($testcases, $scenarios, $tags);

$testcases = ["SoftwareVersioning"];
$scenarios = ["Mismatch,OlderVersion",
              "Mismatch,NewerVersion",
              "Mismatch,NoQuorum"];
$tags = ["config full-hc"];

enter_testcases($testcases, $scenarios, $tags);

$testcases = ["TimeSync"];
$scenarios = ["DefaultScenario"];
$tags = ["config full-hc"];

enter_testcases($testcases, $scenarios, $tags);

### SERVICE RESTART

$testcases = ["ServiceRestart"];

$scenarios = ["KillServiceJVM,Once",
              "KillServiceJVM,Repeatedly,Escalation",
              "ServiceUncaughtException,Once",
              "ServiceUncaughtException,Repeatedly,Escalation",
              "ServiceUncaughtException,SlowExit,Once",
              "ServiceUncaughtException,SlowExit,Repeatedly,Escalation",
              "ServiceExitsRunloop",
              "LevelZeroServiceException,Escalation"];

$tags = ["svc-mgmt node-mgr", "svc-mgmt full-hc"];

enter_testcases($testcases, $scenarios, $tags);

### QUORUM

$testcases = ["Quorum::QuorumGain",
              "Quorum::QuorumLoss",
              "Quorum::QuorumRegain",
              "Quorum::DynamicQuorumLoss",
              "Quorum::DynamicQuorumRegain"];

$scenarios = ["8-Nodes,DiskBased",
              "8-Nodes,NodeBased",
              "16-Nodes,DiskBased",
              "16-Nodes,NodeBased"];

$tags = ["svc-mgmt full-hc"];

enter_testcases($testcases, $scenarios, $tags);

# same testcases with TestSvc in NodeMgr setup, special scenarios
# number of nodes in the cluster is NOT a factor (ideally test on 16)

$scenarios = ["FakeDiskBased,Once",
              "FakeDiskBased,Repeatedly",
              "FakeDiskBased,Once,Fast",
              "FakeDiskBased,Repeatedly,Fast",
              "FakeDiskBased,SlowServiceWithinTimeout",
              "FakeDiskBased,SlowServiceOverTimeout",
              "FakeDiskBased,FailingService"];

$tags = ["svc-mgmt node-mgr"];
         
enter_testcases($testcases, $scenarios, $tags);

### SERVICES

# number of nodes in the cluster is NOT a factor (ideally test on 16)

$testcases = ["ClusterStartup::ServiceStartup",
              "ClusterShutdown::ServiceShutdown"];

$scenarios = ["NormalServices",
              "SlowServiceWithinTimeout",
              "SlowServiceOverTimeout",
              "FailingService"];

# omitting on purpose: "NoSuchService" (not too useful)

$tags = ["svc-mgmt node-mgr"];

enter_testcases($testcases, $scenarios, $tags);

# special testcase ala Fred, start HC services in reduced node_configs

$testcases = ["ClusterStartup::HoneycombRunlevels",
              "ClusterShutdown::HoneycombRunlevels"];
$scenarios = ["DefaultScenario"];
$tags = ["svc-mgmt full-hc"];

enter_testcases($testcases, $scenarios, $tags);

# INSERT HERE

### NODE BOUNCE

$testcases = ["NodeBounce::FailHC"];

$scenarios = ["8-Nodes,BounceOne,Once",
              "8-Nodes,BounceOne,Repeatedly",
              "8-Nodes,BounceTwo,Once",
              "8-Nodes,BounceTwo,Repeatedly",
              "8-Nodes,BounceTooMany,Once",
              "8-Nodes,BounceTooMany,Repeatedly",
              "16-Nodes,BounceOne,Once",
              "16-Nodes,BounceOne,Repeatedly",
              "16-Nodes,BounceTwo,Once",
              "16-Nodes,BounceTwo,Repeatedly",
              "16-Nodes,BounceFour,Once",
              "16-Nodes,BounceFour,Repeatedly",
              "16-Nodes,BounceTooMany,Once",
              "16-Nodes,BounceTooMany,Repeatedly"];

$tags = ["cmm cmm-only", "cmm full-hc"];

enter_testcases($testcases, $scenarios, $tags);

$testcases = ["NodeBounce::FailNetwork"];

$scenarios = ["8-Nodes,SwitchPortDown",

              "8-Nodes,DropHeartbeatPackets",
              "8-Nodes,DropDiscoveryPackets",
              "8-Nodes,DropElectionPackets",
              "8-Nodes,DropAnyCMMPackets",

              "8-Nodes,DelayHeartbeatPackets",
              "8-Nodes,DelayDiscoveryPackets",
              "8-Nodes,DelayElectionPackets",
              "8-Nodes,DelayAnyCMMPackets",

              "16-Nodes,SwitchPortDown",

              "16-Nodes,DropHeartbeatPackets",
              "16-Nodes,DropDiscoveryPackets",
              "16-Nodes,DropElectionPackets",
              "16-Nodes,DropAnyCMMPackets",

              "16-Nodes,DelayHeartbeatPackets",
              "16-Nodes,DelayDiscoveryPackets",
              "16-Nodes,DelayElectionPackets",
              "16-Nodes,DelayAnyCMMPackets"];

$tags = ["cmm cmm-only", "cmm full-hc"];

enter_testcases($testcases, $scenarios, $tags);

$testcases = ["SplitBrain"];

$scenarios = ["8-Nodes,NoQuorum",
              "8-Nodes,OneSubclusterQuorum",
              "16-Nodes,NoQuorum",
              "16-Nodes,OneSubclusterQuorum"];

$tags = ["cmm full-hc"];

enter_testcases($testcases, $scenarios, $tags);

$testcases = ["NodeBounce::HardReboot",
              "NodeBounce::HardPowerCycle"];

$scenarios = ["DefaultScenario"];

$tags = ["cmm cmm-only", "cmm full-hc"];

enter_testcases($testcases, $scenarios, $tags);

$testcases = ["NodeBounce::SoftReboot"]; # only makes sense for full-stack HC
$scenarios = ["DefaultScenario"];
$tags = ["cmm full-hc"];

enter_testcases($testcases, $scenarios, $tags);

### STARTUP

$testcases = ["ClusterStartup::ClusterStartup"];

$scenarios = ["8-Nodes,Staggered",
              "8-Nodes,Simultaneous",
              "16-Nodes,Staggered",
              "16-Nodes,Simultaneous"];

$tags = ["cmm cmm-only", "cmm full-hc"];

enter_testcases($testcases, $scenarios, $tags);

### FAILOVER

$testcases = ["MasterFailover::MasterFailover",
              "MasterFailover::ViceMasterFailover",
              "MasterFailover::MasterAndViceFailover"];

$scenarios = ["8-Nodes,FailoverOnce",
              "8-Nodes,FailoverTwice",
              "8-Nodes,FailoverRepeatedly",
              "16-Nodes,FailoverOnce",	
              "16-Nodes,FailoverTwice",	
              "16-Nodes,FailoverRepeatedly"];

$tags = ["cmm cmm-only", "cmm full-hc"];

enter_testcases($testcases, $scenarios, $tags);








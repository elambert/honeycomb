#!/usr/bin/perl -w
#
# $Id: enter_capacity_testcases.pl 10858 2007-05-19 03:03:41Z bberndt $
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

# This is a helper script to submit skipped results for Capacity testcases to QB DB.
# 
# If you want to add new testcases, copy NEW TESTCASES section, uncomment, modify values.
# If you want to add/modify testcases in existing sections, move the section up (right after NEW TESTCASES), modify values. 
# Keep exit(1) after the call to enter_testcases() so the script doesn't go on to re-enter skipped results for existing testcases.
#

use strict;

my $user = "jd151549"; # CHANGE ME!
my $build = "none";  # CHANGE ME!
my $svn = "/export/home/${user}/svn/honeycomb/trunk/";
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
                #print("$postcmd -p $scenario $testcase \"$tagset\"\n");
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

### Capacity

$testcases = ["Capacity::FillCluster",
              "Capacity::FillClusterDeleteThenStore",
              "Capacity::FillClusterHeal",
              "Capacity::FillClusterWipeThenStore"];

$scenarios = ["8-Nodes",
              "16-Nodes"];

$tags = ["capacity"];

enter_testcases($testcases, $scenarios, $tags);

### Sloshing

$testcases = ["Capacity::Expansion"];

$scenarios = ["SmallInitialDataset,NoLoad",
              "SmallInitialDataset,LowLoad",
              "SmallInitialDataset,HighLoad",
              "MediumInitialDataset,NoLoad",
              "MediumInitialDataset,LowLoad",
              "MediumInitialDataset,HighLoad",
              "FullInitialDataset,NoLoad",
              "FullInitialDataset,LowLoad",
              "FullInitialDataset,HighLoad"];

$tags = ["scaling"];

enter_testcases($testcases, $scenarios, $tags);


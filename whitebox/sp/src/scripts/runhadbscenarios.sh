#!/bin/bash
#
# $Id: runhadbscenarios.sh 11285 2007-07-31 17:45:53Z sarahg $
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

#
# Script to run the various HADB test scenarios.
# See https://hc-twiki.sfbay.sun.com/twiki/bin/view/Main/HADBStateMachineTesting
#
# This script is launched from the service node.  The expectation is that
# the SUNWhcwbsp and honeycomb-hctest.jar are installed on the service
# node.  See the twiki page for more info.
#


# defaults
DEFAULT_ITERS_PER_SCENARIO=1
DEFAULT_LOOPS=1

usage() {
    echo "usage:"
    echo "$0: [-i num_iterations_per_scenario] [-l num_loops]"
    echo
    echo "default num_iterations_per_scenario is $DEFAULT_ITERS_PER_SCENARIO"
    echo
    echo "default num_loops is $DEFAULT_LOOPS"
    echo
    exit 1
}

ITERS_PER_SCENARIO=$DEFAULT_ITERS_PER_SCENARIO
LOOPS=$DEFAULT_LOOPS

while getopts l:i: o
do	
    case "$o" in
    i) ITERS_PER_SCENARIO=$OPTARG;;
    l) LOOPS=$OPTARG;;
    h) usage ;;        	
    *) usage ;;        	
    esac
done


REBOOTTEST="/opt/test/bin/runRebootTest.sh"
LOGDIR="/export/home/root/hadbtest-`date '+%m.%d.%y_%H:%M:%S'`"

echo "Logs are in $LOGDIR"
echo "Will run $ITERS_PER_SCENARIO iterations of each scenario"
echo "Will repeat the above loop $LOOPS times"



# for a given scenario, run it the requested number of times
run_scenario() {
    SCENARIO_NAME=$1
    shift
    ARGS=$*
    
    echo
    echo
    echo

    log_date "Scenario $SCENARIO_NAME, iterations=$ITERS_PER_SCENARIO"

    SCENLOGDIR="$LOGDIR/$SCENARIO_NAME-`date '+%m.%d.%y_%H:%M:%S'`"

    # set up logdir and logfile
    mkdir -p $SCENLOGDIR
    SCENLOGFILE="$SCENLOGDIR/$SCENARIO_NAME.out"

    CMD="$REBOOTTEST -I $ITERS_PER_SCENARIO -L $SCENLOGDIR $ARGS"
    echo "Command: $CMD"
    echo "Logfile: $SCENLOGFILE"
    $CMD > $SCENLOGFILE 2>&1
    if [ $? -ne 0 ]; then
        log_date "Test $SCENARIO_NAME Failed"
        exit 1
    else
        log_date "Test $SCENARIO_NAME Passed"
    fi
}

log_date() {
    echo "[`date`]  $*"
}

i=1 
while [ $i -le $LOOPS ]; 
do
    echo
    echo
    echo
    log_date "------------ Test Iteration $i of $LOOPS -------------"
    
    run_scenario "mdfs_reboot_001" "-CO reboot"
    run_scenario "mdfs_reboot_002" "-NA kill -NN 1 -CO reboot"
    run_scenario "mdfs_reboot_003" "-NA kill -NN 1 -RN -CO reboot"
    run_scenario "mdfs_reboot_004" "-NA disable -NN 1 -CO reboot"
    run_scenario "mdfs_reboot_005" "-NA disable -NN 2 -RN -CO reboot"
    run_scenario "mdfs_reboot_006" "-CO hardreboot -WT 4200 -S 5400"
    run_scenario "mdfs_reboot_007" \
                         "-NA disable -NN 2 -KM -RN -CO reboot -WT 4200 -S 5400"

    run_scenario "mdfs_failure_001" "-NA kill -NN 2 -RN -CO nothing"
    run_scenario "mdfs_failure_002" \
                                 "-NA reboot -NN 2 -RN -KM -CO nothing -WT 1500"
    run_scenario "mdfs_failure_003" "-NA kill -NN 2 -KM -CO nothing -WT 1500"
    run_scenario "mdfs_failure_004" \
                        "-R com.sun.honeycomb.hctest.hadb.DisableHADBDisk " \
                        "-DisableHADBDisk.numberOfNodes 1 -CO reboot"
    
    run_scenario "mdfs_extend_001" \
     "-NA disable -NN 2 -R com.sun.honeycomb.hctest.hadb.ExtendDomain -CO extend"
    run_scenario "mdfs_extend_002" \
     "-NA disable -NN 1 -R com.sun.honeycomb.hctest.hadb.ExtendDomain -CO extend"

    i=`expr $i + 1` 
done

echo "All Tests Passed"
exit 0

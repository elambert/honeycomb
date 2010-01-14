#!/bin/bash
#
# $Id: simpleRebootLoop.sh 11285 2007-07-31 17:45:53Z sarahg $
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
# This is a very simple loop to run CLI reboot or reboot --all.
# It doesn't do validation that things came up well.
# It doesn't do validation that HADB is happy.
# It is primarily useful for trying to repro bugs like:
# 6569877 one node got stuck during CLI reboot; deadlock in kernel related to ZFS?
# 6572999 CLI reboot is not reliable, some nodes are left behind in degraded state, "CLEAN SHUTDOWN TIMED OUT"
#

# defaults
DEFAULT_ITERS=100
DEFAULT_SLEEPTIME=900
DEFAULT_ADMINIP="10.123.45.200"

usage() {
    echo "$0: [-a Admin_IP] [-i num_iterations] [-t sleeptime_in_seconds] [-A]"
    echo
    echo "default Admin_IP is $DEFAULT_ADMINIP"
    echo "This default only works from the service node"
    echo
    echo "default num_iterations is $DEFAULT_ITERS"
    echo
    echo "default sleeptime is $DEFAULT_SLEEPTIME"
    echo
    echo "-A reboots the entire cluster (ie, reboot --all)"
    echo "Don't use this option if running from the service node"
    exit 1
}

ITERS=$DEFAULT_ITERS
SLEEPTIME=$DEFAULT_SLEEPTIME
ADMINIP=$DEFAULT_ADMINIP

while getopts a:t:i:Ah o
do	
    case "$o" in
    a) ADMINIP=$OPTARG;;
    i) ITERS=$OPTARG;;
    A) REBOOTALL=1 ;;
    t) SLEEPTIME=$OPTARG;;
    h) usage ;;        	
    *) usage ;;        	
    esac
done

SSH_CLI="ssh -q -l admin -o StrictHostKeyChecking=no -i $HOME/.ssh/id_dsa $ADMINIP"

i=1 
while [ $i -le $ITERS ]; 
do
    echo "`date` ------------ Test Iteration $i of $ITERS -------------"
    
    echo
    $SSH_CLI sysstat
    echo

    if [ -n "$REBOOTALL" ]; then
        echo "`date` Running reboot all"
        $SSH_CLI reboot -F -A
    else 
        echo "`date` Running reboot" 
        $SSH_CLI reboot -F 
    fi

    echo
    echo "`date` waiting for $SLEEPTIME seconds"
    sleep $SLEEPTIME
    echo

    i=`expr $i + 1` 
done


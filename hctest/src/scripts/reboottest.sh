#!/bin/bash
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

usage() {
    echo "$0: -s <Service Processor IP> -a <Admin IP> [-i # of iterations (default 10)] [-A] "
    echo "-A reboots the entire cluster"
    exit 1
}

if [ $# -lt 2 ]; then
    usage
fi

ITERS=10
while getopts a:s:i:Ah o
do	
    case "$o" in
    a) ADMINIP=$OPTARG;;
    s) SPIP=$OPTARG;;
    i) ITERS=$OPTARG;;
    A) REBOOTALL=1 ;;
    h) usage ;;        	
    *) usage ;;        	
    esac
done

SSH_CLI="ssh -q -l admin -o StrictHostKeyChecking=no -i $HOME/.ssh/id_dsa $ADMINIP"
SSH_SP="ssh -q -l root -o StrictHostKeyChecking=no -i $HOME/.ssh/id_dsa $SPIP"

i=0 
TEST_STATUS=0
while [ $i -lt $ITERS ]; 
do
    echo "Test Iteration ... $i"
    
    # run the upgrade checker
    echo "Pre-Upgrade: running upgrade checker"
    $SSH_SP /opt/honeycomb/bin/upgrade_checker.pl -m
    if [ $? -ne 0 ]; then
        echo "Cluster is not healthy for an upgrade"
        echo "Aborting..."   
        exit 1 
    fi

    if [ -n "$REBOOTALL" ]; then
        echo "Running reboot all"
        $SSH_CLI reboot -F -A
    else 
        echo "Running reboot" 
        $SSH_CLI reboot -F 
    fi

    echo "waiting for 30 minutes"
    # sleep for 30 minutes
    sleep 1800

    # run the upgrade checker
    echo "Post-Upgrade: running upgrade checker"
    $SSH_SP /opt/honeycomb/bin/upgrade_checker.pl -m
    if [ $? -ne 0 ]; then
        TEST_STATUS=1
    fi
 
    i=`expr $i + 1` 
done

exit $TEST_STATUS

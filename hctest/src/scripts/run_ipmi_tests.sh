#! /bin/sh
#
# $Id: run_ipmi_tests.sh 11613 2007-10-18 22:59:22Z sm193776 $
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

OVERALL_TEST_STATUS=0 

usage() {
    echo USAGE: 
    echo "    $0 -c <CLUSTER> -n <NODES>"
    echo BUGS:
    echo "    IPMI works when primary switch is switch1" 
    exit 1
}

myecho() {
    echo "[`date`] $*"
}

run() {
    $*
    rc=$?
    myecho "Running command $* ... "
    if [ $rc -ne 0 ]; then
        OVERALL_TEST_STATUS=1
        myecho "================================================================"
        myecho "$* FAILED"
        myecho "================================================================"
    fi 
    myecho
    myecho
    return $rc
}

run_ipmi_commands() {
    HOST=$1
    myecho "Running ipmi commands on node hcb$i"
    run $SSH_CHEAT ipmitool -f /export/home/root/ipmi-pass -I lan -U Admin -H $HOST lan print 
    run $SSH_CHEAT ipmitool -f /export/home/root/ipmi-pass -I lan -U Admin -H $HOST lan print 2 
    run $SSH_CHEAT ipmitool -f /export/home/root/ipmi-pass -I lan -U Admin -H $HOST chassis status 
    run $SSH_CHEAT ipmitool -f /export/home/root/ipmi-pass -I lan -U Admin -H $HOST sdr 
    run $SSH_CHEAT ipmitool -f /export/home/root/ipmi-pass -I lan -U Admin -H $HOST fru 
    run $SSH_CHEAT ipmitool -f /export/home/root/ipmi-pass -I lan -U Admin -H $HOST bmc info 
    run $SSH_CHEAT ipmitool -f /export/home/root/ipmi-pass -I lan -U Admin -H $HOST sel elist 
}

# Main
while getopts "c:n:h" o; do
    case "$o" in
        c) CLUSTER=$OPTARG ;;
        n) NODES=$OPTARG ;;
        h) usage ;; 
        *) usage ;; 
    esac
done

if [ "$CLUSTER" != "" ] && [ "$NODES" != "" ]; then
    SSH_CHEAT="ssh -q -o StrictHostKeyChecking=no -l root $CLUSTER-cheat"
    $SSH_CHEAT "echo honeycomb > /export/home/root/ipmi-pass"
    run_ipmi 
    i=101
    while [ $i -le `expr 100 + $NODES` ]; do
        run_ipmi_commands hcb$i-sp
        i=`expr $i + 1` 
        sleep 5 
    done

    if [ $OVERALL_TEST_STATUS -ne 0 ]; then
        myecho "================================================================"
        myecho "IPMI Tests FAILED"
        myecho "================================================================"
    else 
        myecho "================================================================"
        myecho "IPMI Tests PASSED"
        myecho "================================================================"
    fi 
else 
    usage 
fi 

exit $OVERALL_TEST_STATUS

#!/bin/bash
#
# $Id: nat_tests.sh 11517 2007-09-19 23:27:47Z sm193776 $
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

if [ $# -lt 1 ]; then
    echo "$0: <cluster>"
    exit 1
fi 

# Cluster specific variables
CLUSTER=$1
ADMIN_VIP=${CLUSTER}-admin
SP_VIP=${CLUSTER}-cheat
DATA_VIP=${CLUSTER}-data
SSH_MASTER_NODE="ssh -l root -q -o StrictHostKeyChecking=no $ADMIN_VIP"
SSH_CHEAT="ssh -q -o StrictHostKeyChecking=no -l root $SP_VIP" 
SSH_PSWITCH="$SSH_MASTER_NODE ssh -q -l nopasswd -o StrictHostKeyChecking=no -p 2222 10.123.45.1"
SSH_SSWITCH="$SSH_PSWITCH /usr/bin/run_cmd_other.sh"
SWITCH_OTHER_IP=`$SSH_PSWITCH grep SWITCH_OTHER_INTERCONNECT /etc/honeycomb/switch.conf | awk -F= {'print $2'}`
DNS=`$SSH_PSWITCH grep DNS= /etc/honeycomb/switch.conf | awk -F= {'print $2'}`
NUM_NODES=`$SSH_MASTER_NODE grep honeycomb.cell.num_nodes /config/config.properties | awk -F= {'print $2'}`;
SSH_CLI="ssh -q -o StrictHostKeyChecking=no -l admin $ADMIN_VIP"  

# Iterations Limits
MAX_MASTER_FAILOVER_ITERATIONS=1

# Number of DNAT and SNAT rules
NUM_DNAT_RULES_WITH_DNS=`$SSH_PSWITCH grep NUM_DNAT_RULES_WITH_DNS= /etc/rcZ.d/S90nat | awk -F= {'print $2'}` 
NUM_SNAT_RULES_WITH_DNS=`$SSH_PSWITCH grep NUM_SNAT_RULES_WITH_DNS= /etc/rcZ.d/S90nat | awk -F= {'print $2'}` 
NUM_DNAT_RULES_WO_DNS=`$SSH_PSWITCH grep NUM_DNAT_RULES_WO_DNS= /etc/rcZ.d/S90nat | awk -F= {'print $2'}`
NUM_SNAT_RULES_WO_DNS=`$SSH_PSWITCH grep NUM_SNAT_RULES_WO_DNS= /etc/rcZ.d/S90nat | awk -F= {'print $2'}`

# TESTS STATS
NUM_TESTS=0
NUM_TESTS_FAILED=0 
OVERALL_TEST_STATUS=0

myecho() {
    echo "[`date`] $*"
}

run() {
    NUM_TESTS=`expr $NUM_TESTS + 1`
    $*
    rc=$?

    if [ $rc -ne 0 ]; then
        OVERALL_TEST_STATUS=1
        NUM_TESTS_FAILED=`expr $NUM_TESTS_FAILED + 1` 
        myecho "================================================================" 
        myecho "TEST $1 FAILED"
        myecho "================================================================" 
    else 
        myecho "================================================================" 
        myecho "TEST $1 PASSED" 
        myecho "================================================================" 
    fi
    myecho
    myecho
    return $rc
}

wait_for_switches() {
    for x in 1 2 ; do 
        ping -c 1 $ADMIN_VIP
        if [ $? -eq 0 ]; then
            $SSH_PSWITCH date
            if [ $? -eq 0 ]; then
                $SSH_SSWITCH date
                if [ $? -eq 0 ]; then
                    myecho "INFO: switch1 and switch2 are online"
                    break
                else 
                    myecho "INFO: slave switch is not ssh'able, retry after 5 mins" 
                    sleep 300
                fi
            else  
                myecho "INFO: master switch is not ssh'able, retry after 5 mins" 
                sleep 300
            fi
        else 
            myecho "INFO: $ADMIN_VIP not pingable, retry after 5 mins"
            sleep 300
        fi
    done
}

enable_dns() {
    $SSH_CLI hivecfg -D y -m sun.com -e sfbay.sun.com -1 10.7.224.10 -2 129.146.11.21
    $SSH_CLI reboot -F -A
    wait_for_switches 
}

run_verification_procs() {
    # Test status, set to pass
    STATUS=0

    if [ "$DNS" == "y" ]; then
        EXPECTED_NUM_DNAT_RULES=$NUM_DNAT_RULES_WITH_DNS
        EXPECTED_NUM_SNAT_RULES=$NUM_SNAT_RULES_WITH_DNS
    else 
        EXPECTED_NUM_DNAT_RULES=$NUM_DNAT_RULES_WO_DNS
        EXPECTED_NUM_SNAT_RULES=$NUM_SNAT_RULES_WO_DNS
    fi
     
    for i in 1 2 3; do 
        myecho "verifying NAT table .. iteration $i"
        NUM_DNAT_RULES=`$SSH_PSWITCH iptables -t nat -L -n | grep -c DNAT`
        NUM_SNAT_RULES=`$SSH_PSWITCH iptables -t nat -L -n | grep -c SNAT`
        if [ "$NUM_DNAT_RULES" != "" ] &&
           [ "$NUM_DNAT_RULES" == "$EXPECTED_NUM_DNAT_RULES" ] &&
           [ "$NUM_SNAT_RULES" != "" ] &&
           [ "$NUM_SNAT_RULES" == "$EXPECTED_NUM_SNAT_RULES" ]; then
            myecho "NAT rules, OK"
            msg1="got the expected $EXPECTED_NUM_DNAT_RULES dnat rules"
            msg2="got the expected $EXPECTED_NUM_SNAT_RULES snat rules"
            myecho "INFO: $msg1"
            myecho "INFO: $msg2"
            break
        else 
            STATUS=1 
            myecho "ERR: NAT rules, not ok"
            myecho "ERR: got $NUM_DNAT_RULES dnat rules and $NUM_SNAT_RULES snat rules"
            myecho "ERR: expected $EXPECTED_NUM_DNAT_RULES dnat rules and $EXPECTED_NUM_SNAT_RULES snat rules"
        fi
    done
    return $STATUS
}

test_reboot_primary_switch() {
    myecho "*** rebooting the primary switch ***"

    $SSH_SP logger -p info ***REBOOTING MASTER SWITCH*** 
    $SSH_PSWITCH reboot

    wait_for_switches
 
    # verification 
    run_verification_procs

    return $? 
} # test_reboot_primary_switch #

test_reboot_secondary_switch() {
    myecho "*** rebooting the secondary switch ***"

    TEST_STATUS=0

    $SSH_PSWITCH ping -c 1 $SWITCH_OTHER_IP 
    if [ $? -eq 0 ]; then
        myecho "$SWITCH_OTHER_IP is alive" 
        $SSH_SSWITCH reboot 

        wait_for_switches
 
        # verification 
        run_verification_procs 
    fi

    return $? 
} # test_reboot_secondary_switch #

test_reboot_both_switches() {
    myecho "*** rebooting both switches ***"

    TEST_STATUS=0

    $SSH_SP logger -p info ***REBOOTING BOTH SWITCHES*** 
    $SSH_PSWITCH /usr/sbin/swadm -r -o 

    wait_for_switches
 
    # verification 
    run_verification_procs 

    return $? 
}

# Test 1 - reboots
run test_reboot_primary_switch

# Test 2 - reboot secondary switch
run test_reboot_secondary_switch

# Test 3 - reboot both switches
run test_reboot_both_switches

# Run the above tests with DNS enabled.
enable_dns
# Test 4 - reboots
run test_reboot_primary_switch

# Test 5 - reboot secondary switch
run test_reboot_secondary_switch

# Test 6 - reboot both switches
run test_reboot_both_switches

myecho "================================================================" 
myecho "NAT TEST SUITE SUMMARY PASSED=`expr $NUM_TESTS - $NUM_TESTS_FAILED` FAILED=$NUM_TESTS_FAILED"
myecho "================================================================" 

exit $OVERALL_TEST_STATUS

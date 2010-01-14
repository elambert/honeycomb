#! /bin/bash
#
# $Id: time_compliance_tests.sh 11489 2007-09-10 19:00:03Z sm193776 $
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
    echo NAME 
    echo "    $0 - tests for time compliance feature"
    echo  
    echo SYNOPSIS
    echo "    $0 <cluster>"
    echo  
    echo DESCRIPTION
    echo "    Automated test suite for time compliance feature"
    echo "    These tests verify time compliance during reboots, switch failovers"
    echo "    and such fault injection events"    
    echo  
    echo USAGE 
    echo "    $0 dev325 > ~/timeCompliance.out 2>&1 &"
    echo "    To view the log file, do "
    echo "    cl38# tail -f ~/timeCompliance.out" 
    echo  
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
NUM_NODES=`$SSH_MASTER_NODE grep honeycomb.cell.num_nodes /config/config.properties | awk -F= {'print $2'}`;
NUM_DISKS=`echo "$NUM_NODES * 4"|bc`

# Iterations Limits
MAX_REBOOT_ITERATIONS=1
MAX_MASTER_FAILOVER_ITERATIONS=1

# TESTS STATS
NUM_TESTS=0
NUM_TESTS_FAILED=0 
OVERALL_TEST_STATUS=0

IRULES="/opt/honeycomb/bin/irules.sh"
TIME_VERIFIER=/opt/test/bin/time_compliance_verifier.pl

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

# Max wait time 1 hour
wait_for_cluster_online() {
    for x in 1 2 3 4 5 6 7 8 9 10 11 12; do
        ping -c 1 $ADMIN_VIP
        if [ $? -ne 0 ]; then
            myecho "Unable to ping $ADMIN_VIP"
            myecho "wait for 5 mins and retry"
            sleep 300
            continue
        fi
 
        o=`ssh -q -o StrictHostKeyChecking=no admin@$ADMIN_VIP sysstat`
        echo $o | grep "$NUM_NODES nodes"
        rc1=$?
        echo $o | grep "$NUM_DISKS disks"
        rc2=$?
        if [ $rc1 -eq 0 ] && [ $rc2 -eq 0 ]; then
            myecho "$NUM_NODES nodes are online and $NUM_DISKS disks are enabled" 
            break 
        else 
            myecho "$NUM_NODES nodes are not online and/or $NUM_DISKS disks are not enabled" 
            myecho "wait for 5 mins and retry"
            sleep 300
        fi 
    done
}

get_ntp_port() {
    PORT=`$SSH_MASTER_NODE $IRULES | grep udp | awk {'print $12'}`
    echo $PORT
}

get_ntp_rule() {
    RULE=`$SSH_MASTER_NODE $IRULES | grep udp` 
    echo $RULE
}

get_ntp_pid() {
    PID=`$SSH_MASTER_NODE cat /var/run/external_ntpd.pid`
    echo $PID
}

run_compliance_verifier() {
        $TIME_VERIFIER $CLUSTER
        if [ $? -eq 0 ]; then
            myecho "good: honeycomb nodes are time compliant"     
            return 0 
        else
            myecho "bad: honeycomb nodes are not time compliant"      
            return 1
        fi
}

verify_ntp_not_locally_synced() {
    $SSH_MASTER_NODE /opt/honeycomb/sbin/ntpq -p | grep -iv local | grep '*' 
    if [ $? -eq 0 ]; then  
        myecho "good: Master node is now time synced to a external ntp server"
        return 0
    else 
        myecho "ntp status on master `$SSH_MASTER_NODE /opt/honeycomb/sbin/ntpq -p`"
        myecho "bad: check ntp status on the master"
        return 1
    fi
}

verify_ntp_synced() {
        $SSH_MASTER_NODE /opt/honeycomb/sbin/ntpq -p | grep '*'
        if [ $? -eq 0 ]; then
            myecho "good: Master node is now time synced to its LOCAL hardware clock"
            return 0
        else 
            myecho "ntp status on master `$SSH_MASTER_NODE /opt/honeycomb/sbin/ntpq -p`"
            myecho "bad: check ntp status on the master"
            return 1
        fi
}

verify_ntp_rule() {
    MASTER_NODE_HOSTNAME=`$SSH_MASTER_NODE hostname`
    PORT=`get_ntp_port`
    if [ "hcb`expr $PORT + 100`" !=  "$MASTER_NODE_HOSTNAME" ]; then
        myecho "bad: incorrect port # for switch ntp rule"
        myecho "expected $MASTER_NODE_HOSTNAME, got hcb`expr $PORT + 100`"
        myecho "switch ntp rule `get_ntp_rule`" 
        return 1 
    else 
        myecho "switch ntp rule `get_ntp_rule`" 
        myecho "good: switch ntp rule setup correctly"
        return 0
    fi 
}

verify_ntp_pid() {
    STATUS=0
    EXT_NTPD_PID=`get_ntp_pid`
    if [ "$EXT_NTPD_PID" != "" ]; then 
        $SSH_MASTER_NODE ps -p $EXT_NTPD_PID 
        if [ $? -eq 0 ]; then
            myecho "good: external ntp daemon is running pid `get_ntp_pid`"
        else 
            myecho "bad: external ntp daemon is not running" 
            STATUS=1
        fi 
    else
            myecho "no external ntpd pid found" 
            STATUS=1 
    fi
    myecho 
    NUM_NTPD=`$SSH_MASTER_NODE ps -ef | grep -c external_ntp.conf`
    if [ $NUM_NTPD -gt 1 ]; then
        myecho "NTP daemons `$SSH_MASTER_NODE ps -ef | grep external_ntp.conf`"
        myecho "bad: more than 1 external ntp daemon is running"
        STATUS=1 
    else
        myecho "good: exactly 1 external ntp daemon is running"
    fi
    
    return $STATUS 
}

run_verification_procs() {
    STATUS=0
    myecho
    verify_ntp_synced  
    if [ $? -ne 0 ]; then 
        STATUS=1
    fi  
    myecho

    # 
    myecho
    verify_ntp_rule 
    if [ $? -ne 0 ]; then
        STATUS=1
    fi
    myecho 
     
    # 
    myecho
    verify_ntp_pid
    if [ $? -ne 0 ]; then
        STATUS=1
    fi
    myecho 
    
    # 
    myecho
    run_compliance_verifier
    if [ $? -ne 0 ]; then
        STATUS=1
    fi 
    myecho 

    myecho "wait for 10 mins"
    sleep 600

    #
    # Total Wait Time 120*5 = 600 seconds = 10 mins
    myecho
    SYNC_STATUS=1 
    for x in 1 2 3 4 5 ; do
    verify_ntp_not_locally_synced
    if [ $? -eq 0 ]; then
        SYNC_STATUS=0
        break
    else 
        myecho "waiting for 2 minutes"
        sleep 120 
    fi 
    done
    if [ $SYNC_STATUS -ne 0 ]; then
        TEST_STATUS=1 
    fi 
    myecho 

    return $STATUS
}

# TEST_STATUS = 0, if pass
# TEST_STATUS = 1, if fail

test_regression_reboot_all() {
    i=0

    TEST_STATUS=0

    while [ $i -lt $MAX_REBOOT_ITERATIONS ];
    do 
        myecho "*** rebooting $CLUSTER .. iteration $i ***"
        ssh -q -o StrictHostKeyChecking=no -l root $SP_VIP logger -p info ***REGRESSION REBOOT TIME COMPLIANCE TEST .. ITERATION $i*** 

        ssh -q -o StrictHostKeyChecking=no admin@$ADMIN_VIP reboot -F -A 

        wait_for_cluster_online

        # Verification
        run_verification_procs 

        i=`expr $i + 1`
    done
 
    return $TEST_STATUS
} # test_regression_reboot #
test_regression_reboot() {
    i=0

    TEST_STATUS=0

    while [ $i -lt $MAX_REBOOT_ITERATIONS ];
    do 
        myecho "*** rebooting $CLUSTER .. iteration $i ***"
        ssh -q -o StrictHostKeyChecking=no -l root $SP_VIP logger -p info ***REGRESSION REBOOT TIME COMPLIANCE TEST .. ITERATION $i*** 

        ssh -q -o StrictHostKeyChecking=no admin@$ADMIN_VIP reboot -F 

        wait_for_cluster_online

        # Verification 
        run_verification_procs 

        i=`expr $i + 1`
    done
 
    return $TEST_STATUS
} # test_regression_reboot #

test_regression_master_failover() {
    myecho "*** running master failover tests ***"
 
    TEST_STATUS=0

    MASTER_NODE_HOSTNAME=`$SSH_MASTER_NODE hostname`
    myecho 
    myecho "hostname before master failover ... $MASTER_NODE_HOSTNAME"
    myecho 

    ssh -q -o StrictHostKeyChecking=no -l root $SP_VIP logger -p info ***MASTER FAILOVER TIME COMPLIANCE TEST*** 
    $SSH_MASTER_NODE reboot 

    myecho "wait for 5 mins"
    sleep 300 

    MASTER_NODE_HOSTNAME=`$SSH_MASTER_NODE hostname`
    myecho 
    myecho "hostname after master failover ... $MASTER_NODE_HOSTNAME"
    myecho
 
    # Verification Steps
    run_verification_procs 

    return $TEST_STATUS  
} # test_regression_master_failover #


test_kill_ntp_service() {
    myecho "*** killing ntp daemon on the master node ***"

    TEST_STATUS=0

    # kill ntp on the master node
    MASTER_NODE_HOSTNAME=`$SSH_MASTER_NODE hostname`
    EXT_NTPD_PID=`$SSH_MASTER_NODE cat /var/run/external_ntpd.pid`
    $SSH_MASTER_NODE kill -9 $EXT_NTPD_PID 

    myecho "wait for 7.5 mins"
    sleep 450 
 
    # verification 
    run_verification_procs 

    return $TEST_STATUS  
} # test_kill_ntp_service # 

test_kill_masterservers_jvm() {
    myecho "*** killing master-servers jvm ***"
 
    TEST_STATUS=0

    # kill ntp on the master node
    MASTER_NODE_HOSTNAME=`$SSH_MASTER_NODE hostname`
    $SSH_MASTER_NODE pkill -f MASTER-SERVERS 

    myecho "wait for 7.5 mins"
    sleep 450 
 
    # verification 
    run_verification_procs 

    return $TEST_STATUS  
} # test_kill_masterservers_jvm # 


test_reboot_primary_switch() {
    myecho "*** rebooting the primary switch ***"

    TEST_STATUS=0

    $SSH_SP logger -p info ***REBOOTING MASTER SWITCH, TIME COMPLIANCE TEST*** 
    $SSH_PSWITCH reboot

    myecho "wait for 10 mins"
    sleep 600
 
    # verification 
    run_verification_procs 

    return $TEST_STATUS 
} # test_reboot_primary_switch #

test_reboot_secondary_switch() {
    myecho "*** rebooting the secondary switch ***"

    TEST_STATUS=0

    $SSH_PSWITCH ping -c 1 $SWITCH_OTHER_IP 
    if [ $? -eq 0 ]; then
        myecho "$SWITCH_OTHER_IP is alive" 
        $SSH_SSWITCH reboot 

        myecho "wait for 5 mins"  
        sleep 300
 
        # verification 
        run_verification_procs 
    fi

    return $TEST_STATUS  
} # test_reboot_secondary_switch #

test_reboot_both_switches() {
    myecho "*** rebooting both switches ***"

    TEST_STATUS=0

    $SSH_SP logger -p info ***REBOOTING BOTH SWITCHES, TIME COMPLIANCE TEST*** 
    $SSH_PSWITCH /usr/sbin/swadm -r -o 

    myecho "wait for 10 mins"  
    sleep 600
 
    # verification 
    run_verification_procs 

    return $TEST_STATUS 

}


test_ntp_server_configuration_tests() {
    SERVERS="129.145.155.32,129.146.17.39"  
    myecho "*** running configuration tests ***"
     
    TEST_STATUS=0

    myecho "Adding $SERVERS as ntp servers" 
    ssh -q -o StrictHostKeyChecking=no admin@$ADMIN_VIP hivecfg -n $SERVERS 

    myecho "rebooting the cluster for ntp server settings"
    ssh -q -o StrictHostKeyChecking=no admin@$ADMIN_VIP reboot -F -A 
  
    wait_for_cluster_online
 
    NTP_SERVERS=`ssh -q -o StrictHostKeyChecking=no admin@$ADMIN_VIP hivecfg | grep -i 'ntp server' | awk -F= {'print $2'}`

    for s in `echo $SERVERS | sed 's/,/ /g'`; do
        echo $NTP_SERVERS | grep $s 
        if [ $? -ne 0 ]; then
            TEST_STATUS=1
        fi
    done 
    return $TEST_STATUS 
} # test_ntp_server_configuration_tests #

test_reboot_non_master() {
    myecho "*** rebooting non master node ***"

    TEST_STATUS=0
 
    VICE_MASTER=`$SSH_MASTER_NODE /opt/honeycomb/bin/nodemgr_mailbox.sh | grep -i vicemaster | awk {'print $1'}`
    myecho "rebooting vice master node hcb$VICE_MASTER"
    $SSH_MASTER_NODE ssh -q -l root -o StrictHostKeyChecking=no hcb$VICE_MASTER reboot
   
    myecho "wait for 2.5 mins" 
    sleep 150
 
    # verification 
    run_verification_procs 

    return $TEST_STATUS  
} # test_reboot_non_master #



# Test 1 - reboots
run test_regression_reboot
run test_regression_reboot_all

# Test 2 - Master Failover 
run test_regression_master_failover

# Test 3 - Kill NTP service
run test_kill_ntp_service
run test_kill_masterservers_jvm

# Test 4 - reboot primary switch
run test_reboot_primary_switch

# Test 5 - reboot secondary switch
run test_reboot_secondary_switch
run test_reboot_both_switches

# Test 6 - reboot non-master node
run test_reboot_non_master

# Test 7 - hivecfg tests
run test_ntp_server_configuration_tests

myecho "================================================================" 
myecho "TIME COMPLIANCE TEST SUITE SUMMARY PASSED=`expr $NUM_TESTS - $NUM_TESTS_FAILED` FAILED=$NUM_TESTS_FAILED"
myecho "================================================================" 

exit $OVERALL_TEST_STATUS

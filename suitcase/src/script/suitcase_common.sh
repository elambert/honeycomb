#!/bin/bash 
#
# $Id: suitcase_common.sh 10856 2007-05-19 02:58:52Z bberndt $
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

WHEREAMI=`cd \`dirname $0\`; pwd`
TESTROOT=$WHEREAMI/..
SSHKEYFILE=$TESTROOT/etc/cluster_id_dsa

#
# path to suitcase on target nodes
#
SUITCASE_JARPATH="/opt/honeycomb/lib"
SUITCASE_JARNAME="honeycomb-suitcase.jar"

TRUE=`true && echo $?`
FALSE=`false || echo $?`

die() {
    local ec=$1
    local msg=$2
    echo $msg
    exit $ec
}

nodeNum () {
    echo $[$1+1]
}

nodeId () {
    echo $[100+`nodeNum $1`]
}

nodeHost () {
    echo "hcb`nodeId $1`"
}

nodeIp () {
    echo "10.123.45.`nodeId $1`"
}

dataDir () {
    local disk=$1
    echo /data/$disk
}

netdisksDir () {
    local node=$1
    local disk=$2
    echo "/netdisks/`nodeIp $node`/data/$disk"
}

auditDir () {
    local node=$1
    local disk=$2
    echo "`netdisksDir $node $disk`/audit"
}

includeNode () {
    local node=$1
    NODES[$node]=1
}

excludeNode () {
    local node=$1
    echo "excluding `nodeHost $node`"
    NODES[$node]=0
}

nodeIncluded () {
    if [ ${NODES[$1]} -eq 1 ]; then
        true
    else
        false
    fi
    return $?
}

initNodeList () {
    local node=0
    while [ $node -lt 16 ]; do
        includeNode $node
        let node++
    done
}

diskIndex () {
    local node=$1
    local disk=$2
    local disk_i=16*node+disk
    echo $disk_i
}

includeDisk () {
    local node=$1
    local disk=$2
    DISKS[`diskIndex $node $disk`]=1
}

excludeDisk () {
    local node=$1
    local disk=$2
    echo "excluding `nodeHost $node`:`dataDir $disk`"
    DISKS[`diskIndex $node $disk`]=0
}

diskIncluded () {
    local node=$1
    local disk=$2
    if [ ${DISKS[`diskIndex $node $disk`]} -eq 1 ]; then
        true
    else
        false
    fi
    return $?
}

initDiskList () {
    local node=0
    while [ $node -lt 16 ]; do
        local disk=0
        while [ $disk -lt 4 ]; do
            includeDisk  $node $disk
            let disk++
        done
        let node++
    done
}

handleExcludeOpt () {
    local opt=$1

    echo $opt | egrep '^[0-9]+$' > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        local node=$opt
        if [ $node -gt 0 -a $node -le 16 ]; then
            excludeNode $[node-1]
            return 0
        fi
    fi

    echo $opt | egrep '^[0-9]+(:[0-9]+)?$' > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        let node=`echo $opt | cut -d ':' -f 1`
        let disk=`echo $opt | cut -d ':' -f 2`
        if [ $node -gt 0 -a $node -le 16 -a $disk -ge 0 -a $disk -lt 4 ]; then
            excludeDisk $[node-1] $disk
            return 0
        fi 
    fi
    
    echo "error: invalid -X option: $opt"
    return 1
}

VERBOSE=false
handleVerboseOpt () {
    VERBOSE=true
}
verbose () {
    $VERBOSE
}

pingNodes() {
    echo "determining which nodes are online..."
    local node=0
    while [ $node -lt 16 ] ; do
        if nodeIncluded $node; then
            local hcbXXX=`nodeHost $node`
            ssh $CHEAT -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "ping $hcbXXX 1 2>&1 /dev/null" 2>&1 /dev/null
            if [ $? -ne 0 ] ; then
                excludeNode $node
            fi
        fi
        let node++
    done
}

startMountDrive () {
    local node=$1
    local disk=$2
    local hcbXXX=`nodeHost $node`
    local data_X=`dataDir $disk`
    local pid_i=$[16*$node+$disk]

    sleep 1
    echo "checking $hcbXXX:$data_X"
    ssh $CHEAT -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "ssh `nodeHost $node` \"mount | grep ^`dataDir $disk`\"" > /dev/null
    if [ $? -ne 0 ]; then
        echo "starting mount $hcbXXX:$data_X"
        echo "ssh $CHEAT -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root \"ssh `nodeHost $node` \\\"mkdir -p `dataDir $disk`; mount /dev/dsk/c${disk}d0s4 `dataDir $disk`\\\"\""
        ssh $CHEAT -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "ssh `nodeHost $node` \"mkdir -p `dataDir $disk`; mount /dev/dsk/c${disk}d0s4 `dataDir $disk`\"" &
        local pid=$!
        MOUNT_PID[$pid_i]=$pid
    else
        MOUNT_PID[$pid_i]=0
    fi

    return 0
}

waitMountDrive () {
    local node=$1
    local disk=$2
    local hcbXXX=`nodeHost $node`
    local data_X=`dataDir $disk`
    local pid_i=$[16*$node+$disk]
    local pid=${MOUNT_PID[$pid_i]}

    if [ $pid -ne 0 ]; then
        echo "waiting for mount $hcbXXX:$data_X"
        wait $pid
        local rc=$?
        if [ $rc -ne 0 ]; then
            echo "error: mount $hcbXXX:$data_X failed (rc=$rc)"
            return 1
        fi
    fi

    echo "$hcbXXX:$data_X mounted"
    ssh $CHEAT -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "ssh $hcbXXX 'mkdir -p $data_X/audit; mkdir -p /netdisks/`nodeIp $node`/data; ln -sf /data/$disk /netdisks/`nodeIp $node`/data/$disk'"
}

startMountDrives () {
    local node=0
    while [ $node -lt 16 ] ; do
        if nodeIncluded $node; then
            local disk=0
            while [ $disk -lt 4 ]; do
                if diskIncluded $node $disk; then
                    startMountDrive $node $disk
                fi
                let disk++
            done
        fi
        let node++
    done
}

waitMountDrives () {
    local errors=0
    local node=0
    while [ $node -lt 16 ] ; do
        if nodeIncluded $node; then
            local disk=0
            while [ $disk -lt 4 ]; do
                if diskIncluded $node $disk; then
                    waitMountDrive $node $disk
                    let errors=errors+$?
                fi
                let disk++
            done
        fi
        let node++
    done
    return $errors
}

prepDrives() {
    local errors=0

    echo "prepping drives for audit..."

    startMountDrives
    let errors=errors+$?

    waitMountDrives
    let errors=errors+$?

    ssh $CHEAT -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "mkdir -p /export/honeycomb/audit"
    let errors=errors+$?

    return $errors
}

fragFileBasename() {
    local node=$1
    local disk=$2
    echo "fragment.`nodeId $node`-$disk"
}

fragFileOut() {
    local node=$1
    local disk=$2
    echo "`auditDir $node $disk`/`fragFileBasename $1 $2`.out"
}

fragFileErr() {
    local node=$1
    local disk=$2
    echo "`auditDir $node $disk`/`fragFileBasename $1 $2`.err"
}

fragFileTmp() {
    local node=$1
    local disk=$2
    echo "/tmp/.`fragFileBasename $1 $2`.$$.err"
}

sysMdFileBasename() {
    local node=$1
    local disk=$2
    echo "system-metadata.`nodeId $node`-$disk"
}

sysMdFileOut() {
    local node=$1
    local disk=$2
    echo "`auditDir $node $disk`/`sysMdFileBasename $1 $2`.out"
}

sysMdFileErr() {
    local node=$1
    local disk=$2
    echo "`auditDir $node $disk`/`sysMdFileBasename $1 $2`.err"
}

sysMdFileTmp() {
    local node=$1
    local disk=$2
    echo "/tmp/.`sysMdFileBasename $1 $2`.$$.err"
}

startEndTimesOut() {
    echo "/export/honeycomb/audit/startEndTimes.out"
}

layoutFileOut() {
    echo "/export/honeycomb/audit/layouts.out"
}

staticFileOut() {
    echo "/export/honeycomb/audit/statics.out"
}

initNodeList
initDiskList
#pingNodes || die $? "aborting"
#prepDrives || die $? "aborting"

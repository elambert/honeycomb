#!/bin/bash
#
# $Id: harvest.sh 10856 2007-05-19 02:58:52Z bberndt $
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
source $WHEREAMI/suitcase_common.sh

HCSUITCASEJAR=$TESTROOT/lib/honeycomb-suitcase.jar

#
# path to suitcase on target nodes
#
SUITCASE_JARPATH="/opt/honeycomb/lib"
SUITCASE_JARNAME="honeycomb-suitcase.jar"

printUsage() {
    echo "$0 [-X <node_id>[:<disk_id>]] [-O oidFile] [-B date] [-A date] [-v] <service processor>"
    echo "Summarizes the fragment and system metadata on the cluster, creating"
    echo ".csv files which can then be imported into an audit database."
    echo "   -X <node_id>[:<disk_id>]"
    echo "      exclude the given node (1-16) or disk (0-3), eg. '-X 4' '-X 16:3"
    echo "      multiple -X options can be specified."
    echo "   -O <file> path to a file with one OID per line. Only fragments matching these OIDs will be harvested"
    echo "   -B <date> find files whose last modified time is before [date] (ie. \"2005-09-16 11:19:38\")"
    echo "   -A <date> find files whose last modified time is after [date] (ie. \"2005-09-16 11:19:38\")"
    echo "   -v verbose"
    echo "   -h print this message"
}

startFragHarvest () {
    local node=$1
    local disk=$2

    if [ ! -z $OIDFILE ]; then 
        OIDARG="-o /oidfile"
    fi
    if [ ! -z "$NEWERTHAN" ]; then 
        NEWERTHANARG="-n $NEWERTHAN"
    fi
    if [ ! -z "$OLDERTHAN" ]; then 
        OLDERTHANARG="-e $OLDERTHAN"
    fi

    echo "starting fragment harvest: `nodeHost $node`:`fragFileOut $node $disk`"
    sleep 1
    echo "ssh $CHEAT -o StrictHostKeyChecking=no -q -l root \"ssh `nodeHost $node` -o StrictHostKeyChecking=no \\\"$RUNHARVEST $NEWERTHANARG -d $STARTTIME -c $OIDARG $OLDERTHANARG `nodeNum $node` `netdisksDir $node $disk` 2> `fragFileErr $node $disk` 1> `fragFileOut $node $disk`; echo $? >> `fragFileErr $node $disk`\\\"\" > `fragFileTmp $node $disk` 2>&1"
    ssh $CHEAT -o StrictHostKeyChecking=no -q -l root "ssh `nodeHost $node` -o StrictHostKeyChecking=no \"$RUNHARVEST $NEWERTHANARG -d $STARTTIME -c $OIDARG $OLDERTHANARG `nodeNum $node` `netdisksDir $node $disk` 2> `fragFileErr $node $disk` 1> `fragFileOut $node $disk`; echo $? >> `fragFileErr $node $disk`\"" > `fragFileTmp $node $disk` 2>&1 &
    local pid=$!
    local pid_i=$[16*$node+$disk]
    FRAG_PID[$pid_i]=$pid
    echo "pid=$pid"

    return $pid
}

startFragHarvests () {
    local node=0;
    while [ $node -lt 16 ]; do
        if nodeIncluded $node; then
            local disk=0
            while [ $disk -lt 4 ]; do
                if diskIncluded $node $disk; then
                    startFragHarvest $node $disk
                fi
                let disk++
            done
        fi
        let node++
    done
}

waitFragHarvest () {
    local node=$1
    local disk=$2
    local pid_i=$[16*$node+$disk]
    local pid=${FRAG_PID[$pid_i]}
    local nodehost=`nodeHost $node`
    local fragfileerr=`fragFileErr $node $disk`

    echo "waiting for fragment harvest: `nodeHost $node`:`fragFileOut $node $disk` (pid=$pid)"
    wait $pid
    local rc=`ssh $CHEAT -o StrictHostKeyChecking=no -q -l root "ssh $nodehost -o StrictHostKeyChecking=no tail -1 $fragfileerr"`
    if [ "$rc" != "0" ]; then
        echo "error: fragment harvest failed: `nodeHost $node`:`fragFileOut $node $disk` (rc=$rc)"
        return 1
    else
        rm -f `fragFileTmp $node $disk`
    fi

    return 0
}

waitFragHarvests () {
    local errors=0
    local node=0;
    while [ $node -lt 16 ]; do
        if nodeIncluded $node; then
            local disk=0
            while [ $disk -lt 4 ]; do
                if diskIncluded $node $disk; then
                    waitFragHarvest $node $disk
                    let errors=errors+$?
                fi
                let disk++
            done
        fi
        let node++
    done

    return $errors
}

startSysMdHarvest () {
    local node=$1
    local disk=$2

    if [ ! -z $OIDFILE ]; then 
        OIDARG="-o /oidfile"
    fi
    if [ ! -z "$NEWERTHAN" ]; then 
        NEWERTHANARG="-n $NEWERTHAN"
    fi
    if [ ! -z "$OLDERTHAN" ]; then 
        OLDERTHANARG="-e $OLDERTHAN"
    fi

    echo "starting system metadata harvest: `nodeHost $node`:`sysMdFileOut $node $disk`"
    sleep 1
    echo "ssh $CHEAT -o StrictHostKeyChecking=no -q -l root \"ssh `nodeHost $node` -o StrictHostKeyChecking=no \\\"$RUNDBDUMP $NEWERTHANARG -d $STARTTIME $OLDERTHANARG $OIDARG `netdisksDir $node $disk` `nodeNum $node` $disk 1> `sysMdFileOut $node $disk` 2> `sysMdFileErr $node $disk`; echo $? >> `sysMdFileErr $node $disk`\\\"\" > `sysMdFileTmp $node $disk` 2>&1"
    ssh $CHEAT -o StrictHostKeyChecking=no -q -l root "ssh `nodeHost $node` -o StrictHostKeyChecking=no \"$RUNDBDUMP $NEWERTHANARG -d $STARTTIME $OLDERTHANARG $OIDARG `netdisksDir $node $disk` `nodeNum $node` $disk 1> `sysMdFileOut $node $disk` 2> `sysMdFileErr $node $disk`; echo $? >> `sysMdFileErr $node $disk`\"" > `sysMdFileTmp $node $disk` 2>&1 &
    local pid=$!
    local pid_i=$[16*$node+$disk]
    SYSMD_PID[$pid_i]=$pid
    echo "pid=$pid"

    return $pid
}

startSysMdHarvests () {
    local node=0;
    while [ $node -lt 16 ]; do
        if nodeIncluded $node; then
            local disk=0
            while [ $disk -lt 4 ]; do
                if diskIncluded $node $disk; then
                    startSysMdHarvest $node $disk
                fi
                let disk++
            done
        fi
        let node++
    done
}

waitSysMdHarvest () {
    local node=$1
    local disk=$2
    local pid_i=$[16*$node+$disk]
    local pid=${SYSMD_PID[$pid_i]}

    echo "waiting for system metadata harvest: `nodeHost $node`:`sysMdFileOut $node $disk` (pid=$pid)"
    wait $pid
    local rc=$?
    if [ $rc -ne 0 ]; then
        echo "error: system metadata harvest failed: `nodeHost $node`:`sysMdFileOut $node $disk` (rc=$rc)"
        return 1
    else
        rm -f `sysMdFileTmp $node $disk`
    fi

    return 0
}

waitSysMdHarvests () {
    local errors=0
    local node=0;
    while [ $node -lt 16 ]; do
        if nodeIncluded $node; then
            local disk=0
            while [ $disk -lt 4 ]; do
                if diskIncluded $node $disk; then
                    waitSysMdHarvest $node $disk
                    let errors=errors+$?
                fi
                let disk++
            done
        fi
        let node++
    done
    return $errors
}

getLayouts () {
    local node=0
    local done=0
    while [ $node -lt 16 -a $done -eq 0 ]; do
        if nodeIncluded $node; then
            echo "Running layout harvest on `nodeHost $node`"
            echo "ssh $CHEAT -o StrictHostKeyChecking=no -q -l root \"ssh `nodeHost $node` -o StrictHostKeyChecking=no \\\"$RUNLAYOUTDUMP -a -c -d $STARTTIME\\\" > `layoutFileOut`\""
            ssh $CHEAT -o StrictHostKeyChecking=no -q -l root "ssh `nodeHost $node` -o StrictHostKeyChecking=no \"$RUNLAYOUTDUMP -a -c -d $STARTTIME\" > `layoutFileOut`"
            local rc=$?
            if [ $rc -ne 0 ]; then
                echo "error: layout harvest failed: $CHEAT:`layoutFileOut` (rc=$rc)"
            fi
            let done=1
        fi
    done
}

getStatics () {
    local node=0
    local done=0
    while [ $node -lt 16 -a $done -eq 0 ]; do
        if nodeIncluded $node; then
            echo "Running static harvest on `nodeHost $node`..."
            echo "ssh $CHEAT -o StrictHostKeyChecking=no -q -l root \"ssh `nodeHost $node` -o StrictHostKeyChecking=no \\\"$RUNSTATICDUMP\\\" > `staticFileOut`\""
            ssh $CHEAT -o StrictHostKeyChecking=no -q -l root "ssh `nodeHost $node` -o StrictHostKeyChecking=no \"$RUNSTATICDUMP\" > `staticFileOut`"
            local rc=$?
            if [ $rc -ne 0 ]; then
                echo "error: static harvest failed: $CHEAT:`staticFileOut` (rc=$rc)"
            fi
            let done=1
        fi
    done
}

#
# MAIN
#

while getopts "O:B:A:X:vh" opt; do
    case $opt in
        O ) OIDFILE=$OPTARG
            if [ ! -f "$OIDFILE" ] ; then 
                echo "No file at $OIDFILE, aborting."
                exit 1
            fi
            ;;
        B ) OLDERTHAN=$OPTARG
            ;;
        A ) NEWERTHAN=$OPTARG
            ;;
        X ) handleExcludeOpt $OPTARG
            ;;
        v ) handleVerboseOpt
            ;;
        h ) printUsage
            exit 0
            ;;
        \? ) printUsage
        exit 1
    esac
done
shift $(($OPTIND -1 ))

#
# check for existance of /homecomb-utests.jar
# push if required
#

if [ -z "$1" ] ; then 
    printUsage
    echo "insufficient arguments: specify service processor node."
    exit 1
fi

CHEAT=$1

LIBRARYPATHARGS="-Djava.library.path=/opt/honeycomb/lib/"
CLASSPATHARGS="-classpath /opt/honeycomb/lib/db-4.2.jar:/opt/honeycomb/lib/jetty-4.2.20.jar:$SUITCASE_JARPATH/$SUITCASE_JARNAME:/opt/honeycomb/lib/honeycomb.jar:/opt/honeycomb/lib/honeycomb-server.jar:/opt/honeycomb/lib/honeycomb-common.jar:/opt/honeycomb/lib/jug.jar"
RUNHARVEST="java $CLASSPATHARGS $LIBRARYPATHARGS com.sun.honeycomb.layout.FragExplorer"
RUNDBDUMP="java $CLASSPATHARGS $LIBRARYPATHARGS com.sun.honeycomb.suitcase.DumpMetadata"
RUNLAYOUTDUMP="java $CLASSPATHARGS $LIBRARYPATHARGS com.sun.honeycomb.layout.PrintLayout"
RUNSTATICDUMP="java $CLASSPATHARGS $LIBRARYPATHARGS com.sun.honeycomb.suitcase.ClusterParameterExtractor"

pingNodes || die $? "aborting"
prepDrives || die $? "aborting"

if [ ! -z "$OLDERTHAN" ]; then
    STARTTIME=$OLDERTHAN
else
    STARTTIME=`date +"%Y-%m-%d %H:%M:%S"`
fi
echo -n "$STARTTIME," | ssh $CHEAT -o StrictHostKeyChecking=no -i $SSHKEYFILE "cat > `startEndTimesOut`"

startFragHarvests || die $? "aborting"
# DISABLE SYSTEM METADATA HARVEST FOR NOW.  IT IS CURRENTLY BROKEN.  MUST
# RENABLE WHEN WE TEST BACKUP (1.1).
#startSysMdHarvests || die $? "aborting"
getLayouts || die $? "aborting"
getStatics || die $? "aborting"
#waitSysMdHarvests || die $? "aborting"
waitFragHarvests || die $? "aborting"

date +"%Y-%m-%d %H:%M:%S" | ssh $CHEAT -o StrictHostKeyChecking=no -i $SSHKEYFILE "cat >> `startEndTimesOut`"

echo "autoharvest done."

exit 0

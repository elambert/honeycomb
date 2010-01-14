#!/bin/bash 
#
# $Id: import.sh 10856 2007-05-19 02:58:52Z bberndt $
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

CHEAT=""
DBNAME=""
DBHOST="hc-dev3"

SYSMD_CONCURRENCY=16
FRAG_CONCURRENCY=8

printUsage() {
    echo "$0 [-X <node_id>[:<disk_id>]] <service processor> <dbname>"
    echo "import files into a db"
    echo "   -X <node_id>[:<disk_id>]"
    echo "      exclude the given node (1-16) or disk (0-3), eg. '-X 4' '-X 16:3"
    echo "      multiple -X options can be specified."
}

startImportSysMd () {
    local node=$1
    local disk=$2
    local outfile="/tmp/.import.system_metadata.$node.$$.out"
    local errfile="/tmp/.import.system_metadata.$node.$$.err"
    local rcfile="/tmp/.import.system_metadata.$node.$$.rc"

    echo "starting import of `nodeHost $node`:`sysMdFileOut $node $disk`..."
    echo "ssh $CHEAT -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root \"ssh `nodeHost $node` -o StrictHostKeyChecking=no \\\"cat `sysMdFileOut $node $disk`\\\"\" | $WHEREAMI/countit.pl 100 2> $outfile | psql -h $DBHOST -U $DBNAME -d $DBNAME -c \"copy system_metadata from stdin delimiter ',' null ''\" > $errfile 2>&1"
    ssh $CHEAT -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "ssh `nodeHost $node` -o StrictHostKeyChecking=no \"cat `sysMdFileOut $node $disk`\"" | $WHEREAMI/countit.pl 100 2> $outfile | psql -h $DBHOST -U $DBNAME -d $DBNAME -c "copy system_metadata from stdin delimiter ',' null ''" > $errfile 2>&1 &
    local pid=$!
    SYSMD_PID[16*node+disk]=$pid
    echo "pid=$pid"
}

waitImportSysMd () {
    local node=$1
    local disk=$2
    local pid=${SYSMD_PID[16*node+disk]}
    local outfile="/tmp/.import.system_metadata.$node.$$.out"
    local errfile="/tmp/.import.system_metadata.$node.$$.err"
    local rcfile="/tmp/.import.system_metadata.$node.$$.rc"

    echo "waiting for import of `nodeHost $node`:`sysMdFileOut $node $disk` to finish (pid=$pid)..."
    wait $pid
    local rc=$?
    if [ $rc -ne 0 ] ; then
        echo "error: import of `nodeHost $node`:`sysMdFileOut $node $disk` failed (rc=$rc)"
        echo "error: see $errfile and $outfile for more information"
        return 1
    else
        /bin/rm -f $outfile $errfile $rcfile
    fi
    return 0
}

importSysMd() {
    local concurrency=$1
    local disk_num=0
    local total_disks=64;
    local started=0
    local finished=0
    local imports[$concurrency]

    while [ $disk_num -lt $total_disks ] ; do
        local node=$[disk_num%16]
        local disk=$[disk_num/16]
        if nodeIncluded $node && diskIncluded $node $disk; then
            if [ $[started-finished] -eq $concurrency ] ; then
                local finish=${imports[finished % $concurrency]}
                local finish_node=$[finish % 16]
                local finish_disk=$[finish / 16]
                waitImportSysMd $finish_node $finish_disk
                let finished++
            fi
            startImportSysMd $node $disk
            imports[started % concurrency]=$disk_num
            let started++
        fi
        let disk_num++
    done

    while [ $[started-finished] -gt 0 ] ; do
        local finish=${imports[finished % $concurrency]}
        local finish_node=$[finish % 16]
        local finish_disk=$[finish / 16]
        waitImportSysMd $finish_node $finish_disk
        let finished++
    done
}

startImportFrag () {
    local node=$1
    local disk=$2
    local outfile="/tmp/.import.fragments.$node-$disk.$$.out"
    local errfile="/tmp/.import.fragments.$node-$disk.$$.err"
    local rcfile="/tmp/.import.fragments.$node-$disk.$$.rc"

    echo "starting import of `nodeHost $node`:`fragFileOut $node $disk`..."
    ssh $CHEAT -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "ssh `nodeHost $node` -o StrictHostKeyChecking=no \"cat `fragFileOut $node $disk`\"" | $WHEREAMI/countit.pl 100 2> $outfile | psql -h $DBHOST -U $DBNAME -d $DBNAME -c "copy fragments from stdin delimiter ',' null ''" > $errfile 2>&1 &
    local pid=$!
    FRAG_PID[16*node+disk]=$pid
    echo "pid=$pid"
}

waitImportFrag () {
    local node=$1
    local disk=$2
    local pid=${FRAG_PID[16*node+disk]}
    local outfile="/tmp/.import.fragments.$node-$disk.$$.out"
    local errfile="/tmp/.import.fragments.$node-$disk.$$.err"
    local rcfile="/tmp/.import.fragments.$node-$disk.$$.rc"

    echo "waiting for import of `nodeHost $node`:`fragFileOut $node $disk` to finish (pid=$pid)..."
    wait $pid
    local rc=$?
    if [ $rc -ne 0 ] ; then
        echo "error: import of `nodeHost $node`:`fragFileOut $node $disk` failed (rc=$rc)"
        echo "error: see $errfile and $outfile for more information"
        return 1
    else
        /bin/rm -f $outfile $errfile $rcfile
    fi
    return 0
}

importFragments() {
    echo "dropping uidindex on fragments table..."
    psql -h $DBHOST -U $DBNAME -d $DBNAME -c "drop index uidindex"
    if [ $? -ne 0 ] ; then
        let ERRORS++
        echo "error dropping uidindex: $?"
        echo "aborting"
        exit $ERRORS
    fi

    local concurrency=$1
    local disk_num=0
    local total_disks=64;
    local started=0
    local finished=0
    local imports[$concurrency]

    while [ $disk_num -lt $total_disks ] ; do
        local node=$[disk_num%16]
        local disk=$[disk_num/16]
        if nodeIncluded $node && diskIncluded $node $disk; then
            if [ $[started-finished] -eq $concurrency ] ; then
                local finish=${imports[finished % $concurrency]}
                local finish_node=$[finish % 16]
                local finish_disk=$[finish / 16]
                waitImportFrag $finish_node $finish_disk
                let finished++
            fi
            startImportFrag $node $disk
            imports[started % concurrency]=$disk_num
            let started++
        fi
        let disk_num++
    done

    while [ $[started-finished] -gt 0 ] ; do
        local finish=${imports[finished % $concurrency]}
        local finish_node=$[finish % 16]
        local finish_disk=$[finish / 16]
        waitImportFrag $finish_node $finish_disk
        let finished++
    done

    echo "recreating uidindex on fragments table..."
    psql -h $DBHOST -U $DBNAME -d $DBNAME -c "create index uidindex on fragments (f_oid,import_time)"
    if [ $? -ne 0 ] ; then
        let ERRORS++
        echo "error creating uidindex: $?"
        echo "aborting"
        exit $ERRORS
    else
        echo "create index uidindex on fragments completed"
    fi
}

initImportFragments() {
    echo "dropping uidindex on fragments table..."
    psql -h $DBHOST -U $DBNAME -d $DBNAME -c "drop index uidindex"
    if [ $? -ne 0 ] ; then
        let ERRORS++
        echo "error dropping uidindex: $?"
        echo "aborting"
        exit $ERRORS
    fi
    local node=0
    while [ $node -lt 16 ]; do
        local disk=0
        while [ $disk -lt 4 ]; do
            let status_i=$[$node+(16*$disk)]
            let online_i=$[node+1]
            if [ ${NODE_ONLINE[$online_i]} -eq 0 ] ; then
                fragments_status[$status_i]="offline"
            else
                fragments_status[$status_i]="init"
            fi
            let disk++
        done
        let node++
    done
}
startImportFragments() {
    local node=$1
    local disk=$2
    local outfile="/tmp/.import.fragments.$[node+1]-$disk.$$.out"
    local errfile="/tmp/.import.fragments.$[node+1]-$disk.$$.err"
    local rcfile="/tmp/.import.fragments.$[node+1]-$disk.$$.rc"
    local array_i=$[$node+(16*$disk)]
    
    if [ "${fragments_status[$array_i]}" == "offline" ] ; then
        echo "skipping import of hcb$[100+$node]:/fragments.$[100+$node+1]-$disk.out..."
    else
        echo "starting import of hcb$[100+$node+1]:/fragments.$[100+$node+1]-$disk.out..."
        ssh $CHEAT -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "ssh hcb$[100+$node+1] -o StrictHostKeyChecking=no \"cat /data/$disk/audit/fragments.$[100+node+1]-$disk.out\"" | $WHEREAMI/countit.pl 100 2> $outfile | psql -h $DBHOST -U $DBNAME -d $DBNAME -c "copy fragments from stdin delimiter ',' null ''" > $errfile 2>&1 &
        local pid=$!
        fragments_pid[$array_i]=$pid
        fragments_status[$array_i]="started"
    fi
}
waitImportFragments() {
    local node=$1
    local disk=$2
    local outfile="/tmp/.import.fragments.$[node+1]-$disk.$$.out"
    local errfile="/tmp/.import.fragments.$[node+1]-$disk.$$.err"
    local rcfile="/tmp/.import.fragments.$[node+1]-$disk.$$.rc"
    local array_i=$[$node+(16*$disk)]
    local rc=0
    
    if [ "${fragments_status[$array_i]}" == "started" ] ; then
        echo "waiting for import of hcb$[100+$node+1]:/fragments.$[100+$node+1]-$disk.out to finish..."
        wait ${fragments_pid[$array_i]} > /dev/null 2>&1
        #rc=`wc -l $errfile | awk '{print $1}'`
        rc=$?
        if [ $rc -ne 0 ] ; then
            fragments_status[$array_i]="error"
            echo "error: during import of hcb$[100+$node+1]:/fragments.$[100+node+1]-$disk.out"
            echo "error: return code [$rc]"
            echo "error: see $errfile and $outfile for more information"
        else
            fragments_status[$array_i]="done"
            /bin/rm -f $outfile $errfile $rcfile
        fi
    else
        echo "no need to wait for import of hcb$[100+$node+1]:/fragments.$[100+$node+1]-$disk.out to finish..."
    fi
    return $rc
}
importAllFragments() {
    initImportFragments
    
    local concurrency=$1
    local finished=0
    local disk=0
    local total_disks=64;
    while [ $disk -lt $total_disks ] ; do
        if [ $disk -ge $concurrency ] ; then
            waitImportFragments $[finished%16] $[finished/16]
            let finished++
        fi
        startImportFragments $[disk%16] $[disk/16]
        let disk++
    done
    
    while [ $[finished] -lt $total_disks ] ; do
        waitImportFragments $[finished%16] $[finished/16]
        let finished++
    done

    echo "recreating uidindex on fragments table..."
    psql -h $DBHOST -U $DBNAME -d $DBNAME -c "create index uidindex on fragments (f_oid,import_time)"
    if [ $? -ne 0 ] ; then
        let ERRORS++
        echo "error creating uidindex: $?"
        echo "aborting"
        exit $ERRORS
    else
        echo "create index uidindex on fragments completed"
    fi
}

#
# MAIN
#

# process command line args and options
while getopts "d:X:h" opt; do
    case $opt in
        X ) handleExcludeOpt $OPTARG
            ;;
        v ) handleVerboseOpt
            ;;
        h ) printUsage
            exit 0
            ;;
        \? )  printUsage
        exit 1
    esac
done
shift $(($OPTIND -1 ))

CHEAT=$1
DBNAME=$2
if [ -z $CHEAT ] ; then
    printUsage
    echo "Please specify a cluster name."
    exit 1
fi
if [ -z $DBNAME ] ; then
    printUsage
    echo "Please specify a database name."
    exit 1
fi

let ERRORS=0

pingNodes || die $? "aborting"
prepDrives || die $? "aborting"

# import timestampfile
echo "importing startEndTimes..."
ssh $CHEAT -o StrictHostKeyChecking=no -i $SSHKEYFILE cat `startEndTimesOut` | psql -h $DBHOST -U $DBNAME -d $DBNAME -c "copy imports from stdin delimiter ',' null ''" > .import_imports.err 2>&1

# import statics
echo "importing statics..."
ssh $CHEAT -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root cat `staticFileOut` | $WHEREAMI/countit.pl 100 2> import.statics.progress | psql -h $DBHOST -U $DBNAME -d $DBNAME -c "copy statics from stdin delimiter ',' null ''" > .import_statics.err 2>&1

# import layout
echo "importing layouts..."
ssh $CHEAT -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root cat `layoutFileOut` | $WHEREAMI/countit.pl 100 2> import.layouts.progress | psql -h $DBHOST -U $DBNAME -d $DBNAME -c "copy layout from stdin delimiter ',' null ''" > .import_layout.err 2>&1

# DISABLE SYSTEM METADATA HARVEST FOR NOW.  IT IS CURRENTLY BROKEN.  MUST
# RENABLE WHEN WE TEST BACKUP (1.1).
#echo "importing system metadata..."
#importSysMd $SYSMD_CONCURRENCY

# import fragments
echo "importing fragments..."
importFragments $FRAG_CONCURRENCY

echo "import complete"
exit 0

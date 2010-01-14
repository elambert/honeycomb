#!/bin/bash 
#
# $Id: autoharvest.sh 10856 2007-05-19 02:58:52Z bberndt $
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


#TODO:
# Be able to specify a node range in the format: 1..4,6,8,15..16


#
# path to suitcase on target nodes
#
SUITCASE_JARPATH="/opt/honeycomb/lib"
SUITCASE_JARNAME="honeycomb-suitcase.jar"

#
# excluded node list 
#
EXCLUDECOUNT=0

#
# Find the key! where da key?!
#
SSHKEYFILE="$ROOT/test/etc/cluster_id_dsa"
if [ ! -f $SSHKEYFILE ] ; then
    SSHKEYFILE="$HOME/.ssh/cluster_id_dsa"
fi
if [ ! -f $SSHKEYFILE ] ; then
    SSHKEYFILE="../etc/cluster_id_dsa"
fi
if [ ! -f $SSHKEYFILE ] ; then
    SSHKEYFILE="/opt/test/etc/cluster_id_dsa"
fi
if [ ! -f $SSHKEYFILE ] ; then
    SSHKEYFILE="/opt/test/cur/etc/cluster_id_dsa"
fi
if [ ! -f $SSHKEYFILE ] ; then
    SSHKEYFILE="/opt/test/dev/etc/cluster_id_dsa"
fi
if [ ! -f $SSHKEYFILE ] ; then
    SSHKEYFILE="/opt/test/qa/etc/cluster_id_dsa"
fi
if [ ! -f $SSHKEYFILE ] ; then         
    SSHKEYFILE="$HOME/.ssh/id_dsa."       
fi


if [ ! -f $SSHKEYFILE ] ; then         
    echo "Cannot locate cluster_id_dsa or id_dsa. Giving up."
    exit 1
fi


#
# returns true if the node is in the exclude list
#
excludeNode() {
    curNode=$1

    i=0
    while test $i -lt $EXCLUDECOUNT; do
        if [ ${excludeArray[$i]} = $curNode ] ; then
            return 1
        fi
        i=`expr $i + 1`                
    done
    return 0
}
#
# Cheat is the intermediate node, target is the final node
#
runCommand ()
{
    user=root
    local COMMAND
    local status
    cstring="$@"
    result=`echo $cstring | grep " > "`
    if [ ! -z "$result" ]; then
        COMMAND=\""$@"\"
        COMMAND="\"$@\""
    else
        COMMAND=\""$@\" 1>&2"
    fi
    
#    echo "runcommand runnning: $COMMAND"

    ssh $cheat -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "ssh $target -o  StrictHostKeyChecking=no $COMMAND"
    status=$?
        #
        # For some reason "no route to host" errors place null in status.
        #
    if [ -z "$status" ]; then
        return 1
    fi
    return $status

}


#
# Checks to see if the unit tests has already been pushed there.
#
checkUtils() {
    runCommand "if [ -f $SUITCASE_JARPATH/$SUITCASE_JARNAME ]; then exit 0; else exit 1; fi"
    if [ $? -ne 0 ]; then
        echo  " $SUITCASE_JARNAME missing on $cheat:$target"

        if [ ! -z $SUITCASE ] ; then
            echo "attempting to populate node $node with $SUITCASE_JARPATH/$SUITCASE_JARNAME"            
            echo "singlepush"
            pushToClient 
        else
            echo "Use -u to provide a path to $SUITCASE_JARNAME to push it automatically"
            echo
            printUsage
            exit 1
        fi
    else
        return 0
    fi
            
}


pushToServer() {
    if [ -z $SERVERPUSHED ] ; then 
        SERVERPUSHED=true
        
        if [ -z $SUITCASE ] ; then
            echo "Path to the suitcase jar file not set.. use -u option."
            echo
            printUsage
            exit 1
        fi
        
        scp -o StrictHostKeyChecking=no  -i $SSHKEYFILE $SUITCASE root@$cheat:/ 
        if [ $? -ne 0 ] ; then
            echo "Failed to copy $SUITCASE to root@$cheat:/, exiting."
            exit 1
        fi
    fi
}


#
# pushes the utils specified in path SUITCASE to node $target
#
pushToClient() {   
    runCommand "scp -o StrictHostKeyChecking=no  root@hcb100:/$SUITCASE_JARNAME $SUITCASE_JARPATH"
    if [ $? -ne 0 ] ; then
        echo "Failed to copy /$SUITCASE_JARNAME from $cheat to $target. Non-fatal error, node's probably toast."
        return 1
    fi
    return 0
    
}


pushOidToClient() {
   echo -n "oidFilePush:$target "
    runCommand "scp -o StrictHostKeyChecking=no  root@hcb100:/oidfile /"
    if [ $? -ne 0 ] ; then
        echo "Failed to copy $target/oidfile from $cheat to $target, exiting."
        exit 1
    fi

}

pushOidFile() {
    #
    # push oidFile to cheat node
    #
    scp -o StrictHostKeyChecking=no  -i $SSHKEYFILE $OIDFILE root@$cheat:/oidfile 
    if [ $? -ne 0 ] ; then
        echo "Failed to copy $SUITCASE to root@$cheat:/, exiting."
        exit 1
    fi


    clientStartNumber=$origStart
    while test $clientStartNumber -le $clientEndNumber; do
        node=$clientStartNumber    

        if [ $node -le 9 ] ; then             
            source=hcb10$node
        else
            source=hcb1$node
        fi
        target=$source

        excludeNode $source
        exclude=$?
        if [ $exclude -eq 1 ] ; then
            pidArray[$node]=0
        else 
            pushOidToClient &
            sleep 1
        fi
        clientStartNumber=`expr $clientStartNumber + 1`
    done
    clientStartNumber=$origStart


}


printUsage() {
    echo "autoharvest.sh [-i find args] [-r date] [-u path to $SUITCASE_JARNAME] [-d oidFile] cheat-node"
    echo "Creates a local file called \"allfrags\" that contains all the fragment info"
    echo "If the -b argument is used, the contents of an optional development directory are copied to"
    echo "the ramdisk on the target nodes at /opt/test/cur." 
    echo "   -u <path> specifies the path to the suitcase jar file. It'll be replaced if it's missing."
    echo "   -r find files only more recent than [date] in the form \"2005-09-16 11:19:38\" (quotes required)"
    echo "   -d <file> path to a file with one OID per line. Only fragments matching these OIDs will be harvested"
    echo "   -n no updating or checking of preconditions (including jar files)"
    echo "   -o Fetch only - only gathers fragment files (doesn't run harvest binaries on target nodes)"
    echo "   -i only operate on files that meet this \"find\" clause. eg: -i \"-name *foo*\""
    echo "   -e older than [date] in the form \"2005-09-16 11:19:38\" (quotes required)- mostly for debugging"
}



setupNodeArray() {
    clientStartNumber=$origStart
    while test $clientStartNumber -le $clientEndNumber; do
        node=$clientStartNumber    

        if [ $node -le 9 ] ; then             
            source=hcb10$node
        else
            source=hcb1$node
        fi
        target=$source

        excludeNode $source
        exclude=$?

        if [ $exclude -eq 1 ] ; then
            nodeArray[$node]=0
        else 
            sleep 1
            ssh $cheat -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "ping $source 1 2>&1 /dev/null" 2>&1 /dev/null &
            nodeArray[$node]=$!
        fi
        clientStartNumber=`expr $clientStartNumber + 1`
        clientcount=`expr $clientcount + 1`
    done
    clientStartNumber=$origStart
    

    i=$origStart
    while test $i -le $clientEndNumber; do
        if [ "${nodeArray[$i]}" -ne 0 ] ; then 
            wait ${nodeArray[$i]} > /dev/null 2>&1
            retcode=$?

            if [ $i -le 9 ]; then                 
                source=hcb10$i                    
            else
                source=hcb1$i                    
            fi

            if [ $retcode -ne 0 ] ; then
                #
                # push didn't work; add this node to the exclude list
                #
                excludeArray[$EXCLUDECOUNT]=$source
                EXCLUDECOUNT=`expr $EXCLUDECOUNT + 1`        
            fi
        fi
        i=`expr $i + 1`
    done

}


while getopts "u:t:x:d:e:r:i:fonl" opt; do
    case $opt in
        u ) SUITCASE=$OPTARG
            if [ ! -f "$SUITCASE" ] ; then 
                echo "suitcase: No file at $SUITCASE, aborting."
                exit 1
            fi
            ;;
        d ) OIDFILE=$OPTARG
            if [ ! -f "$OIDFILE" ] ; then 
                echo "No file at $OIDFILE, aborting."
                exit 1
            fi
            ;;
        r ) echo "newer than $OPTARG"
            NEWERTHAN=$OPTARG
            ;;
        e ) echo "older than $OPTARG"
            OLDERTHAN=$OPTARG
            ;;
        o ) echo "Fetch only running - no fragment gathering will be done."
            FETCHONLY=true;            
            ;;

        n ) echo "No preconditions checked or enforced"
            NOPUSH=true; 
            if [ ! -z $SUITCASE ] ; then
                echo "Can't not check preconditions AND force a push. remove -n or -u"
                exit 1
            fi                      
            ;;
        i ) echo "Find parameter: $OPTARG"
            FIND=$OPTARG
            ;;

        \? )  printUsage
        exit 1
    esac
done
shift $(($OPTIND -1 ))

#
# check for existance of /homecomb-utests.jar
# push if required
#

if [ -z "$1" ] ; then 
    echo "Specify cheat node."
    echo
    printUsage
    exit 1
fi
cheat=$1
clientEndNumber=16
clientStartNumber=1
origStart=$clientStartNumber
clientcount=0

if [ ! -z "$OLDERTHAN" ]; then
    STARTTIME=$OLDERTHAN
else
    STARTTIME=`date +"%Y-%m-%d %H:%M:%S"`
fi
echo -n "$STARTTIME," > startEndTimes

setupNodeArray

#
# push the OID file
#
if [ ! -z "$OIDFILE" ] ; then
    pushOidFile
fi


#
# Add a check for this line in
# 

if [ -z $NOPUSH ] ; then 
    echo -n "checking preconditions... "
    if [ ! -z $SUITCASE ] ; then
        echo running push to server
        pushToServer
    fi
#    echo
#    echo JOEDIAG cur \"$cur\" clienmeEnd \"$clientEndNumber\" 1 \"$1\" 2 \"$2\" 3 \"$3\"
#    echo
    cur=$origStart        
    while test $cur -le $clientEndNumber; do
        node=$cur
        if [ $node -le 9 ] ; then             
            source=hcb10$node
        else
            source=hcb1$node
        fi
        target=$source

        excludeNode $source
        exclude=$?
        if [ $exclude -eq 1 ] ; then
            pidArray[$node]=0
        else 
            echo -n "$node.. "
            sleep 1
            if [ ! -z "$SUITCASE" ] ; then 
                pushToClient &
                pidArray[$cur]=$!

            else 
                checkUtils &
                pidArray[$cur]=$!
            fi
        fi
        cur=`expr $cur + 1`
    done
    echo
    i=$origStart
    while test $i -le $clientEndNumber; do
        if [ "${pidArray[$i]}" -ne 0 ] ; then 
            wait ${pidArray[$i]} > /dev/null 2>&1
            retcode=$?

            if [ $i -le 9 ]; then                 
                source=hcb10$i                    
            else
                source=hcb1$i                    
            fi

            if [ $retcode -ne 0 ] ; then
                #
                # push didn't work; add this node to the exclude list
                #
                excludeArray[$EXCLUDECOUNT]=$source
                EXCLUDECOUNT=`expr $EXCLUDECOUNT + 1`        
            fi
        fi
        i=`expr $i + 1`
    done
fi

calcNodeName() {
    node=$clientStartNumber    
    if [ $node -lt 10 ]; then             
        source=hcb10$node
        ipnum=10$node
    else
        source=hcb1$node
        ipnum=1$node
    fi
    target=$source 
}

#
# MAIN
#

LIBRARYPATHARGS="-Djava.library.path=/opt/honeycomb/lib/"
CLASSPATHARGS="-classpath /opt/honeycomb/lib/db-4.2.jar:/opt/honeycomb/lib/jetty-4.2.20.jar:$SUITCASE_JARPATH/$SUITCASE_JARNAME:/opt/honeycomb/lib/honeycomb.jar:/opt/honeycomb/lib/honeycomb-server.jar:/opt/honeycomb/lib/honeycomb-common.jar:/opt/honeycomb/lib/jug.jar"
RUNHARVEST="java $CLASSPATHARGS $LIBRARYPATHARGS com.sun.honeycomb.layout.FragExplorer"
RUNDBDUMP="java $CLASSPATHARGS $LIBRARYPATHARGS com.sun.honeycomb.suitcase.DumpMetadata"
RUNLAYOUTDUMP="java $CLASSPATHARGS $LIBRARYPATHARGS com.sun.honeycomb.layout.PrintLayout"
RUNSTATICDUMP="java $CLASSPATHARGS $LIBRARYPATHARGS com.sun.honeycomb.suitcase.ClusterParameterExtractor"

CLUSTER=`echo $cheat | sed -e 's/\(.*\)-cheat/\1/'`;
#
# Execute the harvest binaries
#

if [ -z $FETCHONLY ] ; then 
    date
    echo "starting fragment and system metadata harvest: "
    clientStartNumber=$origStart
    
    #psql -U $CLUSTER -h hc-dev3 -d $CLUSTER -c "insert into imports values ('$STARTTIME','$STARTTIME')" > /dev/null;
    
    while test $clientStartNumber -le $clientEndNumber; do
        calcNodeName
        #
        # Node name returned in $source
        #
        excludeNode $source
        exclude=$?

        for disk in 0 1 2 3 ; do
            
            let pid_i=$[4*($node-1)+($disk)]
            
            if [ $exclude -eq 1 ] ; then
                frag_pid[$pid_i]=0
            else
                echo -n "$source:$disk "
                if [ ! -z $OIDFILE ]; then 
                    OIDARG="-o /oidfile"
                fi
                if [ ! -z "$NEWERTHAN" ]; then 
                    NEWERTHANARG="-n $NEWERTHAN"
                fi
                if [ ! -z "$OLDERTHAN" ]; then 
                    OLDERTHANARG="-e $OLDERTHAN"
                fi
                
                sleep 1
                echo "ssh $cheat -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root \"ssh $target -o StrictHostKeyChecking=no \\\"$RUNHARVEST $NEWERTHANARG -d $STARTTIME -c $OIDARG $OLDERTHANARG $node /netdisks/10.123.45.$ipnum/data/$disk/ 2> /fragments.$ipnum-$disk.err 1> /fragments.$ipnum-$disk.out; echo $? >> /fragments.$ipnum-$disk.err\\\"\" > .autoharvest.frags.$ipnum.$disk.err 2>&1 &"
                #ssh $cheat -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "ssh $target -o StrictHostKeyChecking=no \"$RUNHARVEST $NEWERTHANARG -d $STARTTIME -c $OIDARG $OLDERTHANARG $node /netdisks/10.123.45.$ipnum/data/$disk/ 2> /fragments.$ipnum-$disk.err 1> /fragments.$ipnum-$disk.out; echo $? >> /fragments.$ipnum-$disk.err\"" > .autoharvest.frags.$ipnum.$disk.err 2>&1 &
                exit;
                pid=$!;
                frag_pid[$pid_i]=$pid
                echo "frag_pid[node($clientStartNumber) disk($disk)] = $pid"
            fi

        done
        
        sleep 1
        ssh $cheat -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "ssh $target -o StrictHostKeyChecking=no \"$RUNDBDUMP -d $STARTTIME $NEWERTHANARG $OLDERTHANARG $OIDARG /netdisks/10.123.45.$ipnum/data $node 1> /system_metadata.$ipnum.out 2> /system_metadata.$ipnum.err; echo $? >> /system_metadata.$ipnum.err\"" > .autoharvest.sysmd.$ipnum.err 2>&1 &
        sysmd_pid[$clientStartNumber]=$!

        clientStartNumber=`expr $clientStartNumber + 1`
    done

    echo
    RMCOMMAND="rm -rf /layouts"
    clientStartNumber=$origStart
    calcNodeName
    echo "Running layout harvest on $target..."
    LAYOUTNODE=$target # first node, usually hcb101 if it's not exlcuded

    #
    # Layout dump extraction
    #
    runCommand "$RMCOMMAND; $RUNLAYOUTDUMP -a -c -d $STARTTIME > /layouts 2> /layoutDumpErrors" 
    ssh $cheat -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "scp $target:/layouts /"
    #
    # statics extraction - FIXME
    #
    echo "Running statics extraction on $target..."
    RMCOMMAND="rm -rf /statics"
    runCommand "$RMCOMMAND; $RUNSTATICDUMP > /statics 2> /staticsDumpErrors" 
    ssh $cheat -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "scp $target:/statics /"

    #
    # Above two processes are serial, and run sequentially while the harvest processes
    # run in the background. The next secition finishes off the harvest.
    #
    echo "waiting for frag harvests..."
    echo "(to check in on progress, see hcbXXX:/fragments.<node>-<disk>.err on the cluster nodes)"
    i=$origStart
    while test $i -le $clientcount; do
        for disk in 0 1 2 3; do
            let pid_i=$[4*($i-1)+($disk)]
            let pid=${frag_pid[$pid_i]}
            if [ $pid -ne 0 ] ; then 
                wait ${frag_pid[$pid_i]} > /dev/null 2>&1
                wait_rc=$?
                if [ $wait_rc -ne 0 ] ; then
                    fragments_status[$array_i]="error"
                    echo "error: during harvest of hcb$[100+$i]:/fragments.$ipnum-$disk.out"
                    echo "error: return code [$wait_rc]"
                fi
                retcode=`ssh $cheat -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "ssh $target -o StrictHostKeyChecking=no \"tail -1 /fragments.$ipnum-$disk.err\""`
                if [ $retcode -ne 0 ] ; then
                    echo "Failed to harvest fragment data from node $i, disk $disk. retcode: $retcode. Aborting."
                    exit 1
                fi
            else
                echo -n " skipping[$i:$disk].."
            fi
        done
        i=`expr $i + 1`
    done

    echo -n "waiting for system metadata harvests: "
    i=$origStart
    while test $i -le $clientcount; do
        wait ${sysmd_pid[$i]} > /dev/null 2>&1
        retcode=`ssh $cheat -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "ssh $target -o StrictHostKeyChecking=no \"tail -1 /system_metadata.$ipnum.err\""`
        if [ $retcode -ne 0 ] ; then
            echo "Failed to harvest system metadata from node $i. retcode: $retcode. Aborting."
            exit 1
        fi
        i=`expr $i + 1`
    done
fi

date +"%Y-%m-%d %H:%M:%S" >> startEndTimes
echo "autoharvest done."

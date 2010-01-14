#!/bin/bash
# $Id: snapshot.sh 10857 2007-05-19 03:01:32Z bberndt $
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

if [ "$1" = "" ] || [ "$1" == "-h" ] || [ "$1" == "-help" ] || [ "$1" == "--help" ]
then
	echo "HC Snapshot Tool"
	echo "----------------"
	echo "Usage: snapshot mode clustername [snapshotname] [snapshottype] [node] [disk] [map]"
	echo "       mode         : save, restore, list, delete, deletedata"
        echo "                      restore is not possible from a live snapshot, only from result of copy or move"
	echo "       clustername  : name of the cluster (ex. dev318) used to snapshot/restore its audit DB"
        echo "                      use keyword (nodb) if you do not want to do anything to the audit DB"
	echo "       snapshotname : name to give the snapshot for save, restore or delete"
	echo "       snapshottype : type of snapshot activity: copy,move or live, default is move"
	echo "                      live will checkpoint existing data without copy, and skip hadb and system metadata caches"
	echo "       node         : limit snapshot to this node, give 3-digit node id (ex. 107), default is all nodes"
	echo "       disk         : limit snapshot to this disk, give 1-digit disk id (ex. 2), default is all disks"
	echo "       map          : limit snapshot to this layout map, give 4-digit map id (ex. 0234), default is all maps"
	echo ""
	exit 0
fi

MODE=$1
CLUSTER_NAME=$2
SNAPSHOT=$3
TYPE=$4
MYNODE=$5
MYDISK=$6
MYMAP=$7

# Some lookups are done on node 101, disk 0 by default, unless user specified disk/node id
if [ "$MYNODE" != "" ]; then
    N=$MYNODE
else
    N=101
fi
if [ "$MYDISK" != "" ]; then
    D=$MYDISK
else
    D=0
fi

# Parse layout map ID into 2-level dir names
if [ "$MYMAP" != "" ]; then
    # pad map ID with zeros: 23 -> 0023
    while [ `echo -n $MYMAP |wc -c |tr -d ' '` -lt 4 ]; do
        MYMAP=0$MYMAP
    done
    HIDIR=`echo $MYMAP | cut -c 1-2`
    LODIR=`echo $MYMAP | cut -c 3-4`
else 
    DIR=`echo {0..9}{0..9}`
    HIDIR=$DIR
    LODIR=$DIR
fi

# Statics
DISK_IDS=( "c0t0" "c0t1" "c1t0" "c1t1" )
AUDIT_HOST=10.7.228.25 # hc-dev3
SNAP_RE="^[a-zA-Z_0-9]*$"

if [ "$MODE" != "save" ] && [ "$MODE" != "restore" ] && [ "$MODE" != "list" ] && [ "$MODE" != "delete" ] && [ "$MODE" != "deletedata" ]
then
	echo "Bad MODE: $MODE only 'save','restore','list','delete','deletedata' accepted."
	exit -1	
fi

if [ "$TYPE" != "" ] && [ "$TYPE" != "copy" ] && [ "$TYPE" != "move" ] && [ "$TYPE" != "live" ]
then
	echo "Bad TYPE: $TYPE only 'copy', 'move' and 'live' accepted."
	exit -1
fi

if [ "$TYPE" == "" ]; then
    TYPE=move    # default
fi

if [ "$TYPE" == "copy" ]; then
	echo "Using copy mode instead of moving, this may take a while..."
fi

if [ "$SNAPSHOT" == "" ] 
then
	if [ "$MODE" != "list" ] && [ "$MODE" != "deletedata" ]
	then
		echo "Please specify a snapshot name."
		exit -1
	fi
fi

if [ "$MYDISK" != "" ]; then
    if [ "$MYDISK" != "0" ] && [ "$MYDISK" != "1" ] && [ "$MYDISK" != "2" ] && [ "$MYDISK" != 3 ]
    then
        echo "Invalid disk ID [$MYDISK], select from 0, 1, 2 3."
        exit -1
    fi
    DISKS=$MYDISK
else
    DISKS="0 1 2 3"
fi

# verify snapshot name is a valid name for filesystem and db usage
echo $SNAPSHOT | grep "$SNAP_RE" > /dev/null 2>&1

if [ $? != 0 ]
then
	echo "Snapshot name must contain only letters, numbers and underscores. Regular expression to match: $SNAP_RE"
	exit -1
fi

initNodes() {
	ALLNODES=""
	NUM_NODES=0
        MYNODE_OK=0
	echo -n "Calculating nodes in ring..."
        # Figure out which nodes are alive via ping with 1-second timeout
	for NODE in {101..116}
	do
		echo -n "."
		ping -c 1 hcb$NODE 1 > /dev/null 2>&1
		if [ $? == 0 ]; then 
                    NODE_OK=1
                    ALLNODES="$ALLNODES $NODE"
                    NUM_NODES=`expr $NUM_NODES + 1`
                else
                    NODE_OK=0
		fi

                if [ "$NODE" == "$MYNODE" ]; then
                    MYNODE_OK=$NODE_OK
                fi
	done
	echo "[$ALLNODES]. done."

        if [ "$MYNODE" != "" ]; then
            ALLNODES=$MYNODE
        fi
}

abortUnlessYes() {
    read ANSWER
    if [ "$ANSWER" != "y" ]
	then
        echo "Aborted by user."
        exit -1
    fi
}

confirmState() {
	echo "You have $NUM_NODES nodes and $NUM_DISKS mounted disks, is this correct? (y/n) "
        abortUnlessYes
        
        if [ "$MYNODE" != "" ]; then
            echo -n "You selected node $MYNODE, its state is "
            if [ "$MYNODE_OK" == "1" ]; then
                echo -n "online, "
            else
                echo -n "offline, "
            fi
            echo "is this correct? (y/n) "
            abortUnlessYes
        fi

        if [ "$MYDISK" != "" ]; then
            echo -n "You selected disk $MYDISK, its state is "
            if [ "$MYDISK_OK" == "1" ]; then
                echo -n "mounted, "
            else
                echo -n "not mounted, "
            fi
            echo "is this correct? (y/n) "
            abortUnlessYes
        fi

}

verifyHCIsDown() {
	if [ "$TYPE" == "live" ]
	then
		echo "Live checkpoint mode in use, will assume HC is running..."
		return 0
	fi

	for NODE in $ALLNODES
	do
		ssh hcb$NODE "ps -ef | grep java | grep -v grep" > /dev/null 2>&1
		if [ $? == 0 ]
		then
			echo "There are java processes running on node hcb$NODE, please turn off honeycomb first."
			exit -1
		fi
	done
}

mountAllDisks() {
	if [ "$TYPE" == "live" ]
	then
		echo "Live checkpoint mode in use, will assume all disks are mounted."
		return 0
	fi
	
	echo -n "Mounting data slices [$DISKS] on nodes [$ALLNODES]."
	for NODE in $ALLNODES
	do
		ssh hcb$NODE "mkdir /data > /dev/null 2>&1"
		for DISK in $DISKS
		do	
		    #echo "/usr/sbin/mount -F ufs /dev/dsk/c${DISK}d0s4 /data/$DISK"
                    (ssh hcb$NODE "mkdir /data/$DISK ; /usr/sbin/mount -F ufs /dev/dsk/${DISK_IDS[$DISK]}d0s4 /data/$DISK" > /dev/null 2>&1 ; echo -n ".") &
		done
	done
	wait
	echo ". done."
}

initDisks() {

	NUM_DISKS=0
	echo -n "Verifying number of disks mounted..."
	for NODE in $ALLNODES
	do
		ssh hcb$NODE "mkdir /data > /dev/null 2>&1"
		for DISK in $DISKS
		do
			echo -n "."
			ssh hcb$NODE "mount | grep ^/data/$DISK" > /dev/null 2>&1
			if [ $? == 0 ] 
			then
                            DISK_OK=1
                            NUM_DISKS=`expr $NUM_DISKS + 1`
                        else
                            DISK_OK=0
                        fi
                        
                        if [ "$NODE" == "$MYNODE" ]; then
                            if [ "$DISK" == "$MYDISK" ]; then
                                MYDISK_OK=$DISK_OK
                            fi
                        fi

		done
	done
	echo ". done"
}

verifySnapshot() {
	for NODE in $ALLNODES
	do
		for DISK in $DISKS
		do
			if [ "$2" == "true" ]
			then
				ssh hcb$NODE "ls /data/$DISK/.snapshots/$1" > /dev/null 2>&1
				if [ $? != 0 ]
				then
					echo "WARNING: Snapshot does not exist on hcb$NODE disk $DISK with that name."
				fi
                                # TODO: Check that snapshot is of restorable type (not result of live checkpoint)
			else
				ssh hcb$NODE "ls /data/$DISK/.snapshots/$1" > /dev/null 2>&1
				if [ $? == 0 ]
				then
					echo "Snapshot exists on hcb$NODE disk $DISK with that name."
					exit -1
				fi
			fi
		done
	done
}

createDirectories() {
	echo -n "Initializing snapshot directories."
	for NODE in $ALLNODES
	do
		for DISK in $DISKS
		do
			(ssh hcb$NODE "mkdir /data/$DISK/.snapshots" > /dev/null 2>&1 ; echo -n ".") &
		done
	done
	wait
	echo ". done."
}

init() {
	initDB
	initNodes
	verifyHCIsDown	
	mountAllDisks
	initDisks
	confirmState
	createDirectories
}

calcTimeForCopy() {
    MYSNAPSHOT=$1 # empty if we calculate to save a snapshot, set on restore

    if [ "$TYPE" == "move" ] || [ "$TYPE" == "live" ]; then
        # skip calculation, move is instantaneous, live checkpoint is just an ls
        return
    fi

    # use DATASPACE from disk D of node N (default is 101:0)    
    OCCUPIEDSPACE=`ssh hcb$N "df -k /data/$D" | grep data | awk '{print $3}'`
    SNAPSHOTSPACE=`ssh hcb$N "du -sk /data/$D/.snapshots/$MYSNAPSHOT" | awk '{print $1}'`
    DATASPACE=`expr $OCCUPIEDSPACE - $SNAPSHOTSPACE`


    # 20 (MB / s) =  1228800 / minute
    if [ "$MODE" == "save" ]
    then 
        TIMETOCOPY=`expr $DATASPACE / 1228800`
    else # restore 
        TIMETOCOPY=`expr $SNAPSHOTSPACE / 1228800`
    fi
    if [ "$TIMETOCOPY" == "0" ]; then
        TIMETOCOPY=1
    fi

    echo "Copying will take around $TIMETOCOPY minutes."
    echo "Would you like to continue ? (y/n) "
    abortUnlessYes
    return
}

verifySpaceForCopy() {
    MYSNAPSHOT=$1 # empty if we verify before saving snapshot, set on restore

    if [ "$TYPE" == "move" ] || [ "$TYPE" == "live" ]; then
        # skip verification, move doesn't take any space and live checkpoint just needs space for ls
        return
    fi

    echo -n "Verifying disk space for copying snapshot..."
    for NODE in $ALLNODES
      do
      for DISK in $DISKS
        do
	# space occupied in kilobytes
        ( 
            local FREESPACE=`ssh hcb$NODE "df -k /data/$DISK | grep dev " | awk '{print $4}'`
            local OCCUPIEDSPACE=`ssh hcb$NODE "df -k /data/$DISK" | grep data | awk '{print $3}'`
            local SNAPSHOTSPACE=`ssh hcb$NODE "du -sk /data/$DISK/.snapshots/$MYSNAPSHOT" | awk '{print $1}'`
            local DATASPACE=`expr $OCCUPIEDSPACE - $SNAPSHOTSPACE`
            
            if [ "$MODE" == "save" ] && [ "$DATASPACE" -gt "$FREESPACE" ]
            then
                echo "Insufficient space on hcb$NODE disk $DISK to save snapshot: data space $DATASPACE kb, free $FREESPACE kb."
                exit -1
            fi
            
            if [ "$MODE" == "restore" ] && [ "$SNAPSHOTSPACE" -gt "$FREESPACE" ]
            then
                echo "Insufficient space on hcb$NODE disk $DISK to save snapshot: data space $DATASPACE kb, free $FREESPACE kb."
                exit -1
            fi
            
            echo -n "."
        ) &
      done
    done
    wait
    echo ". done"
}

initDB() {
    if [ "$CLUSTER_NAME" == "nodb" ]; then
        SNAPSHOTDB=""
        return
    else
        SNAPSHOTDB="${CLUSTER_NAME}_$SNAPSHOT"
    fi
    
    ssh postgres@$AUDIT_HOST "psql -U system -c '' " > /dev/null 2>&1
    if [ $? != 0 ] 
	then
        echo "Audit database manager does not have database and user system. please correct this."
        echo "To setup the user and db login as postgres user and use the commands createuser and createdb."	
        echo "Would you like me to continue otherwise, without audit db snapshotting? (y/n) "
        abortUnlessYes
    fi
    
    ping $AUDIT_HOST > /dev/null 2>&1
    if [ $? != 0 ] 
	then
        echo "Audit host unavailable, would you like to continue without audit db snapshotting? (y/n) "
        abortUnlessYes
    fi
}

saveConfig() {
    # TODO: Use /config instead of /data/0
    
    if [ "$MYNODE" != "" ]; then
        return
    fi
    
    NODE=$1
    ssh hcb$NODE "mkdir /data/0/.snapshots/$SNAPSHOT" > /dev/null 2>&1
    ssh hcb$NODE "tar cvf /data/0/.snapshots/$SNAPSHOT/.config.tar /config > /dev/null"
    if [ $? != 0 ]
        then
        echo "Problem saving config on node $NODE... Quiting."
        exit -1
    fi
}

restoreConfig() {
    # TODO: Use /config instead of /data/0

    if [ "$MYNODE" != "" ]; then
        return
    fi

    NODE=$1
    ssh hcb$NODE "rm -rf /config/*; tar xvf /data/0/.snapshots/$SNAPSHOT/.config.tar -c /config > /dev/null"
    if [ $? != 0 ]
        then
        echo "Problem restoring config on node $NODE... Quiting."
        exit -1
    fi
}

dropCurrentAuditDB() {
    echo "Recreating auditdb on hc-dev3."
    ssh postgres@$AUDIT_HOST "./dbscript.sh -r -c $CLUSTER_NAME" > /dev/null 2>&1 
}

dropSnapshotAuditDB() {
    # if database exists
    ssh postgres@$AUDIT_HOST "psql -U system -l | grep $SNAPSHOTDB" > /dev/null 2>&1 
    if [ $? == 0 ] 
        then
        echo "Dropping snapshot on hc-dev3."
        ssh postgres@$AUDIT_HOST "dropdb -U system $SNAPSHOTDB" > /dev/null 2>&1 
        ssh postgres@$AUDIT_HOST "dropuser -U system $SNAPSHOTDB" > /dev/null 2>&1 
    fi
}

saveCopyAuditDB() {
    echo "Copying auditdb for $CLUSTER_NAME to $SNAPSHOTDB."
    # if database exists
    ssh postgres@$AUDIT_HOST "psql -U system $CLUSTERNAME -c ''" > /dev/null 2>&1
    if [ $? == 0 ]; then 
        ssh postgres@$AUDIT_HOST "dropdb -U system $SNAPSHOTDB ; createdb -U $CLUSTER_NAME $SNAPSHOTDB" > /dev/null 2>&1
        ssh postgres@$AUDIT_HOST "pg_dump -Ft -U $CLUSTER_NAME $CLUSTER_NAME > $SNAPSHOTDB.$CLUSTER_NAME.tar" > /dev/null
        ssh postgres@$AUDIT_HOST "pg_restore -U $CLUSTER_NAME -d $SNAPSHOTDB $SNAPSHOTDB.$CLUSTER_NAME.tar" > /dev/null
        ssh postgres@$AUDIT_HOST "rm -f $SNAPSHOTDB.$CLUSTER_NAME.tar" > /dev/null
    else
        echo "Audit DB not found for cluster $CLUSTER_NAME, cannot save snapshot."
    fi
}

saveMoveAuditDB() {
    echo "Moving auditdb for $CLUSTER_NAME to $SNAPSHOTDB."
    # if database exists
    ssh postgres@$AUDIT_HOST "psql -U system $CLUSTERNAME -c ''" > /dev/null 2>&1
    if [ $? == 0 ]; then 
        ssh postgres@$AUDIT_HOST "psql -U $CLUSTER_NAME -c \"ALTER DATABASE $CLUSTER_NAME RENAME TO $SNAPSHOTDB\"" > /dev/null
        echo "Recreating empty audit db for $CLUSTER_NAME on hc-dev3"
        ssh postgres@$AUDIT_HOST "~/dbscript.sh -c $CLUSTER_NAME -r" > /dev/null 2>&1
    else
        echo "Audit DB not found for cluster $CLUSTER_NAME, cannot save snapshot."
    fi
}

restoreCopyAuditDB() {
    echo "Copying auditdb from $SNAPSHOTDB to $CLUSTER_NAME."
    # if database exists
    ssh postgres@$AUDIT_HOST "psql -U system $SNAPSHOTDB -c ''" > /dev/null 2>&1 
    if [ $? == 0 ]; then 
        ssh postgres@$AUDIT_HOST "dropdb -U system $CLUSTER_NAME ; createdb -U $CLUSTER_NAME $CLUSTER_NAME" > /dev/null 2>&1
        ssh postgres@$AUDIT_HOST "pg_dump -Ft -U system $SNAPSHOTDB > $SNAPSHOTDB.$CLUSTER_NAME.tar" > /dev/null
        ssh postgres@$AUDIT_HOST "pg_restore -U $CLUSTER_NAME -d $CLUSTER_NAME $SNAPSHOTDB.$CLUSTER_NAME.tar" > /dev/null
        ssh postgres@$AUDIT_HOST "rm -f $SNAPSHOTDB.$CLUSTER_NAME.tar" > /dev/null
    else
	echo "Audit db not found for snapshot $SNAPSHOTDB, cannot restore from snapshot."
    fi
}

restoreMoveAuditDB() {
    echo "Restoring database on hc-dev3 from $SNAPSHOTDB."
    # if database exists
    ssh postgres@$AUDIT_HOST "psql -U system $SNAPSHOTDB -c ''" > /dev/null 2>&1 
    if [ $? == 0 ]; then 
        ssh postgres@$AUDIT_HOST "dropdb -U system $CLUSTER_NAME" > /dev/null 2>&1
        ssh postgres@$AUDIT_HOST "psql -U system -c \"ALTER DATABASE $SNAPSHOTDB RENAME TO $CLUSTER_NAME\"" > /dev/null
        ssh postgres@$AUDIT_HOST "psql -U system -c \"ALTER DATABASE $CLUSTER_NAME OWNER TO $CLUSTER_NAME\"" > /dev/null
    else
        echo "Audit db not found for snapshot $SNAPSHOTDB, cannot restore from snapshot."
    fi
}

if [ "$MODE" == "deletedata" ]
then
	init

	echo "Are you sure you want to delete data ? (y/n) "
	abortUnlessYes

        echo -n "Deleting data from disks [$DISKS] on nodes [$ALLNODES] ..."
        
        for NODE in $ALLNODES
          do
          for DISK in $DISKS
            do
            (ssh hcb$NODE "rm -fr /data/$DISK/*" > /dev/null 2>&1; echo -n ".") &
          done
        done
        
        wait
        echo ". done."

        if [ "$SNAPSHOTDB" != "" ]; then
            dropCurrentAuditDB
        fi
fi

if [ "$MODE" == "delete" ]
then
	initDB
	initNodes
	mountAllDisks
	initDisks
	confirmState

	echo "Are you sure you want to delete snapshot: $SNAPSHOT ? (y/n) "
	abortUnlessYes

        echo -n "Deleting Snapshot: $SNAPSHOT from disks [$DISKS] on nodes [$ALLNODES] ..."
        
        for NODE in $ALLNODES
          do
          for DISK in $DISKS
            do
            echo -n "."
            ssh hcb$NODE "rm -fr /data/$DISK/.snapshots/$SNAPSHOT" > /dev/null 2>&1 &
          done
        done
        wait
        echo ". done."
        
        if [ "$SNAPSHOTDB" != "" ]; then
            dropSnapshotAuditDB 
        fi
fi

if [ "$MODE" == "list" ]
then
	ssh hcb$N "/usr/sbin/mount -F ufs /dev/dsk/c${D}d0s4 /data/$D" > /dev/null 2>&1
	echo "Available Snapshots (listed from node $N disk $D):"
	SNAPSHOTS=`ssh hcb$N "ls /data/${D}/.snapshots/ 2> /dev/null" | awk '{print "* ",$1}'`
	if [ "$SNAPSHOTS" == "" ]
	then
		echo "None."
		exit 0	
	else
		echo "$SNAPSHOTS"
	fi
fi

if [ "$MODE" == "save" ]
then
	init
	verifySnapshot "$SNAPSHOT" "false"
	verifySpaceForCopy
	calcTimeForCopy
	echo -n "Taking Snapshot of $TYPE type: $SNAPSHOT"

        if [ "$MYMAP" != "" ]; then
            echo -n " on layout map ID $MYMAP ..."
        else
            echo -n " on all layout maps 00/00 - 99/99 ..."
        fi

	for NODE in $ALLNODES
	do
                saveConfig $NODE
          
		for DISK in $DISKS 
		do
			ssh hcb$NODE "mkdir /data/$DISK/.snapshots/$SNAPSHOT" > /dev/null 2>&1
			#echo "Snapshot $SNAPSHOT on node $NODE disk $DISK"
			echo -n "."

			# save ls listing for this mapid
			SRC="/data/$DISK/"
			DST="/data/$DISK/.snapshots/$SNAPSHOT/"

			ssh hcb$NODE "for DIR1 in $HIDIR ; do for DIR2 in $LODIR; do ls $SRC/\$DIR1/\$DIR2 > $DST/.ls.\$DIR1.\$DIR2; done; done"  > /dev/null 2>&1 &

                        # for copy or move, put data into the snapshot
			if [ "$TYPE" == "copy" ]
                        then
                            if [ "$MYMAP" != "" ]; then
                                ssh hcb$NODE "mkdir -p /data/$DISK/.snapshots/$SNAPSHOT/$HIDIR/$LODIR"
                                FILES=`ssh hcb$NODE ls /data/$DISK/$HIDIR/$LODIR`
                                if [ "$FILES" != "" ]; then
                                    ssh hcb$NODE "cp -fr /data/$DISK/$HIDIR/$LODIR/* /data/$DISK/.snapshots/$SNAPSHOT/$HIDIR/$LODIR/" &
                                fi
                            else
                                ssh hcb$NODE "cp -fr /data/$DISK/* /data/$DISK/.snapshots/$SNAPSHOT" &
                            fi
			elif [ "$TYPE" == "move" ]
                        then
                            if [ "$MYMAP" != "" ]; then
                                ssh hcb$NODE "mkdir -p /data/$DISK/.snapshots/$SNAPSHOT/$HIDIR/$LODIR"
                                FILES=`ssh hcb$NODE ls /data/$DISK/$HIDIR/$LODIR`
                                if [ "$FILES" != "" ]; then
                                    ssh hcb$NODE "mv /data/$DISK/$HIDIR/$LODIR/* /data/$DISK/.snapshots/$SNAPSHOT/$HIDIR/$LODIR/" &
                                fi
                            else
                                ssh hcb$NODE "mv /data/$DISK/* /data/$DISK/.snapshots/$SNAPSHOT" &
                            fi
			else
                            echo "..."
                            # For live checkpoint, not saving actual data in the snapshot, only .ls
                            # TODO: Identify the snapshot as "not restorable"
                        fi
		done
	done
	wait
	echo ". done."

	for NODE in $ALLNODES
	do
		for DISK in $DISKS 
		do
			ssh hcb$NODE "ls -l /data/$DISK/.snapshots/$SNAPSHOT/.ls.??.?? | awk '{if (\$5 == 0) {print \$9}}' | xargs rm -f" &
		done
		wait
	done

        if [ "$SNAPSHOTDB" != "" ]; then
            if [ "$TYPE" == "copy" ] || [ "$TYPE" == "live" ]; then
                saveCopyAuditDB
            else
                saveMoveAuditDB
            fi
        fi
fi

if [ "$MODE" == "restore" ]
then
	init
	verifySnapshot "$SNAPSHOT" "true"
        calcTimeForCopy
	verifySpaceForCopy $SNAPSHOT
	
	if [ "$TYPE" == "copy" ] || [ "$TYPE" == "move" ]
        then
		echo "Deleting current files in preparation for restore"
		for NODE in $ALLNODES
		do
			for DISK in $DISKS
			do
				# this doesn't delete the .snapshot subdirs themselves!
				ssh hcb$NODE "rm -rf /data/$DISK/*" &
			done
		done
		wait
	fi

        echo -n "Restoring Snapshot: $SNAPSHOT..."

	for NODE in $ALLNODES
	do
                restoreConfig $NODE

		for DISK in $DISKS
		do
			#echo "Restoring $SNAPSHOT on node hcb$NODE disk $DISK"
			echo -n "."
			if [ "$TYPE" == "copy" ]
                        then
                            if [ "$MYMAP" != "" ]; then
                                ssh hcb$NODE "mkdir -p /data/$DISK/$HIDIR/$LODIR"
                                FILES=`ssh hcb$NODE ls /data/$DISK/.snapshots/$SNAPSHOT/$HIDIR/$LODIR`
                                if [ "$FILES" != "" ]; then
                                    ssh hcb$NODE "cp -fr /data/$DISK/.snapshots/$SNAPSHOT/$HIDIR/$LODIR/* /data/$DISK/$HIDIR/$LODIR/" &
                                fi
                            else
				ssh hcb$NODE "cp -fr /data/$DISK/.snapshots/$SNAPSHOT/* /data/$DISK/" &
                            fi
			else	
                            if [ "$MYMAP" != "" ]; then
                                ssh hcb$NODE "mkdir -p /data/$DISK/$HIDIR/$LODIR"
                                FILES=`ssh hcb$NODE ls /data/$DISK/.snapshots/$SNAPSHOT/$HIDIR/$LODIR`
                                if [ "$FILES" != "" ]; then
                                    ssh hcb$NODE "mv /data/$DISK/.snapshots/$SNAPSHOT/$HIDIR/$LODIR/* /data/$DISK/$HIDIR/$LODIR/ ; rm -fr /data/$DISK/.snapshots/$SNAPSHOT" & 
                                fi
                            else
				ssh hcb$NODE "mv /data/$DISK/.snapshots/$SNAPSHOT/* /data/$DISK/ ; rm -fr /data/$DISK/.snapshots/$SNAPSHOT" &
                            fi
			fi
		done
	done
	wait
	echo ". done."

        if [ "$SNAPSHOTDB" != "" ]; then
            if [ "$TYPE" == "copy" ]; then
                restoreCopyAuditDB
            else
                restoreMoveAuditDB
            fi	
	fi
fi


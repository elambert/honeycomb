#!/bin/bash
#
# $Id: verify_snapshot.sh 10857 2007-05-19 03:01:32Z bberndt $
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
# This script can verify that all data on the current cluster 
# is in the right place and that it originated from a snapshot
# that may have been created in a different layout at the time
# This is usefull for both healing testing and sloshing testing.
#

if [ "$1" = "" ] || [ "$1" == "-h" ] || [ "$1" == "-help" ] || [ "$1" == "--help" ]
then
	echo "HC Snapshot Verification Tool"
	echo "----------------"
	echo "Usage: verify_snapshot snapshotname [node] [disk] [map]"
	echo "       snapshotname : snapshot name that contains the data that is in the current cluster."
        echo "       node         : limit verification to this node, give 3-digit node id (ex. 107), default is all nodes"
	echo "       disk         : limit verification to this disk, give 1-digit disk id (ex. 2), default is all disks"
	echo "       map          : limit verification to this layout map, give 4-digit map id (ex. 0234), default is all maps"
        echo "                      supports ?? syntax to specify a set of layout maps (ex. 02?? means to verify 0200-0299)"
        echo ""
	exit 0
fi

SNAPSHOT=$1
MYNODE=$2
MYDISK=$3
MYMAP=$4
START_DATE=`date +"%D %T"`

EVERYNODE=set_by_initNodes
EVERYDISK="0 1 2 3"

if [ "$MYMAP" != "" ]
then
    # pad map ID with zeros: 23 -> 0023
    while [ `echo -n $MYMAP |wc -c |tr -d ' '` -lt 4 ]; do
        MYMAP=0$MYMAP
    done
    MYMAPHI=`echo $MYMAP | cut -c 1-2`
    MYMAPLO=`echo $MYMAP | cut -c 3-4`
else
    MYMAPHI="??"
    MYMAPLO="??"
fi

if [ "$MYDISK" != "" ]; then
    DISKS=$MYDISK
else 
    DISKS="0 1 2 3"
fi

OUTDIR=`mktemp -d -p /var/adm snapshot.XXXXX`
CURRENT_LAYOUT=${OUTDIR}/layout
FRAGS_VERIFIED=${OUTDIR}/frags_good
FRAGS_FAILED=${OUTDIR}/frags_bad
OID_LISTING=${OUTDIR}/oids
LS_DIR=${OUTDIR}/lsdir
mkdir -p $LS_DIR
touch $CURRENT_LAYOUT $FRAGS_VERIFIED $FRAGS_FAILED $OID_LISTING

SLOWVER=0

if [ "$SNAPSHOT" == "" ] 
then
	echo "Please specify a snapshot name."
	exit -1
fi

initNodes() {
	ALLNODES=""
	NUM_NODES=0
	echo -n "Calculating nodes in ring..."
        # Figure out which nodes are alive via ping with 1-second timeout
	for NODE in {101..116}
	do
		echo -n "."
		ping -c 1 hcb$NODE 1 > /dev/null 2>&1
		if [ $? == 0 ] 
		then
			ALLNODES="$ALLNODES $NODE"
			NUM_NODES=`expr $NUM_NODES + 1`
		fi
	done
        echo "[$ALLNODES]. done."

        EVERYNODE="$ALLNODES"

        if [ "$MYNODE" != "" ]; then
            ALLNODES=$MYNODE
        fi
}

confirmState() {
    if [ "$MYMAP" != "" ]; then
        MAPS="map $MYMAP"
    else
        MAPS="all data"
    fi
    echo "You want to verify [$MAPS] on disks [$DISKS] on nodes [$ALLNODES], is this correct? (y/n) "
    read ANSWER
    if [ "$ANSWER" != "y" ]
	then
        echo "Aborting program."
        exit -1
    fi
}

verifyPackageIsInstalled() {
	echo -n "Verifying all nodes have $1 package installed."
	for NODE in $ALLNODES
	do
		ssh hcb$NODE "pkginfo $1" > /dev/null 2>&1
		if [ $? != 0 ]
		then
			echo "Package $1 not installed on node hcb$NODE."
			exit -1
		fi
		echo -n "."
	done
	echo " done."
}

createFullOIDListing() {
	echo -n "Creating full oid listing on cluster..."
	#for NODE in $ALLNODES

    # process each disk separately to not hit find's limit
    # 
    for DISK in $EVERYDISK
    do
        for NODE in $EVERYNODE
	do
		(ssh hcb$NODE "find /data/${DISK}/$MYMAPHI/$MYMAPLO/* | awk '{print ${NODE}\$1}'" >> $OID_LISTING.$NODE 2>> err.$NODE ; echo -n ".") &
	done
	wait
    done

	#for NODE in $ALLNODES
        for NODE in $EVERYNODE
	do
          # Check if find failed because of too many objects
          grep "Arg list too long" err.$NODE
          if [ $? -eq 0 ]; then
              SLOWVER=1
          else
              cat $OID_LISTING.$NODE >> $OID_LISTING 
              echo -n "."
          fi
          rm -f $OID_LISTING.$NODE
          rm -f err.$NODE
	done
	echo ". done."

        if [ $SLOWVER -eq 1 ]; then
            echo "Too many objects on the cluster, will verify in slow motion"
        fi
}

copyBackLS() {
	echo -n "Copying back LS listings to $LS_DIR"
	#for NODE in $ALLNODES
        for NODE in $EVERYNODE
	do
		#for DISK in $DISKS 
                for DISK in $EVERYDISK
		do
			mkdir $LS_DIR/$NODE > /dev/null 2>&1 
			mkdir $LS_DIR/$NODE/$DISK > /dev/null 2>&1 
			(scp hcb$NODE:/data/$DISK/.snapshots/$SNAPSHOT/.ls.$MYMAPHI.$MYMAPLO $LS_DIR/$NODE/$DISK > /dev/null 2>&1  ; echo -n ".") &
		done
	done
	wait
	echo ". done."
}

getLayout() {
    FIRST_NODE=`echo $ALLNODES | awk '{print $1}'`
    ssh hcb$FIRST_NODE "/opt/honeycomb/sbin/print_layout.sh all" > $CURRENT_LAYOUT
    if [ "$?" != "0" ]; then
        echo "Cannot access cluster layout. You  need to start Honeycomb before proceeding."
        exit -1
    fi
}


# Meat of the script
initNodes
confirmState
verifyPackageIsInstalled "SUNWhcwbcluster"
getLayout
createFullOIDListing
copyBackLS


verifyDisk() {
	local NODE=$1
	local DISK=$2
        local MAP=$3

	ls $LS_DIR/$NODE/$DISK/.ls.$MYMAPHI.$MYMAPLO > /dev/null 2>&1
	if [ $? == 0 ]
	then
                local LISTINGS=`ls $LS_DIR/$NODE/$DISK/.ls.$MYMAPHI.$MYMAPLO`
		for LS in $LISTINGS
		do
			local HIMAP=`echo $LS | awk -F'.' '{print $4}'`
			local LOMAP=`echo $LS | awk -F'.' '{print $5}'`
			local MAPID="${HIMAP}${LOMAP}"
                        
                        if [ "$MAP" != "" ] 
			then
				echo "$MAPID" | egrep $MAP > /dev/null 2>&1
				if  [ $? != 0 ]; then
                            		continue
				fi
                        fi

			local OIDS=`cat $LS`
			local FIRST_FRAG=`echo $OIDS | cut -d' ' -f 1`
			local FRAGID=`echo $FIRST_FRAG | awk -F'_' '{print $2}'`
			local NEW_FRAG_LOCATION=`cat $CURRENT_LAYOUT | grep "MapID: $MAPID" | grep "Frag: $FRAGID"`
			local FRAG_NODE=`echo $NEW_FRAG_LOCATION | awk '{print $5}' | awk -F':' '{print $1}'`
			local FRAG_DISK=`echo $NEW_FRAG_LOCATION | awk '{print $5}' | awk -F':' '{print $2}'`
		
			for OID in $OIDS
			do
                          if [ $SLOWVER -eq 1 ]; then
                              # slow verification: ssh to the cluster node to check for presence of each fragment
                              ssh hcb$FRAG_NODE ls /data/$FRAG_DISK/$HIMAP/$LOMAP/$OID > /dev/null 2>&1
                          else
                              # fast verification through ls file
                              grep $FRAG_NODE/data/$FRAG_DISK/$HIMAP/$LOMAP/$OID $OID_LISTING > /dev/null 2>&1
                          fi
                          if [ $? != 0 ]
                              then
                              # Echo dots to a file from multiple threads, so we can calculate total afterwards
                              echo -n "." >> $FRAGS_FAILED
                              echo -e "\nWarning: $OID on $NODE:$DISK was not found at $FRAG_NODE:$FRAG_DISK:$MAPID"
                          else
                              echo -n "." >> $FRAGS_VERIFIED
                          fi
			done
		done
        else
            # TODO: maybe we should fail if user asked for this specific node/disk/map and snapshot is not found?
            # 
            echo -e "\nWarning: No snapshots found on node $NODE disk $DISK (or ls failed)"
	fi
	echo "$NODE:$DISK done."
}

echo "Verifying fragments..."
for NODE in $ALLNODES
do
	for DISK in $DISKS 
	do
		verifyDisk $NODE $DISK $MYMAP &	
	done
done
wait

END_DATE=`date +"%D %T"`

NUM_FRAGS_VERIFIED=`cat $FRAGS_VERIFIED | wc -c | awk '{print $1}'`
NUM_FRAGS_FAILED=`cat $FRAGS_FAILED | wc -c | awk '{print $1}'`
NUM_FRAGS_TOTAL=`expr $NUM_FRAGS_VERIFIED + $NUM_FRAGS_FAILED`
echo "Verification started at [$START_DATE] and ended at [$END_DATE]" 
echo "All done. Analyzed $NUM_FRAGS_TOTAL fragments: $NUM_FRAGS_VERIFIED okay, $NUM_FRAGS_FAILED failures."

if [ "$NUM_FRAGS_FAILED" == "0" ]; then
  rm -rf $OUTDIR
fi
# on failure, leave output directory in place

exit $NUM_FRAGS_FAILED


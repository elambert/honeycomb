#!/bin/bash
#
# $Id: faults.sh 10932 2007-05-30 17:37:09Z elambert $
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
# Documentaiton at: 
# https://hc-twiki.sfbay.sun.com/twiki/bin/view/Main/DiskAndNodeFaultTesting

# GLOBAL VARS
# command line values
MASTER=master
NONMASTER=non-master
ACTIVE=active
SPARE=spare

# simulation mode true/false
SIMULATION=false

# mirror killing mode
MIRROR_KILLING_MODE=false

# node mode 
#MODE="single"
MODE="max"

# fault type: currently supported disk and node type 
FAULT_TYPE="node"
KILLTYPE="random"

# HC node type can be master, non-master or any
HC_NODE_TYPE="any"

# HADB node type can be active, spare or any
HADB_NODE_TYPE="any"

# specify the number of disks to disable on during diskfault
NUM_DISKS_TO_DISABLE=4

# loop control
ITERATIONS=1000
TIMEOUT_TESTCASE=0 # seconds

TIMETOSTOP=false
TIME_OUT=300 # value in seconds
TIME_OUT_INC=600 # seconds

# you can define a single node to skip on nodefaults...
# ex. NODES_TO_SKIP="101 102 103"
NODES_TO_SKIP=""

ONLY_DISK_ZERO="false"
ALWAYS_EXCLUDE_DISK_ZERO="false"
KILL_DISKS_ON_SAME_NODE="false"
NUM_NODES=16

help()
{
        echo "*** NodeFault Script ***"
        echo "Usage: ./nodefault.sh [-i iterations] [-f fault_type] [-k node_kill_type] [-t testcase_timeout] [-m] [-s]"
		echo " i:  number of iterations to run for."
		echo " f:  pick 'node' or 'disk' fault type."
        echo " k:  'soft' (triple kill of a a specified jvm. Default: IO-SERVERS"
		echo "     'ipmireset'"
		echo "     'random' (DEFAULT)"
		echo " t:  timeout for the testcase."
		echo " m:  node mode: single or max (2 nodes)"
		echo " d:  num of disks to fault (1-4)"
		echo " x:  mirror killing mode."
		echo " s:  simulation mode."
		echo " o:  if in disk fault mode, always include a hadb disk "
		echo "     in the list of disks to disable"
		echo " n:  Number of nodes [8 or 16]"
		echo " examples: "
		echo "           faults.sh -i 10 -f node -k random  --> This example would"
		echo "           execute the random node faults for 10 iterations."
		echo "           faults.sh -i 10 -f disk --> This example would"
		echo "           execute the disk faults for 10 iterations."		
}

DATE=`which date`
log()
{
	CURRENT_DATE=`$DATE +"%D %T"`
	echo $2 "$CURRENT_DATE: $1" 
}

while getopts ":i:*:f:*:k:*:s :h :o:*:x t:*:m:*:d:*:n:*" OPTION
do
	case $OPTION in
		i     ) 
    		if [ "$OPTARG" == "" ] 
    		then
    			log "Option -i used but number of iterations not specified"
			exit 1
		else
			ITERATIONS=$OPTARG
    		fi
    		;;
		f     ) 
    		if [ "$OPTARG" == "" ] 
    		then
    			log "Option -f used but fault type not specified. Select node or disk fault type."
			exit 1
		else
			FAULT_TYPE=$OPTARG
    		fi
    		;;
		k     ) 
    		if [ "$OPTARG" == "" ] 
    		then
    			log "Option -k used but node kill type not specified. Select random, soft or ipmireset."
			exit 1
		else
			KILLTYPE=$OPTARG
    		fi
		;;
		s     ) 
			SIMULATION=true
		;;
		t     ) 
    		if [ "$OPTARG" == "" ] 
    		then
    			log "Option -t used but timeout not specified."
			exit 1
		else
			TIMEOUT_TESTCASE=$OPTARG
    		fi
		;;
		d     ) 
    		if [ "$OPTARG" == "" ] 
    		then
    			log "Option -d used but num of disks not specified. Choose a number between 1 and 4."
			exit 1
		else
			NUM_DISKS_TO_DISABLE=$OPTARG
    		fi
		;;
		m     ) 
    		if [ "$OPTARG" == "" ] 
    		then
    			log "Option -m used but mode not specified. Choose single or max mode."
			exit 1
		else
			MODE=$OPTARG	
    		fi
		;;
		x     ) 
			MIRROR_KILLING_MODE=true
		;;
		n     ) 
    		if [ "$OPTARG" == "" ] 
    		then
    			log "Option -n used but number of nodes not specified. Either 8 or 16"
			exit 1
		else
			NUM_NODES=$OPTARG
			if [[ "$NUM_NODES" != "16" && "$NUM_NODES" != "8" ]]; then
    				log "Invalid number of nodes: $NUM_NODES"
				exit 1
			fi
    		fi
		;;
		o     ) 
    		if [ "$OPTARG" == "" ] 
    		then
    			log "Option -o used but disk 0 mode not specified."
			exit 1
		else
			if [[ "${OPTARG}" == "all" ]]; then
				ONLY_DISK_ZERO="true"
				ALWAYS_EXCLUDE_DISK_ZERO="false"
			elif [[ "${OPTARG}" == "none" ]]; then
				ONLY_DISK_ZERO="false"
				ALWAYS_EXCLUDE_DISK_ZERO="true"
			elif [[ "${OPTARG}" == "mix" ]]; then
				ONLY_DISK_ZERO="false"
				ALWAYS_EXCLUDE_DISK_ZERO="false"
			else
    				log "invalid arg to Option -o: ${OPTARG} ."
				exit 1
			fi
    		fi
		;;
		h     )
			help
			exit 0
    		;;
  	esac
done

printOptions()
{
	log "********* Testcase Parameters ********"
	log "* Iterations: $ITERATIONS"
	log "* Fault Type: $FAULT_TYPE"
	log "* Node Fault Mode: $MODE"
	log "* Node Kill Type: $KILLTYPE"
	log "* Simulation Mode: $SIMULATION"
	log "* Num of Disks To Fault: $NUM_DISKS_TO_DISABLE"
	log "* Testcase Timeout: $TIMEOUT_TESTCASE"
	log "* Fault Timeout: $TIME_OUT"
	log "**************************************"
}

printOptions

dot()
{
	echo -n "."
}

JVM_TO_KILL="IO-SERVERS"

MASTER_NODE=""
CALC_MASTER_NODE="/opt/honeycomb/bin/nodemgr_mailbox.sh  | grep 'ALIVE MASTER' | cut -d' ' -f 1 | awk '{print \"hcb\"\$1}'"
CALC_MASTER_NODES="/opt/honeycomb/bin/nodemgr_mailbox.sh  | grep ALIVE | grep MASTER | cut -d' ' -f 1"
CALC_NON_MASTER_NODES="/opt/honeycomb/bin/nodemgr_mailbox.sh  | grep ALIVE | grep -v MASTER | cut -d' ' -f 1"
CALC_ACTIVE_HADB_NODES="echo admin | /opt/SUNWhadb/4/bin/hadbm status honeycomb --nodes  | grep running | grep active | sed -e 's/[^h]*hcb\([0-9]*\).*/\1/'"
CALC_SPARE_HADB_NODES="echo admin | /opt/SUNWhadb/4/bin/hadbm status honeycomb --nodes  | grep running | grep spare | sed -e 's/[^h]*hcb\([0-9]*\).*/\1/'"

# now here comes the meat where we decide which nodes are candidate :)
CANDIDATE_NODES=""

calcUnion()
{
	for N in $1
	do
		RES=`echo $2 | grep $N`
		if [ $? -eq 0 ]
		then
			CANDIDATE_NODES="$CANDIDATE_NODES $N"	
		fi
	done
	
	ODD_BUNCH=""
	EVEN_BUNCH=""

	for N in $CANDIDATE_NODES
	do
		# check if this node belongs to NODES_TO_SKIP
		echo "$NODES_TO_SKIP" | grep "$N" > /dev/null 2>&1
		if [ $? == 0 ]
		then
			continue
		fi
	
		REM=$(( $N % 2 ))
		if [ $REM -eq 0 ] 
		then
			EVEN_BUNCH="$EVEN_BUNCH $N"	
		else
			ODD_BUNCH="$ODD_BUNCH $N"
		fi
	done

	CANDIDATE_NODES="$ODD_BUNCH $EVEN_BUNCH"
}

calcMaster()
{
	for N in $NODE_LIST
	do 
		ping hcb$N > /dev/null 2>&1
		if [ $? -eq 0 ] 
		then	
			MASTER_NODE=`ssh hcb$N "$CALC_MASTER_NODE"`
			return 0
		fi
	done

	# if we get here we didn't find a single node to ssh to so exit!
	log "ERROR: Couldn't find a node to ask who is the master."
	exit 1
}

getGoodNode()
{
	for GOOD_NODE in $NODE_LIST
	do
		ping hcb$GOOD_NODE > /dev/null 2>&1
		if [ $? == 0 ]
		then
			ssh hcb$GOOD_NODE "ps -ef | grep java"  > /dev/null 2>&1 
			if [ $? == 0 ]
			then
				return $GOOD_NODE
			fi
		fi
		dot	
	done

	log "No good nodes available, failing testcase"
	exit 1
}

checkFailure()
{
	if [ $? != 0 ]
	then
		log $1
		exit 1
	fi
}

mirrorKillingMode()
{
	# Mirror Killing mode:
	log "Mirror killing mode"
	TIME_OUT=1800 # half an hour
	CANDIDATE_NODES="$NODE_LIST"
	
	log "Candidate Nodes: [$CANDIDATE_NODES]"
}

calcCandidates()
{
	waitForHADB

	CANDIDATE_NODES=""
	
	log "Calculating node candidates..." "-n"
	
	getGoodNode
	GOOD_NODE=hcb$GOOD_NODE
	
	MASTER_NODES=`ssh $GOOD_NODE "$CALC_MASTER_NODES"`
	checkFailure "Exiting testcase unable to calculate Master nodes"	
	dot	
	NON_MASTER_NODES=`ssh $GOOD_NODE "$CALC_NON_MASTER_NODES"`
	checkFailure "Exiting testcase unable to calculate Non-Master nodes"	
	dot	
	ACTIVE_HADB_NODES=`ssh $GOOD_NODE "$CALC_ACTIVE_HADB_NODES"`
	checkFailure "Exiting testcase unable to calculate active hadb nodes"	
	dot	
	SPARE_HADB_NODES=`ssh $GOOD_NODE "$CALC_SPARE_HADB_NODES"`
	checkFailure "Exiting testcase unable to calculate spare hadb nodes"	
	dot

	# changing breaklines to spaces :)
	MASTER_NODES=`echo $MASTER_NODES`
	NON_MASTER_NODES=`echo $NON_MASTER_NODES`
	ACTIVE_HADB_NODES=`echo $ACTIVE_HADB_NODES`
	SPARE_HADB_NODES=`echo $SPARE_HADB_NODES`

	echo " done."

	log "Master Nodes: [$MASTER_NODES]"
	log "None Master Nodes: [$NON_MASTER_NODES]"
	log "Active HADB Nodes: [$ACTIVE_HADB_NODES]"
	log "Spare HADB Nodes: [$SPARE_HADB_NODES]"

	if [ "$2" == "$MASTER" ] 
        then
		GROUPA="$MASTER_NODES"
        fi

        if [ "$2" == "$NONMASTER" ]
        then
                GROUPA="$NON_MASTER_NODES"
        fi

        if [ "$2" == "any" ]
        then
                GROUPA="$MASTER_NODES $NON_MASTER_NODES"
        fi

        if [ "$3" == "$ACTIVE" ]
        then
                GROUPB=$ACTIVE_HADB_NODES
        fi

        if [ "$3" == "$SPARE" ]
        then
                GROUPB="$SPARE_HADB_NODES"
        fi

        if [ "$3" == "any" ]
        then
                GROUPB="$ACTIVE_HADB_NODES $SPARE_HADB_NODES"
        fi

	calcUnion "$GROUPA" "$GROUPB"

	if [ "$CANDIDATE_NODES"	 == "" ]
	then
		log "No Candidate nodes available, exiting."
		exit 1
	fi

	log "Candidate Nodes: [$CANDIDATE_NODES]"
}

calcHadbDisk () 
{
	node_to_check=hcb$1
	this_hadb_disk=`ssh  ${node_to_check} cat /config/SUNWhadb/mgt.cfg | grep dbdev | cut -d"=" -f 2 | sed -e 's|^/data/\(.\)/hadb|\1|g'`
	echo $this_hadb_disk
}

calcDiskCandidates()
{
	calcMaster
        hadbDisk=`calcHadbDisk $1`
	log "Calculating disk candidates..."
	if [[ ${ALWAYS_EXCLUDE_DISK_ZERO} == "true" ]]; then
		CANDIDATE_DISKS=`ssh admin@$MASTER_NODE hwstat | grep ENABLED \
		| grep $1 | grep -v ":${hadbDisk}" | awk '{print $1}'`
	elif [[ ${ONLY_DISK_ZERO} == "true" ]]; then
		if [[ "${MODE}" == "single" ]] && [[ ${NUM_DISKS_TO_DISABLE} -gt 1 ]]; then
			OTHER_DISK=$RANDOM
			let "OTHER_DISK %= 3"	
			((OTHER_DISK++))
			CANDIDATE_DISKS=`ssh admin@$MASTER_NODE hwstat | grep ENABLED \
			| grep $1 | egrep ":${hadbDisk}|:$OTHER_DISK" | awk '{print $1}'`
		else
			CANDIDATE_DISKS=`ssh admin@$MASTER_NODE hwstat | grep ENABLED \
			| grep $1 | grep ":${hadbDisk}" | awk '{print $1}'`
		fi
	else
		CANDIDATE_DISKS=`ssh admin@$MASTER_NODE hwstat | grep ENABLED \
		| grep $1 | awk '{print $1}'`
	fi
	
	CANDIDATE_DISKS=`echo $CANDIDATE_DISKS`

	log "Candidate Disk: [$CANDIDATE_DISKS]"
}

# only works for executing a single command from a single script.. 
# don't use for backgrounding tasks.. 
runCommandWithTimeout()
{
	timeout="$1"; shift
	$@ &

	trap 'kill -9 %1 1>/dev/null 2>&1' SIGUSR1 
	
	(sleep $timeout; kill -s USR1 $$ >/dev/null 2>&1) >/dev/null 2>&1 &

	wait %1
	result=$?

	kill -9 %2 1>/dev/null 2>&1

	return $result
}

waitForHADB()
{
	calcMaster
	STATUS=`ssh admin@$MASTER_NODE "hadb status -F" |  egrep -v '^\*|^$'`

	while [ true ]
	do
		if [ "$STATUS" == "HAFaultTolerant" ] || [ "$STATUS" == "FaultTolerant" ]
		then
			return
		else
			log "HADB in [$STATUS] state, waiting for 10m"
			sleep 600
			STATUS=`ssh admin@$MASTER_NODE "hadb status -F" |  egrep -v '^\*|^$'`
		fi
	done
}

printHADBStatus()
{
	waitForHADB

	log "Retrieving HADB Stats... " "-n"
	calcMaster

	STATUS=`ssh $MASTER_NODE "echo admin | /opt/SUNWhadb/4/bin/hadbm status honeycomb | grep honeycomb"`
	echo " Status: $STATUS"

	# get list of running nodes... 
#	STATUS=`ssh $MASTER_NODE "echo admin | /opt/SUNWhadb/4/bin/hadbm status honeycomb --nodes | grep running"`
#	NODE_LIST=`echo $STATUS | awk '{print $1}'`
#	echo "status: $NODE_LIST"
#	STATUS=`echo $STATUS`
	
#	log "Retrieving HADB Mirrors: $STATUS"
}


printHCStatus()
{
	log "Retrieving HC Stats... " "-n"
	calcMaster

	STATUS=`ssh admin@$MASTER_NODE sysstat | egrep "Data services|disks"`
	
	#trick removes newlines
	STATUS=`echo $STATUS`
	echo " Status: $STATUS"
}

verifyCMM()
{
	log "Verifying CMM integrity..." "-n"

	/opt/test/bin/cmm_verifier $NUM_NODES > /dev/null 2>&1
	if [ $? -eq 0 ]
	then
		echo " Passed."
	else
		log " Failed."
		exit 1
	fi
}

# executes command localy and waits till it returns sucesfully
waitTillPassLocal()
{
	TRIES=0
	CONTINUE=true
	log "$2" -n
	while $CONTINUE
	do
		#runCommandWithTimeout "20" "$1 > /dev/null 2>&1"
		$1 > /dev/null 2>&1
		if [ $? -eq 0 ] 
		then
			CONTINUE=false
		fi
		sleep 1
		dot	
		TRIES=`expr $TRIES + 1`
		if [ $TRIES -gt 600 ]
		then
			echo " failed. Tried 600 times"
			log "$3"
			exit 1	
		fi
	done
	echo " done."
}

# executes command localy and waits till it returns unsucessfully
waitTillFailLocal()
{
	TRIES=0
	CONTINUE=true
	log "$2" -n
	while $CONTINUE
	do
		#runCommandWithTimeout "20" "$1 > /dev/null 2>&1"
		$1 > /dev/null 2>&1
		if [ $? -ne 0 ] 
		then
			CONTINUE=false
		fi
		sleep 1
		dot	
		TRIES=`expr $TRIES + 1`
		if [ $TRIES -gt 600 ]
		then
			echo " failed. Tried 600 times"
			log "$3"
			exit 1	
		fi
	done
	echo " done."
}

# executes command on master node and waits till it returns successfully 
waitTillPassOnNode()
{
	getGoodNode
	waitTillPassLocal "ssh hcb$GOOD_NODE $1" "$2" "$3"
}

waitTillPassOnNodeS()
{
	waitTillPassLocal "ssh hcb$1 $2" "$3" "$4"
}

waitTillClusterIsComplete()
{
	log "Making sure we have a complete cluster, before proceeding..." "-n"
	count=0
	while [ result == false ]
	do 
		result=true
		for i in $NODE_LIST
		do 
			ping hcb$i
			if [ $? != 0 ]
			then
				result=false
			fi 
		done
		echo -n "."
		count=`expr $count + 1`
		if [ count -gt 600 ]
		then
			echo " failed."
			log "Incopmlete ring formation... exiting testcase"
			exit 1
		fi
	done

	echo " done."
}

waitTillHADBNodeIsRunning()
{
	waitTillClusterIsComplete
	#waitTillPassOnNodeS "$1" "echo admin | /opt/SUNWhadb/4/bin/hadbm status honeycomb | grep 'HAFaultTolerant'" \
	#waitTillPassOnNode "echo admin | /opt/SUNWhadb/4/bin/hadbm status honeycomb --nodes | grep $1 | egrep -i 'running'" \
	#	     "Waiting for HADB node $1 to come back." \
	#	     "HADB node $1 did not come back."

	log "Not waiting for hadb node... carrying on with testcase in 2m"
	sleep 120
}

waitTillHCNodeIsRunning()
{
	waitTillPassOnNode "/opt/honeycomb/bin/nodemgr_mailbox.sh | grep $1 | grep -i ALIVE" \
		     "Waiting for HC node $1 to come back." \
                     "Honeycomb node $1 did not return to running state within 600s."
}


waitTillHADBNodeIsStopped()
{
	waitTillClusterIsComplete
	waitTillPassOnNode "echo admin | /opt/SUNWhadb/4/bin/hadbm status honeycomb --nodes | grep $1 | egrep -i 'stopped'" \
		     "Waiting for HADB node $1 to stop." \
		     "HADB node $1 has not gone away after 600s."
}

waitTillHCNodeIsStopped()
{
	waitTillPassOnNode "/opt/honeycomb/bin/nodemgr_mailbox.sh | grep $1 | grep -i DEAD" \
		     "Waiting for HC node $1 to stop." \
                     "Honeycomb node $1 has not gone away after 600s."
}

REBOOT="reboot"
IPMIRESET="ipmitool -I lan -H hcb$1-sp -U Admin -f /export/ipmi.pass chassis power reset"
SOFT="kill -9 \`ps -ef | grep java | grep $JVM_TO_KILL | grep -v grep | awk '{print \$2}'\`"

ipmiReset()
{
	echo "honeycomb" > /export/ipmi.pass
	ipmitool -I lan -H hcb$1-sp -U Admin -f /export/ipmi.pass chassis power reset > /dev/null 2>&1 
}

KILL_METHODS[0]="ipmireset"
KILL_METHODS[1]="soft"
NUM_KILL_METHODS=2

killNode()
{
	if [ "$KILLTYPE" == "random" ]
	then
       		KILL_METHOD=$RANDOM
        	let "KILL_METHOD %= NUM_KILL_METHODS"
	        KILLTYPE=${KILL_METHODS[$KILL_METHOD]}
	fi

	if [ $SIMULATION = true ] 
	then
		# SIMULATION MODE: not doing any real faults... 
		log "SIMULATION MODE: would of killed $1 with $KILLTYPE"
		return
	else
		log "Killing $1 with $KILLTYPE"
		
		if [ "$KILLTYPE" == "reboot" ]
		then
			ssh hcb$1 $REBOOT > /dev/null 2> /dev/null
			return
		fi

		if [ "$KILLTYPE" == "ipmireset" ]
		then
			ipmiReset $1 
			return
		fi

		if [ "$KILLTYPE" == "soft" ]
		then 
			log "Soft kill... " "-n"
			iter=0
			iterations=6	
			while [ $iter -lt $iterations ] 
			do
				echo -n "kill "
				ssh hcb$1 "$SOFT" > /dev/null 2>&1 &
				sleep 10
				iter=`expr $iter + 1`
			done	
			echo "... done."
			return
		fi
	fi 

	log "Bad kill_type"
	exit 1
}

killDisk()
{
	if [ $SIMULATION = true ] 
	then
		# SIMULATION MODE: not doing any real faults... 
		log "SIMULATION MODE: would of killed disk $1."
		return
	else
		calcMaster
		log "Disabling $1"
       	 	STATUS=`ssh admin@$MASTER_NODE "hwcfg -F -D $1" | egrep -v '^\*|^$'`
	fi
}

panicDisk() 
{
 	#"DISK-101:0"
	node=hcb`echo $1 | cut -d: -f1 | cut -d- -f2`
	disk=`echo $1 | cut -d: -f2`

	#ssh to node and get device path for disk
	devdisk=`ssh $node mount | grep data/${disk} | cut -d" " -f3`
	magicString=`ssh $node  ls -l ${devdisk} \
	| awk '{print $11}'| sed -e "s|../..||"`
	sector=5
	if [ $SIMULATION = true ]; then
		log "SIMULATION MODE: would of killed disk $1."
		log "node is ${node}"
		log "disk is ${disk}"
		log "dev Disk: ${devdisk}"
		log "magic String: ${magicString}"
		return
	else
		calcMaster
		while (( sector < 11 )); do
			ssh ${node} "echo WARNING: ${magicString} fatal read error sector ${sector} > /devices/pseudo/hctestmod@0\:0"
			sleep 2
			((sector++))
		done
		let elapsedWait=0
		while (( elapsedWait < 600 )); do
			diskStatus=`ssh admin@${MASTER_NODE} hwstat \
			| egrep  $1 | awk '{print $4}'`
			if [ "${diskStatus}" == "DISABLED" ]; then
				break
			fi
			sleep 60
			let elapsedWait=elapsedWait+60
		done
		if [ "${diskStatus}" != "DISABLED" ]; then
			log "Failed to disable disk: $1"
			exit 1
		fi
	fi

}

enableDisk()
{
	if [ $SIMULATION = true ] 
	then
		# SIMULATION MODE: not doing any real faults... 
		log "SIMULATION MODE: would of revived disk $1."
		return
	else
		calcMaster
		log "Enabling disk $1"
        	STATUS=`ssh admin@$MASTER_NODE "hwcfg -F -E $1"| egrep -v '^\*|^$'`
	fi
}

verifyNodeIsGone()
{
	if [ $SIMULATION = true ]
	then
		# SIMULATION MODE: not really waiting for node to come back... 
		log "SIMULATION MODE: would of waited for node $1 to go away."
	else
		waitTillFailLocal "ping hcb$1" "Waiting till node $1 is not pingable." "Node $1 failed to reboot."	

		# verify from another node that this node has infact left the ring.
		waitTillHCNodeIsStopped $1
		# verify from another node that this node has also been removed from HADB's ring.
		# waitTillHADBNodeIsStopped $1
	fi
}

verifyNodeIsBack()
{
	if [ $SIMULATION = true ]
	then
		# SIMULATION MODE: not really waiting for node to come back... 
		log "SIMULATION MODE: would of waited on node $1"
	else
		waitTillPassLocal "ping hcb$1" "Waiting till node $1 is pingable." "Node $1 is not pingable."

		log "Waiting 60s for ssh to be up... this could be made slightly better"
		sleep 60

		# now verify this node is in the ring... 
		waitTillHCNodeIsRunning $1

		# now verify that hadb also sees it up and happy... 
		waitTillHADBNodeIsRunning $1
	fi
}

doFaults()
{
	FAULT_NODES_ARRAY=($1)
	FAULT_NODES_ARRAY_LENGTH=${#FAULT_NODES_ARRAY[*]}

	if [ "$FAULT_TYPE" == "node" ]
	then
		log "Node fault on node $1"

		FAULT_NODES_INDEX=0
		while [ $FAULT_NODES_INDEX -lt $FAULT_NODES_ARRAY_LENGTH ]
		do 
			NODE_TO_FAULT=${FAULT_NODES_ARRAY[$FAULT_NODES_INDEX]}
			
			if [ $TIME_OUT -gt 0 ]
			then
				if [ $SIMULATION == false ]
				then
					log "Keeping node $NODE_TO_FAULT out of the ring for $TIME_OUT seconds."
					ssh hcb$NODE_TO_FAULT "touch /config/nohoneycomb ; sync ; sync ; sync" 
				else
					log "SIMULATION:  Keeping node $NODE_TO_FAULT out of the ring for $TIME_OUT seconds."
				fi
			fi

			killNode $NODE_TO_FAULT &
			killNodePID=$!
			verifyNodeIsGone $NODE_TO_FAULT &
			verifyNodePID=$!

			FAULT_NODES_INDEX=`expr $FAULT_NODES_INDEX + 1`		
		done

		# waits on kills and verifyNodeIsGone function calls
		log "Waiting on KillNode and verifyNodeIsGone processes"
		wait ${killNodePID} ${verifyNodePID}
		log "done."
		
		if [ $TIME_OUT -gt 0 ]
		then
			log "Sleeping while node(s) are kept out of the ring for ${TIME_OUT}"
			sleep $TIME_OUT
			log "Done keeping node(s) out of the ring"
		fi

		FAULT_NODES_INDEX=0
		while [ $FAULT_NODES_INDEX -lt $FAULT_NODES_ARRAY_LENGTH ]
		do
			NODE_TO_FAULT=${FAULT_NODES_ARRAY[$FAULT_NODES_INDEX]}
			
			if [ $TIME_OUT -gt 0 ]
			then
				if [ $SIMULATION == false ]
				then 
					log "Bringing node $NODE_TO_FAULT back into the ring."
					waitTillPassLocal "ping hcb$NODE_TO_FAULT" "Waiting till node $NODE_TO_FAULT is pingable." "Node $NODE_TO_FAULT is not pingable."
					ssh hcb$NODE_TO_FAULT "rm /config/nohoneycomb; reboot" > /dev/null 2>&1
					waitTillFailLocal "ping hcb$NODE_TO_FAULT" "Waiting till node $NODE_TO_FAULT is not pingable." "Node $NODE_TO_FAULT failed to reboot." 
				fi
			fi

			FAULT_NODES_INDEX=`expr $FAULT_NODES_INDEX + 1`		
		done


		FAULT_NODES_INDEX=0
		while [ $FAULT_NODES_INDEX -lt $FAULT_NODES_ARRAY_LENGTH ]
		do
			NODE_TO_FAULT=${FAULT_NODES_ARRAY[$FAULT_NODES_INDEX]}
                        verifyNodeIsBack $NODE_TO_FAULT 
			FAULT_NODES_INDEX=`expr $FAULT_NODES_INDEX + 1`		
		done

	fi

	if [ "$FAULT_TYPE" == "disk" ]
	then
		log "Disk fault(s) on node $1"

		FAULT_NODES_INDEX=0
		while [ $FAULT_NODES_INDEX -lt $FAULT_NODES_ARRAY_LENGTH ]
		do 
			NODE_TO_FAULT=${FAULT_NODES_ARRAY[$FAULT_NODES_INDEX]}
			
			if [ $TIME_OUT -gt 0 ]
			then
				log "Keeping disk(s) on $NODE_TO_FAULT disabled $TIME_OUT seconds."
			fi
			
			killDisks $NODE_TO_FAULT 
			FAULT_NODES_INDEX=`expr $FAULT_NODES_INDEX + 1`		
		done

		
		if [ $TIME_OUT -gt 0 ]
		then
			sleep $TIME_OUT
			log "Done keeping disk(s) disabled."
		fi

		FAULT_NODES_INDEX=0
		while [ $FAULT_NODES_INDEX -lt $FAULT_NODES_ARRAY_LENGTH ]
		do
			NODE_TO_FAULT=${FAULT_NODES_ARRAY[$FAULT_NODES_INDEX]}
			log "Enabling all disks on node $NODE_TO_FAULT"
			reviveDisks $NODE_TO_FAULT 
			FAULT_NODES_INDEX=`expr $FAULT_NODES_INDEX + 1`		
		done

		#TODO wait on PIDS
		#wait
	fi
}

reviveDisks()
{
	calcMaster

	DEAD_DISKS=`ssh admin@$MASTER_NODE hwstat | grep DISABLED \
	| grep $1 | awk '{print $1}'`
	DEAD_DISKS=`echo $DEAD_DISKS`
	
	DEAD_DISKS_ARRAY=($DEAD_DISKS)
	DEAD_DISKS_ARRAY_LENGTH=${#DEAD_DISKS_ARRAY[*]}

	DISK_INDEX=0
	while [ $DISK_INDEX -lt $DEAD_DISKS_ARRAY_LENGTH ]
	do
       		DISK=${DEAD_DISKS_ARRAY[$DISK_INDEX]}
		enableDisk $DISK
		DISK_INDEX=`expr $DISK_INDEX + 1`
	done
}

killDisks()
{
	calcDiskCandidates $1
	DISK_CANDIDATE_ARRAY=($CANDIDATE_DISKS)
	DISK_ARRAY_LENGTH=${#DISK_CANDIDATE_ARRAY[*]}
	
	DISK=$RANDOM
	let "DISK %= $DISK_ARRAY_LENGTH"	
       	DISK_RAND_INDEX=$DISK

	DISK_INDEX=0
	while [ $DISK_INDEX -lt $NUM_DISKS_TO_DISABLE ]
	do
       		DISK=${DISK_CANDIDATE_ARRAY[$DISK_RAND_INDEX]}
		log "Disk fault on $DISK"
		if [ "$KILLTYPE" == "soft" ]; then
			killDisk $DISK
		else
			panicDisk $DISK
		fi
		DISK_INDEX=`expr $DISK_INDEX + 1`
		DISK_RAND_INDEX=`expr $DISK_RAND_INDEX + 1`
		DISK_RAND_INDEX=`expr $DISK_RAND_INDEX % $DISK_ARRAY_LENGTH`
	done
}

nodeInSync()
{
	log "Verifying node $1 isn't in recovery/repair mode... " "-n"
	calcMaster

	STATUS=`ssh $MASTER_NODE "echo admin | /opt/SUNWhadb/4/bin/hadbm status honeycomb --nodes | egrep 'repairing|recovering'"`
	STATUS1=`echo $STATUS | grep $1`

	# if STATUS1 comes back empty then don't apply STATUS2 calc because it will always come back with 101!! :) 
	if [ "$STATUS1" != "" ]
	then
		STATUS2=`echo $STATUS | awk '{print 101+\$6}' | grep $1`
	fi
	
	if [ "$STATUS1" != "" ] || [ "$STATUS2" != "" ]
	then
		# node syncing/repairing should be skipped 
		echo " done."
		return 0
	else
		# node not syncing/repairing
		echo " done."
		return -1
	fi
}

nodeIsSafeToKill()
{

	if [ $MIRROR_KILLING_MODE = true ]
	then
		return 0
	fi

	nodeInSync "$1"
	if [ $? == 0 ] 
	then
		log "Node $1 in sync or repairing..."
		return -1	
	else
		MIRROR=`ssh $MASTER_NODE "echo admin | /opt/SUNWhadb/4/bin/hadbm status honeycomb --nodes | grep $1"`
		MIRROR=`echo $MIRROR | awk '{print 101+\$6}'`
			
		for N in $2
		do
			if [ "$N" == "$MIRROR" ]
			then
				return -1
			fi
		done

		return 0
	fi
}

ITER=0

if [ "$NUM_NODES" == "16" ];
then
	NODE_LIST="101 102 103 104 105 106 107 108 109 110 111 112 113 114 115 116"
else
	NODE_LIST="101 102 103 104 105 106 107 108"
fi

if [ "$NODES_TO_SKIP" != "" ]
then
	log "Skipping nodes $NODES_TO_SKIP in the current run."
fi

if [ $TIMEOUT_TESTCASE -gt 0 ]
then
	trap 'TIMETOSTOP=true' SIGUSR1
	log "Running with timeout of $TIMEOUT_TESTCASE seconds."
	(sleep $TIMEOUT_TESTCASE; kill -SIGUSR1 $$) &
fi

while [ $ITER -lt $ITERATIONS ] && [ $TIMETOSTOP == false ] 
do
	if [ $MIRROR_KILLING_MODE = true ]
	then
		mirrorKillingMode
	else	
		# pass command line arguments directly to calcCandidates
		calcCandidates $KILLTYPE $HC_NODE_TYPE $HADB_NODE_TYPE
	fi
	
	if [ "$MODE" == "single" ] 
	then
		for NODE in $CANDIDATE_NODES
		do
			if [ $TIMETOSTOP == true ]
			then
				break
			fi
		
			nodeInSync $NODE
			if [ $? -eq 0 ]
			then
				log "Node $NODE is in the syncing state therefore will be skipped this time around."
			else
				printHADBStatus
				printHCStatus
				verifyCMM
				doFaults "$NODE"
			fi
			
			# single simulation...
			if [ "$4" = "-single" ]
			then
				log "Single iteration... All done."
				exit 0
			fi
		done
	fi	
	
	if [ "$MODE" == "max" ]
	then
		CANDIDATE_ARRAY=($CANDIDATE_NODES)
		ARRAY_LENGTH=${#CANDIDATE_ARRAY[*]}
		MAXFAILURES=2
		NODE_INDEX=0

		while [ $NODE_INDEX -lt $ARRAY_LENGTH ] && [ $TIMETOSTOP == false ]
		do
			FAILED_NODES=""
			FAILED_CANDIDATES=0
			
			printHADBStatus
			printHCStatus	
		
			while [ $FAILED_CANDIDATES -lt $MAXFAILURES ]
			do
				if [ $NODE_INDEX -gt $ARRAY_LENGTH ]
				then
					log "Out of candidates breaking this loop.. will wait for remaining failed nodes."
					break
				fi

				nodeIsSafeToKill "${CANDIDATE_ARRAY[$NODE_INDEX]}" "$FAILED_CANDIDATES"
				if [ $? != 0 ]
				then
					log "Skipping node ${CANDIDATE_ARRAY[$NODE_INDEX]} because it is a mirror of another node or syncing..."
				else
					FAILED_NODES="$FAILED_NODES ${CANDIDATE_ARRAY[$NODE_INDEX]}" 
					FAILED_CANDIDATES=`expr $FAILED_CANDIDATES + 1`
				fi
				
				NODE_INDEX=`expr $NODE_INDEX + 1`
			done

			doFaults "$FAILED_NODES"
		done
	fi

	TIME_OUT=`expr $TIME_OUT + $TIME_OUT_INC`	
	ITER=`expr $ITER + 1`
	log "Finished Iteration: ${ITER}"
done

if [[ "${TIMETOSTOP}" == "true" ]]
then
	log "Time out reached."
fi

log "All done."

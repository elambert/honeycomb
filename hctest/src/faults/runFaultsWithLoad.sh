#!/usr/bin/bash
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




PERF_TEST="/opt/performance/performance_test.sh"
FAULT_SCRIPT="/export/home/root/faults.sh"
VERIFY_OIDS="/opt/test/bin/getAllOIDs.sh"
ADMIN_VIP="10.123.45.200"
NO_HONEYCOMB="/config/nohoneycomb"
NO_REBOOT="/config/noreboot"
ME=$0

############
usage () { #
############
	echo "${ME} <LOCAL_LOG_DIR> <LOG_DIR_ON_CLIENT> <MASTER_CLIENT>"
	echo "      <LOAD_ITERS> [<FAULT_SCRIPT_ARGS>]"
	echo ""
	echo "The purpose of this script is to drive the testing of disk and "
	echo "node fault testing while under load. It does so by starting load"
	echo "on the clients and then executing the node/disk fault script. After"
	echo "the node/disk fault script has completed, it will stop the load"
	echo "and then make sure all nodes and disks are online. Once all "
	echo "elements of the cluster are back on line, it will attempt to "
	echo "retrieve each OID that was stored during load."
	echo ""
	echo "This script makes the following assumptions:"
	echo "   -That ${PERF_TEST} has been configured on the master client."
	echo "   -That ${FAULT_SCRIPT} exists on the cheat."
	echo "   -That ${VERIFY_OIDS} exists on the master client."
	echo ""
	echo "Options:"
	echo ""
	echo "       <LOCAL_LOG_DIR>: Path to directory on the cheat where this"
	echo "                        script can log output."
	echo ""
	echo "       <LOG_DIR_ON_CLIENT>: Path to directory on the master client" 
	echo "                            where performance test can log output."
	echo ""
	echo "       <MASTER_CLIENT>: Name or IP address of client used to apply"
	echo "                        load to cluster. Perfromance test script "
	echo "                        must exist and being configured on this client."
	echo ""
	echo "       <LOAD_ITERS>: Number of iterations the performance_test.sh "
	echo "                     should run."
	echo ""
	echo "       <FAULT_SCRIPT_ARGS>: Arguments to be passed to the fault.sh "
	echo "                            script."
	echo ""
	echo "Exit codes:"
	echo "           This script will exit with a zero-exit code if fault.sh "
	echo "           Completes with a zero exit code and all stored oids are "
	echo "           able to be retrieved. Otherwise, the script will exit with"
	echo "           non-zero exit code."

}

##########
log () { #
##########
	echo `date` $*
}

#########################
restartDownedNodes () { #
#########################

	ssh admin@${ADMIN_VIP} hwstat | egrep -s 'OFFLINE'
	offlineNodes=$?
	if [[ "${offlineNodes}" -eq "0" ]]; then
		offNodes=`ssh admin@${ADMIN_VIP} hwstat | grep 'OFFLINE' | awk '{print $1}' | cut -d- -f2`
		for i in ${offNodes}; do
			node=hcb$i
			log restarting node ${node}
			ping ${node}
			isAlive=$?
			if [[ "${isAlive}" -eq "0" ]]; then
				ssh $node rm ${NO_HONEYCOMB}
				ssh $node rm ${NO_REBOOT}
				ssh $node reboot
				log rebooted $node
			else
				passfile=/tmp/passfile
				echo "honeycomb" > ${passfile}
				ipmitool -f ./passfile -I lan -H ${node}-sp -U Admin chassis power reset
				rm ${passfile}
			fi
		done
		log "sleeping for 3 minutes to let nodes reboot"
		sleep 180

	else 
		log "Skipping restart downed nodes: all nodes on line"
	fi

}

#########################
enableMissingDisks () { #
#########################

	ssh admin@${ADMIN_VIP} hwstat | egrep -s 'DISABLE'
	disableDisks=$?
	if [[ "${disableDisks}" -eq "0" ]]; then
		badDisks=`ssh admin@${ADMIN_VIP} hwstat | grep 'DISABLE' | awk '{print $1}'`
		for i in ${badDisks}; do
			log "enabling disk ${i}"
			ssh admin@${ADMIN_VIP} hwcfg -E ${i}
		done
	else
		log "All disks are on line"
	fi

}

if [[ $# -lt 4 ]]; then
	usage
	exit 1
fi


LOCAL_LOG_DIR=$1;shift
LOAD_DIR=$1;shift
CLIENT=$1;shift
LOAD_ITERS=$1;shift
FAULT_ARGS=$*

#log it
log "Starting faults with load test"
log "Master Client: ${CLIENT}"
log "Load Iterations: ${LOAD_ITERS}"
log "Fault script: ${FAULT_SCRIPT} ${FAULT_ARGS}"

#start load against cluster
#this assumes that performance_test.sh has been configured on the client
ssh ${CLIENT} mkdir -p ${LOAD_DIR}
ssh ${CLIENT} "${PERF_TEST} 1 ${LOAD_ITERS} ${LOAD_DIR} > ${LOAD_DIR}/out 2>&1 < /dev/null" &

log "Load has been started. Sleeping 2 minutes to let load ramp up"
sleep 120

log "Starting fault test"
${FAULT_SCRIPT} ${FAULT_ARGS}
faultStatus=$?
log "Fault Test completed."
if [[ "${faultStatus}" -ne "0" ]];then
	log "Fault Test Failed. Fault Script exited with non-zero exit code."
	exit 1
else
	log "Fault Test Passed"
fi

log "Stopping load against cluster"
ssh ${CLIENT} "${PERF_TEST} stop"

log "Restarting any missing disks or downed nodes."
restartDownedNodes
enableMissingDisks

log "verifying that I can retrieve all OIDs I stored."
ssh ${CLIENT} ${VERIFY_OIDS} ${LOAD_DIR}
oidsOK=$?
if [[ "${oidsOK}" -eq "0" ]]; then
	log "Test PASSED!"
	exit 0
else
	log "Test FAILED!"
	exit 1
fi

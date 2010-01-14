#!/bin/bash
#
# $Id: bounceHC.sh 10858 2007-05-19 03:03:41Z bberndt $
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

# WISH LIST
# -trap ctrl-c in the reboot loop

ME=`basename $0`
ITERATIONS=0;
let CURRENT_ITER=0;
SSHC="ssh -o StrictHostKeyChecking=no -q"
REBOOT_COMMAND="reboot -F"
CLUSTER_ID=`uname -n | sed -e 's|\(.*\)-cheat|\1|'`
ADMIN_NODE="10.123.45.200"
LOG_DIR=`mktemp -d`
LOG_FILE="${LOG_DIR}/${ME}.log"
HADBPASSWD="/tmp/hadb_passwd"
HADBPASSWD_SRC="${LOG_DIR}/hadb_passwd"
STATUSLOG="/tmp/$ME.$$.slog"
EXIT_CODE=0
HADBM="/opt/SUNWhadb/4/bin/hadbm"
HADBMI="${HADBMI}-i"
HADB_FAULT_TOLERANT="HAFaultTolerant"
HADB_RUNNING="running"
HADB_STOPPED="Stopped"
TEE_ON="1";
TEE_OFF="0";
LOG_MODE=${TEE_ON}
CONFIG_PROP='/config/config.properties'
HONEYCOMB_DRIVER='/opt/honeycomb/etc/init.d/honeycomb'
REBOOT="FALSE"
NOREBOOT_MARKER="/config/noreboot"
NOSVC_MARKER="/config/nohoneycomb"
HADB_SHUTDOWN_MSG='"HADB shutdown exiting"'
WIPE_HADB='/opt/honeycomb/bin/wipe.sh -f'
GREENLINE='FALSE'
SLEEP_VALUE=1500
INITIAL_HC_BOOT_SLEEP=1200
HADB_MGT_CFG='/config/SUNWhadb/mgt.cfg'

############
usage () { #
############
    echo ""
    echo "${ME} is a script that starts and stops a honeycomb cluster a predetermined number of times."
    echo "After the cluster has been started, it performs some simple checks to ensure the "
    echo "cluster has been restarted correctly."
    echo ""
    echo "These checks include: "
    echo " - That the HADB Domain has been formed."
    echo " - That the HADB Database is HAFaultTolerant"
    echo ""
    echo "By default, this script will only shutdown/restart the honeycomb server and will not actually "
    echo "reboot the nodes.  To change this behavior, provide the -reboot option on the command line." 
    echo ""
    echo "usage"
    echo ""
    echo "${ME} [-wipe | -w] [-help | -h] [ -iters | -i <NUMBER_OF_ITERS> ]"
    echo "             [-log | -l <LOG_FILE> ] [-sleep | -si <SLEEP_TIME_INTERVAL> ] "
    echo "             [-reboot | -rb ] [-adminvip | -av <ADMIN_IP> ]  [ -greenline | -gl ]"
    echo "             [-script | -rb <PATH_TO_SCRIPT> ] [-args <SCRIPT_ARGS>] "
    echo "             [-cheat | -sp <CHEAT_NODE_IP> ]"
    echo " "
    echo "OPTIONS: "
    echo " "
    echo "        -help      prints this usage message"
    echo " "
    echo "        -iters     the number of  times ${ME} will reboot the cluster. Defaults to ${ITERATIONS} "
    echo " "
    echo "        -log       the location of the log file. Defaults to ${LOG_FILE}."
    echo " "
    echo "        -sleep     the amount of time ${ME} will sleep between reboots. Defaults to ${SLEEP_VALUE}"
    echo " "
    echo "        -wipe      wipe data from cluster. When this option is specified, ${ME} will "
    echo "                   perform the wipe.sh script on all the nodes"
    echo " "
    echo "        -cheat     name/IP addrss of the cheat node "
    echo " "
    echo "        -adminvip  IP address of the admin vip. Defaults to 10.123.45.200"
    echo " "
    echo "        -reboot    Reboot the nodes after shutting down honeycomb."
    echo " "
    echo "        -greenline Tie honeycomb into greenline. Defaults to false."
    echo " "
    echo "        -script    Path to script to be executed after HADB is up and HAFaultTolerant"
    echo " "
    echo "        -args      Arguments passed into the script scpecified with the -script option."
    echo "                   All tokens on the command line after this option are arguments to the script,"
    echo "                   as such this should be the last option specified on the command line"
    echo " "
} 


#######################
getNumberOfNodes () { #
#######################
	NUM_NODES=`ssh ${ADMIN_NODE} cat "${CONFIG_PROP}" | egrep 'honeycomb.cell.num_nodes' | sed -e 's| ||g' | cut -d"=" -f2`
}


####################
getHadbStatus () { #
####################

	# Qry HADBM for the status appears to be harmful
	# Use Honeycomb CLI instead
	#scp -q ${HADBPASSWD_SRC} ${ADMIN_NODE}:${HADBPASSWD} 
	#HADB_STATUS="unkown"
	#if [[ -f ${STATUSLOG} ]]; then
	#	rm ${STATUSLOG}
	#fi
	#ssh ${ADMIN_NODE} ${HADBM} status honeycomb -W ${HADBPASSWD}  > ${STATUSLOG}
	#if [[ -f "${STATUSLOG}" ]]; then
	#	HADB_STATUS=`cat ${STATUSLOG} | egrep honeycomb | cut -d" " -f2`
	#fi

	HADB_STATUS="unkown"
	${SSHC} admin@${ADMIN_NODE} hadb status -F > ${STATUSLOG}
	if [[ -f "${STATUSLOG}" ]]; then
                egrep -q 'running' ${STATUSLOG}
		if [[ $?  -eq 0 ]]; then
                    HADB_STATUS="running"
		fi
	fi
}


#################
waitOnHadb () { #
#################
	LOG_MODE="${TEE_OFF}"
	WAIT_STATUS="${WAIT_TIMED_MSG}"
	_waitvalue=10;
	_status=$1;shift
	if [[ $# -gt 0 ]]; then
		let _wait=$1; shift
	else
		let _wait=${SLEEP_VALUE}
	fi

	while (( _wait > 0 )); do
		getHadbStatus
		if [[ "${HADB_STATUS}" != "${_status}" ]]; then
			log "STATUS: ${HADB_STATUS} WAIT REMAINING: $_wait"
			sleep $_waitvalue
			let _wait=_wait-_waitvalue
		else
			log "STATUS: ${HADB_STATUS}"
			WAIT_STATUS="${WAIT_PASSED_MSG}"
			break;
		fi
	done
	LOG_MODE="${TEE_ON}"
}


##########
die () { #
##########
	let exit_code=$1; shift
	if [[ $# -ne 0 ]]; then
		echo $*
	fi
	exit $exit_code;
}

################	
doServers () { #
################	

	let _iters=${NUM_NODES}
	while (( $_iters > 0 )); do
		let _portNum=2000+_iters
		ssh ${ADMIN_NODE} -p ${_portNum} $*
		let _iters=_iters-1
	done

}

#####################
killHCProcesses() { #
#####################

	#KILL HC PROCESSES
	log "killing all java processes"
	doServers pkill -9 java

	#KILL HADB PROCESSES
	log "killing all hadb processes"
	doServers pkill -9 -f SUNW

}


##################
wipeCluster () { #
##################
	

	let _timer=300
	while (($_timer > 0 )); do
		killHCProcesses
		sleep 10
		isClusterDown
		if [[ "${CLUSTER_DOWN}"  == "TRUE" ]]; then
			break
		else
			log "Honeycomb Processes still running on Cluster. Will Sleep and Check again"
		fi
		let _timer=_timer-10
	done
	if [[ $_timer -le 0 ]]; then
		log "Wipe failed! Unable to shut down honeycomb on all nodes!"
		exit 2;
	fi
	

	# UNMOUNT FS
	log "unmounting filesystem"
	doServers umountall -F nfs
	doServers rm -rf /netdisks
	doServers umount -f /data/0
	doServers umount -f /data/1
	doServers umount -f /data/2
	doServers umount -f /data/3
	doServers /opt/honeycomb/bin/wipe.sh -f


}


#######################
hcProcessesFound () { #
#######################
	NODE_DOWN=FALSE
	_node=$1; shift
	let _portNumber=$_node+2000
	let _nodeNum=$_node+100
	_nodeToCheck=hcb$_nodeNum


	# if node does not respond to pings, assume that is down
	ssh ${ADMIN_NODE} ping ${_nodeToCheck} > /dev/null
	_pingStat=$?
	if [[ "${_pingStat}" -ne 0 ]]; then
		NODE_DOWN=TRUE
		return
	fi 

	ssh ${ADMIN_NODE} -p $_portNumber 'ps -ef' | egrep -s java
	_javaProcessFound=$?
	ssh ${ADMIN_NODE} -p $_portNumber 'ps -ef' | egrep -s SUNW
	_hadbProcessFound=$?
	ssh ${ADMIN_NODE} -p $_portNumber 'ps -ef' | egrep -s jdmk
	_maProcessFound=$?

	if [[ ${_javaProcessFound} -eq 0 ]]; then
		log "JAVA PROCESSES STILL RUNNING ON NODE ${_nodeToCheck}"
	fi

	if [[ ${_hadbProcessFound} -eq 0 ]]; then
		log "SUNWHADB PROCESSES STILL RUNNING ON NODE ${_nodeToCheck}"
	fi

	if [[ ${_maProcessFound} -eq 0 ]]; then
		log "MA PROCESSES STILL RUNNING ON NODE ${_nodeToCheck}"
	fi

	if [[ ${_maProcessFound} -ne 0 && ${_hadbProcessFound} -ne 0  && ${_javaProcessFound} -ne 0 ]]; then
		NODE_DOWN=TRUE;
	fi
}


####################
isClusterDown () { #
####################
	
	let _iters=${NUM_NODES}
	CLUSTER_DOWN=TRUE;
	while (( _iters > 0 )); do
		let _nodeNum=100+_iters
		_nodeName=hcb$_nodeNum
		log "CHECKING NODE ${_nodeName} FOR HONEYCOMB PROCESSES"
		hcProcessesFound ${_iters}
		if [[ "${NODE_DOWN}" == "FALSE" ]]; then
			CLUSTER_DOWN="FALSE"
		fi
		let _iters=_iters-1
	done
}


################
parseArgs () { #
################
    while [[ $# -ne 0 ]]; do
	    cur_opt=$1; shift
	    case $cur_opt in 
		    -help | -h )
			usage;
			die 0;
		    ;;
		    -wipe | -w )
			CLEANSE=TRUE;
		    ;;
		    -iters | -i )
		    	if [[ $# -eq "0" ]]; then
				die 1 "You must specify the number of iterations with the $cur_opt option";
			else
				ITERATIONS=$1; shift
			fi
		    ;;
		    -log | -l )
		    	if [[ $# -eq "0" ]]; then
				die 1 "You must specify the path to a log file with the $cur_opt option";
			else
				ROOT_LOG_DIR=$1; shift
				LOG_DIR=${ROOT_LOG_DIR}/${ME}$$
				mkdir -p ${LOG_DIR}
				LOG_FILE="${LOG_DIR}/${ME}.log"
				HADBPASSWD_SRC="${LOG_DIR}/hadb_passwd"
				STATUSLOG="${LOG_DIR}/$ME.$$.slog"
			fi
		    ;;
		    -script | -sc ) 
		    	if [[ $# -eq "0" ]]; then
				die 1 "You must specify the name of a script with the $cur_opt option";
			else
				POST_REBOOT_SCRIPT=$1; shift
			fi
		    ;;
		    -sleep | -si )
		    	if [[ $# -eq "0" ]]; then
				die 1 "You must specify the a sleep interval with the $cur_opt option";
			else
				SLEEP_VALUE=$1; shift
			fi
		    ;;
		    -reboot | -rb )
		    	REBOOT=TRUE;
		    ;;
		    -greenline | -gl )
		    	GREENLINE=TRUE;
		    ;;
		    -cheat | -sp )
		    	if [[ $# -eq "0" ]]; then
				die 1 "You must specify the cheat's IP address with the $cur_opt option";
			else
				CHEAT_NODE=$1; shift
			fi
		    ;;
		    -adminvip | -av )
		    	if [[ $# -eq "0" ]]; then
				die 1 "You must specify the  admin IP address with the $cur_opt option";
			else
				ADMIN_NODE=$1; shift
			fi
		    ;;
		    -args)  #MUST BE LAST  OPTION ON CL
		    	if [[ $# -eq 0 ]]; then
				ARGS="";
			else
		    		ARGS=$*;
				shift $#;
			fi
		    ;;
		    * )
			die 1 "Unkown option:$cur_opt";
		    ;;
	    esac
    done
}


##########
log () { #
##########
	_logRecord="`date`::$*"
	if [[ ${LOG_MODE} -eq ${TEE_ON} ]]; then
		echo $_logRecord | tee -a $LOG_FILE
	else
		echo $_logRecord >> $LOG_FILE
	fi
}


################
pingNodes () { #
################
	ALL_NODES_REACHED=TRUE;	
	let _iters=${NUM_NODES}
	while (( _iters > 0 )); do
		let _nodeNum=100+_iters
		ssh ${CHEAT_NODE} ping hcb${_nodeNum} > /dev/null
		_pingStat=$?
		if [[ "${_pingStat}" -ne 0 ]]; then
			log "${_nodeNum} not responding to pings"
			ALL_NODES_REACHED=FALSE;	
		fi
		let _iters=_iters-1
	done
	
}


###############
grabLogs () { #
###############
	_rbiter=$1;

	ssh ${CHEAT_NODE} logadm  /var/adm/messages -s 10b
    	scp ${CHEAT_NODE}:/var/adm/messages.0 ${LOG_DIR}/reboot${_rbiter}.log
	let _iters=${NUM_NODES}
	while (( _iters > 0 )); do
		let _nodeNum=100+_iters
		let _hadbDev=_iters-1
		let _portNum=2000+_iters
		_dbCfg=`ssh ${ADMIN_NODE} -p ${_portNum} "cat ${HADB_MGT_CFG} | grep ma.server.dbconfigpath | cut -d= -f2"`
		_maLog=`ssh ${ADMIN_NODE} -p ${_portNum} "cat ${HADB_MGT_CFG} | grep logfile.name | cut -d= -f2"`
		_history=`ssh ${ADMIN_NODE} -p ${_portNum} "cat ${HADB_MGT_CFG} | grep ma.server.dbhistorypath | cut -d= -f2"`
		scp -P ${_portNum} -q ${ADMIN_NODE}:${_maLog} ${LOG_DIR}/reboot${_rbiter}-node${_nodeNum}.malog
		scp -P ${_portNum} -q ${ADMIN_NODE}:${_dbCfg}/honeycomb/${_hadbDev}/cfg ${LOG_DIR}/reboot${_rbiter}-node${_nodeNum}.cfg
		scp -P ${_portNum} -q ${ADMIN_NODE}:${_dbCfg}/honeycomb/${_hadbDev}/meta ${LOG_DIR}/reboot${_rbiter}-node${_nodeNum}.meta
		scp -P ${_portNum} -q ${ADMIN_NODE}:${_history}/honeycomb.dia.${_hadbDev} ${LOG_DIR}/reboot${_rbiter}-node${_nodeNum}.history.dia
		scp -P ${_portNum} -q ${ADMIN_NODE}:${_history}/honeycomb.out.${_hadbDev} ${LOG_DIR}/reboot${_rbiter}-node${_nodeNum}.history.out
		let _iters=_iters-1
	done
	ssh root@${ADMIN_NODE} test -f ${HADBMI}
	hadbi_exists=$?
	if [[ "${hadbi_exists}" != "0" ]]; then
		ssh root@${ADMIN_NODE} cp ${HADBM} ${HADBMI}
	fi
	ssh root@${ADMIN_NODE} ${HADBMI} status --nodes --details honeycomb -W ${HADBPASSWD} > ${LOG_DIR}/honeycomb_status.out 2>&1
}

##############
shutDown() { #
##############
	_iter=$1;shift
	_exitCode=$1;shift
	grabLogs "${_iter}"
	tar cf ${ROOT_LOG_DIR}/${ME}.log.tar -C `dirname ${LOG_DIR}` `basename ${LOG_DIR}`
	log "DONE"
	log "LOG FILE is ${ROOT_LOG_DIR}/${ME}.log.tar"
	exit ${_exitCode}
}

###############
trapStop () { #
###############
	log "Term Signal received. Exiting.."
	shutDown ${CURRENT_ITER} 2
}


########
# main #
########

parseArgs $*

getNumberOfNodes

if [[ "${REBOOT}" == "TRUE" ]]; then
	doServers rm ${NOREBOOT_MARKER}
else
	doServers touch ${NOREBOOT_MARKER}
fi

if [[ "${GREENLINE}" == "TRUE" ]]; then
	doServers rm ${NOSVC_MARKER}
else
	doServers touch ${NOSVC_MARKER}
fi


if [[ -f "${LOG_FILE}" ]]; then
	rm "${LOG_FILE}"
fi

echo "HADBM_ADMINPASSWORD=admin" > "${HADBPASSWD_SRC}"

scp ${CHEAT_NODE}:/var/adm/messages.0 ${LOG_DIR}/pre.log

if [[ "${CLEANSE}" == "TRUE" ]]; then
    wipeCluster
    doServers reboot

    #WAIT FOR ALL NODES TO COME BACK UP
    while ((1)); do
	    sleep 20
	    pingNodes
	    if [[ "${ALL_NODES_REACHED}" != "TRUE" ]]; then	
	  	log "Some nodes still not reachable. Gonna sleep for a while."  
	    else
		break;
	    fi
    done
    sleep 60    
    if [[ "${GREENLINE}"  != "TRUE" ]]; then
    	doServers ${HONEYCOMB_DRIVER} start
    fi
    log "waiting for cluster to come up"
    waitOnHadb "${HADB_RUNNING}" ${INITIAL_HC_BOOT_SLEEP}
    ssh ${CHEAT_NODE} logadm  /var/adm/messages -s 10b
    scp ${CHEAT_NODE}:/var/adm/messages.0 ${LOG_DIR}/start.log
fi

trap trapStop TERM 

log `ssh admin@${ADMIN_NODE} version -v`
log `ssh root@${ADMIN_NODE} ${HADBM} --version`

#REBOOT LOOP
while (( $CURRENT_ITER < $ITERATIONS )); do
	let CURRENT_ITER=$CURRENT_ITER+1;

	# REBOOT HONEYCOMB (NODES WILL ALSO REBOOT IF /CONFIG/NOREBOOT NOT PRESENT ON NODE)
	log "STARTING REBOOT ITER $CURRENT_ITER"
	ssh ${CHEAT_NODE} logger -p err "${ME} STARTING REBOOT ${CURRENT_ITER}"
	log "ABOUT TO REBOOT WITH COMMAND: ${SSHC} admin@${ADMIN_NODE} ${REBOOT_COMMAND}"
	${SSHC} admin@"${ADMIN_NODE}" "${REBOOT_COMMAND}"
	_REBOOT_STATUS=$?
	log "REBOOT (ITER $CURRENT_ITER) RETURNED ${_REBOOT_STATUS}"
	if [[ $_REBOOT_STATUS -ne 0 ]]; then
		log "CLUSTER REBOOT NUM ${CURRENT_ITER} EXITED WITH NON-ZERO EXIT CODE  ${_REBOOT_STATUS} "
		EXIT_CODE=2
		break
	fi

	if [[ ${REBOOT} == "TRUE" ]]; then
	    sleep 60
            #WAIT FOR ALL NODES TO COME BACK UP
    	    while ((1)); do
	        sleep 20
	        pingNodes
	        if [[ "${ALL_NODES_REACHED}" != "TRUE" ]]; then	
	  	    log "Some nodes still not reachable. Gonna sleep for a while."  
	        else
		    break;
	        fi
            done
	else 
		#CHECK NODES FOR HC PROCESSES
		log "CHECKING NODES FOR HONEYCOMB PROCESSES"
		let _shutdownTimer=300
		while (( $_shutdownTimer > 0)); do
			sleep 10
			isClusterDown
			if [[ ${CLUSTER_DOWN} == "TRUE" ]]; then
				break
			fi
			let _shutdownTimer=$_shutdownTimer-10;
		done
		if [[ ${CLUSTER_DOWN} == "FALSE" ]]; then
			log "HONEYCOMB DID SHUTDOWN NOT CLEANLY! PROCESSES STILL RUNNING ON NODES"
			if [[ ${REBOOT} != "TRUE" ]]; then
				EXIT_CODE=2;
				break;
			fi
        	fi
	fi


	if [[ "${GREENLINE}" == "FALSE" ]]; then
    		doServers ${HONEYCOMB_DRIVER} start
	fi
	log "SLEEPING ON REBOOT"
	sleep 180
	pingNodes
	waitOnHadb "${HADB_RUNNING}"
        sleep 60
	log "DONE SLEEPING"


	# CHECK CLUSTER STATE
	_CLUSTER_STATUS=`${SSHC} admin@"${ADMIN_NODE}" sysstat`
	log CLUSTER STATUS ${_CLUSTER_STATUS}

	# EDL: I have commented these hadbm calls out for the time being because we 
	#      believe that hadbm calls can be harmful if the db is not in the correct state
	hadb_domain_status=0  # Always  assume success for the time being
	# CHECK HADB DOMAIN
	#scp ${HADBPASSWD_SRC} ${ADMIN_NODE}:${HADBPASSWD}
	#${SSHC} root@${ADMIN_NODE} /opt/SUNWhadb/4/bin/hadbm listdomain -W ${HADBPASSWD} | tee /tmp/hadb.domain
	#hadb_domain_status=$?
	#if [[ "${hadb_domain_status}" -eq "0" ]]; then
	#	cat /tmp/hadb.domain >> ${LOG_FILE}
	#else
	#	cat /tmp/hadb.domain >> ${LOG_FILE}
	#	log "HADB DOMAIN STATUS QRY RETURNED NON-ZERO EXIT CODE"
	#fi
	#rm /tmp/hadb.domain

	#${SSHC} root@${ADMIN_NODE} /opt/SUNWhadb/4/bin/hadbm status --nodes honeycomb -W ${HADBPASSWD} | tee /tmp/hadb.statusnodes
	#if [[  -f /tmp/hadb.statusnodes ]]; then
	#	cat /tmp/hadb.statusnodes >> ${LOG_FILE}
	#fi
	#rm /tmp/hadb.statusnodes

	# CHECK HADB HC DATABASE
	getHadbStatus
	if [[ "${HADB_STATUS}" == "${HADB_RUNNING}" ]]; then
		hadb_fault_tol=0;
	else	
		hadb_fault_tol=1;
	fi
	log "HABD STATUS: ${HADB_STATUS}"
	log HADB STATUS PER CLI `${SSHC} admin@"${ADMIN_NODE}" hadb status`


	# SO, WHAT'S THE VERDICT
	if [[ "${hadb_domain_status}" -eq "0" &&
	      "${hadb_fault_tol}" -eq "0" ]];then
	        log "REBOOT ${CURRENT_ITER} SUCCEEDED!";
	else
	        log "REBOOT ${CURRENT_ITER} FAILED!";
		if [[ "${GREENLINE}" != "TRUE" ]]; then
			killHCProcesses
		fi
		EXIT_CODE=2;
		break

	fi

	if [[ -n "${POST_REBOOT_SCRIPT}" ]]; then 
		log "ABOUT TO EXECUTE SCRIPT: ${POST_REBOOT_SCRIPT} ${ARGS} "
		eval ${POST_REBOOT_SCRIPT} ${ARGS}
    		waitOnHadb "${HADB_FAULT_TOLERANT}" ${SLEEP_VALUE}
		if [[ ${WAIT_STATUS} != "${WAIT_PASSED_MSG}" ]]; then
	        	log "HADB NO LONGER FAULT TOLERANT AFTER EXECUTING ${POST_REBOOT_SCRIPT}"
			EXIT_CODE=2;
			break
		fi
	fi

	log " " 
	log " " 
done

shutDown "${CURRENT_ITER}" ${EXIT_CODE}

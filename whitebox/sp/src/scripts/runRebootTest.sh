#!/usr/bin/bash
#
# $Id: runRebootTest.sh 12006 2008-05-20 18:45:23Z elambert $
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

LD="/opt/test/lib"
classPath="${LD}/hadbmgt.jar:${LD}/jmx.jar:${LD}/jmxremote.jar:"
classPath="${classPath}:${LD}/jmxremote_optional.jar:${LD}/honeycomb-hctest.jar"
classPath="${classPath}:${LD}/honeycomb-test.jar:${LD}/honeycomb-wbsp.jar"
rebootTest="com.sun.honeycomb.hctest.cases.RebootTest"
RUNLOAD="false"
THREADS=1
LOAD_LOGDIR="/mnt/test/emi-stresslogs"
DATAVIP=""
CLIENTS=""
USE_EXTENDED_MD="1"
LOAD_ERROR="false"
LOCAL_LOGDIR="."
LOAD_PATH="/bin:/sbin:/usr/bin:/usr/sbin:/usr/lib/java/bin:/opt/test/bin"
LOAD_PATH="${LOAD_PATH}:/usr/local/bin:/opt/bin"
LOAD_PATH="${LOAD_PATH}:/usr/i686-pc-linux-gnu/gcc-bin/3.2:/usr/local/pgsql/bin"
LOAD_CP="/opt/test/lib/honeycomb-client.jar:/opt/test/lib/honeycomb-test.jar"
LOAD_CP="${LOAD_CP}:/opt/test/lib/honeycomb-hctest.jar"
EMI="/opt/test/bin/load/emi-load/start-master-stress.sh"
EMI_KILL="/opt/test/bin/load/emi-load/kill-stress-test.sh"
EMI_RERETRIEVE="/opt/test/bin/load/emi-load/reretrieve-failed.sh"
EMI_REQUERY="/opt/test/bin/load/emi-load/requery-failed.sh"
MY_PID=$$
FAIL_ON_LOAD_FAULTS="true"
ME=`basename $0`
RERTV_LOAD_STAT="N/A"
REQRY_LOAD_STAT="N/A"
STORE_LOAD_STAT="PASSED"
RTV_LOAD_STAT="PASSED"
QRY_LOAD_STAT="PASSED"
OP_FAILURE_THRESHHOLD=5
GET_HADB_LOGS="/opt/test/bin/getHadbLogs.sh"

###########
usage() { #
###########
	echo ""
	echo "NAME"
	echo "    ${ME} -- reboot and fault test driver script"
	echo ""
	echo "SYNOPSIS"
	echo "    ${ME}  [-runload [load_args]  -endload ] [ <rebootTestArgs> ]"
	echo ""
	echo "DESCRIPTION"
	echo "    ${ME} is a script used to drive the execution of Honeycomb reboot,"
	echo "    disk-fault and node-fault testcases. Its' main responsibility is to"
	echo "    perform the setup and shutdown work needed by these test cases. As such,"
        echo "    it does the following:" 
	echo "        -if specified, configure the emi load framework on a list of clients"
	echo "        -if specified, start emi-load against the cluster"
	echo "        -run the specified testcase"
	echo "        -stop any client load that was started"
	echo "        -get a copy of all cheat log entries logged during the test"
	echo "        -get a copy of all hadb ma log and history entries logged during the"
	echo "         test"
	echo "        -perform some basic analysis of the emi load output"
	echo ""
        echo "    The options to this script are as follows:"
	echo ""
	echo "    -runload datavip <VIP> logdir <LOAD_LOG_DIR> [ clients <CLIENT_LIST> ] "
	echo "             [ threads <NUM_THRDS> ] [ ignoreLoadFaults] -endload"
	echo "        The -runload option instructs the script to start client load against "
	echo "        the cluster at the begining of the test and to stop the client load at"
	echo "        the end of the test. When the -runload option is encountered, the script"
	echo "        expects that all options following the -runload option but preceeding"
	echo "        the -endload option are arguments to the -runload command. The -runload"
	echo "        option supports the following options:"
	echo "          datavip <VIP>: Name of the cluster against which load is applied"
	echo "          clients <CLIENT_LIST>: A space separated list of clients on which emi"
	echo "                                 load will be run"
	echo "          logdir <LOAD_LOG_DIR>: The path to a directory locacted on each of the"
	echo "                                 clients. This directory will be used to hold "
	echo "                                 output from the emi load framework"
	echo "          threads <NUM_THREADS>: Number of threads the emi load framework shoud"
	echo "                                 use for each type of load operation"
	echo "          ignoreLoadFaults: The script should start and stop load as specified"
	echo "                            but not perform any analysis of the load when the"
	echo "                            test has completed."
	echo "          -endload: instructs script that we are done providing options to the "
	echo "                    -runload command."
	echo ""
        echo "    All other options not contained within a -runload/-enload block will be"
        echo "    directly passed to the RebootTest.class. It is this class which executes "
        echo "    the test."
	echo ""
	echo "LOAD ANALYSIS"
	echo "    If the -runload option was specified on the command line and the "
	echo "    'ignoreLoadFaults' argument was not provided, then once control has been"
	echo "    returned from RebootTest.class, this script will proceed to analyze the emi"
	echo "    load results. This analysis consists of the following steps:"
	echo ""
	echo "    LOG COLLECTION"
	echo "        Once the test case has completed and all load against the cluster"
	echo "        has been halted, the script will collect all the emi output files and"
	echo "        and place them in the log directory."
	echo ""
	echo "    VERIFY STORE OPERATIONS"
	echo "        Examine all emi store log files to ensure that store operations were"
	echo "        succeeding and that no un-expected exceptions were encountered by the"
	echo "        client store threads. If no stores completed, or if one or more "
	echo "        unexpected exception was encountered then the test is considered failed."
	echo "        "
	echo "    VERIFY RETRIEVE OPERATIONS"
	echo "        Examine all emi retrieve log files. If one or more retrieve operation"
	echo "        failed during the execution of the test attempt to re-retrieve those "
	echo "        OIDs. The re-retrieves are performed to ensure that initial failure to"
	echo "        retrieve was not due to data loss. If the re-retrieve still fails to "
	echo "        retrieve one or mode OIDs, then the test is considered failed."
	echo ""
	echo "        Once all the re-retrieves have completed, verify that no retrieve"
	echo "        operations failed due to an un-expected exceptions. If an expected "
	echo "        exception was encountered, the test is considered failed."
	echo ""
	echo "        Determine what percentage of retrieve operations failed. If the percent"
	echo "        is greater then ${OPS_FAILURE_THRESHOLD}% then the test is considered failed."
	echo ""
	echo "    VERIFY QUERY OPERATIONS"
	echo "        Examine all emi query log files. If one or more query operation"
	echo "        failed during the execution of the test attempt to re-execute those "
	echo "        queries. The re-queries are performed to ensure that initial failure to"
	echo "        retrieve was not due to data loss. If the re-retrieve still fails to "
	echo "        retrieve one or mode OIDs, then the test is considered failed."
	echo ""
	echo "        Once all the re-queries have completed, verify that no query operation"
	echo "        operations failed due to an un-expected exceptions. If an expected "
	echo "        exception was encountered, the test is considered failed."
	echo ""
	echo "        Determine what percentage of query operations failed. If the percent"
	echo "        is greater then ${OPS_FAILURE_THRESHOLD}% then the test is considered failed."
	echo ""
	echo "OUTPUT FILES"
	echo "    In order to obtain a complete record of activity during the execution of "
	echo "    the test, the script captures a variety of files and places them in a "
	echo "    directory specified by the user. The path to this directory is determined by"
	echo "    scanning the arguments which the user expects to be passed to the RebootTest"
	echo "    (-L || --logdir). For the purposes of this section, the path to this"
	echo "    directory shall be referred to as the <LOG_DIR>."
	echo ""
	echo "    MESSAGE FILES"
	echo "        The script will capture all of the system log entries for the cheat"
	echo "        node. It does so by copy all of the /var/adm/message files that have"
	echo "        entries during the time which the test was running. These message files"
	echo "        are copied as is into <LOG_DIR>"
	echo ""
	echo "    HADB FILES"
	echo "        The script will capture the relevant HADB log files which contain"
	echo "        entries during the time which the test was executing. The files are "
	echo "        placed in the <LOG_DIR>/hadb-logs directory. This script does not "
	echo "        actually delegates the job of retrieving these files to a script called"
	echo "        getHadbLogs.sh."
	echo ""
	echo "    EMI OUTPUT FILES"
	echo "        As mentioned in the LOAD ANALYSIS section, the script will capture all"
	echo "        the emi load output generated on the clients. It does so by copying "
	echo "        the following files from each client:"
	echo "            <LOAD_LOGDIR>/store.<FSIZE_TYPE>.out [store ops stdout]"
	echo "            <LOAD_LOGDIR>/store.<FSIZE_TYPE>.err [store ops stderr]"
	echo "            <LOAD_LOGDIR>/query-each-repeat.<FSIZE_TYPE>.out [query ops stdout]"
	echo "            <LOAD_LOGDIR>/query-each-repeat.<FSIZE_TYPE>.err [query ops stderr]"
	echo "            <LOAD_LOGDIR>/retrieve-each-repeat.<FSIZE_TYPE>.out [rtv ops stdout]"
	echo "            <LOAD_LOGDIR>/retrieve-each-repeat.<FSIZE_TYPE>.err [rtv ops stderr]"
	echo "        In the list above, the variable <FSIZE_TYPE> has one of the following"
	echo "        values: small, medium,large, xlarge. The files are copied into the "
	echo "        directory <LOG_DIR>/load. Inorder to prevent collisions resulting from"
	echo "        a file with same name being copied from multiple clients and to aid in"
	echo "        determining what client a file came from, as part of the copy process"
	echo "        the file will be renamed to include the name of the client. The new "
	echo "        name abides by the following pattern: <OP>.<FSIZE_TYPE>.<CLIENT>.<TYPE>"
	echo "        where:"
	echo "             <OP> = store | retrieve-each-repeat | query-each-repeat"
	echo "             <CLIENT> = name of client as passed into the script"
	echo "             <TYPE> = out | err"
	echo ""
	echo "        As part of the load analysis, some files be generated and placed in the "
	echo "        <LOG_DIR>/load directory. They include the following:"
	echo "             store.out: Master list of store operations from all clients."
	echo "             store.err: Master list of all store operations that failed."
	echo "             store.err.txt: Master file holding stderr for all store operations"
	echo "             store.err.unf: List of all store ops that failed due to an "
	echo "                            an unexpected exception."
	echo "             rtv.out: Master list of retrieve operations from all clients."
	echo "             rtv.err: Master list of all retrieve operations that failed."
	echo "             rtv.err.txt: Master file holding stderr for all retrieve operations"
	echo "             rtv.err.unf: List of all retrieve ops that failed due to an "
	echo "                            an unexpected exception."
	echo "             rertv.out: List of all retrieve ops executed as part of reretrieve"
	echo "             rertv.err: List of all failed re-retrieve ops"
	echo "             rertv.err.txt: Master file holding standard error output for all"
	echo "                            re-retrieve operations."
	echo "             qry.out: Master list of query operations from all clients."
	echo "             qry.err: Master list of all query operations that failed."
	echo "             qry.err.txt: Master file holding stderr for all query operations"
	echo "             qry.err.unf: List of all query ops that failed due to an "
	echo "                            an unexpected exception."
	echo "             reqry.out: List of all query ops executed as part of requery"
	echo "             reqry.err: List of all failed re-query ops"
	echo "             reqry.err.txt: Master file holding standard error output for all"
	echo "                            re-query operations."
        echo ""
	echo "EXIT CODE"
	echo "    If the RebootTest exits with a zero return-code and no load failures are "
	echo "    detected (or the ignoreLoadFaults option was specified) then the scipt"
	echo "    will exit with a return code of zero. Otherwise, the script will exit"
	echo "    with a return code of 1."
	echo ""
	echo "EXAMPLES"
	echo "    Here is an example of how to execute the script so that it will run the "
	echo "    100 reboot test with load from 4 clients."
	echo ""
	echo "    $  ${ME} -runload logdir /mnt/test/reboot001 datavip dev305-data \ " 
	echo "       clients \"10.7.227.1 10.7.227.3 10.7.227.4 10.7.227.6\" -endload \ "
	echo "       -I 100 -L /export/home/root/reboot001 -CO reboot"
	echo ""
	echo "SEE ALSO"
	echo "    getHadbLogs.sh, com.sun.honeycomb.hctest.cases.RebootTest"
	echo ""
	echo "KNOWN ISSUES"
	echo "    Re-retrieve operations will hang on the clients. For unknown reasons, the"
	echo "    re-retrieve operation can hang on one or more clients. This prevents the"
	echo "    script from completing. To work around this, kill the re-retrieve ops that"
	echo "    running on the cheat via the following command:"
	echo "    $ pkill -f reretrieve-failed"
	echo "    Once the script has completed you will then need to manually re-retrieve"
	echo "    any of the retrieve ops that failed and were not retried."
	echo ""
	echo "    Re-query of certain OIDs may consistenly fail. If the initial store of an "
	echo "    OID fails to insert the metadata into HADB, then there is no assurances that"
	echo "    re-query will be able to query the OID until a full data-doctor cycle has "
	echo "    complete after the OID was stored. If the re-qry runs before the data "
	echo "    doctor cycle has finished, some queries may consistently fail."
	echo ""


}


##########
log () { #
##########
	echo `date`":"$*
}


##############
createEnv() { #
##############
	envFile=`mktemp`
	cat > ${envFile} << EOF
export PATH=${LOAD_PATH}
DATAVIP="${DATAVIP}"
CLASSPATH="${LOAD_CP}" 
LOGDIR="${LOAD_LOGDIR}"
NUMTHREADS="${THREADS}"
SLEEPTIME=15
RUNONCETESTS=0
STARTSTORES=1
STARTRETRIEVES=1
STARTQUERIES=1
STARTDELETES=0
EXTENDED_METADATA="${USE_EXTENDED_MD}"
CONTENT_VERIFICATION=1
PATTERN=binary
STORE_CHECK_INDEXED=0
SOCKET_TIMEOUT_SEC=0
EOF
}


##################
setUpClient () { #
##################
	for i in `echo $CLIENTS`; do
		log "setting up client $i"
		ping $i > /dev/null 2>&1
		if [ $? -ne 0 ]; then
			log "Client $i is not reachable. Exiting"
			exit 1
		fi
		ssh -q -o StrictHostKeyChecking=no $i rm -rf ${LOAD_LOGDIR}
		ssh -q -o StrictHostKeyChecking=no $i mkdir -p ${LOAD_LOGDIR}
		scp -q ${envFile} $i:/opt/test/bin/load/emi-load/ENV
	done
}


################
startLoad () { #
################
	for client in `echo ${CLIENTS}`; do
		log "starting load on $client"
		ssh -q -o StrictHostKeyChecking=no $client \
		"nohup $EMI >> ${LOAD_LOGDIR}/master.out 2>>${LOAD_LOGDIR}/master.err &"
	done
}


###############
stopLoad () { #
###############
	for client in `echo ${CLIENTS}`; do
		log "Stopping load on ${client}"
		ssh -q -o StrictHostKeyChecking=no $client ${EMI_KILL}
	done
}


#########################
getErrorPercentage () { #
#########################
	outFile=$1
	errFile=$2
	op=$3
	
	if [ "$outFile" == "" ] ||  [ ! -f $outFile ] ; then
		numOps=0
	else
		numOps=`cat $outFile | wc -l | sed -e 's| ||g'`
	fi
	
	if [ "$errFile" == "" ] || [ ! -f $errFile ]; then
		numErr=0
	else
		numErr=`cat $errFile | wc -l | sed -e 's| ||g'`
	fi
	
	if [ $numOps -eq 0 ]; then
		percent=0
	else
		percent=$(echo "scale=10; ($numErr/$numOps) * 100" | bc)
		percent=${percent%.*}
		percent=${percent:-0}
	fi
	
	case $op in
		store) 
			NUM_STORE_OPS=$numOps
			NUM_STORE_ERRS=$numErr
			PER_STORE_ERRS=$percent
			;;
		retrieves) 
			NUM_RTV_OPS=$numOps
			NUM_RTV_ERRS=$numErr
			PER_RTV_ERRS=$percent
			;;
		queries)
			NUM_QRY_OPS=$numOps
			NUM_QRY_ERRS=$numErr
			PER_QRY_ERRS=$percent
			;;
	esac
	
}


#################
verifyLoad () { #
#################

	ioe="STR ERR java.io.IOException::"
	ae="STR ERR com.sun.honeycomb.common.ArchiveException::"
	se="${ae}java.net.SocketException"
	he="${ae}org.apache.commons.httpclient.HttpException"
	ce="${ae}java.net.ConnectException"
	filteredExceptions="$ioe|$se|$he|$ce"
	
	log "Verifying stores"
	# verify that I actually stored some stuff, if i didn't
	# no point in checking for rtvs or querys
	if  ! test -s $STORE_OUT; then
		log "ERROR: $STORE_OUT is empty. No OIDs stored during test?"
		LOAD_ERROR="true"
		STORE_LOAD_STAT="FAILED"	
		return
	fi
	
	getErrorPercentage $STORE_OUT $STORE_ERR store
	egrep -v "$filteredExceptions" $STORE_ERR > $STORE_ERR_UNF
	if  test -s $STORE_ERR_UNF && [ ${FAIL_ON_LOAD_FAULTS} == "true" ]; then
			log "ERROR: One or more store operations failed." \
			    "See $STORE_ERR_UNF for complete list"
			LOAD_ERROR="true"
			STORE_LOAD_STAT="FAILED"
	fi
	
	if [ -s $STORE_ERR_TEXT ]; then
		echo "WARNING:standard err for store ops not empty. See $STORE_ERR_TEXT"
	fi
	
	log "Verifying retrieve operations."
	if test -s $RTV_ERR ; then
		for i in `echo $CLIENTS`; do
			log "Starting reretrieve on $i"
			log "See $LOCAL_LOADDIR/${i}_retrieve_retry.out for standard out"
			log "See $LOCAL_LOADDIR/${i}_retrieve_retry.err for standard err"
			ssh -q -o StrictHostKeyChecking=no $i $EMI_RERETRIEVE \
			> $LOCAL_LOADDIR/${i}_retrieve_retry.out \
			2>$LOCAL_LOADDIR/${i}_retrieve_retry.err &
		done
		log "Waiting on re-retreives. This may take a while."
		wait
		for i in `echo $CLIENTS`; do
			if  ssh $i test -f ${RERETRIEVES_OUT}; then
				scp -q ${i}:${RERETRIEVES_OUT} $LOCAL_LOADDIR/rertrieve.$i.out
			fi
			if ssh $i test -f ${RERETRIEVES_ERR}; then
				scp -q ${i}:${RERETRIEVES_ERR} \
				$LOCAL_LOADDIR/rertrieve.$i.err.txt
			fi
			if [ -f ${LOCAL_LOADDIR}/rertrieve.${i}.out ]; then
				cat ${LOCAL_LOADDIR}/rertrieve.${i}.out >> $RTV_RETRY_ERR
			fi
			if [ -f  ${LOCAL_LOADDIR}/rertrieve.${i}.err.txt ]; then
				cat ${LOCAL_LOADDIR}/rertrieve.${i}.err.txt \
				>> $RTV_RETRY_ERR_TEXT 
			fi
		done
		
		if [ "${FAIL_ON_LOAD_FAULTS}" == "true" ] &&
		( [ -s $RTV_RETRY_ERR  ] ||  [ -s $RTV_RETRY_ERR_TEXT ] ) ; then
			log "ERROR: Failed to retrieve one or more OID. "\
			"Possible data corruption."
			log "ERROR: see $RTV_RETRY_ERR for list of OIDs"
			log "ERROR: see $RTV_RETRY_ERR_TEXT for standard error output"
			LOAD_ERROR="true"
			RERTV_LOAD_STAT="FAILED"
		else
			RERTV_LOAD_STAT="PASSED"
		fi
		
	fi
	if [ -s $RTV_ERR_TEXT ]; then
		echo "WARNING: standard error for rtv ops not empty. See $RTV_ERR_TEXT"
	fi
	
	getErrorPercentage $RTV_OUT $RTV_ERR retrieves
	if [ ${PER_RTV_ERRS} -ge $OP_FAILURE_THRESHHOLD ]; then
		RTV_LOAD_STAT="FAILED"
	fi
	
	log "verifying query operations"
	if  test -s $QRY_ERR ; then
		for i in `echo $CLIENTS`; do
			log "Starting re-query on $i"
			log "See $LOCAL_LOADDIR/${i}_qry_retry.out for standard out"
			log "See $LOCAL_LOADDIR/${i}_qry_retry.err for standard err"
			ssh -q -o StrictHostKeyChecking=no $i $EMI_REQUERY \
			> $LOCAL_LOADDIR/${i}_qry_retry.out \
			2>$LOCAL_LOADDIR/${i}_qry_retry.err &
		done
		log "Waiting on re-queries. This may take a while."
		wait
		for i in `echo $CLIENTS`; do
			if ssh $i test -f ${REQRY_OUT}; then
				scp -q $i:$REQRY_OUT $LOCAL_LOGDIR/reqry.$i.out
			fi
			
			if ssh $i test -f ${REQRY_ERR}; then
				scp -q $i:$REQRY_ERR $LOCAL_LOADDIR/reqry.$i.err.txt
			fi
			if [ -f $LOCAL_LOADDIR/reqry.$i.out ]; then
				cat $LOCAL_LOADDIR/reqry.$i.out >> $QRY_RETRY_ERR
			fi
			if [ -f $LOCAL_LOADDIR/reqry.$i.err.txt ]; then
				cat $LOCAL_LOADDIR/reqry.$i.err.txt >> $QRY_RETRY_ERR_TEXT 
			fi
		done
		
		if [ "${FAIL_ON_LOAD_FAULTS}" == "true" ] &&  
		( [ -s $QRY_RETRY_ERR ]  ||  [ -s $QRY_RETRY_ERR_TEXT ] ); then
			log "ERROR: Failed to query one or more OID. Database wiped?"
			log "ERROR: see $QRY_RETRY_ERR for list of OIDs"
			log "ERROR: see $QRY_RETRY_ERR_TEXT for standard error output"
			LOAD_ERROR="true"
			REQRY_LOAD_STAT="FAILED"
		else
			REQRY_LOAD_STAT="PASSED"
		fi
		
	fi
	getErrorPercentage $QRY_OUT $QRY_ERR queries
	if [ ${PER_QRY_ERRS} -ge $OP_FAILURE_THRESHHOLD ]; then
		QRY_LOAD_STAT="FAILED"
	fi
	if [ -s $QRY_ERR_TEXT ]; then
		echo "WARNING: standard error for qry ops not empty. See $QRY_ERR_TEXT"
	fi
	
}


###############
grabLogs () { #
###############
	# find the log file that contains our start marker
	msg_file="/var/adm/messages"
	msg_file_suffix=""
	found="false"
	
	while [ true ]; do
		
		if [ "${msg_file_suffix}" == "" ]; then
			full_path="${msg_file}"
		else
			full_path="${msg_file}.${msg_file_suffix}"
		fi
		
		if [ ! -f "${full_path}" ] && [ ! -f "${full_path}.gz" ]; then
			break
		fi 
	
		gzegrep -s "${START_MARKER}" "${full_path}"
		if [ $? -eq 0 ]; then
			found="true"
			break;
		fi
		
		if [ "${msg_file_suffix}" == "" ]; then
			msg_file_suffix=0
		else
			let msg_file_suffix=$((msg_file_suffix + 1 ))
		fi
	done
	
	if [ "${found}" == "true" ]; then
		cp -f "${msg_file}" "${LOCAL_LOGDIR}"
		if [ "${msg_file_suffix}" != "" ]; then
			let index=0
			while [ ${index} -le ${msg_file_suffix} ]; do
				cur_msg_file="${msg_file}.${index}"
				if [ ${index} -gt 1 ]; then
					cur_msg_file="${cur_msg_file}.gz"
				fi
				cp -f "${cur_msg_file}" "${LOCAL_LOGDIR}"
				let index=$((index + 1 )) 
			done
		fi
	else
		log "Was unable to find the Test Start Marker in the log files."
		log "MARKER = ${START_MARKER}"
	fi
	
	mkdir -p ${HADB_LOG_OUT}
	log "Grabbing HADB logs"
	${GET_HADB_LOGS} ${START_TIME} ${END_TIME} ${HADB_LOG_OUT}
	
	if [ "$RUNLOAD" != "true" ]; then
		return
	fi
	
	
	mkdir -p ${LOCAL_LOADDIR}
	
	# grab the emi load out and error files
	# from the clients
	loadFileSizes="small medium large xlarge"
	for client in `echo ${CLIENTS}`; do
		
		for fsize in $loadFileSizes; do
			
			log "getting output files for ${fsize} operations from $client"
			
			scp -q -o StrictHostKeyChecking=no \
			$client:$LOAD_LOGDIR/store.$fsize.out \
			$LOCAL_LOADDIR/store.$fsize.$client.out
			
			scp -q -o StrictHostKeyChecking=no \
			$client:$LOAD_LOGDIR/store.$fsize.err \
			$LOCAL_LOADDIR/store.$fsize.$client.err
			
			scp -q -o StrictHostKeyChecking=no \
			$client:$LOAD_LOGDIR/query-each-repeat.$fsize.out \
			$LOCAL_LOADDIR/query-each-repeat.$fsize.$client.out
			
			scp -q -o StrictHostKeyChecking=no \
			$client:$LOAD_LOGDIR/query-each-repeat.$fsize.err \
			$LOCAL_LOADDIR/query-each-repeat.$fsize.$client.err
			
			scp -q -o StrictHostKeyChecking=no \
			$client:$LOAD_LOGDIR/retrieve-each-repeat.$fsize.out \
			$LOCAL_LOADDIR/retrieve-each-repeat.$fsize.$client.out
			
			scp -q -o StrictHostKeyChecking=no \
			$client:$LOAD_LOGDIR/retrieve-each-repeat.$fsize.err \
			$LOCAL_LOADDIR/retrieve-each-repeat.$fsize.$client.err
			
		done
		
	done 
	
	# create master output and error files for each operation
	cat $LOCAL_LOADDIR/store.*.out >> $STORE_OUT
	cat $LOCAL_LOADDIR/store.*.err >> $STORE_ERR_TEXT
	egrep 'STR ERR' $STORE_OUT > $STORE_ERR
	cat $LOCAL_LOADDIR/query-each-repeat.*.out >> $QRY_OUT
	cat $LOCAL_LOADDIR/query-each-repeat.*.err >> $QRY_ERR_TEXT
	egrep 'QRY ERR' $QRY_OUT > $QRY_ERR
	cat $LOCAL_LOADDIR/retrieve-each-repeat.*.out >> $RTV_OUT
	cat $LOCAL_LOADDIR/retrieve-each-repeat.*.err >> $RTV_ERR_TEXT
	egrep 'RTV ERR' $RTV_OUT > $RTV_ERR
}

logFullness () {
	fullness=`ssh admin@10.123.45.200 df -h`
	log $fullness
}


########
# MAIN #
########
START_TIME=`date +%Y-%m-%d_%H:%M:%S`
# workaround an issue where the line
# "Warning: Permanently added 'localhost' (RSA) to the list of known hosts."
# causes the test to fail on the first run after reboot
ssh localhost hostname >/dev/null 2>&1

# for ease of re-running test based on log files that are archived
log "runRebootTest.sh invoked with args $*"

#parseArgs
if [ $1 == "--help" ] || [ $1 == "-help" ] || [ $1 == "-h" ]; then
	usage
	exit 0
fi

if [ $1 == "-runload" ]; then
	shift
	RUNLOAD="true"
	while [ $# -ne 0 ]; do
		curArg=$1; shift
		if [ "${curArg}" == "-endload" ]; then
			endLoadFound="true"
			break
		elif [ "${curArg}" == "threads" ]; then
			if [ $# -eq 0 ]; then
				log "you must specify the number of load threads"
				log "you wish each VM to spawn with ${curArg}"
				usage
				exit 1;
			else 
				THREADS=$1;shift
			fi
		elif [ "${curArg}" == "logdir" ]; then
			if [ $# -eq 0 ]; then
				log "you must specify the path to client log dir with ${curArg}"
				usage
				exit 1;
			else 
				LOAD_LOGDIR=$1; shift
			fi
		elif [ "${curArg}" == "datavip" ]; then
			if [ $# -eq 0 ]; then
				log "you must specify the datavip with ${curArg}"
				usage
				exit 1;
			else 
				DATAVIP=$1; shift
			fi
		elif [ "${curArg}" == "clients" ]; then
			if [ $# -eq 0 ]; then
				log "you must specify a client list with ${curArg}"
				usage
				exit 1;
			else 
				CLIENTS=$1; shift
			fi
		elif [ "${curArg}" == "ignoreLoadFaults" ]; then
			FAIL_ON_LOAD_FAULTS="false"
		else 
			log "Unknown load option: ${curArg}"
			exit 1
		fi

	done
fi

# find the log dir being passed RebootTest
for curArg in $*; do
	if [ "${logDirNext}" == "true" ]; then
		LOCAL_LOGDIR="${curArg}";
		break;
	fi
	if [ "${curArg}" == "-L" ] || [ "${curArg}" == "--logdir" ]; then
		logDirNext="true";
	fi 
done

LOCAL_LOADDIR="${LOCAL_LOGDIR}/load"
STORE_OUT="${LOCAL_LOADDIR}/store.out"
STORE_ERR="${LOCAL_LOADDIR}/store.err"
STORE_ERR_TEXT="${LOCAL_LOADDIR}/store.err.txt"
STORE_ERR_UNF="${LOCAL_LOADDIR}/store.err.unf"
RTV_OUT="${LOCAL_LOADDIR}/rtv.out"
RTV_ERR="${LOCAL_LOADDIR}/rtv.err"
RTV_ERR_TEXT="${LOCAL_LOADDIR}/rtv.err.txt"
RTV_ERR_UNF="${LOCAL_LOADDIR}/rtv.err.unf"
QRY_OUT="${LOCAL_LOADDIR}/qry.out"
QRY_ERR="${LOCAL_LOADDIR}/qry.err"
QRY_ERR_TEXT="${LOCAL_LOADDIR}/qry.err.txt"
QRY_ERR_UNF="${LOCAL_LOADDIR}/qry.err.unf"
QRY_RETRY_ERR="${LOCAL_LOADDIR}/reqry.err"
QRY_RETRY_ERR_TEXT="${LOCAL_LOADDIR}/reqry.err.txt"
RTV_RETRY_ERR="${LOCAL_LOADDIR}/rertv.err"
RTV_RETRY_ERR_TEXT="${LOCAL_LOADDIR}/rertv.err.txt"
START_MARKER="STARTING TEST $ME: PID=${PID}"
END_MARKER="ENDING TEST $ME: PID=${PID}"
RERETRIEVES_OUT="${LOAD_LOGDIR}/final-failed-retrieves.out"
RERETRIEVES_ERR="${LOAD_LOGDIR}/final-failed-retrieves.err"
REQRY_OUT="${LOAD_LOGDIR}/final-failed-qry.out"
REQRY_ERR="${LOAD_LOGDIR}/final-failed-qry.out"
HADB_LOG_OUT="${LOCAL_LOGDIR}/hadb-logs"

# verify that we have the hctest.jar file 
if [ ! -f "/opt/test/lib/honeycomb-hctest.jar" ]; then
	log "Missing the honeycomb-hctest.jar file."
	log "Please copy the latest version of the file to "
	log "/opt/test/lib and try again."
	exit 1
fi

#prep logs
# ROLL LOGS
log "Rolling logs"
logadm -s 10b /var/adm/messages
logger "${START_MARKER}"

#start load if requested
if [ "${RUNLOAD}" == "true" ]; then
	if  [ "${endLoadFound}" != "true" ]; then
		log "You specified -runload on the command line, "
		log "but no corresponding -endload argument found"
		usage
		exit 1
	fi
	if [ "${DATAVIP}" == "" ]; then
		log "You must specify the datavip in order to run load"
		usage
		exit 1
	fi
	if [ "${CLIENTS}" == "" ]; then
		log "You must specify a client list in order to run load"
		usage
		exit 1
	fi
	createEnv
	setUpClient
	startLoad
fi

logFullness
java -Dcom.sun.honeycomb.test.util.RunCommand.noValidateCmd=true \
-cp ${classPath} ${rebootTest} $*
testExit=$?
logFullness
END_TIME=`date +%Y-%m-%d_%H:%M:%S`
if [ "${testExit}" != "0" ]; then
	log "Test exited with non-zero exit code"
	test_loop_status="FAILED"
else 
	log "Test completed successfully"
	test_loop_status="PASSED"
fi

#stop load if started 
if [ "${RUNLOAD}" == "true" ]; then
	stopLoad
	log "sleeping to let load ramp down"
	sleep 60
fi

logger "$END_MARKER"
grabLogs

if [ "$RUNLOAD" == "true" ]; then
	verifyLoad
	log " "
	log " "
	log " Test Loop  : ${test_loop_status}"
	log " STR Load   : ${STORE_LOAD_STAT} [ ops = ${NUM_STORE_OPS} err = ${NUM_STORE_ERRS}  ( ${PER_STORE_ERRS} % ) ]"
	log " RTV Load   : ${RTV_LOAD_STAT} [ ops = ${NUM_RTV_OPS} err = ${NUM_RTV_ERRS}  ( ${PER_RTV_ERRS} % ) ]"
	log " RTV Retry  : ${RERTV_LOAD_STAT}"
	log " QRY Load   : ${QRY_LOAD_STAT} [ ops = ${NUM_QRY_OPS} err = ${NUM_QRY_ERRS}  ( ${PER_QRY_ERRS} % ) ]" 
	log " QRY Retry  : ${REQRY_LOAD_STAT}"
	log " "
	log " "
fi

if [ "${testExit}" != "0" ] || [ ${LOAD_ERROR} != "false" ]; then
	log "TEST FAILED"
	exit 1
else
	log "TEST PASSED"
	exit 0
fi



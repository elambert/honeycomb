#!/bin/bash
#
# $Id: getAllOIDs.sh 10858 2007-05-19 03:03:41Z bberndt $
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

PERF_TEST="/opt/performance/performance_test.sh"
ME=$0


############
usage () { #
############
	echo "${ME} <LOG_DIR>"
	echo ""
	echo "This script examines the output of a prior performance_test.sh "
	echo "run. For each client that participated in the performance test,"
	echo "it gathers all the OIDs stored by that client in a single "
	echo "Master list of OIDs. It then retrieves all the oids. Any errors"
	echo "it encounters while retrieving an OID are placed in the master.err"
	echo "file in the <LOG_DIR>. If no errors are encountered while retrieving"
	echo "then the script exits with a zero exit code, otherwise it will exit"
	echo "with a non-zero exit code."
	echo ""
	echo "This script parses the performance_test.sh script to determine the "
	echo "the list of clients and the name of the cluster. As such, you should"
	echo "run it on the same client you used to launch the performance test."
	echo ""
	echo "OPTIONS:"
	echo "        <LOG_DIR>: The log directory passed into performance_test.sh"
	echo ""
}


########
# main #
########

if [[ $# -eq 0 || $1 == "-help" ]]; then
	usage
	exit 1
fi

LOG_DIR=$1;shift

if [[ ! -d ${LOG_DIR} ]]; then
	echo "Could not find directory ${LOG_DIR}"
	usage 
	exit 1
fi

if [[ ! -f ${PERF_TEST} ]]; then
	echo "Could not find performance script ${PERF_TEST}"
	usage 
	exit 1
fi

CLIENT_LIST=`cat ${PERF_TEST} | grep "^CLIENTS=" | cut -d\" -f2`
CLUSTER=`cat ${PERF_TEST} | grep "^CLUSTER=" | cut -d= -f2`

CP=/opt/performance/honeycomb-perftest.jar:/opt/performance/honeycomb-client.jar:/opt/performance/honeycomb-test.jar
OID_MASTER_LIST=${LOG_DIR}/master.oid

for i in `echo ${CLIENT_LIST}`; do
echo $i
	ssh $i "find ${LOG_DIR} -name \*Store\* | egrep -v analyze | xargs cat > ${OID_MASTER_LIST}"
	ssh $i "cat ${OID_MASTER_LIST} | /usr/lib/java/bin/java -classpath ${CP} RetrieveStress ${CLUSTER}-data 20 -1 > ${LOG_DIR}/stress.ret.out 2> ${LOG_DIR}/stress.ret.err" &
done

echo waiting for retrieves to complete
wait
echo retrieves  completed
for i in `echo ${CLIENT_LIST}`; do
	ssh $i test -f ${LOG_DIR}/stress.ret.err
	errFileExists=$?
	if [[ "${errFileExists}" -eq "0" ]];then
		ssh $i "cat  ${LOG_DIR}/stress.ret.err" >> ${LOG_DIR}/master.err  
	fi
done

test -s ${LOG_DIR}/master.err
isEmpty=$?
if [[ "${isEmpty}" -eq "0" ]]; then
	echo "Failed to retreive one or more OID. Please check ${LOG_DIR}/master.err"
	exit 1
else
	echo "Retreived all oids"
	exit 0
fi


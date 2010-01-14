#!/bin/bash
#
# $Id: stanford_local_test_analysis.sh 10845 2007-05-19 02:31:46Z bberndt $
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

# Run the Ingest and Retrieve test and analysis locally on this machine -  so 
# another client can ssh in and invoke this script to run a test and analysis.

OPER=$1
CLUSTER=$2
PROCS=$3

RUNTIME=$4
LOGDIR=$5

HOSTNAME=`hostname`

OUTFILE="${LOGDIR}/${OPER}/${HOSTNAME}.${CLUSTER}.${OPER}.out"

ERRFILE="${LOGDIR}/${OPER}/${HOSTNAME}.${CLUSTER}.${OPER}.err"

if [ "$OPER" = "Store" ]; then
  echo [`date`] OUTPUT FILE is ${OUTFILE}
  echo [`date`] Store Digital Objects >> ${ERRFILE}
  java -classpath .:honeycomb-perftest.jar:honeycomb-client.jar:honeycomb-test.jar StanfordIngestStress ${CLUSTER}-data ${PROCS}  ${RUNTIME} 1>>${OUTFILE} 2>>${ERRFILE}

elif [ "$OPER" = "Retrieve" ]; then
  echo [`date`] OUTPUT FILE is ${OUTFILE}
  echo [`date`] Retrieve via UUIDs >> ${ERRFILE}
  java -classpath .:honeycomb-perftest.jar:honeycomb-client.jar:honeycomb-test.jar StanfordRetrieveStress ${CLUSTER}-data ${PROCS} ${RUNTIME} 1>>${OUTFILE} 2>>${ERRFILE}

elif [ "$OPER" = "AnalyzeStore" ]; then
  INFILE="${LOGDIR}/Store/${HOSTNAME}.${CLUSTER}.Store.out"
  cat ${INFILE} | java -classpath .:honeycomb-perftest.jar:honeycomb-client.jar:honeycomb-test.jar StanfordAnalyze ${PROCS} 

elif [ "$OPER" = "AnalyzeRetrieve" ]; then
  INFILE="${LOGDIR}/Retrieve/${HOSTNAME}.${CLUSTER}.Retrieve.out"
  cat ${INFILE} | java -classpath .:honeycomb-perftest.jar:honeycomb-client.jar:honeycomb-test.jar StanfordAnalyze ${PROCS}  

else
  echo "Operation type ${OPER} unknown"

fi

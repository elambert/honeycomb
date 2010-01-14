#!/bin/bash
#
# $Id: run_test.sh 10845 2007-05-19 02:31:46Z bberndt $
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

# Run the various tests locally on this machine - utility script so 
# another client can ssh in and invoke this script to run a test.

# Possible operations = Store, MDStore, AddMD, Retrieve, Delete, Query
OPER=$1

# Iteration number to name files with
ITER=$2

CLUSTER=$3
PROCS=$4

if [ "$OPER" = "Query" ]; then
  QUERYTYPE=$5
else
  SIZE=$5
fi

RUNTIME=$6
NUMCLPROC=$7
LOGDIR=$8
OIDFILE=$9
HOSTNAME=`hostname`

if [ "$OPER" = "Query" ]; then
  OUTFILE="${LOGDIR}/${ITER}/${ITER}.${HOSTNAME}.${OPER}.${CLUSTER}.${NUMCLPROC}.${QUERYTYPE}"
else
  OUTFILE="${LOGDIR}/${ITER}/${ITER}.${HOSTNAME}.${OPER}.${CLUSTER}.${NUMCLPROC}.${SIZE}"
fi

#hack to get around inability to pass 10 arguments to a script
#Delete type and the NUMCLPROCS are appended together to pass both in
# This extracts out NUMCLPROCS
if [ "$OPER" = "Delete" ]; then
  NUMCLPROC=`expr "$NUMCLPROC" : '\([0-9]*x[0-9]*\)'`
fi

ERRFILE="${LOGDIR}/${ITER}.${HOSTNAME}.${CLUSTER}.${NUMCLPROC}.err"

echo [`date`] OUTPUT FILE is ${OUTFILE}

if [ "$OPER" = "Store" ]; then
  echo [`date`] Store ${SIZE} >> ${ERRFILE}
  java -classpath .:honeycomb-perftest.jar:honeycomb-client.jar:honeycomb-test.jar StoreStress ${CLUSTER}-data ${PROCS} ${SIZE} ${SIZE} ${RUNTIME} 1> ${OUTFILE} 2>> ${ERRFILE}

elif [ "$OPER" = "MDStore" ]; then
  echo [`date`] MDStore ${SIZE} >> ${ERRFILE}
  java -classpath .:honeycomb-perftest.jar:honeycomb-client.jar:honeycomb-test.jar MDStoreStress ${CLUSTER}-data ${PROCS} ${SIZE} ${SIZE} ${RUNTIME} 1> ${OUTFILE} 2>> ${ERRFILE}

elif [ "$OPER" = "AddMD" ]; then
  echo [`date`] AddMD ${SIZE} >> ${ERRFILE}
  cat ${OIDFILE} | java -classpath .:honeycomb-perftest.jar:honeycomb-client.jar:honeycomb-test.jar AddMDStress ${CLUSTER}-data ${PROCS} ${RUNTIME} 1> ${OUTFILE} 2>> ${ERRFILE}

elif [ "$OPER" = "Retrieve" ]; then
  echo [`date`] Retrieve ${SIZE} >> ${ERRFILE}
  cat ${OIDFILE} | java -classpath .:honeycomb-perftest.jar:honeycomb-client.jar:honeycomb-test.jar RetrieveStress ${CLUSTER}-data ${PROCS} ${RUNTIME} 1> ${OUTFILE} 2>> ${ERRFILE}

elif [ "$OPER" = "Delete" ];  then
  echo [`date`] Delete ${DELTYPE} ${SIZE} >> ${ERRFILE}
  cat ${OIDFILE} | java -classpath .:honeycomb-perftest.jar:honeycomb-client.jar:honeycomb-test.jar DeleteStress ${CLUSTER}-data ${PROCS} ${RUNTIME} 1> ${OUTFILE} 2>> ${ERRFILE}

elif [ "$OPER" = "Query" ]; then
  echo [`date`] Query ${QUERYTYPE} >> ${ERRFILE}
  if [ "$QUERYTYPE" = "UNIQUE" ]; then
    cat ${OIDFILE} | java -classpath .:honeycomb-perftest.jar:honeycomb-client.jar:honeycomb-test.jar NewQueryStress ${CLUSTER}-data ${PROCS} ${RUNTIME} ${QUERYTYPE} 1> ${OUTFILE} 2>> ${ERRFILE}
  else
    java -classpath .:honeycomb-perftest.jar:honeycomb-client.jar:honeycomb-test.jar NewQueryStress ${CLUSTER}-data ${PROCS} ${RUNTIME} ${QUERYTYPE} 1> ${OUTFILE} 2>> ${ERRFILE}
  fi

else
  echo "Operation type ${OPER} unknown"

fi

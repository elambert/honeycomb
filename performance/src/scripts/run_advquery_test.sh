#!/bin/bash
#
# $Id: run_advquery_test.sh 11701 2007-12-13 15:43:18Z dr129993 $
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

# Get and check the arguments
# arg #  Description
# ARG0 
# ARG1   Operation: AdvQueryMDStore, AdvQuery
# ARG2   Iteration #
# ARG3   Cluster name
# ARG4   Number of processes/threads
# ARG5   Run time in seconds
# ARG6   Number of client processes
# ARG7   Log directory
# ARG8   Seed for random # generator
# ARG9   Metadata generation patterns
# ARG10  Time interval to print statistics (in milliseconds)
# ARG11  Pause interval in seconds. The number of seconds inbetween operations
#
#        If AdvQueryMDStore
# ARG12  Minimum size in bytes for created objects
# ARG13  Maximum size in bytes for created objects
# ARG14  Size in bytes of repeat pattern to use when creating objects
#
#        If AdvQuery
# ARG12  The type of query: COMPLEX, OREQUAL, COMPLEX2, COMPLEX3, COMPLEX4, COMPLEX5, COMPLEX6, UNIQUE, EMPTY, ALL, MANY
# ARG13  The full pathname of the file used to verify query results
#        This is the file created by the AdvQueryMDStore run and
#        contains object and metadata information.
# ARG14  Results group size. The size of the result group fetched from the 
#        cluster.
# ARG15  Optional, name of client
# 
ARGCOUNT=$#
#echo Arg count: ${ARGCOUNT}

OPER=${1}
if [ "$OPER" = "AdvQueryMDStore" ]; then
   if [ $ARGCOUNT -lt 14 ]; then
      echo Invalid number of arguments.
      echo Should be 14 arguments, received ${ARGCOUNT}
      exit -1
   fi
elif [ "$OPER" = "AdvQuery" ]; then
   echo "run_advquery_test"
   if [ $ARGCOUNT -lt 13 ]; then
      echo Invalid number of arguments.
      echo Should be 13 arguments, received ${ARGCOUNT}
      exit -1
   fi
   echo "after run_advquery"
   if [ $ARGCOUNT -eq 15 ]; then
      CLIENT=${15}
   else
      CLIENT=""
   fi
   if [ $ARGCOUNT -eq 14 ]; then
      RESULTS_GROUP_SIZE=${14}
   else
      RESULTS_GROUP_SIZE=0
   fi
else
   echo Invalid operation specified. Operations: AdvQueryMDStore or AdvQuery
fi


# Iteration number to name files with

ITER=${2}

CLUSTER=${3}
PROCS=${4}
PAUSETIME=${11}
if [ "$OPER" = "AdvQuery" ]; then
  QUERYTYPE=${12}
  SIZE=100
  OIDFILE=${13}
else
  SIZE=${14}
  MINSIZE=${12}
  MAXSIZE=${13}
  REPEATSIZE=${14}
fi

RUNTIME=${5}
NUMCLPROC=${6}
LOGDIR=${7}
SEED=${8}
PATTERN=${9}
INTERVAL=${10}
PAUSETME=${11}
HOSTNAME=`hostname`
if [ "$CLIENT" = "" ]; then
   echo SETTING CLIENT TO HOSTNAME
   CLIENT=$HOSTNAME
fi

if [ "$OPER" = "AdvQuery" ]; then
  OUTFILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.${OPER}.${CLUSTER}.${NUMCLPROC}.${QUERYTYPE}"
  STATSFILE="${LOGDIR}/${ITER}.${CLIENT}.${CLUSTER}.${NUMCLPROC}.${QUERYTYPE}.stats"
  RAWSTATSFILE="${LOGDIR}/${ITER}.${CLIENT}.${CLUSTER}.${NUMCLPROC}.${QUERYTYPE}.rawstats"
  ERRFILE="${LOGDIR}/${ITER}.${CLIENT}.${CLUSTER}.${NUMCLPROC}.${QUERYTYPE}.err"
else
  OUTFILE="${LOGDIR}/${ITER}/${ITER}.${HOSTNAME}.${OPER}.${CLUSTER}.${NUMCLPROC}.${SIZE}"
  STATSFILE="${LOGDIR}/${ITER}.${HOSTNAME}.${CLUSTER}.${NUMCLPROC}.stats"
  ERRFILE="${LOGDIR}/${ITER}.${HOSTNAME}.${CLUSTER}.${NUMCLPROC}.err"
fi

#echo statsfile: ${STATSFILE}
#echo errfile: ${ERRFILE}
#echo seed: ${SEED}
#echo pattern: ${PATTERN}

if [ "$OPER" = "AdvQueryMDStore" ]; then
  echo [`date`] AdvQueryMDStore  >> ${ERRFILE}
  echo [`date`] AdvQueryMDStore >> ${OUTFILE}
  java -classpath .:honeycomb-advquery.jar:honeycomb-perftest.jar:honeycomb-client.jar:honeycomb-test.jar AdvQueryMDStore ${CLUSTER}-data ${PROCS} ${SEED} ${MINSIZE} ${MAXSIZE}  ${SIZE} ${RUNTIME} ${INTERVAL} ${STATSFILE} ${PAUSETIME} "${PATTERN}" 1> ${OUTFILE} 2>> ${ERRFILE}

elif [ "$OPER" = "AdvQuery" ]; then
  echo [`date`] AdvQuery ${QUERYTYPE} >> ${ERRFILE}
  echo [`date`] AdvQuery ${QUERYTYPE} >> ${OUTFILE}
  echo "[`date`] AdvQuery Raw Statistics File ${RAWSTATSFILE}" 
  java -classpath .:honeycomb-advquery.jar:honeycomb-perftest.jar:honeycomb-client.jar:honeycomb-test.jar AdvQueryStress ${CLUSTER}-data ${PROCS}  ${SEED} ${QUERYTYPE} ${RUNTIME} ${OIDFILE} ${INTERVAL} ${STATSFILE} ${RAWSTATSFILE} ${PAUSETIME} ${RESULTS_GROUP_SIZE} "${PATTERN}" 1> ${OUTFILE} 2>> ${ERRFILE}

else
  echo "Operation type ${OPER} unknown"

fi

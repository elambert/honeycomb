#!/bin/bash
#
# $Id: performance_test_base.sh 11199 2007-07-11 22:07:48Z hs154345 $
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
# Functions to run single stream and aggregate performance test suite
##

TMPARR2=($CLIENTS)
NUMCLIENTS=${#TMPARR2[@]}
NUMCLPROCS="${NUMCLIENTS}x${PROCS}"
SSH_ARGS='-o StrictHostKeyChecking=no -q' 

DO_SS="false"
DO_AGG="false"

# Set variable indicating if we're doing Single Stream tests at all by 
# checking if any of the single stream tests are being run
if [ "$STORE_SS" == "true" -o "$MDSTORE_SS" == "true" \
      -o "$ADDMD_SS" == "true" -o "$RETRIEVE_SS" == "true" \
      -o "$QUERY_SS" == "true" -o "$DELETE_SS" == "true" ]; then
  DO_SS="true"
fi

# Set variable indicating if we're doing Aggregate tests at all by
# checking if any of the aggregate tests are being run
if [ "$STORE_AGG" == "true" -o "$MDSTORE_AGG" == "true" \
      -o "$ADDMD_AGG" == "true" -o "$RETRIEVE_AGG" == "true" \
      -o "$QUERY_AGG" == "true" -o "$DELETE_AGG" == "true" ]; then
  DO_AGG="true"
fi

# expected number of arguments
NUM_ARGS=3

_print_usage () {
  echo "Usage: ./performance_test.sh <starting iteration> <ending iteration> <results directory>"
}

_print_parameters() {
  echo "CURRENT TEST PARAMETERS:"
  echo
  echo "CLUSTER: ${CLUSTER}"
  echo "MASTER_CELL: ${MASTERCELL}"
  echo "CLIENTS: ${CLIENTS}"
  echo "THREADS PER CLIENT: ${PROCS}"
  echo "FILESIZES: ${FILESIZES}"
  echo 
  echo "TESTS RUN:"
  if [ "$STORE_SS" = "true" ]; then
    echo "Single Stream Store for ${SS_STORETIME} sec"
  fi
  if [ "$MDSTORE_SS" = "true" ]; then
    echo "Single Stream MDStore for ${SS_MDSTORETIME} sec"
  fi
  if [ "$ADDMD_SS" = "true" ]; then
    echo "Single Stream AddMD for ${SS_ADDMDTIME} sec"
  fi
  if [ "$RETRIEVE_SS" = "true" ]; then
    echo "Single Stream Retrieve for ${SS_RETRIEVETIME} sec"
  fi
  if [ "$QUERY_SS" = "true" ]; then
    echo "Single Stream Query for ${SS_QUERYTIME} sec"
    echo "Query types: ${QUERYTYPES}"
  fi
  if [ "$DELETE_SS" = "true" ]; then
    echo "Single Stream Delete for ${SS_DELTIME} sec"
    echo "Delete types: ${DELTYPES}"
  fi
  if [ "$STORE_AGG" = "true" ]; then
    echo "Aggregate Store for ${AGG_STORETIME} sec"
  fi
  if [ "$MDSTORE_AGG" = "true" ]; then
    echo "Aggregate MDStore for ${AGG_MDSTORETIME} sec"
  fi
  if [ "$ADDMD_AGG" = "true" ]; then
    echo "Aggregate AddMD for ${AGG_ADDMDTIME} sec"
  fi
  if [ "$RETRIEVE_AGG" = "true" ]; then
    echo "Aggregate Retrieve for ${AGG_RETRIEVETIME} sec"
  fi
  if [ "$QUERY_AGG" = "true" ]; then
    echo "Aggregate Query for ${AGG_QUERYTIME} sec"
    echo "Query types: ${QUERYTYPES}"
  fi
  if [ "$DELETE_AGG" = "true" ]; then
    echo "Aggregate Delete for ${AGG_DELTIME} sec"
    echo "Delete types: ${DELTYPES}"
  fi
  echo
}

# Capture cluster state
_cmm_verifier () {
  OUTFILE="${LOGDIR}/status/cmm.`date +%m%d-%H%M`"
  echo
  echo -n "CMM VERIFIER [$OUTFILE]: "
  ssh ${CLUSTER}-cheat ${SSH_ARGS} /opt/test/bin/cmm_verifier $NODES 1 1 yes quorum >$OUTFILE 2>&1
  grep "CLUSTER STATE" $OUTFILE |tail -1
}

# Measure HADB fullness: count of objects, deviceinfo, resourceinfo
# XXX: HACK! Assumes that node 101 is up! May hang if HADB is down!!
#
_hadb_fullness () {
  OUTFILE=$1

  MSG="Count of objects in HADB:"
  if [ ! -e hadb_fullness ]; then
    # T98C20F is the system.object_ctime table
    echo "select count(*) from t_system;" >hadb_fullness.in
  fi
  scp -P 2001 $SSH_ARGS hadb_fullness.in root@${CLUSTER}-admin:hadb_fullness
  COUNT=`ssh root@${CLUSTER}-admin -p 2001 ${SSH_ARGS} /opt/SUNWhadb/4/bin/clusql -nointeractive localhost:15005 system+superduper -command=hadb_fullness |tail -2 |tr -d "[:space:]"`
  echo "$MSG $COUNT"  # to the main log
  echo >>$OUTFILE
  echo "[`date`] $MSG $COUNT" >>$OUTFILE

  MSG="[`date`] HADB deviceinfo:"
  echo >>$OUTFILE
  echo $MSG >>$OUTFILE
  echo admin | ssh root@${CLUSTER}-admin -p 2001 ${SSH_ARGS} /opt/SUNWhadb/4/bin/hadbm deviceinfo honeycomb >> $OUTFILE

  MSG="[`date`] HADB resourceinfo:"
  echo >>$OUTFILE
  echo $MSG >>$OUTFILE
  echo admin |ssh root@${CLUSTER}-admin -p 2001 ${SSH_ARGS} /opt/SUNWhadb/4/bin/hadbm resourceinfo honeycomb >>$OUTFILE
}

# Get a measure of cluster fullness
# XXX: HACK! Assumes that node 101 is up!!
# XXX If used space is < 1GB, math is all wrong
#
_cluster_fullness () {
  OUTFILE="${LOGDIR}/status/full.`date +%m%d-%H%M`"
  echo "CLUSTER FULLNESS [$OUTFILE]:" >>$OUTFILE

  echo -n "Disk fullness: " >>$OUTFILE
  if [ -z "$MASTERCELL" ] ; then
    ssh admin@${CLUSTER}-admin ${SSH_ARGS} df -h >>$OUTFILE
    FULLNESS=`ssh admin@${CLUSTER}-admin ${SSH_ARGS} df -h | awk '{print $11}' | sed -e 's/%//g'`
  else
    ssh admin@${CLUSTER}-admin ${SSH_ARGS} df -c $MASTERCELL -h >>$OUTFILE
    FULLNESS=`ssh admin@${CLUSTER}-admin ${SSH_ARGS} df -c $MASTERCELL -h | awk '{print $11}' | sed -e 's/%//g'`
  fi
  cat $OUTFILE  # to the main log
  echo >>$OUTFILE


  echo "Physical disk fullness:" >>$OUTFILE
  if [ -z "$MASTERCELL" ] ; then
    ssh admin@${CLUSTER}-admin ${SSH_ARGS} df -p >>$OUTFILE
  else
    ssh admin@${CLUSTER}-admin ${SSH_ARGS} df -c $MASTERCELL -p >>$OUTFILE
  fi
  echo >>$OUTFILE

  _hadb_fullness $OUTFILE
}

_run_aggregate_store() {
  ITER=$1
  RUNTIME=$2

  echo [`date`] ${ITER} Aggregate Store Test

  for SIZE in $FILESIZES; do

    echo [`date`] Storing ${SIZE}
    for CLIENT in $CLIENTS; do
      echo [`date`] Launching test on ${CLIENT}
      ssh $CLIENT ${SSH_ARGS} "cd /opt/performance; source /etc/profile; ./run_test.sh Store ${ITER} ${CLUSTER} ${PROCS} ${SIZE} ${RUNTIME} ${NUMCLPROCS} ${LOGDIR}" &
    done

    wait
  done  
  echo [`date`] Aggregate Store Test Done
}

_run_aggregate_mdstore() {
  ITER=$1
  RUNTIME=$2

  echo [`date`] ${ITER} Aggregate MDStore Test

  for SIZE in $FILESIZES; do

    echo [`date`] Storing ${SIZE}
    for CLIENT in $CLIENTS; do
      echo [`date`] Launching test on ${CLIENT}

      ssh $CLIENT ${SSH_ARGS} "cd /opt/performance; source /etc/profile; ./run_test.sh MDStore ${ITER} ${CLUSTER} ${PROCS} ${SIZE} ${RUNTIME} ${NUMCLPROCS} ${LOGDIR}" &
    done

    wait
  done  
  echo [`date`] Aggregate MDStore Test Done
}


_run_aggregate_retrieve() {
  ITER=$1
  RUNTIME=$2

  echo [`date`] ${ITER} Aggregate Retrieve Test

  for SIZE in $FILESIZES; do

    echo [`date`] Retrieving ${SIZE}
    for CLIENT in $CLIENTS; do
      FILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.Store.${CLUSTER}.${NUMCLPROCS}.${SIZE}"
      echo [`date`] Launching test on ${CLIENT} using OIDS from ${FILE}
      ssh $CLIENT ${SSH_ARGS} "cd /opt/performance; source /etc/profile; ./run_test.sh Retrieve ${ITER} ${CLUSTER} ${PROCS} ${SIZE} ${RUNTIME} ${NUMCLPROCS} ${LOGDIR} ${FILE}" &
    done

    wait
  done  
  echo [`date`] Aggregate Retrieve Test Done
}

_run_aggregate_delete() {
  # delete each file size for time RUNTIME/#FILESIZES 

  ITER=$1
  DELTIME=$2
  DELTYPE=$3

  echo [`date`] Aggregate Delete Test

  for SIZE in $FILESIZES; do
    echo [`date`] ${ITER} Deleting size ${SIZE}, ${DELTIME} seconds

    for CLIENT in $CLIENTS; do
      if [ "$DELTYPE" = "MDonly" ]; then
	FILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.AddMD.${CLUSTER}.${NUMCLPROCS}.${SIZE}"
      elif [ "$DELTYPE" = "MDData" ]; then
	FILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.MDStore.${CLUSTER}.${NUMCLPROCS}.${SIZE}"
      else
	# default assumes that at least Stores have been performed
	FILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.Store.${CLUSTER}.${NUMCLPROCS}.${SIZE}"
      fi
      STR="${NUMCLPROCS}.${DELTYPE}"

      echo [`date`] Launching test on ${CLIENT} using OIDs from ${FILE}
      ssh $CLIENT ${SSH_ARGS} "cd /opt/performance; source /etc/profile; ./run_test.sh Delete ${ITER} ${CLUSTER} ${PROCS} ${SIZE} ${DELTIME} ${STR} ${LOGDIR} ${FILE}" &
    done
    wait
  done

  echo [`date`] Aggregate Delete Test Done
}

_run_aggregate_addmd() {
  ITER=$1
  RUNTIME=$2

  echo [`date`] ${ITER} Aggregate AddMD Test

  for SIZE in $FILESIZES; do
    echo [`date`] ${ITER} Adding MD to size ${SIZE}

    for CLIENT in $CLIENTS; do
      FILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.Store.${CLUSTER}.${NUMCLPROCS}.${SIZE}"

      echo [`date`] Launching test on ${CLIENT} using OIDS from ${FILE}
      ssh $CLIENT ${SSH_ARGS} "cd /opt/performance; source /etc/profile; ./run_test.sh AddMD ${ITER} ${CLUSTER} ${PROCS} ${SIZE} ${RUNTIME} ${NUMCLPROCS} ${LOGDIR} ${FILE}" &
    done

    wait
  done

  echo [`date`] Aggregate AddMD Test Done
}

_run_aggregate_query() {
  ITER=$1
  QUERYTIME=$2
  QUERYTYPE=$3

  echo [`date`] ${ITER} Aggregate Query Test ${QUERYTYPE}

  for CLIENT in $CLIENTS; do
    FILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.MDStore.${CLUSTER}.${NUMCLPROCS}.${FILESIZES[0]}"

    echo [`date`] Launching test on ${CLIENT}
    ssh $CLIENT ${SSH_ARGS} "cd /opt/performance; source /etc/profile; ./run_test.sh Query ${ITER} ${CLUSTER} ${PROCS} ${QUERYTYPE} ${QUERYTIME} ${NUMCLPROCS} ${LOGDIR} ${FILE}" &
  done

  wait
  echo [`date`] Aggregate Query ${QUERYTYPE} Test Done
}

_run_single_stream_store() {
  ITER=$1
  RUNTIME=$2

  echo [`date`] ${ITER} Single Stream Store Test

  for SIZE in $FILESIZES; do
    echo [`date`] Storing ${SIZE}
    ./run_test.sh Store ${ITER} ${CLUSTER} 1 ${SIZE} ${RUNTIME} 1x1 ${LOGDIR}
  done  
  echo [`date`] Single Stream Store Test Done
}

_run_single_stream_retrieve() {
  ITER=$1
  RUNTIME=$2

  CLIENT=`hostname`

  echo [`date`] Single Stream Retrieve Test

  for SIZE in $FILESIZES; do
    FILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.Store.${CLUSTER}.1x1.${SIZE}"

    echo [`date`] Retrieving ${SIZE} using OIDS from ${FILE}
    ./run_test.sh Retrieve ${ITER} ${CLUSTER} 1 ${SIZE} ${RUNTIME} 1x1 ${LOGDIR} ${FILE}
  done
  echo [`date`] Single Stream Retrieve Test Done
}

_run_single_stream_mdstore() {
  ITER=$1
  RUNTIME=$2

  echo [`date`] ${ITER} Single Stream MDStore Test

  for SIZE in $FILESIZES; do
    echo [`date`] Storing with MD ${SIZE}
    ./run_test.sh MDStore ${ITER} ${CLUSTER} 1 ${SIZE} ${RUNTIME} 1x1 ${LOGDIR}
  done  
  echo [`date`] Single Stream MDStore Test Done
}

_run_single_stream_addmd() {
  ITER=$1
  RUNTIME=$2

  CLIENT=`hostname`

  echo [`date`] Single Stream AddMD Test

  for SIZE in $FILESIZES; do
    FILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.Store.${CLUSTER}.1x1.${SIZE}"

    echo [`date`] Adding MD to ${SIZE} using OIDS from ${FILE}
    ./run_test.sh AddMD ${ITER} ${CLUSTER} 1 ${SIZE} ${RUNTIME} 1x1 ${LOGDIR} ${FILE}
  done
  echo [`date`] Single Stream AddMD Test Done
}

_run_single_stream_delete() {
  ITER=$1
  DELTIME=$2
  DELTYPE=$3

  CLIENT=`hostname`

  echo [`date`] Single Stream Delete Test

  for SIZE in $FILESIZES; do
    if [ "$DELTYPE" = "MDonly" ]; then
      FILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.AddMD.${CLUSTER}.1x1.${SIZE}"
    elif [ "$DELTYPE" = "MDData" ]; then
      FILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.MDStore.${CLUSTER}.1x1.${SIZE}"
    else
      # default assumes that at least Stores have been performed
      FILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.Store.${CLUSTER}.1x1.${SIZE}"
    fi
    STR="1x1.${DELTYPE}"

    echo [`date`] Deleting ${DELTYPE} ${SIZE} using OIDS from ${FILE}
    ./run_test.sh Delete ${ITER} ${CLUSTER} 1 ${SIZE} ${DELTIME} ${STR} ${LOGDIR} ${FILE}
  done
  echo [`date`] Single Stream Delete Test Done
}

_run_single_stream_query() {
  ITER=$1
  QUERYTIME=$2
  QUERYTYPE=$3

  CLIENT=`hostname`

  FILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.MDStore.${CLUSTER}.1x1.${FILESIZES[0]}"
  echo [`date`] Single Stream ${QUERYTYPE} Query Test 

  ./run_test.sh Query ${ITER} ${CLUSTER} 1 ${QUERYTYPE} ${QUERYTIME} 1x1 ${LOGDIR} ${FILE}

  echo [`date`] Single Stream Query Test Done
}

_process_query() {
  OPER=$1
  ITER=$2
  SS=$3
  RESFILE=$4
  RESSUM=$5

  if [ "$SS" = "yes" ]; then
    CLTS=`hostname`
    STR="1x1"
  else
    CLTS=$CLIENTS
    STR=$NUMCLPROCS
  fi

  for QUERY in $QUERYTYPES; do
    OUTFILE="${LOGDIR}/${ITER}.${OPER}.${CLUSTER}.${STR}.${QUERY}.compiled"
    for CLIENT in $CLTS; do
      INFILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.${OPER}.${CLUSTER}.${STR}.${QUERY}"
      echo "${ITER} ${CLIENT}" >> $OUTFILE
      ssh $CLIENT ${SSH_ARGS} "cd /opt/performance; source /etc/profile; cat ${INFILE}" >> $OUTFILE &
    done
    wait
    echo >> $OUTFILE
    
    echo "${OPER} ${QUERY}" >> $RESFILE
    ./parse_query.pl $OUTFILE >> $RESFILE
    echo "${OPER} ${QUERY}: `./parse_query.pl $OUTFILE`" >> $RESSUM
    echo >> $RESFILE
    echo >> RESSUM
  done
}
  

_process_data() {
  OPER=$1
  ITER=$2
  SS=$3
  RESFILE=$4 #results file with results from all the operations compiled
  RESSUM=$5 #summary file of results
  DELTYPE=$6 #type of delete, if it is a delete

  echo [`date`] >> $RESFILE
  echo [`date`] >> $RESSUM

  if [ "$SS" = "yes" ]; then
    CLTS=`hostname`
    STR="1x1"
  else
    CLTS=$CLIENTS
    STR=$NUMCLPROCS
  fi
  
  if [ "$OPER" = "Delete" ]; then
    STR=${STR}.${DELTYPE}
  fi

  for SIZE in $FILESIZES; do
    OUTFILE="${LOGDIR}/${ITER}-analyze/${ITER}.${OPER}.${CLUSTER}.${STR}.${SIZE}"
    for CL in $CLTS; do
      INFILE="${LOGDIR}/${ITER}/${ITER}.${CL}.${OPER}.${CLUSTER}.${STR}.${SIZE}"
      ssh $CL ${SSH_ARGS} "cd /opt/performance; source /etc/profile; ./run_analyze.sh ${INFILE}" >> $OUTFILE &
    done
    wait

    echo >> $OUTFILE
    echo "${OPER} ${SIZE} ${DELTYPE}" >> $RESFILE
    if [ "$SS" = "yes" ]; then
      SUM=`./compile_ss.pl $OUTFILE $RESFILE`
    else
      SUM=`./compile_aggregate.pl $OUTFILE $RESFILE`
    fi
    echo "${OPER} ${SIZE} ${DELTYPE}: ${SUM}" >> $RESSUM
    echo >> $RESFILE
    echo >> $RESSUM
  done
    
}

_record_fullness() {
  RESFILE=$1
  RESSUM=$2

  # check and record cluster/hadb fullness
  _cluster_fullness
  echo "Fullness: ${FULLNESS}%, HADB object count: ${COUNT}" >> $RESFILE
  echo >> $RESFILE
  echo "Fullness: ${FULLNESS}%, HADB object count: ${COUNT}" >> $RESSUM
  echo >> $RESSUM
}

_do_test() {
  # Try to stop an already running set of tests
  if [ "$1" = "stop" ]; then
    ps -Aef |grep performance_test |grep -v stop | awk '{print $2}' |xargs kill
    ps -Aef |grep java | grep -v grep | awk '{print $2}' | xargs kill
    for CLIENT in $CLIENTS; do
      ssh $CLIENT ${SSH_ARGS} "ps -Aef | grep java| grep -v grep | awk '{print $2}' | xargs kill"
    done
    exit 0
  fi
  
  if [ $# -ne $NUM_ARGS ]; then  
    _print_usage
    exit 1
  fi

# HACK: because the mdconfig -d does not report correctly the schema
#       at the moment
#  if [ "$MDSTORE_SS" == "true" -o "$ADDMD_SS" == "true" \
#   -o "$MDSTORE_AGG" == "true" -o "$ADDMD_AGG" == "true" ]; then
#    ssh admin@${CLUSTER}-admin mdconfig -d | egrep -i 'first|second|third|fourth' >/dev/null
#    if [ $? -ne 0 ]; then
#      echo "Wrong Schema! Load QA Schema before preceding!"
#      exit 2
#    fi
#  fi

  VERSION=`ssh admin@${CLUSTER}-admin ${SSH_ARGS} version`
  echo [`date`] ${VERSION}
  
  echo [`date`] STARTING PERFORMANCE TEST. ITERATIONS [${ITER} - ${2}]
  echo [`date`] CREATING LOG DIRECTORIES ON ALL CLIENTS
  for CLIENT in $CLIENTS; do
      echo "Creating ${LOGDIR} and status directory on ${CLIENT}"
      ssh $CLIENT ${SSH_ARGS} "mkdir -p ${LOGDIR}/status"
  done
  
  _print_parameters
  
  while [ $ITER -lt $ITERATIONS ]; do
  
    echo [`date`] STARTING ITERATION ${ITER}
  
    _cmm_verifier
    _cluster_fullness
  
    echo [`date`] CREATING RAW RESULTS DIRECTORY ${LOGDIR}/${ITER}
    for CLIENT in $CLIENTS; do
      echo "Creating ${LOGDIR}/${ITER} on ${CLIENT}"
      ssh $CLIENT ${SSH_ARGS} "mkdir -p ${LOGDIR}/${ITER}"
    done
    echo CREATING ANALYZE DIRECTORY ON MAIN CLIENT ${LOGDIR}/${ITER}-analyze
    mkdir -p "${LOGDIR}/${ITER}-analyze"
  
    echo RESULTS FILES:
  
    if [ "$DO_SS" == "true" ]; then
      SSRESFILE=${LOGDIR}/${ITER}.${CLUSTER}.1x1.report
      SSRESSUM=${LOGDIR}/${ITER}.${CLUSTER}.1x1.summary
  
      echo SINGLE STREAM RESULTS
      echo ${SSRESFILE}
      echo ${SSRESSUM}
  
      echo "Date: `date +%m/%d/%y` Cluster: ${CLUSTER} (${NODES}) 1x1 ${VERSION}" >> $SSRESFILE
      echo "Date: `date +%m/%d/%y` Cluster: ${CLUSTER} (${NODES}) 1x1 ${VERSION}" >> $SSRESSUM
      _record_fullness $SSRESFILE $SSRESSUM   
    fi
    
    if [ "$DO_AGG" == "true" ]; then
      AGGRESFILE=${LOGDIR}/${ITER}.${CLUSTER}.${NUMCLPROCS}.report
      AGGRESSUM=${LOGDIR}/${ITER}.${CLUSTER}.${NUMCLPROCS}.summary
      
      echo AGGREGATE RESULTS
      echo ${AGGRESFILE}
      echo ${AGGRESSUM}
  
      echo "Date: `date +%m/%d/%y` Cluster: ${CLUSTER} (${NODES}) ${NUMCLPROCS} ${VERSION}" >> $AGGRESFILE
      echo "Date: `date +%m/%d/%y` Cluster: ${CLUSTER} (${NODES}) ${NUMCLPROCS} ${VERSION}" >> $AGGRESSUM
  #    _record_fullness $AGGRESFILE $AGGRESSUM
    fi    
      
    # Do aggregate store test for all filesizes
    if [ "$STORE_AGG" = "true" ]
    then
      _run_aggregate_store $ITER $AGG_STORETIME
      _process_data "Store" $ITER "no" $AGGRESFILE $AGGRESSUM
      _record_fullness $AGGRESFILE $AGGRESSUM
    fi

    # Do single stream store test for all filesizes
    if [ "$STORE_SS" = "true" ]
    then
      _run_single_stream_store $ITER $SS_STORETIME
      _process_data "Store" $ITER "yes" $SSRESFILE $SSRESSUM
      _record_fullness $SSRESFILE $SSRESSUM
    fi
    
    # Do aggregate MDStore test for all filesizes
    if [ "$MDSTORE_AGG" = "true" ]
    then
      _run_aggregate_mdstore $ITER $AGG_MDSTORETIME
      _process_data "MDStore" $ITER "no" $AGGRESFILE $AGGRESSUM
      _record_fullness $AGGRESFILE $AGGRESSUM
    fi

    # Do single stream MDStore test for all filesizes
    if [ "$MDSTORE_SS" = "true" ]
    then
      _run_single_stream_mdstore $ITER $SS_MDSTORETIME
      _process_data "MDStore" $ITER "yes" $SSRESFILE $SSRESSUM
      _record_fullness $SSRESFILE $SSRESSUM
    fi
    
    # Do aggregate retrieve test for all filesizes
    if [ "$RETRIEVE_AGG" = "true" ]
    then
      _run_aggregate_retrieve $ITER $AGG_RETRIEVETIME
      _process_data "Retrieve" $ITER "no" $AGGRESFILE $AGGRESSUM
    fi

    # Do single stream Retrieve test for all filesizes
    if [ "$RETRIEVE_SS" = "true" ]
    then
      _run_single_stream_retrieve $ITER $SS_RETRIEVETIME
      _process_data "Retrieve" $ITER "yes" $SSRESFILE $SSRESSUM
    fi
    
    # Do aggregate addmd test for all filesizes
    if [ "$ADDMD_AGG" = "true" ]
    then
      _run_aggregate_addmd $ITER $AGG_ADDMDTIME
      _process_data "AddMD" $ITER "no" $AGGRESFILE $AGGRESSUM
    fi

    # Do single stream AddMD test for all filesizes
    if [ "$ADDMD_SS" = "true" ]
    then
      _run_single_stream_addmd $ITER $SS_ADDMDTIME
      _process_data "AddMD" $ITER "yes" $SSRESFILE $SSRESSUM
    fi
  
    # Do aggregate query test for all query types
    if [ "$QUERY_AGG" = "true" ]
    then
      for QUERYTYPE in $QUERYTYPES; do
        _run_aggregate_query $ITER $AGG_QUERYTIME $QUERYTYPE
      done
      _process_query "Query" $ITER "no" $AGGRESFILE $AGGRESSUM
    fi

    # Do single stream Query test for all filesizes
    if [ "$QUERY_SS" = "true" ]
    then
      for QUERYTYPE in $QUERYTYPES; do
        _run_single_stream_query $ITER $SS_QUERYTIME $QUERYTYPE
      done
      _process_query "Query" $ITER "yes" $SSRESFILE $SSRESSUM
    fi
  
    # Do aggregate delete test 
    if [ "$DELETE_AGG" = "true" ]
    then
      for DELTYPE in $DELTYPES; do
        _run_aggregate_delete $ITER $AGG_DELTIME $DELTYPE
        _process_data "Delete" $ITER "no" $AGGRESFILE $AGGRESSUM $DELTYPE
      done
    fi

    # Do single stream Delete test for all filesizes
    if [ "$DELETE_SS" = "true" ]
    then
      for DELTYPE in $DELTYPES; do
        _run_single_stream_delete $ITER $SS_DELTIME $DELTYPE
        _process_data "Delete" $ITER "yes" $SSRESFILE $SSRESSUM $DELTYPE
      done
    fi
  
  
    ((ITER++))
  done
  
  _cmm_verifier
  _cluster_fullness
  
  if [ "$DO_AGG" == "true" ]; then
    echo "Fullness: ${FULLNESS}%, HADB object count: ${COUNT}" >> $AGGRESFILE
    echo "Fullness: ${FULLNESS}%, HADB object count: ${COUNT}" >> $AGGRESSUM
  fi
  if [ "$DO_SS" == "true" ]; then
    echo "Fullness: ${FULLNESS}%, HADB object count: ${COUNT}" >> $SSRESFILE
    echo "Fullness: ${FULLNESS}%, HADB object count: ${COUNT}" >> $SSRESSUM
  fi
  
  echo
  echo [`date`] ALL DONE  
  return 0
}


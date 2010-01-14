#!/bin/bash
#
# $Id: adv_query_test_base.sh 11701 2007-12-13 15:43:18Z dr129993 $
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
# Functions to run advanced query test suite
##

TMPARR2=($CLIENTS)
NUMCLIENTS=${#TMPARR2[@]}
NUMCLPROCS="${NUMCLIENTS}x${PROCS}"

echo Number of clients: ${NUMCLIENTS}
echo Number of procs: ${PROCS}
echo CLIENTxPROCESSES: ${NUMCLPROCS}
echo BASE - STORE_LOG ${STORE_LOG}
echo METADATA_VERSION: ${METADATA_VERSION}

DO_SS="false"
DO_AGG="false"
echo "MDSTORE_SS" ${MDSTORE_SS}
echo MDSTORE_AGG  ${MDSTORE_AGG}

echo QUERY_AGG  ${QUERY_AGG}

# Set variable indicating if we're doing Single Stream tests at all by
# checking if any of the single stream tests are being run
if [ "$MDSTORE_SS" == "true" -o "$QUERY_SS" == "true" ]; then
  DO_SS="true"
fi

# Set variable indicating if we're doing Aggregate tests at all by
# checking if any of the aggregate tests are being run
if [ "$MDSTORE_AGG" == "true" -o "$QUERY_AGG" == "true" ]; then
  DO_AGG="true"
fi


# expected number of arguments
NUM_ARGS=3

SEED="8_2_3_4_5_6_7_9"
MD_PATTERN_FILE=`dirname $0`/advquery_md_randgen.txt
METADATA_VERSION="`dirname $0`/${METADATA_VERSION}"

if [ -f "${METADATA_VERSION}" ]; then
   MD_PATTERN_FILE="`dirname $0`/${METADATA_VERSION}"
fi

PATTERN="`cat ${MD_PATTERN_FILE}`"

_print_usage () {
  echo "Usage: ./adv_query_test_base.sh <starting iteration> <ending iteration> <results directory>"
}

_print_parameters() {
  echo "CURRENT TEST PARAMETERS:"
  echo
  echo "CLUSTER: ${CLUSTER}"
  echo "CLIENTS: ${CLIENTS}"
  echo "THREADS PER CLIENT: ${PROCS}"
  echo "FILESIZES: ${FILESIZES}"
  echo "SEED: " ${SEED}
  echo "STORE LOG/QUERY VERIFY FILE: ${STORE_LOG}"
 
  echo "TESTS RUN:"
  if [ "$MDSTORE_SS" = "true" ]; then
    echo "Single Stream AdvQueryMDStore for ${SS_MDSTORETIME} sec"
  fi
  if [ "$MDSTORE_AGG" = "true" ]; then
    echo "Multithread AdvQueryMDStore for ${AGG_MDSTORETIME} sec"
  fi
  if [ "$QUERY_SS" = "true" ]; then
    echo "Single Stream AdvQuery for ${SS_QUERYTIME} sec"
    echo "AdvQuery types: ${QUERYTYPES}"
  fi
  if [ "$QUERY_AGG" = "true" ]; then
    echo "Aggregate AdvQuery for ${AGG_QUERYTIME} sec"
    echo "AdvQuery types: ${QUERYTYPES}"
  fi
  echo
  echo "PATTERN: ${PATTERN}"
  echo
}

# Capture cluster state
_cmm_verifier () {
  OUTFILE="${LOGDIR}/status/cmm.`date +%m%d-%H%M`"
  echo
  echo -n "CMM VERIFIER [$OUTFILE]: "
  ssh ${CLUSTER}-cheat /opt/test/bin/cmm_verifier $NODES 1 1 yes quorum >$OUTFILE 2>&1 </dev/null
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
  scp -P 2001 hadb_fullness.in root@${CLUSTER}-admin:hadb_fullness
  COUNT=`ssh root@${CLUSTER}-admin -p 2001 /opt/SUNWhadb/4/bin/clusql -nointeractive localhost:15005 system+superduper -command=hadb_fullness |tail -2 |tr -d "[:space:]"`
  echo "$MSG $COUNT"  # to the main log
  echo >>$OUTFILE
  echo "[`date`] $MSG $COUNT" >>$OUTFILE

  MSG="[`date`] HADB deviceinfo:"
  echo >>$OUTFILE
  echo $MSG >>$OUTFILE
  echo admin | ssh root@${CLUSTER}-admin -p 2001 /opt/SUNWhadb/4/bin/hadbm deviceinfo honeycomb >> $OUTFILE

  MSG="[`date`] HADB resourceinfo:"
  echo >>$OUTFILE
  echo $MSG >>$OUTFILE
  echo admin |ssh root@${CLUSTER}-admin -p 2001 /opt/SUNWhadb/4/bin/hadbm resourceinfo honeycomb >>$OUTFILE
}

# Get a measure of cluster fullness
# XXX: HACK! Assumes that node 101 is up!!
# XXX If used space is < 1GB, math is all wrong
#
_cluster_fullness () {
  OUTFILE="${LOGDIR}/status/full.`date +%m%d-%H%M`"
  echo "CLUSTER FULLNESS [$OUTFILE]:" >>$OUTFILE

  echo -n "Disk fullness: " >>$OUTFILE
  ssh admin@${CLUSTER}-admin df -h >>$OUTFILE
  cat $OUTFILE  # to the main log
  echo >>$OUTFILE

  FULLNESS=`ssh admin@${CLUSTER}-admin df -h | awk '{print $8}' | sed -e 's/%//g'`
  DISKFULLNESS=`ssh admin@${CLUSTER}-admin df -h | awk '{print $6}' | sed -e 's/G;//g'`
  echo "Physical disk fullness:" >>$OUTFILE
  ssh admin@${CLUSTER}-admin df -p >>$OUTFILE
  echo >>$OUTFILE

  _hadb_fullness $OUTFILE
}


_run_aggregate_query() {
  ITER=$1
  QUERYTIME=${SS_QUERYTIME} #$2
  QUERYTYPE=$3

  echo
  echo [`date`] ${ITER} Aggregate AdvQuery Test ${QUERYTYPE}

  for CLIENT in $CLIENTS; do
     FILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.AdvQueryMDStore.${CLUSTER}.${NUMCLPROCS}.${FILESIZES[0]}"
     if [ "${STORE_LOG}" = "" ]; then
        OIDFILE="${LOGDIR}/${CLIENT}.AdvQueryMDStore.${CLUSTER}"
     else
        OIDFILE=${STORE_LOG}
     fi

    echo [`date`] Launching test on ${CLIENT}
    if [ ! -f $OIDFILE ]; then
       echo $OIDFILE does not exist
       echo Will not verify any query results.
       OIDFILE="NOFILE";
    fi

    ssh $CLIENT "cd /opt/performance; source /etc/profile;  ./run_advquery_test.sh AdvQuery ${ITER} ${CLUSTER} ${PROCS} ${QUERYTIME} ${NUMCLPROCS} ${LOGDIR} ${SEED} "${PATTERN}" ${QUERY_STATS_INTERVAL} ${WAIT_TIME} ${RESULTS_GROUP_SIZE} ${QUERYTYPE} ${OIDFILE} ${CLIENT}" &

  done

  wait
  echo [`date`] Aggregate AdvQuery ${QUERYTYPE} Test Done
}

_run_aggregate_stream_mdstore() {
  ITER=$1
  RUNTIME=$2

  echo
  echo [`date`] ${ITER} Multithread Stream AdvQueryMDStore Test

  ./run_advquery_test.sh AdvQueryMDStore ${ITER} ${CLUSTER} ${PROCS} ${RUNTIME} ${NUMCLPROCS} ${LOGDIR} ${SEED} "${PATTERN}" ${STORE_STATS_INTERVAL} ${WAIT_TIME} 1024 1024 1024
  echo [`date`] Multithread Stream AdvQueryMDStore Test Do
}

_run_single_stream_mdstore() {
  ITER=$1
  RUNTIME=$2

  echo
  echo [`date`] ${ITER} Single Stream AdvQueryMDStore Test

  ./run_advquery_test.sh AdvQueryMDStore ${ITER} ${CLUSTER} ${PROCS} ${RUNTIME} ${NUMCLPROCS} ${LOGDIR} ${SEED} "${PATTERN}" ${STORE_STATS_INTERVAL} ${WAIT_TIME} 1024 1024 1024  
  echo [`date`] Single Stream AdvQueryMDStore Test Done
}

_run_single_stream_query() {
  ITER=$1
  QUERYTIME=${SS_QUERYTIME} #$2
  QUERYTYPE=$3

  CLIENT=`hostname`

  FILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.AdvQueryMDStore.${CLUSTER}.${NUMCLPROCS}.${FILESIZES[0]}"
  if [ "${STORE_LOG}" = "" ]; then
     OIDFILE="${LOGDIR}/${CLIENT}.AdvQueryMDStore.${CLUSTER}"
  else
     OIDFILE=${STORE_LOG}
  fi

  echo
  echo [`date`] Single Stream AdvQuery ${QUERYTYPE} Test

  if [ ! -f $OIDFILE ]; then
     echo $OIDFILE does not exist
     echo Will not verify any query results.
     OIDFILE="NOFILE";
  fi

   echo "Results group size: ${RESULTS_GROUP_SIZE}"
   echo " ./run_advquery_test.sh AdvQuery ${ITER} ${CLUSTER} ${PROCS} ${QUERYTIME} ${NUMCLPROCS} ${LOGDIR} ${SEED} PATTERN ${QUERY_STATS_INTERVAL} ${WAIT_TIME} ${QUERYTYPE} ${OIDFILE} ${RESULTS_GROUP_SIZE}"
  ./run_advquery_test.sh AdvQuery ${ITER} ${CLUSTER} ${PROCS} ${QUERYTIME} ${NUMCLPROCS} ${LOGDIR} ${SEED} "${PATTERN}" ${QUERY_STATS_INTERVAL} ${WAIT_TIME} ${QUERYTYPE} ${OIDFILE} ${RESULTS_GROUP_SIZE}

  echo [`date`] Single Stream AdvQuery ${QUERYTYPE} Test Done
}

_process_query() {
  OPER=$1
  ITER=$2
  SS=$3
  RESFILE=$4
  RESSUM=$5

  STATSUM=$7

  if [ "$SS" = "yes" ]; then
    CLTS=`hostname`
    STR=$NUMCLPROCS  
  else
    CLTS=$CLIENTS
    STR=$NUMCLPROCS
  fi

  for QUERY in $QUERYTYPES; do
    OUTFILE="${LOGDIR}/${ITER}.${OPER}.${CLUSTER}.${STR}.${QUERY}.compiled"
    for CLIENT in $CLTS; do
      INFILE="${LOGDIR}/${ITER}/${ITER}.${CLIENT}.${OPER}.${CLUSTER}.${STR}.${QUERY}"
      echo INFILE: ${INFILE}
      echo "${ITER} ${CLIENT}" >> $OUTFILE
      ssh $CLIENT "cd /opt/performance; source /etc/profile; cat ${INFILE}" >> $OUTFILE &
    done
    wait
    echo >> $OUTFILE
    
    echo "${OPER} ${QUERY}" >> $RESFILE
    QUERYSTR="${QUERY}:"
    ./parse_advquery.pl $OUTFILE $OPER $QUERYSTR >> $RESFILE
    ./parse_advquery.pl $OUTFILE $OPER $QUERYSTR >> $RESSUM
    ./parse_advquery_summary.pl $OUTFILE $OPER $QUERYSTR $DISKFULLNESS $FULLNESS $COUNT >> $STATSUM
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
    STR=$NUMCLPROCS
  else
    CLTS=$CLIENTS
    STR=$NUMCLPROCS
  fi
  
  for SIZE in $FILESIZES; do
    OUTFILE="${LOGDIR}/${ITER}-analyze/${ITER}.${OPER}.${CLUSTER}.${STR}.${SIZE}"
    for CL in $CLTS; do
      INFILE="${LOGDIR}/${ITER}/${ITER}.${CL}.${OPER}.${CLUSTER}.${STR}.${SIZE}"
      ssh $CL "cd /opt/performance; source /etc/profile; ./run_advquery_analyze.sh ${INFILE}" >> $OUTFILE &
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
      ssh $CLIENT "ps -Aef | grep java| grep -v grep | awk '{print $2}' | xargs kill"
    done
    exit 0
  fi
  
  if [ $# -ne $NUM_ARGS ]; then  
    echo "Number of arguments $# don't match expected number $NUM_ARGS"
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

  VERSION=`ssh admin@${CLUSTER}-admin version </dev/null`
  echo [`date`] ${VERSION}
 
  let END_ITER=${2}-1 
  echo [`date`] STARTING ADVANCED QUERY TEST. ITERATIONS [${ITER} - ${END_ITER}]
  echo [`date`] CREATING LOG DIRECTORIES ON ALL CLIENTS
  for CLIENT in $CLIENTS; do
      echo "Creating ${LOGDIR} and status directory on ${CLIENT}"
      ssh $CLIENT "mkdir -p ${LOGDIR}/status"
  done
 
  echo CALLING PRINT PARAMETERS 
  _print_parameters
  echo BACK FROM PRINT PARAMETERS  
  # create summary statisics file for all queries and iterations  
  STATSUMFILE=${LOGDIR}/${CLUSTER}.${NUMCLPROCS}.stats
  echo "Writing header to STATS file"
  echo "#Query dbObjects diskFullnes percentFull Res/sec TotRes TotalTime FCLat  FCCount FCTime" > $STATSUMFILE

  while [ $ITER -lt $ITERATIONS ]; do
  
    echo [`date`] STARTING ITERATION ${ITER}
  
    _cmm_verifier
    _cluster_fullness
  
    echo [`date`] CREATING RAW RESULTS DIRECTORY ${LOGDIR}/${ITER}
    for CLIENT in $CLIENTS; do
      echo "Creating ${LOGDIR}/${ITER} on ${CLIENT}"
      ssh $CLIENT "mkdir -p ${LOGDIR}/${ITER}"
    done
    echo CREATING ANALYZE DIRECTORY ON MAIN CLIENT ${LOGDIR}/${ITER}-analyze
    mkdir -p "${LOGDIR}/${ITER}-analyze"
  
    echo RESULTS FILES:
 
    echo STATS SUMMARY FILE: ${STATSUMFILE}
 
    if [ "$DO_SS" == "true" ]; then
      SSRESFILE=${LOGDIR}/${ITER}.${CLUSTER}.${NUMCLPROCS}.report
      SSRESSUM=${LOGDIR}/${ITER}.${CLUSTER}.${NUMCLPROCS}.summary
 
      echo SINGLE STREAM RESULTS
      echo ${SSRESFILE}
      echo ${SSRESSUM}
  
      echo "Date: `date +%m/%d/%y` Cluster: ${CLUSTER} (${NODES}) ${NUMCLPROCS} ${VERSION}" >> $SSRESFILE
      echo "Date: `date +%m/%d/%y` Cluster: ${CLUSTER} (${NODES}) ${NUMCLPROCS} ${VERSION}" >> $SSRESSUM
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
      _record_fullness $AGGRESFILE $AGGRESSUM
    fi    
      

    if [ "$MDSTORE_AGG" = "true" ]
    then
      _run_aggregate_stream_mdstore $ITER $AGG_MDSTORETIME
      _process_data "AdvQueryMDStore" $ITER "yes" $AGGRESFILE $AGGRESSUM
      _record_fullness $AGGRESFILE $AGGRESSUM
    fi

    # Do single stream MDStore test for all filesizes
    if [ "$MDSTORE_SS" = "true" ]
    then
      _run_single_stream_mdstore $ITER $SS_MDSTORETIME
      _process_data "AdvQueryMDStore" $ITER "yes" $SSRESFILE $SSRESSUM
      _record_fullness $SSRESFILE $SSRESSUM
    fi
    
    # Do aggregate query test for all query types
    if [ "$QUERY_AGG" = "true" ]
    then
      for QUERYTYPE in $QUERYTYPES; do
        _run_aggregate_query $ITER $AGG_QUERYTIME $QUERYTYPE
      done
      _process_query "AdvQuery" $ITER "no" $AGGRESFILE $AGGRESSUM $QUERYTYPE $STATSUMFILE
    fi

    # Do single stream AdvQuery test for all filesizes
    if [ "$QUERY_SS" = "true" ]
    then
      for QUERYTYPE in $QUERYTYPES; do
        _run_single_stream_query $ITER $SS_QUERYTIME $QUERYTYPE
      done
      _process_query "AdvQuery" $ITER "yes" $SSRESFILE $SSRESSUM $QUERYTYPE $STATSUMFILE
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


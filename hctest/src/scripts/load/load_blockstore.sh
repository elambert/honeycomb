#!/bin/bash
#
# $Id: load_blockstore.sh 10858 2007-05-19 03:03:41Z bberndt $
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
# Load generator scripts, uses so-called BlockStore tool.
#
# SET THESE VARIABLES:
#
# CLUSTER, NODES, CLIENTS, CNUM, PROCS, FILESIZES, 
# ITERATIONS, TIME, LOGDIR
# SVN, USER (if wiping audit DB)
#
# The script runs 3 loops:
# loop ($ITERATIONS)
#  loop ($PROCS)
#    loop($FILESIZES)
#      run for $TIME
#    pool
#  pool
# pool
#
# If you want to kill a running load_blockstore test, execute:
# ./load_blockstore.sh stop
#

# Log directory. This script's output goes to stdout.
LOGDIR=`pwd`
mkdir -p $LOGDIR

# cluster
CLUSTER=devXXX
NODES=16

# clients
CLIENTS="clXX,clYY,clZZ"
CNUM=3 # number of clients
PROCS="1 2 3" # number of threads per client per test iteration

# file sizes to store, then retrieve (data size)
# Note: metadata size is hard-coded in MDStaticOpGenerator
# Note: selection of file sizes is determined by mixes.
FILESIZES="1k 50k 1m 100m 1g"
# alternative is picking filesize randomly from specified mixes.
FILESIZES="random"
MIXES=mix1,mix2,mix3,ofotomix,multichunk,stanfordmix,fewmegsmix

# duration of run
ITERATIONS=2
TIME=5 # minutes (each iteration)

# DO NOT EDIT PARAMETERS BELOW UNLESS YOU KNOW WHAT THEY MEAN...

# operations
STORE=StoreOpGenerator
RETRIEVE=FetchOpGenerator
ADDMD=MDStaticOpGenerator
STOREWITHMD=StoreWithMDOpGenerator
FETCHMD=FetchMDOpGenerator
QUERYMD=QueryMDStaticOpGenerator

MAXFETCHCOUNT=1000
#BLOCKSTOREOPTS="noValidate:nohash:threadedaudit:maxfetchcount=${MAXFETCHCOUNT}"
BLOCKSTOREOPTS="noValidate:nohash:threadedaudit:maxfetchcount=${MAXFETCHCOUNT}:recycleoids"

BLOCKSTORE="/opt/test/bin/runtest com.sun.honeycomb.hctest.cases.storepatterns.ContinuousStore -ctx factory=ContinuousMixFactory:cluster=${CLUSTER}:time=${TIME}m:mixes=${MIXES}:${BLOCKSTOREOPTS}:nodes=${NODES}"

# Translate command name (as reported by statistics) into OpGenerator classname
# Legal CMD values are: Store Retrieve MDStore MDAdd MDRetrieve Query Delete
CMDOP= # global var
_cmd_to_op () {
  CMD=$1
  case $CMD in
    Store)
      CMDOP="100%StoreOpGenerator" ;;
    Retrieve)
      CMDOP="100%FetchOpGenerator" ;;
    MDStore)
      CMDOP="100%StoreWithMDOpGenerator" ;;
    MDAdd)
      CMDOP="100%MDStaticOpGenerator" ;;
    MDRetrieve)
      CMDOP="100%FetchMDOpGenerator" ;;
    QuerySimple)
      CMDOP="100%QueryMDStaticOpGenerator" ;;
    QueryComplex)
      CMDOP="100%QueryMDStaticOpGenerator" ;;
    Delete)
      CMDOP="100%DeleteOpGenerator" ;;
    *)
      CMDOP=$CMD ;;
      # do not prepend 100% in default case to allow for mixed load expressions
  esac
}

# Translate command name into keyword to query statistics for
CMDSTAT= # global var
_cmd_to_stats () {
  CMD=$1
  case $CMD in
    MDStore)
      CMDSTAT=Store ;;
    QuerySimple)
      CMDSTAT=Query ;;
    QueryComplex)
      CMDSTAT=Query ;;
    Store|MDAdd|Retrieve|MDRetrieve|Delete)
      CMDSTAT=$CMD ;;
    *)
      CMDSTAT= ;;
  esac
}

# get statistics out of the test run's log
# Legal CMD values are: Store Retrieve MDStore MDAdd MDRetrieve Query Delete
# For mixed load expressions, report all statistics
_print_stats () {
  _cmd_to_stats $1
  LOG=$2
  egrep "RUN_ID|Duration|Total $CMDSTAT|Avg $CMDSTAT" $LOG
}

# Careful! Only do this if you wiped your cluster!
_wipe_audit_DB () {
  echo "WIPING AUDIT DB"
  /opt/test/bin/dbscript.sh -r -c $CLUSTER
}

# restart RMI on each client
_restart_RMI () {
  echo
  echo "RESTARTING RMI ON CLIENTS [$CLIENTS]"
  _stop_RMI
  sleep 30
  _start_RMI
}
                                                                                                                            
_stop_RMI() {
  DO_CLIENTS=`echo $CLIENTS | sed 's/,/ /g'`
  for CLIENT in $DO_CLIENTS; do
    ssh $CLIENT "/opt/test/cur/bin/ShutdownClntSrv >/dev/null 2>&1 </dev/null"
    sleep 1
    ssh $CLIENT pkill -9 -f honeycomb
    ssh $CLIENT pkill java
    ssh $CLIENT pkill -9 java
  done
}
                                                                                                                            
_start_RMI() {
  DO_CLIENTS=`echo $CLIENTS | sed 's/,/ /g'`
  for CLIENT in $DO_CLIENTS; do
    ssh $CLIENT "/opt/test/cur/bin/RunClntSrv >/dev/null 2>&1 </dev/null &"
    sleep 1
  done
}

# Capture cluster state
_cmm_verifier () {
  OUTFILE="cmm.`date +%m%d-%H%M`"
  echo
  echo -n "CMM VERIFIER [$OUTFILE]: "
  ssh ${CLUSTER}-cheat /opt/test/bin/cmm_verifier $NODES 1 1 yes quorum >$OUTFILE 2>&1
  grep "CLUSTER STATE" $OUTFILE |tail -1
}

# Measure HADB fullness: count of objects, deviceinfo, resourceinfo
# XXX: HACK! Assumes that node 101 is up! May hang if HADB is down!!
#
_hadb_fullness () {
  OUTFILE=$1
  
  MSG="Count of objects in HADB:"
  if [ ! -e tablename ]; then
    echo "select tablename from attributetable;" > tablename.in
  fi
  scp -P 2001 tablename.in root@${CLUSTER}-admin:tablename
  cmd="ssh root@${CLUSTER}-admin -p 2001 /opt/SUNWhadb/4/bin/clusql"
  cmd="$cmd -nointeractive localhost:15005 system+superduper -command=tablename"
  cmd="$cmd | grep -i T_SYSTEM | grep -vi TEST_TYPE | tail -1"
  TABLENAME=`$cmd` 
  if [ ! -e hadb_fullness ]; then  
      echo "select count(*) from $TABLENAME;" >hadb_fullness.in
  fi
  scp -P 2001 hadb_fullness.in root@${CLUSTER}-admin:hadb_fullness
  COUNT=`ssh root@${CLUSTER}-admin -p 2001 /opt/SUNWhadb/4/bin/clusql -nointeractive localhost:15005 system+superduper -command=hadb_fullness |tail -2 |tr -d "[:space:]"`
  echo "$MSG $COUNT"  # to the main log
  echo >>$OUTFILE
  echo "[`date`] $MSG $COUNT" >>$OUTFILE
  
  MSG="[`date`] HADB deviceinfo:"
  echo >>$OUTFILE
  echo $MSG >>$OUTFILE
  echo admin |ssh root@${CLUSTER}-admin -p 2001 /opt/SUNWhadb/4/bin/hadbm deviceinfo honeycomb >>$OUTFILE

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
  OUTFILE="full.`date +%m%d-%H%M`"
  echo "CLUSTER FULLNESS [$OUTFILE]:" >>$OUTFILE

  echo -n "Disk fullness: " >>$OUTFILE
  ssh admin@${CLUSTER}-admin df -h >>$OUTFILE
  cat $OUTFILE  # to the main log
  echo >>$OUTFILE

  echo "Physical disk fullness:" >>$OUTFILE
  ssh admin@${CLUSTER}-admin df -p >>$OUTFILE
  echo >>$OUTFILE

  _hadb_fullness $OUTFILE
}

# Run given command, redirect output to given log file
#
_run_test () {
  CMD=$1   # Legal CMD values are: Store Retrieve MDStore MDAdd MDRetrieve Query Delete
  SIZE=$2  # Filesize (data object size)
  PROCS=$3 # Processes (threads) per client
  EXTRAS=$4  # Additional options to BlockStore command line, if any. Must start with ":"
  
  _cmd_to_op $CMD # Note: for mixed load, expression is not translated, eg: 30%StoreOpGenerator,70%FetchOpGenerator
 
  LOG=${LOGDIR}/${ITER}.${CMD}.${CLUSTER}.${CNUM}x${PROC}.${SIZE} # eg: logs/42.Store.dev318.8x4.5k
  
  RUNTEST="${BLOCKSTORE}:clients=${CLIENTS}:processes=${PROC}:operations=${CMDOP}${EXTRAS}"
  
  if [ "$SIZE" != "random" ]; then
      RUNTEST="${RUNTEST}:minsize=${SIZE}:maxsize=${SIZE}"
  fi

  echo
  echo [`date`] ${CMD}. FILESIZE ${SIZE}. ${CNUM}x${PROC} CLIENTS. TIME ${TIME}m. LOG $LOG.
  echo $RUNTEST
  echo "[`date`] Running $CMD Test iteration $ITER... "

  # workaround for stuck tests: wait for completion 
  # for at most 3 minutes on top of expected runtime
  $RUNTEST >$LOG 2>&1 </dev/null &
  let MINUTES=$TIME+3
  i=0
  while [ $i -lt $MINUTES ]; do
      o=`grep "END RUN" $LOG`
      if [ $? -eq 0 ]; then
          break
      fi
      if [ $i -gt $TIME ]; then
          echo "[`date`] Waiting for the test iteration $ITER to finish..."
      fi
      sleep 60
      let i=$i+1
  done
  _restart_RMI

  echo "[`date`] Test iteration $ITER is done."
  _print_stats $CMD $LOG

  _cmm_verifier
  _cluster_fullness
}

######### MAIN ##########

# To stop an already running set of tests...
if [ "$1" = "stop" ]; then
  ps aux |grep load_blockstore |grep -v stop | awk '{print $2}' |xargs kill
  _stop_RMI
  exit 0
fi

echo [`date`] STARTING CLUSTER FILLUP TEST. CLIENTS=$CNUM [$CLIENTS]. PROCESSES=[$PROCS]. ITERATIONS=[$ITERATIONS]. EACH RUN=${TIME}m.

# Initial setup
_restart_RMI
_cmm_verifier
_cluster_fullness

# Run loop: repeat for given number of iterations, for each process count, for each file size.
# Add/remove tests as desired. Each _run_test will take $TIME minutes.
#
ITER=0
while [ $ITER -lt $ITERATIONS ]; do
  for PROC in $PROCS; do
    for SIZE in $FILESIZES; do

      ((ITER++))

      _run_test "Store" $SIZE $PROC

      _run_test "MDStore" $SIZE $PROC

      _run_test "MDAdd" $SIZE $PROC

      _run_test "Retrieve" $SIZE $PROC

      _run_test "MDRetrieve" $SIZE $PROC

#     NOTE: to run the delete test, comment out :recycleoids
#     from BLOCKSTOREOPTS since this will cause duplicate deletes
#     which will fail.
#     _run_test "Delete" $SIZE $PROC

      _run_test "QuerySimple" $SIZE $PROC ":querytype=simplequery"

      _run_test "QueryComplex" $SIZE $PROC ":querytype=complexquery"

      # example of running a mixed load test
      #_run_test "30%StoreOpGenerator,70%FetchOpGenerator" $SIZE $PROC

    done    
  done
done

echo
echo [`date`] ALL DONE



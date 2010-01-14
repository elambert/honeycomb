#!/bin/sh
#
# $Id: healing_base.sh 11940 2008-03-21 19:17:17Z dm155201 $
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
# Common code to be used by healing tests.
#

SNAPSHOT=healing_snapshot # static snapshot name

TARGET=dd_healing_cycle_seconds # call init_dd_cycle to set

NODE=victim # call select_victim to set
DISK=victim

NODES="101 102 103 104 105 106 107 108 109 110 111 112 113 114 115 116"
DISKS="1 2 3" # XXX: skipping HADB disk zero

HEALTIME=dd_cycle_finish_timestamp_from_sysstat # call get_heal_time to set
LASTHEALTIME=when_last_dd_cycle_finished # reset after wait_to_heal
NEWHEALTIME=when_this_dd_cycle_finished

EMI_THREADS=10 # store/retrieve/query threads

# check for pre-requisites: whitebox package on cheat#
#
check_prerequisites () {
  CLUSTER=$1
  ssh root@${CLUSTER}-cheat "pkginfo SUNWhcwbsp"
  if [ "$?" != "0" ]
  then
	echo "Please install the SUNWhcwbsp on your cheat."
	exit -1
  fi

  # TODO: Also check for SUNWhcwbcluster on the nodes
}

# select which disk to fail
#
select_victim () {
  # TODO: node/disk should be randomly selected
  NODE=105
  DISK=2
}

# generate data set on the cluster using EMI tools
#
generate_data_set () {
  echo "[`date`] Running stores with data set ${DATASET}. Log file ${STOREFILE}"

  EMI_ARGS="num_threads min_size_bytes max_size_bytes pattern extended_md runtime_seconds num_ops poll_indexed socket_timeout_sec"
  ARGS1="binary 1 -1" # pattern(binary) extended_md(yes) runtime(unlimited)
  ARGS2="1 600" # poll_indexed(yes) socket_timeout(10 min)
  THR=$EMI_THREADS

  if [ "$DATASET" == "SmallSet_SmallFiles" ] 
  then
    EMI_ARGS="$THR 1024 1024 $ARGS1 10000 $ARGS2" # 10,000 1k files
    /opt/test/bin/load/emi_store.sh ${CLUSTER}-data $EMI_ARGS >$STOREFILE

  elif [ "$DATASET" == "SmallSet_LargeFiles" ] 
  then
    EMI_ARGS="$THR 1048576 1048576 $ARGS1 80 $ARGS2" # 80 100MB files
    /opt/test/bin/load/emi_store.sh ${CLUSTER}-data $EMI_ARGS >$STOREFILE
    EMI_ARGS="$THR 1073741824 1073741824 $ARGS1 10 $ARGS2" # 10 1GB files
    /opt/test/bin/load/emi_store.sh ${CLUSTER}-data $EMI_ARGS >>$STOREFILE
    EMI_ARGS="$THR 2147483648 2147483648 $ARGS1 10 $ARGS2" # 10 2GB files
    /opt/test/bin/load/emi_store.sh ${CLUSTER}-data $EMI_ARGS >>$STOREFILE

  elif [ "$DATASET" == "LargeSet_SmallFiles" ]
  then
    EMI_ARGS="$THR 1024 1024 $ARGS1 5000000 $ARGS2" # 5mln 1k files
    /opt/test/bin/load/emi_store.sh ${CLUSTER}-data $EMI_ARGS >$STOREFILE

  elif [ "$DATASET" == "FullCluster_LargeFiles" ] 
  then
    EMI_ARGS="$THR 1048576 1048576 $ARGS1 200000 $ARGS2" # 200,000 100MB files = 20TB
    /opt/test/bin/load/emi_store.sh ${CLUSTER}-data $EMI_ARGS >$STOREFILE
    EMI_ARGS="$THR 1073741824 1073741824 $ARGS1 2000 $ARGS2" # 2,000 1GB files = 2TB
    /opt/test/bin/load/emi_store.sh ${CLUSTER}-data $EMI_ARGS >>$STOREFILE
    EMI_ARGS="$THR 2147483648 2147483648 $ARGS1 2000 $ARGS2" # 2,000 2GB files = 4TB
    /opt/test/bin/load/emi_store.sh ${CLUSTER}-data $EMI_ARGS >>$STOREFILE

  else
    echo "Unsupported data set: $DATASET"
    exit 1
  fi
}


# run CLI command until it succeeds
# output goes into global $CLI_OUT
#
CLI_OUT=0
#
run_cli_command () {
  CMD="$1"
  TRY=0
  RC=1
  while [ "$RC" != "0" ]; do
    CLI_OUT=`ssh admin@${CLUSTER}-admin $CMD`
    RC=$?
    if [ "$RC" != "0" ]; then
      sleep 60
      TRY=`expr TRY+1`
      echo "[`date`] Cannot get to the CLI after $TRY retries"
    fi
  done
}

# set datadoctor healing cycles for fast speed, the rest at default
#
turn_dd_on () {
  echo "[`date`] Datadoctor on, healing cycle $TARGET"
  run_cli_command "ddcfg -F default"
  run_cli_command "ddcfg -F recover_lost_frags_cycle $TARGET"
  run_cli_command "ddcfg -F remove_dup_frags_cycle $TARGET"
}

# turn off datadoctor (before doing verification)
#
turn_dd_off () {
    echo "[`date`] Datadoctor off"
    run_cli_command "ddcfg -F off"
}

# configure correct datadoctor cycle target
#
init_dd_cycle () {
  if [ "$DATASET" == "SmallSet_SmallFiles" ]; then
    TARGET=600 # 10 minutes
  elif [ "$DATASET" == "SmallSet_LargeFiles" ]; then
    TARGET=600 # 10 minutes
  elif [ "$DATASET" == "LargeSet_SmallFiles" ]; then
    TARGET=3600 # 1 hour
  elif [ "$DATASET" == "FullCluster_LargeFiles" ]; then
    TARGET=43200 # 12 hours
  else
    echo "Unsupported dataset: $DATASET"
    exit -1
  fi

  turn_dd_off
  turn_dd_on
}


# read last healing cycle timestamp from CLI
#
get_heal_time () {
  DDTASK=$1
  run_cli_command "sysstat"
  echo "$CLI_OUT" >sysstat.out
  if [ "$DDTASK" == "repop" ]; then
    HEALTIME=`cat sysstat.out |grep 'Query Integrity' | awk '{print $6, $7, $8, $9, $10, $11}'`
  else 
    HEALTIME=`cat sysstat.out |grep 'Data Reliability' | awk '{print $7, $8, $9, $10, $11, $12}'`
  fi
}

# wait for healing or repop cycle to complete
#
wait_to_heal () {
  DDTASK=$1 # healing or repop
  # Get past the 3-minute grace period
  # sleep 200

  # XXX Workaround: sleep longer to skip past the
  # bogus superfast completed cycle...
  sleep $TARGET
  echo "[`date`] Sleeping for $TARGET seconds"

  TRY=0  
  get_heal_time $DDTASK
  LASTHEALTIME="$HEALTIME"
  NEWHEALTIME="$HEALTIME"
  echo "[`date`] Last healing cycle completed at $LASTHEALTIME"

  while [ "$NEWHEALTIME" == "$LASTHEALTIME" ]; do
    sleep 60
    TRY=`expr TRY+1`
    # XXX not sure if this logging loop works
    if [ "$TRY" == "10" ]; then
      echo "[`date`] Healing is not done after 10 minutes!"
      TRY=0
    fi
    get_heal_time
    NEWHEALTIME="$HEALTIME"
  done

  # TODO: Bail if waited for 4x cycle

  LASTHEALTIME="$NEWHEALTIME"
  echo "[`date`] Healing cycle completed at $LASTHEALTIME"
}

verify_snapshot () {
  turn_dd_off

  # Snapshot verification is limited to the healed disk for speed.
  #
  VERIFY_OUT=`mktemp -p $PWD verify.XXXXXX`
  echo "[`date`] Verifying data against live snapshot... Log file ${VERIFY_OUT}"
  ssh root@${CLUSTER}-cheat "/bin/yes | /opt/test/bin/verify_snapshot.sh $SNAPSHOT $NODE $DISK" >${VERIFY_OUT} 2>&1
  RC=$?
  if [ "$RC" == "0" ]; then
    echo "[`date`] Snapshot verification OK."
  else 
    echo "[`date`] Snapshot verification FAILED ($RC errors)."
  fi

  turn_dd_on
  return $RC
}

# Check if the output file from EMI read or query verification had any errors
#
verify_emi_results () {
  LOGFILE="$1"
  grep -q "ERR" $LOGFILE
  if [ "$?" == "0" ]; then
    echo "[`date`] EMI run had errors!!"
    echo "EXITING..."
    exit -1
  fi
  grep -q "OK" $LOGFILE
  if [ "$?" == "0" ]; then 
    echo "[`date`] EMI run verification passed."
  else 
    echo "[`date`] EMI run had no OK results!! Output:"
    cat $LOGFILE
    echo "EXITING..."
    exit -1
  fi
}

# Run retrieves and verify success
#
do_emi_retrieve () {
    CLUSTER=$1
    THREADS=$2
    INFILE=$3 # file with OIDs from EMI store
    OUTFILE=$4
    echo "[`date`] Doing retrieve with verification via EMI. Log file $OUTFILE"
    ARGSR="1 600" # retrieve args: content-verification(yes) socket-timeout(10min)
    cat $INFILE |/opt/test/bin/load/emi_retrieve.sh ${CLUSTER}-data $EMI_THREADS $ARGSR >$OUTFILE
    verify_emi_results $OUTFILE
}

# Run queries and verify success
#
do_emi_query () {
    CLUSTER=$1
    THREADS=$2
    INFILE=$3 # file with OIDs from EMI store
    OUTFILE=$4
    echo "[`date`] Doing query with verification via EMI. Log file $OUTFILE"
    ARGSQ="-1 -1 600 -" # query args: runtime(unlimited) num-ops(unlimited) socket-timeout(10min) query(default)
    cat $INFILE |/opt/test/bin/load/emi_query.sh ${CLUSTER}-data $EMI_THREADS $ARGSQ >$OUTFILE
    verify_emi_results $OUTFILE
}

# run given command on a cluster node
#
run_on_node() {
  NODE=$1
  CMD=$2
  PORT=`expr $NODE + 1900` # 2001..2016
  ssh -p $PORT root@${CLUSTER}-admin $CMD
}


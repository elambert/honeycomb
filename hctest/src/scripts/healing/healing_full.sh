#!/bin/sh
#
# $Id: healing_full.sh 10858 2007-05-19 03:03:41Z bberndt $
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
# This test is meant to run on a full cluster.
#
# Test takes a snapshot (must have whitebox pkg!),
# disables a disk and waits for healing to complete,
# verifies against snapshot, and
# does read-back verification with EMI tools.
# Then it wipes and reenables the disk, waits for heal-back,
# and repeats verification.
#
# You must specify filename with stores OIDs for verification.
#
# Test duration >24 hours.
#

if [ "$#" != "2" ]; then
  echo "Usage: $0 <cluster> <dataSet>"
  echo "DataSet choices: SmallSet_SmallFiles, SmallSet_LargeFiles, LargeSet_SmallFiles, FullCluster_LargeFiles"
  exit -1
fi

CLUSTER=$1
DATASET=$2

# Load functions
source ./healing_base.sh

check_prerequisites $CLUSTER

# output files
PWD=`pwd`
STOREFILE=subset.oids # XXX: USER MUST SPECIFY FILENAME!
RETRIEVEFILE_1=`mktemp -p $PWD retrieves_1.XXXXXX` # after healing
QUERYFILE_1=`mktemp -p $PWD queries_1.XXXXXX`
RETRIEVEFILE_2=`mktemp -p $PWD retrieves_2.XXXXXX` # after heal-back
QUERYFILE_2=`mktemp -p $PWD queries_2.XXXXXX`

### MAIN ###

# ensure correct datadoctor settings
#
init_dd_cycle

echo "[`date`] Cluster is full, not storing any more data."

# take live snapshot
#
SNAPSHOT_OUT=`mktemp -p $PWD snapshot.XXXXXX`
echo "[`date`] Taking live snapshot... Output file ${SNAPSHOT_OUT}"
ssh root@${CLUSTER}-cheat "/bin/yes | /opt/test/bin/snapshot.sh delete $CLUSTER $SNAPSHOT" >${SNAPSHOT_OUT} 2>&1
ssh root@${CLUSTER}-cheat "/bin/yes | /opt/test/bin/snapshot.sh save $CLUSTER $SNAPSHOT live" >>${SNAPSHOT_OUT} 2>&1

# this number is not used, only for logging
#
get_heal_time
LASTHEALTIME="$HEALTIME"
echo "[`date`] Previous healing cycle completed at $LASTHEALTIME"

# Kill a disk and wait for healing to complete
#
select_victim
echo "[`date`] Disabling disk $NODE:$DISK"
run_cli_command "hwcfg -F -D DISK-${NODE}:${DISK}"

echo "[`date`] Waiting for healing cycle to complete"
wait_to_heal

# Do verification: snapshot, retrieve, query
#
verify_snapshot
# XXX Not sure if this conditional ever executes
if [ "$?" != "0" ]; then
  echo "[`date`] HACK: Waiting for 2nd healing cycle to complete..."
  wait_to_heal
  verify_snapshot
  if [ "$?" != "0" ]; then
    echo "[`date`] ERROR: Snapshot verification still fails after 2nd healing cycle."
    echo "EXITING..."
    exit -1
  fi
fi

echo "[`date`] Verifying a subset of stored OIDs using file $STOREFILE"

echo "[`date`] Doing retrieve with verification via EMI. Log file ${RETRIEVEFILE_1}"
cat $STOREFILE |/opt/test/bin/load/emi_retrieve.sh ${CLUSTER}-data $EMI_THREADS >${RETRIEVEFILE_1}
verify_emi_results ${RETRIEVEFILE_1}

echo "[`date`] Doing query with verification via EMI. Log file ${QUERYFILE_1}"
cat $STOREFILE |/opt/test/bin/load/emi_query.sh ${CLUSTER}-data $EMI_THREADS -1 -1 - >${QUERYFILE_1}
verify_emi_results ${QUERYFILE_1}
 
# Reenable disk and wait for heal-back
#
# Wipe the disk before reenabling.
#
# XXX: Need to mount the disk.
# Currently it's mounted because of disable bug.
#
echo "[`date`] Wiping disk $NODE:$DISK"
NPORT=`expr $NODE + 1900`
ssh -p $NPORT root@${CLUSTER}-admin rm -rf /data/${DISK}/*

echo "[`date`] Reenabling disk $NODE:$DISK"
run_cli_command "hwcfg -F -E DISK-${NODE}:${DISK}"

echo "[`date`] Waiting for heal-back cycle to complete"
wait_to_heal

# Do verification again: snapshot, retrieve, query
#
verify_snapshot
if [ "$?" != "0" ]; then
  echo "[`date`] HACK: Waiting for 2nd heal-back cycle to complete..."
  wait_to_heal
  verify_snapshot
  if [ "$?" != "0" ]; then
    echo "[`date`] ERROR: Snapshot verification still fails after 2nd healing cycle."
    echo "EXITING..."
    exit -1
  fi
fi

echo "[`date`] Doing retrieve with verification via EMI. Log file ${RETRIEVEFILE_2}"
cat $STOREFILE |/opt/test/bin/load/emi_retrieve.sh ${CLUSTER}-data $EMI_THREADS >${RETRIEVEFILE_2}
verify_emi_results ${RETRIEVEFILE_2}

echo "[`date`] Doing query with verification via EMI. Log file ${QUERYFILE_2}"
cat $STOREFILE |/opt/test/bin/load/emi_query.sh ${CLUSTER}-data $EMI_THREADS -1 -1 - >${QUERYFILE_2}
verify_emi_results ${QUERYFILE_2}

echo "ALL DONE"
exit 0


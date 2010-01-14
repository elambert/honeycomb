#!/bin/sh
#
# $Id: repop_test.sh 11940 2008-03-21 19:17:17Z dm155201 $
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
# For cleanest test, run on empty cluster. 
#
# Test stores X objects using EMI tools,
# then wipes HADB, restarts the cluster,
# waits for repopulation to complete,
# then does query verification with EMI tools.
#
# Test duration ~30 min for small data set, ~12 hours for large.
#

if [ "$#" != "2" ]; then
  echo "Usage: $0 <cluster> <dataSet>"
  echo "DataSet choices: SmallSet_SmallFiles, LargeSet_SmallFiles"
  exit -1
fi

CLUSTER=$1
DATASET=$2

ROOT=`dirname $0`
if [ "$ROOT" = "." ]; then
    ROOT=`pwd`
fi

# Load functions
source ${ROOT}/healing_base.sh

check_prerequisites $CLUSTER

HADBSTATUS=unknown

# output files
PWD=`pwd`
STOREFILE=`mktemp -p $PWD stores.XXXXXX` # will contain stored OIDs
QUERYFILE_1=`mktemp -p $PWD queries_1.XXXXXX`
QUERYFILE_2=`mktemp -p $PWD queries_2.XXXXXX`


# GOTO MAIN

### FUNCTIONS ###

# set datadoctor healing cycles for fast speed, the rest at default
#
turn_repop_on () {
  echo "[`date`] Datadoctor on, healing cycle $TARGET"
  run_cli_command "ddcfg -F default"
  run_cli_command "ddcfg -F populate_ext_cache_cycle $TARGET"
}

# configure correct datadoctor cycle target
#
init_hadb_dd_cycle () {
  if [ "$DATASET" == "SmallSet_SmallFiles" ]; then
    TARGET=600 # 10 minutes
  elif [ "$DATASET" == "LargeSet_SmallFiles" ]; then
    TARGET=43200 # 12 hours
  else
    echo "Unsupported dataset: $DATASET"
    exit -1
  fi

  turn_repop_on
}

# get HADB status
#
get_hadb_status() {
  run_cli_command "sysstat"
  echo "$CLI_OUT" >sysstat.out
  HADBSTATUS=`cat sysstat.out |grep "Query" |head -1 |cut -d" " -f7`
}

# wait for HADB to come online
#
wait_for_hadb() {
  TRY=0
  get_hadb_status
  
  while [ "$HADBSTATUS" != "FaultTolerant" ] &&
        [ "$HADBSTATUS" != "HAFaultTolerant" ]; do
      sleep 60
      TRY=`expr $TRY + 1`
      if [ "$TRY" == "10" ]; then
          echo "[`date`] HADB is not online after 10 minutes!"
          TRY=0
      fi 
      get_hadb_status
  done
}

### MAIN ###

# ensure HADB is online
#
wait_for_hadb

# store data on the cluster
#
generate_data_set 

# verify correct state of HADB by querying
#
do_emi_query $CLUSTER $EMI_THREADS $STOREFILE $QUERYFILE_1

# wipe HADB and restart the cluster
#
echo "[`date`] Wiping HADB"
ssh admin@${CLUSTER}-admin hadb clear -F

echo "[`date`] Sleeping 5 minutes to let HADB clear"
sleep 300

# ensure HADB is online
#
echo "[`date`] Waiting for HADB to get recreated"
wait_for_hadb

get_heal_time repop
LASTHEALTIME="$HEALTIME"
echo "[`date`] Previous repop cycle completed at $LASTHEALTIME"

init_hadb_dd_cycle

echo "[`date`] Waiting for healing cycle to complete"
wait_to_heal repop

# do query with verification
#
do_emi_query $CLUSTER $EMI_THREADS $STOREFILE $QUERYFILE_2

echo "ALL DONE"
exit 0


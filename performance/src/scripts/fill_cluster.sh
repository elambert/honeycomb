#!/bin/bash
#
# $Id: fill_cluster.sh 11199 2007-07-11 22:07:48Z hs154345 $
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
# script to fill a cluster with a mix of files

##
# Usage: ./fill_cluster.sh <starting iter> <target fullness> <logdir> <ifinpercentage>
# <starting iter> - starting iteration number (for naming)
# <target fullness> - target fullness according to df, 
#                   in terms of percentage or number of objects (ie, 4 = 4%, or 1000000 objs)
# <logdir> - full pathname of the directory in which to log results
# <ifinpercentage> - true: the filling target is in percentage of capacity, 10=10% 
#                  - false: the filling target is in number of objs, 1000000 for 1 mln objs 
##

#######################################################################
# Parameters to tweak
#
# duration of iteration
TIME=20 # minutes (each iteration)

# Filesizes
FILESIZES="1048576 2097152 3145728 4194304 5242880"

# cluster
CLUSTER=dev310
NODES=16

# clients
CLIENTS="cl151 cl152 cl153 cl154 cl155 cl156 cl157 cl158"

# number of threads per client per test iteration
PROCS="20"
#
###################################################################

# Arguments to script

# Iteration number to name with
STARTITER=$1

# Target fullness
TARGETFULL=$2

# log directory
LOGDIR=$3

# if the filling target is in percentage of capacity
IF_IN_PERCENTAGE=$4

# expected number of arguments
NUM_ARGS=4

# Script variables

TMPARRAY=($CLIENTS)
NUMCLIENTS=${#TMPARRAY[@]}
NUMCLPROCS="${NUMCLIENTS}x${PROCS}"

# Fullness
FULLNESS=0
NEWFULLNESS=0

ITER=$STARTITER

let RUNTIME=$TIME*60

_print_usage() {
    echo "Usage: ./fill_cluster.sh <starting iteration> 
             <target fullness> <results directory> <ifinpercentage>"
}

# Capture cluster state
_cmm_verifier () {
  OUTFILE="${LOGDIR}/status/cmm.`date +%m%d-%H%M`"
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
  
#  MSG="Count of objects in HADB:"
  if [ ! -e hadb_fullness ]; then
    # T98C20F is the system.object_ctime table
    echo "select count(*) from t_system;" >hadb_fullness.in
  fi
  scp -P 2001 hadb_fullness.in root@${CLUSTER}-admin:hadb_fullness
  COUNT=`ssh root@${CLUSTER}-admin -p 2001 /opt/SUNWhadb/4/bin/clusql -nointeractive localhost:15005 system+superduper -command=hadb_fullness |tail -2 |tr -d "[:space:]"`
#  echo "$MSG $COUNT"  # to the main log
#  echo >>$OUTFILE
#  echo "[`date`] $MSG $COUNT" >>$OUTFILE
  
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

  NEWFULLNESS=`ssh admin@${CLUSTER}-admin df -h | awk '{print $11}' | sed -e 's/%//g'`
  if [ "$NEWFULLNESS" != "" ]; then
     FULLNESS=$NEWFULLNESS
  fi
#  echo [`date`] New fullness: ${FULLNESS}

  echo "Physical disk fullness:" >>$OUTFILE
  ssh admin@${CLUSTER}-admin df -p >>$OUTFILE
  echo >>$OUTFILE
  
  _hadb_fullness $OUTFILE
}



# Try to stop an already running set of tests
if [ "$1" = "stop" ]; then
  ps -ef |grep fill_cluster |grep -v stop | awk '{print $2}' |xargs kill
  for CLIENT in $CLIENTS; do
	ssh $CLIENT "ps -ef | grep java | grep -v grep | awk '{print $2}' | xargs kill"
  done
  exit 0
fi

if [ $# -ne $NUM_ARGS ]; then
    _print_usage
    exit 1
fi

echo [`date`] CREATING LOG DIRECTORIES ON ALL CLIENTS
for CLIENT in $CLIENTS; do
    echo "Creating ${LOGDIR} and status dir on ${CLIENT}"
    ssh $CLIENT "mkdir -p ${LOGDIR}/status"
done

_cluster_fullness

if [ "$IF_IN_PERCENTAGE" = "true" ]; then
     echo [`date`] STARTING TO FILL CLUSTER. TARGET IS ${TARGETFULL}%
     FILL=`echo $FULLNESS | awk -F. '{print $1}'`
else
     echo [`date`] STARTING TO FILL CLUSTER. TARGET IS ${TARGETFULL} OBJECTS
     FILL=$COUNT
fi


while [ $FILL -lt $TARGETFULL ]; do

  echo [`date`] CURRENT FULLNESS ${FULLNESS}%
  echo [`date`] CURRENT NUMBER OF OBJS ${COUNT}

  for CLIENT in $CLIENTS; do
    echo "Creating ${LOGDIR}/${ITER} on ${CLIENT}"
    ssh $CLIENT "mkdir -p ${LOGDIR}/${ITER}"
  done

  for SIZE in $FILESIZES; do

    echo [`date`] Storing ${SIZE}
    for CLIENT in $CLIENTS; do
	 echo [`date`] Launching MDstore of ${SIZE} on ${CLIENT}
	 ssh $CLIENT "cd /opt/performance; source /etc/profile; ./run_test.sh MDStore ${ITER} ${CLUSTER} ${PROCS} ${SIZE} ${RUNTIME} ${NUMCLPROCS} ${LOGDIR}" &
	
    done

    wait
  done  
  
  _cmm_verifier
  _cluster_fullness
   
   if [ "$IF_IN_PERCENTAGE" = "true" ]; then
        FILL=`echo $FULLNESS | awk -F. '{print $1}'`
   else
        FILL=$COUNT
   fi

  ((ITER++))
done
echo
echo [`date`] ALL DONE


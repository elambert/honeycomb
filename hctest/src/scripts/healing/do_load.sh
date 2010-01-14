#!/bin/bash
#
# $Id: do_load.sh 11940 2008-03-21 19:17:17Z dm155201 $
#f
# Load generation script for healing tests. 
# Takes an output file with OIDs from EMI store
# Retrieves and queries them forever. 
#

CLUSTER=$1
STOREFILE=$2

while [ 1 ]; do 
  ARGSR="10 0 600" # num-threads(10) content-verification(no) socket-timeout(10min)
  cat $STOREFILE |/opt/test/bin/load/emi_retrieve.sh ${CLUSTER}-data $ARGSR >>retrieve_load.out
done &

while [ 1 ]; do 
  ARGSQ="10 -1 -1 600 -" #num-threads(10) runtime(unlimited) num-ops(unlimited) socket-timeout(10min) query(default)
  cat $STOREFILE |/opt/test/bin/load/emi_query.sh ${CLUSTER}-data $ARGSQ >>query_load.out
done &

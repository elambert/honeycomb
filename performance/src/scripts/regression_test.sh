
#!/bin/bash
#
# $Id: regression_test.sh 10845 2007-05-19 02:31:46Z bberndt $
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
# Note: Wipe the cluster and load the QA schema before starting this test.
#
# XXX: Aggregate tests results are very different from single stream
# performance for store and retrieve. The anamoly shows up in large
# file store/retrieve. For single stream, retrieve performance for
# 100MB files are better than stores, and the opposite is true for
# aggregate performance. However, aggregate tests are not enabled in
# this suite since it has not been tested if we can produce consistent
# results using multiple streams.
##

# Tolerance for comparing results
TOLERANCE=5  # % of expected value
STATSTHRESHOLD=1048756 # 1MB

# cluster and number of nodes in cluster
CLUSTER=
NODES=16
MASTERCELL=
# clients to be used in the test and number of clients
CLIENTS=

# log directory
LOGDIR=

### process options
while getopts hc:l:n:t:m: name
do
  case $name in
    c)      CLUSTER=$OPTARG ;;
    l)      LOGDIR=$OPTARG ;;
    n)      NODES=$OPTARG ;;
    t)      CLIENTS=$OPTARG ;;
    m)      MASTERCELL=$OPTARG ;;
    h|?)    cat <<-END >&2
            USAGE: regression_test.sh <-c cluster> [-m master_cell] <-t clients> <-l logdir> [-n numNodes]
            eg,
               regression_test.sh -c dev309 -t cl5 -l /tmp
               regression_test.sh -c dev309 -t cl5 -l /tmp -n 16
		END
            exit 1
  esac
done

shift $(( $OPTIND - 1 ))

if [ -z $CLUSTER ] || [ -z $CLIENTS ] || [ -z $LOGDIR ]; then
  $0 -h; exit
fi

LOGDIR=`cd $LOGDIR; pwd`

if [ -d $LOGDIR ]; then
  list=`ls $LOGIDR | wc -w`
  if [ $list -gt 0 ]; then
    time=`date +%s`
    mv $LOGDIR ${LOGDIR}.save.${time}
    mkdir -p $LOGDIR
  fi
else
  mkdir -p $LOGDIR
fi

###################################################################
# number of threads per client per test iteration
PROCS="20"

# Filesizes to be used in bytes and number of filesizes
FILESIZES="10 5120 102400000"

# Operations to test - comment out any tests that are not to be performed
# NOTE: Retrieve relies on Store having been run first
#       MDOnly Delete relies on AddMD having been run first
#       MDData Delete relies on MDStore having been run first
#       AddMD relies on Store having been run first
#       Query UNIQUE relies on MDStore having been run first

# Single Stream Tests
STORE_SS=true
MDSTORE_SS=true
ADDMD_SS=true
RETRIEVE_SS=true
QUERY_SS=true
DELETE_SS=true

# Aggregate Tests
STORE_AGG=false
MDSTORE_AGG=false
ADDMD_AGG=false
RETRIEVE_AGG=false
QUERY_AGG=false
DELETE_AGG=false

# Query types
#QUERYTYPES="EMPTY UNIQUE SIMPLE COMPLEX2 COMPLEX3 COMPLEX4 COMPLEX5 COMPLEX6 ALL"
QUERYTYPES="EMPTY UNIQUE SIMPLE COMPLEX2"

# Delete types
DELTYPES="MDonly MDData"

# Amount of time test should be run for each file size in minutes
# (ie, if there are 5 filesizes, and STORETIME=10, then the Store test
# will take a total of 50 minutes for one iteration of the test)
# Total test time = #filesizes * time for test * #tests

# Default time for convenience
SS_TIME=5 #minutes - time for single stream tests
AGG_TIME=5 #minutes - time for aggregate tests

NUMSIZES=`echo $FILESIZES | wc -w`

# Single stream tests (converts to seconds, replace $SS_TIME if desired):
let SS_STORETIME=$SS_TIME*60
let SS_MDSTORETIME=$SS_TIME*60
let SS_ADDMDTIME=$SS_TIME*60
let SS_RETRIEVETIME=$SS_TIME*60
#default query time is half normal runtime
let SS_QUERYTIME=$SS_TIME*60/2 
#default delete time is normal runtime divided by number of filesizes
let SS_DELTIME=$SS_TIME*60

# Aggregate tests (converts to seconds, replace $AGG_TIME if desired):
let AGG_STORETIME=$AGG_TIME*60
let AGG_MDSTORETIME=$AGG_TIME*60
let AGG_ADDMDTIME=$AGG_TIME*60
let AGG_RETRIEVETIME=$AGG_TIME*60
#default query time is half normal runtime
let AGG_QUERYTIME=$AGG_TIME*60/2
#default delete time is normal runtime divided by number of filesizes
let AGG_DELTIME=$AGG_TIME*60

# Arguments to the script
# beginning and ending iteration numbers
ITER=1
ITERATIONS=2

########################################################################
_compare() {
  if [ -z $3 ]; then
    echo "ERROR: $1, value is empty"
    return 1
  fi
  
  ref=`echo "scale=2; $2/1.0" | bc -l`
  value=`echo "scale=2; $3/1.0" | bc -l`

  tolerance=`echo "$TOLERANCE * $ref * 10000 " | bc -l | cut -d'.' -f1,1`
  diff=`echo "($ref - $value) * 1000000" | bc -l | cut -d'.' -f1,1`
  if [ $diff -gt $tolerance ]; then
    echo "ERROR: $1, expected $ref, got $value"
    return 1
  else
    echo "INFO: $1, $value (expected $ref)" 
    return 0 
  fi
}

_verify() {
  refFile=$1
  summaryFile=$2
  retval=0
  for fsize in $FILESIZES; do
    
    if [ $fsize -gt $STATSTHRESHOLD ] 
    then 
        # store
        val=`egrep "^Store $fsize :" $summaryFile | cut -d ' ' -f 6,6`
        ref=`egrep "^Store $fsize :" $refFile | cut -d ' ' -f 6,6`
        if ! _compare "Store $fsize (MB/sec)" $ref $val; then
            retval=1
        fi
    else
        # store
        val=`egrep "^Store $fsize :" $summaryFile | cut -d ' ' -f 4,4`
        ref=`egrep "^Store $fsize :" $refFile | cut -d ' ' -f 4,4`
        if ! _compare "Store $fsize (ops/sec)" $ref $val; then
            retval=1
        fi
    fi
  
    if [ $fsize -gt $STATSTHRESHOLD ]
    then
        # mdstore
        val=`egrep "^MDStore $fsize :" $summaryFile | cut -d ' ' -f 6,6`
        ref=`egrep "^MDStore $fsize :" $refFile | cut -d ' ' -f 6,6`
        if ! _compare "MDStore $fsize (MB/sec)" $ref $val; then
           retval=1
        fi
    else
        # mdstore
        val=`egrep "^MDStore $fsize :" $summaryFile | cut -d ' ' -f 4,4`
        ref=`egrep "^MDStore $fsize :" $refFile | cut -d ' ' -f 4,4`
        if ! _compare "MDStore $fsize (ops/sec)" $ref $val; then
           retval=1
        fi
    fi

    if [ $fsize -gt $STATSTHRESHOLD ]
    then
        # retrieve
        val=`egrep "^Retrieve $fsize :" $summaryFile | cut -d ' ' -f 6,6`
        ref=`egrep "^Retrieve $fsize :" $refFile | cut -d ' ' -f 6,6`
        if ! _compare "Retrieve $fsize (MB/sec)" $ref $val; then
           retval=1
        fi
    else
        # retrieve
        val=`egrep "^Retrieve $fsize :" $summaryFile | cut -d ' ' -f 4,4`
        ref=`egrep "^Retrieve $fsize :" $refFile | cut -d ' ' -f 4,4`
        if ! _compare "Retrieve $fsize (ops/sec)" $ref $val; then
           retval=1
        fi
    fi

    # addMD
    val=`egrep "^AddMD $fsize :" $summaryFile | cut -d ' ' -f 4,4`
    ref=`egrep "^AddMD $fsize :" $refFile | cut -d ' ' -f 4,4`
    if ! _compare "AddMD $fsize (ops/sec)" $ref $val; then
      retval=1
    fi

    # delete MD only
    val=`egrep "^Delete $fsize MDonly:" $summaryFile | cut -d ' ' -f 4,4`
    ref=`egrep "^Delete $fsize MDonly:" $refFile | cut -d ' ' -f 4,4`
    if ! _compare "Delete $fsize MDonly (ops/sec)" $ref $val; then
      retval=1
    fi

    # delete MD and data
    val=`egrep "^Delete $fsize MDData:" $summaryFile | cut -d ' ' -f 4,4`
    ref=`egrep "^Delete $fsize MDData:" $refFile | cut -d ' ' -f 4,4`
    if ! _compare "Delete $fsize MDData (ops/sec)" $ref $val; then
      retval=1
    fi
  done

  # Query
  for qtype in EMPTY UNIQUE SIMPLE COMPLEX2; do
    val=`egrep "^Query $qtype:" $summaryFile | cut -d ' ' -f 7,7`
    ref=`egrep "^Query $qtype:" $refFile | cut -d ' ' -f 7,7`
    if ! _compare "Query $qtype (avg. results/sec)" $ref $val; then
      retval=1
    fi
  done
  if [ $retval -ne 0 ]; then
    echo "ERROR: Test failed"
  else 
    echo "Test passed"
  fi
  exit $retval;
}

########################################################################

. `dirname $0`/performance_test_base.sh

_do_test $ITER $ITERATIONS $LOGDIR $@
_verify `dirname $0`/PERF_EXPECTED ${LOGDIR}/1.${CLUSTER}.1x1.summary

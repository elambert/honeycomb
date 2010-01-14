#!/bin/bash
#
# $Id: adv_query_test.sh 11964 2008-04-11 12:20:19Z dr129993 $
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
# Note: Wipe the cluster and load the QA schema and the advanced
#       query stress test schema before starting this test.
#
# Runs a full cycle of the advanced query stress test. This includes
# loading the database with randomly generated metadata for an hour and
# then it runs a series of queries based on the data loaded.
##

# Tolerance for comparing results
TOLERANCE=15  # % of expected value
STATSTHRESHOLD=1048756 # 1MB

# cluster and number of nodes in cluster
CLUSTER=
NODES=16
# clients to be used in the test and number of clients
CLIENTS=

# log directory
LOGDIR=

### process options
while getopts hc:l:n:t:v:p:o:m:q:i:e:w:g: name
do
  case $name in
    c)      CLUSTER=$OPTARG ;;
    l)      LOGDIR=$OPTARG ;;
    n)      NODES=$OPTARG ;;
    t)      CLIENTS=$OPTARG ;;
    v)      STORE_LOG=$OPTARG ;;
    p)      STREAMS=$OPTARG ;;
    o)      TEST_OPTION=$OPTARG ;;
    m)      METADATA_VERSION=$OPTARG ;;
    q)      QUERY_TEST_TYPE=$OPTARG ;;
    i)      NUM_ITERS=$OPTARG ;;
    e)      TEST_TIME=$OPTARG ;;
    w)      WAIT_TIME=$OPTARG ;;
    g)      RESULTS_GROUP_SIZE=$OPTARG ;;
    h|?)    cat <<-END >&2
            USAGE: adv_query_test.sh <-c cluster> [-n numNodes]  <-t clients> <-l logdir> [-v storelogfile] 
                    [-p 1 | 5 | 10 | 20] [-o SS | AG | STORE | STRESSAGG ] [-m metadata/pattern file] 
                    [-q ADVQUERY STRESS MAX_FETCH_LIMIT or individual query type ]
                    Individual query types include: COMPLEX, OREQUAL, COMPLEX2, COMPLEX3, COMPLEX4, 
                    COMPLEX5, COMPLEX6, UNIQUE, EMPTY, ALL, MANY
                    [-e Elapsed time (in minutes) for each test iteration]
                    [-w Wait time (in seconds) between each query, default is 0]
                    [-g Result group set size. Used for query operations only]
            eg,
               adv_query_test.sh -c dev309 -t cl5 -l /mnt/test/advquery -n 8 -p 5 -q STRESS
               adv_query_test.sh -c dev309 -t cl5 -l /mnt/test/advquery -n 8

               -c the name of the cluster 
               -n the number of nodes in the cluster (8 or 16)
               -v The storelogfile is used to validate queries. It is the output of the AdvQueryMDStore
                  run. The full pathname of the file must be used. Do not use this option if running
                  store operations concurrently with the queries.

               -o Test options: SS -single stream tests only, AG-aggregate test, STORE runs the
                  AdvQueryMDStore to load honecomb with data, STRESSAGG - runs store and query cycles.

               -p The number of threads per client. The default is 1. Note that the
                  store operation will be run with 1 client only and 20 threads.

               -m metadata schema/pattern file. If not specified uses default pattern file
                  (advquery_md_randgen.txt). These patterns used to randomly generate the
                  metadata data. If running a query test you must specify the same pattern file
                  that was used during the store run.
               -q type of query testing performed. Can select an invidual query type
                  or select STRESS, ADVQUERY or MAX_ROW_LIMIT types which each run a set of queries.
                  MAX_ROW_LIMIT -  runs the max row limit test to test out of memory error.

                  ADVQUERY - runs complex query tests. Each query is tested individually for the
                  time limit specified. Several of the queries results are verified.
                  {COMPLEX OREQUAL COMPLEX5 COMPLEX6 COMPLEX4 EMPTY}

                  STRESS - runs some of the same tests as ADVQUERY but the other queries used
                  return more results. {COMPLEX5 COMPLEX2 OREQUAL COMPLEX6 MANY COMPLEX3}

               -e Elpased time, in minutes, for each iteration and each query. 
                  The default is 120 minutes for the AdvQueryMDStore run. For queries
                  the default is 10 minutes. That is 10 minutes for each separate query run.

               -w The wait time in seconds inbetween queries. The default is 0. This option applies
                  only to queries.

               -g The number of group result set size to use when performing queries. A value of 0
                  will use the default group set size (5000). 

		END
            exit 1
  esac
done

shift $(( $OPTIND - 1 ))

if [ -z $CLUSTER ] || [ -z $CLIENTS ] || [ -z $LOGDIR ]; then
  $0 -h; exit
fi
if [ -z $QUERY_TEST_TYPE ]; then
   QUERY_TEST_TYPE=COMPLEX
fi

if [ -z $WAIT_TIME ]; then
   WAIT_TIME=0
fi

if [ -z $RESULTS_GROUP_SIZE ]; then
    RESULTS_GROUP_SIZE=0
fi

###################################################################
# number of threads per client per test iteration
PROCS=${STREAMS}

if [ -z $STREAMS ]; then
   PROCS="1"
else
   PROCS=${STREAMS}
fi

if [ ${PROCS} -gt 20 ]; then
   echo Too many threads
   $0 -h; exit
fi

#LOGDIR=`cd $LOGDIR; pwd`
for CLIENT in $CLIENTS; do
  echo "Creating ${LOGDIR} directory on ${CLIENT}"
    time=`ssh $CLIENT date +%d%h%H%M%S`
    ssh $CLIENT "mv $LOGDIR ${LOGDIR}.save.${time}"
    ssh $CLIENT "mkdir -p $LOGDIR"
done


# Filesizes to be used in bytes and number of filesizes
FILESIZES="1024 1024000"

# NOTE: 
#       AdvQuery Query tests rely on AdvQueryMDStore having been run first

# Initialize test types
MDSTORE_SS=false
MDSTORE_AGG=false
QUERY_SS=false
QUERY_AGG=false

if [ "$TEST_OPTION" = "" ]; then
   TEST_OPTION="SS"
fi

if [ "$TEST_OPTION" = "SS" ]; then
   QUERY_SS=true
fi
if [ "$TEST_OPTION" = "STORE" ]; then
   MDSTORE_AGG=true
   FILESIZES="1024"
   PROCS="20"
fi

if [ "$TEST_OPTION" = "STRESSAGG" ]; then
   MDSTORE_AGG=true
   QUERY_AGG=true
   FILESIZES="1024"
   PROCS="20"
fi

# Aggregate Tests
if [ "$TEST_OPTION" = "AG" ]; then
   QUERY_AGG=true
fi

# Query types
QUERYTYPES="COMPLEX"
QUERYTYPES_PERF_EXPECTED="COMPLEX UNIQUE EMPTY COMPLEX2 COMPLEX3 COMPLEX4 COMPLEX6 MAX_FETCH_LIMIT MANY OREQUAL"

if [ ! -z $QUERY_TEST_TYPE ]; then
   QUERYTYPES=${QUERY_TEST_TYPE}
fi

if [ "$QUERY_TEST_TYPE" = "ADVQUERY" ]; then
   QUERYTYPES="COMPLEX OREQUAL COMPLEX5 COMPLEX6 COMPLEX4 EMPTY"
   if [ -z $STREAMS ]; then
       PROCS="5"
   fi
fi
if [ "$QUERY_TEST_TYPE" = "MAX_ROW_LIMIT" ]; then
   QUERYTYPES="MAX_FETCH_LIMIT MANY"
fi
if [ "$QUERY_TEST_TYPE" = "STRESS" ]; then
   QUERYTYPES="COMPLEX5 COMPLEX2 OREQUAL COMPLEX6 MANY COMPLEX3"
   if [ -z $STREAMS ]; then
      PROCS="10"
   fi
fi

# Amount of time test should be run for each in minutes

if [ -z $TEST_TIME ]; then
   MDSTORE_TIME=120
   QUERY_TIME=10
else
   MDSTORE_TIME=$TEST_TIME
   QUERY_TIME=$TEST_TIME
fi

NUMSIZES=`echo $FILESIZES | wc -w`
if [ "$QUERY_AGG" == "true" -o "$QUERY_SS" == "true" ]; then
     echo QUERIES TO RUN: ${QUERYTYPES}
fi

# Convert times to seconds:
let MDSTORETIME=$MDSTORE_TIME*60
let QUERYTIME=$QUERY_TIME*60
SS_QUERYTIME=${QUERYTIME}
AGG_QUERYTIME=${QUERYTIME}
QUERY_STATS_INTERVAL=60
SS_MDSTORETIME=$MDSTORETIME
AGG_MDSTORETIME=$MDSTORETIME

STORE_STATS_INTERVAL=300

# Arguments to the script
# beginning and ending iteration numbers
ITER=1
if [ -z ${NUM_ITERS} ]; then
   ITERATIONS=2
else
   let ITERATIONS=$NUM_ITERS+1
fi

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
  chk_value=`echo "$value * 100 " | bc -l | cut -d'.' -f1,1`
  if [ "$4" = "Errors" ]; then
    if [ $chk_value -gt 0 ]; then
      if [ "${NUMCLPROCS}" = "1x1" ]; then
         if [ $ref -eq 0 ]; then
           echo "ERROR: $1, expected $ref, got $value"
           return 1
         fi
      fi
    fi
  fi


  if [ $diff -gt $tolerance ]; then
    echo "WARNING: $1, expected $ref, got $value"
    if [ "${OIDFILE}" = "NOFILE" ]; then
       echo "This may indicate a problem"
    else
       echo "Ignore warning: Query results validation is on"
    fi
    return 0
  else
    echo "INFO: $1, $value (expected $ref)" 
    return 0 
  fi
}

_verify() {
  refFile=$1
  summaryFile=$2
  retval=0
  echo REF-RESULTS FILE: $1
  echo SUMMARY FILE: $2
 
  if [ "$MDSTORE_SS" = "true" -o "$MDSTORE_AGG" = "true" ]; then
   for fsize in $FILESIZES; do
    
    if [ $fsize -gt $STATSTHRESHOLD ]
    then
        # mdstore
        val=`egrep "^AdvQueryMDStore $fsize :" $summaryFile | cut -d ' ' -f 6,6`
        ref=`egrep "^AdvQueryMDStore $fsize :" $refFile | cut -d ' ' -f 6,6`
        if ! _compare "AdvQueryMDStore $fsize (MB/sec)" $ref $val; then
           retval=1
        fi
    else
        # mdstore
        echo "Summary file: ${summaryFile}"
        echo "Seaching for: AdvQueryMDStore $fsize :"   
        val=`egrep "^AdvQueryMDStore $fsize :" $summaryFile | cut -d ' ' -f 4,4`
        ref=`egrep "^AdvQueryMDStore $fsize :" $refFile | cut -d ' ' -f 4,4`
        if ! _compare "AdvQueryMDStore $fsize (ops/sec)" $ref $val; then
           retval=1
        fi
    fi
   done
  fi

  # Query
  query_pass_count=0
  query_fail_count=0
  query_retval=0
  if [[ "$QUERY_SS" = "true" || "$QUERY_AGG" = "true" ]]; then
    for qtype in ${QUERYTYPES}; do
      if [[ "$qtype" = "COMPLEX" || "$qtype" = "EMPTY" ||
           "$qtype" = "COMPLEX2" || "$qtype" = "MANY"  ||
           "$qtype" = "COMPLEX3" || "$qtype" = "COMPLEX4" ||
           "$qtype" = "UNIQUE"   || "$qtype" = "COMPLEX6" ||
           "$qtype" = "COMPLEX5" || "$qtype" = "ALL" ||
           "$qtype" = "MAX_FETCH_LIMIT" ||  "$qtype" = "OREQUAL" ]]; then
         val=`egrep "^AdvQuery $qtype: Average results/sec" $summaryFile | cut -d ' ' -f 7,7`
         ref=`egrep "^AdvQuery $qtype: Average results/sec" $refFile | cut -d ' ' -f 7,7`

        if ! _compare "AdvQuery $qtype Average results/sec" $ref $val; then
            retval=1
            query_retval=1
         fi

         val=`egrep "^AdvQuery $qtype: Total queries:" $summaryFile | cut -d ' ' -f 5,5`
         echo "INFO: AdvQuery $qtype, Total Queries, $val"

         val=`egrep "^AdvQuery $qtype: Total queries validated" $summaryFile | cut -d ' ' -f 6,6`
         echo "INFO: AdvQuery $qtype, Total Queries Validated, $val"

         if [ "${NUMCLPROCS}" = "1x1" ]; then
            val=`egrep "^AdvQuery $qtype: Total errors" $summaryFile | cut -d ' ' -f 5,5`
         else
            val=`egrep "^AdvQuery $qtype: Average errors" $summaryFile | cut -d ' ' -f 7,7`
         fi
         
         ref=`egrep "^AdvQuery $qtype Errors:" $refFile | cut -d ' ' -f 8,8`
         if ! _compare "AdvQuery $qtype Errors" $ref $val "Errors"; then
            retval=1
            query_retval=1
         fi

         if [[ "$NUMCLPROCS" = "1x1" && val -gt 0 ]]; then
             echo ERROR: Test Failed: AdvQuery ${qtype}
             ((query_fail_count=query_fail_count+1))
         else 
             if [ $query_retval -eq 0 ]; then
                    echo PASS: Test Passed: AdvQuery ${qtype}
                    ((query_pass_count=query_pass_count+1))
             fi
             if [ $query_retval -gt 0 ]; then
                 echo ERROR: Test Failed: AdvQuery ${qtype}
                 ((query_fail_count=query_fail_count+1))
             fi
         fi
      fi
  done
  fi
  if [ $retval -ne 0 ]; then
    echo "ERROR: Some Tests failed: " ${query_fail_count}
  else 
    echo "Test passed"
  fi
  exit $retval;
}

########################################################################

. `dirname $0`/adv_query_test_base.sh

_do_test $ITER $ITERATIONS $LOGDIR $@
_verify `dirname $0`/ADV_QUERY_PERF_EXPECTED ${LOGDIR}/1.${CLUSTER}.${NUMCLPROCS}.summary

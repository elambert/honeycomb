#!/bin/bash

# $Id: capi-load.sh 11445 2007-08-29 03:03:42Z dr129993 $
#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# Use is subject to license terms.

# A script to load specified file to the Ofoto view in webdav
#
# Peter Cudhea, borrowed from Shamim Mohamed's webdav-load.sh script

# Some defaults

NB_THREADS=8
SIZES_DATA=1,512000
SIZES_MD=512
EMULATOR=false
TIME_LOOP=300
ITERATIONS=1
TMPDIR=/tmp
LOAD_TEST=./hcload
DEBUG_FLAGS=1
PORT=-1
FAILEARLY=0
RUN_MIX_TEST=0
NAMESPACE=perf_types

function usage()
{
    err=$1
    if [ -n "$err" ]; then
        # Add a newline
        err="
** Error! $err
"
    fi

    cat <<EOF 1>&2
$err
Usage: $0 [-t num_threads] [-i time_per_phase] [-I number_of_iterations] [-T tmp_file_prefix] [-s SIZES_DATA] [-S MD_SIZE] [-o statfile] [-p port] [-d debug_flags] [-f failearly] [-e emulator] [-E alternate_load_test ] [-N namespace] [data-VIP]
Options:
    
    -d : internal debug flags
    -e : Emulator (restrict size of metadata items: true=64, false=512, or max_size)
    -E : alternate load test (e.g. -E "valgrind hcload"
    -f : failearly (true or 1 - stop at end of phase, 2 = stop immediately)
    -i : time_per_phase per iteration
    -I : number of iterations (0 means unlimited)
    -m : non-zero=>run mix test
    -N : metadata name space to use when selecting fields (default is perf_types)
    -s : comma-separated list of data sizes
    -S : maximum number of characters for metadata char, string and binary types
    -t : no. of threads
    -T : filename prefix (TMPDIR)
EOF
    exit 1
}

while getopts "d:e:E:f:i:I:m:p:s:S:t:T:" opt; do
    case "$opt" in
    d) DEBUG_FLAGS="$OPTARG" ;;
    e) EMULATOR="$OPTARG" ;;
    E) LOAD_TEST="$OPTARG" ;;
    f) FAILEARLY="$OPTARG" ;;
    i) TIME_LOOP="$OPTARG" ;;
    I) ITERATIONS="$OPTARG" ;;
    m) RUN_MIX_TEST="$OPTARG" ;;
    p) PORT="$OPTARG" ;;
    s) SIZES_DATA="$OPTARG" ;;
    S) SIZES_MD="$OPTARG" ;;
    t) THREADS="$OPTARG" ;;
    T) TMPDIR="$OPTARG" ;;
    esac
done
shift $[$OPTIND - 1]

if [ $# -lt 1 ]; then
    usage "Not enough arguments."
fi
if [ -z "$LD_LIBRARY_PATH" ] ; then
    usage "LD_LIBRARY_PATH not set"
fi

CLUSTER="$1"

START=`perl -e 'print time;'`

function runtest
{
    # store
    $LOAD_TEST -t $TIME_LOOP -n $NB_THREADS -v $CLUSTER -p $PORT -o store -s $SIZES_DATA -e $EMULATOR -l $TMPDIR -d $DEBUG_FLAGS -f $FAILEARLY -N $NAMESPACE || return

    # retrieve
    $LOAD_TEST  -n $NB_THREADS -v $CLUSTER -p $PORT -o retrieve -e $EMULATOR -l $TMPDIR -d $DEBUG_FLAGS -f $FAILEARLY -N $NAMESPACE || return

    # add metadata
    $LOAD_TEST  -t $TIME_LOOP -n $NB_THREADS -v $CLUSTER -p $PORT -o addmd -s $SIZES_DATA -S $SIZES_MD -e $EMULATOR -l $TMPDIR -d $DEBUG_FLAGS -f $FAILEARLY -N $NAMESPACE || return

    # query
    $LOAD_TEST -t $TIME_LOOP -n $NB_THREADS -v $CLUSTER -p $PORT -o query -e $EMULATOR -l $TMPDIR -d $DEBUG_FLAGS -f $FAILEARLY -N $NAMESPACE || return

    # queryplus
    $LOAD_TEST -t $TIME_LOOP -n $NB_THREADS -v $CLUSTER -p $PORT -o queryplus -e $EMULATOR -l $TMPDIR -d $DEBUG_FLAGS -f $FAILEARLY -N $NAMESPACE || return
}

i=1
t=$ITERATIONS
while [ "$t" -eq 0 -o "$i" -le "$t" ] ; do
    echo "==============================="
    echo "======= Iteration $i =========="
    date
    if [ $RUN_MIX_TEST -eq 0 ] ; then
        runtest
    fi
    if [ $RUN_MIX_TEST -ne 0 ] ; then
        echo $LOAD_TEST -t $TIME_LOOP -n $NB_THREADS -v $CLUSTER -p $PORT -o mix -s $SIZES_DATA -S $SIZES_MD -e $EMULATOR -l $TMPDIR -d $DEBUG_FLAGS -f $FAILEARLY -N $NAMESPACE
        $LOAD_TEST -t $TIME_LOOP -n $NB_THREADS -v $CLUSTER -p $PORT -o mix -s $SIZES_DATA -S $SIZES_MD -e $EMULATOR -l $TMPDIR -d $DEBUG_FLAGS -f $FAILEARLY -N $NAMESPACE
    fi
 

    exitcode=$?
    if [ $exitcode -ne 0 ] ; then
	if [ "$FAILEARLY" -ne 0 ] ; then
	    echo "Test terminating early due to exitcode=$exitcode. Time = "
	    date
	    exit $exitcode
	 fi
	 echo "Iteration $i terminating early due to exitcode=$exitcode."
    fi
    sleep 1
    i=$((i+1))
done
echo "==================================="
echo "Test Complete at "`date`


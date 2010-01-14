#!/bin/bash
#
# $Id: analyze-perf.sh 11559 2007-10-01 20:58:57Z mc210319 $
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

# find and source ENV file
DIR=`dirname $0`
ENV=$DIR/ENV
if [ ! -f $ENV ]; then
    echo "ERROR: can't find ENV file $ENV" 1>&2
    exit 1
fi
. $ENV

sizes=( small medium large xlarge )
tests=( store query-each-repeat retrieve-each-repeat)
verbose=false
interval=0
filename=0
multi=0
while getopts 'vmt:f:' OPTION
do
  case $OPTION in
  v) verbose=true;;
  t) interval="$OPTARG";;
  f) filename="$OPTARG";;
  m) multi=1;;
  ?) printf"Usage: %s: [-v] [-t TIME] [-f FILE] [-m clNN]\n" $(basename $0) >&2
     exit 1
  esac 
done
shift $(($OPTIND - 1))

# Input Checking

if [ "$filename" != 0 ]
then
    if [ "$multi" != 0 ]
    then
        # Multi client, single file
        filenames=""
        for CLIENT in $*; do
            scp -q root@$CLIENT:$LOGDIR/$filename $LOGDIR/$CLIENT.$filename
            filenames="$filenames $LOGDIR/$CLIENT.$filename"
        done
        sort -k 1 -S 30% -T $LOGDIR -n $filenames -o $LOGDIR/combined.$filename
        
        echo "Checking performance for $filename: $*"
        cat $LOGDIR/combined.$filename | java -classpath $CLASSPATH \
            com.sun.honeycomb.test.stress.AnalyzeStress $filename $verbose \
            $interval
        echo "Done checking performance"
    else
        # Single client, single file
        echo "Checking performance for $filename"
        cat $LOGDIR/$filename | java -classpath $CLASSPATH \
            com.sun.honeycomb.test.stress.AnalyzeStress $filename $verbose \
            $interval
        echo "Done checking performance"
    fi
else
    if [ "$multi" != 0 ]
    then
        # Multi client / all files
        for TEST in ${tests[@]}; do
            for SIZE in ${sizes[@]}; do
                filenames=""
                for CLIENT in $*; do
                    scp -q root@$CLIENT:$LOGDIR/$TEST.$SIZE.out \
                        $LOGDIR/$CLIENT.$TEST.$SIZE.out
                    filenames="$filenames $LOGDIR/$CLIENT.$TEST.$SIZE.out"
                done
                sort -k 1 -S 30% -T $LOGDIR -n $filenames -o \
                    $LOGDIR/combined.$TEST.$SIZE.out
                echo "Checking performance for $TEST.$SIZE.out: $*"
                cat $LOGDIR/combined.$TEST.$SIZE.out | java -classpath \
                    $CLASSPATH com.sun.honeycomb.test.stress.AnalyzeStress \
                    $SIZE $verbose $interval
            done
        done
        echo "Done checking performance"
    else
        # Single client / all files
        echo "Checking performance for small, medium, large, and xlarge files"
        for TEST in ${tests[@]}; do
            for SIZE in ${sizes[@]}; do
                cat $LOGDIR/$TEST.$SIZE.out | java -classpath $CLASSPATH \
                    com.sun.honeycomb.test.stress.AnalyzeStress $SIZE $verbose\
                     $interval
            done
        done
        echo "Done checking performance"
    fi
fi        

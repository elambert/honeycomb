#!/bin/sh
#
# $Id: stats_blockstore.sh 10858 2007-05-19 03:03:41Z bberndt $
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
# Calculate performance statistics from load_blockstore output files.
#
# Run in the directory with output files. Arguments:
#
# from-iter, to-iter - iteration numbers of load_blockstore to consider
# op-type - limit to this operation: Store, Retrieve, Query etc.
# runtime - default runtime of a single iteration.
#

if [ $# -eq 0 ]; then
  echo "$0 <from-iter> <to-iter> [op-type] [runtime]"
fi

i=$1
let iter=$2+1
optype=$3
RUNTIME=$4

# If test exited without printing duration (eg was killed), 
# assume that iteration was this long (in minutes).
if [ "$RUNTIME" == "" ]; then
    RUNTIME=23 
fi

while [ $i -lt $iter ]; do

  # load_blockstore output files, ordered by iter and time
  for file in `ls -rt $i.* |grep -v singlestream |grep "$optype"`; do
    tstamp=`ls -l $file |awk '{print $6, $7, $8}'`
    ops=`grep OK $file |wc -l |sed 's/^ *//'`
    msec=`grep runtime $file |awk '{print $9}' |sed 's/ms//'`
    if [ "$msec" == "" ]; then
      echo "Unknown runtime for $file, assuming $RUNTIME minutes"
      let msec=$RUNTIME*60*1000
    fi
    let sec=$msec/1000
    let opsec=$ops/$sec

    # calculate MB/sec throughput based on filesize
    size=`echo $file |awk 'BEGIN { FS="." } ; {print $5}'`
    case "$size" in
    [0-9]*k)
      kbytes=`echo $size |sed 's/k//'`
      msg="[$tstamp] $file: ops/sec=$opsec"
      ;;
    [0-9]*m)
      mbytes=`echo $size |sed 's/m//'`
      let mbsec=$mbytes*$ops/$sec
      msg="[$tstamp] $file: ops/sec=$opsec, MB/sec=$mbsec"
      ;;
    [0-9]*g)
      gbytes=`echo $size |sed 's/g//'`
      let mbsec=$gbytes*1024*$ops/$sec
      msg="[$tstamp] $file: ops/sec=$opsec, MB/sec=$mbsec"
      ;;
    *)
      msg="[$tstamp] $file: ops/sec=$opsec, MB/sec=? (size $size)"
      ;;
    esac

    # report on failures
    fops=`grep FAIL $file |grep -v SUM |wc -l |sed 's/^ *//'`
    if [ "$fops" != "0" ]; then
      msg="$msg [FAIL=$fops]"
    fi
    echo $msg
  done

  # tina's singlestream output files, ordered by iter and time
  for file in `ls -rt $i.* |grep singlestream |grep "$optype"`; do
    tstamp=`ls -l $file |awk '{print $6, $7, $8}'`
    echo [$tstamp] $file: Statistics below
    grep Rounded $file
  done

  let i=$i+1
done

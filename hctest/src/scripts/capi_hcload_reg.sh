#!/bin/bash
#
# $Id: capi_hcload_reg.sh 11602 2007-10-16 21:18:27Z wr152514 $
#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# Use is subject to license terms.
#

# run hcload for regression test
# needs perf schema

if [ $# != 2 ]; then
    echo "Usage: $0 <trunk> <datavip>"
    exit 1
fi

# 28800 sec = 8 hours
RUN_SEC=28800
#RUN_SEC=600

TRUNK=$1
CLUSTER=$2

echo TRUNK=$TRUNK
echo CLUSTER=$CLUSTER
date

MIXLOG=/tmp/hcload_mix.out.$$
MIX2LOG=/tmp/hcload_mix2.out.$$
##############################################

#
#  build lib & app for local architecture
#
#echo BUILDING $TRUNK/client_c
#cd $TRUNK/client_c	|| exit 1
#ant			|| exit 1
#
#echo BUILDING $TRUNK/hctest/src/native/build
#cd $TRUNK/hctest/src/native/build	|| exit 1
#make					|| exit 1


#
#  Determine platform we are running on
#
OS=`uname -s`
MACH=`uname -p`
 
if [ ".$OS" = ".SunOS" ] ; then
  REL=`uname -r`
  if [ ".$REL" = ".5.9" ] ; then
    REL=9
  else
    REL=10
  fi
  if [ ".$MACH" = ".sparc" ] ; then
    PLATFORM=sol_${REL}_sparc
  else
    PLATFORM=sol_${REL}_x86
  fi
else
  if [ ".$OS" = ".Linux" ] ; then
    PLATFORM="$OS"
  else
    PLATFORM=Win32
  fi
fi

#
#  run program
#
export LD_LIBRARY_PATH=$TRUNK/build/client_c/build_$PLATFORM/honeycomb/dist/
EXE=$TRUNK/build/hctest/dist/bin/build_$PLATFORM/hcload
echo "RUNNING $EXE"
echo "STARTING hcload -o mix > $MIXLOG"
rm -rf /tmp/e
mkdir /tmp/e
$EXE -v $CLUSTER -l /tmp/e -n 8 -o mix -s 1024,2048  \
                               -t $RUN_SEC -f true > $MIXLOG 2>&1 &
PID1=$!
echo "STARTING hcload -o mix2 > $MIX2LOG"
$EXE -v $CLUSTER -l /tmp/e -n 8 -o mix2 -s 1024,2048  \
                               -t $RUN_SEC -f true > $MIX2LOG 2>&1 &
PID2=$!

FAIL=0
echo "WAITING for hcload -o mix > $MIXLOG"
wait $PID1
if [ $? != 0 ]; then
    echo C API hcload -o mix test: FAIL
    echo =====================================================================
    tail -100 $MIXLOG
    echo =====================================================================
    FAIL=1
fi
echo "WAITING for hcload -o mix2 > $MIX2LOG"
wait $PID2
if [ $? != 0 ]; then
    echo C API hcload -o mix2 test: FAIL
    echo =====================================================================
    tail -100 $MIX2LOG
    echo =====================================================================
    FAIL=1
fi

if [ "$FAIL" == "1" ] ; then
    exit 1
else
    /bin/rm $MIXLOG $MIX2LOG
fi

date
echo C API hcload tests: PASS


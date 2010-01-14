#!/bin/bash
#
# $Id: capi_hctestharness_reg.sh 11488 2007-09-07 23:12:36Z wr152514 $
#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# Use is subject to license terms.
#

# run hctestharness for regression
# needs qa, utf8 schemas

if [ $# != 2 ]; then
    echo "Usage: $0 <trunk> <datavip>"
    exit 1
fi

TRUNK=$1
CLUSTER=$2

echo TRUNK=$TRUNK
echo CLUSTER=$CLUSTER

test_result() {
    if [ $1 != 0 ] ; then
        echo C API hctestharness: FAIL
        exit 1
    fi
}
##############################################

#
#  build lib & app for local architecture
#
#echo BUILDING $TRUNK/client_c
#cd $TRUNK/client_c	|| echo C API hctestharness test: FAIL ; exit 1
#ant			|| echo C API hctestharness test: FAIL ; exit 1
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
echo "RUNNING $TRUNK/build/hctest/dist/bin/build_$PLATFORM/hctestharness"
cd $TRUNK/build/hctest/dist/bin/build_$PLATFORM
test_result $?
export LD_LIBRARY_PATH=$TRUNK/build/client_c/build_$PLATFORM/honeycomb/dist/
./hctestharness -x vip=${CLUSTER}:failearly=true:max_file=xlarge_file
test_result $?

echo C API hctestharness test: PASS

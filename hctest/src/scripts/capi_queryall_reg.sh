#!/bin/bash
#
# $Id$
#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# Use is subject to license terms.
#

# run queryall for regression

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
        echo C API queryall test: FAIL
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
echo "RUNNING $TRUNK/build/hctest/dist/bin/build_$PLATFORM/queryall"
cd $TRUNK/build/hctest/dist/bin/build_$PLATFORM 
test_result $?
export LD_LIBRARY_PATH=$TRUNK/build/client_c/build_$PLATFORM/honeycomb/dist/
NOIDS=`./queryall ${CLUSTER} | wc -l`
test_result $?
echo queryall: $NOIDS

DATAVIPS=`ssh -q -o StrictHostKeyChecking=no -i $TRUNK/hctest/etc/ssh/id_dsa -l admin $CLUSTER hiveadm | grep dataVIP | awk '{print $9}'`

#echo $DATAVIPS

TOTAL=0
for i in $DATAVIPS ; do
  ssh -q -o StrictHostKeyChecking=no -i $TRUNK/hctest/etc/ssh/id_dsa -l root \
        $i 'echo "select count (*) from T_SYSTEM;" > /tmp/hadb_fullness'
  test_result $?
  HNOIDS=`ssh -q -o StrictHostKeyChecking=no -i $TRUNK/hctest/etc/ssh/id_dsa \
        -l root $i \
        /config/hadb_install/4/bin/clusql localhost:15005 system+superduper \
                -command=/tmp/hadb_fullness |tail -2 |tr -d "[:space:]"`
  test_result $?
  #echo $HNOIDS
  TOTAL=`expr $TOTAL + $HNOIDS`
done
#echo total $TOTAL

if [ ! $NOIDS -eq $TOTAL ] ; then
    echo queryall=$NOIDS HADB=$TOTAL
    echo C API queryall test: FAIL
    exit 1
fi

echo C API queryall test: PASS

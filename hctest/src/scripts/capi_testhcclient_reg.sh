#!/bin/bash
#
# $Id: capi_testhcclient_reg.sh 11488 2007-09-07 23:12:36Z wr152514 $
#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# Use is subject to license terms.
#

# run testhcclient for regression
# needs qa schema

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
        echo C API testhcclient: FAIL
        exit 1
    fi
}

##############################################

#
#  build for local architecture
#
#cd $TRUNK/client_c	|| exit 1
#ant			|| exit 1

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
cd $TRUNK/build/client_c/build_$PLATFORM/test/dist
test_result $?
touch reallybigdata
export LD_LIBRARY_PATH=../../honeycomb/dist/
./testhcclient $CLUSTER
test_result $?

echo C API testhcclient: PASS

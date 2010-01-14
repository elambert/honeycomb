#!/bin/bash
#
# $Id: sdk_regression.sh 11488 2007-09-07 23:12:36Z wr152514 $
#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# Use is subject to license terms.
#

# compile and run SDK C/Java apps
# assumes being run on Solaris
# assumes emulator is running locally and loaded w/ mp3demo schema
# runtime: ~1 minute

if [ $# != 1 ]; then
    echo "Usage: $0 <trunk>"
    exit 1
fi

TRUNK=$1

EXIT=0

test_result() {
    if [ $1 != 0 ] ; then
        echo $2: FAIL
        EXIT=1
    else
        echo $2: PASS
    fi
}

####################

echo BUILDING C APPS
cd $TRUNK/build/sdk/dist/c/examples
test_result $? cd_c_examples
make
test_result $? make_c_examples

echo BUILDING JAVA APPS
cd $TRUNK/build/sdk/dist/java/examples
test_result $? cd_java_examples
./master_build.sh
test_result $? make_java_examples

echo RUNNING C APPS
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
echo LD_LIBRARY_PATH=$TRUNK/build/sdk/dist/c/$PLATFORM/lib
export LD_LIBRARY_PATH=$TRUNK/build/sdk/dist/c/$PLATFORM/lib

cd $TRUNK/build/sdk/test
test_result $? cd_test_dir

chmod +x * Scripts/*/*

./Master_C_Solaris.sh
test_result $? C_TESTS

echo RUNNING JAVA APPS
./Master_Java_UNIX.sh
test_result $? JAVA_TESTS

if [ "$EXIT" -ne 0 ] ; then
    echo SDK: FAIL
else
    echo SDK: PASS
fi

exit $EXIT

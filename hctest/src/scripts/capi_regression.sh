#!/bin/bash
#
# $Id: capi_regression.sh 11488 2007-09-07 23:12:36Z wr152514 $
#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# Use is subject to license terms.
#

# compile and run c api regression suite
# needs qa, perf, utf8 schemas

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
        echo C API $2: FAIL
        exit 1
    else
        echo C API $2: PASS
    fi
}

##############################################

#
#  build lib & apps for local architecture
#
echo ==================================================-
echo "==== COMPILING"
echo ==================================================-
$TRUNK/build/hctest/dist/bin/capi_compile_reg.sh $TRUNK
if [ $? != 0 ] ; then
    echo C API compile: FAIL
    exit 1
else
    echo C API compile: PASS
fi

#
#  run individual tests
#
echo ==================================================-
echo "==== RUNNING capi_testhcclient_reg.sh"
echo ==================================================-
$TRUNK/build/hctest/dist/bin/capi_testhcclient_reg.sh $TRUNK $CLUSTER
test_result $? testhcclient

echo ==================================================-
echo "==== RUNNING capi_hctestharness_reg.sh"
echo ==================================================-
$TRUNK/build/hctest/dist/bin/capi_hctestharness_reg.sh $TRUNK $CLUSTER
test_result $? hctestharness

echo ==================================================-
echo "==== RUNNING capi_hcload_reg.sh"
echo ==================================================-
$TRUNK/build/hctest/dist/bin/capi_hcload_reg.sh $TRUNK $CLUSTER
test_result $? hcload

echo ==================================================-
echo "==== RUNNING capi_queryall_reg.sh"
echo ==================================================-
$TRUNK/build/hctest/dist/bin/capi_queryall_reg.sh $TRUNK $CLUSTER
test_result $? queryall

echo "C API Regression (Compile and Run): PASS"

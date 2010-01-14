#!/bin/bash
#
# $Id: capi_compile_reg.sh 11336 2007-08-08 00:29:13Z wr152514 $
#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# Use is subject to license terms.
#

# compile client and regression test programs for c api

if [ $# != 1 ]; then
    echo "Usage: $0 <trunk>"
    exit 1
fi

TRUNK=$1

test_result() {
    if [ $1 != 0 ] ; then
        echo C API compile: FAIL
        exit 1
    fi
}

####################

echo "BUILDING $TRUNK/client_c"
cd $TRUNK/client_c
test_result $?
ant
test_result $?

echo "BUILDING $TRUNK/hctest/src/native/build"
cd $TRUNK/hctest/src/native/build
test_result $?
make
test_result $?

echo C API Compile: PASS

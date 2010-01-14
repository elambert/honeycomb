#!/bin/sh

#
# $Id: runAll.sh 10854 2007-05-19 02:52:12Z bberndt $
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

DIR=`cd \`dirname $0\`; pwd`

NUMPASSED=0
NUMFAILED=0
FAILEDNAMES=""
PASSEDNAMES=""

LOGDIR=$DIR/logs
LOGARCHIVE=$DIR/log-archive/testrun-`date '+%b%d%Y_%H%M%S'`
mkdir -p $LOGARCHIVE
KNOWN_FAILURES_BASE="scenarii/KNOWN_FAILURES"
KNOWN_FAILURES=$DIR/$KNOWN_FAILURES_BASE

CLEANAFTERTEST=1
ABORTONFAILURE=0
VERBOSE=""

# parse args
while [ -n "${1}" ]; do
    if [ "${1}" = "--noclean" ]; then
        echo "--noclean specified...won't clean in between tests"
        CLEANAFTERTEST=0
    elif [ "${1}" = "--abortonfailure" ]; then
        echo "--abortonfailure specified...will exit on error"
        ABORTONFAILURE=1
    elif [ "${1}" = "--verbose" ]; then
        echo "--verbose specified...will pass --verbose option to run.sh"
        VERBOSE="--verbose"
    else 
        echo "usage: runAll [--noclean] [--abortonfailure] [--verbose]"
        exit 1
    fi
    shift
done

# clean before starting as well, unless no clean was specified
if [ $CLEANAFTERTEST -eq 1 ]; then
    $DIR/clean.sh -n
fi

for test in $DIR/scenarii/*.*
do
    testname=`basename $test`

    echo
    echo "***** Executing [$testname] *****"
    echo

    $DIR/run.sh $VERBOSE $test
    if [ $? -ne 0 ]
    then
        # see if this is new or known failure
        grep $testname $KNOWN_FAILURES > /dev/null 2>&1
        if [ $? -eq 0 ]; then
            test_status="(known failure)"
        else 
            test_status="(-----> NEW FAILURE <------)"
        fi

        echo "Test [$testname] failed  $test_status"

        if [ $ABORTONFAILURE -eq 1 ]; then
            echo "aborting testrun due to failure"
            exit 1
        fi

        NUMFAILED=`expr $NUMFAILED + 1`
        FAILEDNAMES=" $FAILEDNAMES \n $testname  $test_status"
    else
        # see if this is new or known failure
        grep $testname $KNOWN_FAILURES > /dev/null 2>&1
        if [ $? -eq 0 ]; then
            test_status="(-----> NEW PASS <------)"
        else 
            test_status="(known pass)"
        fi

        echo "Test [$testname] passed  $test_status"

        PASSEDNAMES=" $PASSEDNAMES \n $testname  $test_status"
        NUMPASSED=`expr $NUMPASSED + 1`
    fi

    cp -r $LOGDIR $LOGARCHIVE/$testname
    echo "see logs in $LOGARCHIVE/$testname/ for more details"

    if [ $CLEANAFTERTEST -eq 1 ]; then
        $DIR/clean.sh -n
    fi
done

echo "\nRESULTS SUMMARY: $NUMPASSED tests passed; $NUMFAILED tests failed"
echo "(update expected failures by editing $KNOWN_FAILURES_BASE)"
echo
echo "Names of tests that passed: $PASSEDNAMES"
echo
if [ $NUMFAILED -eq 0 ]; then
    echo "All tests passed"
    exit 0
else 
    echo "Names of tests that failed:  $FAILEDNAMES"
    echo
    echo "Logs for failed tests are in $LOGARCHIVE/\$testname/"
    exit 1
fi

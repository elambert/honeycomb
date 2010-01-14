#!/bin/bash

#
# $Id: run_tests.sh 10240 2007-02-05 21:58:30Z dm155201 $
#
# Either runs unique test(s) specified on command line,
# or all tests in test.list file + all JUnit tests.
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

UTESTSJAR=$DIR/honeycomb-utests.jar

TESTDIR=`cd $DIR/../../test/dist; pwd`
TESTBIN=$TESTDIR/bin/runtestquiet

BUILDDIR=`cd $DIR/../..; pwd`
SHAREDIR=$BUILDDIR/unit_tests/dist/share
SERVERJAR=$BUILDDIR/server/dist/lib/honeycomb-server.jar
COMMONJAR=$BUILDDIR/common/dist/lib/honeycomb-common.jar
JUGJAR=$BUILDDIR/external/dist/lib/jug.jar
JETTYJAR=$BUILDDIR/external/dist/lib/jetty-4.2.20.jar
JUNITJAR=$BUILDDIR/external/dist/lib/junit-4.1.jar

CLASSPATH=$SERVERJAR:$COMMONJAR:$JUGJAR:$UTESTSJAR:$JETTYJAR:$JUNITJAR
export CLASSPATH

EXTRA_JVM_ARGS="-Dhoneycomb.config.dir=. -Djava.library.path=$BUILDDIR/md_caches/dist:$BUILDDIR/server/dist/lib -Dmd.cache.path=$BUILDDIR/md_caches/dist -Duid.lib.path=$BUILDDIR/external/dist/lib -Dshare.path=$SHAREDIR -Doa.upgrade.basedir=$BUILDDIR/unit_tests/dist"
export EXTRA_JVM_ARGS

LD_LIBRARY_PATH=$BUILDDIR/md_caches/dist:$BUILDDIR/server/dist/lib
export LD_LIBRARY_PATH

NUM_NODES=$1
RESERVED_CAPACITY=$2

# DEFAULTS
if [ "$NUM_NODES" == "" ]
then
    NUM_NODES=16
fi

if [ "$RESERVED_CAPACITY" == "" ]
then
    RESERVED_CAPACITY=85
fi

EXTRA_JVM_ARGS="$EXTRA_JVM_ARGS -Dnodes=$NUM_NODES -Ddisk.reserved.capacity=$RESERVED_CAPACITY"

echo "Running Layout Simulation Tests"
$TESTBIN com.sun.honeycomb.layout.LayoutSimulation


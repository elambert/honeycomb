#!/bin/bash
#
# $Id: store.sh 12002 2008-05-13 22:30:56Z ds158322 $
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

# find and source ENV file
DIR=`dirname $0`
ENV=$DIR/ENV
if [ ! -f $ENV ]; then
    echo "ERROR: can't find ENV file $ENV" 1>&2
    exit 1
fi
. $ENV

export HONEYCOMB_DESIRED_NODE=$STORE_NODE

# java StoreStress <dataVIP> <num_threads> <min_size_bytes> <max_size_bytes> 
#                  <runtime_seconds> <num_ops> <extended_metadata>

java -classpath $CLASSPATH com.sun.honeycomb.test.stress.StoreStress \
     $DATAVIP $NUMTHREADS 0 $[1*1024*1024] $PATTERN $EXTENDED_METADATA -1 -1 \
     $STORE_CHECK_INDEXED $SOCKET_TIMEOUT_SEC \
     >>$LOGDIR/store.small.out 2>>$LOGDIR/store.small.err &

java -classpath $CLASSPATH com.sun.honeycomb.test.stress.StoreStress \
     $DATAVIP $NUMTHREADS $[1024*1024] $[10*1024*1024] $PATTERN $EXTENDED_METADATA -1 -1 \
     $STORE_CHECK_INDEXED $SOCKET_TIMEOUT_SEC \
     >>$LOGDIR/store.medium.out 2>>$LOGDIR/store.medium.err &

java -classpath $CLASSPATH com.sun.honeycomb.test.stress.StoreStress \
     $DATAVIP $NUMTHREADS $[10*1024*1024] $[100*1024*1024] $PATTERN $EXTENDED_METADATA -1 -1 \
     $STORE_CHECK_INDEXED $SOCKET_TIMEOUT_SEC \
     >>$LOGDIR/store.large.out 2>>$LOGDIR/store.large.err &

java -classpath $CLASSPATH com.sun.honeycomb.test.stress.StoreStress \
     $DATAVIP $NUMTHREADS $[100*1024*1024] $[10*1024*1024*1024] $PATTERN $EXTENDED_METADATA -1 -1 \
     $STORE_CHECK_INDEXED $SOCKET_TIMEOUT_SEC \
     >>$LOGDIR/store.xlarge.out 2>>$LOGDIR/store.xlarge.err &


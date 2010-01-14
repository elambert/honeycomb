#!/bin/bash
#
# $Id: start-query.sh 10443 2007-03-14 20:20:51Z sarahg $
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

# query that will wait for the latest contents in the file and
# query on that and block for new lines in file
if [ -n "$RUNONCETESTS" -a $RUNONCETESTS -ne 0 ]; then
    $DIR/query-each.sh $DATAVIP $NUMTHREADS $LOGDIR/store.small.out >> $LOGDIR/query-each.small.out 2>> $LOGDIR/query-each.small.err &
    $DIR/query-each.sh $DATAVIP $NUMTHREADS $LOGDIR/store.medium.out >> $LOGDIR/query-each.medium.out 2>> $LOGDIR/query-each.medium.err &
    $DIR/query-each.sh $DATAVIP $NUMTHREADS $LOGDIR/store.large.out >> $LOGDIR/query-each.large.out 2>> $LOGDIR/query-each.large.err &
    $DIR/query-each.sh $DATAVIP $NUMTHREADS $LOGDIR/store.xlarge.out >> $LOGDIR/query-each.xlarge.out 2>> $LOGDIR/query-each.xlarge.err &
else
    echo "Skipping RUNONCETESTS query tests"
fi

# query that will loop once it gets to the end of the file
$DIR/query-each-repeat.sh $DATAVIP $NUMTHREADS $LOGDIR/store.small.out >> $LOGDIR/query-each-repeat.small.out 2>> $LOGDIR/query-each-repeat.small.err &
$DIR/query-each-repeat.sh $DATAVIP $NUMTHREADS $LOGDIR/store.medium.out >> $LOGDIR/query-each-repeat.medium.out 2>> $LOGDIR/query-each-repeat.medium.err &
$DIR/query-each-repeat.sh $DATAVIP $NUMTHREADS $LOGDIR/store.large.out >> $LOGDIR/query-each-repeat.large.out 2>> $LOGDIR/query-each-repeat.large.err &
$DIR/query-each-repeat.sh $DATAVIP $NUMTHREADS $LOGDIR/store.xlarge.out >> $LOGDIR/query-each-repeat.xlarge.out 2>> $LOGDIR/query-each-repeat.xlarge.err &

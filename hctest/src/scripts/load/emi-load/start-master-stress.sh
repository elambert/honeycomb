#!/bin/bash
#
# $Id: start-master-stress.sh 11258 2007-07-21 01:47:02Z sarahg $
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

# see the README for information on how to launch this test

# find and source ENV file
DIR=`dirname $0`
ENV=$DIR/ENV
if [ ! -f $ENV ]; then
    echo "ERROR: can't find ENV file $ENV" 1>&2
    exit 1
fi
. $ENV

echo "`date`: Starting test using dataVIP $DATAVIP and logdir $LOGDIR"

# Currently launches 4 programs to store small, medium
# and extra large files.  NUMTHREADS per program is specified
# in ENV
if [ -n "$STARTSTORES" -a $STARTSTORES -ne 0 ]; then
    $DIR/store.sh
else
    echo "Skipping launch of stores"
fi

# Start the queries and retrieves.  Each of these launches
# 2 programs for each store activity (currently 4 file size
# brackets).  One program always waits for the latest activity
# to be added to the file and the other cycles through the full
# history of activity in a loop.
# Currently, the program that waits for the latest activity is
# only run if the RUNONCETESTS var is non-zero

if [ -n "$STARTQUERIES" -a $STARTQUERIES -ne 0 ]; then
    $DIR/start-query.sh
else
    echo "Skipping launch of queries"
fi


if [ -n "$STARTRETRIEVES" -a $STARTRETRIEVES -ne 0 ]; then
    $DIR/start-retrieve.sh
else
    echo "Skipping launch of retrieves"
fi

# note that deletes don't interfere with other objects
# stored in other threads.  The delete thread first does
# the store and then the delete.
if [ -n "$STARTDELETES" -a $STARTDELETES -ne 0 ]; then
    $DIR/delete.sh
else
    echo "Skipping launch of deletes"
fi

#!/bin/sh

#############################################
# QB Database sample data
#
# Run this script on hc-web to populate qb db
# with sample data that will get you started
#
# Author: Daria Mehra
# Revision: $Id: qb_populate.sh 3371 2005-01-25 23:40:17Z dm155201 $
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



##############################################

ITEM=$1
ID=$2

DB=qb
HOST=hc-web
USER=nobody
PASSWORD=

if test x$QB_DATABASE != x
then
  DB=$QB_DATABASE
fi

if test x$QB_HOST != x
then
  HOST=$QB_HOST
fi

if test x$QB_USER != x
then
  USER=$QB_USER
fi

if test x$QB_PASSWORD != x
then
  PASSWORD=$QB_PASSWORD
fi

MYSQL=
if test x$PASSWORD = x
then
  MYSQL="mysql -t -h $HOST -u $USER -D $DB"
else
  MYSQL="mysql -t -h $HOST -u $USER -D $DB -p$PASSWORD"
fi

function run_main {

  if [ "$ITEM" == "run" ]; then
    get_run
  fi

  if [ "$ITEM" == "results" ]; then
    get_results_for_run
  fi
}


function get_run {
$MYSQL <<EOF
  select 
    run.id, run.start_time, run.end_time, run.exitcode,
    tester.name, bed.name, run.logtag, run.logs_url,
    run.command, run.env, run.comments
  from 
    run, bed, tester
  where
    run.bed=bed.id and 
    run.tester=tester.id and
    run.id=$ID
  ;
EOF
}

function get_results_for_run() {
$MYSQL <<EOF
  select
    result.id, testproc.name, testcase.parameters, result.run,
    result.start_time, result.end_time, result.status,
    build.name, tester.name, result.logs_url, result.notes
  from
    result, testcase, testproc, build, tester
  where
    result.testcase=testcase.id and
    testcase.testproc=testproc.id and
    result.build=build.id and
    result.submitter=tester.id and
    result.run=$ID
EOF
}

run_main
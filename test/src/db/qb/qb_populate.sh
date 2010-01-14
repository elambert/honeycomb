#!/bin/sh

#############################################
# QB Database sample data
#
# Run this script on hc-web to populate qb db
# with sample data that will get you started
#
# Author: Daria Mehra
# Revision: $Id: qb_populate.sh 10856 2007-05-19 02:58:52Z bberndt $
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
  MYSQL="mysql -h $HOST -u $USER -D $DB"
else
  MYSQL="mysql -h $HOST -u $USER -D $DB -p$PASSWORD"
fi

echo
echo "WARNING!!!"
echo "  THIS SCRIPT WILL INSERT SAMPLE DATA INTO QB DATABASE."
echo "  THE DATABASE SHOULD BE EMPTY, OTHERWISE INSERTION MAY FAIL."
echo "  THE DATABASE WILL BE ACCESSED USING THE FOLLOWING COMMAND."
echo
echo "    $MYSQL"
echo
echo "  PRESS CTRL-C NOW TO CANCEL!  Or press return to continue."
read dummy

function verify() {
  EXITCODE=$1
  if [ "$EXITCODE" -eq "0" ]
  then
    echo " ok"
  else
#    exit $EXITCODE
    echo " ignoring error"
  fi
}

function populate_table() {
  TABLE=$1
  echo -n Populating table $TABLE...
}

############ USERS ##############

populate_table tester
$MYSQL <<EOF
  insert into $TABLE (name, alias, email)
  values ("dm155201", "dmehra", "daria.mehra@sun.com");
EOF
$MYSQL <<EOF
  insert into $TABLE (name, alias, email)
  values ("dmehra", "dmehra", "daria.mehra@sun.com");
EOF
verify $?

############ TEST RESOURCES ##############

populate_table resource_keyword
for NAME in cluster client num_nodes data_vip admin_vip tsv_ip client_ip os os_version hardware
do
$MYSQL <<EOF
  insert into $TABLE (name) values ("$NAME");
EOF
done
verify $?

populate_table resource
for ((i=301; i<=315; i++))
do
$MYSQL <<EOF
  insert into $TABLE (type, name, description)
  select rk.id, "dev$i", "Honeycomb cluster"
  from resource_keyword rk where rk.name='cluster';  
EOF
done
verify $?

populate_table resource
for ((i=1; i<=7; i++))
do
$MYSQL <<EOF
  insert into $TABLE (type, name, description)
  select rk.id, "cp$i", "Client pool"
  from resource_keyword rk where rk.name='client';
EOF
done
verify $?

populate_table resource
$MYSQL <<EOF
  insert into $TABLE (type, name, description)
  select rk.id, "hc-lab", "Test driver box where Flamebox runs and QB CLI is being tested"
  from resource_keyword rk where rk.name='client';
EOF
verify $?

populate_table resource_property
$MYSQL <<EOF
  insert into $TABLE (resource, name, value)
  select r.id, rk.id, 3
  from resource r, resource_keyword rk
  where r.name='dev306' and r.version=1 and rk.name='num_nodes';

  insert into $TABLE (resource, name, value)
  select r.id, rk.id, "dev306-1"
  from resource r, resource_keyword rk
  where r.name='dev306' and r.version=1 and rk.name='data_vip';

  insert into $TABLE (resource, name, value)
  select r.id, rk.id, "dev306-admin"
  from resource r, resource_keyword rk
  where r.name='dev306' and r.version=1 and rk.name='admin_vip';

  insert into $TABLE (resource, name, value)
  select r.id, rk.id, "rigs"
  from resource r, resource_keyword rk
  where r.name='dev306' and r.version=1 and rk.name='hardware';

  insert into $TABLE (resource, name, value)
  select r.id, rk.id, "linux"
  from resource r, resource_keyword rk
  where r.name='hc-lab' and r.version=1 and rk.name='os';

  insert into $TABLE (resource, name, value)
  select r.id, rk.id, "Gentoo Base System version 1.4.16"
  from resource r, resource_keyword rk
  where r.name='hc-lab' and r.version=1 and rk.name='os_version';
EOF
verify $?

############ TEST BEDS ##############

populate_table bed
$MYSQL <<EOF
  insert into $TABLE (name, owner, description)
  select "qb-testing", u.id, "Daria's testbed for QB dev/test"
  from tester u where u.name='dm155201';
EOF
verify $?

populate_table bed_resource
$MYSQL <<EOF
  insert into $TABLE (bed, resource)
  select b.id, r.id from bed b, resource r
  where b.name='qb-testing' and r.name='hc-lab';

  insert into $TABLE (bed, resource)
  select b.id, r.id from bed b, resource r
  where b.name='qb-testing' and r.name='dev306';
EOF
verify $?

############ BUILDS ############

populate_table build
$MYSQL <<EOF
  insert into $TABLE (name, timestamp, branch, revnum, revtime, description)
  values ("charter-final", "04-12-31 23:59:59", "charter", "1234", "04-12-10 15:03:24", "Final version of the charter software - qualified by QA as such");

  insert into $TABLE (name, timestamp, branch, revnum, revtime, description)
  values ("honeycomb-bin.3247-.tar.gz", "05-01-15 03:04:02", "trunk", "3247", "05-01-14 09:36:04", "Nightly build");
EOF
verify $?

############ TEST CASES ##############

populate_table testproc
$MYSQL <<EOF
  insert into $TABLE (name, objective, setup, steps, results)
  values ("GenericTest", "To determine whether something works correctly",
          "Set this thing up as directed in the user guide",
          "Run through all common use cases", "Expect success");

  insert into $TABLE (name, objective, link)
  values ("AlwaysPass", "To generate a PASS result", "/bin/true");

  insert into $TABLE (name, objective, link)
  values ("AlwaysFail", "To generate a FAIL result", "/bin/false");

  insert into $TABLE (name, objective, link, author)
  select "ComplexTest", "To do something really complex", 
  "docs/Test/Plans/LifeIsHard.txt", u.id
  from tester u where u.name='dm155201';
EOF
verify $?

populate_table testcase
$MYSQL <<EOF
  insert into $TABLE (testproc, parameters, comments)
  select tp.id, "cluster=large", "This test will run a long time!"
  from testproc tp where tp.name="ComplexTest";

  insert into $TABLE (testproc, parameters, comments)
  select tp.id, "cluster=small", "This test is tough but short"
  from testproc tp where tp.name="ComplexTest";

  insert into $TABLE (testproc, parameters, comments)
  select tp.id, "numfail=0 retries=20", "Repeat each use case 20 times, do not introduce failures."
  from testproc tp where tp.name="GenericTest";

  insert into $TABLE (testproc, parameters, comments)
  select tp.id, "numfail=1 retries=20", "Repeat each use case 20 times, introduce 1 failure."
  from testproc tp where tp.name="GenericTest";

  insert into $TABLE (testproc, parameters, comments)
  select tp.id, "numfail=0 retries=1", "The simplest useful scenario"
  from testproc tp where tp.name="GenericTest";

  insert into $TABLE (testproc, parameters, comments)
  select tp.id, "numfail=0 retries=0", "No-op"
  from testproc tp where tp.name="GenericTest";

  insert into $TABLE (testproc, parameters, comments)
  select tp.id, "", "This testcase has no parameters"
  from testproc tp where tp.name="AlwaysPass";

  insert into $TABLE (testproc)
  select tp.id
  from testproc tp where tp.name="AlwaysFail";
EOF
verify $?

############ TEST SUITES ##############

populate_table suite
$MYSQL <<EOF
  insert into $TABLE (name, description)
  values ("UberAlles", "The suite to rule them all!");

  insert into $TABLE (name, description)
  values ("Smoketest", "Short basic goodness tests");

  insert into $TABLE (name, description)
  values ("VeryAdvanced", "Complex tests go here");

  insert into $TABLE (name, description)
  values ("Failures", "Failure scenarios");  
EOF
verify $?

populate_table suite_hierarchy
$MYSQL <<EOF
  insert into $TABLE (suite, parent)
  select sc.id, sp.id from suite sc, suite sp
  where sc.name<>'UberAlles' and sp.name='UberAlles';

  insert into $TABLE (suite, parent)
  select sc.id, sp.id from suite sc, suite sp
  where sc.name='Smoketest' and sp.name='VeryAdvanced';
EOF
# Note: UberAlles will be parent of every other suite
verify $?

populate_table suite_testcase
$MYSQL <<EOF
  insert into $TABLE (suite, testcase)
  select s.id, t.id from suite s, testcase t, testproc p
  where p.id=t.testproc and
  s.name='Smoketest' and p.name='AlwaysPass' and t.parameters='';

  insert into $TABLE (suite, testcase)
  select s.id, t.id from suite s, testcase t, testproc p
  where p.id=t.testproc and
  s.name='Smoketest' and p.name='GenericTest' and t.parameters='numfail=0 retries=0';

  insert into $TABLE (suite, testcase)
  select s.id, t.id from suite s, testcase t, testproc p
  where p.id=t.testproc and
  s.name='Smoketest' and p.name='GenericTest' and t.parameters='numfail=0 retries=1';

  insert into $TABLE (suite, testcase)
  select s.id, t.id from suite s, testcase t, testproc p
  where p.id=t.testproc and
  s.name='Failures' and p.name='AlwaysFail' and t.parameters='';

  insert into $TABLE (suite, testcase)
  select s.id, t.id from suite s, testcase t, testproc p
  where p.id=t.testproc and
  s.name='Failures' and p.name='GenericTest' and t.parameters not like '%numfail=0%';

  insert into $TABLE (suite, testcase)
  select s.id, t.id from suite s, testcase t, testproc p
  where p.id=t.testproc and
  s.name='VeryAdvanced' and p.name='ComplexTest';
EOF
verify $?


############ TEST RUNS/RESULTS ##############

populate_table run
$MYSQL <<EOF
  insert into $TABLE (command, bed, tester, logs_url, 
  logtag, start_time, end_time, exitcode, env, comments)
  select 
  "/i/ran/this/script/from/here --with-arg-1=1 --with-arg-2=3", bed.id, u.id, 
  "logserver:/here/is/my/log", "asdf1234", "05-01-17 15:45:01", "05-01-17 15:59:58",
  0, "DEBUG=1 VERBOSE=1 testware_version=0.01", ""
  from bed, tester u where bed.name='qb-testing' and u.name='dm155201';
EOF
verify $?

populate_table result
RUN=1
$MYSQL <<EOF
  insert into $TABLE 
  (testcase, run, status, start_time, end_time,
      build, submitter, logs_url, notes)
  select 
  t.id, $RUN, "pass", "05-01-17 15:46:01", "05-01-17 15:47:00", 
  b.id, u.id, "logserver:/here/is/my/log/123", 
  "This testcase always passes - how boring."
  from testcase t, testproc p, build b, tester u
  where t.testproc=p.id and u.name="dm155201" and
  p.name="AlwaysPass" and t.parameters="" and b.name="charter-final";

  insert into $TABLE 
  (testcase, run, status, start_time, end_time, 
      build, submitter, logs_url, notes)
  select 
  t.id, $RUN, "pass", "05-01-17 15:49:01", "05-01-17 15:51:01", 
  b.id, u.id, "logserver:/here/is/my/log/234", ""
  from testcase t, testproc p, build b, tester u 
  where t.testproc=p.id and u.name='dm155201' and
  p.name="GenericTest" and t.parameters="numfail=0 retries=1" and b.name="charter-final";
EOF
verify $?

populate_table run
$MYSQL <<EOF
  insert into $TABLE (command, bed, tester, logs_url, 
  logtag, start_time, end_time, exitcode, env, comments)
  select 
  "/i/ran/this/script/from/here --with-single-flag", bed.id, u.id,
  "logserver:/here/is/new/log", "hjkl5678", "05-01-17 16:05:01", "05-01-19 11:23:07",
  0, "DEBUG=1 VERBOSE=1 testware_version=0.01", "Wow this took a long time..."
  from bed, tester u where bed.name='qb-testing' and u.name='dm155201';
EOF
verify $?

populate_table result
RUN=2
$MYSQL <<EOF
  insert into $TABLE 
  (testcase, run, status, start_time, end_time, 
      build, submitter, logs_url, notes)
  select 
  t.id, $RUN, "fail", "05-01-17 16:06:01", "05-01-17 16:06:03", 
  b.id, u.id, "logserver:/here/is/new/log/345", "Of course it failed. Duh."
  from testcase t, testproc p, build b, tester u 
  where t.testproc=p.id and u.name='dm155201' and 
  p.name="AlwaysFail" and t.parameters="" and b.name="honeycomb-bin.3247-.tar.gz";

  insert into $TABLE 
  (testcase, run, status, start_time, end_time,
      build, submitter, logs_url, notes)
  select 
  t.id, $RUN, "fail", "05-01-18 08:26:14", "05-01-18 11:20:13", 
  b.id, u.id, "logserver:/here/is/new/log/678", 
  "Seems like a bug... investigating!"
  from testcase t, testproc p, build b, tester u 
  where t.testproc=p.id and u.name='dm155201' and 
  p.name="ComplexTest" and t.parameters="cluster=large" and b.name="honeycomb-bin.3247-.tar.gz";

  insert into $TABLE 
  (testcase, run, status, start_time, end_time,
      build, submitter, logs_url, notes)
  select 
  t.id, $RUN, "pass", "05-01-19 11:22:01", "05-01-19 11:22:38",
  b.id, u.id, "logserver:/here/is/new/log/890", ""
  from testcase t, testproc p, build b, tester u 
  where t.testproc=p.id and u.name='dm155201' and 
  p.name="GenericTest" and t.parameters="numfail=1 retries=20" and b.name="honeycomb-bin.3247-.tar.gz";

  insert into $TABLE 
  (testcase, run, status, start_time, end_time,
      build, submitter, logs_url, notes)
  select 
  t.id, $RUN, "skipped", "05-01-19 11:22:48", "05-01-19 11:22:51",
  b.id, u.id, "", "Wrong cluster size - don't run this test"
  from testcase t, testproc p, build b, tester u 
  where t.testproc=p.id and u.name='dm155201' and 
  p.name="ComplexTest" and t.parameters="cluster=small" and b.name="honeycomb-bin.3247-.tar.gz";
EOF
verify $?

############ METRICS ##############

populate_table metric
$MYSQL <<EOF
  insert into $TABLE (name, units, datatype)
  values ("upload_throughput", "MB/sec", "double");

  insert into $TABLE (name, units, datatype)
  values ("download_throughput", "MB/sec", "double");

  insert into $TABLE (name, units, datatype)
  values ("CLI_latency", "usec", "int");

  insert into $TABLE (name, units, datatype)
  values ("retries", "count", "int");

  insert into $TABLE (name, units, datatype)
  values ("dirname", "full pathname", "string");

  insert into $TABLE (name, units, datatype)
  values ("cluster_uptime", "D HH:MM:SS", "time");
EOF
verify $?

populate_table result_metric
RESULT=1
$MYSQL <<EOF
  insert into $TABLE (metric, result)
  select m.id, $RESULT
  from metric m where m.name="retries" and m.units="count" and m.datatype="int";

  insert into result_metric_int (metric, value)
  select max(m.id), 10 from result_metric m;

  insert into $TABLE (metric, result)
  select m.id, $RESULT
  from metric m where m.name="dirname" and m.units="full pathname" and m.datatype="string";

  insert into result_metric_string (metric, value)
  select max(m.id), "/i/created/this/dir" from result_metric m;

  insert into $TABLE (metric, result)
  select m.id, $RESULT
  from metric m where m.name="cluster_uptime" and m.units="D HH:MM:SS" and m.datatype="time";

  insert into result_metric_time (metric, value, at_time)
  select max(m.id), "2 13:43:22", "05-01-17 15:49:01" from result_metric m;

  insert into result_metric_time (metric, value)
  select max(m.id), "2 16:43:22" from result_metric m;
EOF
verify $?

############ BUGS ##############

populate_table result_bug
$MYSQL <<EOF
  insert into $TABLE (result, bug, notes)
  values (1, "6209689", "This was the first test to find this bug");

  insert into $TABLE (result, bug)
  values (1, "6208632");

  insert into $TABLE (result, bug, notes)
  values (2, "6209689", "Fails intermittently about 2/3 of the time");
EOF
verify $?

############ TEST BATCHES ##############

# No sample data here, batches are not yet approved
# as part of QB database - see qb_maketables.sh (batch)



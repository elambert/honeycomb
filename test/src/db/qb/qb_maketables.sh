#!/bin/sh

#############################################
# QB Database schema
#
# Run this script on hc-web to recreate qb db
#
# Authors: Joshua Dobies, Daria Mehra
# Revision: $Id: qb_maketables.sh 10856 2007-05-19 02:58:52Z bberndt $
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

if [ $# -lt 1 ]; then
  echo "Specify one or both options to recreate main and/or batch tables"
  echo "USAGE: $0 [main] [batch]"
  exit -1
fi

while [ $# -ge 1 ]; do
  if [ $1 == "main" ]; then
    MAIN=main
  fi
  if [ $1 == "batch" ]; then
    BATCH=batch
  fi
  shift
done

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
echo "  THIS SCRIPT WILL DROP QB TABLES ( $MAIN $BATCH ) AND RECREATE THEM."
echo "  THE DATABASE AFFECTED IS THE ONE ACCESSED VIA THIS COMMAND..."
echo
echo "      $MYSQL"
echo
echo "  YOU WILL LOSE DATA!!!"
echo "  Press CTRL-C now to cancel!  (hit return to proceed)"
read dummy
echo "  ARE YOU SURE?!?  (hit enter to proceed)"
read dummy

### run_main is called at the bottom after all function definitions

function run_main() {

  if [ "$MAIN" == "main" ]; then
    recreate_main_tables
  fi

  if [ "$BATCH" == "batch" ]; then
    recreate_batch_tables
  fi
}

function verify() {
  EXITCODE=$1
  if [ "$EXITCODE" -eq "0" ]
  then
    echo " ok"
  else
    exit $EXITCODE
  fi
}

######################################

function recreate_main_tables() {

echo -n Dropping old MAIN tables...

$MYSQL << OK_ALL_DONE

drop table if exists tester;
drop table if exists result_metric_time;
drop table if exists result_metric_string;
drop table if exists result_metric_double;
drop table if exists result_metric_int;
drop table if exists result_metric;
drop table if exists metric;
drop table if exists result_bug;
drop table if exists result_tag;
drop table if exists tag;
drop table if exists result;
drop table if exists run;
drop table if exists reservation;
drop table if exists resource_attr;
drop table if exists resource;
drop table if exists resource_tree;
drop table if exists bed;
drop table if exists build;
drop table if exists suite_testcase;
drop table if exists suite_hierarchy;
drop table if exists suite;
drop table if exists testcase_tag;
drop table if exists testcase;
drop table if exists testproc;

OK_ALL_DONE

verify $?

echo -n Creating new MAIN tables...

$MYSQL << OK_ALL_DONE

# Usernames are references from many other tables
# (testcase author, result submitter, testbed owner etc)
# so it made sense to keep a lookup table.
# The database username doesn't have to match Sun ID.
#
create table tester
(
  id				bigint unsigned auto_increment unique,
  name                          varchar(128) not null,
  alias                         varchar(32) null,
  email                         varchar(255) null,

  primary key (name),
  index alias_i (alias)
)
type = InnoDB
comment = 'Lookup table for test engineer usernames'
;

# Procedure describes a sequence of actions which comprise a test.
# Testcase = procedure + unique parameters.
# Many testcases can reference the same procedure and only differ in params.
# Also OK to have exactly one testcase of a given procedure.
# Link can point to a program which automates this procedure,
# or to a test plan or other doc where procedure is already described.
#
create table testproc
(
  id				bigint unsigned auto_increment unique,
  name                          varchar(255) not null,
  objective                     text null,
  setup                         text null,
  steps                         text null,
  results                       text null,
  link                          text null,
  author                        bigint unsigned null,
  deleted                       bool default 'false' not null,

  primary key (name),
  index author_i (author),
  foreign key (author) references tester(id)
)
type = InnoDB
comment = 'Test procedure defines logical steps to perform during a test'
;

# Testcase = procedure (logical steps) + parameters (unique inputs)
# A name of a testcase = procedure name + parameters
#
create table testcase
(
  id				bigint unsigned auto_increment unique,
  testproc                      bigint unsigned not null,
  parameters                    varchar(255) default '' not null,
  comments                      text null,
  author                        bigint unsigned null,
  disabled                      bool default 'false' not null,
  deleted                       bool default 'false' not null,

  primary key (testproc, parameters),
  index testproc_i (testproc), index author_i (author),
  foreign key (testproc) references testproc(id),
  foreign key (author) references tester(id)
)
type = InnoDB
comment = 'Testcase = shared procedure + unique parameters'
;

#
# multiple tags can apply to each test case
#
create table testcase_tag
(
  id				bigint unsigned auto_increment unique,
  testcase			bigint unsigned not null,
  tag				bigint unsigned not null,

  primary key (testcase, tag),
  index testcase_i (testcase),
  index tag_i (tag),
  foreign key (testcase) references testcase(id),
  foreign key (tag) references tag(id)
)
type = InnoDB
comment = 'Which tags apply to each test case'
;

# Suite is a logical grouping of testcases and/or other suites
#
create table suite
(
  id				bigint unsigned auto_increment unique,
  name                          varchar(255) not null,
  description                   text null,
  author                        bigint unsigned null,
  deleted                       bool default 'false' not null,

  primary key (name),
  index author_i (author),
  foreign key (author) references tester(id)
)
type = InnoDB
comment = 'Suite combines testcases and/or other suites'
;

# Test suites can include other test suites.
# This hierarchy is dynamic: if A contains B, adding C to B auto-adds C to A
#
create table suite_hierarchy
(
  id			         bigint unsigned auto_increment unique,
  suite                          bigint unsigned not null,
  parent                         bigint unsigned not null,
  
  primary key (suite, parent),
  index suite_i (suite), index parent_i (parent),
  foreign key (suite) references suite(id),
  foreign key (parent) references suite(id)
)
type = InnoDB
comment = 'Define suites of suites'
;

# Test suites include testcases (not procedures!).
# A suite can include testcases, or testcases and other suites, or only suites.
#
create table suite_testcase
(
  id				bigint unsigned auto_increment unique,
  suite				bigint unsigned not null,
  testcase			bigint unsigned not null,

  primary  key (suite, testcase),
  index suite_i (suite), index testcase_i (testcase),
  foreign key (suite) references suite(id),
  foreign key (testcase) references testcase(id)
)
type = InnoDB
comment = 'Define suites of testcases'
;

# Build identifies the software which is being tested.
# The unique build name (version) is sufficient to point at a build,
# other info is duplicated from build's version file for querying.
# Revision is code revision to reference RCS: 
# numeric (revnum) for Subversion, or timestamp (revtime) for CVS
#
create table build
(
  id				bigint unsigned auto_increment unique,
  name				varchar(255) not null,
  timestamp                     datetime null,
  branch                        varchar(255) null,
  revnum                        bigint unsigned null,
  revtime                       datetime null,
  description			text null,
  deleted                       bool default 'false' not null,

  primary key (name),
  index branch_i (branch)
)
type = InnoDB
comment = 'Software build version info for querying'
;

# testbed is a combination of resources that belong to someone
# convenient for scheduling tests to run in batches, but optional
# testbed can be referenced in test runs/results
#
create table bed
(
  id				bigint unsigned auto_increment unique,
  name				varchar(255) not null,
  owner				bigint unsigned null,
  description			text null,
  deleted                       bool default 'false' not null,

  primary key (name),
  index owner_i (owner),
  foreign key (owner) references tester(id)
)
type = InnoDB
comment = 'Testbed is a set of resources owned by someone'
;
create index bed_owner on bed(owner);

# provides versioning for a set of resources.
#
create table resource_tree
(
  tuple				bigint unsigned auto_increment unique,
  created			datetime not null,
  deleted			bool default 'false' not null,

  primary key (tuple)
)
type = InnoDB
comment = 'Tree IDs will be used for version numbers'
;

# sample resources: cluster dev101, Solaris client hc-sol23 clientPoolA
# Resource hierarchy is possible using parent field.
#
create table resource
(
  tuple				bigint unsigned auto_increment unique,

  id				varchar(128) not null,
  tree				bigint unsigned not null,

  class				varchar(128) not null,
  parent			varchar(128) null,

  notes				text null,
  created			datetime not null,
  deleted                       bool default 'false' not null,

  primary key (id, tree),

  foreign key (tree) references resource_tree(tuple),
  foreign key (parent, tree) references resource(id, tree),

  index tree_i (tree), index id_tree_i (tree, id), index parent_tree_i (parent, tree)
)
type = InnoDB
comment = 'Resource can be a cluster, client, client pool, switch, etc.'
;

# Resource properties are free-form name/value pairs
#
create table resource_attr
(
  resource			bigint unsigned not null,
  name				varchar(255) not null,
  value				varchar(255) null,
  created			datetime not null,

  foreign key (resource) references resource(tuple),

  index resource_i (resource)
)
type = InnoDB
comment = 'Resource properties are keyword/value pairs, eg num_nodes = 3'
;

# reservation records a history of owners
#
create table reservation
(
  tuple				bigint unsigned auto_increment unique,

  resource			varchar(128) not null,
  owner				varchar(64) not null,
  start				datetime not null,
  end				datetime,

  primary key (tuple),
  foreign key (resource) references resource(id),

  index owner_start_end_i (owner, start, end),
  index resource_start_end_i (resource, start, end)
)
type = InnoDB
comment = 'History of resource reservations'
;

# run is a record of how some program ran, from cmd line and environment to exit code.
# Test results reference a run, ie they happen in the context of a run.
# Run environment (env vars, testware version, etc) is recorded as blob in env.
#
create table run
(
  id				bigint unsigned auto_increment unique,
  command			text not null,
  bed		   	        bigint unsigned null,
  tester		       	bigint unsigned null,
  logs_url		        varchar(255) null,
  logtag                        varchar(16) null,
  start_time			datetime null,
  end_time			datetime null,
  exitcode			int null,
  env                           text null,
  comments                      text null,
  deleted                       bool default 'false' not null,

  primary key (id),
  index bed_i (bed), index tester_i (tester),
  foreign key (bed) references bed(id),
  foreign key (tester) references tester(id)
)
type = InnoDB
comment = 'Provide run context for test results'
;

# result is completion of a test or its logical part, with pass|fail status.
# Exception: skipped tests record a no-op result with status=skipped.
# result corresponds to a given testcase (did this testcase pass or fail?)
# based on a given result, zero, one or more bugs can be filed
# a result can have zero, one or many metrics recorded for it

create table result
(
  id				bigint unsigned auto_increment unique,
  testcase			bigint unsigned not null,
  run    			bigint unsigned null,
  status			enum('running', 'pass', 'fail', 'skipped', 'unknown') default 'running' not null,
  start_time			datetime null,
  end_time			datetime null,
  build                         bigint unsigned null,
  submitter			bigint unsigned null,
  logs_url		        varchar(255) null,
  notes				text null,
  deleted                       bool default 'false' not null,

  primary key (id),
  index testcase_i (testcase), index run_i (run), index build_i (build), index submitter_i (submitter),
  foreign key (testcase) references testcase(id),
  foreign key (run) references run(id),
  foreign key (build) references build(id),
  foreign key (submitter) references tester(id)
)
type = InnoDB
comment = 'Record result (pass/fail etc.) for a testcase'
;
create index result_testcase on result (testcase);

# bug entries here will be Bugster IDs
# notes optionally explain how the bug is relevant to the test result
#
create table result_bug
(
  id				bigint unsigned auto_increment unique,
  result			bigint unsigned not null,
  bug				varchar(255) not null,
  notes                         text null,

  primary key (result, bug),
  index result_i (result),
  foreign key (result) references result(id)
)
type = InnoDB
comment = 'Which bugs are related to a test result'
;

# tags are a way of logically grouping test results
# sample tags: regression, experimental, quick, multiclient, ...
#
create table tag
(
  id				bigint unsigned auto_increment unique,
  name                          varchar(255) not null unique,
  description                   text null,
  deleted                       bool default 'false' not null,

  primary key (name)
)
type = InnoDB
comment = 'Lookup table of nametags to group test results'
;

# multiple tags can apply to each test result
#
create table result_tag
(
  id				bigint unsigned auto_increment unique,
  result			bigint unsigned not null,
  tag				bigint unsigned not null,
  notes                         text null,

  primary key (result, tag),
  index result_i (result),
  index tag_i (tag),
  foreign key (result) references result(id),
  foreign key (tag) references tag(id)
)
type = InnoDB
comment = 'Which tags apply to each test result'
;

# metrics are data points to be queried for averages, timeline graphs etc.
# lookup table. sample metric: "upload throughput, MB/sec"
# it's okay to also have metric: "upload throughput, Kb/sec"
#
create table metric
(
  id				bigint unsigned auto_increment unique,
  name                          varchar(255) not null,
  units                         varchar(64) not null,
  datatype                      enum('int', 'double', 'string', 'time') not null,
  author                        bigint unsigned null,
  deleted                       bool default 'false' not null,

  primary key (name, units, datatype),
  index author_i (author),
  foreign key (author) references tester(id)
)
type = InnoDB
comment = 'Define measurements to take during tests'
;

# a given result record can have many different metrics,
# and multiple values for the same metric, eg:
# throughput measured every 5 sec during the test
#
create table result_metric
(
  id				bigint unsigned auto_increment unique,
  metric			bigint unsigned not null,
  result			bigint unsigned not null,
  mgroup                        bigint unsigned null,

  primary key (metric, result),
  index metric_i (metric), index result_i (result), index mgroup_i (mgroup),
  foreign key (metric) references metric(id),
  foreign key (result) references result(id)
)
type = InnoDB
comment = 'Which metrics are related to a test result, can be grouped'
;

# the actual values are split into tables by data type, for fast query and math
# timestamps (default = now) are automatically recorded with each metric value
#
create table result_metric_int
(
  id				bigint unsigned auto_increment unique,
  metric			bigint unsigned not null,
  value				int not null,
  at_time                       timestamp,

  primary key (id),
  index metric_i (metric),
  foreign key (metric) references result_metric(id)
)
type = InnoDB
comment = 'Data points for integer metrics'
;

create table result_metric_double
(
  id				bigint unsigned auto_increment unique,
  metric			bigint unsigned not null,
  value				double not null,
  at_time                       timestamp,

  primary key (id),
  index metric_i (metric),
  foreign key (metric) references result_metric(id)
)
type = InnoDB
comment = 'Data points for double or floating-point metrics'
;

create table result_metric_string
(
  id				bigint unsigned auto_increment unique,
  metric			bigint unsigned not null,
  value				varchar(255) not null,
  at_time                       timestamp,

  primary key (id),
  index metric_i (metric),
  foreign key (metric) references result_metric(id)
)
type = InnoDB
comment = 'Data points for string metrics'
;

create table result_metric_time
(
  id				bigint unsigned auto_increment unique,
  metric			bigint unsigned not null,
  value				time not null,
  at_time                       timestamp,

  primary key (id),
  index metric_i (metric),
  foreign key (metric) references result_metric(id)
)
type = InnoDB
comment = 'Data points for time (duration) metrics'
;

commit;
OK_ALL_DONE

verify $?

} # end of recreate_main_tables()

#################################

function recreate_batch_tables() {

echo -n Ignore the following error if any...
$MYSQL << OK_ALL_DONE

alter table run drop column batch;

OK_ALL_DONE
echo ok

echo -n Dropping old BATCH tables...

$MYSQL << OK_ALL_DONE

drop table if exists batch_tests;
drop table if exists batch_suites;
drop table if exists batch;

OK_ALL_DONE

verify $?

echo -n Creating new BATCH tables...

$MYSQL << OK_ALL_DONE

# Batch is a group of tests runnable together, sequentially, on the same testbed.
# This is a convenience feature for scheduling; you don't need a batch to run a test.

create table batch
(
  id				bigint unsigned auto_increment unique,
  name                          varchar(255) not null,
  owner                         bigint unsigned not null,
  bed                           bigint unsigned not null,
  active                        int null,
  comments                      text null,
  deleted                       bool default 'false' not null,

  primary key (name),
  index bed_i (bed), index owner_i (owner),
  foreign key (bed) references bed(id),
  foreign key (owner) references tester(id)
)
type = InnoDB
comment = 'Batches combine tests/suites for scheduling test runs'
;

# The batch creation script/GUI will provide ability to add 
# testcases, or entire test suites with exclusion lists. 

create table batch_tests
(
  id				bigint unsigned auto_increment unique,
  batch                         bigint unsigned not null,
  testcase                      bigint unsigned not null,
  
  primary key (batch, testcase),
  index batch_i (batch), index testcase_i (testcase),
  foreign key (batch) references batch(id),
  foreign key (testcase) references testcase(id)
)
type = InnoDB
comment = 'Which testcases are included in each batch'
;

create table batch_suites
(
  id				bigint unsigned auto_increment unique,
  batch                         bigint unsigned not null,
  suite                         bigint unsigned not null,

  primary key (batch, suite),
  index batch_i (batch), index suite_i (suite),
  foreign key (batch) references batch(id),
  foreign key (suite) references suite(id)
)
type = InnoDB
comment = 'Which test suites are included in each batch'
;

alter table run add column batch 
bigint unsigned null
references batch(id);

OK_ALL_DONE

verify $?

} # end of recreate_batch_tables()

##################################

run_main

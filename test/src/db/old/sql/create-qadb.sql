-- $Id: create-qadb.sql 10856 2007-05-19 02:58:52Z bberndt $
--
-- Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
-- Use is subject to license terms.

create table test_result
(
  id				bigint unsigned auto_increment primary key,

  test				varchar(255) not null,
  pass				bit null,
  start_time			datetime null,
  end_time			datetime null,
  build				varchar(128) null,
  performer			varchar(128) null,
  proc_retval			int null,
  logs_url			varchar(255) null,
  notes				text null
);
create index test_result_test on test_result (test);

create table matrix_member
(
  matrix			varchar(128) not null,
  test				varchar(128) not null,

  primary key (matrix, test)
);
create index matrix_member_matrix on matrix_member (matrix);

create table result_bug
(
  result			bigint unsigned not null,
  bug				varchar(255) not null,

  primary key (result, bug),
  foreign key (result) references test_result(id)
);
create index result_bug_result on result_bug (result);
create index result_bug_bug on result_bug (bug);

create table result_metric
(
  result			bigint unsigned not null,
  name				varchar(255) not null,
  value				varchar(255) not null,

  primary key (result, name),
  foreign key (result) references test_result(id)
);
create index result_metric_result on result_metric (result);
create index result_metric_name on result_metric (name);


/*
test resource.
has a single owner.
test resources can be grouped.  a resource can only belong to one group, such
that it cannot be mistakingly owned by more than one person.
create test_resource
(
  id				bigint unsigned auto_increment primary key,
  resoure_group			bigint unisgned,
  foreign key resource_group references test_resource(id),

  name				varchar(128) not null unique,
  resource_type			int,
  owner				varchar(64),
  notes				text
)
go

create test_resource_prop
(
  test_id			bigint unsigned auto_increment,
  foreign key test_id references test_resource(id),

  name				varchar(128) not null,
  value				varchar(128)
)
go

create test_resource_default_prop
(
)
go

create table node
(
  id				ident_t not null,
  hostname			name_t not null,
  ip				ip_t,
  				
)

create table test_bed
(
  name				name_t not null,
  owner				name_t not null
)

create table test_bed_machines
(
  testbed			name_t not null,
  machine			ident_t not null
)
*/


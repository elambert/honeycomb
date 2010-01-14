#!/bin/sh

#
# Flamebox is a reimplementation of Tinderbox, roughly based on 
# Tinderbox 2 code by Ken Estes.
#
# Rewritten by Riverbed Technology, Inc.
# (C) 2003-2004 Riverbed Technology, Inc. All Rights Reserved.
#
# David Wu (davidwu@riverbed.com)
#
# This code contains portions copied from the Tinderbox 2 tool, which
# carries the following license:
#
## The contents of this file are subject to the Mozilla Public
## License Version 1.1 (the "License"); you may not use this file
## except in compliance with the License. You may obtain a copy of
## the License at http://www.mozilla.org/NPL/
##
## Software distributed under the License is distributed on an "AS
## IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
## implied. See the License for the specific language governing
## rights and limitations under the License.
##
## The Original Code is the Tinderbox build tool.
##
## The Initial Developer of the Original Code is Netscape Communications
## Corporation. Portions created by Netscape are
## Copyright (C) 1998 Netscape Communications Corporation. All
## Rights Reserved.
##
## Complete rewrite by Ken Estes, Mail.com (kestes@staff.mail.com).
##

USER=nobody
PASSWORD=

if test x$PASSWORD = x ; then
  MYSQL="mysql -u $USER --table"
else
  MYSQL="mysql -u $USER -p$PASSWORD --table"
fi

echo
echo "Will use user=\"$USER\" and password=\"$PASSWORD\" for flamebox"
echo "database. If you have a previous flamebox install, this script will"
echo "drop all flamebox tables. Press ctrl-c to bail out now or return to"
echo "continue."

read dummy

echo Dropping old tables

$MYSQL << OK_ALL_DONE

use flamebox;

drop table task;
drop table page;
drop table run;

OK_ALL_DONE

echo creating new tables

$MYSQL << OK_ALL_DONE

use flamebox;

create table task (
    id mediumint not null auto_increment primary key,
    name varchar(64),

    index(name)
);

show tables like 'task';
describe task;

create table page (
    id mediumint not null auto_increment primary key,
    repository varchar(64) binary not null,
    module varchar(64) binary not null,
    branch varchar(64) binary not null,

    unique(repository, module, branch)
);

show tables like 'page';
describe page;

create table run (
    id mediumint not null auto_increment primary key,
    starttime datetime not null,
    pageid mediumint not null,
    taskid mediumint not null,
    status int not null,
    groupid mediumint not null,

    unique(starttime, pageid, taskid),
    index(starttime),
    index(pageid),
    index(taskid)
);

show tables like 'run';
describe run;

show tables;

OK_ALL_DONE

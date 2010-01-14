-- $Id: filestore_mysql.sql 10856 2007-05-19 02:58:52Z bberndt $
--
-- Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
-- Use is subject to license terms.

drop table if exists file;

create table file (
    path blob not null,
    oid tinyblob not null,
    index path_index (path(255)),
    index oid_index (oid(64))
);

drop table if exists directory;

create table directory (
    path blob not null,
    index path_index (path(255))
);

-- $Id: mysql_config.sql 10855 2007-05-19 02:54:08Z bberndt $
--
-- Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
-- Use is subject to license terms.

create database if not exists honeycomb_emd;

use mysql;
GRANT ALL PRIVILEGES ON honeycomb_emd.* to honeycomb@localhost IDENTIFIED BY 'honeycomb' WITH GRANT OPTION;
FLUSH PRIVILEGES;

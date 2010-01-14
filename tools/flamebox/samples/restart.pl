#!/usr/bin/perl

require 'params.pl';
require 'db.pl';

print "Hit return to rebuild database.\n";

print `sh ./maketables.sh`;

do_db_test_insert();

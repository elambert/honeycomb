#!/usr/bin/perl -w

############################################
# Perl API to QB Test Management Database
#
# Author: Daria Mehra
# Revision: $Id: QBDB.pm 10856 2007-05-19 02:58:52Z bberndt $
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



############################################

use strict;
use DBI;
require Exporter;

our @ISA = qw(Exporter);
our @EXPORT_OK = qw( post_run
		     post_result
		     post_metric
		     post_bug
		     post_build
                     post_tag

		     select_results
                     select_bugs
                     select_tags
		     );

our ($dbi, $dburl, $dbuser, $dbpasswd);
$dburl = "DBI:mysql:qb:hc-web";
$dbuser = "nobody";
$dbpasswd = "";
$dbi = DBI->connect($dburl, $dbuser, $dbpasswd, 
		    { AutoCommit => 0, PrintError => 0, RaiseError => 0 }) 
    or die "Couldn't connect to QB database: " . $DBI::errstr;

# Autocommit is disabled, therefore DBI will implicitly begin a new transaction
# after creating connection, after each commit and after each rollback.
# Our code needs to call commit explicitly, and rollback is part of fail_op().
# Careful with commits! Only commit from highest-level operations in public API.

# If some DB request failed, rollback transaction, communicate error, and exit
#
sub fail_db_op
{
    my ($msg) = @_;
    $msg .= ": ";
    $msg .= $dbi->errstr() if defined $dbi->errstr();
    $dbi->rollback();
    fail_op($msg);
}

# generic failure handling
#
sub fail_op
{
    my ($msg) = @_;
    die "[" . localtime(time) . "] DATABASE ERROR: " . $msg;
    # Note: it's OK to die in CGI context because cgi script traps
}

# Each post_ subroutine takes a hash of named columns => values,
# inserts the data into QB database, and returns ID of inserted item
# If input contains ID, post_ will update existing entry for that item
# Not all post_ subs support update mode, some only insert.

# Insert a new test run entry into QB database, or update existing one: public API.
# If args include id => <numeric-database-id>, then it's an existing run.
#
sub post_run
{
    my %args = @_;  # eg: { command => "foo", env => "bar duh" }

    # get database ID for these items; quietly insert them if not in DB
    if (defined $args{bed}) {
	my $bed = get_bed_id($args{bed}) 
	    or fail_db_op("Missing required ID for arg: bed = " . $args{bed});
	$args{bed} = $bed; # reset to pass along
    }
    if (defined $args{tester}) {
	my $tester = get_tester_id($args{tester}) 
	    or fail_db_op("Missing required ID for arg: tester = " . $args{tester});
	$args{tester} = $tester;
    }

    my $run_id;
    if (defined $args{id}) {
	$run_id = $args{id};
	delete $args{id};
	$args{end_time} = "NOW()" unless defined $args{end_time};
	update_db_entry("run", {id => $run_id}, \%args);
    } else {
	$args{start_time} = "NOW()" unless defined $args{start_time};
	$run_id = insert_db_entry("run", \%args);
    }
    $dbi->commit();
    return $run_id;
    # ID can be used to update this run later, or reference it in test results
}

# Insert a new test result into QB database, or update existing one: public API.
# If args include id => <numeric-database-id>, then it's an existing result.
#
sub post_result
{
    my %args = @_; # eg: { testproc => "mytest", parameters => "filesize=5", status => "fail" }

    if (defined $args{testproc} && $args{parameters}) {
    	my $testcase_id = get_testcase_id($args{testproc}, $args{parameters})
	    or fail_db_op("Missing required ID for testcase: procedure = " . 
			  $args{testproc} . ", parameters = ". $args{parameters});
	$args{testcase} = $testcase_id;
	delete $args{testproc}; # replaced by testcase_id
	delete $args{parameters};
    } else {
	fail_db_op("Missing both result ID and testproc+parameters") unless defined $args{id};
    }

    if (defined $args{submitter}) {
	my $tester = get_tester_id($args{submitter}) 
	    or fail_db_op("Missing required ID for arg: submitter = " . $args{tester});
	$args{submitter} = $tester;
    }

    if (defined $args{build}) {
	my $build = get_build_id($args{build}) 
	    or fail_db_op("Missing required ID for arg: build = " . $args{tester});
	$args{build} = $build;
    }

    my $post_bugs = 0; # convenience: bug IDs can be given as a list
    # if you do this, notes cannot be supplied! use post_bug() instead
    if (defined $args{buglist}) {
	$post_bugs = $args{buglist};
	delete $args{buglist};
    }

    my $post_tags = 0; # convenience: tags can be given as a list
    # if you do this, notes cannot be supplied! use post_tag() instead
    if (defined $args{taglist}) {
	$post_tags = $args{taglist};
	delete $args{taglist};
    }

    my $result_id;
    if (defined $args{id}) {
	$result_id = $args{id};
	delete $args{id};
	$args{end_time} = "NOW()" unless defined $args{end_time};
	update_db_entry("result", {id => $result_id}, \%args);	
    } else { # insert new result
	$args{start_time} = "NOW()" unless defined $args{start_time};
	$result_id = insert_db_entry("result", \%args);
    }

    if ($post_bugs) { # insert bugs on this test result
	foreach my $bug (split(' ', $post_bugs)) {
	    get_id_autoinsert("result_bug", { result => $result_id, bug => $bug });
	}
    }

    if ($post_tags) { # add tags for this test result
        foreach my $tag (split(' ', $post_tags)) {
            post_tag(result => $result_id, name => $tag, testcase => $args{testcase});
        }
    }
    
    $dbi->commit();
    return $result_id;
    # ID can be used to update this result later, or reference it in metrics and bugs
}

# Insert a new metric for a given test result into QB database: public API.
# Updating existing metrics is not supported, insert a new one instead.
# Exception: a result_metric entry can be updated to include group ID.
#
sub post_metric
{
    my %args = @_; # eg: { result => 123, name => "speed", units = "ops/sec", datatype = "int", value => 50 }

    # special case: update entry to include group ID
    if (defined $args{id}) { 
        defined $args{mgroup} or 
            fail_db_op("Missing required group ID to update metric: ".$args{id});
        update_metric_group($args{id}, $args{mgroup});
        $dbi->commit(); # done!
        return $args{id}; 
    }

    # metric values are recorded on separate tables based on datatype: 
    # int, double, string, time. Timestamp when measured is also recorded.
    # Common tables metric (dictionary) and result_metric (reference)
    # are updated as part of get_metric_id(...).

    my $table = "result_metric_".$args{datatype}; 

    my $metric_id = 
        get_metric_id($args{name}, $args{units}, $args{datatype}, $args{result})
	or fail_db_op("Missing required ID for arg: metric = ".$args{name});
    
    if (defined $args{mgroup}) { # update metric group ID
        update_metric_group($metric_id, $args{mgroup});
        delete $args{mgroup};
    }

    foreach my $key ('result', 'name', 'units', 'datatype', 'mgroup') {
	delete $args{$key} if defined $args{$key}; # not needed for insertion
    }
    $args{metric} = $metric_id;
    
    # args are: metric, value, optionally timestamp (default = now)
    insert_db_entry($table, \%args); 
    $dbi->commit();
    return $metric_id;
    # ID can be used to update the result_metric entry (ie to set groups).
}

# special case: update group ID on existing metric entry
sub update_metric_group
{
    my ($metric_id, $mgroup) = @_;
    update_db_entry("result_metric", {id => $metric_id}, {mgroup => $mgroup});
}

# Insert bugs for a given test result into QB database: public API.
#
sub post_bug
{
    my %args = @_; # eg: { result => 123, bug => 45678, notes => "Fails 100%" }

    get_id_autoinsert("result_bug", \%args)
	or fail_db_op("Failed to insert bug = ".$args{bug}." for result = ".$args{result});
    $dbi->commit();

    return $args{bug};
    # bug ID is informational only
}

# Insert test nametags for a given test result into QB DB: public API
# Updates 2 tables: result_tag and testcase_tag (used in reporting)
#
sub post_tag
{
    my %args = @_; # eg: { result => 123, testcase => "456", name => "experimental", notes => "Not ready for prime time" }

    my $commit = 1; # hack to call from post_result without premature commit
    if (defined $args{nocommit}) {
	$commit = 0;
	delete $args{nocommit};
    }

    my $tag_id = get_tag_id($args{name})
        or fail_db_op("Missing required ID for arg: tag name = ".$args{name});
    delete $args{name}; # no longer needed, replaced by ID
    $args{tag} = $tag_id;

    my $testcase_id = $args{testcase};
    delete $args{testcase}; # not needed in result_tag table

    if (!defined $testcase_id) {
        # We must be updating an existing result, so we know result ID,
        # but not testcase ID. Look up testcase ID for this result.
        my $stmt = "SELECT testcase FROM result where id = $args{result}";
        my $result = db_select_arrayref($stmt);
        $testcase_id = $result->[0]->[0];
        if (!defined $testcase_id) {
            fail_db_op("Testcase undefined in post_tag for tag = ".$args{tag}." for result = ".$args{result});
        }
    }

    get_id_autoinsert("result_tag", \%args)
	or fail_db_op("Failed to insert tag = ".$args{tag}." for result = ".$args{result});

    my %args2 = ("testcase" => $testcase_id, "tag" => $tag_id);
    get_id_autoinsert("testcase_tag", \%args2)
	or fail_db_op("Failed to insert tag = ".$args2{tag}." for testcase = ".$args2{testcase});

    $dbi->commit() if $commit;

    return $args{tag};
}

# Create build entry with detailed info (revision number, branch etc.)
# Short build entry (just id+name) can be auto-created from post_result.
#
sub post_build
{
    my %args = @_; # eg: { name => "honeycomb-bin.12345-trunk.tar.gz, revnum => 12345 }

    my $build_id = get_build_id($args{name})
	or fail_db_op("Missing required ID for arg: build = ".$args{name});

    update_db_entry("build", {name => $args{name}}, \%args);
    $dbi->commit();
    return $build_id;
}

# Helper function to insert a set of column values into a given table
#
sub insert_db_entry
{
    my ($table, $args) = @_; # table name and hash ref to column name => value pairs

    my @values = values %$args;
    my %is_number;
    @is_number{@values} = DBI::looks_like_number(@values);
    $is_number{"NOW()"} = "DB function, do not quote";
    $is_number{"NULL"} = "DB function, do not quote";

    my ($column_list, $value_list) = ("", ""); # compose from the hash

    foreach my $col (keys %$args) {
	my $val = $args->{$col};
	$column_list .= "${col},";
	unless (defined $is_number{$val} && $is_number{$val}) {
	    $val = $dbi->quote($val);
	}
	$value_list .= "${val},";
    }
    chop $column_list; # remove trailing comma
    chop $value_list;

    $dbi->do("INSERT INTO $table (${column_list}) VALUES (${value_list})") 
	or fail_db_op("inserting into $table (${column_list}) VALUES (${value_list})");
    
    my $id = get_last_inserted_id();
    return $id;
}

# Helper function to update an existing entry in a given table
#
sub update_db_entry
{
    my ($table, $where, $args) = @_; # table name, hash ref to conditions and to columns => values

    if (defined $args->{id}) { # note: it's ok to have $where->{id}
	fail_db_op("Cannot update ID column: id = ".$args->{id});
    }
    
    my @values = values %$args;
    my %is_number;
    @is_number{@values} = DBI::looks_like_number(@values);
    $is_number{"NOW()"} = "DB function, do not quote";

    # new values (which columns to update)
    my $update = "UPDATE $table SET ";
    foreach my $col (keys %$args) {
	my $val = $args->{$col};
	unless (defined $is_number{$val} && $is_number{$val}) {
	    $val = $dbi->quote($val);
	}
	$update .= "${col}=${val},";
    }
    chop $update; # remove trailing comma
    
    # condition (which entries to update)
    $update .= " WHERE " unless (scalar keys %$where == 0);
    foreach my $col (keys %$where) {
	my $val = $where->{$col};
	unless (defined $is_number{$val} && $is_number{$val}) {
	    $val = $dbi->quote($val);
	}
	$update .= "${col}=${val} AND ";
    }
    $update =~ s/ AND $//; # remove trailing AND

    $dbi->do($update) or fail_db_op("updating $table");
    # return nothing because ID may not be known
}

sub db_do
{
    my ($stmt) = @_;
    $dbi->do($stmt);
}

# call this after any insert to get ID of newly inserted item
#
sub get_last_inserted_id
{
    my $getid = $dbi->prepare("SELECT LAST_INSERT_ID()")
	or fail_db_op("preparing");
    $getid->execute() or fail_db_op("executing");
    my ($id) = $getid->fetchrow();
    return $id;
}

# get item's ID by unique name, create if doesn't already exist
# this is a common operation, hence the subroutine
#
sub get_id_autoinsert
{
    my ($table, $args) = @_; # table name and hash ref to column name => value pairs
    # eg: get_id_autoinsert("friends", {nickname => "Party Pooper"});

    # see if item already exists
    my $stmt = "SELECT id FROM $table WHERE ";
    foreach my $col_name (keys %$args) {
	$stmt .= "$col_name = ? AND ";
    }
    $stmt =~ s/ AND $//; # remove trailing AND
    
    my $getid = $dbi->prepare($stmt) or fail_db_op("preparing");
    $getid->execute(values %$args) or fail_db_op("executing");
    my $id = $getid->fetchrow();
   
    unless ($id) { # auto-insert new item
	$id = insert_db_entry($table, $args);
    }
    return $id;
}

# find testbed ID by name, create if doesn't already exist
#
sub get_bed_id
{
    my ($name) = @_;
    return get_id_autoinsert("bed", {name => $name});
}

# find tester (user) by name, create if doesn't already exist
#
sub get_tester_id
{
    my ($name) = @_;
    return get_id_autoinsert("tester", {name => $name});
}

# find build by name (version tag), create if doesn't already exist
#
sub get_build_id
{
    my ($name) = @_;
    return get_id_autoinsert("build", {name => $name});
}

# find tag by name, create if doesn't already exist
#
sub get_tag_id
{
    my ($name) = @_;
    return get_id_autoinsert("tag", {name => $name});
}

# find testcase by procedure name and parameters,
# create both case and procedure if don't already exist
#
sub get_testcase_id
{
    my ($proc_name, $parameters) = @_;
    my $proc_id = get_id_autoinsert("testproc", {name => $proc_name});
    my $case_id = get_id_autoinsert("testcase", {testproc => $proc_id, parameters => "$parameters"});
    return $case_id;
}

# find metric by its name, units and datatype, create if doesn't already exist
# then correlate metric with result, create entry if doesn't already exist, and return that ID
#
sub get_metric_id
{
    my ($name, $units, $datatype, $result) = @_;
    my $metric_id = get_id_autoinsert("metric", {name => $name, units => $units, datatype => $datatype}); # in dictionary table
    my $result_metric_id = get_id_autoinsert("result_metric", {result => $result, metric => $metric_id}); # in result-metric correlation table
    return $result_metric_id;
}


# helper sub provides correct quoting of where clause in DB queries
# important: call it _before_ adding cross-table references!
#
sub quote_where_clause
{
    my ($where) = @_; # hash ref
    my @values = values %$where;
    my %is_number;
    @is_number{@values} = DBI::looks_like_number(@values);
    $is_number{"NOW()"} = "DB function, do not quote";
    foreach my $key (keys %$where) {
	my $val = $where->{$key};
	unless (defined $is_number{$val} && $is_number{$val}) {
	    $where->{$key} = $dbi->quote($val);
	}
    }
    return $where;
}
    
# select tests results given the selection list (what)
# and constraints (the where clause of the query).
#
sub select_results
{
    my ($what, $where) = @_; 
    # what = array ref to set of fields, where = hash ref to conditions
    my %optional = (); # which reference fields are optional => LEFT JOIN

    $where = quote_where_clause($where);
    
    # add cross-table references where needed
    foreach my $field (@$what) {
        $where->{'result.testcase'} = 'testcase.id' if ($field =~ /^testcase/);
        $where->{'testcase.testproc'} = 'testproc.id' if ($field =~ /^testproc/);
        $where->{'result.submitter'} = 'tester.id' if ($field =~ /^tester/);

        if ($field =~ /^run/) { # run reference is optional, ie run can be NULL
            $optional{'run'} = {'result.run' => 'run.id'};
        }
	if ($field =~ /^build/) { # build is optional too
	    $optional{'build'} = {'result.build' => 'build.id'};
	}
    }
    
    my @sort = ('result.id'); # order by result ID

    return select_db_entries($what, $where, \@sort, \%optional);
}

# select test-related bugs for a given test result ID
#
sub select_bugs
{
    my ($what, $where) = @_; 
    # what = array ref to set of fields, where = hash ref to conditions
    $where = quote_where_clause($where);
    my @sort = ('result_bug.bug'); # order by bug ID
    return select_db_entries($what, $where, \@sort);
}

# select test-related tags for a given result ID
#
sub select_tags
{
    my ($what, $where) = @_;
    # what = array ref to set of fields, where = hash ref to conditions
    $where = quote_where_clause($where);
    $where->{'result_tag.tag'} = 'tag.id'; # cross-table reference

    my @sort = ('tag.name'); # order by tag name
    return select_db_entries($what, $where, \@sort);
}

# select metrics collected for a given result ID
# this select is more complex than usual because metrics are stored
# in several tables, based on their datatype, to enable avg/max/min calc
#
sub select_metrics
{
    my ($what, $where) = @_;
    # what = array ref to set of fields, where = hash ref to conditions
    $where = quote_where_clause($where);
    $where->{'result_metric.metric'} = 'metric.id'; # cross-table ref

    my @sort = ('metric.id', 'result_metric.id');
    foreach my $datatype ('int', 'double', 'string', 'time') {
        my $tbl = 'result_metric_'.$datatype;
        push @sort, $tbl.".id";
    } # to sort data points chronologically

    my @what_all; # extended set of fields to select, incl. by-datatype tables
    my %by_datatype = (); # select will JOIN result_metric_<datatype> tables

    foreach my $col_name (@$what) {
        if ($col_name =~ /result_metric\.(value|at_time)/) {
            my $field = $1; # will SELECT from each by-datatype table
            foreach my $datatype ('int', 'double', 'string', 'time') {
                my $tbl = 'result_metric_'.$datatype;
                $by_datatype{$tbl} = { 'result_metric.id' => $tbl.'.metric' };
                push @what_all, $tbl.".".$field;
            }
        } else {
            push @what_all, $col_name; # do not expand
        }
    }
    
    my @rawdata = select_db_entries(\@what_all, $where, \@sort, \%by_datatype);
    my @data = (); # will strip out NULL fields
    foreach my $row (@rawdata) {
        my @stripped_row;
        foreach my $field (@$row) {
            push @stripped_row, $field unless ($field =~ /^$/);
        }
        push @data, \@stripped_row;
    }
    return @data;
}


# helper subroutine for selects. Inputs: 
# - array ref to list of field names (what),
# - hash ref to field = value pairs (where),
# - array ref to list of fields to order the data by (sort)
# - hash ref to table = condition pairs (join X on A=B and Y=Z)
# Outputs: array of data rows
#
# XXX: does not currently support LIMIT or cookies
# this may be needed if the query returns too much data for HTML rendering
#
sub select_db_entries 
{
    my ($what, $where, $sort, $optional) = @_;
    my %tables = (); # select from these tables

    # compose select statement
    my $stmt = "SELECT ";
    foreach my $col_name (@$what) {
	$stmt .= "$col_name, ";
	if ($col_name =~ /(.*)\./) { # parse out table name
	    $tables{$1} = 1 unless (defined $optional->{$1}); 
	} # optional table references will become LEFT JOIN
    }
    $stmt =~ s/, $//; # remove trailing comma
    
    $stmt .= " FROM ".join(', ', keys %tables);

    foreach my $table (keys %$optional) {
        $stmt .= " LEFT JOIN ".$table." ON ";
        my $join_on_conds = $optional->{$table};
        # assume that join condition is always simple equality
        foreach my $cond (keys %$join_on_conds) {
            $stmt .= $cond." = ".$join_on_conds->{$cond}." AND ";
        }
        $stmt =~ s/ AND $//; # remove trailing AND
    }

    $stmt .= " WHERE ";
    foreach my $field (keys %$where) {
	my $val = $where->{$field};
	if ($field =~ /start_time/) { # special test: >=
	    $stmt .= "$field >= $val AND ";
	} elsif ($field =~ /end_time/) { # special test: <=
	    $stmt .= "$field <= $val AND ";
	} elsif ($val =~ /^\'\%.*\%\'$/) { # special test: wildcard
	    $stmt .= "$field LIKE $val AND ";
	} else { # normal test: equality
	    $stmt .= "$field = $val AND ";
	}
    }
    $stmt =~ s/ AND $//; # remove trailing AND

    $stmt .= " ORDER BY ".join(', ', @$sort);

    # run select, get back a ref to array of data entries
    my $data = $dbi->selectall_arrayref($stmt);
    
    if (!defined $data) {
        die "ERROR: SELECT FAILED: \n".$stmt."\n"; # for debugging purposes
    }

    return @$data;
}

#
# raw select
#
sub db_select_arrayref
{
    my ($stmt) = @_;
    my $results = $dbi->selectall_arrayref($stmt);
    
    if (!defined $results) {
        die "ERROR: SELECT FAILED: \n".$stmt."\n"; # for debugging purposes
    }

    return $results;
}

sub latestTree()
{
  my $result = undef;
  my $stmt;
  $stmt = "select max(tuple) from resource_tree";
  print STDERR "LAB: $stmt\n";
  my $arrayref = $dbi->selectall_arrayref($stmt);
  if (defined($arrayref))
  {
    my @tuples = @$arrayref;
    my $tuple = $tuples[0];
    $result = @$tuple[0];
  }
  return $result;
}

sub reservables()
{
  my $result = undef;
  my $tree = latestTree();
  if (defined($tree))
  {
    my $stmt = "select distinct r.id from resource r, resource_attr ra where ra.resource = r.tuple and ra.name = 'reservable' and ra.value = 'true' and r.tree = ${tree} order by r.id asc";
    print STDERR "LAB: $stmt\n";
    $result = $dbi->selectall_arrayref($stmt);
  }
  return $result;
}

sub currentReservation($)
{
  my ($reservable) = @_;
  my $result = undef;
  my $stmt;
  $stmt = "select max(tuple) from reservation where resource = '${reservable}'";
  print STDERR "LAB: $stmt\n";
  my $arrayref = $dbi->selectall_arrayref($stmt);
  if (defined($arrayref))
  {
    my @tuples = @$arrayref;
    my $tuple = $tuples[0];
    my $latest_reservation = @$tuple[0];
    if (defined($latest_reservation))
    {
      $stmt = "select tuple, owner, start from reservation where tuple = ${latest_reservation} and end is null";
      print STDERR "LAB: $stmt\n";
      $arrayref = $dbi->selectall_arrayref($stmt);
      if (defined($arrayref))
      {
        @tuples = @$arrayref;
        $tuple = $tuples[0];
        $result = $tuple;
      }
    }
  }
  return $result;
}

sub checkout($$)
{
  my ($resource, $owner) = @_;
  my %args = ();
  $args{resource} = $resource;
  $args{owner} = $owner;
  $args{start} = "NOW()";
  return insert_db_entry("reservation", \%args);
}

sub checkin($)
{
  my ($tuple) = @_;
  my %args = ();
  $args{end} = "NOW()";
  update_db_entry("reservation", {tuple => $tuple}, \%args);
}

sub loadResources($$)
{
  my ($where, $deep) = @_;

  my @resources = ();

  my $stmt = "select tuple, id, tree, class, parent, notes, created, deleted from resource where ${where}";
  print STDERR "LAB: ${stmt}\n";
  my $arrayref = $dbi->selectall_arrayref($stmt);
  if (defined($arrayref))
  {
    foreach my $tuple (@$arrayref)
    {
      my ($tuple, $id, $tree, $class, $parent, $notes, $created, $deleted) = @$tuple;
      my %data = ();
      $data{tuple} = $tuple;
      $data{id} = $id;
      $data{tree} = $tree;
      $data{class} = $class;
      $data{parent} = $parent;
      $data{notes} = $notes;
      $data{created} = $created;
      $data{deleted} = $deleted;

      if ($deep)
      {
        my %attributes = ();
        $stmt = "select name, value from resource_attr where resource = ${tuple}";
        print STDERR "LAB: ${stmt}\n";
        $arrayref = $dbi->selectall_arrayref($stmt);
        if (defined($arrayref))
        {
          foreach $tuple (@$arrayref)
          {
            my ($name, $value) = @$tuple;
            $attributes{$name} = $value;
          }
          $data{attributes} = \%attributes;
        }
      }

      push(@resources, \%data);
    }
  }

  if (scalar(@resources) == 0)
  {
    return undef;
  }

  return \@resources;
}

sub db_commit
{
  $dbi->commit();
}


1; # end of module

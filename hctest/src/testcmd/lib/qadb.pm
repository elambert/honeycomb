#!/usr/bin/perl -w
#
# $Id: qadb.pm 10858 2007-05-19 03:03:41Z bberndt $
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

use strict ;
use DBI ;
use Sys::Hostname ;
use POSIX qw(strftime);

package qadb ;

use vars qw($errno $errstr $dbh) ;
($errno, $errstr) = (0, "") ;

$dbh = undef ;
my $dbhost = "hcarchive" ;
my $dbuser = "test" ;
my $dbpass = "" ;
my $dbname = "test" ;

$ENV{LANG} = "en_US"; # for some reason, this fixes a segfault.

################################################################################
# connect
################################################################################
sub connect ()
{
  $dbh = DBI->connect("DBI:mysql:database=${dbname};host=${dbhost}",$dbuser, $dbpass, {'RaiseError' => 1});
  if (!defined($dbh))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    return 0 ;
  }

  return 1 ;
}

################################################################################
# disconnect
################################################################################
sub disconnect ()
{
  if (!defined($dbh))
  {
    return 1 ;
  }

  if (!$dbh->disconnect())
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    return 0 ;
  }

  return 1 ;
}

################################################################################
# quote_replace
################################################################################
sub quote_replace ($)
{
  my ($string) = @_ ;

#modify the string, so as to replace all embedded double quotes with
#single quotes to make the string predictably db-storable.

  $string =~  s/\"/\'/g;
  return $string;
}

################################################################################
# quote_replace
################################################################################
sub time_to_datetime ($)
{
  my ($t) = @_;

  my ($sec,$min,$hour,$mday,$mon,$year,$wday,$yday) = localtime($t);
  $mon++;
  $year += 1900;

  return "${year}-${mon}-${mday} ${hour}:${min}:${sec}";
}

################################################################################
# test_result insert
# 
# return the id of the newly inserted test_result on success; 
# 0 otherwise (inqadb::errno and inqadb::errstr are set appropriately.)
#
# start_time and end_time should be specified as values taken from time().
################################################################################
sub test_result_insert ($$$$$$$$$$$$$$)
{
  my ($test, $pass, $cluster, $start_config, $end_config, $start_time, $end_time, $build, $branch, $performer, $proc_retval, $logs_url, $log_summary, $notes) = @_ ;

  if (!defined($test))
  {
    ($errno, $errstr) = (-1, "test_result_insert(...): argument 1, test, must be non-null");
    return 0;
  }
  else
  {
    chomp($test);
    $test = "\"" . quote_replace($test) . "\"";
  }

  if (!defined($pass))
  {
    $pass = "null";
  }
  else
  {
    chomp($pass);
    if ($pass =~ /^[Yy]([Ee][Ss])?$/ ||
        $pass =~ /^[Tt][Rr][Uu][Ee]$/ ||
        $pass =~ /^1$/)
    {
      $pass = 1;
    }
    else
    {
      $pass = 0;
    }
  }

  if (defined($cluster))
  {
    $cluster = "'" . $cluster . "'";
  }
  else
  {
    $cluster = "null";
  }

  if (defined($start_config))
  {
    $start_config = "'" . quote_replace($start_config) . "'";
  }
  else
  {
    $start_config = "null";
  }

  if (defined($end_config))
  {
    $end_config = "'" . quote_replace($end_config) . "'";
  }
  else
  {
    $end_config = "null";
  }

  if (defined($log_summary))
  {
    $log_summary = "'" . quote_replace($log_summary) . "'";
  }
  else
  {
    $log_summary = "null";
  }

  if (defined($start_time))
  {
    chomp($start_time);
    $start_time = "'" . time_to_datetime($start_time) . "'";
  }
  else
  {
    $start_time="null";
  }
  
  if (defined($end_time))
  {
    chomp($end_time);
    $end_time = "'" . time_to_datetime($end_time) . "'";
  }
  else
  {
    $end_time="null";
  }

  if (!defined($build))
  {
    $build = "null";
  }
  else
  {
#   chomp($build);
    $build = "\"" . quote_replace($build) . "\"";
  }

  if (!defined($branch))
  {
    $branch = "null";
  }
  else
  {
    chomp($branch);
    $branch = "\"" . quote_replace($branch) . "\"";
  }

  if (!defined($performer))
  {
    $performer = "null";
  }
  else
  {
    chomp($performer);
    $performer = "\"" . quote_replace($performer) . "\"";
  }

  if (!defined($proc_retval))
  {
    $proc_retval = "null";
  }
  else
  {
    chomp($proc_retval);
    if ($proc_retval !~ /^(0$)|([1-9][0-9]*$)/)
    {
      ($errno, $errstr) = (-1, "test_result_insert(...): argument 7, proc_retval ($proc_retval), must be a non-negative integer.");
      return 0;
    }
  }

  if (!defined($logs_url))
  {
    $logs_url = "null";
  }
  else
  {
    chomp($logs_url);
    $logs_url = "\"" . quote_replace($logs_url) . "\"";
  }

  if (!defined($notes))
  {
    $notes = "null";
  }
  else
  {
    chomp($notes);
    $notes = "\"" . quote_replace($notes) . "\"";
  }

  my $insert_sth ;

  my $sql =
<<EOF
insert into test_result 
(test, pass, hc_cluster, start_config, end_config, start_time, end_time, build, branch, performer, proc_retval, logs_url, log_summary, notes)
values 
(${test}, ${pass}, ${cluster}, ${start_config}, ${end_config}, ${start_time}, ${end_time}, ${build}, ${branch}, ${performer}, ${proc_retval}, ${logs_url}, ${log_summary}, ${notes})
EOF
;

  #print("${sql}\n");

  my $sth = $dbh->prepare($sql);
  if (!$sth->execute())
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    return 0;
  }

  return $dbh->{'mysql_insertid'} ;
}

################################################################################
# test_result update
# 
# return 1 on success; 
# 0 otherwise (inqadb::errno and inqadb::errstr are set appropriately.)
#
# start_time and end_time should be specified as values taken from time().
################################################################################
sub test_result_update ($$$$$$$$$$$)
{
  my ($result_id, $test, $pass, $start_time, $end_time, $build, $branch, $performer, $proc_retval, $logs_url, $log_summary, $defects_ref) = @_ ;

  if (!defined($result_id))
  {
    ($errno, $errstr) = (-1, "test_result_update(...): argument 1, result_id, must be non-null");
    return 0;
  }

  my %sets;

  if (defined($test))
  {
    $test = "\"" . quote_replace($test) . "\"";
    $sets{"test"} = $test;
  }

  if (defined($pass))
  {
    chomp($pass);
    if ($pass =~ /^[Yy]([Ee][Ss])?$/ ||
        $pass =~ /^[Tt][Rr][Uu][Ee]$/ ||
        $pass =~ /^1$/)
    {
      $pass = 1;
    }
    else
    {
      $pass = 0;
    }
    $sets{"pass"} = $pass;
  }
  
  if (defined($start_time))
  {
    chomp($start_time);
    $start_time = "'" . time_to_datetime($start_time) . "'";
    $sets{"start_time"} = $start_time;
  }
  
  if (defined($end_time))
  {
    chomp($end_time);
    $end_time = "'" . time_to_datetime($end_time) . "'";
    $sets{"end_time"} = $end_time;
  }

  if (defined($build))
  {
    chomp($build);
    $build = "\"" . quote_replace($build) . "\"";
    $sets{"build"} = $build;
  }

  if (defined($branch))
  {
    chomp($branch);
    $branch = "\"" . quote_replace($branch) . "\"";
    $sets{"branch"} = $branch;
  }

  if (defined($performer))
  {
    chomp($performer);
    $performer = "\"" . quote_replace($performer) . "\"";
    $sets{"performer"} = $performer;
  }

  if (defined($proc_retval))
  {
    chomp($proc_retval);
    if ($proc_retval !~ /^(0$)|([1-9][0-9]*$)/)
    {
      ($errno, $errstr) = (-1, "test_result_insert(...): argument 7, proc_retval ($proc_retval), must be a non-negative integer.");
      return 0;
    }
    $sets{"proc_retval"} = $proc_retval;
  }

  if (defined($logs_url))
  {
    chomp($logs_url);
    $logs_url = "\"" . quote_replace($logs_url) . "\"";
    $sets{"logs_url"} = $logs_url;
  }

  if (defined($log_summary))
  {
    $log_summary = "\"" . quote_replace($log_summary) . "\"";
    $sets{"log_summary"} = $log_summary;
  }

  $dbh->{'AutoCommit'} = 0;
  if ($dbh->{'AutoCommit'})
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    return 0 ;
  }

  my $retval=0;

  my $begin_sql = "begin";
  #print(${begin_sql} . "\n");
  if (!$dbh->do($begin_sql))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    $retval = 0;
    goto DONE;
  }

  my $set_result=0;
  my $update_sql=
<<EOF
update test_result set
EOF
;
  my $first=1;
  foreach my $key (keys(%sets))
  {
    $set_result=1;
    if (!$first)
    {
      $update_sql .= ", ";
    }
    $first = 0;

    my $value = $sets{$key};
    $update_sql .= " ${key}=${value}";
  }
  $update_sql .= " where id=${result_id}";

  #print(${update_sql} . "\n");
  if (!$dbh->do($update_sql))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    $retval = 0;
    goto DONE;
  }

  my $set_bug=0;
  my $delete_bug_sql = 
<<EOF
delete from result_bug where result=${result_id}
EOF
;

  #print(${delete_bug_sql} . "\n");
  if (!$dbh->do($delete_bug_sql))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    $retval = 0;
    goto DONE;
  }

  if (defined($defects_ref) && scalar(@$defects_ref))
  {
    $set_bug=1;
    foreach my $defect (@$defects_ref)
    {
      my $insert_bug_sql =
<<EOF
insert into result_bug (result, bug) values (${result_id}, ${defect})
EOF
;   
      #print(${insert_bug_sql} . "\n");
      if (!$dbh->do($insert_bug_sql))
      {
        ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
        $retval = 0;
        goto DONE;
      }
    }
  }

  my $commit_sql = "commit";
  #print(${commit_sql} . "\n");
  if (!$dbh->do($commit_sql))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    $retval = 0;
    goto DONE;
  }
  $retval=1;

DONE:
  $dbh->{'AutoCommit'} = 1;
  return $retval;
}

################################################################################
# test_result query
#
# where: the where clause filter over test_result
# matrix: only include tests from this matrix
#
# return (status, rows)
################################################################################
sub test_result_query($$$)
{
  my ($where, $matrix, $orderby) = @_;

  my $select = "select t.id, t.test, t.pass, t.hc_cluster, t.start_config, t.end_config, t.start_time, t.end_time, t.build, t.branch, t.performer, t.proc_retval, t.logs_url, t.log_summary, t.notes from test_result t";

  if (defined($matrix))
  {
    $select .= ", matrix_member m";
  }

  if (defined($where) || defined($matrix))
  {
    $select .= " where";
  }

  if (defined($where))
  {
    $where =~ s/(^|(\s+))id(\s*(=|<|<=|>=))/$1t.id$3/g;
    $where =~ s/(^|(\s+))test(\s*(=|<|<=|>=|like))/$1t.test$3/g;
    $where =~ s/(^|(\s+))pass(\s*(=|<|<=|>=))/$1t.pass$3/g;
    $where =~ s/(^|(\s+))cluster(\s*(=|<|<=|>=))/$1t.hc_cluster$3/g;
    $where =~ s/(^|(\s+))log_summary(\s*(=|<|<=|>=))/$1t.log_summary$3/g;
    $where =~ s/(^|(\s+))start_config(\s*(=|<|<=|>=))/$1t.start_config$3/g;
    $where =~ s/(^|(\s+))end_config(\s*(=|<|<=|>=))/$1t.end_config$3/g;
    $where =~ s/(^|(\s+))start_time(\s*(=|<|<=|>=))/$1t.start_time$3/g;
    $where =~ s/(^|(\s+))end_time(\s*(=|<|<=|>=))/$1t.end_time$3/g;
    $where =~ s/(^|(\s+))build(\s*(=|<|<=|>=|like))/$1t.build$3/g;
    $where =~ s/(^|(\s+))build(\s*(=|<|<=|>=|like))/$1t.branch$3/g;
    $where =~ s/(^|(\s+))performer(\s*(=|<|<=|>=|like))/$1t.performer$3/g;
    $where =~ s/(^|(\s+))proc_retval(\s*(=|<|<=|>=))/$1t.proc_retval$3/g;

    $select .= " ${where}";
  }

  if (defined($matrix))
  {
    if (defined($where))
    {
      $select .= " and";
    }

    $matrix = "\"" . quote_replace($matrix) . "\"";
    $select .= " m.matrix = ${matrix} and m.test = t.test";
  }

  if (defined($orderby))
  {
    $orderby =~ s/(^|(\s+))id(\s*(=|<|<=|>=))/$1t.id$3/g;
    $orderby =~ s/(^|(\s+))test(\s*(=|<|<=|>=|like))/$1t.test$3/g;
    $orderby =~ s/(^|(\s+))pass(\s*(=|<|<=|>=))/$1t.pass$3/g;
    $orderby =~ s/(^|(\s+))cluster(\s*(=|<|<=|>=))/$1t.hc_cluster$3/g;
    $orderby =~ s/(^|(\s+))start_time(\s*(=|<|<=|>=))/$1t.start_time$3/g;
    $orderby =~ s/(^|(\s+))end_time(\s*(=|<|<=|>=))/$1t.end_time$3/g;
    $orderby =~ s/(^|(\s+))build(\s*(=|<|<=|>=|like))/$1t.build$3/g;
    $orderby =~ s/(^|(\s+))build(\s*(=|<|<=|>=|like))/$1t.branch$3/g;
    $orderby =~ s/(^|(\s+))performer(\s*(=|<|<=|>=|like))/$1t.performer$3/g;
    $orderby =~ s/(^|(\s+))proc_retval(\s*(=|<|<=|>=))/$1t.proc_retval$3/g;

    $select .= " order by ${orderby}";
  }
  else
  {
    $select .= " order by t.id desc";
  }

  #print("${select}\n");

  my $rows = $dbh->selectall_arrayref($select) ;
  if (!defined($rows))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    return (0, undef);
  }
  return (1, $rows);
}

################################################################################
# test_result prev_result
# 
# return 1 on success; 
# 0 otherwise (inqadb::errno and inqadb::errstr are set appropriately.)
#
################################################################################
sub prev_result($$$)
{
  my ($id, $test, $branch) = @_ ;

  my $select = "select t.id, t.pass from test_result t where t.test = \"$test\" and t.branch = \"$branch\" and t.id < $id order by id desc";
  my $rows = $dbh->selectall_arrayref($select) ;
  if (!defined($rows))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    return (0, undef);
  }
  return (1, $rows);
}

################################################################################
# result_metric insert
# 
# return 1 on success; 0, otherwise (errno and errstr are set appropriately).
#
################################################################################
sub result_metric_insert ($$$)
{
  my ($result, $name, $value) = @_;

  $name = "\"" . quote_replace($name) . "\"";
  $value = "\"" . quote_replace($value) . "\"";

  my $insert = "insert into result_metric (result, name, value) values (${result}, ${name}, ${value})";
  #print ("${insert}\n");
  if (!$dbh->do($insert))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    return 0 ;
  }
  return 1;
}

################################################################################
# result_bug insert
# 
# return 1 on success; 0, otherwise (errno and errstr are set appropriately).
#
################################################################################
sub result_bug_insert ($$)
{
  my ($result, $bug) = @_;

  $bug = "\"" . quote_replace($bug) . "\"";

  my $insert = "insert into result_bug (result, bug) values (${result}, ${bug})";
  #print ("${insert}\n");
  if (!$dbh->do($insert))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    return 0 ;
  }
  return 1;
}

################################################################################
# result_bug query
#
# where: the where clause filter over result_bug
#
# return (status, rows)
################################################################################
sub result_bug_query($)
{
  my ($where) = @_;

  my $select = "select result, bug from result_bug";

  if (defined($where))
  {
    $select .= " where ${where}";
  }

  my $rows = $dbh->selectall_arrayref($select) ;
  if (!defined($rows))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    return (0, undef);
  }
  return (1, $rows);
}

################################################################################
# result_metric query
#
# where: the where clause filter over result_metric
#
# return (status, rows)
################################################################################
sub result_metric_query($)
{
  my ($where) = @_;

  my $select = "select name, value from result_metric";

  if (defined($where))
  {
    $select .= " where ${where}";
  }

  my $rows = $dbh->selectall_arrayref($select) ;
  if (!defined($rows))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    return (0, undef);
  }
  return (1, $rows);
}

################################################################################
# file_to_matrix
################################################################################
sub file_to_matrix($$)
{
  my ($matrix_file, $matrix_name) = @_;

  if (!open(FILE, "<${matrix_file}"))
  {
    ($errno, $errstr) = (-1, "unable to open file: ${matrix_file}") ;
    return 0 ;
  }

  $matrix_name = "\"" . quote_replace($matrix_name) . "\"" ;

  my $set_sth ;

  my $set =
<<EOF
begin work
EOF
;

  if (!$dbh->do($set))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    return 0 ;
  }

  $set =
<<EOF
delete from matrix_member where matrix=${matrix_name}
EOF
;

  if (!$dbh->do($set))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    return 0 ;
  }

  while (<FILE>)
  {
    my $line = $_;
    chomp($line);

    if (!$line || $line =~ /^\s*#/ || $line =~ /^\s*$/)
    {
      next;
    }

    ($line) = split(/#/, $line);
    
    my ($leading_space, $trailing_space_or_comment);

    ($leading_space, $line, $trailing_space_or_comment) = ($line =~ /^(\s*)(.+\S)(\s*)$/);

    my $test = "\"" . quote_replace($line) . "\"";

    $set =
<<EOF
insert into matrix_member (matrix, test) values (${matrix_name}, ${test})
EOF
;

    if (!$dbh->do($set))
    {
      ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
      return 0 ;
    }

  }

  $set =
<<EOF
commit
EOF
;

  if (!$dbh->do($set))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    return 0 ;
  }


  print("${set}\n");
  if (!$dbh->do($set))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    return 0 ;
  }

  return 1 ;
}

################################################################################
# matrix_member query
#
# where: the where clause filter over matrix_member
#
# return (status, rows)
################################################################################
sub matrix_member_query($)
{
  my ($where) = @_;

  my $select = "select matrix, test from matrix_member";

  if (defined($where))
  {
    $select .= " where ${where}";
  }

  my $rows = $dbh->selectall_arrayref($select) ;
  if (!defined($rows))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    return (0, undef);
  }
  return (1, $rows);
}

################################################################################
# matrix query
#
# where: the where clause filter over matrix_member
#
# return (status, rows)
################################################################################
sub matrix_query($)
{
  my ($where) = @_;

  my $select = "select distinct matrix from matrix_member";

  if (defined($where))
  {
    $select .= " where ${where}";
  }

  my $rows = $dbh->selectall_arrayref($select) ;
  if (!defined($rows))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
    return (0, undef);
  }
  return (1, $rows);
}


################################################################################
# testproc
################################################################################
sub testproc
{
  my ($build, $matrix) = @_;

  my $call =
<<EOF
testproc "$build", "$matrix"
EOF
;

  print "->prepare\n";
  my $sth = $dbh->prepare($call);
  print "prepare->\n";
  print "->execute\n";
  $sth->execute();
  print "execute->\n";
  print "->selectall\n";
  my $rows = $sth->fetchall_arrayref() ;
  print "selectall->\n";
  if (!defined($rows))
  {
    ($errno, $errstr) = ($dbh->{'mysql_errno'}, $dbh->{'mysql_error'}) ;
  }
  foreach my $row (@$rows)
  {
    my ($col1, $col2) = @$row ;
    print "${col1}, ${col2}\n";
  }

  return 1;
}

# end of module
return 1 ;

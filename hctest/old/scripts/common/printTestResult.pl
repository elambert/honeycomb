#!/usr/bin/perl -w
#
# $Id: printTestResult.pl 10858 2007-05-19 03:03:41Z bberndt $
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

use strict;
use Getopt::Long qw(:config no_ignore_case bundling);


my $usage =
<<EOF
NAME

    $0 - print a test result

SYNOPSIS

    $0 print [options] <test>

DESCRIPTION

    print a test result.  <test> is the string name of a test.

OPTIONS

    -R, --result <1=pass, 0=fail>
        the result of the test.  1 indicates PASS, 0 FAILURE.
        defaults to null, or "unknown".

    -s, --start <YYYY-MM-DD HH:MM:SS>
        the start time of the test.  defaults to now.

    -e, --end <YYYY-MM-DD HH:MM:SS>
        the end time of the test.  defaults to now.

    -b, --build <build>
        a string to describe the software being run.
        defaults to "dev".

    -p, --performer <performer>
        the user associated with this test result.
        defaults to the value of USER set in the environment.

    -r, --retval <process return value>
        if the test represents a process which ran, this specifies the return
        value of the test process.

    -l, --logs <logs url>
        a url which points to the location of the test logs.

    -n, --notes <string | file>
        small notes to be included with this result.

    -m, --metric "<name>=<value>"
        associate a metric with the test result.

    -d, --defect <defect>
        associate this defect with this test result.  multiple -d options
        may be specified.

    -N, --new-lines
        use newlines.  default is to print the result on one line, which is
        required for grep'ing test results in log files.

    -h, --help
        print this message.

EOF
;

my $test = undef;
my $result = undef;
my $start_time = undef;
my $end_time = undef;
my $build = undef;
my $performer = undef;
my $proc_retval = undef;
my $logs_url = undef;
my $notes = undef;
my @metrics = ();
my @defects = ();
my $new_lines = 0;
my $help=undef;

if (!GetOptions("R|result=s" => \$result,
                "s|start=s" => \$start_time,
                "e|end=s" => \$end_time,
                "b|build=s" => \$build,
                "p|performer=s" => \$performer,
                "r|retval=i" => \$proc_retval,
                "l|logs=s" => \$logs_url,
                "n|notes=s" => \$notes,
                "m|metric=s" => \@metrics,
                "d|defect=s" => \@defects,
                "N|new-lines" => \$new_lines,
                "h|help" => \$help))
{
  print "${usage}\n";
  exit 1;
}

if (defined($help))
{
  print "${usage}\n";
  exit 0;
}

my $test_name = shift(@ARGV);

if (!defined($test_name))
{
  print("invalid args: must specify a test\n");
  exit 1;
}

my $metric_tags = undef;
foreach my $metric (@metrics)
{

  if ($metric !~ /([^=]+)=(.*)$/)
  {
    print("invalid metric spec: ${metric}\n");
    exit 1;
  }
  else
  {
    my ($name, $value) = ($1, $2);
    $metric_tags .= "<metric name=\"${name}\">${value}</metric>";
    $metric_tags .= "\n" if $new_lines;
  }
}

if (defined($notes))
{ 
  if (-f $notes)
  {
    if (!open(NOTES,"<${notes}"))
    {
      print("error: unable to open notes file, ${notes}\n");     
      exit(1);
    }
    else
    {
      my @_notes = <NOTES>;
      if ($new_lines)
      {
        $notes = join("",@_notes);
      }
      else
      {
        $notes = "";
        foreach my $line (@_notes)
        {
          chomp($line);
          $notes .= "${line}\t";
        }
      }
    }
  }
}

my $defect_tags = undef;
foreach my $defect (@defects)
{
  $defect_tags .= "<bug>${defect}</bug>";
  $defect_tags .= "\n" if $new_lines;
}


if (!defined($start_time))
{
  my ($sec,$min,$hour,$mday,$mon,$year,$wday,$yday) = localtime(time());
  $mon++;
  $year += 1900;
  $start_time = "${year}-${mon}-${mday} ${hour}:${min}:${sec}";
}

if (!defined($end_time))
{
  my ($sec,$min,$hour,$mday,$mon,$year,$wday,$yday) = localtime(time());
  $mon++;
  $year += 1900;
  $end_time = "${year}-${mon}-${mday} ${hour}:${min}:${sec}";
}

if (!defined($build))
{
  $build="dev";
}

if (!defined($performer))
{
  $performer = `whoami`;
  chomp($performer);
}


print("<test_result");
  print("\n") if ($new_lines);
  print(" test_name=\"${test_name}\"");
  print("\n") if ($new_lines);
  if (defined($result))
  {
    print(" result=\"${result}\"");
    print("\n") if ($new_lines);
  }
  if (defined($start_time))
  {
    print(" start_time=\"${start_time}\"");
    print("\n") if ($new_lines);
  }
  if (defined($end_time))
  {
    print(" end_time=\"${end_time}\"");
    print("\n") if ($new_lines);
  }
  if (defined($build))
  {
    print(" build=\"${build}\"");
    print("\n") if ($new_lines);
  }
  if (defined($performer))
  {
    print(" performer=\"${performer}\"");
    print("\n") if ($new_lines);
  }
  if (defined($proc_retval))
  {
    print(" proc_retval=\"${proc_retval}\"");
    print("\n") if ($new_lines);
  }
  if (defined($logs_url))
  {
    print(" logs_url=\"${logs_url}\"");
    print("\n") if ($new_lines);
  }
print(">");
print("\n") if ($new_lines);

  if (defined($defect_tags))
  {
    print($defect_tags);
    print("\n") if ($new_lines);
  }
  if (defined($metric_tags))
  {
    print($metric_tags);
    print("\n") if ($new_lines);
  }
  if (defined($notes))
  {
    print("<notes>");
    print("\n") if ($new_lines);
    print("${notes}");
    print("\n") if ($new_lines);
    print("</notes>");
    print("\n") if ($new_lines);
  }

print("</test_result>");
print("\n");

exit(0);

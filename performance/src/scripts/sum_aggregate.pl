#!/usr/bin/perl
#
# $Id: sum_aggregate.pl 10845 2007-05-19 02:31:46Z bberndt $
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

$inputfile = $ARGV[0];

open(IFILE,$inputfile);
@ilines = <IFILE>;
close(IFILE);

$opscount = 0;
$bytescount = 0;
$opsrate=0;
$datarate=0;

for ($i=0; $i <= $#ilines; $i++) {
  $_ = $ilines[$i];

  if (/(\d+.\d+)\sops\/sec/){
    if ($unit eq "ops/min") {
      $opsmin = $1*60;
      $opsrate += $opsmin;
    }
    else {
      $opsrate += $1;
      $unit = "ops/sec";
    }
  }
  elsif (/(\d+.\d+)\sops\/min/) {
    if ($unit eq "ops/sec") {
      $opsec = $1/60;
      $opsrate += $opsec;
    }
    else {
      $opsrate += $1;
      $unit = "ops/min";
    }
  }
  elsif (/(\d+)\sops/) {
    $opscount += $1;
  }
  elsif (/(\d+)\sbytes/) {
    $bytescount += $1;
  }
  elsif (/(\d+.\d+)\sKB\/s/) {
    if ($dataunit eq "MB/sec") {
      $mbsec = $1/1000;
      $datarate += $mbsec;
    }
    else {
      $datarate += $1;
      $dataunit = "KB/sec";
    }
  }
  elsif (/(\d+.\d+)\sMB\/s/) {
    if ($dataunit eq "KB/sec") {
      $kbsec = $1*1000;
      $datarate += $kbsec;
    }
    else {
      $datarate += $1;
      $dataunit = "MB/sec";
    }
  }
}


print STDOUT "Total ops: $opscount\n";
print STDOUT "Total bytes: $bytescount\n";
print STDOUT "Total opsrate: $opsrate $unit\n";
print STDOUT "Total datarate: $datarate $dataunit\n";


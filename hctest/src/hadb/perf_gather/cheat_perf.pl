#!/usr/bin/perl
#
# $Id: cheat_perf.pl 10858 2007-05-19 03:03:41Z bberndt $
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

$TIME_TO_SLEEP = 10;

sub getCount() {
  #my @output = `echo 'select count(*) from T98C20F;' | ssh hcb101 /opt/SUNWhadb/4/bin/clusql localhost:15005 system+superduper 2>/dev/null`;
  #my @fields = split(/ /, $output[0]);
  #chomp($output[3]);
  #$output[3]=~s/[ ]*([^ ]+)/\1/;
  #return($output[3]);
  return(0);
}

sub getSize() {
  my @output = `ssh hcb101 'echo "admin" | /opt/SUNWhadb/4/bin/hadbm deviceinfo honeycomb`;
  my $result = 0;

  for (my $i=1; $i<@output; $i++) {
    my (@fields) = split(/[ ]+/, $output[$i]);
    $result += $fields[1]-$fields[2];
  }

  return($result);
}

$prev = getCount();
$starttime = time();
$iter = 1;

while (1) {
  sleep ($TIME_TO_SLEEP*$iter+$starttime-time());
  $current = getCount();
  $perf = ($current-$prev)/$TIME_TO_SLEEP;
  print (($iter*$TIME_TO_SLEEP)."\t$current\t$perf\t".getSize()."\n");
  $prev = $current;
  $iter++;
}

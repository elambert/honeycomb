#!/usr/bin/perl -w
#
# $Id: calc_rate.pl 10858 2007-05-19 03:03:41Z bberndt $
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
#
# use strict;
#

# are we measuring stores or retrieves?
my $action;

if ($ARGV[0] =~ "STORERESULTS") {
  $action = "stored";
} elsif ($ARGV[0] =~ "RETRIEVERESULTS") {
  $action = "retrieved";
} else {
  die "unknown node";
}

open(FILE, "$ARGV[0] | grep === | awk '{print \$2}'|") || die "Cant run $ARGV[0]";

my $total = 0;
my $maxsecs = 0;

while(<FILE>) {
  chomp;
  my($host, $file) = split(/:/);
  my $cmd = "ssh $host 'tail -30 $file | grep $action | tail -1'";
  my $line = `$cmd`;
  my @linea = split(/ /, $line);
  my $bytes = $linea[8];
  my $secs = $linea[11] / 1000;
  $bytes =~ s/,//g;
  $total += $bytes;
  if($secs > $maxsecs) {
    $maxsecs = $secs;
  }
}

my $gigs = $total / 1024 / 1024 / 1024;
my $hours = $maxsecs / 60 / 60;
my $gb_per_hr = $gigs / $hours;
my $daily = $gb_per_hr * 24;
print "$action $gigs gigabytes in $hours hours, a rate of $gb_per_hr gigabytes per hour or $daily gigabytes per day.\n";


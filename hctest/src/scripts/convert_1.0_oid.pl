#!/usr/bin/perl -w
#
# $Id: convert_1.0_oid.pl 10898 2007-05-23 23:49:34Z tt107269 $
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

# Script to take the output of EMI Stress/Performance test from a 1.0
# system and convert the 1.0 OIDs to 1.1 OIDs, while preserving the 
# EMI Stress output format.
# Intended for use in 1.0->1.1 upgrade testing.

# Usage: ./convert_1.0_oid.pl <input file> <output file>

use strict;

my $infile = $ARGV[0];
my $outfile = $ARGV[1];

open (IFILE, $infile);
open (OFILE, ">>$outfile");

while ( <IFILE> ) {
  if (/([0-9]+)\s([0-9]+)\s(\w+)\s(Stress\-\w+\.\w+\-[0-9]+\.[0-9]+)\s([0-9]+)\s(\w+)\s(\w+)/) {
   my $start = $1;
   my $end = $2;
   my $oid = $3;
   my $uid = $4;
   my $size = $5;
   my $op = $6;
   my $status = $7;
 
   my $oidout = `ConvertOid $3`;

   my $newoid;

   $_ = $oidout;
   if (/External:\s(\w+)/) {
       $newoid = $1;
    }

    print OFILE "$start $end $newoid $uid $size $op $status\n";
  }
}
close (IFILE);
close(OFILE);

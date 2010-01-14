#! /usr/bin/perl

#
# $Id: XMLStoreExtract.pl 10855 2007-05-19 02:54:08Z bberndt $
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

$INPUT_FILE=$ARGV[0];
if (!$INPUT_FILE) {
  $INPUT_FILE="StoreHandlerPerf.txt";
  print STDERR "Using the default input file <$INPUT_FILE>\n";
}

$STEP=2000;
$ALPHA=0.3;

open(INPUT, $INPUT_FILE) || die("Couldn't open $INPUT_FILE");

$first = 1;
$processed = 0;
$previous_open = 0;
$previous_write = 0;
$previous_close = 0;
$open = 0;
$write = 0;
$close = 0;

$ffscreate = 0;
$oacommit = 0;

LOOP: while ($line = <INPUT>) {
    if (substr($line,0,1) eq "#") {
      next LOOP;
    }
    chomp($line);
    @fields = split(/ /,$line);
#    if ($fields[2] < 1000000) {
#      next LOOP;
#    }
    $open += $fields[4]/$STEP;
    $write += $fields[5]/$STEP;
    $close += $fields[6]/$STEP;
    $ffscreate += $fields[7]/$STEP;
    $oacommit += ($fields[7]+$fields[8])/$STEP;

    $processed++;
    if ($processed == $STEP) {
      if ($first == 1) {
        $previous_open = $open;
        $previous_write = $write;
        $previous_close = $close;
        $previous_ffscreate = $ffscreate;
        $previous_oacommit = $oacommit;
        $first = 0;
      } else {
        $open_output = $ALPHA*($open) + (1-$ALPHA)*$previous_open;
        $write_output = $ALPHA*($write) + (1-$ALPHA)*$previous_write;
        $close_output = $ALPHA*($close) + (1-$ALPHA)*$previous_close;
        $ffscreate_output = $ALPHA*($ffscreate) + (1-$ALPHA)*$previous_ffscreate;
        $oacommit_output = $ALPHA*($oacommit) + (1-$ALPHA)*$previous_oacommit;
        $previous_open = $open_output;
        $previous_write = $write_output;
        $previous_close = $close_output;
        $previous_ffscreate = $ffscreate;
        $previous_oacommit = $oacommit;

        print $fields[0]." $open_output $write_output $close_output $ffscreate_output $oacommit_output\n";
      }

      $open=0;
      $write=0;
      $close=0;
      $ffscreate=0;
      $oacommit=0;
      $processed = 0;
    }
}

close(INPUT);

#!/usr/bin/perl
#
# $Id$
#
# AdvQuery Tests. Parses and summarizes performance statistics
# for an iteration.
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

$infile = $ARGV[0];
$queryhdr = $ARGV[1];
$queryhdr2 = $ARGV[2];
$dbfull = $ARGV[3];
$dbfullpercent = $ARGV[4];
$dbobjects = $ARGV[5];

open (IFILE,$infile);

$totalres=0;
$numres=0;
$numerrs=0;
$totalerrs=0;
$numrecs=0;
$totalrecs=0;
$numtime=0;
$totaltime=0;
$numlat=0;
$totallat=0;
$numfcalls=0;
$totalftime=0;
$totalfcalls=0;

while ( <IFILE> ) {
    if (/Average\sresults\/sec:\s(\d+.\d+)/) {
	$numres++;
	$totalres+=$1;
    }
    if (/Total\serrors:\s(\d+)/) {
        $numerrs++;
        $totalerrs+=$1;
    }
    if (/Total\sresults:\s(\d+)\,\sTotal\stime:\s(\d+)ms/) {
#    if (/Total\sresults:\s(\d+)\,/) {
        $numrecs++;
        $totalrecs+=$1;
        $numtime++;
        $totaltime+=$2;
    }
    if (/First\squery\scall\slatency\s\(average\):\s(\d+.\d+)\sms/) {
       $numlat++;
       $totallat+=$1;
    }
    if (/Total\sfirst\squery\scalls:\s(\d+)\,\sTotal\stime:\s(\d+)ms/) {
       $numfcalls++;
       $totalfcalls+=$1;
       $totalftime+=$2;
    }
}

$averes=0;
$aveerrs=0;
$averecs=0;
$avetime=0;
$avelat=0;
$avefcall=0;
$aveftime=0;

if ($numerrs > 0) {
    $aveerrs = $totalerrs/$numerrs;
}

if ($numres > 0) {
    $averes = $totalres/$numres;
}

if ($numrecs > 0) {
    $averecs = $totalrecs/$numrecs;
}

if ($numtime > 0) {
    $avetime = $totaltime/$numtime;
}

if ($numlat > 0) {
    $avelat = $totallat/$numlat;
}

if ($numfcalls > 0) {
    $avefcall = $totalfcalls/$numfcalls;
    $aveftime = $totalftime/$numfcalls;
}

print STDOUT "$queryhdr2 $dbobjects $dbfull  $dbfullpercent $averes  $averecs  $averrs  $avetime  $avelat  $avefcall  $aveftime\n";

#!/usr/bin/perl
#
# $Id$
#
# AdvQuery Tests. Parses performance statistics for an individual client run.
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
$totalqueries=0;
$totalqueriesvalidated=0;

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
    if (/Total\sQueries\sValidated:\s(\d+)/) {
       $totalqueriesvalidated+=$1
    }
    if (/Total\sQueries:\s(\d+)/) {
       $totalqueries+=$1
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

if ($numerrs > 0) {
    $ave = $totalerrs/$numerrs;
    print STDOUT "$queryhdr $queryhdr2 Average errors per thread: $ave\n";
}

print STDOUT "$queryhdr $queryhdr2 Total errors: $totalerrs\n";

if ($numres > 0) {
    $ave = $totalres/$numres;
    print STDOUT "$queryhdr $queryhdr2 Average results/sec per thread: $ave\n";
}

if ($numrecs > 0) {
    $ave = $totalrecs/$numrecs;
    print STDOUT "$queryhdr $queryhdr2 Total results: $ave\n";
}

if ($numtime > 0) {
    $ave = $totaltime/$numtime;
    print STDOUT "$queryhdr $queryhdr2 Total time: $ave\n";
}

if ($numlat > 0) {
    $ave = $totallat/$numlat;
    print STDOUT "$queryhdr $queryhdr2 First query call latency: $ave\n";
}

if ($numfcalls > 0) {
    $ave = $totalfcalls/$numfcalls;
    print STDOUT "$queryhdr $queryhdr2 First query calls: $ave\n";
    $ave = $totalftime/$numfcalls;
    print STDOUT "$queryhdr $queryhdr2 First query call time: $ave\n";
}

print STDOUT "$queryhdr $queryhdr2 Total queries: $totalqueries\n";

print STDOUT "$queryhdr $queryhdr2 Total queries validated: $totalqueriesvalidated\n";




#!/usr/bin/perl
#
# $Id: compile_ss.pl 10845 2007-05-19 02:31:46Z bberndt $
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
$resfile = $ARGV[1];

open(IFILE,$inputfile);

$opscount=0;
$bytescount=0;

$aggopsratetotal=0;
$aggbytesratetotal=0;

$indtotal=0;
$indavetotal=0;
$numaves=0;
$minops=999999999999999999999;
$maxops=-1;
$indsquarestotal=0;

$indtotalbytes=0;
$indavetotalbytes=0;
$numavebytes=0;
$minbytes=9999999999999999999999;
$maxbytes=-1;
$bytesquarestotal=0;

$dataunit="default";
$bytes="false";

while ( <IFILE> ) {
    if (/Total:\s+(\d+)\sops/) {
	$opscount = $1;
    }
    elsif (/Total:\s+(\d+)\sbytes/) {
	$bytescount = $1;
    }
    elsif (/Total:\s+(\d+)\sms/) {
	$timecount = $1;
    }
    elsif (/Aggregate:\s+(\d+.\d+)\sops\/sec/) {
	$aggopsratetotal = $1;
    }
    elsif (/Aggregate:\s+(\d+.\d+)\s(([KMG]B|bytes)\/s)/) {
	if ($bytes eq "false") {
	    $bytes="true";
	}
	$aggbytesratetotal = convert($1,$2);
    }
    elsif (/Individual\sAve:\s+(\d+.\d+)\sms/) {
	$indavetotal = $1;
    }
    elsif (/Individual\sMin:\s+(\d+)\sms/) {
	$minops = $1;
    }
    elsif (/Individual\sMax:\s+(\d+)\sms/) {
	$maxops = $1;
    }
    elsif (/Individual\sStdDev:\s+(\d+.\d+)\sms/) {
	$indstddev = $1;
    }
    elsif (/Individual\sAve:\s+(\d+.\d+)\s(([KMG]B|bytes)\/s)/) {
	$indavetotalbytes = convert($1,$2);
    }
    elsif (/Individual\sMin:\s+(\d+.\d+)\s(([KMG]B|bytes)\/s)/) {
	$minbytes =convert($1,$2);
    }
    elsif (/Individual\sMax:\s+(\d+.\d+)\s(([KMG]B|bytes)\/s)/) {
	$maxbytes = convert($1,$2);
    }
    elsif (/Individual\sStdDev:\s+(\d+.\d+)\s(([KMG]B|bytes)\/s)/) {
	$bytestddev = convert($1,$2);
    }
}

open (OFILE,">>$resfile");

print OFILE "Average opsrate: $aggopsratetotal ops/sec\n";
print STDOUT "$aggopsratetotal ops/sec";

if ($bytes eq "true") {
    print OFILE "Average datarate: $aggbytesratetotal $dataunit\n";
    print STDOUT " $aggbytesratetotal $dataunit";
}
print STDOUT "\n";

print OFILE "Average ops duration: $indavetotal ms\n";
print OFILE "Min ops duration: $minops ms\n";
print OFILE "Max ops duration: $maxops ms\n";
print OFILE "Standard deviation ops duration: $indstddev ms\n";

if ($bytes eq "true") {
    print OFILE "Average ops datarate: $indavetotalbytes $dataunit\n";
    print OFILE "Min ops datarate: $minbytes $dataunit\n";
    print OFILE "Max ops datarate: $maxbytes $dataunit\n";
    print OFILE "Standard deviation ops datarate: $bytestddev $dataunit\n";
}

sub convert {
    $rate = $1;
    $unit = $2;
    if ($dataunit eq $unit) {
	return $rate;
    }
    elsif ($dataunit eq "default") {
	$dataunit = $unit;
	return $rate;
    }
    else {
	if ($dataunit eq "bytes/s") {
	    if ($unit eq "KB/s") {
		$newrate = $rate*1000;
		return $newrate;
	    }
	    elsif ($unit eq "MB/s") {
		$newrate = $rate*1000000;
		return $newrate;
	    }
	    elsif ($unit eq "GB/s") {
		$newrate = $rate*1000000000;
		return $newrate;
	    }
	    else {
		print OFILE "Unknown format $unit\n";
		return $rate;
	    }
	}
	elsif ($dataunit eq "KB/s") {
	    if ($unit eq "bytes/s") {
		$newrate = $rate/1000;
		return $newrate;
	    }
	    elsif ($unit eq "MB/s") {
		$newrate = $rate*1000;
		return $newrate;
	    }
	    elsif ($unit eq "GB/s") {
		$newrate = $rate*1000000;
		return $newrate;
	    }
	    else {
		print OFILE "Unknown format $unit\n";
		return $rate;
	    }
	}
	elsif ($dataunit eq "MB/s") {
	    if ($unit eq "bytes/s") {
		$newrate = $rate/1000000;
		return $newrate;
	    }
	    elsif ($unit eq "KB/s") {
		$newrate = $rate/1000;
		return $newrate;
	    }
	    elsif ($unit eq "GB/s") {
		$newrate = $rate*1000;
		return $newrate;
	    }
	    else {
		print OFILE "Unknown format $unit\n";
		return $rate;
	    }
	}
	elsif ($dataunit eq "GB/s") {
	    if ($unit eq "bytes/s") {
		$newrate = $rate/1000000000;
		return $newrate;
	    }
	    elsif ($unit eq "KB/s") {
		$newrate = $rate/1000000;
		return $newrate;
	    }
	    elsif ($unit eq "MB/s") {
		$newrate = $rate/1000;
		return $newrate;
	    }
	    else {
		print OFILE "Unknown format $unit\n";
		return $rate;
	    }
	}
	else {
	    print OFILE "Data unit unknown format $dataunit\n";
	}
    }
}

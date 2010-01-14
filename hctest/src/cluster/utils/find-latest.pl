#!/bin/perl
#
# $Id: find-latest.pl 10858 2007-05-19 03:03:41Z bberndt $
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
#
# need to take . and replace it with arg and substring
#
#
#(16:00:07) Daria: continue if $foo =~ /HELLO/g;
package main;
my($recent)=0;
my($winner)="";
my($restrictTo) = $ARGV[1];
my $buildpath = $ARGV[0]."/".$ARGV[1];

&dodir($buildpath);
printf("$winner\n");
exit 0;
 

sub dodir {    
    my($f,$p);
    my ($cwd) = @_;
    if (! opendir(DIR,$cwd)) {
        print STDERR  "Can't read \"$cwd\"\n";
        return;
    }
    my(@files) = readdir(DIR);
    
#    if anything in @files matches $restrictTo, skip considering $cwd
    my($found)=0;
    foreach  (@files) {
        my($cur)=$_;
        if($cur =~ /$restrictTo/g) {
            $found=1;
        }
    }

    

    if($found != 1) {        
        my($tvar)=(stat($cwd))[9];
        if ( ($tvar > $recent) || $recent==0) {
#        if($cwd =~ /QA/g) {
            if($cwd =~ /$restrictTo/g) {
                $winner=$cwd;
                $recent=$tvar;
            }
        }
    }

    closedir(DIR);
    for $f (@files) {	# Run thru the files in the directory.
        $p = "$cwd/$f";	# Path relative to $cwd.
        next if ($f eq '.' || $f eq '..');
        if($f =~ /$restrictTo/g) {
            if (-d $p) {
                &dodir($p)
                }
        }
    }
}

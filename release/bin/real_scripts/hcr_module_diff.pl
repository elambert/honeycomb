#!/usr/bin/perl

#
# $Id: hcr_module_diff.pl 10849 2007-05-19 02:44:57Z bberndt $
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

use HCR::Init;
use HCR::Modules;
use HCR::Releases;
use Getopt::Std;

HCR::Init::check();

$modules = HCR::Modules::new();
$releases = HCR::Releases::new();

sub usage {
    die "\
Valid command line options are :\
-m <module name> to specify the module to be diffed
-r <release name> to specify in which release context the module should be diffed\
\n";
}

%opts = {};
getopts("m:r:", \%opts);

if (!$opts{m}) {
    print "A module has to be specified\n";
    usage();
}

if (!$opts{r}) {
    print "A release has to be specified\n";
    usage();
}

$release = $releases->get_release($opts{r});
if (!$release) {
    print "\
Release $opts{r} is unknown\
\n";
    $releases->print_available();
    die "\n";
}

$modules->get_diff($opts{m}, $release, 0);

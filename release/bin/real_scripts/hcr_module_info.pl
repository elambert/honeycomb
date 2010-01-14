#!/usr/bin/perl

#
# $Id: hcr_module_info.pl 10849 2007-05-19 02:44:57Z bberndt $
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
use Getopt::Std;

HCR::Init::check();

$modules = HCR::Modules::new();

%opts = {};
getopts("m:", \%opts);

if (!$opts{m}) {

    # All modules
    print_all_modules($modules);

    print "\nRun hcr_module_info.pl -m <module name> for more info on a given module\n";

} else {

    $version = $modules->get_version($opts{m}) || die "\
Module $opts{m} is unknown\
\
With no parameter, gives info on all modules\
With 1 parameter, gives info on a specific module\
\n";

    # Info on a specific module
    print "Information on module <$opts{m}>\n\n";
    
    print "List of available versions :\n";
    $version->print();
}

print "\n";

sub print_all_modules {
    my $modules = shift;
    my $module;

    print "List of available modules\n\n";

    my @list = $modules->list();
    foreach $module (@list) {
	my $version = $modules->get_version($module);
	print "$module (latest version ".$version->release_candidate()->toString().")\n";
    }
}

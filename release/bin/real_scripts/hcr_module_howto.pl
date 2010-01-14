#!/usr/bin/perl

#
# $Id: hcr_module_howto.pl 10849 2007-05-19 02:44:57Z bberndt $
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
-m <module name> to specify the module to work on
-r <release name> to specify in which release context you want to work\
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

$mod_version = $modules->get_version($opts{m}) ||
    die "\
Module $opts{m} is unknown\
\n";

$release = $releases->get_release($opts{r});
if (!$release) {
    print "\
Release $opts{r} is unknown\
\n";
    $releases->print_available();
    die "\n";
}

$last_rel_version = $release->{version}->release_candidate()->toString();
$module_def_version = $release->get_module_version($opts{m});

$module_svn_url = $hcr_svn_trunk."/".$opts{m};
if ($module_def_version) {
    $module_svn_url = $hcr_svn_dev."/".$opts{m}."-".$module_def_version;
}

$module_rc_version = $mod_version->fork($module_def_version)->toString();
$release_rc_version = $release->{version}->fork()->toString();

print "\
Here are the instructions to work on the $opts{m} module\
in the context of release $opts{r}\
\
1. The last published version of release $opts{r} is $last_rel_version\
   You can get the bits from :
   $hcr_releases/$opts{r}/$opts{r}-$last_rel_version
   (Simply do a cp -RL <above path> <dest>)
\
2. Replace the $opts{m} directory with the working version of\
   module $opts{m}.\
   To do that, checkout the working copy from :\
   svn co $module_svn_url $opts{m}\
\
3. Do your changes in the checked out copy.\
\
4. Putback your changes.\
\
[Warning ! Steps 5. and 6. should be performed by the\
release engineer]
\
5. When ready for publication, publish a new version\
   of the $opts{m} module :\
   hcr_module_publish.sh -m $opts{m} -r $opts{r}\
   At that time, if nothing changes, version\
   $opts{m}-$module_rc_version will be published.
\
6. When ready, publish a new release :\
   hcr_release_publish.sh -r $opts{r}\
   If nothing changes, this will be release $opts{r}-$release_rc_version\
\
";

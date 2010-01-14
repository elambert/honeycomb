#!/usr/bin/perl

#
# $Id: hcr_module_unpublish.pl 10849 2007-05-19 02:44:57Z bberndt $
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
-m <module name> to specify the module to be unpublished
-r <release name> to specify in which release context the module should be unpublished\
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

$mod_def_version = $release->get_module_version($opts{m});
$rc_version = $mod_version->release_candidate($mod_def_version) || die "\
Version $mod_def_version of module $opts{m} is not available\
\n";
$dirname = $opts{m}."-".$rc_version->toString();

print "Are you sure you want to unpublish module $opts{m}, part of release $opts{r} ?\
This is module [$dirname].\
[y-n] ? ";

$answer = <STDIN>;
chomp($answer);
if ($answer ne "y") {
    die "Operation cancelled. Module [$dirname] has *NOT* been unpublished\n";
}

print "\nUnpublishing module $opts{m}, in the context of the $opts{r} release [$dirname]\n\n";

# Check that no release has been created with that module

$last_release_version = $release->{version}->release_candidate();

if ($last_release_version->{depth} > 0) {
  $module_link = "$hcr_releases/$opts{r}/$opts{r}-".$last_release_version->toString()."/$opts{m}";
  $link_value = readlink($module_link);
  if ($link_value) {
    @fields = split(/\//, $link_value);
    if ($fields[@fields-1] eq $dirname) {
      die "The module you are trying to unpublish is already published part of release [$opts{r}-".
$last_release_version->toString()."].\
Unpublish that release first (this operation is *NOT* adviced).\
\n";
    }
  } else {
    print "WARNING: Cannot read value of symlink [$module_link]\n";
  }
}

# Check that there is no fork

open(SVN, "svn ls $hcr_svn_dev|") || die "Failed to execute [svn ls $hcr_svn_dev]\
\n";

while (<SVN>) {
  chomp;
  if (/$dirname/) {
    die "The module $dirname is used as a root module for an other release. Cannot unpublish !\
\n";
  }
}

close(SVN);

# Delete the svn_tags entry

@args = ("svn", "delete", "$hcr_svn_tags/$dirname",
         "--message", "6226407\nkeep open\nHCR module unpublish [$dirname]");
system(@args) && die "Failed to execute [svn delete $hcr_svn_tags/$dirname]\
\n";

# Delete the module directory

@args = ("chmod", "-R", "u+w", "$hcr_modules/$opts{m}/$dirname");
system(@args) && die "\
Failed to set the write permission to [$hcr_modules/$opts{m}/$dirname]\
\n";

@args = ("rm", "-fr", "$hcr_modules/$opts{m}/$dirname");
system(@args) && die "Failed to delete the $dirname directory\
\n";

print "\n***** Module $dirname successfully unpublished*****\n\n";

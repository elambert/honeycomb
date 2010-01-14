#!/usr/bin/perl

#
# $Id: hcr_module_publish.pl 10849 2007-05-19 02:44:57Z bberndt $
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
use HCR::Login;
use Getopt::Std;

HCR::Init::check();

$modules = HCR::Modules::new();
$releases = HCR::Releases::new();

sub usage {
    die "\
Valid command line options are :\
-m <module name> to specify the module to be exported
-r <release name> to specify in which release context the module should be exported\
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

print "\nExporting module $opts{m}, in the context of the $opts{r} release\n\n";

$login = HCR::Login::new();

$mod_def_version = $release->get_module_version($opts{m});
$rc_version = $mod_version->fork($mod_def_version) || die "\
Version $mod_def_version of module $opts{m} is not available\
\n";

$dirname = $opts{m}."-".$rc_version->toString();
$repos = $mod_version->get($mod_def_version)->{depth} == 0
    ? $hcr_svn_trunk."/$opts{m}"
    : $hcr_svn_dev."/$opts{m}-$mod_def_version";

print "Building version ".$rc_version->toString()." of module $opts{m} [$dirname]\n";
print "The repository used is [$repos]\n";

$repos_dest = $hcr_svn_tags."/$dirname";
print "\nCreating a tag for $dirname\n";
@args = ("svn", "copy"
	 , "--message", "6226407\nkeep open\nHCR tag creation [$dirname]"
	 , $repos, $repos_dest);
push @args, $login->get_svn_arguments();
system(@args) && die "\
svn copy failed\
\n";

$destdir = "$hcr_modules/$opts{m}/$dirname";
print "\nExport the source to $destdir\n";
@args = ("svn", "export", $repos_dest, $destdir);
push @args, $login->get_svn_arguments();
system(@args) && die "\
svn export failed\
\n";

# Create the version file

print "Grab the lastest revision number from subversion\n";

@login_args = $login->get_svn_arguments();

open(OUT, ">$destdir/hc_version") || die "\
Cannot create the version output file [$destdir/hc_version]\
\n";

open(SVN, "svn ls -v $hcr_svn_tags @login_args |") || die "\
Cannot execute svn info\
\n";

print OUT "$dirname ";

while (<SVN>) {
  if (/$dirname/) {
    ($rev) = $_=~/^[ ]+([0-9]+)/;
    print OUT "(svn $rev)\n";
  }
}

close(SVN);
close(OUT);

# Mark all files read only

@args = ("chmod", "-R", "a-w", "$destdir");
system (@args) && die "\
Failed to remove write permissions in $destdir\
\n";

print "\n***** Module $dirname successfully exported*****\n\n";

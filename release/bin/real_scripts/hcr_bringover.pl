#!/usr/bin/perl

#
# $Id: hcr_bringover.pl 10849 2007-05-19 02:44:57Z bberndt $
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
use HCR::Login;

HCR::Init::check();

$modules = HCR::Modules::new();
$releases = HCR::Releases::new();

sub usage {
    die "\
Valid command line options are :\
-m <modules> (optional) to specify the module(s) to work on (see below)
-r <release name> to specify in which release context you want to work\
-d <destination> to know where to put the checked out copy\
\
<modules> is a comma separated list of modules (e.g. server,client)\
If the -m option is not specified, all the modules will be copied from the latest\
\tstable release.\
The * value (-m *) will checkout all modules
\n";
}

%opts = {};
getopts("m:r:d:v:", \%opts);

if (!$opts{r}) {
    print "A release has to be specified\n";
    usage();
}

if (!$opts{d}) {
    print "A destination directory has to be specified\n";
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

if (-d $opts{d}) {
    die "\
Directory [$opts{d}] already exists. It shouldn't exist before running this script\
\n";
}

@modlist = ();
$checkout_all = 0;

if ($opts{m}) {
  if ($opts{m} eq "*") {
    $checkout_all = 1;
  } else {
	@inputmodules = split(/,/, $opts{m});

    foreach $module (@inputmodules) {
      $mod = $release->get_module_definition($module) || die "\
Module $module is unknown\
\n";
      push @modlist, $mod;
    }
  }
}

mkdir($opts{d}) || die "\
Failed to create [$opts{d}]\
\n";

open(DIR, "sh -c \"cd $opts{d}; pwd\" |");
$destination = <DIR>;
close(DIR);
chomp($destination);

print "Verion: $opts{v}\n";
$last_rel_version = $release->{version}->release_candidate($opts{v})->toString();

$login = HCR::Login::new();
print "\n";

foreach $modentry (@{$release->{def}}) {
  $module = $modentry->{module};

  # Do we have to copy or checkout ?
  $checkout = $checkout_all;
  $moddest = "$destination/$module";

  if (contains(\@modlist, $module)) {
	$checkout = 1;
  }

  if ($checkout) {
	if (!$modentry->can_checkout()) {
	  print "*** WARNING: module $module can not be checked out ***\
(cponly property set in the release definition file)\n";
      $checkout = 0;
    }
  }

  if ($checkout) {
	# Do a checkout

	$module_def_version = $release->get_module_version($module);

	$module_svn_url = $hcr_svn_trunk."/".$module;
	if ($module_def_version) {
	  $module_svn_url = $hcr_svn_dev."/".$module."-".$module_def_version;
	}

	print "Checking out the latest version of $module from subversion\
[$module_svn_url]\n";

	@args = ("svn", "co", "$module_svn_url", "$moddest");
    push @args, $login->get_svn_arguments();
    @runargs = ("sh", "-c", "@args > /dev/null");
    system(@runargs) && die "\
Failed to check out the working version\
\n";

  } else {
	# Do a copy

    if ($module=~/^build/) {
	    $from = "$hcr_releases/$opts{r}/$opts{r}-$last_rel_version/build";
    } else {
	    $from = "$hcr_releases/$opts{r}/$opts{r}-$last_rel_version/$module";
    }

    print "Copying the $module bits from $from\n";

	mkdir "$moddest" || die "\
Failed to create $moddest\
\n";

	@args = ("sh", "-c", "cp -R $from/* $moddest");
	system(@args) && die "\
Failed to execute [@args]\
\n";

  print "    Setting the right permissions\n";

  @args = ("chmod", "-R", "u+w", "$moddest");
  system(@args) && die "\
Failed to chmod\
\n";
  }

  print "\n";
}

print "***** Working version available in $destination *****\
\n";

sub contains {
  my $array = shift;
  my $elem = shift;
  my $cur;

  foreach $cur (@$array) {
	if ($elem eq $cur->{module}) {
	  return $cur;
	}
  }

  return undef;
}

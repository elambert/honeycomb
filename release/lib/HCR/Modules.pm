#
# $Id: Modules.pm 10849 2007-05-19 02:44:57Z bberndt $
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

package HCR::Modules;

use HCR::VersionTree;
use HCR::Utils;

sub new {
    my $self = {};
    my @files = HCR::Utils::parse_dir($::hcr_modules);
    my $file;
    my %modules = ();

    foreach $file (@files) {
	$modules{$file} = HCR::VersionTree::parse_dir($::hcr_modules."/".$file);
    }

    $self->{modules} = \%modules;
    bless $self;
    return($self);
}

sub list {
    my $self = shift;
    my $hashref = $self->{modules};
    return(keys(%$hashref));
}

sub get_version {
    my $self = shift;
    my $module = shift;
    return(${$self->{modules}}{$module});
}

sub get_diff {
    my $self = shift;
    my $module_name = shift;
    my $release = shift;
    my $dontprint = shift;
    my $login = shift;

    my $mod_version = $self->get_version($module_name) ||
    die "\
Module $module_name is unknown\
\n";

    my $mod_def_version = $release->get_module_version($module_name);
    my $last_version = $mod_version->release_candidate($mod_def_version) || die "\
Version $mod_def_version of module $module_name is not available\
\n";

    my $complete_module_name = $module_name."-".$last_version->toString();
    my $lastrepos = "$::hcr_svn_tags/$complete_module_name";

    my $repos = $mod_version->get($mod_def_version)->{depth} == 0
	? $::hcr_svn_trunk."/$module_name"
	: $::hcr_svn_dev."/$module_name-$mod_def_version";

    my @args = ("svn", "diff", $lastrepos, $repos);

    my $result = undef;

    if ((!$dontprint) || ($dontprint != 1)) {
	print "Computing the diff between [$lastrepos]\
and [$repos] ...\
\n";

	system(@args) && die "\
Failed to run svn diff\
\n";
    } else {
	if ($login) {
	    push @args, $login->get_svn_arguments();
	}

	my $cmd = "sh -c '@args' | wc";

	open(SVN, "$cmd |") || die "\
Failed to execute svn diff\
\n";
	my $line = <SVN>;
	chomp($line);
	$result = ($line != 0);

	close(SVN);
    }

    return($result);
}

1;

    
    

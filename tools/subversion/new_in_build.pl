#!/usr/bin/perl -w

# Find out diffs in code changes between 2 builds in a given release.
#
# $Id: new_in_build.pl 10853 2007-05-19 02:50:20Z bberndt $
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




use strict;
use Getopt::Long;

my $QUIET = 0;
GetOptions("q" => \$QUIET);
my ($RELEASE, $OLD_BUILD, $NEW_BUILD) = @ARGV; # eg: anza anza-1 anza-2 [-q]

my @test_modules = ("test", "hctest", "itest", "unit_tests", "suitcase", "whitebox");

sub usage() {
    print "USAGE: $0 <release> <old_build> [new_build] [-q] \n";
    print "Will query subversion for code changes between two given builds (or since last build), and output to stdout. \n";
    print "Quiet option -q will ignore test, hctest, unit_test, suitcase modules, and modules with no changes. \n";
    exit 0;
}

# Is given module part of test_modules list? If so, ignore if running with QUIET.
# 
sub ignore_module {
    my ($module) = @_;
    return 0 unless $QUIET;
    foreach my $m (@test_modules) {
        return 1 if ($module eq $m);
    }
    return 0;
}

if (!defined $RELEASE or !defined $OLD_BUILD) {
    usage();
}
if (!defined $NEW_BUILD) {
    print "New_build not set; will list all changes since build $OLD_BUILD. \n";
}

my $SVN_URL = "https://subversion.sfbay.sun.com/repos/honeycomb/";
my $SVN_BRANCH = $SVN_URL."hcr_dev";
my $SVN_TRUNK = $SVN_URL."trunk";
my $DIR = "/export/release/repository/releases";
my $RDIR = $DIR."/".$RELEASE;
my $OBDIR = $RDIR."/".$OLD_BUILD;
my $NBDIR = (defined $NEW_BUILD) ? $RDIR."/".$NEW_BUILD : undef;

die "No such release in repository: $RDIR" unless -d $RDIR;
die "No such build in repository: $OBDIR" unless -d $OBDIR;
die "No definition found for release: $RDIR" unless -e "${RDIR}/definition";
die "No hc_version found for build: $OBDIR" unless -e "${OBDIR}/hc_version";
if (defined $NEW_BUILD) {
    die "No such build in repository: $NBDIR" unless -d $NBDIR;
    die "No hc_version found for build: $NBDIR" unless -e "${NBDIR}/hc_version";
}

my %modules = ();

# Read modules and their branch versions from release definition
#
open (RDEF, "${RDIR}/definition") or die "Can't open ${RDIR}/definition";
while (my $line = <RDEF>) {
    next if ($line =~ /^\#/); # skip comments
    next if ($line =~ /^(\s)*$/); # skip blank lines
    chomp $line;
    if ($line =~ /([A-Za-z_]+)-(\d+)/) {
        my $module = $1;
        my $version = $2;
        $modules{$module}{'version'} = "${module}-${version}";    
    } elsif ($line =~ /([A-Za-z_]+)/) {
        my $module = $1;
        $modules{$module}{'version'} = "trunk";
    } else {
        die "Can't parse ${RDIR}/definition line: $line";
    }
}
close (RDEF);

# Read svn revision numbers for modules from old build's hc_version file
#
open (OLDVER, "${OBDIR}/hc_version") or die "Can't open ${OBDIR}/hc_version";
while (my $line = <OLDVER>) {
    next if ($line =~ /Honeycomb.*release/); # header
    if ($line =~ /\w+ \(\w+ \d+\)/) {
        $line =~ s/\(//;
        $line =~ s/\)//;
        chomp $line;
        my ($module, $version, $svn, $rev) = split(/[- ]/, $line);
        if (! exists $modules{$module}) {
            warn "Module $module present in build $OLD_BUILD but not in release $RELEASE";
        } else {
            if (! defined $rev) {
                warn "Module $module not present in build $OLD_BUILD"
                }
            $modules{$module}{'oldrev'} = $rev;
        }
    } else {
        warn "Can't parse line in ${OBDIR}/hc_version file: $line \n";
    }
}
close (OLDVER);

# Read svn revision numbers for modules from new build's hc_version file
#
if (defined $NEW_BUILD) {
    open (NEWVER, "${NBDIR}/hc_version") or die "Can't open ${NBDIR}/hc_version";
    while (my $line = <NEWVER>) {
        next if ($line =~ /Honeycomb.*release/); # header
        if ($line =~ /\w+ \(\w+ \d+\)/) {
            $line =~ s/\(//;
            $line =~ s/\)//;
            chomp $line;
            my ($module, $version, $svn, $rev) = split(/[- ]/, $line);
            if (! exists $modules{$module}) {
                warn "Module $module present in build $NEW_BUILD but not in release $RELEASE" 
                } else {
                    if (! defined $rev) {
                        warn "Module $module not present in build $NEW_BUILD"
                        }
                    $modules{$module}{'newrev'} = $rev;
                }
        } else {
            warn "Can't parse line in ${NBDIR}/hc_version file: $line \n";
        }
    }
    close (NEWVER);
}

# print report header
#
if (defined $NEW_BUILD) {
    print "Code changes in release [$RELEASE] between builds [$OLD_BUILD] and [$NEW_BUILD]: \n";
} else {
    print "Code changes in release [$RELEASE] since build [$OLD_BUILD]: \n";
}
print "Ignoring modules: ".join(', ',@test_modules).", and modules with no changes.\n" if $QUIET;
my $div = "============================================================================ \n";

# Query subversion for code changes between old and new revision for each module
#
foreach my $module (keys %modules) {
    next if ($QUIET && ignore_module($module));

    my $path = $modules{$module}{'version'};
    my $svn_path = ($path eq "trunk") ? $SVN_TRUNK."/".$module : $SVN_BRANCH."/".$path;
    # print "DEBUG: module [$module] svn path [$svn_path] \n";
    my $oldrev = $modules{$module}{'oldrev'};
    my $newrev = $modules{$module}{'newrev'};
    $newrev = "HEAD" unless defined $newrev; # get all changes since $oldrev 
    $oldrev = "BASE" unless defined $oldrev; # get all changes ever, if this is a new module
    if ($oldrev eq $newrev) {
        print $div."MODULE $module = NO CHANGE \n".$div unless $QUIET;
    } else {
        print $div."MODULE $module \n".$div;
        if ($oldrev eq "BASE") {
            # XXX: I can't make this command work with BASE arg [dmehra]
            # system("svn log $svn_path -r ${oldrev}:${newrev}");
            system("svn log $svn_path");
        } else {
            system("svn log $svn_path -r ${oldrev}:${newrev}");
        }
    }
}

# Samples
#/export/release/repository/releases/anza/definition/export/release/repository/releases/anza/definition
#/export/release/repository/releases/anza/anza-2/hc_version
#svn log https://subversion/repos/honeycomb/hcr_dev/server-24 -r 6130:6261

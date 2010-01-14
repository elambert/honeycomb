#!/usr/bin/env perl

#
# $Id: configure.pl 10856 2007-05-19 02:58:52Z bberndt $
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

use Getopt::Std;
use File::Basename;
use File::Copy;
use getos;
sub usage {
    print <<END;

Usage : configure.pl [-v]
\t-v runs in a verbose mode 
END
    exit 1;
}

sub getEnv {
    my $name = shift;
    my $default = shift;

    my $res = $ENV{$name};
    if (!$res) {
	$res = $default;
    }

    return($res);
}

sub formatLine {
    my $line = shift;

    foreach my $entry (@config) {
	my ($key, $value) = @$entry;
#	print "$key->$value\n";
	$$line =~ s/%$key%/$value/g;
    }
}

sub formatFile {
    my $prefix = shift;
    my $srcDir = shift;
    my $destDir = shift;
    my $source = $srcDir."/".$prefix.".template";
    my $dest = $destDir."/".$prefix;

    if ($verbose == 1) {
	print "Formatting [$prefix.template] -> [$prefix]\n";
    }

    $destDir = dirname($dest);
    if ( ! -d $destDir ) {
	mkdir $destDir;
    }

    open(IN, $source) || die "Failed to open [$source]\n";
    open(OUT, ">$dest") || die "Failed to open [$dest] - Write mode\n";

    while (my $line = <IN>) {
	formatLine(\$line);
	print OUT $line;
    }
    close(IN);
    close(OUT);
}

sub readEnvFile {
    my $file = "$HC_SRC/tools/env.$OS";
    
    if (!( -f $file )) {
	if ($verbose == 1) {
	    print "WARNING: $file does not exist\n";
	}
	return;
    }

    open(IN, $file);
    while (my $line = <IN>) {
	chomp($line);
	if ( (substr($line, 0, 1) ne "#")
	    && (length($line) >=2) ) {
	    my $i = index($line, "=");
	    my @couple = (substr($line, 0, $i), substr($line, $i+1));
	    push @config, \@couple;
	}
    }
    close(IN);
}

sub copyHostInclude{
  my $destDir = shift;
  my $includeSource = "$HC_SRC/tools/hchost.$OS";
  my $includeDest = "$destDir/src/hchost.h";
#  print "copying from $includeSource to $includeDest\n";
  copy($includeSource, $includeDest) or die "File cannot be copied: $includeSource.";
}

# Read command line arguments



%args = ();
getopts("v", \%args) || usage();

if ($args{'v'}) {
    $verbose = 1;
}

# Discover the local OS

$OS=getOS();

# Read environment variables

$local_dir = `pwd`;
chomp($local_dir);
$HC_SRC = getEnv("HC_SRC", $local_dir);
$HC_BUILD = getEnv("HC_BUILD", "$local_dir/build");
$HC_BUILD .= "_$OS";

if ( ! -d $HC_BUILD ) {
    mkdir $HC_BUILD || die "Failed to mkdir $HC_BUILD\n";
}

# Setup the config

@config = ();

push @config, ["OS", $OS];

# Convert to cygwin style so that merging relative paths will work
$HC_SRC =~ s-\\-\/-g;
$HC_SRC =~ s-(.):-/cygdrive/\1-g;
push @config, ["HC_SRC", $HC_SRC];

$HC_BUILD =~ s-\\-\/-g;
$HC_BUILD =~ s-(.):-/cygdrive/\1-g;
push @config, ["HC_BUILD", "$HC_BUILD"];
readEnvFile();

if ($verbose == 1) {
    print "Configuring build files for [$OS]\n";

    print <<END;

*****************
* Configuration *
*****************
END

    foreach $entry (@config) {
	($key, $value) = @$entry;
	print "$key\t$value\n";
    } 
    print "\n";
}

# Format the configuration files

formatFile("profile.curl", "$HC_SRC/tools", "$HC_BUILD/curl");
formatFile("profile.honeycomb", "$HC_SRC/tools", "$HC_BUILD/honeycomb");
formatFile("profile.test", "$HC_SRC/tools", "$HC_BUILD/test");
formatFile("Makefile", "$HC_SRC/tools", $HC_BUILD);
formatFile("Makefile.env", "$HC_SRC/tools", $HC_BUILD);
#copyHostInclude("$HC_BUILD/honeycomb");

# Print the OS and exits

print "$OS\n";
exit 0;

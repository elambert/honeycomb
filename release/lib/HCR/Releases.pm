#
# $Id: Releases.pm 10849 2007-05-19 02:44:57Z bberndt $
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

package HCR::Releases;

use HCR::VersionTree;
use HCR::Utils;
use HCR::Release;

sub new {
    my $self = {};
    my @files = HCR::Utils::parse_dir($::hcr_releases);
    my $file;
    my %releases = ();

    foreach $file (@files) {
	$releases{$file} = HCR::Release::new($file, $::hcr_releases."/".$file, "-");
    }

    $self->{releases} = \%releases;
    bless $self;
    return($self);
}

sub list {
    my $self = shift;
    my $hashref = $self->{releases};
    return(keys(%$hashref));
}

sub get_release {
    my $self = shift;
    my $release = shift;
    return(${$self->{releases}}{$release});
}

sub print_available {
    my $self = shift;
    my @list = $self->list();
    my $name;
    
    print "List of available releases :\n";
    foreach $name (@list) {
	print "$name\n";
    }
}

1;

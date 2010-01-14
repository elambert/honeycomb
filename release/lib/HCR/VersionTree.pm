#
# $Id: VersionTree.pm 10849 2007-05-19 02:44:57Z bberndt $
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

package HCR::VersionTree;

use HCR::Utils;

sub parse_dir {
    my $path = shift;
    my @files = HCR::Utils::parse_dir($path, "-");
    my $res = new();
    my $file;
    foreach $file (@files) {
	$vers = substr($file, index($file, "-")+1);
	$res->insert($vers);
    }
    return($res);
}

sub new {
    my $self = {};
    my @children = ();
    $self->{parent} = undef;
    $self->{n} = 0;
    $self->{depth} = 0;
    $self->{children} = \@children;
    bless $self;
    return $self;
}

sub internal_new {
    my $parent = shift;
    my $number = shift;
    my $self = new();
    $self->{parent} = $parent;
    $self->{depth} = $parent->{depth}+1;
    $self->{n} = $number;
    return $self;
}

sub insert {
    my $self = shift;
    my $s = shift;
    my @array = split(/\./, $s);
    $self->insert_rec(\@array, 0);
}

sub insert_rec {
    my $self = shift;
    my $array = shift;
    my $index = shift;
    my $n = $$array[$index];
    my $i = 0;
    my $chref = $self->{children};
    for ($i=@{$chref}; $i<$n; $i++) {
	${$chref}[$i] = internal_new($self, $i);
    }
    if ($index+1<@$array) {
	${$chref}[$n-1]->insert_rec($array, $index+1);
    }
}

sub get {
    my $self = shift;
    my $s = shift;
    if (!$s) { return($self); }
    my @array = split(/\./, $s);
    return($self->get_rec(\@array, 0));
}

sub get_rec {
    my $self = shift;
    my $array = shift;
    my $index = shift;
    my $n = $$array[$index]-1;
    my $chref = $self->{children};
    
    if ($n > @$chref) {
	return(unref);
    }

    if ($index+1 == @$array) {
	return($$chref[$n]);
    } else {
	return($$chref[$n]->get_rec($array, $index+1));
    }
}

sub release_candidate {
    my $self = shift;
    my $version = shift;

    if ($version) {
	return($self->get($version)->release_candidate());
    }

    my $chref = $self->{children};

    if (@$chref == 0) {
	return($self);
    }
    
    return($$chref[@$chref-1]);
}

sub fork {
    my $self = shift;
    my $version = shift;

    if ($version) {
	my $target = $self->get($version);
	if ($target == unref) { return(undef); }
	return($target->fork());
    }
    
    my $chref = $self->{children};

    my $newn = @$chref;
    $$chref[$newn] = internal_new($self, $newn);
    return($$chref[@$chref-1]);
}

sub toString {
    my $self = shift;
    my $noroot = shift;
    my $res = "";
    if ($self->{parent}) {
	$res = $self->{parent}->toString(1);
	$res = length($res)>0 ? "$res.".($self->{n}+1)
	    :($self->{n}+1);
    } elsif (!$noroot) {
	$res = "<root>";
    }
    return($res);
}

sub print {
    my $self = shift;
    my $cur = shift;
    my $i = 0;
    my $chref = $self->{children};

    if ($self->{parent}) {
	if (length($cur) > 0) {
	    $cur .= ".";
	}
	$cur .= $self->{n}+1;
	print "$cur\n";
    }

    for ($i=0; $i<@{$chref}; $i++) {
	${$chref}[$i]->print($cur);
    }
}

1;

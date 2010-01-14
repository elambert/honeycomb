#
# $Id: getos.pm 10856 2007-05-19 02:58:52Z bberndt $
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

sub getOS {
  my $os = `uname`;
  chomp($os);
  if ($os eq "Windows_NT" or
      length($os) > 11 and
      substr($os, 0, 11) eq "CYGWIN_NT-5") {
    return "Win32";
  } elsif ($os eq "Darwin") {
    return "macOSX";
  } elsif ($os eq "Linux") {
    return "Linux";
  } elsif ($os eq "SunOS") {
    my $token_raw = `uname -r`;
    chomp($token_raw);
    my @tokens = split(/\./, $token_raw);
    my $release = $tokens[1];

    my $arch = `uname -p`;
    chomp($arch);

    if ($arch eq "i386") {
      return("sol_".$release."_x86");
    } elsif ($arch eq "sparc") {
      return("sol_".$release."_sparc");
    } else {
      die "Unrecognized Solaris architecture [$arch]\n";
    }
  } else {
    die "Unrecognized operating system [$os]\n";
  }
}

1;

#!/usr/bin/perl

#
# $Id: setup_admin_account.pl 10855 2007-05-19 02:54:08Z bberndt $
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

@ARGV==2 || die "Wrong arguments. Usage is :\
setup_admin_account.pl <new admin username> <new admin passwd>\n";

$name = $ARGV[0];
$passwd = $ARGV[1];

$passwd_file = "/etc/passwd";
$shadow_file = "/etc/shadow";

$mv_passwd = 0;
$mv_shadow = 0;

# Check the admin account name

open(IN, $passwd_file) || die "Failed to open [$passwd_file]\n";
@lines = <IN>;
close(IN);

$oldname = undef;

for ($i=0; (!$oldname) && ($i<@lines); $i++) {
  $line = $lines[$i];

  if ($line=~/admin user/) {
    chomp($line);
    @fields = split(/:/, $line);
    $oldname = $fields[0];
  }
}

if ($oldname ne $name) {
  print "Changing the name of the admin account from [$oldname] to [$name]\n";

  open(OUT, ">$passwd_file.new") || die "Failed to open [$passwd_file.new] in write mode\n";
  foreach $line (@lines) {
    if ($line=~/admin user/) {
      chomp($line);
      @fields = split(/:/, $line);
      $fields[0] = $name;
      $line = join(":", @fields)."\n";
    }
    print OUT $line;
  }
  close(OUT);

  $mv_passwd = 1;
}

# Check the admin account password

# Don't encrypt the passwd, already done
#$crypted = crypt($passwd, "hcsalt");
$crypted = $passwd;

open(IN, $shadow_file) || die "Failed to open [$shadow_file]\n";
@lines = <IN>;
close(IN);

$oldpasswd = undef;
$uptodate = 1;

if ($oldname ne $name) {
  $uptodate = 0;
}

if ($uptodate == 1) {
  for ($i=0; (!$oldpasswd) && ($i<@lines); $i++) {
    $line = $lines[$i];

    if ($line=~/^$oldname/) {
      chomp($line);
      @fields = split(/:/, $line);
      $oldpasswd = $fields[1];
    }
  }

  if ($oldpasswd ne $crypted) {
    $uptodate = 0;
  }
} else {
  $oldpasswd = $crypted;
}

if ($uptodate == 0) {
  print "Changing the name/passwd of the admin account from [$oldname:$oldpasswd] to [$name:$crypted]\n";

  open(OUT, ">$shadow_file.new") || die "Failed to open [$shadow_file.new] in write mode\n";

  foreach $line (@lines) {
    if ($line=~/^$oldname/) {
      chomp($line);
      @fields = split(/:/, $line);
      $fields[0] = $name;
      $fields[1] = $crypted;
      $line = "$fields[0]:$fields[1]::::::\n";
    }
    print OUT $line;
  }
  close(OUT);

  $mv_shadow = 1;
}

if ($mv_passwd == 1) {
  rename("$passwd_file.new", $passwd_file);
}
if ($mv_shadow == 1) {
  rename("$shadow_file.new", $shadow_file);
}

exit 0;

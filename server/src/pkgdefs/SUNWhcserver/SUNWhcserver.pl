#!/usr/bin/perl

#
# $Id: SUNWhcserver.pl 10937 2007-05-31 03:46:57Z mgoff $
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

#
# Pre install script for the SUNWhcserver package
#

#
# Constants
#

$PKG_INSTALL_ROOT=@ENV{PKG_INSTALL_ROOT};
$SYSTEM = $ENV{PKG_INSTALL_ROOT}."/etc/system";

# Logging parameters
$LOGSERVER="10.123.45.1";
$SERVICEPROCESSOR="10.123.45.100";

#
# Useful functions ...
#

sub jumpstart_install {
  if (($PKG_INSTALL_ROOT ne "") && ($PKG_INSTALL_ROOT ne "/")) {
    return(1);
  }
  return(0);
}

sub restart_service {
  my $svc = shift;

  if (jumpstart_install() == 1) {
    return;
  }

  print "Restarting [$svc]\n";

  @args = ("/usr/sbin/svcadm", "restart", "$svc");
  system(@args) && die "Failed to restart [$svc]\n";
}

sub file_contains {
  my $filename = shift;
  my $string = shift;
  my $result = 0;
  my $line;

  open(IN, $filename) || die "Failed to open $filename in read mode\n";
  while (($line=<IN>) && ($result == 0)) {
    if ($line=~/^$string/) {
      $result = 1;
    }
  }
  close(IN);

  return($result);
}

sub file_append {
  my $filename = shift;
  my $line = shift;

  open(OUT, ">>$filename") || die "Failed to open $filename in write mode\n";
  print OUT $line."\n";
  close(OUT);
}

sub add_group {
  my $name = shift;
  my $gid = shift;
  my $group_file = "$PKG_INSTALL_ROOT/etc/group";

  my $already_configured = file_contains($group_file, $name);

  if ($already_configured == 0) {
    file_append($group_file, $name."::".$gid.":");
    print "Group $name has been configured\n";
  } else {
    print "group $name is already configured\n";
  }
}

sub add_user {
  my $name = shift;
  my $uid = shift;
  my $gid = shift;
  my $shell = shift;
  my $home = shift;
  my $passwd = shift;
  my $passwd_file = "$PKG_INSTALL_ROOT/etc/passwd";
  my $shadow_file = "$PKG_INSTALL_ROOT/etc/shadow";

  if (!$passwd) { $passwd = ""; }

  my $already_configured = file_contains($passwd_file, $name);

  if ($already_configured == 0) {
    file_append($passwd_file, $name.":x:".$uid.":".$gid.":$name user:".$home.":".$shell);
    my $pwd = crypt($passwd, "hcsalt");
    file_append($shadow_file, $name.":".$pwd.":::::::");
    print "User $name has been configured\n";
  } else {
    print "User $name already configured\n";
  }
}

# Set property in /etc/system. Copied from hadb_setup.pl
#
sub set_property {
  my $file = shift;
  my $name = shift;
  my $value = shift;
  my $prefix = shift;
  my @lines;
  my $rewrite = 0;
  my $found = 0;
  my $oldvalue;

  if (!$prefix) {
    $prefix = "";
  } else {
    $prefix="$prefix ";
  }

  open(IN, $file) || die "Failed to open $file";
  @lines = <IN>;
  close(IN);

  for (my $i=0; ($i<@lines) && ($found==0); $i++) {
    if ($lines[$i]=~/$name/) {
      $found = 1;
      ($oldvalue) = ($lines[$i]=~/=[ ]*(.+)\n/);
      print "The property $name is already defined. ";
      if (($line=~/^#/) || ($oldvalue ne $value)) {
        $lines[$i] = $prefix."$name=$value\n";
        print "Replacing old value [$oldvalue] with [$value]\n";
        $rewrite = 1;
      } else {
        print "Its value is already set to [$value]\n";
      }
    }
  }

  if (($found == 0) || ($rewrite==1)) {
    open(OUT, ">$file") || die "Failed to open $file in write mode";
    print OUT @lines;
    if ($found == 0) {
      print "The property $name was not defined. Setting its value to [$value]\n";
      if (length($lines[@lines]) > 1) {
        print OUT "\n";
      }
      print OUT $prefix."$name=$value\n";
    }
    close(OUT);
  }
}

#
# Main code
#

##### /etc/system properties #####

# Workaround for UFS/NFS deadlock BUG 6370397: 
# Increase segmapsize from default 16M to 256M
set_property($SYSTEM, "segmapsize", "0x10000000", "set");
set_property($SYSTEM, "nopanicdebug", "1" , "set");

##### Syslog configuration #####

$SYSLOG_CONF = "$PKG_INSTALL_ROOT/etc/syslog.conf";
$HC_LOG = "/var/adm/honeycomb";

print "Configure the syslog service for honeycomb\n";

open(IN, $SYSLOG_CONF) || die "Failed to open $SYSLOG_CONF\n";
$already_configured = 0;

while (<IN>) {
  if (/^kern\.warning/) {
	print ("The syslog configuraton file already contains the Honeycomb configuration\n");
        $already_configured = 1;
        break;
  }
}

close(IN);

if ($already_configured == 0) {
  print "Adding the honeycomb configuration to $SYSLOG_CONF\n";

  open(OUT, ">>$SYSLOG_CONF") || die "Failed to open $SYSLOG_CONF in write mode\n";
  print OUT "local1.info\t\@$LOGSERVER\n";
  print OUT "kern.warning\t/var/adm/kernel\n";
  print OUT "*.debug\t\@$SERVICEPROCESSOR\n";
  close(OUT);
 
  open(OUT, "<$SYSLOG_CONF") || die "Failed to open $SYSLOG_CONF in read mode\n";
  open(NEW, ">$SYSLOG_CONF.tmp") || die "Failed to open $SYSLOG_CONF.tmp in write mode\n";

  print "Changing the logging setup\n";
  while (<OUT>) {
    if (/^\*\.err.*messages$/) {
        print "Modifying $SYSLOG\n"; 
	s/\*\.err;kern/\*/;
    }

    print NEW $_;
  }

  close OUT;
  close NEW;

  `mv $SYSLOG_CONF.tmp $SYSLOG_CONF`;
  restart_service("system-log");

  print "Syslog sucessfully updated\n";
}

##### Setup the honeycomb group #####

add_group("honeycomb", "1000");

##### Setup some users #####

add_user("admin", "1000", "1000", "/opt/honeycomb/bin/hcsh",
         "/opt/honeycomb/home/admin", "admin");
add_user("internal", "2000", "1000", "/usr/bin/bash",
         "/opt/honeycomb/home/internal", crypt("internal", "hcsalt"));

exit(0);

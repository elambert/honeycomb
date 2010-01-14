#!/usr/bin/perl

#
# $Id: hadb_setup.pl 11326 2007-08-07 21:01:47Z mgoff $
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
# This scripts configures a Solaris machine to be able to run Clustra
#

#
# Constants
#

$PRTCONF = "/usr/sbin/prtconf";
$SYSTEM = $ENV{PKG_INSTALL_ROOT}."/etc/system";
$MGTCFG = $ENV{PKG_INSTALL_ROOT}."/config/SUNWhadb/mgt.cfg";

#
# Helpers
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
# Update script
#

# Node has 3GB
$mem = 3220176896;

# Node has 4GB
# $mem = 4227858432;

print "Target node has $mem bytes of memory\n";

# Shared memory
set_property($SYSTEM, "shmsys:shminfo_shmmax", "0x".sprintf("%X", $mem), "set");

# Semaphores
set_property($SYSTEM, "semsys:seminfo_semmni", "16", "set");
set_property($SYSTEM, "semsys:seminfo_semmns", "128", "set");
set_property($SYSTEM, "semsys:seminfo_semmnu", "1000", "set");

# Update the HADB configuration
set_property($MGTCFG, "ma.server.dbdevicepath", "/data/0/hadb");
set_property($MGTCFG, "ma.server.dbconfigpath", "/data/0/hadb/dbdef");
set_property($MGTCFG, "repository.dr.path", "/data/0/hadb/repository");
set_property($MGTCFG, "ma.server.dbhistorypath", "/data/0/hadb/history");
set_property($MGTCFG, "logfile.name", "/data/0/hadb/log/ma.log");
set_property($MGTCFG, "logfile.loglevel", "INFO");
set_property($MGTCFG, "ma.server.mainternal.interfaces", "10.123.45.101/26");

# Set root privileges to clu_nsup_srv

$NSUP = $ENV{PKG_INSTALL_ROOT}."/opt/SUNWhadb/4/lib/server/clu_nsup_srv";

@args = ("chown", "root", $NSUP);
system(@args) && die "Failed to chown [$NSUP]";

@args = ("chmod", "u+s", $NSUP);
system(@args) && die "Failed to chmod [$NSUP]";

print "The node has been properly configured to support HADB\n";

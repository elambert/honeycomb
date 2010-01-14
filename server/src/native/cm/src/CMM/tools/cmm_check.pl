#!/usr/bin/perl

#
# $Id: cmm_check.pl 10855 2007-05-19 02:54:08Z bberndt $
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

@nodes = ("hcb101","hcb102","hcb103","hcb104","hcb106","hcb107","hcb108","hcb109","hcb110" );

$ssh = "/usr/bin/ssh";
$reboot="/sbin/reboot";
$scp="/usr/bin/scp";
$unlink="/bin/unlink";

$honeycomb_prefix="/opt/honeycomb";
$save_prefix="/tmp";
$log_file="/var/log/honeycomb.log";

#---------------------------------------
#
# End of configuration
#
#---------------------------------------

use File::Temp;

#
# This routine reboots all the nodes
#

sub reboot {
  if (-e "$save_prefix/CMM.STOP") {
    print LOG "The script does not reboot (STOP requested)\n";
    close LOG;
    return;
  }

  close LOG;

  foreach $node (@nodes) {
    if (fork() == 0) {
      # Delete the log file
      system("$ssh $node $unlink $log_file");

      # Call reboot

      #--------------------
      # Uncomment to really reboot
      #--------------------

#      exec("ssh $node $reboot -f") || print "Failed to reboot $node\n";
    }
  }
}

#
# Failure method : Was has to be done when the check fails
#

sub failure {
  my ($msg) = @_;

  print LOG "!!!!! CMM CHECK FAILURE !!!!!\n";

  $tmp_dir = mkdtemp("$save_prefix/cmm_XXXXXX");

  print LOG "Temporary directory is $tmp_dir\n";

  open(ERR_LOG, ">$tmp_dir/log.txt");
  print (ERR_LOG "The check failed [$msg]\n");
  close(ERR_LOG);

  # Dump the cluster logs

  foreach $node (@nodes) {
    system("$scp $node:$log_file $tmp_dir/log_$node");
  }

  reboot();
  exit 1;
}

#
# Main Routine
#

open(LOG, ">>$save_prefix/CMM.TXT") || die "Failed to open LOG file";
($sec,$min,$hour,$mday,$mon,$year,$wday,$yday,$isdst) = localtime(time);
print LOG "\n[".($mon+1)."-$mday-$year $hour:$min:$sec] Script started\n";

for ($i=0; $i<@nodes; $i++) {
  $err = 0;
  open(CMD, "$ssh ".$nodes[$i]." $honeycomb_prefix/tests/cmm_consistency_check |") || ($err = 1);

  if (!$err) {
    chomp($status[$i] = <CMD>);
    close(CMD) || ($err = 1);
  }

  if ($err) {
    failure "Command failed on node $nodes[$i]";
  }
}

if (@status != @nodes) {
  failure("The number of collected status is not equal to the number of nodes (@status-@nodes)");
}

# Check that all the statuses are the same

($nb_nodes, $master, $vicemaster) = split(/ /, $status[0]);

if ($master == "NULL") {
  failure("Node ".$nodes[0]." doesn't have a master");
}

if ($vicemaster == "NULL") {
  failure("Node ".$nodes[0]." doesn't have a vicemaster");
}

for ($i=1; $i<@status; $i++) {
  ($tmp_nodes, $tmp_master, $tmp_vicemaster) = split(/ /, $status[$i]);

  if ($tmp_nodes != $nb_nodes) {
    failure("The number of nodes is inconsistent between "
      .$nodes[0]." and ".$nodes[$i]." ($nb_nodes - $tmp_nodes)");
  }

  if ($tmp_master != $master) {
    failure("The master is inconsistent between "
      .$nodes[0]." and ".$nodes[$i]." ($nb_nodes - $tmp_nodes)");
  }

  if ($tmp_vicemaster != $vicemaster) {
    failure("The vicemaster is inconsistent between "
      .$nodes[0]." and ".$nodes[$i]." ($nb_nodes - $tmp_nodes)");
  }
}

print LOG "The CMM check succeeded.\n";
print LOG "Nb of nodes is $nb_nodes\n";
print LOG "Master node is node id $master\n";
print LOG "Vicemaster node is node id $vicemaster\n";

# Do a cluster reboot in case of success ??

reboot();
exit 0;

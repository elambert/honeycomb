#!/bin/sh
#
# $Id: healing_loop.sh 10858 2007-05-19 03:03:41Z bberndt $
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
# Cause disk failures one by one, in a loop.
# Wait for healing to complete a cycle after each failure.
# Assumes cycle target of 10 minutes.
# Then verify correctness against a snapshot.
#
# TODO: Wipe disk before reenabling
# TODO: Every 10 healing cycles, run retrieve w/content verification.
#

CLUSTER=$1

SNAPSHOT=healing_snapshot

TARGET=600 # datadoctor healing cycle, seconds

NODES="101 102 103 104 105 106 107 108 109 110 111 112 113 114 115 116"
DISKS="1 2 3" # XXX: skipping HADB disk zero, see bug 6503698

# Load functions
source ./healing_base.sh

check_prerequisites $CLUSTER

### MAIN ###

# ensure correct datadoctor settings
#
turn_dd_off
turn_dd_on

# wait one healing cycle to ensure clean state
#
get_heal_time
LASTHEALTIME="$HEALTIME"
echo "[`date`] Last healing cycle (before test start) completed at $LASTHEALTIME"
wait_to_heal

# take live snapshot
#
echo "[`date`] Taking live snapshot..."
ssh root@${CLUSTER}-cheat "/bin/yes | /opt/test/bin/snapshot.sh delete $CLUSTER $SNAPSHOT"
ssh root@${CLUSTER}-cheat "/bin/yes | /opt/test/bin/snapshot.sh save $CLUSTER $SNAPSHOT live"


# loop over all nodes, all disk, except HADB disk zero
# foreach disk: disable, heal, verify, reenable, heal-back
#
for NODE in $NODES; do
  for DISK in $DISKS; do

    echo "[`date`] Disabling disk $NODE:$DISK"
    run_cli_command "hwcfg -F -D DISK-${NODE}:${DISK}"

    echo "[`date`] Waiting for healing cycle to complete"
    wait_to_heal

    turn_dd_off

    # Snapshot verification is limited to the healed disk for speed.
    #
    echo "[`date`] Verifying data against live snapshot..."
    ssh root@${CLUSTER}-cheat "/bin/yes | /opt/test/bin/verify_snapshot.sh $SNAPSHOT $NODE $DISK" 

    # Workaround for suspected bug: when healing is reported done, it's not... need 2nd cycle
    # 
    if [ "$?" != "0" ]; then

      echo "[`date`] Snapshot verification after 1st healing cycle FAILED!"

      turn_dd_on

      echo "[`date`] HACK: Waiting for 2nd healing cycle to complete"
      wait_to_heal

      turn_dd_off
 
      echo "[`date`] Verifying data against live snapshot..."
      ssh root@${CLUSTER}-cheat "/bin/yes | /opt/test/bin/verify_snapshot.sh $SNAPSHOT $NODE $DISK"
    
      if [ "$?" != "0" ]; then
        echo "[`date`] Snapshot verification after 2nd healing cycle FAILED!!!"
      else 
        echo "[`date`] Snapshot verification after 2nd healing cycle OK"
      fi

    else
      echo "[`date`] Snapshot verification after healing OK"
    fi

    turn_dd_on

    # TODO: Wipe disk before reenabling
   
    echo "[`date`] Reenabling disk $NODE:$DISK"
    run_cli_command "hwcfg -F -E DISK-${NODE}:${DISK}"

    echo "[`date`] Waiting for heal-back cycle to complete"
    wait_to_heal

  done
done

echo "ALL DONE"
exit 0

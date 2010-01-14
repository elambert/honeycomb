#!/bin/bash
#
# $Id: nodecheck.sh 10856 2007-05-19 02:58:52Z bberndt $
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
# This script runs on a given cluster node,
# collects basic and CM-specific node state.
# The intent is to run it on each cluster node,
# then scp -r output dirs to the cheat.
#

log_output() {
    CMD=$1
    FILE=$2

    DIV="=========================================================";

    echo            >>$FILE
    echo $DIV       >>$FILE
    echo "+++ $CMD" >>$FILE
    echo $DIV       >>$FILE
    echo            >>$FILE

    $CMD            >>$FILE
    echo            >>$FILE
}


# Output files will be in this local directory.
NODE=`uname -n`
DIR=/tmp/state-${NODE}
rm -rf $DIR
mkdir $DIR

# Basic machine state
STATEFILE=${DIR}/basic
echo -n "+++ NODE: " >>$STATEFILE
uname -a >>$STATEFILE
echo -n "+++ VERSION: " >>$STATEFILE
head -1 /opt/honeycomb/version >>$STATEFILE
echo -n "+++ DATE: " >>$STATEFILE
date >>$STATEFILE
echo -n "+++ UPTIME: " >>$STATEFILE
uptime >>$STATEFILE

# Processes, services
PROCFILE=${DIR}/procs
log_output "ps -Af" $PROCFILE
log_output "svcs -x" $PROCFILE
log_output "svcs" $PROCFILE

# CM state
CMFILE=${DIR}/nodemgr
log_output "/opt/honeycomb/bin/nodemgr_mailbox.sh" $CMFILE

# Networking
NETFILE=${DIR}/network
log_output "ifconfig -a" $NETFILE
log_output "netstat -a" $NETFILE

# Disks, filesystems
FSFILE=${DIR}/filesys
log_output "share" $FSFILE
log_output "mount" $FSFILE

# Save important HC files
cp /opt/honeycomb/config/config.properties $DIR
cp /opt/honeycomb/version $DIR

# XXX: should we save the log, too? may be large.
# cp /var/adm/messages $DIR

# TODO FROM CHEAT
# A dump of the current switch rules would be nice.
# The date from the switch.
# via CLI: "hwstat", "syscfg"
# cmm_verifier output - see https://hc-twiki.sfbay.sun.com/bin/view/Main/CmTools

# OTHER TOOLS
# The ability to get 'snoop' output from arbitrary interfaces.
# The ability to get a running stack trace from all jvms
# And get HC logs...


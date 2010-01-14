#!/bin/bash
#
# $Id: cmcheck.sh 10856 2007-05-19 02:58:52Z bberndt $
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
# This script runs from the cheat node, collects CM-related info.
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
DIR=/tmp/state-cluster
rm -rf $DIR
mkdir $DIR

# CMM verifier

# This assumes cmm_cheat_install has been done
# and node_config.xml for the cluster was copied to cheat

NUMNODES=8 # XXX input cluster size somehow
CMMVERFILE=${DIR}/cmmverf
log_output "/opt/test/bin/cmm_verifier $NUMNODES" $CMMVERFILE

# This assumes irules.sh has been put in /opt/test/bin
SWITCHFILE=${DIR}/switch
log_output "/opt/test/bin/irules.sh" $SWITCHFILE

# XXX: should we save the log, too? may be large.
# cp /var/adm/messages $DIR

# TODO FROM CHEAT
# Get the date from the switch.
# via CLI: "hwstat", "syscfg"

# OTHER TOOLS
# The ability to get 'snoop' output for traffic between all nodes,
# this may be possible if we mirror all traffic to the cheat via switch,
# pending investigation by Mike. (Otherwise snoop per-node). 




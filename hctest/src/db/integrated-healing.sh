#!/bin/bash
#
# $Id: integrated-healing.sh 10858 2007-05-19 03:03:41Z bberndt $
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

WHEREAMI=`cd \`dirname $0\`; pwd`
POSTRESULT=$WHEREAMI/../../../suitcase/src/script/postResult.sh

post () {
    local result=$1
    shift
    echo $POSTRESULT -r $result $*
    $POSTRESULT -r $result $*
}

post pass -p "16-Nodes" Healing,SingleDisk,IdleCluster integrated-healing
post pass -p "DataDisks,16-Nodes" HealingBack,SingleDisk,IdleCluster integrated-healing
post skipped -p "WipedDisks,16-Nodes" HealingBack,SingleDisk,IdleCluster integrated-healing

post pass -p "16-Nodes" Healing,DualDisk,IdleCluster integrated-healing
post pass -p "DataDisks,16-Nodes" HealingBack,DualDisk,IdleCluster integrated-healing

post pass -p "16-Nodes" Healing,SingleNode,IdleCluster integrated-healing
post pass -p "DataDisks,16-Nodes" HealingBack,SingleNode,IdleCluster integrated-healing

post pass -p "16-Nodes" Healing,DualNode,IdleCluster integrated-healing
post pass -p "DataDisks,16-Nodes" HealingBack,DualNode,IdleCluster integrated-healing

post pass -p "16-Nodes" Healing,SingleDisk,ClientLoad integrated-healing
post pass -p "DataDisks,16-Nodes" HealingBack,SingleDisk,ClientLoad integrated-healing

post skipped -p "16-Nodes" Healing,DualDisk,ClientLoad integrated-healing
post skipped -p "DataDisks,16-Nodes" HealingBack,DualDisk,ClientLoad integrated-healing

post skipped -p "16-Nodes" Healing,DualNode,IdleCluster integrated-healing
post skipped -p "DataDisks,16-Nodes" HealingBack,DualNode,IdleCluster integrated-healing

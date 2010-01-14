#!/bin/bash
#
# $Id: rebootRolling.sh 10858 2007-05-19 03:03:41Z bberndt $
#
# This test reboots cluster nodes one by one in round-robbin order.
# It logs cluster state (condensed output of hwstat) between reboots.
# Time between reboots is configurable.
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

USAGE="$0 <cluster> <sleep_interval>"

if test $# != 2 ; then
  echo $USAGE
  exit 1
fi

HOST=`hostname`
cheat=`echo $HOST | egrep 'cheat|sp|hcb100'`
if test -z $cheat ; then
    echo MUST BE RUN FROM devXXX-cheat
    exit 1
fi

OS=`uname`
if test $OS == "Linux"; then
    HEAD="head -n "
    TAIL="tail -n "
else
    HEAD="head -"
    TAIL="tail -"
fi

CLUSTER=$1
CLI=admin@${CLUSTER}-admin

SLEEP=$2
mins=`expr $SLEEP / 60`

STATEFILE=cluster.${CLUSTER}.state
nodenum=1
iter=1

echo "Starting rolling reboot test on cluster $CLUSTER with $SLEEP-second ($mins-minute) reboot intervals"

while [ 1 ]
do
    echo ITERATION $iter

    # get cluster state from CLI
    ssh $CLI hwstat |grep "IN-CLUSTER" >$STATEFILE 2>&1

    # log cluster state to stdout    
    TIMESTAMP=`date`
    echo [$TIMESTAMP] CLUSTER STATE:
    cat $STATEFILE
    echo

    # must update total node count because we may have lost nodes
    totalnodes=`cat $STATEFILE |wc -l |awk '{print $1}'`
    if [ $nodenum -eq 0 ] ; then
        nodenum=$totalnodes
    elif [ $nodenum -gt $totalnodes ] ; then
        nodenum=$totalnodes
    fi

    # HCID-002-103    Motherboard     [IN-CLUSTER MASTER ELIGIBLE]
    HOST=""
    NODE=`cat $STATEFILE |${TAIL}${nodenum} |${HEAD}1 |awk '{print $1}' | sed -e 's/^..........//'`

    # round-robbin picking of the victim node
    nodenum=`expr $nodenum - 1`

    if [ -n "$NODE" ] ; then
        echo "REBOOTING NODE ${NODE}"
        HOST=hcb1$NODE
        echo ssh "${HOST} reboot &"
        /usr/bin/logger "$0 REBOOT -f ${HOST}/${HOST}"
        ssh ${HOST} "reboot" &
    else
        /usr/bin/logger "$0 NO CLUSTER"
    fi

    echo
    echo "sleep $SLEEP seconds ($mins minutes) ..."
    echo
    sleep $SLEEP
    iter=`expr $iter + 1`
done

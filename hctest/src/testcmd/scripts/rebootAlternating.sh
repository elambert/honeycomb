#!/bin/bash
#
# $Id: rebootAlternating.sh 10858 2007-05-19 03:03:41Z bberndt $
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
cheat=`echo $HOST | egrep 'cheat|hcb100'`
if test -z $cheat ; then
    echo MUST BE RUN FROM devXXX-cheat
    exit 1
fi

CLUSTER=$1
CLI=admin@${CLUSTER}-admin

SLEEP=$2

odd=y
odd2=y

while [ 1 ]
do
    # HCID-002-103    Motherboard     [IN-CLUSTER MASTER ELIGIBLE]
    HOST=""
    if test $odd == "y" ; then
        NODE=`ssh $CLI hwstat |grep " MASTER "| awk '{print $1}' | sed -e 's/^..........//'`
        if [ -n "$NODE" ] ; then
            odd="n"
            echo "rebooting master node ${NODE}"
        fi
    else
        if test $odd2 == "y" ; then
            NODE=`ssh $CLI hwstat | grep "IN-CLUSTER" | egrep -v " MASTER "| tail -n 1 | awk '{print $1}' | sed -e 's/^..........//'`
            odd2="n"
        else
            NODE=`ssh $CLI hwstat | grep "IN-CLUSTER" | egrep -v " MASTER "| head -n 1 | awk '{print $1}' | sed -e 's/^..........//'`
            odd2="y"
        fi
        if [ -n "$NODE" ] ; then
            odd="y"
            echo "rebooting non-master node ${NODE}"
        fi
    fi
    if [ -n "$NODE" ] ; then
        HOST=hcb1$NODE
        echo ssh "${HOST} reboot -f &"
        /usr/bin/logger "$0 REBOOT -f ${HOST}/${HOST}"
        ssh ${HOST} "reboot -f" &
    else
        /usr/bin/logger "$0 NO CLUSTER"
    fi
    echo sleep $SLEEP ...
    sleep $SLEEP
done

#!/bin/bash
#
# $Id: alternatingExceptions.sh 10858 2007-05-19 03:03:41Z bberndt $
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
# This script needs to be run from the cheat node since it
# ssh's cmds to the individual nodes.
#
# The relevant 'ErrInj' services need to be configured in
# honeycomb/src/config/node_config.xml.in - e.g. CONFIG
# derives from ErrInj_CONFIG in the CONFIG-SERVERS JVM:
#
# <jvm
#   name="CONFIG-SERVERS"
#   rss="128MB"
#   params="-server -Xms64m -Xmx128m">
#   <group
#       runlevel="3">
#       <service
#           name="Layout"
#           class="layout.LayoutService"/>
#   </group>
#   <group
#       runlevel="1">
#       <service
#           name="ErrInj_CONFIG"
#           class="cm.err_inj.ErrInj"/>
#   </group>
# </jvm>
#
# Here is the list of expected services:
#
JVMS[0]=PLATFORM
JVMS[1]=CONFIG
JVMS[2]=API
JVMS[3]=IO
N_JVMS=4

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
c=0

while [ 1 ]
do
    JVM=${JVMS[$c]}
    c=$(( $c + 1 ))
    if test $c -eq $N_JVMS ; then
        c=0
    fi

    # HCID-002-103    Motherboard     [IN-CLUSTER MASTER ELIGIBLE]
    HOST=""
    if test $odd == "y" ; then
        NODE=`ssh $CLI hwstat |grep " MASTER "| awk '{print $1}' | sed -e 's/^..........//'`
        if [ -n "$NODE" ] ; then
            odd="n"
            echo "sending exception to jvm $JVM on master node ${NODE}"
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
            echo "sending exception to jvm $JVM on non-master node ${NODE}"
        fi
    fi
    if [ -n "$NODE" ] ; then
        HOST=hcb1$NODE
        /usr/bin/logger "$0 sending exception to jvm $JVM on $HOST"
        ssh $HOST "/usr/lib/java/bin/java -Djava.library.path=/opt/honeycomb/lib -classpath /opt/honeycomb/lib/honeycomb.jar -Djava.util.logging.config.file=/opt/honeycomb/share/logging.properties com.sun.honeycomb.cm.err_inj.ErrInjClient $JVM -s excep"

    else
        /usr/bin/logger "$0 NO CLUSTER"
    fi
    echo sleep $SLEEP ...
    sleep $SLEEP
done

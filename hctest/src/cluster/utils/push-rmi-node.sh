#!/bin/bash
#
# $Id: push-rmi-node.sh 10858 2007-05-19 03:03:41Z bberndt $
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




INSTALL_DIR=/data/0

echo "installing/running from $INSTALL_DIR"
echo "which is different from what will run on reboot"

# 
# Push node RMI server from sp to nodes, start 'em executing.
# /opt/hctest must populated on sp.
# Use in the form "push-rmi-node.sh Y" where y is the highest node.
# eg, ./push-rmi-node.sh 8 
#
if [ -z "$1" ] ; then 
    echo "Please supply an end node number"
    exit 1
fi 

nodeStartNumber=1
nodeEndNumber=$1
user=root

islinux=`uname -a | grep -i linux`
if [ -z "$islinux" ] ; then 
    islinux='false'
else
    islinux='true'
fi

while test $nodeStartNumber -le $nodeEndNumber; do
    curNode=$nodeStartNumber
    ip=`expr $nodeStartNumber + 100`
    node=hcb$ip
    echo =================== $node    
    
    if [ $islinux == "true" ] ; then 
        res=`ping -q -c 1 $node |  grep '^rtt' | awk '{print $1}'`
        if [ ! "$res" == "rtt" ] ; then
            res=""
        fi
    else
        res=`ping $node | grep alive `
    fi
    if [ ! -z "$res" ] ; then
        echo "attempting ssh"
        ssh $node -l $user pkill -9 -f NodeSrvServer
        echo "killed NodeMgr"
        ssh $node -l $user "rm -rf $INSTALL_DIR/hctest"
        echo "removed old $INSTALL_DIR/hctest"
        scp -r /opt/hctest $user@$node:$INSTALL_DIR/
        echo "copied target"
        ssh $node -l $user " $INSTALL_DIR/hctest/bin/RunNodeSrv > /dev/null 2>&1 &"
        echo "launched server."
    else 
        echo $node is dead.
    fi
    nodeStartNumber=`expr $nodeStartNumber + 1`
done


#!/bin/bash
#
# $Id: natrules_verifier.sh 10858 2007-05-19 03:03:41Z bberndt $
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

if [ $# -lt 2 ]; then
    echo "$0: <admin vip> <no. of nodes>"
    exit 1
fi 

ADMINVIP=$1
NUM_NODES=$2

START_TIME=`date`
SSHARGS=" -l root -o StrictHostKeyChecking=no -q " 
SSH="/usr/bin/ssh"

# Test status, set to pass
s=0
p=1

# ssh node nat rules 
while [ $p -le $NUM_NODES ];
do
    port=`expr $p + 2000`
    hostname=hcb`expr $p + 100` 
    host=`$SSH -p $port $SSHARGS $ADMINVIP hostname` 
    if [ $? -ne 0 ]; then
        s=1 
        echo "incorrect nat rule for host $hostname" 
    else 
        if [ "$host" != "$hostname" ]; then
            echo "incorrect hostname, expected $hostname got $host"
            s=1
        fi 
    fi
    p=`expr $p + 1` 
done

if [ "$s" == "0" ]; then
    echo "iptables for cluster nodes ... OK"
fi 

# switch nat rule
$SSH -p 2222 -l nopasswd -o StrictHostKeyChecking=no -q $ADMINVIP date >> /dev/null 
if [ $? -ne 0 ]; then
    s=1 
    echo "incorrect nat rule for switch"
else 
    echo "ssh switch iptables ... OK"
fi
END_TIME=`date`

if [ $s -eq 0 ]; then
    echo "iptables test: PASSED [Start Time: $START_TIME End Time: $END_TIME]"
else 
    echo "iptables test: FAILED [Start Time: $START_TIME End Time: $END_TIME]"
fi

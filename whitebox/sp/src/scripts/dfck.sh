#!/bin/bash
#
# $Id: dfck.sh 10857 2007-05-19 03:01:32Z bberndt $
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
#  factory check to make sure all disks are mounted correctly -
#  to be run on cheat node
#

usage() {
    echo "Usage: $0 <num_nodes>"
    exit 1
}
 
if [ -z "$1" ] ; then
    usage
fi
if ! [ "$1" = "16"  -o "$1" = "8" ] ; then
    echo num_nodes must be 8 or 16
    usage
fi
NUM_NODES=$1

local_errors=0
nfs_errors=0
c=1
while [ $c -le $NUM_NODES ] ; do
    node=hcb`expr 100 + $c`
    echo =================== $node
    ssh $node df > /tmp/df.$$
    for i in 0 1 2 3 ; do
        drive="/data/$i"
        tmp=`grep ^$drive /tmp/df.$$`
        if [ -z "$tmp" ] ; then
            missing[local_errors]="${node}:$drive"
            local_errors=`expr $local_errors + 1`
        fi
    done
    d=1
    while [ $d -le $NUM_NODES ] ; do
        if [ $d -ne $c ] ; then
            node2="10.123.45.`expr 100 + $d`"
            for i in 0 1 2 3 ; do
                drive="/netdisks/$node2/data/$i"
                tmp=`grep ^$drive /tmp/df.$$`
                if [ -z "$tmp" ] ; then
                    missing_nfs[nfs_errors]="${node}:$drive"
                    nfs_errors=`expr $nfs_errors + 1`
                fi
            done
         fi
         d=`expr $d + 1`
    done
    c=`expr $c + 1`
done

if [ $local_errors -ne 0 ] ; then
    echo "MISSING LOCAL MOUNTS: $local_errors"
    c=0
    while test $c -lt $local_errors ; do
        echo "  " ${missing[$c]}
        c=`expr $c + 1`
    done
    exit 1
fi
if [ $nfs_errors -ne 0 ] ; then
    echo "MISSING NFS MOUNTS: $nfs_errors"
    c=0
    while test $c -lt $nfs_errors ; do
        echo "  " ${missing_nfs[$c]}
        c=`expr $c + 1`
    done
    exit 1
fi

echo OK
exit 0

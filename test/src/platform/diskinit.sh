#!/bin/sh
#
# $Id: diskinit.sh 10856 2007-05-19 02:58:52Z bberndt $
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

# get all partitions known
# fdisk them each into 2: 1 for ext3 for boot, and one for xfs for data
for fs in `tail -n 4 /proc/partitions | awk '{print $4}'`
do
fdisk /dev/$fs << EOF
n
p
1
1
+123
n
p
2
125
30515
w
EOF
done

# now create ext3 file systems on all disks first partition
for fs in `cat /proc/partitions | grep sd[a,b,c,d]1 | awk '{print $4}'`
do
mkfs.ext3 -f /dev/$fs
done

# now create xfs file systems on the #2 partition
for fs in `cat /proc/partitions | grep sd[a,b,c,d]2 | awk '{print $4}'`
do
mkfs.xfs -f /dev/$fs
done

mkdir /data
mkdir /data/0
mkdir /data/1
mkdir /data/2
mkdir /data/3
# mount all the disks
mount -a
mount /dev/sda2 /data/0
mount /dev/sdb2 /data/1
mount /dev/sdc2 /data/2
mount /dev/sdd2 /data/3

# just make sure they are all mounted
mount

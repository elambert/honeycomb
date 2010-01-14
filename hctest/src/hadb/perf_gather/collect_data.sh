#!/bin/bash
#
# $Id: collect_data.sh 10858 2007-05-19 03:03:41Z bberndt $
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

rm -rf perf_gather_dataset

mkdir perf_gather_dataset
SSHARGS="-q -o StrictHostKeyChecking=no"
n=0
while (( $n < 16 ))
do
        node=`printf "hcb1%02d" $[$n+1]`
        scp $SSHARGS $node:md_data perf_gather_dataset/md_data-$node
        scp $SSHARGS $node:disk_data perf_gather_dataset/disk_data-$node
        scp $SSHARGS $node:vmstat_data perf_gather_dataset/vmstat_data-$node
        scp $SSHARGS $node:mpstat_data perf_gather_dataset/mpstat_data-$node
        scp $SSHARGS $node:cpu_data perf_gather_dataset/cpu_data-$node
        scp $SSHARGS $node:/data/0/hadb/history/\* perf_gather_dataset
        n=$[$n+1]
	echo "Data collected from $node"
done
cp ops_data perf_gather_dataset
rm -f perf_gather_data_dataset.tar
tar cvf perf_gather_dataset.tar perf_gather_dataset


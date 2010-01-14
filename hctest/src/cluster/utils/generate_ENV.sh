#! /bin/sh
#
# $Id: generate_ENV.sh 3764 2005-03-04 22:12:49Z dm155201 $
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

# This script creates an ENV file for the given cluster, based on the
# template ENV file.

print_usage () {
    echo
    echo "usage: `basename $0` cluster num_nodes [ svnroot ]"
    echo        
}

gen_CLUSTER=$1
gen_NUMNODES=$2
gen_SVNROOT=$3
gen_MAX_NODES=16

gen_DEFAULTSVNROOT=`cd ../../../..; pwd`

if [ -z "$gen_CLUSTER" ]; then
    echo lalalalal
    print_usage
    exit 1
fi

if [ -z "$gen_NUMNODES" ]; then
    print_usage
    exit 1
fi

if [ $gen_NUMNODES -gt $gen_MAX_NODES ]; then
    echo "my, what a big cluster you have! ($gen_NUMNODES nodes ??)"
    print_usage
    exit 1
fi

if [ $gen_NUMNODES -lt 0 ]; then
    echo "cluster must have at least 1 node"
    print_usage
    exit 1
fi

if [ -z "$gen_SVNROOT" ]; then
    gen_SVNROOT=$gen_DEFAULTSVNROOT
fi

gen_ENV_TEMPLATE=$gen_SVNROOT/hctest/src/cluster/utils/ENV.in
gen_NEW_ENV=$gen_SVNROOT/hctest/src/cluster/utils/ENV.$gen_CLUSTER

if [ ! -f $gen_ENV_TEMPLATE ]; then
    echo "cannot find $gen_ENV_TEMPLATE"
    exit 1
fi

if [ -f $gen_NEW_ENV ]; then
    echo "$gen_NEW_ENV already exists"
    exit 1
fi

cp $gen_ENV_TEMPLATE $gen_NEW_ENV

# add the data vip for this cluster
sed "s/SERVER_PUBLIC_DATA_VIP=\"\"/SERVER_PUBLIC_DATA_VIP=$gen_CLUSTER-data/" $gen_NEW_ENV > $gen_NEW_ENV.temp 
mv $gen_NEW_ENV.temp $gen_NEW_ENV

# uncomments lines for nodes in this cluster
i=0
while [ $i -le $gen_NUMNODES ]; do
    if [ $i -lt 10 ]; then
        gen_HOSTNAME=hcb10$i
    else
        gen_HOSTNAME=hcb1$i
    fi
    sed "s/# SERVER_PRIVATE_IPS=\"$gen_HOSTNAME/SERVER_PRIVATE_IPS=\"$gen_HOSTNAME/" $gen_NEW_ENV > $gen_NEW_ENV.temp
    mv $gen_NEW_ENV.temp $gen_NEW_ENV
    i=`expr $i + 1`
done



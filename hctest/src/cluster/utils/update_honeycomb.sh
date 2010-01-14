#! /bin/sh
#
# $Id: update_honeycomb.sh 10858 2007-05-19 03:03:41Z bberndt $
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

#
# Use this script to upgrade the honeycomb software in a boot image
# in the world where there is NO MORE CHEAT NODE with a new
# honeycomb-bin.tar.gz that is built after an 'ant tar'.
#
# This script makes assumptions about files being in certain places,
# so some editing is needed to get it to work in your config.
#

# working dir for images, honeycomb -- change this as needed
dir=`pwd`

# non-cheat node image -- change this as needed
img=$dir/initrd
vers=`basename $img`

# new HC build
hc=$dir/honeycomb-bin.tar.gz

# file to track updates
updates=$dir/HC_VERSION

# config file for cluster
configdir=$dir/config
clustconfig=$configdir/cluster_config.properties

if [ ! -f $img.gz ]; then
    echo "ERROR: File $img.gz does not exist"
    exit 1
fi

if [ ! -f $hc ]; then
    echo "ERROR: File $hc does not exist"
    exit 1
fi

if [ ! -d $configdir ]; then
    echo "ERROR: Directory $noncheatconfigdir does not exist"
    echo "The following files for the non-cheat node image must exist:"
    echo " $clustconfig"
    exit 1
fi

if [ ! -f $clustconfig ]; then
    echo "ERROR: File $clustconfig does not exist"
    exit 1
fi

echo
echo "Updating initrd with..."
ls -l $hc
echo

set -x
gunzip $img.gz
mkdir -p $dir/mnt
mount $img -o loop $dir/mnt
mkdir -p $dir/mnt/opt/honeycomb
cd $dir/mnt/opt/honeycomb
rm -rf $dir/mnt/opt/honeycomb/*
tar -xzvf $hc
cp $configdir/* share/

# add timestamp
ls -l $hc >> $dir/mnt/HC_VERSION

cd $dir
umount $dir/mnt
gzip $img

set +x
date >> $updates
echo "HC software updated with" >> $updates
ls -l $hc >> $updates
echo "" >> $updates

echo
tail -n 4 $updates

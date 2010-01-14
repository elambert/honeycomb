#!/bin/sh
#
# $Id: install_linux.sh 10853 2007-05-19 02:50:20Z bberndt $
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

# Mandatory arguments
# $1 is the node to install to
# $2 is the path to the bzImage file
# $3 is the path to the initrd.gz file

case $# in
1)  node="$1" bzImage=bzImage initrd_gz=initrd.gz;;
3)  node="$1" bzImage="$2"    initrd_gz="$3";;
*)  echo "usage $0 <node> <bzImage> <initrd.gz>"
    exit 2
esac

boot_dir=/boot/0/boot
install_version=default
install_dir=$boot_dir/versions/$install_version

make_installdir="
   mkdir -p $install_dir"
make_bootable="
  rm -f $boot_dir/active && ln -s versions/$install_version $boot_dir/active &&
  /var/boot/install_local.sh || echo 'Failed!'"


echo "Trying to install on node $node..."

if [ -f $bzImage -o -f $initrd_gz ] ; then
	echo "Creating $node:$install_dir"
	ssh -n $node "$make_installdir"
fi

if [ -f $bzImage ]
then
    echo "Copying $bzImage to $node:$install_dir..."
    scp $bzImage $node:$install_dir/bzImage
else
    echo "Could not find $bzImage on local machine. Exiting..."
    exit 1
fi

if [ -f $initrd_gz ]
then
    echo "Copying $initrd_gz to $node:$install_dir..."
    scp $initrd_gz $node:$install_dir/initrd.gz
else
    echo "Could not find $initrd_gz on local machine. Exiting..."
    exit 1
fi

ssh -n $node "$make_bootable"
ssh -n $node "cat $boot_dir/log; ls -l $boot_dir/active/."


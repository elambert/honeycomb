#! /bin/sh
#
# $Id: update_honeycomb_linux.sh 10858 2007-05-19 03:03:41Z bberndt $
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
# in the world where the nodes boot off the cheat node but the cheat
# isn't a cluster member.
#
# This script makes assumptions about files being in certain places,
# so some editing is needed to get it to work in your config.
#
# open    - unzip and mount the initrd via loopback   
# replace - put local hcjars and hcconfig into initrd 
# close   - zip the initrd and copy into boot directory

# string to print when we're passed incorrect args 
usage="Usage: $0 [ open | replace | close ] "

mntdir=mnt 			# directory where we'll loop mount 
bootdir=/tftpboot		# install directory for cluster nodes
jardir=/opt/honeycomb/lib	# directory for honeycomb jars
configdir=/opt/honeycomb/share	# directory for honeycomb config
startup=/opt/honeycomb/etc/init.d/honeycomb   # honeycomob startup script 
img=initrd     			# node image for linux
kernel=bzImage 			# linux kernel
updates=HC_VERSION 		# file to track updates locally
version=/HC_VERSION             # file in image where we note jar version
hcconfig=cluster_config.properties # per-cluster hc config file
hcjars="*.jar"	                # honeycomb jars

# check args and set mode
if [ $# -eq 0 ]; then
    mode=all
else
    case $* in
    open | replace | close)
        mode=$1
        ;;
    *)  
        echo "ERROR: unexpected argument: $*"; echo $usage; exit 1
    esac
fi

# must be root to mount on loopback device
if [ "$USER" != "root" ]; then
    echo "ERROR: must run as root, you are logged in as $USER";
    echo "(try running on a cheat node)"; exit 1
fi

# open and mount the initrd
if [ $mode = all -o $mode = open ]; then
    if [ ! -d $mntdir ]; then
        mkdir -p $mntdir
        if [ $? = 1 ]; then
            echo "ERROR: unable to create $mntdir"; exit 1
        fi
    fi
    if [ ! -f $img.gz ]; then
        echo "ERROR: zipped image $img.gz does not exist"
        echo "(did you forget to close after opening the initrd?)"; exit 1
    fi
    echo "Opening $img.gz..."
    gunzip $img.gz
    mount $img -o loop $mntdir
    echo "$img.gz opened and loop mounted on $mntdir"
fi

# replace jars and config file in initrd with new versions 
if [ $mode = all -o $mode = replace ]; then
    found="`ls $hcjars 2> /dev/null`"
    if [ "$found" == "" ]; then
        echo "ERROR: no honeycomb jars found: $hcjars"; exit 1
    fi
    if [ ! -f $hcconfig ]; then
        echo "ERROR: honeycomb config $hcconfig does not exist"; exit 1
    fi
    if [ ! -d $mntdir/$configdir ]; then
        echo "ERROR: $mntdir/$configdir not found"; exit 1
    fi
    if [ ! -d $mntdir/$jardir ]; then
        echo "ERROR: $mntdir/$jardir not found"; exit 1
    fi
    if [ ! -f $mntdir/$startup ]; then
        echo "ERROR: $mntdir/$startup not found"; exit 1
    fi

    cp -f $hcjars $mntdir/$jardir	# copy into the initrd
    cp -f $hcconfig $mntdir/$configdir
    chmod uog+r $mntdir/$jardir/*	# set world-readable
    chmod uog+r $mntdir/$configdir/*
    chown -R 2000 $mntdir/opt/honeycomb
    chgrp -R 1000 $mntdir/opt/honeycomb
    chown -R 2000 $mntdir/home/admin
    chmod 750 $mntdir/opt/honeycomb
    chmod 770 $mntdir/opt/honeycomb/config
    chmod 700 $mntdir/opt/honeycomb/.ssh        
    chmod 700 $mntdir/opt/honeycomb/home/admin
    chmod 700 $mntdir/opt/honeycomb/home/admin/.ssh

    # comment out next line to enable automatic reboot
    sed -i '/^automatic_reboot/s/yes/no/' $mntdir/$startup

    ls -l $hcjars > $mntdir/$version 	# record jar version
    date >> $updates			# add timestamp to local log
    echo "HC software updated with" >> $updates
    ls -l $hcjars >> $updates
    echo "" >> $updates
    echo
    tail -n 4 $updates
fi

# close and copy to the install directory
if [ $mode = all -o $mode = close ]; then
    if [ ! -f $img ]; then
        echo "ERROR: Cannot close, $img does not exist"; exit 1
    fi
    if [ -f $img.gz ]; then
        echo "ERROR: Cannot close, $img.gz already exists"; exit 1
    fi
    if [ ! -d $mntdir ]; then
        echo "ERROR: Cannot close, mntdir $mntdir not found"; exit 1
    fi
    echo "Closing $img..."
    umount $mntdir
    gzip $img
    if [ ! -d $bootdir ]; then
       echo "ERROR: bootdir $bootdir does not exist"; exit 1
    fi
    if [ ! -f $kernel ]; then
       echo "ERROR: kernel $kernel does not exist"; exit 1
    fi
    cp $img.gz $bootdir
    cp $kernel $bootdir
    echo "Closed $img.gz and copied to $bootdir"
fi
 


#!/bin/sh
#
# $Id: disk_check.sh 10855 2007-05-19 02:54:08Z bberndt $
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

# Set this to 1 to get verbose messages
verbose=0

# Number of expected partitions
numpartitions=2

# Size of the partitions
sizes[1]=1024
sizes[2]=""

# Expected number of blocks for the partitions
blocks[1]=1052257
blocks[2]=244059480

trace ()
{
    if [ $verbose -eq 1 ]
    then
        echo $1
    fi
}

usage ()
{
    trace "Usage: disk_check {checkpartition|initpartition|checkfs|initfs} <device>"
    trace "E.g. disk_check check /dev/hda"
    exit 1
}

if [ $# -ne 2 ]
then
    usage
fi

# Check options passed
case "$1" in
    checkpartition)
        trace "Checking partitions for $2..."
	# Check to see if the number of partitions match
	partitions=`/sbin/sfdisk -l -uM $2 | grep + | wc -l | awk '{print $1}'`
	if [ "$partitions" != "$numpartitions" ]
	then
	    trace "Number of partitions do not match..."
	    trace "Expected $numpartitions got $partitions"
	    exit 1
	fi

	# Check to see if the partitions are of the correct sizes
        # Don't check the last partition for now
 	partition_number=1
	while [ $partition_number -lt $numpartitions ]
	do
	    size=`/sbin/sfdisk -s $2$partition_number`
	    expected_size=${blocks[$partition_number]}
	    if [ $size -ne $expected_size ]
	    then
		trace "Partition size does not match for $2$partition_number"
		trace "expected $expected_size got $size"
		exit 1		
	    fi

	    partition_number=`expr $partition_number + 1`
	done
    ;;

    initpartition)
	trace "Initializing partitions for $2..."

        partition_command=""
 	partition_number=1
	while [ $partition_number -le $numpartitions ]
	do
	    size=${sizes[$partition_number]}
	    partition_command=$partition_command",$size\n"
	    partition_number=`expr $partition_number + 1`
	done

	trace -e $partition_command
        echo -e $partition_command | /sbin/sfdisk -uM $2 >& /dev/null
    ;;

    checkfs)
	trace "Checking filesystems for $2..."
        clean=`/bin/xfs_logprint -t $2 2>&1 | grep 'state: <CLEAN>'`
        if [ "$clean" == "" ]
	then
	    trace "Filesystem on $2 is not consistent..."
	    exit 1
	fi
    ;;

    initfs)
	trace "Initializing filesystems for $2..."
	/sbin/mkfs.xfs -f $2 >& /dev/null
	if [ $? -ne 0 ]
	then
	    trace "Failed to create an xfs filesystem on $2"
	    exit 1
	fi
    ;;

    *)
	usage
esac

exit 0

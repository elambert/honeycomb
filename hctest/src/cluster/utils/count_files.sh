#! /bin/sh
#
# $Id: count_files.sh 10858 2007-05-19 03:03:41Z bberndt $
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

# count how many files we have on each disk, mostly to get the total number
# of fragments on a given disk

MNT="/data"
DISKS="0 1 2 3 4 5 6 7"

for disk in $DISKS
do
	totalcnt=`find $MNT/$disk -type f | wc -l`
	echo "$MNT/$disk has $totalcnt normal files (including MD, tmp, and .)"

	mdcnt=`find $MNT/$disk/MD_cache -type f | wc -l`
	echo "  $MNT/$disk/MD_cache has $mdcnt MD_cache files"

	tmpcnt=`find $MNT/$disk/tmp -type f | wc -l`
	echo "  $MNT/$disk/tmp has $tmpcnt tmp files"

	localcnt=`find $MNT/$disk/. -type f -maxdepth 1 | wc -l`
	echo "  $MNT/$disk/. has $localcnt normal files in it"

	fragcnt=`expr $totalcnt - $mdcnt - $tmpcnt - $localcnt`
	echo "$MNT/$disk has $fragcnt fragment files in it"

	echo "-------------------------------------------"
done

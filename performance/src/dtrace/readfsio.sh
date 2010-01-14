#!/usr/bin/ksh
#
# $Id: readfsio.sh 10845 2007-05-19 02:31:46Z bberndt $
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



## 

/usr/sbin/dtrace -C -s <( print -r '
/*
 * readfsio.sh - read FS I/O stats, with cache miss rate.
 *
 * This script provides statistics on the number of reads and the bytes
 * read from filesystems (logical), and the number of bytes read from
 * disk (physical). A summary is printed every five seconds by filesystem.
 *
 * A total miss-rate is also provided for the file system cache.
 */

#pragma D option quiet

self int trace;
uint64_t lbytes;
uint64_t pbytes;

dtrace:::BEGIN
{
	trace("Tracing...\n");
}

fbt::fop_read:entry
/self->trace == 0/
{
	self->fs_mount = args[0]->v_vfsp == `rootvfs ? "/" :
	    args[0]->v_vfsp->vfs_vnodecovered ?
	    stringof(args[0]->v_vfsp->vfs_vnodecovered->v_path) : NULL;
}

fbt::fop_read:entry
/self->fs_mount != NULL/
{
	@rio[self->fs_mount, "logical"] = count();
	lbytes += args[1]->uio_resid;
	self->size = args[1]->uio_resid;
	self->uiop = args[1];
}

fbt::fop_read:return
/self->size/
{
	@rbytes[self->fs_mount, "logical"] =
	    sum(self->size - self->uiop->uio_resid);
	self->size = 0;
	self->uiop = 0;
	self->fs_mount = 0;
}

io::bdev_strategy:start
/self->size && args[0]->b_flags & B_READ/
{
	@rio[self->fs_mount, "physical"] = count();
	@rbytes[self->fs_mount, "physical"] = sum(args[0]->b_bcount);
	pbytes += args[0]->b_bcount;
}

profile:::tick-5s
{
	trunc(@rio, 20);
	trunc(@rbytes, 20);
	printf("\033[H\033[2J");
	printf("\nRead IOPS (count)\n");
	printa("%-32s %10s %10@d\n", @rio);
	printf("\nRead Bandwidth (bytes)\n");
	printa("%-32s %10s %10@d\n", @rbytes);
	printf("\nTotal File System miss-rate: %d%%\n",
	    lbytes ? 100 * pbytes / lbytes : 0);
	trunc(@rbytes);
	trunc(@rio);
	lbytes = pbytes = 0;
}
')
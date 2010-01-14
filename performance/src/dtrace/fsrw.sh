#!/usr/bin/ksh
#
# $Id: fsrw.sh 10845 2007-05-19 02:31:46Z bberndt $
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
/usr/sbin/dtrace -n '
/*
 * fsrw.sh - file system read/write event tracing.
 *
 * This traces file related activity: system call reads and writes,
 * vnode logical read and writes (fop), and disk I/O. It can be used
 * to examine the behaviour of each I/O layer, from the syscall
 * interface to what the disk is doing. Behaviour such as read-ahead, and
 * max I/O size breakup can be observed.
 *
 * USAGE:	fsrw.sh
 *
 * FIELDS:
 *		Event		Traced event (see EVENTS below)
 *		Device		Device, for disk I/O
 *		RW		Either Read or Write
 *		Size		Size of I/O in bytes
 *		Offset		Offset of I/O in kilobytes
 *		Path		Path to file on disk
 *
 * EVENTS:
 *		sc-read		System call read
 *		sc-write	System call write
 *		fop_read	Logical read
 *		fop_write	Logical write
 *		disk_io		Physical disk I/O
 *		disk_ra		Physical disk I/O, read ahead
 *
 * The events are drawn with a level of indentation, which can sometimes
 * help identify related events.
 */

#pragma D option quiet
#pragma D option switchrate=10hz

dtrace:::BEGIN
{
	printf("%-12s %10s %2s %8s %6s %s\n",
	    "Event", "Device", "RW", "Size", "Offset", "Path");
}

syscall::*read:entry,
syscall::*write*:entry
{
	/*
	 * starting with a file descriptior, dig out useful info
	 * from the corresponding file_t and vnode_t.
	 */
	this->filistp = curthread->t_procp->p_user.u_finfo.fi_list;
	this->ufentryp = (uf_entry_t *)((uint64_t)this->filistp +
	    (uint64_t)arg0 * (uint64_t)sizeof (uf_entry_t));
	this->filep = this->ufentryp->uf_file;
	self->offset = this->filep->f_offset;
	this->vnodep = this->filep != 0 ? this->filep->f_vnode : 0;
	self->vpath = this->vnodep ? (this->vnodep->v_path != 0 ?
	    cleanpath(this->vnodep->v_path) : "<unknown>") : "<unknown>";

	/* only trace activity to regular files and directories, as */
	self->sc_trace = this->vnodep ? this->vnodep->v_type == VREG ||
	    this->vnodep->v_type == VDIR ? 1 : 0 : 0;
}

syscall::*read:entry
/self->sc_trace/
{
	printf("sc-%-9s %10s %2s %8d %6d %s\n", probefunc, ".", "R",
	    (int)arg2, self->offset / 1024, self->vpath);
}

syscall::*write*:entry
/self->sc_trace/
{
	printf("sc-%-9s %10s %2s %8d %6d %s\n", probefunc, ".", "W",
	    (int)arg2, self->offset / 1024, self->vpath);
}

syscall::*read:return,
syscall::*write*:return
{
	self->vpath = 0;
	self->offset = 0;
	self->sc_trace = 0;
}

fbt::fop_read:entry,
fbt::fop_write:entry
/self->sc_trace && args[0]->v_path/
{
	printf("  %-10s %10s %2s %8d %6d %s\n", probefunc, ".",
	    probefunc == "fop_read" ? "R" : "W", args[1]->uio_resid,
	    args[1]->_uio_offset._f / 1024, cleanpath(args[0]->v_path));
}

fbt:ufs:ufs_getpage_ra:entry
{
	/* fetch the real offset (file_t is unaware of this) */
	self->ra_offset = ((inode_t *)args[0]->v_data)->i_nextrio;
	self->read_ahead = 1;
}

fbt:ufs:ufs_getpage_ra:return
{
	self->read_ahead = 0;
	self->ra_offset = 0;
}

io::bdev_strategy:start
{
	this->offset = self->read_ahead ? self->ra_offset : args[2]->fi_offset;
	printf("    %-8s %10s %2s %8d %6d %s\n",
	    self->read_ahead ? "disk_ra" : "disk_io", args[1]->dev_statname,
	    args[0]->b_flags & B_READ ? "R" : "W", args[0]->b_bcount,
	    this->offset / 1024, args[2]->fi_pathname);
	/*
	 * it would seem to make sense to only trace disk events during
	 * an fop event, easily coded with a self->fop_trace flag. However
	 * writes are asynchronous to the fop_write calls (they are flushed
	 * at some later time), and so this approach will miss tracing
	 * most of the disk writes.
	 */
}
'
#!/usr/bin/ksh
#
# $Id: stacksize.sh 10845 2007-05-19 02:31:46Z bberndt $
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
 * stacksize.sh - measure stack size for running threads.
 *
 * USAGE:       stacksize.sh              # hit Ctrl-C to end sample
 *
 * FIELDS:
 *		value		size of the user stack
 *		count		number of samples at this size
 */

#pragma D option quiet

this uintptr_t stkinfoptr;
this uintptr_t stkptr;

dtrace:::BEGIN
{
	trace("Sampling... Hit Ctrl-C to end\n");
}

sched:::on-cpu, profile:::profile-997
{
	this->stkinfoptr = 0;
	this->stkptr = 0;
}

sched:::on-cpu, profile:::profile-997
/execname != "sched"/
{
	this->stkinfoptr = curthread->t_lwp->lwp_ustack;
	this->stkptr = (uintptr_t)0;
}

sched:::on-cpu, profile:::profile-997
/this->stkinfoptr != 0 && curpsinfo->pr_dmodel == PR_MODEL_ILP32/
{
	this->stkinfo32 = (stack32_t *)copyin(this->stkinfoptr,
	    sizeof (stack32_t));
	this->stktop = (uintptr_t)this->stkinfo32->ss_sp +
	    this->stkinfo32->ss_size;
	this->stkptr = (uintptr_t)uregs[R_SP];
}

sched:::on-cpu, profile:::profile-997
/this->stkinfoptr != 0 && curpsinfo->pr_dmodel == PR_MODEL_LP64/
{
	this->stkinfo = (stack_t *)copyin(this->stkinfoptr,
	    sizeof (stack_t));
	this->stktop = (uintptr_t)this->stkinfo->ss_sp +
	    this->stkinfo->ss_size;
	this->stkptr = (uintptr_t)uregs[R_SP];
}

sched:::on-cpu, profile:::profile-997
/this->stkptr != 0/
{
	@sizes[execname] = quantize(this->stktop - this->stkptr);
}

dtrace:::ERROR
{
	@errors[execname] = count();
}

dtrace:::END
{
	printa(@sizes);
	printf("\nErrors:\n");
	printa("    %@d %s\n", @errors);
}
')
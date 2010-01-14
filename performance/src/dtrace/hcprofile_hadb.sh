#!/usr/bin/ksh
#
# $Id: hcprofile_hadb.sh 8637 2006-06-29 20:24:24Z gp198228 $
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
# hcprofile_hadb.sh - display stats related to hadb
#
# NOTE: clu_sql_srv spawns processes of same name (with different args).
# Stats are aggregated for now.
##

### default variables
interval=5; count=-1;

### process options
while getopts h name
do
  case $name in
    h|?)    cat <<-END >&2
                USAGE: hcprofile_hadb.sh output [interval [count]]
                   eg,
                        hcprofile_hadb.sh /tmp/d.out # 5 second samples
                        hcprofile_hadb.sh /tmp/d.out 1  # 1 second samples
		END
      exit 1
  esac
done

if [[ "$#" < 1 ]]; then
  $0 -h
  exit
fi

shift $(( $OPTIND - 1 ))

### option logic
output=$1; shift;

if [[ "$1" > 0 ]]; then
  interval=$1; shift
fi
if [[ "$1" > 0 ]]; then
  count=$1; shift
fi

#################################
exec /usr/sbin/dtrace -o $output -n '
inline int INTERVAL = '$interval';
inline int COUNTER  = '$count';

string hadbProcs[8];
int diskReadBytes[8];
int readCount[8];
int diskWriteBytes[8];
int writeCount[8];
int pagesPagedIn[8];
int netReadBytes[8];
int netWriteBytes[8];
int syscallCount[8];

#pragma D option quiet

/**********************************************************************/
dtrace:::BEGIN 
{
  /* starting values */
  counts = COUNTER;
  secs = INTERVAL;

  hadbProcs[1] = "clu_sql_srv";
  hadbProcs[2] = "clu_sqlshm_srv";
  hadbProcs[3] = "clu_relalg_srv";
  hadbProcs[4] = "clu_nsup_srv";
  hadbProcs[5] = "clu_trans_srv";
  hadbProcs[6] = "clu_noman_srv";
  hadbProcs[7] = "ma";

  diskReadBytes[1] = 0; readCount[1] = 0;
  diskReadBytes[2] = 0; readCount[2] = 0;
  diskReadBytes[3] = 0; readCount[3] = 0;
  diskReadBytes[4] = 0; readCount[4] = 0;
  diskReadBytes[5] = 0; readCount[5] = 0;
  diskReadBytes[6] = 0; readCount[6] = 0;
  diskReadBytes[7] = 0; readCount[7] = 0;

  diskWriteBytes[1] = 0; writeCount[1] = 0;
  diskWriteBytes[2] = 0; writeCount[2] = 0;
  diskWriteBytes[3] = 0; writeCount[3] = 0;
  diskWriteBytes[4] = 0; writeCount[4] = 0;
  diskWriteBytes[5] = 0; writeCount[5] = 0;
  diskWriteBytes[6] = 0; writeCount[6] = 0;
  diskWriteBytes[7] = 0; writeCount[7] = 0;

  pagesPagedIn[1] = 0;
  pagesPagedIn[2] = 0;
  pagesPagedIn[3] = 0;
  pagesPagedIn[4] = 0;
  pagesPagedIn[5] = 0;
  pagesPagedIn[6] = 0;
  pagesPagedIn[7] = 0;

  netWriteBytes[1] = 0; netReadBytes[1] = 0;
  netWriteBytes[2] = 0; netReadBytes[2] = 0;
  netWriteBytes[3] = 0; netReadBytes[3] = 0;
  netWriteBytes[4] = 0; netReadBytes[4] = 0;
  netWriteBytes[5] = 0; netReadBytes[5] = 0;
  netWriteBytes[6] = 0; netReadBytes[6] = 0;
  netWriteBytes[7] = 0; netReadBytes[7] = 0;

  syscallCount[1] = 0;
  syscallCount[2] = 0;
  syscallCount[3] = 0;
  syscallCount[4] = 0;
  syscallCount[5] = 0;
  syscallCount[6] = 0;
  syscallCount[7] = 0;

  printf("Tracing... Please wait.\n");
}

/**********************************************************************/
vminfo:::pgpgin,
syscall:::entry,
fbt::fop_read:entry,
fbt::fop_write:entry,
io:genunix::start
{
  this->procCount = (execname == hadbProcs[1]) ? 1 :
                    (execname == hadbProcs[2]) ? 2 :
                    (execname == hadbProcs[3]) ? 3 :
                    (execname == hadbProcs[4]) ? 4 :
                    (execname == hadbProcs[5]) ? 5 :
                    (execname == hadbProcs[6]) ? 6 :
                    (execname == hadbProcs[7]) ? 7 : 0;
}

/**********************************************************************/
fbt::fop_read:entry,
fbt::fop_write:entry
/this->procCount > 0/
{
  self->sockRWProc = args[0]->v_type == 9 ? this->procCount : 0;
  self->size = args[1]->uio_resid;
  self->uiop = args[1];
}

/**********************************************************************/
fbt::fop_read:return
/self->sockRWProc && self->uiop/
{
  this->resid = self->uiop->uio_resid;
  (self->sockRWProc > 0) ? (netReadBytes[self->sockRWProc]
                                 += (self->size - this->resid)) : 1;
  self->size = 0;
  self->uiop = 0;
}

/**********************************************************************/
fbt::fop_write:return
/self->sockRWProc && self->uiop/
{
  this->resid = self->uiop->uio_resid;
  (self->sockRWProc > 0) ? (netWriteBytes[self->sockRWProc]
                                 += (self->size - this->resid)) : 1;
  self->size = 0;
  self->uiop = 0;
}

/**********************************************************************/
io:genunix::start
/this->procCount > 0/
{
  diskReadBytes[this->procCount] += args[0]->b_flags & B_READ ? args[0]->b_bcount : 0;
  readCount[this->procCount] += args[0]->b_flags & B_READ ? 1 : 0;
}

/**********************************************************************/
io:genunix::start
/this->procCount > 0/
{
  diskWriteBytes[this->procCount] += args[0]->b_flags & B_READ ? 0 : args[0]->b_bcount;
  writeCount[this->procCount] += args[0]->b_flags & B_READ ? 0 : 1;
}

/**********************************************************************/
vminfo:::pgpgin
/this->procCount > 0/
{
  pagesPagedIn[this->procCount] += arg0;
}

/**********************************************************************/
syscall:::entry
/this->procCount > 0/
{
  syscallCount[this->procCount] += 1;
}

/**********************************************************************/
profile:::tick-1sec
{
  secs--;
}

/**********************************************************************
 * Print Report
 */
profile:::tick-1sec
/secs == 0/
{
  /* fetch 1 min load average */
  this->load1a  = `hp_avenrun[0] / 65536;
  this->load1b  = ((`hp_avenrun[0] % 65536) * 100) / 65536;
  
  /* convert counters to Kbytes */
  diskReadBytes[1] /= 1024; diskWriteBytes[1] /= 1024;
  diskReadBytes[2] /= 1024; diskWriteBytes[2] /= 1024;
  diskReadBytes[3] /= 1024; diskWriteBytes[3] /= 1024;
  diskReadBytes[4] /= 1024; diskWriteBytes[4] /= 1024;
  diskReadBytes[5] /= 1024; diskWriteBytes[5] /= 1024;
  diskReadBytes[6] /= 1024; diskWriteBytes[6] /= 1024;
  diskReadBytes[7] /= 1024; diskWriteBytes[7] /= 1024;
  
  netReadBytes[1] /= 1024; netWriteBytes[1] /= 1024; 
  netReadBytes[2] /= 1024; netWriteBytes[2] /= 1024; 
  netReadBytes[3] /= 1024; netWriteBytes[3] /= 1024; 
  netReadBytes[4] /= 1024; netWriteBytes[4] /= 1024; 
  netReadBytes[5] /= 1024; netWriteBytes[5] /= 1024; 
  netReadBytes[6] /= 1024; netWriteBytes[6] /= 1024; 
  netReadBytes[7] /= 1024; netWriteBytes[7] /= 1024; 

  printf("%Y,  load: %d.%02d\n\n", walltimestamp, this->load1a,
         this->load1b);

  printf("%-16s %-6s %-6s %-6s %-6s %-6s %-6s %-6s %-6s\n", "PROC", "#READS", "READ(KB)",
         "#WRITES", "WRITE(KB)", "#PAGESIN", "NETREAD", "NETWRITE", "#SYSCALLS");
  printf("%-16s %-6d %-8d %-7d %-9d %-8d %-8d %-8d %-8d\n", hadbProcs[1], readCount[1],
         diskReadBytes[1], writeCount[1], diskWriteBytes[1], pagesPagedIn[1],
         netReadBytes[1], netWriteBytes[1], syscallCount[1]);
  printf("%-16s %-6d %-8d %-7d %-9d %-8d %-8d %-8d %-8d\n", hadbProcs[2], readCount[2],
         diskReadBytes[2], writeCount[2], diskWriteBytes[2], pagesPagedIn[2],
         netReadBytes[2], netWriteBytes[2], syscallCount[2]);
  printf("%-16s %-6d %-8d %-7d %-9d %-8d %-8d %-8d %-8d\n", hadbProcs[3], readCount[3],
         diskReadBytes[3], writeCount[3], diskWriteBytes[3], pagesPagedIn[3],
         netReadBytes[3], netWriteBytes[3], syscallCount[3]);
  printf("%-16s %-6d %-8d %-7d %-9d %-8d %-8d %-8d %-8d\n", hadbProcs[4], readCount[4],
         diskReadBytes[4], writeCount[4], diskWriteBytes[4], pagesPagedIn[4],
         netReadBytes[4], netWriteBytes[4], syscallCount[4]);
  printf("%-16s %-6d %-8d %-7d %-9d %-8d %-8d %-8d %-8d\n", hadbProcs[5], readCount[5],
         diskReadBytes[5], writeCount[5], diskWriteBytes[5], pagesPagedIn[5], 
         netReadBytes[5], netWriteBytes[5], syscallCount[5]);
  printf("%-16s %-6d %-8d %-7d %-9d %-8d %-8d %-8d %-8d\n", hadbProcs[6], readCount[6],
         diskReadBytes[6], writeCount[6], diskWriteBytes[6], pagesPagedIn[6], 
         netReadBytes[6], netWriteBytes[6], syscallCount[6]);
  printf("%-16s %-6d %-8d %-7d %-9d %-8d %-8d %-8d %-8d\n", hadbProcs[7], readCount[7],
         diskReadBytes[7], writeCount[7], diskWriteBytes[7], pagesPagedIn[7], 
         netReadBytes[7], netWriteBytes[7], syscallCount[7]);
  printf("\n");

  /* clear data */
  diskReadBytes[1] = 0; readCount[1] = 0;
  diskReadBytes[2] = 0; readCount[2] = 0;
  diskReadBytes[3] = 0; readCount[3] = 0;
  diskReadBytes[4] = 0; readCount[4] = 0;
  diskReadBytes[5] = 0; readCount[5] = 0;
  diskReadBytes[6] = 0; readCount[6] = 0;
  diskReadBytes[7] = 0; readCount[7] = 0;

  diskWriteBytes[1] = 0; writeCount[1] = 0;
  diskWriteBytes[2] = 0; writeCount[2] = 0;
  diskWriteBytes[3] = 0; writeCount[3] = 0;
  diskWriteBytes[4] = 0; writeCount[4] = 0;
  diskWriteBytes[5] = 0; writeCount[5] = 0;
  diskWriteBytes[6] = 0; writeCount[6] = 0;
  diskWriteBytes[7] = 0; writeCount[7] = 0;

  pagesPagedIn[1] = 0;
  pagesPagedIn[2] = 0;
  pagesPagedIn[3] = 0;
  pagesPagedIn[4] = 0;
  pagesPagedIn[5] = 0;
  pagesPagedIn[6] = 0;
  pagesPagedIn[7] = 0;

  netWriteBytes[1] = 0;  netReadBytes[1] = 0;
  netWriteBytes[2] = 0;  netReadBytes[2] = 0;
  netWriteBytes[3] = 0;  netReadBytes[3] = 0;
  netWriteBytes[4] = 0;  netReadBytes[4] = 0;
  netWriteBytes[5] = 0;  netReadBytes[5] = 0;
  netWriteBytes[6] = 0;  netReadBytes[6] = 0;
  netWriteBytes[7] = 0;  netReadBytes[7] = 0;

  syscallCount[1] = 0;
  syscallCount[2] = 0;
  syscallCount[3] = 0;
  syscallCount[4] = 0;
  syscallCount[5] = 0;
  syscallCount[6] = 0;
  syscallCount[7] = 0;

  secs = INTERVAL;
  counts--;
}

/**********************************************************************/
profile:::tick-1sec
/counts == 0/
{
  exit(0);
}
'

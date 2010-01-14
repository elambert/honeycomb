#!/usr/bin/ksh
#
# $Id: profileProcess.sh 10845 2007-05-19 02:31:46Z bberndt $
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
# profileProcess.sh - display stats related to API-SERVERS
##

### default variables
interval=5; count=-1;
process=""
processArg=""
outputFile=""
optProcessArg=0

### process options
while getopts ha:p:o: name
do
  case $name in
    p)      process=$OPTARG ;;
    a)      processArg=$OPTARG ;;
    o)      outputFile=$OPTARG ;;
    h|?)    cat <<-END >&2
            USAGE: profileProcess.sh <-p proc> [-a process arg] [interval [count]]
            eg,
               profileProcess.sh -p java -a API-SERVERS  # 5 second samples
               profileProcess.sh -p java 1 # 1 second samples
		END
            exit 1
  esac
done

shift $(( $OPTIND - 1 ))

### option logic
if [ "$1" > 0 ]; then
  interval=$1; shift
fi
if [ "$1" > 0 ]; then
  count=$1; shift
fi

predicateProcessName=''
if [ ! -z "$process" ]; then
  predicateProcessName='/execname == "'$process'"/'
fi
if [ ! -z "$processArg" ]; then
  optProcessArg=1
fi

if [ ! -z "$outputFile" ]; then
  dtraceOpt='-o '$outputFile
fi 

################################################
exec /usr/sbin/dtrace $dtraceOpt -C -s <( print -r '

inline int INTERVAL = '$interval';
inline int COUNTER  = '$count';
inline int optProcessArg = '$optProcessArg';

int diskReadBytes;
int readCount;
int diskWriteBytes;
int writeCount;
int pagesPagedIn;
int netWriteBytes;
int netReadBytes;
int syscallCount;

#pragma D option quiet

#include <string.h>
#include <sys/uio.h>

/**********************************************************************/
dtrace:::BEGIN 
{
  /* starting values */
  counts = COUNTER;
  secs = INTERVAL;

  diskReadBytes = 0;
  readCount = 0;
  diskWriteBytes = 0;
  writeCount = 0;
  pagesPagedIn = 0;
  netWriteBytes = 0;
  netReadBytes = 0;
  syscallCount = 0;

  printf("Tracing... Please wait.\n");
}

/**********************************************************************/
vminfo:::pgpgin,
syscall:::entry,
fbt::fop_read:entry,
fbt::fop_write:entry,
io:genunix::start
{
  this->ok = 0;
}

/**********************************************************************/
vminfo:::pgpgin,
syscall:::entry,
fbt::fop_read:entry,
fbt::fop_write:entry,
io:genunix::start
'$predicateProcessName'
{
  this->ok = 1;
  optProcessArg ? (this->ok = (strstr(curpsinfo->pr_psargs,
                                "'$processArg'") != (char*) 0) ? 1 : 0) : 1;
}

/**********************************************************************/
vminfo:::pgpgin
/this->ok/
{
  pagesPagedIn += arg0;
}

/**********************************************************************/
fbt::fop_read:entry,
fbt::fop_write:entry
/this->ok/
{
  self->sockrw = args[0]->v_type == 9 ? 1 : 0;
  self->size = args[1]->uio_resid;
  self->uiop = args[1];
}

/**********************************************************************/
fbt::fop_read:return
/self->sockrw && self->uiop/
{
  this->resid = self->uiop->uio_resid;
  netReadBytes += (self->sockrw ? (self->size - this->resid) : 0);
  self->size = 0;
  self->uiop = 0;
}

/**********************************************************************/
fbt::fop_write:return
/self->sockrw && self->uiop/
{
  this->resid = self->uiop->uio_resid;
  netWriteBytes += (self->sockrw ? (self->size - this->resid) : 0);
  self->size = 0;
  self->uiop = 0;
}

/**********************************************************************/
io:genunix::start
/this->ok/
{
  diskReadBytes += args[0]->b_flags & B_READ ? args[0]->b_bcount : 0;
  readCount += args[0]->b_flags & B_READ ? 1 : 0;
}

/**********************************************************************/
io:genunix::start
/this->ok/
{
  diskWriteBytes += args[0]->b_flags & B_READ ? 0 : args[0]->b_bcount;
  writeCount += args[0]->b_flags & B_READ ? 0 : 1;
}

/**********************************************************************/
syscall:::entry
/this->ok/
{
  syscallCount += 1;
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
  diskReadBytes /= 1024; diskWriteBytes /= 1024; netReadBytes /= 1024; netWriteBytes /= 1024; 

  printf("%Y,  load: %d.%02d\n\n", walltimestamp, this->load1a,
         this->load1b);

  printf("%-21s %-6s %-6s %-6s %-6s %-6s %-6s %-6s %-6s\n", "PROC", "#READS", "READ(KB)",
         "#WRITES", "WRITE(KB)", "#PAGESIN", "NETREAD", "NETWRITE", "#SYSCALLS");
  printf("%-21s %-6d %-8d %-7d %-9d %-8d %-7d %-8d %-8d\n", "'$process'('$processArg')",
         readCount, diskReadBytes, writeCount, diskWriteBytes, pagesPagedIn,
         netReadBytes, netWriteBytes, syscallCount);
  printf("\n");

  /* clear data */
  diskReadBytes = 0;
  readCount = 0;
  diskWriteBytes = 0;
  writeCount = 0;
  pagesPagedIn = 0;
  netWriteBytes = 0;
  netReadBytes = 0;
  syscallCount = 0;

  secs = INTERVAL;
  counts--; 
}

/**********************************************************************/
profile:::tick-1sec
/counts == 0/
{
  exit(0);
}
')


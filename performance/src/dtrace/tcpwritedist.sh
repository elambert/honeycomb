#!/usr/bin/ksh
#
# $Id: tcpwritedist.sh 10845 2007-05-19 02:31:46Z bberndt $
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
# tcpwritedist.sh - simple TCP write distribution by process.
#
# This measures the size of writes from applications to the TCP level, which
# may well be much larger than the MTU size (this is application writes not
# packet writes). It can help identify which process is creating network
# traffic, and the size of the writes by that application. It uses a simple
# probe that produces meaningful output for most protocols.
#
# Tracking TCP activity by process is complex for a number of reasons,
# the greatest is that inbound TCP traffic is asynchronous to the process.
# The easiest TCP traffic to match is writes, which this script demonstrates.
# However there are still issues - for an inbound telnet connection the
# writes are associated with the command, for example "ls -l", not something
# meaningful such as "in.telnetd".
#
# USAGE:        tcpwritedist.sh [interval [count]]
# 
#               tcpwritedist.sh              # default output, 5 second samples
#
#       eg,
#               tcpwritedist.sh 1            # 1 second samples
#               tcpwritedist.sh 5 12         # print 12 x 5 second samples
# FIELDS:
#		PID	process ID
#		CMD	command and argument list
#		value	TCP write payload size in bytes
#		count	number of writes
##

##############################
# --- Process Arguments ---
#

### default variables
interval=5; count=-1;

### process options
while getopts h name
do
        case $name in
	h|?)    cat <<-END >&2
                USAGE: tcpwritedist.sh [interval [count]]
               eg,
                       tcpwritedist.sh              # default output, 5 second samples
                       tcpwritedist.sh 1            # 1 second samples
                       tcpwritedist.sh 5 12         # print 12 x 5 second samples
		END
                exit 1
        esac
done

shift $(( $OPTIND - 1 ))

### option logic
if [[ "$1" > 0 ]]; then
        interval=$1; shift
fi
if [[ "$1" > 0 ]]; then
        count=$1; shift
fi

#################################
# --- Main Program, DTrace ---
#
/usr/sbin/dtrace -C -s <( print -r '
#pragma D option quiet
 
/*
  * Command line arguments
  */
 inline int INTERVAL    = '$interval';
 inline int COUNTER     = '$count';
 
/* print header */
dtrace:::BEGIN
{
        counts = COUNTER;
        secs = INTERVAL;
        printf("Tracing... Please wait.\n");
}

/*
 * Process TCP Write
 */
fbt:ip:tcp_output:entry
{
	/* fetch details */
	this->size = msgdsize((mblk_t*)args[1]);

	/* store details */
	/*@Size[pid, curpsinfo->pr_psargs] = quantize(this->size);*/
        @Size[pid, execname] = sum(this->size);
}

/*
 * Timer
 */
profile:::tick-1sec
{
       secs--;
}

/*
 * Print Report
 */
profile:::tick-1sec
/secs == 0/
{
        /* fetch 1 min load average */
        this->load1a  = `hp_avenrun[0] / 65536;
        this->load1b  = ((`hp_avenrun[0] % 65536) * 100) / 65536;

        printf("%Y,  load: %d.%02d\n\n", walltimestamp, 
               this->load1a, this->load1b);

	/*printa(" PID: %-6d CMD: %S\n%@d\n", @Size);*/
        printf("%6s   %s %32s\n", "PID", "CMD", "BYTES");
        printa("%6d %-16s  %16@d\n", @Size);

        /* clear data */
        trunc(@Size);
        secs = INTERVAL;
        counts--;
}

/*
 * End of program
 */
profile:::tick-1sec
/counts == 0/
{
       exit(0);
}

/*
 * Cleanup for Ctrl-C
 */
dtrace:::END
{
       trunc(@Size);
}
')
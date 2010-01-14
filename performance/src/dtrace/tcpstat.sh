#!/usr/bin/ksh
#
# $Id: tcpstat.sh 10845 2007-05-19 02:31:46Z bberndt $
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
 * tcpstat.sh - print TCP statistics. Uses DTrace.
 *
 * This prints TCP statistics every second, retrieved from the MIB provider.
 *
 * USAGE:	tcpstat.sh
 *
 * FIELDS:
 *		TCP_out		TCP bytes sent
 *		TCP_outRe	TCP bytes retransmitted
 *		TCP_in		TCP bytes received
 *		TCP_inDup	TCP bytes received duplicated
 *		TCP_inUn	TCP bytes received out of order
 *
 * The above TCP statistics are documented in the mib2_tcp struct
 * in the /usr/include/inet/mib2.h file; and also in the mib provider
 * chapter of the DTrace Guide, http://docs.sun.com/db/doc/817-6223.
 */

#pragma D option quiet

/*
 * Declare Globals
 */
dtrace:::BEGIN
{
	TCP_out = 0; TCP_outRe = 0;
	TCP_in = 0; TCP_inDup = 0; TCP_inUn = 0;
	LINES = 20; line = 0;
}

/*
 * Print Header
 */
profile:::tick-1sec { line--; }

profile:::tick-1sec
/line <= 0 /
{
	printf("%11s %11s %11s %11s %11s\n",
	    "TCP_out", "TCP_outRe", "TCP_in", "TCP_inDup", "TCP_inUn");

	line = LINES;
}

/*
 * Save Data
 */
mib:::tcpOutDataBytes		{ TCP_out += arg0;   }
mib:::tcpRetransBytes		{ TCP_outRe += arg0; }
mib:::tcpInDataInorderBytes	{ TCP_in += arg0;    }
mib:::tcpInDataDupBytes		{ TCP_inDup += arg0; }
mib:::tcpInDataUnorderBytes	{ TCP_inUn += arg0;  }

/*
 * Print Output
 */
profile:::tick-1sec
{
	printf("%11d %11d %11d %11d %11d\n",
	    TCP_out, TCP_outRe, TCP_in, TCP_inDup, TCP_inUn);

	/* clear values */
	TCP_out   = 0;
	TCP_outRe = 0;
	TCP_in    = 0;
	TCP_inDup = 0;
	TCP_inUn  = 0;
}
')
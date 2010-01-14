#!/usr/bin/ksh
#
# $Id: udpstat.sh 10845 2007-05-19 02:31:46Z bberndt $
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
 * udpstat.sh - print UDP statistics. Uses DTrace.
 *
 * This prints UDP statistics every second, retrieved from the MIB provider.
 *
 * USAGE:	udpstat.sh
 *
 * FIELDS:
 *		UDP_out		UDP datagrams sent
 *		UDP_outErr	UDP datagrams errored on send
 *		UDP_in		UDP datagrams received
 *		UDP_inErr	UDP datagrams undeliverable
 *		UDP_noPort	UDP datagrams received to closed ports
 *
 * The above UDP statistics are documented in the mib2_udp struct
 * in the /usr/include/inet/mib2.h file; and also in the mib provider
 * chapter of the DTrace Guide, http://docs.sun.com/db/doc/817-6223.
 */

#pragma D option quiet

/*
 * Declare Globals
 */
dtrace:::BEGIN
{
	UDP_in = 0; UDP_out = 0;
	UDP_inErr = 0; UDP_outErr = 0; UDP_noPort = 0;
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
	    "UDP_out", "UDP_outErr", "UDP_in", "UDP_inErr", "UDP_noPort");

	line = LINES;
}

/*
 * Save Data
 */
mib:::udpInDatagrams	{ UDP_in += arg0;	}
mib:::udpOutDatagrams	{ UDP_out += arg0;	}
mib:::udpInErrors	{ UDP_inErr += arg0;	}
mib:::udpInCksumErrs	{ UDP_inErr += arg0;	}
mib:::udpOutErrors	{ UDP_outErr += arg0;	}
mib:::udpNoPorts	{ UDP_noPort += arg0;	}

/*
 * Print Output
 */
profile:::tick-1sec
{
	printf("%11d %11d %11d %11d %11d\n",
	    UDP_out, UDP_outErr, UDP_in, UDP_inErr, UDP_noPort);

	/* clear values */
	UDP_out		= 0;
	UDP_outErr	= 0;
	UDP_in		= 0;
	UDP_inErr	= 0;
	UDP_noPort	= 0;
}
'
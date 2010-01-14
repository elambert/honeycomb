#!/usr/local/bin/gawk -f
#
# $Id: webdav-get-rates.awk 10858 2007-05-19 03:03:41Z bberndt $
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

# Scan a cluster log file for WebDAV PUT instrumentation lines
# and calculate the rate trend
#
# Uses mktime() which is in gawk 3.1
#
# Options:
#     -t type of operation to look for (default: PUT)
#     -i interval to average over (default: 6 minutes, i.e. 0.1 hour)
#
# Sample gnuplot commands to plot the resulting file(s):
#     set xlabel 'Time (6 min., or 10/hr.)'
#     set ylabel 'Files/s'
#     set title 'PUT rates'
#     plot 'foo1' using 1:2 with lines title 'dev320', 'foo2' using 1:2 with lines title 'dev306'  

# Shamim Mohamed 7/2006

BEGIN {
    # Defaults
    bsize = 360;                # 0.1 hours
    opstr = "PUT";

    # Options
    if (ARGC > 0) {
        nhandled = 0;
        for (i = 0; i < ARGC; i++) {
            if (ARGV[i] == "-t") {
                i++; nhandled += 2;
                opstr = toupper(ARGV[i]);
            }
            else if (ARGV[i] == "-i") {
                i++; nhandled += 2;
                bsize = int(ARGV[i]);
            }
        }
        for (i = nhandled; i < ARGC; i++)
            ARGV[i-nhandled] = ARGV[i];
        ARGC -= nhandled;
    }

    printf("Starting: i = %d, op = %s\n", bsize, opstr);

    months["Jan"] = 1;
    months["Feb"] = 2;
    months["Mar"] = 3;
    months["Apr"] = 4;
    months["May"] = 5;
    months["Jun"] = 6;
    months["Jul"] = 7;
    months["Aug"] = 8;
    months["Sep"] = 9;
    months["Oct"] = 10;
    months["Nov"] = 11;
    months["Dec"] = 12;
}

/ INSTR SUM [0-9]*ms /{
    split($3, hms, ":")
    tstamp = mktime(sprintf("2006 %02d %s %s %s %s\n",
                            months[$1], $2, hms[1], hms[2], hms[3]));

    if (start_time == 0)
        start_time = tstamp;
    tstamp -= start_time;

    gsub("^.* INSTR SUM ", "");
    split($0, args);

#    printf(">>> <%s> <%s> <%s> <%s> <%s> <%s> <%s> <%s> <%s> <%s>\n",
#           args[0],args[1],args[2],args[3],args[4],args[5],args[6],args[7],args[8],args[9]);
    if (toupper(args[2]) == opstr) {

        gsub("ms.*$", "", args[1]);
        elapsed = args[1]/1000.0;
    
        bucket = int(tstamp/bsize);
    
        totals[bucket] += elapsed;
        nums[bucket]++;
    
        if (bucket > largest_bucket)
            largest_bucket = bucket;
    }
}

END {
    printf("# t/%-5d  rate   mean        n   cumul.\n", bsize);

    cumul = 0;
    for (bucket = 0; bucket <= largest_bucket; bucket++)
        if (bucket in nums) {
            printf("%6d    %5.2f %6.2f %8d %8d\n", bucket, nums[bucket]/bsize,
                   totals[bucket]/nums[bucket], nums[bucket],
                   cumul += nums[bucket]);
        }
        else
            printf("%6d     0.00    -          0 %8d\n", bucket, cumul);
}


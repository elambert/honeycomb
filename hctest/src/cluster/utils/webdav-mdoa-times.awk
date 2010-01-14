#!/usr/local/bin/gawk -f
# $Id: webdav-mdoa-times.awk 10858 2007-05-19 03:03:41Z bberndt $
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

# Scan a cluster log file for WebDAV OA and MD instrumentation lines
# and calculate the trends
#
# Uses mktime() which is in gawk 3.1
#
# Options:
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

    # Options
    if (ARGC > 0) {
        nhandled = 0;
        for (i = 0; i < ARGC; i++) {
            if (ARGV[i] == "-i") {
                i++; nhandled += 2;
                bsize = int(ARGV[i]);
            }
        }
        for (i = nhandled; i < ARGC; i++)
            ARGV[i-nhandled] = ARGV[i];
        ARGC -= nhandled;
    }

    printf("Starting: i = %d\n", bsize);

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

/ INSTR /{
    split($3, hms, ":")
    tstamp = mktime(sprintf("2006 %02d %s %s %s %s\n",
                            months[$1], $2, hms[1], hms[2], hms[3]));

    if (start_time == 0)
        start_time = tstamp;
    tstamp -= start_time;
    bucket = int(tstamp/bsize);
    if (bucket > largest_bucket)
        largest_bucket = bucket;

    gsub("^.* INSTR ", "");
    split($0, fields);

    if (substr($0, 0, 9) == "MD CREATE") {
        units = elapsed = fields[4]; 
        gsub("^[0-9.]*", "", units); 
        gsub(units, "", elapsed);

        if (units == "ms")
            elapsed /= 1000;

        md_totals[bucket] += elapsed;
        md_num[bucket]++;
    }
    else if (substr($0, 0, 10) == "FILE STORE") {
        units = elapsed = fields[5];
        gsub("^[0-9.]*", "", units);
        gsub(units, "", elapsed);

        if (units == "ms")
            elapsed /= 1000;

        units = bytes = fields[4];
        gsub("^[0-9.]*", "", units);
        gsub(units, "", bytes);

        if (units == "kiB")
            bytes *= 1024;
        else if (units == "MiB")
            bytes *= 1048576;
        else if (units == "GiB")
            bytes *= 1073741824;

        oa_totals[bucket] += elapsed;
        byte_totals[bucket] += bytes;
        oa_num[bucket]++;
    }
    else if (substr($0, 0, 4) == "SUM ") {
        units = fields[2];
        gsub("^[0-9.]*", "", units);

        elapsed = fields[2];
        gsub(units, "", elapsed);

        if (units == "ms")
            elapsed /= 1000;

        sum_totals[bucket] += elapsed;
        sum_num[bucket]++;
    }
}

END {
    printf("# t/%-5d  MD     OA    Sum    Bytes/s\n", bsize);

    cumul = 0;
    for (bucket = 0; bucket <= largest_bucket; bucket++) {
        tput = md = oa = sum = "  -  ";
        if (bucket in md_num)
            md = sprintf("%5.2f", md_totals[bucket]/md_num[bucket]);
        if (bucket in oa_num) {
            oa = sprintf("%5.2f", oa_totals[bucket]/oa_num[bucket]);
            tput = sprintf("%5.2f", byte_totals[bucket]/bsize);
        }
        if (bucket in sum_num)
            sum = sprintf("%5.2f", sum_totals[bucket]/sum_num[bucket]);

        printf("%6d  %s  %s  %s  %s\n", bucket, md, oa, sum, tput);
    }
}


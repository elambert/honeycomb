#!/bin/sh
#
# $Id: webdav-log-scraper.sh 10858 2007-05-19 03:03:41Z bberndt $
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
# A script to extract webdav PUT instrumentation lines from the
# cluster log and convert to a human-readable table


if [ "$#" != 1 ]; then
    echo "Usage: $0 <logfile>" >&2
    exit 1
fi

echo "# OID:                                             OA write:  MD create:      PUT:    RETR:     GET:   PROPF: Q amort:      Q:    Q.5:    Q.exist:   Other:"

# The first nawk chops off leading crap that Java and syslogd add

<"$1" nawk '
    / INSTR | <==> / {
        for (i = 13; i <= NF; i++) {
            printf("%s ", $i)
        }
        printf "\n"
    }

' | nawk '

    / <==> / {
        split($0, X, " <==> ")
        oids[X[1]] = X[2]
        fnames[X[2]] = X[1]
    }

    /INSTR MD CREATE / {
        printf("%-50s\tMD\t%s\n", $4, $5)
    }

    /INSTR FILE STORE / {
        printf("%-50s\tOA\t%s (%s, %s)\n", $4, $6, $5, $7)
    }

    /INSTR RETRIEVE/ {
        oid = $3
        dur = $4
        printf("%-50s\tRETR\t%s\t%s\n", oid, dur, fnames[oid])
    }

    /INSTR QUERY/ {
        elapsed = $NF
        nresults = 1
        req_attrs = ""
        q_extn = ""

        split($2, X, "/")
        if (X[2] != "") {
            # extended query
            q_extn = X[2]
        }
        else {
            # It is not an FS (extended attributes) query
            nresults = $(NF-1)
            gsub("[^0-9]", "", nresults)
        }

        printf("%-50s\tQU\t%s\t%5s\t%5s\n", "", elapsed, q_extn, nresults)
    }

    /INSTR SUM / {
        action = $4
        elapsed = $3
        fname = ""
        for (i = 5; i <= NF; i++) {
            if (substr($i, 1, 5) == "HTTP/")
                break
            fname = fname " " $i
        }
        fname = substr(fname, 9)
        printf("%-50s\tSUM %s\t%s\t%s\n", oids[fname], action, elapsed, fname)
    }

' | sort | nawk -F '\t' '
    BEGIN {
	hextab ["0"] = 0;	hextab ["8"] = 8;
	hextab ["1"] = 1;	hextab ["9"] = 9;
	hextab ["2"] = 2;	hextab ["A"] = hextab ["a"] = 10
	hextab ["3"] = 3;	hextab ["B"] = hextab ["b"] = 11;
	hextab ["4"] = 4;	hextab ["C"] = hextab ["c"] = 12;
	hextab ["5"] = 5;	hextab ["D"] = hextab ["d"] = 13;
	hextab ["6"] = 6;	hextab ["E"] = hextab ["e"] = 14;
	hextab ["7"] = 7;	hextab ["F"] = hextab ["f"] = 15;

        FMT = "%-50s %8s   %8s   %8s %8s %8s %8s %8s %8s %8s %8s  %s%s\n"
    }

    /\tMD\t/ {
        md = 0 + $3
    }
    /\tOA\t/ {
        oa = 1000 * (0 + $3)
    }
    /\tSUM / {
        oid = $1
        sub("[ \t]*", "", oid)
        t = 0 + $3
        split($2, X, " ")
        action = X[2]
        f = urldecode($4)
        if (action == "PUT") {
            if (oid != "")
                printf(FMT,
                        oid,  oa, md,   t, "-", "-", "-", "-", "-", "-",  "-", f, "")

        } else if (action == "GET") 
            printf(FMT,
                "-", "-",  "    -",   "-", "-", t, "-", "-", "-", "-", "-", f, "")
        else
            # PROPFIND
            printf(FMT,
                "-", "-",  "    -",   "-", "-", "-", t, "-", "-", "-", "-", f, "")
    }

    /\tRETR\t/ {
        oid = $1
        retr = 0 + $3
        f = $4
        printf(FMT, oid, "-", "-","-", retr,  "-", "-", "-", "-", "-",  "-", f, "")
    }

    /\tQU\t/ {
         amortized = snresults = ""
         t = 0 + $3
         q_extn = $4

         req_attrs = 0 + q_extn
         if (req_attrs == 0)
             req_attrs = ""
         else
             req_attrs = "<" req_attrs ">"

         nresults = 0 + $5
         if (nresults != 0) {
             amortized = int(0.5 + (t / nresults))
             if (nresults != 1)
                 snresults = "[" nresults "]"
         }

         if (req_attrs == "") {
             if (q_extn == "exist")
                 # Q.exist
                 printf(FMT,
                    "-", "-", "    -",  "-", "-", "-", "-", "-", "-", "-", t,
                    req_attrs, snresults)
             else
                 # Q and amortized
                 printf(FMT,
                    "-", "-", "    -", "-", "-", "-", "-", t, amortized, "-", "-",
                    req_attrs, snresults)
         }
         else
             # Q.5
             printf(FMT,
                    "-", "-", "    -",  "-", "-", "-", "-", "-", amortized, t, "-",
                    req_attrs, snresults)

    }

    function urldecode(s) {
        decoded = ""
	i   = 1
	len = length (s)
	while ( i <= len ) {
	    c = substr (s, i, 1)
	    if ( c == "%" ) {
	    	if ( i+2 <= len ) {
		    c1 = substr (s, i+1, 1)
		    c2 = substr (s, i+2, 1)
		    if ( hextab [c1] == "" || hextab [c2] == "" ) {
			print "WARNING: invalid hex encoding: %" c1 c2 | \
				"cat >&2"
		    } else {
		    	code = 0 + hextab [c1] * 16 + hextab [c2] + 0
		    	#print "\ncode=", code
		    	c = sprintf ("%c", code)
			i = i + 2
		    }
		} else {
		    print "WARNING: invalid % encoding: " substr ($0, i, len - i)
		}
	    } else if ( c == "+" ) {	# special handling: "+" means " "
	    	c = " "
	    }
	    decoded = decoded c
	    ++i
	}

	return decoded
    }

'


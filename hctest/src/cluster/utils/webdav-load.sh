#!/bin/bash

# $Id: webdav-load.sh 10858 2007-05-19 03:03:41Z bberndt $
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
# A script to load or retrieve the specified file to the Ofoto view in
# webdav.  Run it without -G to load; using the same command-line
# options and adding -G makes it GET and verify all the PUTs that were
# done.
#
# Shamim Mohamed April 2006

if [ -n "$http_proxy" ]; then
    echo "WARNING: \$http_proxy is set; unsetting." >&2
    unset http_proxy
fi

# Some defaults

THREADS=8
NUM=10
OP="PUT"

function usage()
{
    err=$1
    if [ -n "$err" ]; then
        # Add a newline
        err="
** Error! $err
"
    fi

    cat <<EOF 1>&2
$err
Usage: $0 [-n num] [-f filename] [-t num_threads] [-T prefix] [-o statfile] data-VIP[:port]
Options:
    -f : file to upload
    -n : no. of entries at each dir level
    -t : no. of threads
    -T : filename prefix (tag)
EOF
    exit 1
}

while getopts "t:n:f:T:o:G" opt; do
    case "$opt" in
    n) NUM="$OPTARG" ;;
    t) THREADS="$OPTARG" ;;
    f) INPUT="$OPTARG" ;;
    T) PREFIX="$OPTARG" ;;
    o) OUTFILE="$OPTARG" ;;
    G) OP="GET" ;;
    esac
done
shift $[$OPTIND - 1]

if [ $# -lt 1 ]; then
    usage "Not enough arguments."
fi
CLUSTER="$1"

if [ -z "$INPUT" ]; then
    usage "Input file (-f) is required"
fi
if [ -z "$PREFIX" ]; then
    usage "Prefix (-T) is required"
fi

SIZE=`ls -l "$INPUT" | awk '{print $5}'`
MIMETYPE=`file -b --mime "$INPUT"`

START=`perl -e 'print time;'`

# Util: integer generator
function forall {
    fr=$1
    t=$2
    st=${3:-1}
    i=$fr
    while [ "$i" -le "$t" ]; do
        printf "%02x\n" $i
        i=$((i+$st))
    done
}

function get {
    TMPFILE="/var/tmp/difftmp$$"
    curl -s -S -o "$TMPFILE" \
        "http://$CLUSTER/webdav/oFotoHashDirs/$1/$2/$3/$4/$5/$6/$PREFIX.t$1.$2.$3.$4.$5.$6"
    diff -q "$TMPFILE" "$INPUT"
    STATUS="$?"
    rm "$TMPFILE"
    return "$STATUS"
}

function upload {
    curl -s -S -H "Content-type: $MIMETYPE" -T "$INPUT" \
        "http://$CLUSTER/webdav/oFotoHashDirs/$1/$2/$3/$4/$5/$6/$PREFIX.t$1.$2.$3.$4.$5.$6"
}

function thread_run {
    ID="$1"
    for j in `forall 1 $NUM`; do
        for k in `forall 1 $NUM`; do
            for l in `forall 1 $NUM`; do
            for m in `forall 1 $NUM`; do
            for n in `forall 1 $NUM`; do
                if [ "$ID" = 01 ]; then
                    echo -ne "\r$j.$k.$l.$m.$n \E[K" >&2
                fi
                case "$OP" in
                "PUT")
                    upload $ID $j $k $l $m $n || \
                        echo "WARNING: PUT" \
                            "$ID/$j/$k/$l/$m/$n/$PREFIX.t$i.$j.$k.$l.$m.$n"\
                            "failed" >&2
                    ;;
                "GET")
                    get $ID $j $k $l $m $n || \
                        echo "WARNING: GET" \
                            "$ID/$j/$k/$l/$m/$n/$PREFIX.t$i.$j.$k.$l.$m.$n"\
                            "failed" >&2
                    ;;
                esac
            done
            done
            done
        done
    done
}

for t in `forall 1 $THREADS`; do
    thread_run "$t" "$1" &
done
wait
echo >&2

NOW=`perl -e 'print time;'`

NUM_FILES="$(($NUM*$NUM*$NUM*$THREADS*$NUM*$NUM))"
DURATION="$((NOW-START))"
RATE=`echo $NUM_FILES/$DURATION | bc -l`
RATE=`printf "%.2f" $RATE`
RESULTS="Uploaded $SIZE-byte file $NUM_FILES times in $DURATION sec ($RATE files/s)"

if [ -z "$OUTFILE" ]; then
    echo "$RESULTS"
else
   echo "$RESULTS" >"$OUTFILE"
fi


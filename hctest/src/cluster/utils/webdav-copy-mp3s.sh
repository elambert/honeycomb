#!/bin/bash
#
# $Id: webdav-copy-mp3s.sh 11769 2008-01-23 02:05:58Z sm152240 $
#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# Use is subject to license terms.
#
# A script to music files (mp3 or ogg) to the mp3 view. For directory
# arguments it behaves recursively à la "ls -R". It uses id3v2(1) and
# vorbiscomment(1) to extract metadata from the files.  
#
# Shamim Mohamed April 2006

if [ -n "$http_proxy" ]; then
    echo "WARNING: \$http_proxy is set; unsetting." >&2
    unset http_proxy
fi

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
Usage: $0 [-o statfile] -s data-VIP[:port] { file | dir } ...
Options:
    -o : file to write statistics to
    -s : the server to write to
Directory arguments are recursed into.
EOF
    exit 1
}

while getopts "s:o:" opt; do
    case "$opt" in
    s) SERVER="$OPTARG" ;;
    o) OUTFILE="$OPTARG" ;;
    esac
done
shift $[$OPTIND - 1]

if [ $# -lt 1 ]; then
    usage "Not enough arguments."
fi
CLUSTER="$1"

if [ -z "$SERVER" ]; then
    usage "Server (-s) is required"
fi

function upload {
    FILE="$1"
    MIMETYPE=`file -b --mime "$FILE"`

    ARTIST=
    ALBUM=
    TITLE=
    TRACK=

    TMPFILE="/tmp/foo$$"

    case "$MIMETYPE" in
        "audio/mpeg")
            TYPE="mp3"
            id3v2 -l "$FILE" | awk '/^TIT2 /{printf "TITLE=\"%s\"\n", substr($0, 2+index($0, ":"))}/^TPE1 /{printf "ARTIST=\"%s\"\n", substr($0, 2+index($0, ":"))}/^TALB /{printf "ALBUM=\"%s\"\n", substr($0, 2+index($0, ":"))}/^TRCK /{printf "TRACK=\"%s\"\n", substr($0, 2+index($0, ":"))}' >"$TMPFILE"
            ;;

        "application/ogg")
            TYPE="ogg"
            vorbiscomment -l "$FILE" | sed 's/^artist=/ARTIST=/;s/^album=/ALBUM=/;s/^title=/TITLE=/;s/^tracknumber=/TRACK=/;s/= */=\"/;s/$/\"/' >"$TMPFILE"
            ;;
        *)
            return 1
            ;;
    esac

    . "$TMPFILE"
    rm "$TMPFILE"

    echo -ne "\r$ARTIST/$ALBUM/${TRACK:-0} - $TITLE.$TYPE \E[K" >&2
    curl -s -S -H "Content-type: $MIMETYPE" -T "$FILE" \
       "http://$SERVER/webdav/byArtist/$ARTIST/$ALBUM/${TRACK:-0} - $TITLE.$TYPE"
    NUM_FILES=$((NUM_FILES+1))
}

function doit () {
    local g
    if [ -d "$1" ]; then
        for g in "$1"/*; do
            doit "$g"
        done
    elif [ -f "$1" ]; then
        upload "$1"
    fi
}

NUM_FILES=0
START=`perl -e 'print time;'`

for f ; do
    doit "$f"
done

NOW=`perl -e 'print time;'`
DURATION="$((NOW-START))"
RATE=`echo $NUM_FILES/$DURATION | bc -l`
RATE=`printf "%.2f" $RATE`
RESULTS="Uploaded $NUM_FILES files in $DURATION sec ($RATE files/s)"

if [ -z "$OUTFILE" ]; then
    echo "$RESULTS"
else
   echo "$RESULTS" >"$OUTFILE"
fi


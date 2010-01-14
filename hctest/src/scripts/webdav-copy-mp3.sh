#!/bin/bash

# $Id: webdav-copy-mp3.sh 10858 2007-05-19 03:03:41Z bberndt $
#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# Use is subject to license terms.

# A script to music files to the mp3 view.
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

    TMPFILE="/tmp/foo$$"

    case "$MIMETYPE" in
        "audio/mpeg")
            TYPE="mp3"
            id3v2 -l "$FILE" | sed 's/: */:/' | awk -F: '/^TIT2 /{printf "TITLE=\"%s\"\n", $2}/^TPE1 /{printf "ARTIST=\"%s\"\n", $2}/^TALB /{printf "ALBUM=\"%s\"\n", $2}' >"$TMPFILE"
            ;;

        "application/ogg")
            TYPE="ogg"
            vorbiscomment -l "$FILE" | sed 's/^artist=/ARTIST=/;s/^album=/ALBUM=/;s/^title=/TITLE=/;s/= */=\"/;s/$/\"/' >"$TMPFILE"
            ;;
    esac

    . "$TMPFILE"
    rm "$TMPFILE"

    echo -ne "\r$ARTIST/$ALBUM/$TITLE.$TYPE \E[K" >&2
    curl -s -S -H "Content-type: $MIMETYPE" -T "$FILE" \
       "http://$SERVER/webdav/byArtist/$ARTIST/$ALBUM/$TITLE.$TYPE"
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


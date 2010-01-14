#!/usr/bin/bash

# This script tests GenFS by first storing many files with PUT (using
# multiload) and retrieving the metadata using the API and comparing
# the values.
#
# For details, see
#     http://hc-web.sfbay/svn/docs/Test/Divisadero/WebDAV-correctness-testplan.html

# Shamim Mohamed Feb 2007
#
# $Id: Test-GenFS-put_retr.sh 10321 2007-02-21 21:31:32Z sm152240 $
#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# Use is subject to license terms.

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <cluster> <outfile>" >&2
    exit 1
fi

CLUSTER="$1"
OUTFILE="$2"

# For each instance; total PUT time is 7 times this
DURATION=3600

SEED=`date`
PREFIX="http://${CLUSTER}:8080/webdav/epochAlpha"

# Call the Java SDK program
SDKCLASSPATH="/usr/local/java/lib/honeycomb-sdk.jar"
SDKCLASSPATH="${SDKCLASSPATH}:/usr/local/java/lib/honeycomb-client.jar"
function retrieve_metadata {
    java -classpath "$SDKCLASSPATH" RetrieveMetadata "$@"
}

# The epochAlpha view is:
#
#    <fsView name="epochAlpha"
#          filename="${word}.txt">       <!-- string:512 -->
#      <attribute name="timestart"/>     <!-- long -->
#      <attribute name="filename"/>      <!-- string:64 -->
#      <attribute name="first"/>         <!-- string:64 -->
#      <attribute name="second"/>        <!-- string:64 -->
#      <attribute name="third"/>         <!-- string:64 -->
#      <attribute name="fourth"/>        <!-- string:64 -->
#      <attribute name="fifth"/>         <!-- string:64 -->
#    </fsView>

# Construct a GenFS URL in the epochAlpha view. (Unset values are elided.)
function make_url {
    URL="/webdav/epochAlpha/${timestart}/${filename}/${first}/${second}"
    URL="${URL}/${third}/${fourth}/${fifth}/${word}.txt"
    echo "$URL" | gsed -r 's/\/+/\//g'
}

# Argument is a filename that is assumed to have "set" statements
function parse_metadata {
    timestart=
    filename=
    first=
    second=
    third=
    fourth=
    fifth=
    word=

    . "$1"
}

# PUT files at various levels of the WebDAV hierarchy using multiload
function genfs_put {
    OUTFILE="$1"
    URI="/file{0-99}/A{0-99}/B{0-99}/C{0-99}/D{0-99}/E{0-99}"
    FNAME="word{0-99}.txt"

    MULTILOAD="./multiload -t $DURATION"

    for i in 6 5 4 3 2 1 0; do
        >"${OUTFILE}.$i"
        URL="${PREFIX}/$i$URI/$FNAME"

        echo "$MULTILOAD -o ${OUTFILE}.$i -S '${SEED}.$i' ${URL}"
        $MULTILOAD -o "${OUTFILE}.$i" -S "${SEED}.$i" "${URL}" >/dev/null

        URI="${URI%/*}"
    done
} 

# This sed pattern converts the output of RetrieveMetadata into
# something that can be sourced by the shell: get just the attributes
# we're interested in and quote values. (Meant to be used with -n.)
ATTRPAT="(timestart|filename|first|second|third|fourth|fifth|word)"
SEDPAT="s/\$/'/;/^${ATTRPAT}=/s/=/='/p"

# Read stdin; for every OID/URL pair (one per line) get metatadata and
# verify all values are as expected.
function genfs_verify {
    TMPFILE="/var/tmp/MDvalues.$$.$1"

    while read OID URL; do

        # Retrieve metadata into temp file
        retrieve_metadata "$CLUSTER" "$OID" | gsed -rn "$SEDPAT" >$TMPFILE

        # Parse temp file into shell variables
        parse_metadata "$TMPFILE"

        # Construct expected URLs using variables set by parse_metadata
        newURL=`make_url`

        if [ "$URL" != "$newURL" ]; then
            echo -e "\n$OID\n    $URL\n    $newURL"  >&2
            cat "$TMPFILE" >&2
        else
            echo -n "."
        fi

        rm "$TMPFILE"
    done
}

########################################################################

#
# Here's main()
#

# Save all the files
genfs_put "$OUTFILE"

echo "Files stored; now verifying"

# Verify them
for i in 6 5 4 3 2 1 0; do
    genfs_verify $i <"${OUTFILE}.$i" &
done

wait

echo "Done."

exit 0

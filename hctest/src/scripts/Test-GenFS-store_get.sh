#!/usr/bin/bash

# This script tests GenFS by first storing many files with varying
# amounts of metadata (using the API); then for each file verifies
# that it can be retrieved using WebDAV.
#
# For details, see
#     http://hc-web.sfbay/svn/docs/Test/Divisadero/WebDAV-correctness-testplan.html

# Shamim Mohamed Feb 2007
#
# $Id: Test-GenFS-store_get.sh 10321 2007-02-21 21:31:32Z sm152240 $
#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# Use is subject to license terms.

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <cluster>" >&2
    exit 1
fi

CLUSTER="$1"

# Total files uploaded will be 7 times this
NUM_FILES=10000

PREFIX="http://${CLUSTER}:8080/webdav/epochAlpha"

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
    echo -n "$PREFIX"
    URL="/${timestart}/${filename}/${first}/${second}/${third}"
    URL="${URL}/${fourth}/${fifth}/${sixth}/${word}.txt"
    echo "$URL" | gsed -r 's/\/+/\//g'
}

# Array of attribute names of interest
ATTRNAME[1]=filename
ATTRNAME[2]=first
ATTRNAME[3]=second
ATTRNAME[4]=third
ATTRNAME[5]=fourth
ATTRNAME[6]=fifth

# These are the prefixes for each value to be generated
VALUES[1]=file
VALUES[2]=A
VALUES[3]=B
VALUES[4]=C
VALUES[5]=D
VALUES[6]=E

# Create a temp file to upload
TMPFILE="/var/tmp/GenFSStore.$$"
echo "$RANDOM" > $TMPFILE

# The Java SDK program to store a file with metadata
SDKCLASSPATH="/usr/local/java/lib/honeycomb-sdk.jar"
SDKCLASSPATH="${SDKCLASSPATH}:/usr/local/java/lib/honeycomb-client.jar"
STORECMD="java -classpath $SDKCLASSPATH StoreFile $CLUSTER $TMPFILE"

# Store a file with metadata. MD values are generated randomly, and shell
# variables are set for each MD attribute created. The OID created is
# saved in the "$OID" variable.
function store_with_md {
    N="$1"
    word="word$RANDOM"
    timestart="$N"

    CMD="$STORECMD -m timestart=$timestart -m word=$word"

    for ((i = 1; i <= $N; i++)); do
        NAME="${ATTRNAME[$i]}"
        VALUE="${VALUES[$i]}$RANDOM"
        CMD="$CMD -m ${NAME}=${VALUE}"

        # Set the shell variable
        eval "${NAME}=${VALUE}"
    done

    # Unset remaining variables
    for ((; i <= 6; i++)); do
        eval "unset ${ATTRNAME[$i]}"
    done

    # Run the command and save the OID in a variable
    OID=`eval $CMD`
}

# Store $NUM_FILES files with metadata. The argument is the number of
# attributes to set.
function do_stores {
    NATTR="$1"

    for ((f = 0; f < "$NUM_FILES"; f++)); do

        # Store a file with pseudo-random metadata (which is left
        # in shell variables)
        store_with_md $NATTR

        # Construct the expected URL
        URL=`make_url`

        # If the URL doesn't exist, something went wrong.
        if ! curl -f -s -o /dev/null "$URL"; then
            echo -e "\n$OID\n    $URL" >&2
        else
            echo -n "."
        fi

        # Optional: print progress report every 100 files.
        if [ "${f%00}" -lt "$f" ]; then
            echo -e "\n$NATTR: $f" >&2
        fi

    done
}

########################################################################

#
# Here's main()
#

for ((i = 0; i <= 6; i++)); do
    do_stores "$i" &
done

wait

echo "Done."

exit 0

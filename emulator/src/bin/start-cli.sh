#!/bin/bash

#
# If you want ro run the cli against the non master cell
# in a multicell environment
#
SERVER="null"
if [ $# -eq 1 ]; then 
SERVER=$1;
fi

# Directory on top of this script
DIR=`cd \`dirname $0\`/..; pwd`

CLASSPATH=\
$DIR/lib/honeycomb-adm.jar:\
$DIR/lib/honeycomb-mgmt.jar:\
$DIR/lib/honeycomb-server.jar:\
$DIR/lib/honeycomb-common.jar:\
$DIR/lib/activation.jar:\
$DIR/lib/servicetags-api.jar:\
$DIR/lib

    java -server -Xms16m -Xmx16m \
	-Dcli.emulator="true" \
	-Dcli.emulator.server="$SERVER" \
        -DCLI_PROCESS \
        -Dsun.io.useCanonCaches=false \
        -Djava.library.path="$DIR/lib" $DEBUG_FLAGS\
        -classpath "$CLASSPATH" \
        com.sun.honeycomb.adm.cli.Shell

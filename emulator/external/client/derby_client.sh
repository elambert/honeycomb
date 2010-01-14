#! /bin/sh

#
# $Id: derby_client.sh 10857 2007-05-19 03:01:32Z bberndt $
#
# Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
# Use is subject to license terms.
#

CUR_DIR=`cd \`dirname $0\`/..; pwd`
EMU_ROOT=`cd $CUR_DIR/../../build/emulator/dist; pwd`

CLASSPATH=$CUR_DIR/client/derbytools.jar:$CUR_DIR/derby-10.1.1.0.jar

echo "Emulator root: $EMU_ROOT"
echo "Database:      $1"
echo "CLASSPATH:     $CLASSPATH"
echo
echo "Starting the client"
echo

java -Dij.protocol=jdbc:derby: -Dij.database=$1 -Dderby.system.home=$EMU_ROOT/var/metadata -cp $CLASSPATH org.apache.derby.tools.ij
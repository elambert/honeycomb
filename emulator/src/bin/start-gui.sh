#!/bin/sh
DIR=`cd \`dirname $0\`/..; pwd`
#
# If you want ro run the cli against the non master cell
# in a multicell environment
#
SERVER="null"
if [ $# -eq 1 ]; then 
SERVER=$1;
fi


CLASSPATH=$DIR/lib/st5800-admingui.jar
CLASSPATH=$CLASSPATH:$DIR/lib/xmlrpc-2.0.1.jar
CLASSPATH=$CLASSPATH:$DIR/lib/commons-codec-1.3.jar
CLASSPATH=$CLASSPATH:$DIR/lib/honeycomb-common.jar
CLASSPATH=$CLASSPATH:$DIR/lib/honeycomb-server.jar
CLASSPATH=$CLASSPATH:$DIR/lib/honeycomb-adm.jar
CLASSPATH=$CLASSPATH:$DIR/lib/honeycomb-mgmt.jar
CLASSPATH=$CLASSPATH:$DIR/lib/jetty-4.2.20.jar
CLASSPATH=$CLASSPATH:$DIR/lib/servlet-4.2.19.jar
CLASSPATH=$CLASSPATH:$DIR/lib/concurrent.jar
CLASSPATH=$CLASSPATH:$DIR/lib/swing-layout-1.0.jar
CLASSPATH=$CLASSPATH:$DIR/lib/honeycomb-adm.jar
CLASSPATH=$CLASSPATH:$DIR/lib/honeycomb-mgmt.jar
CLASSPATH=$CLASSPATH:$DIR/lib/honeycomb-activation.jar
CLASSPATH=$CLASSPATH:$DIR/lib/honeycomb-emulator.jar
CLASSPATH=$CLASSPATH:$DIR/lib/honeycomb-common.jar
CLASSPATH=$CLASSPATH:$DIR/lib/activation.jar
CLASSPATH=$CLASSPATH:$DIR/lib






rm "/var/tmp/admingui.emulator.xml"
touch /var/tmp/admingui.emulator.xml

java -server -Xms16m -Xmx16m \
    -Dcli.emulator="true" \
    -Djava.library.path="$DIR/lib" \
    -Dcli.emulator.server="$SERVER" \
    -DCLI_PROCESS \
    -Dadmingui.web.port=8090 \
    -Dsun.io.useCanonCaches=false \
    -Dadmingui.web.path=$DIR/lib \
    -cp $CLASSPATH \
    -Xdebug -Xrunjdwp:transport=dt_socket,address=8008,server=y,suspend=n \
com.sun.honeycomb.admingui.server.AdminGUIMain 

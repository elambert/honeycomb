#! /bin/sh

#
# $Id: startHttpServer.sh 10842 2007-05-19 02:24:31Z bberndt $
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

echo "\n\nIMPORTANT: *** This script is intended for testing only ***" 
echo "The AdminGUI service emulator is expecting the metadata configuration to be specified in /var/tmp/admingui.emulator.xml"


################################################################################
# Be sure you run the copy of this file that resides in the build part of the 
# tree, i.e. build/admingui/dist/bin/start.sh
# The script will fail if you run the "source" copy, 
# i.e. admingui/bin/start.sh
################################################################################

/usr/bin/touch  /var/tmp/admingui.emulator.xml

DIR=`cd \`dirname $0\`/../../..; pwd`

CLASSPATH=$DIR/admingui/dist/lib/honeycomb-admingui.jar:$DIR/external/dist/lib/xmlrpc-2.0.1.jar:$DIR/external/dist/lib/commons-codec-1.3.jar:$DIR/common/dist/lib/honeycomb-common.jar:$DIR/server/dist/lib/honeycomb-server.jar:$DIR/external/dist/lib/jetty-4.2.20.jar:$DIR/external/dist/lib/servlet-4.2.19.jar:$DIR/server/dist/share

echo "Classpath: [$CLASSPATH]"

java -cp $CLASSPATH -Dadmingui.web.path=$DIR/admingui/dist/web -Dadmingui.web.port=8090 -Xdebug -Xrunjdwp:transport=dt_socket,address=8001,server=y,suspend=n com.sun.honeycomb.admingui.server.AdminGUIMain

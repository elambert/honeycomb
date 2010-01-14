#! /bin/sh

#
# $Id: start.sh 7522 2006-03-17 18:41:34Z dp127224 $
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

################################################################################
# Be sure you run the copy of this file that resides in the build part of the 
# tree, i.e. build/admingui/dist/bin/startGui.sh
# The script will fail if you run the "source" copy, 
# i.e. admingui/bin/startGui.sh
################################################################################

##################################################
# How to run HC admin GUI during development/test
##################################################
# 1.    Start the HTTP server.  To do this run
#           build/admingui/dist/bin/startHttpServer.sh
#       By default, the server will run on port 8090, but this is configurable.
# 
# 2.    Start the GUI. There are two ways to do this.
#   a.  Standalone.  To do this run this script.  You can specify the host
#       that is running the HTTP server by specifying it as a commandline
#       parameter to this script.  If you don't specify a host, the default
#       "localhost" will be used.  The HTTP server port is not currently configurable
#       and must be 8090.
#   b.  Applet.  The customer will be running the app as an applet off the
#       appliance.  To run as an applet in development, go to this URL;
#           <host>:<port>
#       where <host> is the host running the HTTP server and port is the 
#       port the HTTP server is running on.  The default is 8090.


DIR=`cd \`dirname $0\`/../../..; pwd`

CLASSPATH=$DIR/admingui/dist/lib/honeycomb-admingui.jar:$DIR/external/dist/lib/swing-layout-1.0.jar:$DIR/external/dist/lib/mozart-ui.jar:$DIR/external/dist/lib/xmlrpc-2.0.1.jar:$DIR/external/dist/lib/commons-codec-1.3.jar:$DIR/server/dist/lib/honeycomb-server.jar

echo "Classpath: [$CLASSPATH]"

java -cp $CLASSPATH -Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=n \
     -Dadmingui.host=$1 \
     -Dadmingui.objectFactory=com.sun.honeycomb.admingui.present.ObjectFactory \
     com.sun.nws.mozart.ui.MainFrame

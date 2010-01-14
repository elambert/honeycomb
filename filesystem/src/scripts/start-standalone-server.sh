#!/bin/sh
#
# $Id: start-standalone-server.sh 10857 2007-05-19 03:01:32Z bberndt $
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

#
# Driver script to start the WebDAV server with the Honeycomb simulator
#

BASEDIR="$PWD"

CLASSNAME="com.sun.honeycomb.webdav.DAVServer"

CLASSPATH="${BASEDIR}/build/filesystem/dist/lib/honeycomb-fs.jar"
CLASSPATH="$CLASSPATH:${BASEDIR}/build/external/dist/lib/jetty-4.2.20.jar"
CLASSPATH="$CLASSPATH:${BASEDIR}/build/external/dist/lib/servlet-4.2.19.jar"
CLASSPATH="$CLASSPATH:${BASEDIR}/build/server/dist/lib/honeycomb-server.jar"
CLASSPATH="$CLASSPATH:${BASEDIR}/build/common/dist/lib/honeycomb-common.jar"
CLASSPATH="$CLASSPATH:${BASEDIR}/build/external/dist/lib/jug.jar"

CLASSPATH="$CLASSPATH:${BASEDIR}/emulator/external/client/derbytools.jar"
CLASSPATH="$CLASSPATH:${BASEDIR}/build/emulator/dist/lib/derby-10.1.1.0.jar"

DEFINES="-Djava.library.path=${BASEDIR}/build/filesystem/dist/lib"
DEFINES="$DEFINES:${BASEDIR}/build/server/dist/lib"

set -x
java -cp "$CLASSPATH" $DEFINES "$CLASSNAME" "$@"

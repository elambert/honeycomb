#!/bin/bash
# $Id: run.sh 3322 2005-01-16 19:47:18Z ar146282 $
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
# Helper script to run the Java apps in this directory.

if [ $# -lt 1 ]
then
    echo "usage: $0 app [args]"
    echo "app.java should contain the Main method"
    exit 1
fi


BUILD_DIR="../../../../../../../build"
SERVERJAR="$BUILD_DIR/server/dist/lib/honeycomb-server.jar"
UTJAR="$BUILD_DIR/unit_tests/dist/honeycomb-utests.jar"
HCLIB="$BUILD_DIR/server/dist/lib"
PKG=com.sun.honeycomb.datadoctor

# if app name contians .java suffix, remove
APP=$1
APP=${APP%.java}

# get arguments to app
ARGS=$*
ARGS=${ARGS#$1}

java -classpath $SERVERJAR:$UTJAR -Djava.library.path=$HCLIB $PKG.$APP $ARGS


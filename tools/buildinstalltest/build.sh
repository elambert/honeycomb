#!/bin/sh
#
# $Id: build.sh 10853 2007-05-19 02:50:20Z bberndt $
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

# Only edit this script in the repository
# under honeycomb/tools
#
# Use the named directory to do a fresh checkout and build of the
# honeycomb trunk.
# usage: $0 <dir>
#

print_usage () {
    echo "usage: $0 <dir>"
    echo "    Checks out a fresh copy of the honeycomb/trunk under <dir> and"
    echo "    performs `make all`"
}

WHEREAMI=`cd \`dirname $0\`; pwd`
MYSVNROOT=$WHEREAMI/../..

. $MYSVNROOT/tools/buildinstalltest/common.sh

TARGETDIR=$1
if [ -z "$TARGETDIR" ]; then
    print_usage
    echo "error: please specify a target directory."
    exit 1
fi

SVNREPO="http://subversion.sfbay.sun.com/build/honeycomb/trunk"
BUILDID=`date +"%Y%m%dT%H%M"`
SVNROOT=trunk-$BUILDID

echo
echo "*** Honeycomb Build $BUILD_NAME"
echo
echo "*** Build dir:   $TARGETDIR/$SVNROOT"
echo "*** SVN Repo:    $SVNREPO"
echo

run mkdir -p $TARGETDIR
run cd $TARGETDIR
date
run svn co $SVNREPO $SVNROOT
date
run cd $SVNROOT/build
date
run make all
date
run cd $TARGETDIR

# atomically update the latest link
run ln -sf $SVNROOT latest.new
run mv -f latest.new latest
date

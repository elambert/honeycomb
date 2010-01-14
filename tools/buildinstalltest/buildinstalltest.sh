#!/bin/sh
#
# $Id: buildinstalltest.sh 10853 2007-05-19 02:50:20Z bberndt $
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
# usage: buildinstalltest cluster [svnroot [version]]
#
# example: buildinstalltest.sh dev331
# example: buildinstalltest.sh dev331 /export/home/sarahg/svn/honeycomb
# example: buildinstalltest.sh dev331 /export/home/sarahg/svn/honeycomb trunk-with-oa-hack
#

ROOT=`dirname $0`
if [ "$ROOT" = "." ]; then
    ROOT=`pwd`
fi

. $ROOT/common.sh

CLUSTER=$1
if [ -z "$CLUSTER" ]; then
    echo "usage: buildinstalltest cluster [svnroot [version]]"
    exit 1
fi
CHEAT=$CLUSTER-cheat

# if build isn't specified, use a default appropriate
# for flamebox
SVNROOT=$2
BUILD_NAME=$3
if [ -z "$SVNROOT" ]; then
    DATE=`date +%F-%H%M`

    # XXX need to accomodate both anza and balboa eventually
    # trunk is fine for now
    BRANCH=trunk
    SVNROOT=$DEFAULTPARENTDIR/$BRANCH-$DATE
    if [ ! -d $DEFAULTPARENTDIR ]; then 
        echo "Can't use default build dir of $SVNROOT because default parent dir $DEFAULTPARENTDIR doesn't exist"
        echo
        echo "usage: buildinstalltest cluster [svnroot [version]]"
        exit 1
    fi
    if [ -z "$BUILD_NAME" ]; then
        BUILD_NAME=`basename $SVNROOT`
    fi
fi

if [ -z "$BUILD_NAME" ]; then
    BUILD_NAME=`basename $SVNROOT`-`date +%F-%H%M`
fi

PKGDIR=$SVNROOT/build/pkgdir

echo
echo "Honeycomb Automated Build/Install/Test"
echo
echo "*** Build dir:   $SVNROOT"
echo "*** Build name:  $BUILD_NAME"
echo "*** Pkg dir:     $PKGDIR"
echo "*** Cluster:     $CLUSTER"
echo "*** Cheat:       $CHEAT"
echo

print_date "Starting build, install, and test at"
starttime=`date`

echo
echo "=================== BUILD TASK =================================="
echo
run $ROOT/build.sh $SVNROOT
echo
echo "=================== INSTALL TASK =================================="
echo
run $ROOT/install.sh $CHEAT $PKGDIR $DEFAULTINSTALLTYPE
echo
echo "=================== REGRESSION TEST TASK =================================="
echo
run $ROOT/regressiontest.sh $CLUSTER $SVNROOT $BUILD_NAME

print_date "Finished build, install, and test (which was started at $starttime) at"

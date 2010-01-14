#!/bin/sh
#
# $Id: nightly.sh 10853 2007-05-19 02:50:20Z bberndt $
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

# XXX Don't edit this directly on hc-lab!
# Edit this in your copy of the repository
# in honeycomb/tools/subversion and then scp 
# it over to hc-lab after commiting the change.
#
# scp nightly.sh build@hc-lab:/build/bin/nightly.sh
#

#
# Nightly build script. Run from cron or manually.
# To give an alternate build dir, use:
#
# BUILD=/build/nightly/2004-07-07-respin nightly.sh
#

# This is done by the build user, who only has access
# to the build/* repos, so we must use the build/honeycomb
# path and not the repos/honeycomb path
## SVNREPOSPATH="http://subversion.sfbay.sun.com/repos/honeycomb"
SVNREPOSPATH="http://subversion.sfbay.sun.com/build/honeycomb"
BRANCH=trunk
RELEASE=$BRANCH

PARENT_DIR=/build/nightly
PATH=/usr/local/bin:/usr/local/java/bin:$PATH
export JAVA_HOME=/usr/local/java

# Set the build dirs
DATE=`date +%F`
if [ -z $BUILD ]; then BUILD=$PARENT_DIR/$RELEASE-$DATE; fi
BUILD_NAME=`basename $BUILD`
SRC=$BUILD/src
IMAGES=$BUILD/images

# Function to run commands and exit 1 on error
run() {
  echo "### $*"
  echo
  $*
  if [ $? -ne 0 ]; then exit 1; fi
}

# Welcome message
echo "*** Honeycomb Nightly Build $BUILD_NAME"
echo
echo "*** Build dir:  $BUILD"
echo "*** Source dir: $SRC"
echo "*** Images dir: $IMAGES"
echo "*** SVN branch: $BRANCH"
echo

# Print the build start date
DATE=`date`
echo "*** Build started at $DATE"
echo

# Create the build dirs
run mkdir $BUILD $SRC $IMAGES

# Checkout honeycomb
cd $SRC
DATE=`date +"%Y%m%dT%H%M"`

echo "### svn checkout --revision {$DATE} $SVNREPOSPATH/$BRANCH honeycomb"

echo

svn checkout --revision {$DATE} $SVNREPOSPATH/$BRANCH honeycomb

if [ $? -ne 0 ]; then exit 1; fi

# Build honeycomb
cd $SRC/honeycomb
echo

echo "### Building with emma instrumentation for code coverage"
run ant -Demma.build=true tar

# normal build; must run LAST so the honeycomb-bin.tar.gz symlink is set to it
echo "### Building official build"
run ant tar

# build the tests for automated regression running
echo "### Building tests" 
run ant test.jar

# build the sdk to get the latest JavaDoc for the API accessible at
# http://hc-lab.sfbay/sdk/doc/
echo "### Building sdk" 
run ant sdk

echo
run ln -s $SRC/honeycomb/honeycomb-bin.tar.gz $IMAGES/honeycomb-bin.tar.gz

# Build the gentoo platform
                                                                                                                                   
# Build the kernel
                                                                                                                                   
# Build the initrd

# Make links to the latest bzImage and initrd.gz
PLATFORM=`readlink -f /build/platform/latest`
run ln -s $PLATFORM/images/bzImage $IMAGES/bzImage
run ln -s $PLATFORM/images/initrd.gz $IMAGES/initrd.gz

# Make a link to the latest if we're under the hc-dev build area
if [ `dirname $BUILD` = "/build/nightly" ]; then
    run rm -f /build/nightly/latest
    run ln -s $BUILD /build/nightly/latest
fi

# Print the resulting images
run ls -la $IMAGES
echo

# Print the build finish date
DATE=`date`
echo "*** Build finished at $DATE"

#!/usr/bin/bash
#
# $Id: check_release.sh 11729 2008-01-08 14:32:37Z jk142663 $
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

# this script is run as a cron job on wednesdays. It checks to see if an 
# official build is out - if yes, starts flamebox release tasks.

FLAMEBOX_LOCK_FILE=/tmp/start_flamebox_on_release

SERVER=10.7.228.10

# hc-flamebox mounts hc-dev:/export/release onto /export/release
# if new release directory exists, start flamebox tasks against 
# that release 
cd /export/release/repository/releases/1.1/ 
VERSION=`ls -1trd 1.1-* | tail -1`

if [ -e /tmp/Upgrade_To_${VERSION}_DONE ]; then
    echo "WRN: Flamebox in progress of running tasks against $VERSION"
    echo "WRN: or has completed running tasks against $VERSION"  
    echo "WRN: Check $POST_COMMIT_FILE file for next_dev_build value"
    exit 0 
fi

if [ "$VERSION" != "" ]; then
    echo "Upgrading to version $VERSION"
    URL="http://$SERVER/~hcbuild/repository/releases/1.1/$VERSION/AUTOBUILT/pkgdir/st5800_${VERSION}.iso"
    /usr/sfw/bin/wget -O - -q $URL
    URL_EXISTS=$?
    if [ -e  $FLAMEBOX_LOCK_FILE ]; then
        SAFE_TO_START_RELEASE_BUILD=1
    else 
        SAFE_TO_START_RELEASE_BUILD=0
    fi

    if [ $URL_EXISTS -ne 0 ]; then
        echo "$URL does not exist"
        exit 0
    fi

    if [ $SAFE_TO_START_RELEASE_BUILD -ne 0 ]; then
        echo "$FLAMEBOX_LOCK_FILE exists, cannot proceed with build qual tests"
        exit 0  
    fi

    # Start Flamebox tasks against an official build only
    export URL=$URL 
    touch $FLAMEBOX_LOCK_FILE  
    cd $HOME/svn/trunk/tools/flamebox/config
    rm -f tasks 
    ln -s tasks.release tasks
    cd $HOME/svn/trunk/tools/flamebox/bin
    logger "Start Flamebox Tasks on build $VERSION"
    ./flamebox-client.pl --cluster dev322 --nodes 8 --clients cl38,cl39,cl40,cl37 --isourl $URL -- --once --verbose --taskdir $HOME/svn/trunk/tools/flamebox/config/release/ 
    rm -f $FLAMEBOX_LOCK_FILE
    touch /tmp/Upgrade_To_${VERSION}_DONE 
fi

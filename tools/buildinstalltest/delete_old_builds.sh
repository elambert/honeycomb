#!/bin/sh
#
# $Id: build.sh 6923 2006-02-04 01:15:36Z jd151549 $
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
# usage: delete_old_builds.sh dir 
# 

print_usage () {
    echo "usage: $0 <dir>"
    echo "    Deletes all children directories under <dir> not pointed to by"
    echo "    the symlink 'latest'".
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

cd $TARGETDIR

LATEST=`ls -l $TARGETDIR | grep latest | cut -d '>' -f 2 | cut -d ' ' -f 2`
echo "LATEST: $LATEST"
# youngest could still be in use.
YOUNGEST=`ls -tr1 | grep -v latest | tail -1`
echo "YOUNGEST: $YOUNGEST"

for i in `ls $TARGETDIR | grep trunk`; do
    if [ "$i" = "$LATEST" -o "$i" = "$YOUNGEST" ]; then
        echo "skipping $i"
    else
        echo "deleting $i"
	/bin/rm -rf $TARGETDIR/$i
    fi
done

exit 0

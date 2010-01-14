#!/bin/sh
#
# $Id: quicktest.sh 10853 2007-05-19 02:50:20Z bberndt $
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
# Script to run a set of tests listed in the given file.  This is much
# faster than using the "explore" option to runtest, which inspects all
# tests looking for those that match the given tag.
#

print_usage() {
    echo
    echo "usage: cluster [client | svnroot] [testlistfile]"
    echo
    echo "  cluster      - remote system to be installed, e.g. dev321"
    echo "  client       - remote client on which to run tests, e.g. cl8"
    echo "  svnroot      - defaults to ../.. (relative to script working dir)"
    echo "  testlistfile - tests to run, defaults to local quicktest.list"
    echo
    echo "  If no client is specified, tests are run from local machine, "
    echo "  using the hctest code from the svnroot. Client hostname are  "
    echo "  assumed to start with 'cl' string."
    echo

}

ROOT=`dirname $0`
if [ "$ROOT" = "." ]; then
    ROOT=`pwd`
fi

. $ROOT/common.sh

CLUSTER=$1
SVNROOT=$2
CLIENT=$2
TESTS_TO_RUN=$3
CHEAT=$CLUSTER-cheat
REMOTE_CLIENT=0

if [ -z "$CLUSTER" ]; then
    print_usage
    exit 1
fi

# if no svnroot given, use the one in common.sh
if [ -z "$SVNROOT" ]; then
    SVNROOT="`cd $DEFAULTSVNROOT; pwd`"
else
    # check if this is a svnroot, or client hostname
    replace=`echo "$CLIENT" | sed s/\^cl/XXX/`
    if [ "$CLIENT" != "$replace" ]; then
        REMOTE_CLIENT=1
        SVNROOT="`cd $DEFAULTSVNROOT; pwd`"
    fi
fi

if [ $REMOTE_CLIENT -eq 0 -a ! -d "$SVNROOT" ]; then
    echo "directory $SVNROOT does not exist"
    print_usage
    exit 1
fi

if [ -z "$TESTS_TO_RUN" ]; then
    TESTS_TO_RUN=$ROOT/quicktest.list
fi
if [ ! -f "$TESTS_TO_RUN" ]; then
    echo "file $TESTS_TO_RUN not found"
    print_usage
    exit 1
fi

cd $SVNROOT
SVNROOT=`pwd`

echo
echo "-----------------------------------------------------------------------"
echo "  running quicktest on $CLUSTER from $SVNROOT"
echo "-----------------------------------------------------------------------"
echo
# give user a chance to Ctrl-C and quit before we do anything
#pause_here


trap cleanup_ssh 0 2 15

configure_ssh $SVNROOT

# be sure data vip is up
ping_cheat $CLUSTER-data

VERSION="`echo $USER`_`date +%F-%H%M`"  
TESTARGS="-ctx cluster=$CLUSTER:build=$VERSION:qb=no:noValidate:failearly"
if [ $REMOTE_CLIENT -eq 1 ]; then

    # lauch quicktest on remote client machine
    ping_cheat $CLIENT
    HCTESTBIN="/opt/test/bin"
    
    run_ssh_cmd root@$CLIENT "\
    export PATH=\$PATH:/usr/lib/java/bin;\
    $HCTESTBIN/runtest $TESTARGS `grep -v '^#' $TESTS_TO_RUN`"

else

    # lauch quicktest on local machine
    HCTESTBIN=$SVNROOT/build/hctest/dist/bin

    run $HCTESTBIN/runtest $TESTARGS `grep -v '^#' $TESTS_TO_RUN`

fi

# remember return code of tests; it will be the exit value of this script
return_val=$?

sleep 10

print_date "Finished with testrun at"

exit $return_val
# do not add anything below this line

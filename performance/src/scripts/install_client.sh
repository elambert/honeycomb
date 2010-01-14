#!/bin/bash 
#
#
# $Id: install_client.sh 11485 2007-09-07 22:54:01Z sm152240 $
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
# install performance test suite on specified client
#

PERF_INSTALL_DIR="/opt/performance"
PERF_INSTALL_LIBDIR="${PERF_INSTALL_DIR}/lib"
PERF_INSTALL_BINDIR="${PERF_INSTALL_DIR}/bin"
CLIENT_JAR="build/client/dist/lib/honeycomb-client.jar"
TEST_JAR="build/test/dist/lib/honeycomb-test.jar"
PERF_JAR="build/performance/dist/lib/honeycomb-perftest.jar"
ADVQUERY_JAR="build/performance/dist/lib/honeycomb-advquery.jar"
PERF_SCRIPTS="performance/src/scripts"
PERF_WEBDAV="build/performance/dist/bin"
SLIDE_BASE="external/jakarta-slide-webdavclient-bin-2.1"
SLIDE_LIB="${SLIDE_BASE}/lib"
SLIDE_BIN="${SLIDE_BASE}/bin"

printUsage() 
{
    echo "install_client.sh -r build_path -c client"
}

Cleanup () 
{
    if [ ! -z $TEMPDIR ]; then
        rm -rf $TEMPDIR
    fi
}

IntHandler () 
{
    # no-op to allow return code to pass out
    echo ""
    echo "program interupted; cleaning up."
    Cleanup
    exit 1
}
                                                                                                                                                                                                       
trap IntHandler SIGTERM SIGKILL SIGINT SIGQUIT SIGHUP


SetupSshKey ()
{
    SSHKEYFILE="$ROOT/test/etc/cluster_id_dsa"
    if [ ! -f $SSHKEYFILE ] ; then
        SSHKEYFILE="$HOME/.ssh/cluster_id_dsa"
        if [ ! -f $SSHKEYFILE ] ; then         
            echo -n "cannot locate cluster_id_dsa at $SSHKEYFILE"
            if [ ! -z $ROOT ]  ; then
                echo " or $ROOT/test/etc/cluster_id_dsa. "
            else 
                echo "."
            fi

            SSHKEYFILE="$HOME/.ssh/id_dsa"
            echo "Trying with \"${SSHKEYFILE}\""
            if [ ! -f "$SSHKEYFILE" ] ; then         
                echo "Sheesh, you don't even have that. I give up."
                exit 1
            fi
        fi
    fi

    TEMPDIR=`mktemp -d -p /tmp perf.XXXXXXXX`
    if [ $? -ne 0 ] ; then
        echo "Unable to create $TEMPDIR, fatal, exiting."
        exit 1
    fi

    TEMPKEYFILE="${TEMPDIR}/cluster_id_dsa"
    cp $SSHKEYFILE $TEMPKEYFILE
    if [ $? -ne 0 ] ; then
        echo "Unable to copy $SSHKEYFILE to $TEMPDIR, fatal, exiting."
        Cleanup
        exit 1
    fi

    SSHKEYFILE=$TEMPKEYFILE
    chmod 600 $SSHKEYFILE
    if [ $? -ne 0 ] ; then
        echo "Unable to set permissions to 600 on $SSHKEYFILE, exiting."
        Cleanup
        exit 1
    fi
}

runCommand ()
{
    local COMMAND
    local status
    cstring="$@"
    result=`echo $cstring | grep " > "`
    if [ ! -z "$result" ]; then
        COMMAND="$@"
    else
        COMMAND="$@ 1>&2"
    fi
                                                                                                                                                                                                       
    ssh $TARGETCLIENT -o StrictHostKeyChecking=no -i $SSHKEYFILE -l root $COMMAND
    status=$?
    #
    # For some reason "no route to host" errors place null in status.
    #
    if [ -z "$status" ]; then
        return 1
    fi
    return $status
}


while getopts ":r:c:" opt; do
    case $opt in
        r ) echo "root dir is $OPTARG" 
            ROOT=$OPTARG
            if [ ! -d $ROOT ] ; then
                echo "Can't locate directory $ROOT, aborting."
                exit 1
            fi
            ;;
        c ) echo "target client is $OPTARG"
            TARGETCLIENT=$OPTARG
            ;;
        \? ) printUsage
            exit 1
    esac
done
shift $(($OPTIND -1 ))
        
if [ -z "$ROOT" -o -z "$TARGETCLIENT" ]; then
    echo "build_path and client must be specified"
    printUsage
    exit 1;
fi

SetupSshKey
echo "Installing performance test suite on $TARGETCLIENT"
runCommand "rm -rf $PERF_INSTALL_DIR"
runCommand "mkdir $PERF_INSTALL_DIR"
runCommand "mkdir $PERF_INSTALL_LIBDIR"
runCommand "mkdir $PERF_INSTALL_BINDIR"
scp -i $SSHKEYFILE -o StrictHostKeyChecking=no -r \
    $ROOT/$CLIENT_JAR \
    $ROOT/$TEST_JAR \
    $ROOT/$PERF_JAR \
    $ROOT/$ADVQUERY_JAR \
    $ROOT/$PERF_SCRIPTS/* \
    root@$TARGETCLIENT:$PERF_INSTALL_DIR
if [ $? -ne 0 ] ; then
    echo "failed to install client $TARGETCLIENT."
    Cleanup
    exit 1
fi

scp -i $SSHKEYFILE -o StrictHostKeyChecking=no -r \
    $ROOT/$SLIDE_LIB/* \
    root@$TARGETCLIENT:$PERF_INSTALL_LIBDIR
if [ $? -ne 0 ] ; then
    echo "failed to install slide lib on client $TARGETCLIENT."
    Cleanup
    exit 1
fi

scp -i $SSHKEYFILE -o StrictHostKeyChecking=no -r \
    $ROOT/$SLIDE_BIN/run.sh \
    root@$TARGETCLIENT:$PERF_INSTALL_BINDIR/run_slide.sh
if [ $? -ne 0 ] ; then
    echo "failed to install slide bin on client $TARGETCLIENT."
    Cleanup
    exit 1
fi

scp -i $SSHKEYFILE -o StrictHostKeyChecking=no -r \
    $ROOT/$PERF_WEBDAV/multiload \
    root@$TARGETCLIENT:$PERF_INSTALL_BINDIR
if [ $? -ne 0 ] ; then
    echo "failed to install multiload (webdav) bin on client $TARGETCLIENT."
    Cleanup
    exit 1
fi

Cleanup
exit 0

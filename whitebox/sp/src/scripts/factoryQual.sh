#!/bin/bash
#
# $Id: factoryQual.sh 10857 2007-05-19 03:01:32Z bberndt $
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
#  Run tests for factory qualification of honeycomb cluster.
#  To be run on cheat node.
#
usage()
{
    echo "usage: $0 <cluster-basename> <N_nodes>"
    exit 1
}

if [ $# -eq 0 ] ; then
    usage
fi

DATAVIP=$1-data
ADMINVIP=$1-admin
NNODES=$2

echo ----------------------------------------------------------------
echo Launching internal server tests
echo
/opt/test/bin/cmm_verifier $NNODES factory
if [ $? -ne 0 ] ; then
    echo INTERNAL SERVER TESTS FAILED
    exit 1
fi
echo ----------------------------------------------------------------
echo Launching internal mount tests
echo
/opt/test/bin/dfck.sh $NNODES
if [ $? -ne 0 ] ; then
    echo INTERNAL MOUNT TESTS FAILED
    exit 1
fi
echo ----------------------------------------------------------------
echo Launching client/CLI tests
echo
/opt/test/bin/factoryClient.sh $DATAVIP $ADMINVIP
if [ $? -ne 0 ] ; then
    echo CLIENT/CLI TESTS FAILED
    exit 1
fi
echo ----------------------------------------------------------------
echo Launching cleanup of test files
echo
/opt/test/bin/cleanDisks.sh $NNODES
if [ $? -ne 0 ] ; then
    echo CLEANUP OF TEST FILES FAILED
    exit 1
fi

echo "DONE - reboot nodes?"

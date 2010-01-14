#!/bin/bash 
#
# $Id: install_suitcase.sh 10856 2007-05-19 02:58:52Z bberndt $
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

DBADMIN=honeycomb
WHEREAMI=`cd \`dirname $0\`; pwd`
TESTROOT=$WHEREAMI/..
JARNAME=honeycomb-suitcase.jar
JARPATH=$TESTROOT/lib/$JARNAME
SSHKEYFILE=$TESTROOT/etc/cluster_id_dsa

printUsage() {
    echo "$0 <service processor>"
    echo "install suitcase module onto a cluster"
}

pingNodes() {
    # determine which nodes are online
    echo "determining which nodes are online..."
    local node=0
    while [ $[node++] -lt 16 ] ; do
        ssh $CHEAT -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "ping hcb$[100+$node] 1 2>&1 /dev/null" 2>&1 /dev/null
        if [ $? -eq 0 ] ; then
            NODE_ONLINE[$node]=1
            let NODES_ONLINE++
        else
            NODE_ONLINE[$node]=0
        fi
    done
}

scpJar() {
    scp -o StrictHostKeyChecking=no -i $SSHKEYFILE $JARPATH root@$CHEAT:
    local node=0
    while [ $[node++] -lt 16 ]; do
        if [ ${NODE_ONLINE[$node]} -eq 1 ] ; then
            echo copy to hcb$[100+$node]
            ssh $CHEAT -o StrictHostKeyChecking=no -q -i $SSHKEYFILE -l root "scp $JARNAME hcb$[100+$node]:/opt/honeycomb/lib"
        fi
    done
}

#
# MAIN
#

# process command line args and options
while getopts "h" opt; do
    case $opt in
        h ) printUsage
            exit 0
            ;;
        \? )  printUsage
        exit 1
    esac
done
shift $(($OPTIND -1 ))

CHEAT=$1
if [ -z $CHEAT ] ; then
    printUsage
    echo "Please specify a service processor name."
    exit 1
fi

pingNodes
scpJar
exit 0

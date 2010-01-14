#! /usr/bin/env bash

#
# $Id: start_cmm_single.sh 10854 2007-05-19 02:52:12Z bberndt $
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

# Start all the CMM nodes

DIR=`cd \`dirname $0\`; pwd`
DIR=`dirname $DIR`

start_node() {
    # nodeid jarfile
    java -Dcmm.test.nodeid=$1 -Dcmm.test.root=$DIR -jar $2 &
    if [ $? ]
    then
	echo "Node $1 has been started"
    else
	echo "Failed to start node $1"
	exit 1
    fi
}

if [ $# -gt 0 ]
then
    for node in $@
    do
      start_node $node $DIR/lib/CMMTest.jar
    done
    exit 0
fi

NB_NODES=16

i=101
while [ $i -lt $(($NB_NODES+101)) ]
do
  start_node $i $DIR/lib/CMMTest.jar
  ((i++))
  sleep 1
done

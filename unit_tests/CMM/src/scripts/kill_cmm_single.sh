#! /usr/bin/env bash

#
# $Id: kill_cmm_single.sh 10854 2007-05-19 02:52:12Z bberndt $
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

PSOPTS="pid args"
if [ `uname` = Darwin ]
then
    PSOPTS="pid command"
fi

kill_node() {
    echo "Killing node [$1]"

    PSLINE=`ps -a -o "$PSOPTS" | grep java | grep "nodeid=$1"`

    PID=`echo $PSLINE | awk '{ print $1 }'`

    echo "Process is [$PID]."
    echo "$PSLINE"
    echo

    kill $PID
}


if [ $# -gt 0 ]
then
    for i in $@
      do
      kill_node $i
    done
    exit 0
fi

NB_NODES=16

echo "Do you really want to kill all the $NB_NODES nodes ? [y-n]"
read ANSWER

if [ $ANSWER != y ]
then
    echo "Cancelled"
    exit 1
fi

i=101
while [ $i -lt $(($NB_NODES+101)) ]
do
  kill_node $i
  ((i++))
done


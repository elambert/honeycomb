#!/bin/bash
#
# $Id: update-client-parallel.sh 10858 2007-05-19 03:03:41Z bberndt $
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
echo "there are $# arguments"




#(12:02:16) eswierk: i=$(($# - 1))
#(12:02:25) eswierk: echo ${!i}
#${!foo} means indirect expansion

for thingy in "$@"; do
   lagger=$clientStartNumber
   clientStartNumber=$clientEndNumber
   clientEndNumber=$thingy

   parameterString=`echo $parameterString $lagger`
done
echo  ClientStartNumber, clientend number: $clientStartNumber $clientEndNumber


if [ -z "$parameterString" ] ; then 
    echo "no arguments? okay...."
fi

if [ -z "$clientStartNumber" ] ; then 
    echo "Start node, please?"
    exit 1
fi


if [ -z "$clientEndNumber" ] ; then 
    echo "End node, please?"
    exit 1
fi

echo da rest: $parameterString

utilsDir=`dirname $0`
utilsDir=`cd $utilsDir; pwd`

if [ ! -f $utilsDir/update-client.sh ] ; then
    echo "Can't locate $utilsDir/updat-client.sh, fatal, exiting."
    exit 1
fi

clientcount=0
while test $clientStartNumber -le $clientEndNumber; do
    node=$clientStartNumber    


    echo "updating: cl$node"
    $utilsDir/update-client.sh $parameterString $node > /dev/null 2>&1 &
    process=$!
    pidArray[$clientcount]=$process
    clientStartNumber=`expr $clientStartNumber + 1`
    clientcount=`expr $clientcount + 1`
done

i=0
while test $i -lt $clientcount; do
    echo waiting for process $i ${pidArray[$i]}
    wait ${pidArray[$i]}
    i=`expr $i + 1`
done

#!/bin/bash
#
# $Id: harvest.sh 10858 2007-05-19 03:03:41Z bberndt $
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


if [ -z "$1" ] ; then 
    echo "Start node, please?"
    exit 1
fi

if [ -z "$2" ] ; then 
    clientEndNumber=$1
else
    clientEndNumber=$2
fi
origStart=$1
clientStartNumber=$1
clientcount=0
while test $clientStartNumber -le $clientEndNumber; do
    node=$clientStartNumber    
    if [ $node -lt 10 ]; then             
        source=hcb10$node
        ipnum=10$node
    else
        source=hcb1$node
        ipnum=1$node
    fi

    ssh $source -o StrictHostKeyChecking=no "rm -rf /fragments"
    ssh $source -o StrictHostKeyChecking=no "java -classpath /honeycomb-utests.jar:/opt/honeycomb/lib/honeycomb.jar:/opt/honeycomb/lib/honeycomb-server.jar:/opt/honeycomb/lib/honeycomb-common.jar:/opt/honeycomb/lib/jug.jar -Djava.library.path=/opt/honeycomb/lib/ com.sun.honeycomb.util.FragExplorer -c /netdisks/10.123.45.$ipnum/data/?  > /fragments 2> /dev/null"   &
    pidArray[$node]=$!

    clientStartNumber=`expr $clientStartNumber + 1`
    clientcount=`expr $clientcount + 1`
done

i=0
while test $i -lt $clientcount; do
    echo waiting for ${pidArray[$i]}
    wait ${pidArray[$i]}
    i=`expr $i + 1`
done


clientStartNumber=$origStart

rm -rf /utils/allfrags
while test $clientStartNumber -le $clientEndNumber; do
    node=$clientStartNumber    
    rm -rf /utils/cookedFrag.$node

    if [ $node -le 9 ] ; then             
        source=hcb10$node
    else
        source=hcb1$node
    fi


    scp $source:/fragments /utils/fragments.$source
    echo parsing fragments from $source
    ./parsecsv.pl /utils/fragments.$source > /utils/cookedFrag.$node &
    pidArray[$node]=$!
    clientStartNumber=`expr $clientStartNumber + 1`
done
clientStartNumber=$origStart
i=$clientStartNumber
while test $i -le $clientEndNumber; do
    echo waiting for ${pidArray[$i]}
    wait ${pidArray[$i]}
    i=`expr $i + 1`
done




while test $clientStartNumber -le $clientEndNumber; do
    node=$clientStartNumber
    if [ $node -le 9 ] ; then             
        source=hcb10$node
    else
        source=hcb1$node
    fi

    cat /utils/cookedFrag.$node >> /utils/allfrags
#    rm -rf /utils/cookedFrag.$node
#    rm -rf /utils/fragments.$source
    clientStartNumber=`expr $clientStartNumber + 1`
done






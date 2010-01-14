#!/bin/bash
#
# $Id: factoryClient.sh 10857 2007-05-19 03:01:32Z bberndt $
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
#  Test cluster w/ basic client and cli activity
#  File sizes: 1k, 100m, 1g, 2g
#
#  Usage: factoryClient.sh <datavip> <adminvip>
#  
usage()
{
    echo "Usage: factoryClient.sh <datavip> <adminvip>"
    exit 1
}
if [ $# -ne 2 ] ; then
    usage
fi
DATAVIP=$1
ADMINVIP=$2

TMP=/tmp

#
#  Create test files
#
FILES[0]=$TMP/$$.1k
FILES[1]=$TMP/$$.100m
FILES[2]=$TMP/$$.1g
FILES[3]=$TMP/$$.2g
TIME=time
echo create 1k..
$TIME dd if=/dev/zero of=${FILES[0]} bs=1k count=1
echo create 100m..
$TIME dd if=/dev/zero of=${FILES[1]} bs=1024k count=100
echo create 1g..
$TIME dd if=/dev/zero of=${FILES[2]} bs=1024k count=1024
echo create 2g..
$TIME dd if=/dev/zero of=${FILES[3]} bs=1024k count=2048

# 100m takes 25 sec from /dev/urandom on hon proxy
#time dd if=/dev/urandom of=/tmp/med.$$ bs=1024k count=100

#
#  client proc
#
client_ops()
{
    infile=${FILES[$1]}
    retfile=${infile}_ret

    echo store $infile ..
    OID=`/opt/test/bin/store $DATAVIP $infile`  || return 1
    echo stored $infile as $OID

    echo retrieve $OID to $retfile ..
    /opt/test/bin/retrieve $DATAVIP $OID $retfile  || return 1

    echo compare $retfile to original file..
    cmp -s $infile $retfile
    if [ $? -ne 0 ] ; then
        echo retrieved file $retfile corrupted
        return 1
    fi
    echo files match: $infile $retfile

    echo delete $retfile as $OID ..
    /opt/test/bin/delete $DATAVIP $OID  || return 1

#maybe fix this in sdk to not be so noisy
#/opt/test/bin/retrieve $DATAVIP $OIDsmall /tmp/small_ret.$$ || echo failure ok
    echo "thread $1 done ($infile)"

    return 0
}

echo status..
STATUS=`ssh admin@$ADMINVIP sysstat`
echo $STATUS

echo LAUNCHING CLIENTS
for i in 0 1 2 3 ; do
    client_ops $i &
    THREADS[$i]=$!
done

echo WAITING FOR CLIENTS..
fail=0
c=0
while test $c -lt 4 ; do
    wait ${THREADS[$c]}
    ret=$?
    if [ $ret -ne 0 ] ; then
        echo "client ${FILES[$c]} done.. FAIL"
        fail=$(( $fail + 1 ))
    else
        echo "client ${FILES[$c]} done.. OK"
    fi
    c=$(( $c + 1 ))
done

if [ $fail -ne 0 ] ; then
    echo CLIENT TESTS FAILED
    exit 1
fi

echo status..
STATUS=`ssh admin@$ADMINVIP sysstat`
echo $STATUS

#
#  clean up
#
/bin/rm -f $TMP/$$.*

echo CLIENT TESTS OK
exit 0

#!/bin/bash
#
# $Id: launch_webdav_ofoto_reg.sh 11676 2007-12-03 19:44:55Z wr152514 $
#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# Use is subject to license terms.
#

# run c ofoto for regression test
# needs ofoto_2 schema

if [ $# != 5 ]; then
    echo "Usage: $0 <trunk> <datavip> <n_nodes> <client1> <client2>"
    exit 1
fi

TRUNK=$1
CLUSTER=$2
NODES=$3
CLNT1=$4
CLNT2=$5

if [ "$NODES" != "16" ] && [ "$NODES" != "8" ]; then
    echo n_nodes should be 8 or 16
    exit 1
fi

echo TRUNK=$TRUNK
echo CLUSTER=$CLUSTER
echo NODES=$NODES
echo "CLIENTS=$CLNT1 $CLNT2"

date

LOG1=/tmp/webdav_ofoto_$CLNT1.out.$$
LOG2=/tmp/webdav_ofoto_$CLNT2.out.$$
##############################################

#
#  copy files to clients
#
echo copying to clients..
ssh -q -o StrictHostKeyChecking=no -i $TRUNK/hctest/etc/ssh/id_dsa \
		root@$CLNT1 mkdir -p /mnt/test/ofoto
ssh -q -o StrictHostKeyChecking=no -i $TRUNK/hctest/etc/ssh/id_dsa \
		root@$CLNT2 mkdir -p /mnt/test/ofoto

scp -q -o StrictHostKeyChecking=no -i $TRUNK/hctest/etc/ssh/id_dsa \
	$TRUNK/hctest/src/webdav/ofoto/c/Linux/examples/ofoto/put \
				root@$CLNT1:/mnt/test/ofoto/
scp -q -o StrictHostKeyChecking=no -i $TRUNK/hctest/etc/ssh/id_dsa \
	$TRUNK/hctest/src/webdav/ofoto/c/Linux/examples/ofoto/get \
				root@$CLNT1:/mnt/test/ofoto/
scp -q -o StrictHostKeyChecking=no -i $TRUNK/hctest/etc/ssh/id_dsa \
	$TRUNK/hctest/src/scripts/webdav_ofoto.sh \
				root@$CLNT1:/mnt/test/ofoto/
scp -q -o StrictHostKeyChecking=no -i $TRUNK/hctest/etc/ssh/id_dsa \
	$TRUNK/hctest/src/webdav/ofoto/c/Linux/examples/ofoto/put \
				root@$CLNT2:/mnt/test/ofoto/
scp -q -o StrictHostKeyChecking=no -i $TRUNK/hctest/etc/ssh/id_dsa \
	$TRUNK/hctest/src/webdav/ofoto/c/Linux/examples/ofoto/get \
				root@$CLNT2:/mnt/test/ofoto/
scp -q -o StrictHostKeyChecking=no -i $TRUNK/hctest/etc/ssh/id_dsa \
	$TRUNK/hctest/src/scripts/webdav_ofoto.sh \
				root@$CLNT2:/mnt/test/ofoto/

#
#  run program
#
echo "STARTING $CLNT1 > $LOG1"
ssh  -q -o StrictHostKeyChecking=no -i $TRUNK/hctest/etc/ssh/id_dsa \
     root@$CLNT1 /mnt/test/ofoto/webdav_ofoto.sh $CLUSTER $NODES > $LOG1 2>&1 &
PID1=$!
echo "STARTING $CLNT2 > $LOG2"
ssh  -q -o StrictHostKeyChecking=no -i $TRUNK/hctest/etc/ssh/id_dsa \
     root@$CLNT2 /mnt/test/ofoto/webdav_ofoto.sh $CLUSTER $NODES > $LOG2 2>&1 &
PID2=$!

FAIL=0
echo "WAITING for $CLNT1 > $LOG1"
wait $PID1
if [ $? != 0 ]; then
    echo $CLNT1 test: FAIL
    echo =====================================================================
    cat $LOG1
    echo =====================================================================
    FAIL=1
fi
echo "WAITING for $CLNT2 > $LOG2"
wait $PID2
if [ $? != 0 ]; then
    echo $CLNT2 test: FAIL
    echo =====================================================================
    cat $LOG2
    echo =====================================================================
    FAIL=1
fi

if [ "$FAIL" == "1" ] ; then
    echo WebDAV tests: FAIL
    exit 1
else
    /bin/rm $LOG1 $LOG2
fi

date
echo WebDAV tests: PASS


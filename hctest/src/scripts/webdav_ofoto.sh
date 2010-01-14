#!/bin/bash
#
# $Id: webdav_ofoto.sh 11630 2007-10-31 18:36:46Z wr152514 $
#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# Use is subject to license terms.
#

if [ $# != 2 ]; then
    echo "Usage: $0 <datavip> <n_nodes>"
    exit 1
fi
DATAVIP=$1
NODES=$2

iterations=
if [ "$NODES" == "16" ]; then
    for i in {1..50}; do
        iterations="$iterations $i"
    done
elif [ "$NODES" == "8" ]; then
    for i in {1..25}; do
        iterations="$iterations $i"
    done
else
    echo n_nodes should be 8 or 16
    exit 1
fi

OFOTODIR=/mnt/test/ofoto

PUT=$OFOTODIR/put
GET=$OFOTODIR/get
PHASE1=phase1.$$
PHASE2=phase2.$$

if [ ! -d $OFOTODIR ] ; then
    echo $OFOTODIR not found
    exit 1
fi
if [ ! -x $PUT ] ; then
    echo $PUT not found/executable
    exit 1
fi
if [ ! -x $GET ] ; then
    echo $GET not found/executable
    exit 1
fi

echo PHASE1: puts followed by gets

echo launching puts
for i in $iterations; do
    start=`echo $i \* 2 | bc`
    end=`expr $start + 1`
    $PUT $DATAVIP $start $end $PHASE1 -1 verbose > /mnt/test/of_$$_$i.out &
    PUTS[$i]=$!
done
echo waiting for puts
ERR=0
for i in $iterations; do
    wait ${PUTS[$i]}
    if [ $? != 0 ]; then
        ERR=`expr $ERR + 1`
    else
        tail -1 /mnt/test/of_$$_$i.out
    fi
done
if [ $ERR != "0" ] ; then
    echo $ERR put errs
    exit 1
fi

echo puts ok

echo launching gets
for i in $iterations; do
    start=`echo $i \* 2 | bc`
    end=`expr $start + 1`
    $GET $DATAVIP $start $end $PHASE1 -1 validate &
    GETS[$i]=$!
done
echo waiting for gets
ERR=0
for i in $iterations; do
    wait ${GETS[$i]}
    if [ $? != 0 ]; then
        ERR=`expr $ERR + 1`
    else
        /bin/rm /mnt/test/of_$$_$i.out
    fi
done
if [ $ERR != "0" ] ; then
    echo $ERR get errs
    exit 1
fi
echo gets ok

echo PHASE2: puts w/ gets

echo launching puts
for i in $iterations; do
    start=`echo $i \* 2 | bc`
    end=`expr $start + 1`
    $PUT $DATAVIP $start $end $PHASE2 -1 &
    PUTS[$i]=$!
done

echo launching gets
for i in $iterations; do
    start=`echo $i \* 2 | bc`
    end=`expr $start + 1`
    $GET $DATAVIP $start $end $PHASE1 -1 validate &
    GETS[$i]=$!
done

echo waiting for puts
ERR=0
for i in $iterations; do
    wait ${PUTS[$i]}
    if [ $? != 0 ]; then
        ERR=`expr $ERR + 1`
    fi
done
if [ $ERR != "0" ] ; then
    echo $ERR put errs
    exit 1
fi

echo waiting for gets
ERR=0
for i in $iterations; do
    wait ${GETS[$i]}
    if [ $? != 0 ]; then
        ERR=`expr $ERR + 1`
    fi
done
if [ $ERR != "0" ] ; then
    echo $ERR get errs
    exit 1
fi

echo PASS


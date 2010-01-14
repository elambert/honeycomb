#!/bin/sh

#
# This script runs on the cheat node. 
# Given OID in internal (dot notation) format, print its fragment file paths on cluster nodes.
#
# For example:
# ./find_oid.sh 8ad7b329-91ed-11db-833c-00e081594d96.0.0.3664.2.2.0.1342
#
# If you have external (client-side) OID, convert it to internal format first
# by using ConvertOid tool on a test client.
#

OID=$1

NODES="101 102 103 104 105 106 107 108 109 110 111 112 113 114 115 116"

UID=`echo $OID |cut -d"." -f1`
MAP=`echo $OID |cut -d"." -f8`

while [ `echo -n $MAP |wc -c |tr -d ' '` -lt 4 ]; do
    MAP=0$MAP
done
MAPHI=`echo $MAP | cut -c 1-2`
MAPLO=`echo $MAP | cut -c 3-4`

for i in $NODES; do 
    echo hcb$i = `ssh hcb$i ls -l /data/?/${MAPHI}/${MAPLO}/${UID}* 2>&1 |grep -v "No such file"`
done


#!/bin/bash
# $Id: backup.sh 10857 2007-05-19 03:01:32Z bberndt $
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

CURL=/opt/sfw/bin/curl
RCP=`which rcp`
SSHOPTIONS="-q -o StrictHostKeyChecking=no"

if [ $1 == '-h' ] 
then
	echo "Backup Tool Help"
	echo "backup.sh operation filename [t1 t2]"
	echo ""
	echo "          operation - operation to complete backup or restore accepted."
	echo "          filename  - filename to use during the previous operation."
	echo "          t1        - timestamp after which all objects should be backed up."
	echo "          t2        - timestamp before which all objects should be backed up."
	echo "                      replay the current objects. Only in restore mode."
	echo ""
	echo "Example: backup.sh backup /export/b1 0 99999999999"
	exit 0	
fi

MASTER=hcb`/opt/honeycomb/bin/nodemgr_mailbox.sh  | grep "ALIVE MASTER" | awk '{print $1}'`
# HACK!!! because current cli is broken.. but should be replaced with netcfg call
#         to figure out what the datavip is
DATA_VIP=`ifconfig bge1000:3 | grep inet | awk '{print $2}'`

PORT=8080
OPERATION=$1
FILE=$2
T1=$3
T2=$4

# double \\ on the " around the variables so that the ssh doesn't consume them
# before they reach their destination
if [ "$T1" == "" ]
then
	T1=0
fi

if [ "$T2" == "" ]
then
	T2=0
fi

EXPRESSION="com.sun.honeycomb.oa.bulk.BackupRestoreMain.doOperation(\\\"$OPERATION\\\",\\\"/var/adm/backup\\\",${T1}L,${T2}L);"

if [ $OPERATION == "restore" ]
then
	rcp $FILE $MASTER:/var/adm/backup
fi

echo "Running $OPERATION from $MASTER"
RESPONSE=`ssh $SSHOPTIONS $MASTER "$CURL -d expression=\"$EXPRESSION\" http://$DATA_VIP:$PORT/eval 2> /dev/null"`

if [ $? != 0 ] 
then
	echo "A problem was encountered running backup."
	echo "Response: $RESPONSE"
	exit -1
fi

echo $RESPONSE | grep -i "error" 
if [ $? == 0 ]
then
	echo "A problem was encountered running backup."
	echo "Response: $RESPONSE"
	exit -1
fi

if [ $OPERATION == "backup" ]
then
	rcp $MASTER:/var/adm/backup $FILE
fi


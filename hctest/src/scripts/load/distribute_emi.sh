#!/bin/bash
#
# $Id: distribute_emi.sh 11253 2007-07-19 21:18:53Z jk142663 $
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
# Easy script to start EMI load test on different clients in store, retrieve, or query mode.
# Run from a test client, start under /mnt/test/<somedir> to make sure you have space for large output files.
# 
# To stop the test:
# cd /mnt/test/<somedir>
# ./distribute_emi.sh stop
#

CLIENTS="cl01 cl02 cl03 cl04"
CLUSTER=dev123

MINSIZE=1000000
MAXSIZE=100000000
TIMEOUT=-1
NUMOPS=-1
THREADS=15
EXTENDED_METADATA=0
CONTENT_VERIFICATION=1

CLASSPATH=/opt/test/lib/honeycomb-hctest.jar:/opt/test/lib/honeycomb-test.jar:\
/opt/test/lib/honeycomb-client.jar 

if [ "$1" == "stop" ]
then
	for CL in $CLIENTS
	do
		ssh $CL	"pkill -f $CLUSTER" 
	done
	pkill -f $CLUSTER 
	wait &
	exit 0
fi

if [ "$1" == "store" ]
then
     for CL in $CLIENTS
     do
         ssh $CL "/usr/lib/java/bin/java -classpath $CLASSPATH \
          com.sun.honeycomb.test.stress.StoreStress \
          ${CLUSTER}-data $THREADS $MINSIZE $MAXSIZE $TIMEOUT $NUMOPS $EXTENDED_METADATA" \
          >> $CL.oids &
     done
fi

if [ "$1" == "retrieve" ]
then
     for CL in $CLIENTS
     do
         cat $CL.oids | ssh $CL "/usr/lib/java/bin/java -classpath $CLASSPATH \
             com.sun.honeycomb.test.stress.RetrieveStress \
             ${CLUSTER}-data $THREADS $CONTENT_VERIFICATION" >> $CL.retrieves &
     done
fi

if [ "$1" == "query" ]
then
     for CL in $CLIENTS
     do
         cat $CL.oids | ssh $CL "/usr/lib/java/bin/java -classpath $CLASSPATH com.sun.honeycomb.test.stress.QueryStress ${CLUSTER}-data $THREADS $TIMEOUT $NUMOPS -" >> $CL.queries &
     done
fi

wait 

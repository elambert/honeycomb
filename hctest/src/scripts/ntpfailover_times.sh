#!/bin/bash
#
# $Id: ntpfailover_times.sh 10858 2007-05-19 03:03:41Z bberndt $
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

if [ $# -lt 2 ]; then
    echo 
    echo "$0: <cluster> <number of test iterations>"
    echo 
    echo "Example Usage: $0 dev310 3"
    echo 
    exit 1
fi

SSH="/usr/bin/ssh -o StrictHostKeyChecking=no -q"  
ADMINVIP="$1-admin"
DATAVIP="$1-data"
MAXITER=$2
iter=0

while [ $iter -lt $MAXITER ]; 
do

    echo "--------------------------------------------------"
    echo "            Iteration $iter"
    echo "--------------------------------------------------"

    # Master node before failover
    echo "`$SSH $ADMINVIP hostname` is the master before failover"
    $SSH $ADMINVIP reboot 

    sleep 600 

    # Master node after failover
    echo "`$SSH $ADMINVIP hostname` is the master after failover"
    SERVERS=`$SSH $ADMINVIP grep server /etc/inet/external_ntp.conf | awk {'print $2'}`
    STARTTIME=`$SSH $ADMINVIP grep \'ntpd 4.2.0\' /var/adm/external_ntp.log | tail -1 | awk {'print $3'}` 
    LOCALSYNCTIME=`$SSH $ADMINVIP grep -i \'synchronized to LOCAL\' /var/adm/external_ntp.log | tail -1 | awk {'print $3'}` 
    retry=0
    while [ $retry -lt 10 ]; 
    do
        $SSH $ADMINVIP ntpq -p | grep -v LOCAL | cut -f 1 | grep '*' 2>&1 >> /dev/null 
        if [ $? -eq 0 ]; then
            EXTSERVERSYNCTIME=`$SSH $ADMINVIP grep -i \'synchronized to $s\' /var/adm/external_ntp.log | tail -1 | awk {'print $3'}`
            break 
        fi 
        sleep 300 
        retry=`expr $retry + 1` 
    done
    echo "start time $STARTTIME, local sync time $LOCALSYNCTIME, external sync time $EXTSERVERSYNCTIME"

    echo
    echo
    iter=`expr $iter + 1` 
done

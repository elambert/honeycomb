#!/bin/bash
#
# $Id: start_io.sh 11907 2008-03-05 23:03:25Z wr152514 $
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

# timeout on socket ops in sec
SOCK_TIMEOUT=60

usage() {
    echo "$0 -o [STR|RTV|QRY] -c client -s cluster -t <test duration> -f <oid file> -i <svn trunk>"
    exit 1
}

# Main
while getopts "ho:c:i:s:t:f:" option; do
    case "$option" in
        o) OP=$OPTARG ;;
        c) CL=$OPTARG ;;
        i) WS=$OPTARG ;;
        s) CLUSTER=$OPTARG ;; 
        t) DURATION=$OPTARG ;; 
        f) OIDFILE=$OPTARG ;;
        h) usage ;;
        *) exit 1 ;; 
    esac    
done

SSH=/usr/bin/ssh
SSH_OPTIONS="-o StrictHostKeyChecking=no -l root -q -i ${WS}/hctest/etc/ssh/id_dsa"

LOGDIR="/tmp/${CLUSTER}"
if [ ! -d $LOGDIR ]; then
   mkdir $LOGDIR 
fi

case "$OP" in  
    STR) 
    LOGFILE="stores.${CL}"
    LOGFILE_ERR="stores.${CL}.err"
    EMI_STORE=/opt/test/bin/load/emi_store.sh
    EMI_TEST_CMD_LINE="$EMI_STORE ${CLUSTER}-data 20 0 10485760 binary 1 $DURATION -1 0 $SOCK_TIMEOUT"
    ;; 
    RTV)
    LOGFILE="retrieves.${CL}"
    LOGFILE_ERR="retrieves.${CL}.err"
    $SSH $SSH_OPTIONS $CL "sed -e \"s/DATAVIP=dev3XX-data/DATAVIP=$CLUSTER-data/\" /opt/test/bin/load/emi-load/ENV > /tmp/ENV.tmp"
    $SSH $SSH_OPTIONS $CL mv /tmp/ENV.tmp /opt/test/bin/load/emi-load/ENV 
    EMI_RETRIEVE=/opt/test/bin/load/emi-load/retrieve-each.sh
    EMI_TEST_CMD_LINE="$EMI_RETRIEVE ${CLUSTER}-data 20 binary 1 $OIDFILE $SOCK_TIMEOUT"
    ;;
    QRY)
    LOGFILE="queries.${CL}"
    LOGFILE_ERR="queries.${CL}.err"
    $SSH $SSH_OPTIONS $CL "sed -e \"s/DATAVIP=dev3XX-data/DATAVIP=$CLUSTER-data/\" /opt/test/bin/load/emi-load/ENV > /tmp/ENV.tmp"
    $SSH $SSH_OPTIONS $CL mv /tmp/ENV.tmp /opt/test/bin/load/emi-load/ENV 
    EMI_QUERY=/opt/test/bin/load/emi-load/query-each.sh
    EMI_TEST_CMD_LINE="$EMI_QUERY ${CLUSTER}-data 20 $OIDFILE $SOCK_TIMEOUT" 
    ;;
esac

echo "Starting EMI $OP Test, cluster = $CLUSTER, client = $CL"
$SSH $SSH_OPTIONS $CL "PATH=/usr/lib:/bin:/usr/sbin:/opt/test/bin:/usr/local/bin:/usr/lib/java/bin:/usr/bin:\$PATH $EMI_TEST_CMD_LINE" > $LOGDIR/$LOGFILE 2>$LOGDIR/$LOGFILE_ERR &

#! /bin/sh -x
#
# $Id: run_cmm_tests.sh 11634 2007-11-01 22:16:51Z sm193776 $
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

# This is a helper script for running the basic CM Tests in a 
# sequence.  See https://hc-twiki.sfbay.sun.com/twiki/bin/view/Main/CmTests
# The cluster should already be setup in cmmonly mode per the twiki
# page.


# launch in background like this after filling in valid values
# for the parameters below
# nohup /opt/test/bin/run_cmm_tests.sh > /mnt/test/cmm-log.out 2>&1 &

CLUSTER=dev3XX
NODES=8
ITERATIONS=500
MODE=FULL_HC

while getopts "c:n:i:m:" o; do
    case "$o" in
        c) CLUSTER=$OPTARG ;; 
        n) NODES=$OPTARG ;; 
        i) ITERATIONS=$OPTARG ;;
        m) MODE=$OPTARG ;;
        *) exit 1 ;; 
    esac 
done


runtest com.sun.honeycomb.hctest.cases.ClusterStartup -ctx cluster=$CLUSTER:nodes=$NODES:noskip:$MODE:nocluster:startskew=0:starttimeout=0:stoptimeout=0:iterations=$ITERATIONS

runtest com.sun.honeycomb.hctest.cases.NodeBounce -ctx cluster=$CLUSTER:nodes=$NODES:noskip:$MODE:fail=hc:starttimeout=30:stoptimeout=60:startskew=0:bounce=2:iterations=$ITERATIONS

runtest com.sun.honeycomb.hctest.cases.MasterFailover -ctx cluster=$CLUSTER:nodes=$NODES:noskip:$MODE:fail=hc:startskew=0:starttimeout=30:stoptimeout=60:iterations=$ITERATIONS

runtest com.sun.honeycomb.hctest.cases.MasterFailover -ctx cluster=$CLUSTER:nodes=$NODES:noskip:$MODE:fail=hc:startskew=0:starttimeout=30:stoptimeout=60:iterations=$ITERATIONS:vice

runtest com.sun.honeycomb.hctest.cases.MasterFailover -ctx cluster=$CLUSTER:nodes=$NODES:noskip:$MODE:fail=hc:startskew=0:starttimeout=30:stoptimeout=60:iterations=$ITERATIONS:vice=aswell

runtest com.sun.honeycomb.hctest.cases.CMMFaultyService -ctx cluster=$CLUSTER:nodes=$NODES:noskip

runtest com.sun.honeycomb.hctest.cases.CMMSlowServiceStartup -ctx cluster=$CLUSTER:nodes=$NODES:noskip

#!/bin/bash
#
# $Id: powercycle_loop.sh 10858 2007-05-19 03:03:41Z bberndt $
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
# This script is meant to run from a test client. 
# It powercycles all cluster nodes using ipmi;
# then waits for cluster to come back online;
# then waits another 30 minutes to allow I/O to occur;
# then powercycles all nodes again, forever.
#
# The test also starts I/O. It assumes having script
# distribute_emi.sh with right cluster/clients in current dir.
# Contains hack to restart I/O after powercycling cluster,
# otherwise load test tends to get stuck.
#
# The purpose of this test is to trigger UFS corruption
# as a result of abrupt poweroff of nodes during I/O,
# or to prove that such corruption does not occur.
#
# Run overnight or over a weekend.
#
# Author: Sam Cramer, Daria Mehra.
#

CLUSTER=dev403
SP=root@${CLUSTER}-cheat
ADMIN=admin@${CLUSTER}-admin

NODES="101 102 103 104 105 106 107 108"
#NODES="101 102 103 104 105 106 107 108 109 110 111 112 113 114 115 116"

ssh $SP "echo honeycomb >ipmi_passwd"

while [ true ]; do 

  # Wait for cluster to come back online. 
  # We check for data services, not HADB; this test ignores HADB status.
  #
  ONLINE=`echo yes |ssh $ADMIN sysstat |grep "Data services" |cut -d" " -f3`
  while [ "$ONLINE" != "Online," ]; do
    sleep 300
    ONLINE=`echo yes |ssh $ADMIN sysstat |grep "Data services" |cut -d" " -f3`
  done

  echo [`date`] Data services online, cluster state:
  echo
  echo yes |ssh $ADMIN sysstat
  echo

  echo [`date`] Restarting load test in case it got stuck
  echo
  ./distribute_emi.sh stop
  echo [`date`] Restarting load >>stores.out 
  nohup ./distribute_emi.sh store >>stores.out 2>&1 </dev/null &

  # Allow 10 minutes for cluster to do I/O
  #
  echo [`date`] Waiting 10 minutes before next powercycling
  sleep 600

  echo [`date`] Cluster state before powercycling:
  echo
  echo yes |ssh $ADMIN sysstat
  echo

  echo [`date`] Powercycling all cluster nodes

  for i in $NODES; do
    echo -n "hcb$i.."
    ssh $SP ipmitool -I lan -H hcb$i-sp -U Admin -f ipmi_passwd chassis power cycle
  done
  echo

done

#! /bin/bash
#
# $Id: switch_failover_verifier.sh 10858 2007-05-19 03:03:41Z bberndt $
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

if [ $# -lt 1 ] || [ $# -gt 1 ]; then
   echo "$0 <cluster>"
   exit 1
fi

cluster=$1
admin_vip="${cluster}-admin" 
data_vip="${cluster}-data"
SSHARGS="-l nopasswd -p 2222 -o StrictHostKeyChecking=no -q"
switch_virtual_ip=10.123.45.1
ssh_switch="ssh $SSHARGS $admin_vip"
$ssh_switch ssh $SSHARGS $switch_virtual_ip "cat /etc/honeycomb/switch.conf" > /tmp/switch.conf
. /tmp/switch.conf
pswitch_ip=$SWITCH_THIS_INTERCONNECT
sswitch_ip=$SWITCH_OTHER_INTERCONNECT
rm -f /tmp/switch.conf
ssh_pswitch="$ssh_switch ssh $SSHARGS $pswitch_ip"
ssh_sswitch="$ssh_switch ssh $SSHARGS $sswitch_ip"

# Verification Steps:

# Primary Switch
# 1. verify date 
# 2. verify membership
# 3. verify services are up and running (netstat -anp)
# 4. verify iptables
# 5. verify ports and its connections (zlc) 
# 6. admin vip configured
verify_pswitch_status() {
   $ssh_pswitch ping -c 1 $sswitch_ip 
   if [ $? -ne 0 ]; then
      return 
   fi 
   $ssh_pswitch date
   $ssh_pswitch vrrpconfig -a
   $ssh_pswitch netstat -anp
   $ssh_pswitch iptables -t nat -L
   $ssh_pswitch zlc zre1..24 query
   $ssh_pswitch ifconfig zhp1
}

# Secondary Switch (if up)
# 1. Verify date 
# 2. verify membership
# 3. verify services are up and running (netstat -anp)
# 4. no admin vip configured
# 5. verify ports and its connections (zlc) 
# 6. verify iptables
verify_sswitch_status() {
   $ssh_sswitch ping -c 1 $sswitch_ip 
   if [ $? -ne 0 ]; then
      return 
   fi 
   $ssh_sswitch date
   $ssh_sswitch vrrpconfig -a
   $ssh_sswitch netstat -anp
   $ssh_sswitch iptables -t nat -L
   $ssh_sswitch zlc zre1..24 query
   $ssh_sswitch ifconfig zhp1
}

# Master Node
# 1. NodeManagerMailbox 
# 2. spreader rules
# 3. hadb status
verify_masternode_status() {
#  echo "ssh_masternode : $ssh_masternode"
   $ssh_masternode date 
   $ssh_masternode /opt/honeycomb/bin/nodemgr_mailbox.sh

   # No. of nodes ALIVE in the cluster 
   nodes=`$ssh_masternode grep honeycomb.cell.num_nodes /config/config.properties | awk -F= {'print $2'}` 
    
   irulesok=0
   while [ $irulesok -eq 0 ]; 
   do
      $ssh_masternode /opt/honeycomb/bin/irules.sh | sort -n -k 12
      irules=`$ssh_masternode /opt/honeycomb/bin/irules.sh | grep -c tcp`
      if [ "$irules" == "$nodes" ]; then
         irulesok=1
      fi
      sleep 5
   done
  
   iter=0
   while [ $iter -lt 10 ]; 
   do 
       $ssh_masternode ntpdate -q -u 129.146.17.39
       if [ $? -eq 0 ]; then
           echo "master node is capable to receive ntp traffic: GOOD!"
       else 
           echo "master node is unable to receive ntp traffic: BAD :("               
       fi 
       iter=`expr $iter + 1` 
   done
   $ssh_masternode "echo admin | /opt/SUNWhadb/4/bin/hadbm status honeycomb" 
   $ssh_masternode "echo admin | /opt/SUNWhadb/4/bin/hadbm status honeycomb --nodes"
}

# Client 
# 1. Ability todo client i/o.
verify_api_status() {
   oid=`/opt/test/bin/store $data_vip /etc/hosts`
   /opt/test/bin/retrieve $data_vip $oid hosts.new
   diff /etc/hosts hosts.new
   if [ $? -ne 0 ]; then
      echo "store and retrieve files mismatch"
      echo "FAILED FAILED"
   else 
      echo "`hostname` can store and retrieve" 
   fi 
}

ssh_node="ssh -p 2001 -l root -o StrictHostKeyChecking=no -q $admin_vip"
masternode=`$ssh_node /opt/honeycomb/bin/nodemgr_mailbox.sh | grep -i "alive master" | awk {'print $5'}`
echo "master node: $masternode"
if [ "$masternode" == "" ]; then
   echo "no master in the cluster"
   exit 1
fi

ssh_masternode="$ssh_node ssh -l root -o StrictHostKeyChecking=no -q $masternode"
echo "====================="
echo "switch1 configuration"
echo "====================="
verify_pswitch_status
echo "====================="
echo "switch2 configuration"
echo "====================="
verify_sswitch_status
echo "====================="
echo "cluster configuration"
echo "====================="
verify_masternode_status
echo "====================="
echo "client configuration"
echo "====================="
verify_api_status

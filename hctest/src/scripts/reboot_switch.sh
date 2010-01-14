#!/bin/bash
#
# $Id: reboot_switch.sh 10858 2007-05-19 03:03:41Z bberndt $
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

if [ $# -lt 1 ]; then
   echo "usage: $0 <admin vip>"
   exit 1 
fi
SSHARGS="-p 2222 -l nopasswd -o StrictHostKeyChecking=no -q"
SCPARGS="-q -P 2222 -o StrictHostKeyChecking=no"
SCPUSER="nopasswd"
SCPSWITCH="${SCPUSER}@${1}"

sshswitch="ssh -p 2222 -l nopasswd -o StrictHostKeyChecking=no -q $1"
scp $SCPARGS $SCPSWITCH:/etc/honeycomb/switch.conf /tmp/switch.conf
. /tmp/switch.conf
pstate=""
sstate="" 
ip1=""
ip2="" 
count=1
switch_this=$SWITCH_THIS_INTERCONNECT
switch_other=$SWITCH_OTHER_INTERCONNECT
sshswitch_this="eval ssh $SSHARGS $1 ssh $SSHARGS $switch_this"
rm -f /tmp/switch.conf

while true
do
   pstate=`$sshswitch_this "vrrpconfig -a | awk {'print $2'} | tail -1"`
   sstate=`$sshswitch run_cmd_other.sh "vrrpconfig -a | \
             awk {'print $2'} | tail -1"`
   date1=`$sshswitch_this date` 
   date2=`$sshswitch  run_cmd_other.sh date` 
   ip1=`$sshswitch_this "ifconfig -a | grep inet | \
          grep 10.123.0 |awk {'print $2'} | awk -F: {'print $2'}"`
   ip2=`$sshswitch run_cmd_other.sh "ifconfig -a | grep inet | \
          grep 10.123.0 |awk {'print $2'} | awk -F: {'print $2'}"`
   echo "$ip1=$pstate [$date1] $ip2=$sstate [$date2]" 
   echo "**** Count = $count ****" 
   if [ "$pstate" == "M" ] && [ "$sstate" == "M" ]; then
      echo "Stopping the test as we have split brain"
      exit 1
   else
      $sshswitch /usr/sbin/swadm -r -o 
   fi
   pstate=""
   sstate="" 
   ip1=""
   ip2="" 
   count=`expr $count + 1`
   sleep 300 
done

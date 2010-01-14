#!/bin/bash
#
# $Id: alerts.sh 10858 2007-05-19 03:03:41Z bberndt $
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

WHEREAMI=`cd \`dirname $0\`; pwd`
POSTRESULT=$WHEREAMI/../../../suitcase/src/script/postResult.sh

post () {
  echo $POSTRESULT -r skipped $*
  $POSTRESULT -r skipped $*
}

post config-alert_cc alerts
post config-alert_from alerts
post config-alert_to alerts
post config-net_smtp_server alerts
post config-net_smtp_port alerts

post once-a-day-alert alerts

# realtime node alerts
post -p "event=root.star.Layout.diskmap.currentMap" realtime-alert-node alerts
post -p "event=root.star.PlatformService.switchType" realtime-alert-node alerts
post -p "event=root.masterNodeId.SwitchSpreader.dataVip" realtime-alert-node alerts
post -p "event=root.masterNodeId.SwitchSpreader.adminVip" realtime-alert-node alerts
post -p "event=root.masterNodeId.SwitchSpreader.ipAddress" realtime-alert-node alerts
post -p "event=root.masterNodeId.SwitchSpreader.activeSwitch" realtime-alert-node alerts

# realtime disk alerts
post -p "root.star.DiskMonitor.diskX.device" realtime-alert-disk alerts
post -p "root.star.DiskMonitor.diskX.diskSize" realtime-alert-disk alerts
post -p "root.star.DiskMonitor.diskX.percentUsed" realtime-alert-disk alerts
post -p "root.star.DiskMonitor.diskX.status" realtime-alert-disk alerts
post -p "root.star.DiskMonitor.diskX.mode" realtime-alert-disk alerts
post -p "root.star.DiskMonitor.diskX.diskId" realtime-alert-disk alerts
post -p "root.star.DiskMonitor.diskX.path" realtime-alert-disk alerts

# realtime cli alerts
post -p "root.masterNodeId.AdminServer.cliAlerts" realtime-alert-cli alerts

# realtime cm alerts
post realtime-alert-cm alerts

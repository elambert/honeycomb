#!/bin/bash
#
# $Id: real-world-disk-faults.sh 10858 2007-05-19 03:03:41Z bberndt $
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
    local result=$1
    shift
    echo $POSTRESULT -r $result $*
    $POSTRESULT -r $result $*
}

post pass -p "error=substitute-every-10th-OPEN_ACCEPT-with-OPEN_REJECT" infusion-nohc real-world-disk-fault
post pass -p "error=inject-CRC-error-in-every-500th-SSP-command-or-identity-address-frame" infusion-nohc real-world-disk-fault
post pass -p "error=replace-and-monitor-primitive-and-stop-after-10-seconds" infusion-nohc real-world-disk-fault
post pass -p "error=inject-RD-error-and-monitor-every-other-OPEN_REJECT" infusion-nohc real-world-disk-fault
post pass -p "error=drop-and-monitor-frame-when-data-pattern-matches" infusion-nohc real-world-disk-fault
post unknown -p "error=reorder-align-primitives" infusion-nohc real-world-disk-fault
post pass -p "opts=-w" lockfs-nohc real-world-disk-fault
post pass -p "opts=-e" lockfs-nohc real-world-disk-fault
post skipped drivepull-nohc real-world-disk-fault
post skipped drivepull-honeycomb real-world-disk-fault
post pass cablepull-nohc real-world-disk-fault
post skipped cablepull-honeycomb real-world-disk-fault

#!/bin/sh

#
# $Id: restart_pmap.sh 10855 2007-05-19 02:54:08Z bberndt $
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

# Save state from running portmapper
rm /tmp/.saved_portmap_state
/usr/sbin/pmap_dump > /tmp/.saved_portmap_state

# Kill standard portmapper
/usr/bin/pkill -TERM -f portmap

# Run portmapper bound to localhost and internal ip address
#
INTERNAL_IP=`/opt/honeycomb/bin/get_internal_ip.sh`
if [ "$INTERNAL_IP" ]; then
echo bound portmapper 1 to $INTERNAL_IP
/opt/honeycomb/bin/portmap -h 127.0.0.1 -h $INTERNAL_IP
fi

# Reset mappings in portmapper
echo `/usr/sbin/pmap_set < /tmp/.saved_portmap_state`

# Run portmapper bound to external ip address
# Important - run this after the previous step i.e. resetting mappings
# in portmapper bound to localhost and internal ip address
#
EXTERNAL_IP=`/opt/honeycomb/bin/get_external_ip.sh`
if [ "$EXTERNAL_IP" ]; then
echo bound portmapper 2 to $EXTERNAL_IP
/opt/honeycomb/bin/portmap -h $EXTERNAL_IP
fi

exit 0

#!/usr/bin/bash
#
# $Id$
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

usage() {
    echo "$0 -z <zone name> 
             -d <zone dir> 
             -f <file system> 
             -i <network interface> 
             -n <ip address> 
             -m <mount point>
             -p <resource pool>"
    echo "Examples:"
    echo "$0 -z test-zone1 -d /export/home/zones -f /testfs1 
             -i nge0 -n 10.7.227.94/22 -m /mnt/testfs1
             -p testpool" 
    exit 1 
}

while getopts "z:d:f:i:n:m:h" o; do
    case "$o" in
        z) ZONE=$OPTARG ;;
        d) ZONEDIR=$OPTARG ;;
        f) FS=$OPTARG ;;
        i) INTERFACE=$OPTARG ;;
        n) IP=$OPTARG ;;
        m) MNTPT=$OPTARG ;;
        p) POOL=$OPTARG ;;
        h) usage ;; 
        *) usage ;; 
    esac
done

# Main
if [ ! -e "$ZONEDIR/$ZONE" ]; then 
    mkdir -p $ZONEDIR/$ZONE
    chmod 700 $ZONEDIR/$ZONE
fi

if [ ! -e "$MNTPT" ]; then 
    mkdir -p $MNTPT 
fi

cat << EOF | zonecfg -z $ZONE 
create
set autoboot=true
set zonepath=$ZONEDIR/$ZONE  

add net
set physical=$INTERFACE 
set address=$IP 
end

add fs
set dir=$FS 
set special=$MNTPT 
set type=lofs
end

add rctl
set name=zone.cpu-shares
add value (priv=privileged,limit=1,action=none)
end

set pool=$POOL  

verify
commit
exit
EOF

# install and boot the zone
zoneadm -z $ZONE install
zoneadm -z $ZONE boot 

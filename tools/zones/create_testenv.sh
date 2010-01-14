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

PKGS="SUNWluzone SUNWzoner SUNWzoneu SUNWpool SUNWpoold SUNWpoolr"
 
usage() {
    echo 
    echo USAGE
    echo     "$0 -p <test pool> -d <data device>"
    echo
    echo DESCRIPTION
    echo     Creates 2 test zones 'test-zone1 and test-zone2' with 2 file 
    echo     systems /testfs1 and /testfs2. Each of the test zone is configured
    echo     on nge0 and nge1 with IP addresses 10.7.227.94 and 10.7.227.95 
    echo     respectively.
    echo 
    echo EXAMPLES
    echo     "$0 -p qpool -d c1t1d0"
    echo 
    exit 1 
}

create_pool() {
    # Initialize pool configuration 
    pooladm -e
    pooladm -x
    pooladm -s

    # Create test pool 
    poolcfg -c 'create pool $POOL (string pool.scheduler="FSS")'
    pooladm -c
}

create_zones() {
    # Create a storage pool from the specified data slices 
    zpool create testpool $DEVICE 

    # Create 2 ZFS filesystems from the zpool
    zfs create -o /mnt/testfs1 testpool/testfs1
    zfs create -o /mnt/testfs2 testpool/testfs2

    # Set attributes on 2 ZFS filesystems  
    zfs set sharenfs=rw testpool/testfs1
    zfs compression=on testpool/testfs1
    zfs set quota=30g testpool/testfs1 
    zfs set sharenfs=rw testpool/testfs2
    zfs compression=on testpool/testfs2
    zfs set quota=30g testpool/testfs2

    # Configure Fair Share Scheduler
    dispadmin -d FSS
    priocntl -s -c FSS -i pid 1
    priocntl -s -c FSS -i all 

    # Configure global zone 
cat << EOF | zonecfg -z global
set pool=pool_default

add rctl
set name=zone.cpu-shares
add value (priv=privileged,limit=20,action=none)
end

verify
commit
exit
EOF
 
    # Create 2 solaris test zones
    create_zone.sh -z test-zone1 -d /export/home/zones -f /testfs1 -i nge0 -n 10.7.227.94/22 -m /mnt/testfs1 
    create_zone.sh -z test-zone2 -d /export/home/zones -f /testfs2 -i nge1 -n 10.7.227.95/22 -m /mnt/testfs2

    # Create NIS, DNS configuration - @@@ How to do this via a script? 
    # This configruation is set; when root logins to a zone for the first time using the following commands
    # 'zlogin -c test-zone1'
    # 'zlogin -c test-zone2'  
}

check_pkgs() {
    pkginfo $PKGS 
    if [ $? -ne 0 ]; then 
        echo "Either one or more $PKGS not installed"
        exit 1 
    fi
}

check_and_enable_services() {
    svcs svc:/system/zones:default | grep disabled
    ZONE_RC=$? 
    if [ $ZONE_RC -eq 0 ]; then
         echo "svc:/system/zones:default is not enabled"
         echo "Enabing service svc:/system/zones:default"
         svcadm enable svc:/system/zones:default 
         if [ $? -ne 0 ]; then  
             echo "svcadm enable svc:/system/zones:default FAILED"
             exit 1
         fi 
    fi
   
    svcs svc:/system/pools:default | grep disabled 
    POOL_RC=$?
    if [ $POOL_RC -eq 0 ]; then
         echo "svc:/system/pools:default is not enabled"
         echo "Enabing service svc:/system/pools:default" 
         svcadm enable svc:/system/pools:default
         if [ $? -ne 0 ]; then
             echo "svcadm enable svc:/system/pools:default FAILED"
             exit 1
         fi 
    fi 
}
 
# Main
while getopts "d:p:h" o; do
    case "$o" in
        d) DEVICE=$OPTARG ;; 
        p) POOL=$OPTARG ;;
        h) usage ;; 
        *) usage ;; 
    esac
done

check_pkgs
check_and_enable_services
create_pool
create_zones

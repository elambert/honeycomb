#! /bin/bash
#
# $Id: metadatalinksdelete.sh 10858 2007-05-19 03:03:41Z bberndt $
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

#!/bin/sh

VIP=PUT_YOUR_VIP_HERE
FILE=/etc/syslog.conf

set -x

while :
do
echo "-------------------------"
oid=`store $VIP $FILE -m "filename"="fromtest"`
oid2=`storemetadata $VIP $oid` 
oid3=`storemetadata $VIP $oid ` 
oid4=`storemetadata $VIP $oid ` 
delete $VIP $oid
oid5=`storemetadata $VIP $oid2` 
delete $VIP $oid2
oid6=`storemetadata $VIP $oid3` 
oid7=`storemetadata $VIP $oid3` 
oid8=`storemetadata $VIP $oid3` 
delete $VIP $oid3
delete $VIP $oid4
delete $VIP $oid5
oid9=`storemetadata $VIP $oid6` 
delete $VIP $oid6
oid10=`storemetadata $VIP $oid7` 
delete $VIP $oid7
delete $VIP $oid8
#retrieve_std_out $VIP $oid9 |head -2
delete $VIP $oid9
retrieve_std_out $VIP $oid10 |head -2
#delete $VIP $oid10
#retrieve_std_out $VIP $oid10 |head -2
done


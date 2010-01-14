#! /bin/sh
#
# $Id: list-system-cache-entries.sh 10858 2007-05-19 03:03:41Z bberndt $
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
# Run from the cheat.  Verbose option not currently used 
#
# usage: list-system-cache-entries.sh datavip [-v]
#

# example:
# get all md objs:
# ./list-system-cache-entries.sh dev331-data|grep -v Empty| grep 200000000

# As an aside, the redundany is figure out in MDDiskAbstraction
# in emd/common
#  if (cacheId.equals(CacheInterface.SYSTEM_CACHE)) {
#     nbCaches = OAClient.getInstance().getReliability().getRedundantFragCount() + 1;
#  } else {
#      nbCaches = redundancy;
#  }

MAXMAPID=10000
mapid=0

if [ -z "${1}" ]; then
    echo "usage $0 datavip [-v]"
    exit 1
else
    datavip=$1
fi

if [ "${2}" = "-v" ]; then
    verbose=true
else
    verbose=false
fi

while [ $mapid -lt $MAXMAPID ]; do
    objs=`curl -s "http://$datavip:8080/query?metadata-type=system&where-clause=getObjects $mapid"`
    echo "=> $mapid $objs"
    mapid=`expr $mapid + 1`
done

#!/bin/bash
#
# $Id: check_sys_cache.sh 10857 2007-05-19 03:01:32Z bberndt $
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
 
JAVA=`which java`
CP="/opt/honeycomb/lib/honeycomb-wbcluster.jar:/opt/honeycomb/lib/honeycomb-server.jar:/opt/honeycomb/lib/honeycomb-common.jar:/opt/honeycomb/lib/md_caches/bdb_common.jar:/opt/honeycomb/lib/md_caches/bdb_system.jar:/opt/honeycomb/lib/db-4.2.jar:/opt/honeycomb/lib/jug.jar:/opt/honeycomb/lib/jetty-4.2.20.jar"

echo "Disk 0:"
${JAVA} -Djava.library.path=/opt/honeycomb/lib/md_caches -cp ${CP} CheckFrags /data/0 $1 2> /dev/null
echo "Disk 1:"
${JAVA} -Djava.library.path=/opt/honeycomb/lib/md_caches -cp ${CP} CheckFrags /data/1 $1 2> /dev/null
echo "Disk 2:"
${JAVA} -Djava.library.path=/opt/honeycomb/lib/md_caches -cp ${CP} CheckFrags /data/2 $1 2> /dev/null
echo "Disk 3:"
${JAVA} -Djava.library.path=/opt/honeycomb/lib/md_caches -cp ${CP} CheckFrags /data/3 $1 2> /dev/null

exit 0

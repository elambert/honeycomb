#!/bin/sh
#
# $Id: wipe_hadb.sh 7975 2006-04-20 06:40:57Z sarahg $
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

DIR=`cd \`dirname $0\`/..; pwd`

if [ ".$1" = ".-f" ]; then
    FORCE=y
fi

echo "All hadb will be erased, and procs killed!"
echo "You have 5 seconds to hit ^C"
sleep 5
echo

echo "Killing hadb procs..."
pkill -9 -f SUNWhadb;pkill -9 -f SUNWjdmk
sleep 3
echo "There should be no remaining HADB procs..."
ps -ef | egrep '[S]UNWhadb|SUNWjdmk'
ls /usr/bin/ipcrm >/dev/null 2>&1
if [ $? -ne 0 ]; then
  echo "Command not found: /usr/bin/ipcrm. You must reboot after wipe!!!"
else 
  #One set of shared memory for the first copy of the database
  echo "Removing HADB shared memory segments..."
  for i in 1 2 3 4 5; do /usr/bin/ipcrm -M 1500$i 2>/dev/null; done
  #Another set of shared memory for the second copy of the database 
  for i in 1 2 3 4 5; do /usr/bin/ipcrm -M 1502$i 2>/dev/null; done
fi
sleep 2
echo "Erasing emulator HADB directory..."
rm -rf "$DIR/var/hadb"

exit 0

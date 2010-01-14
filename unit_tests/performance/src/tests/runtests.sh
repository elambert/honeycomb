#! /usr/bin/sh
#
# $Id: runtests.sh 10854 2007-05-19 02:52:12Z bberndt $
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
   echo "$0 <Path to unit test scripts>"
   exit 1 
fi

RWPATH=$1
PROFILE_SCRIPTNAME="/opt/honeycomb/etc/profiler/dtrace/profileProcess.sh"
 
verify_localdisk_readwrite() {
   # verify that read/write binary are in place
   if [ ! -f "$RWPATH/read" ]; then
      echo "read binary does not exist"
      exit 1   
   fi
   if [ ! -f "$RWPATH/write" ]; then
      echo "write binary does not exist"
      exit 1   
   fi

   echo "Test will take about a minute to run"
   # Verify Local Disk write
   $PROFILE_SCRIPTNAME -p write 1 > /tmp/localDiskWrite &
   sleep 5 
   $RWPATH/write /tmp/writeFile 1024 4096
   writeProfilePid=$! 
   sleep 5 
   grep "write" /tmp/localDiskWrite | awk {'print $5'} | grep 4
   if [ $? -eq 0 ]; then
      echo "Dtrace reports 4K Local Disk Write ... PASS"
   else   
      echo "Dtrace does not report 4K Local Disk Write ... FAIL"
   fi 
   kill -9 $writeProfilePid 2>&1 1>>/dev/null
 
   # Verify Local Disk read 
   $PROFILE_SCRIPTNAME -p read 1  > /tmp/localDiskRead &
   sleep 5 
   $RWPATH/read /tmp/writeFile 1024 
   readProfilePid=$!
   sleep 5 
   grep "read" /tmp/localDiskRead | awk {'print $3'} | grep 4
   if [ $? -eq 0 ]; then
      echo "Dtrace reports 4K Local Disk Read ... PASS"
   else   
      echo "Dtrace does not report 4K Local Disk Read ... FAIL"
   fi 
  
   # cleanup 
   kill -9 $readProfilePid 2>&1 1>>/dev/null 
}

verify_paging() {
   if [ ! -f "$RWPATH/paging" ]; then
      echo "paging binary does not exist"
      exit 1   
   fi 

   echo "Test will take about a minute to run"
   # Verify Paging 
   $PROFILE_SCRIPTNAME -p paging 1 > /tmp/paging &
   sleep 5 
   $RWPATH/paging 100 
   pagingProfilePid=$! 
   sleep 5 
   grep "paging" /tmp/paging | awk {'print $6'} | grep -v 0 
   if [ $? -eq 0 ]; then
      echo "Dtrace reports pagin numbers ... PASS"
   else   
      echo "Dtrace does not report pagin numbers ... FAIL"
   fi 
   kill -9 $pagingProfilePid 2>&1 1>>/dev/null
}

verify_localdisk_readwrite
verify_paging 

#!/bin/ksh
#
# $Id: run_usage.sh 10858 2007-05-19 03:03:41Z bberndt $
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
# Shell script to run all the usage tests
#
if [ "$1" = "" ]; then
  echo "Usage: run_usage.sh <hostname>"
  exit 1
fi
HOST=$1
export PORT=8080
TEST_FAIL=0
#
# Determine machine type
# Note: only Linux and Solaris covered so far - no windows
#
if (uname -s | grep -i sunos > /dev/null) then
   if (uname -p | grep -i sparc > /dev/null) then
      OS_TYPE=sol_sparc
   else
      OS_TYPE=sol_x86
   fi
elif (uname -s | grep -i linux > /dev/null) then
      OS_TYPE=Linux
else
   echo "Don't know this machine type"; uname -p; uname -s
   exit 1
fi
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:../../../build/client_c/dist/$OS_TYPE/lib
cd $OS_TYPE
./test_usage_sessions $HOST $PORT
if (test $? -ne 0) then
  let TEST_FAIL=$TEST_FAIL+1
fi
./test_usage_query $HOST $PORT
if (test $? -ne 0) then
  let TEST_FAIL=$TEST_FAIL+1
fi
./test_usage_large $HOST $PORT
if (test $? -ne 0) then
  let TEST_FAIL=$TEST_FAIL+1
fi
./test_range_retrieve $HOST $PORT
if (test $? -ne 0) then
  let TEST_FAIL=$TEST_FAIL+1
fi
if (test $TEST_FAIL -ne 0) then
  echo "Usage tests failed"
  exit 1
else
  echo "Usage tests passed"
  exit 0
fi

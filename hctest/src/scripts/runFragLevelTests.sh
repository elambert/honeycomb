#!/bin/bash
#
# $Id: runFragLevelTests.sh 10858 2007-05-19 03:03:41Z bberndt $
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

LOGDIR=/mnt/test/fragTests
mkdir -p $LOGDIR

# cluster
CLUSTER=dev321
NODES=16

FILESIZES="32768 65535 1048576000 1572864000 2097152000 1048576001 3145728000 104857600000 1 500 1000 65536  1048248320 1048575999"

#
# "nice to have" filesizes.
#

#65536 65537 98304 131071 131072 131073 163840 196607 196608 196609 327679 327680 327681 491520 655359 655360 655361 819200 983039 983040 983041 524288000 1048903680 2096824320 2097151999 2097152001 2097479680 2621440000 3145400320 3145727999 3145728001 3146055680  104857272320 

#OPERATIONS="corruptfragment"

#OPERATIONS="deletesingle deletedouble"
OPERATIONS="deletesingle deletedouble corruptfragment"
FRAGMENTTESTS="/opt/test/bin/runtest com.sun.honeycomb.hctest.cases.FragmentLevelTests -ctx cluster=${CLUSTER}:nodes=${NODES}:recoverycommutes:fullheal=true"
#COMMUTES=recoverycommutes

#recoverycommutes:corruptfragment:fullheal=true:startingfilesize=67000 > results 2>&1 &
# FragmentLevelTests -ctx cluster=dev327:nodes=16:deletesingle:fullheal=true:startingfilesize=1024



runTest () {
  SIZE=$1 # Filesize (data object size)
  OPERATION=$2

  
  LOG=${LOGDIR}/${CLUSTER}.${OPERATION}.${SIZE}
  RUNTEST="${FRAGMENTTESTS}:$OPERATION:startingfilesize=${SIZE}:endingfilesize=${SIZE}"
  echo creating log: $LOG
  echo
  echo running: $RUNTEST
  $RUNTEST >$LOG 2>&1 </dev/null

  echo "Done."

}






for SIZE in $FILESIZES; do
    for OPERATION in $OPERATIONS; do
        runTest  $SIZE $OPERATION
    done    
done




echo
echo [`date`] ALL DONE

#! /bin/sh
#
# $Id: simultaneous-metadata-ops.sh 11054 2007-06-19 21:22:43Z jk142663 $
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
# repro for 6507453
#
# launch from a test client
#

# TODO: tweak these to suit your cluster/desired number of objects
CLUSTER=dev316
NUMOBJS=20
DATAVIP=$CLUSTER-data

# can optionally pass an oid to the test 
# this is useful for testing against a multi-chunk obj
# ideally, this object should have only 1 reference
oid=$1  

if [ -n "$oid" ]; then
    echo "Using passed in OID $oid for test"
else
    echo "calling store on $DATAVIP to get our base object"
    oid=`store $DATAVIP /etc/hosts`
    echo "oid returned from store is $oid"
fi

OUT1=/mnt/test/addmd-1-$oid.out
OUT2=/mnt/test/addmd-2-$oid.out

echo "launching multiple tests to add/delete $NUMOBJS MD objects using base oid $oid"
echo "full logs are at path below on this host"
echo " $OUT1"
echo " $OUT2"

/opt/test/bin/runtest com.sun.honeycomb.hctest.cases.MetadataComplexChains \
-ctx cluster=$CLUSTER:objects=$NUMOBJS:verbose=true:oid=$oid:noretrieve=true > $OUT1 2>&1 &

/opt/test/bin/runtest com.sun.honeycomb.hctest.cases.MetadataComplexChains \
-ctx cluster=$CLUSTER:objects=$NUMOBJS:verbose=true:oid=$oid:noretrieve=true > $OUT2 2>&1 &

echo "waiting for test to complete; see logs for progress"
wait
echo "test is complete, results were: "
echo
echo "run 1"
egrep 'END RUN_ID|FAIL RES' $OUT1
echo
echo "run 2"
egrep 'END RUN_ID|FAIL RES' $OUT2 

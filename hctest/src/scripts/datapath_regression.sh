#! /bin/bash
#
# $Id: datapath_regression.sh 11393 2007-08-22 19:01:19Z wr152514 $
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

# run automatable 'datapath' tests for regression

if [ $# != 1 ]; then
    echo "Usage: $0 <cluster>"
    exit 1
fi
CLUSTER=$1

result=0
test_result() {
    if [ $1 != 0 ] ; then
        result=1
    fi
}

#########################
 
echo ==================================================-
echo "==== regression_reg.sh"
echo ==================================================-
/opt/test/bin/regression_reg.sh $CLUSTER
test_result $? Regression

echo ==================================================-
echo "==== rangertv_reg.sh"
echo ==================================================-
/opt/test/bin/rangertv_reg.sh  $CLUSTER-data
test_result $? Range_retrieve

echo ==================================================-
echo "==== interfaces_reg.sh"
echo ==================================================-
/opt/test/bin/interfaces_reg.sh $CLUSTER 
test_result $? Interfaces

echo ==================================================-
echo "==== negative_reg.sh"
echo ==================================================-
/opt/test/bin/negative_reg.sh $CLUSTER
test_result $? Negative

echo ==================================================-
echo "==== delete_reg.sh"
echo ==================================================-
/opt/test/bin/delete_reg.sh $CLUSTER
test_result $? Delete

if [ $result != 0 ] ; then
    echo Datapath: FAIL
else
    echo Datapath: PASS
fi
exit $result

#! /bin/bash
#
# $Id: negative_reg.sh 11393 2007-08-22 19:01:19Z wr152514 $
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

# test 'negative cases' for regression
# 16-node runtime: 2 min

if [ $# != 1 ]; then
    echo "Usage: $0 <cluster>"
    exit 1
fi
CLUSTER=$1

test_result() {
    if [ $1 != 0 ] ; then
        echo Negative test: FAIL
        exit 1
    fi
}

#########################

/opt/test/bin/runtest -x cluster=$CLUSTER \
    com.sun.honeycomb.hctest.cases.MetadataQueryInvalid
test_result $?
echo "MetadataQueryInvalid test: PASS"

/opt/test/bin/runtest -x cluster=$CLUSTER \
    com.sun.honeycomb.hctest.cases.MetadataStoreInvalid
test_result $?
echo "MetadataStoreInvalid test: PASS"

echo Negative tests: PASS

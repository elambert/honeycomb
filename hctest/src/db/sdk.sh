#!/bin/bash
#
# $Id: sdk.sh 10858 2007-05-19 03:03:41Z bberndt $
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

WHEREAMI=`cd \`dirname $0\`; pwd`
POSTRESULT=$WHEREAMI/../../../suitcase/src/script/postResult.sh

post () {
    local result=$1
    shift
    echo $POSTRESULT -r $result $*
    $POSTRESULT -r $result $*
}

post pass -p "test_no_metadata(Java/UNIX)" SDK_Examples sdk-tests
post pass -p "test_no_metadata(Java/Windows)" SDK_Examples sdk-tests
post pass -p "test_no_metadata(C/UNIX)" SDK_Examples sdk-tests
post pass -p "test_no_metadata(C/Windows)" SDK_Examples sdk-tests
post pass -p "test_metadata(Java/UNIX)" SDK_Examples sdk-tests
post pass -p "test_metadata(Java/Windows)" SDK_Examples sdk-tests
post pass -p "test_metadata(C/UNIX)" SDK_Examples sdk-tests
post pass -p "test_metadata(C/Windows)" SDK_Examples sdk-tests
post pass -p "test_schema(Java/UNIX)" SDK_Examples sdk-tests
post pass -p "test_schema(Java/Windows)" SDK_Examples sdk-tests
post pass -p "test_schema(C/UNIX)" SDK_Examples sdk-tests
post pass -p "test_schema(C/Windows)" SDK_Examples sdk-tests
post pass -p "test_bad_command_line(Java/UNIX)" SDK_Examples sdk-tests
post pass -p "test_bad_command_line(Java/Windows)" SDK_Examples sdk-tests
post pass -p "test_bad_command_line(C/UNIX)" SDK_Examples sdk-tests
post pass -p "test_bad_command_line(C/Windows)" SDK_Examples sdk-tests
post pass -p "test_bad_metadata(Java/UNIX)" SDK_Examples sdk-tests
post pass -p "test_bad_metadata(Java/Windows)" SDK_Examples sdk-tests
post pass -p "test_bad_metadata(C/UNIX)" SDK_Examples sdk-tests
post pass -p "test_bad_metadata(C/Windows)" SDK_Examples sdk-tests
post pass -p "test_get_unique_values(Java/UNIX)" SDK_Examples sdk-tests
post pass -p "test_get_unique_values(Java/Windows)" SDK_Examples sdk-tests

post pass -p "testhcclient" C_API_Smoketest sdk-tests

post skipped -p "C_Examples" BuildSDK sdk-tests
post pass -p "FileCheck" BuildSDK sdk-tests
post skipped -p "JavaExamples" BuildSDK sdk-tests

post pass -p "Smoketest" Emulator sdk-tests

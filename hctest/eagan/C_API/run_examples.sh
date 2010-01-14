#!/bin/ksh
#
# $Id: run_examples.sh 10858 2007-05-19 03:03:41Z bberndt $
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
# Execute C API examples for a simple usage test.
#
if [ "$1" = "" ]; then
  echo "Usage: run_examples.sh <hostname>"
  exit 1
fi
HOST=$1
C_DIST_PATH=../../../build/sdk/dist/c/examples
SCRATCH_DIR=/tmp
FILE_TO_STORE=$SCRATCH_DIR/myfile
TEST_FAIL=0
NUM_RESULTS=100000  # In case on system with many records
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
cd $C_DIST_PATH/$OS_TYPE/build
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:.
#
# Make sure QA schema is loaded. If test_id is there then
# probably correct
#
./RetrieveSchema $HOST | grep -i test_id > /dev/null
if (test $? -ne 0) then
	echo "No test_id in schema bailing out"
	exit 1
fi
echo "This is file that will be used in Storefile" > $SCRATCH_DIR/myfile
OID1=`./StoreFile $HOST $SCRATCH_DIR/myfile | grep 000 | cut -d$ -f1`
OID2=`./AddMetadata -m test_id=addmetadata $HOST $OID1`
./RetrieveMetadata $HOST $OID2 | grep test_id | grep addmetadata > /dev/null
if (test $? -ne 0) then 
	echo "RetrieveMetadata did not find record as expected"
	let TEST_FAIL=$TEST_FAIL+1
fi
OID3=`./AddMetadata -m test_id=addmetadata1 $HOST $OID2`
./RetrieveMetadata $HOST $OID3 | grep test_id | grep addmetadata1 > /dev/null
if (test $? -ne 0) then 
	echo "RetrieveMetadata did not find record as expected"
	let TEST_FAIL=$TEST_FAIL+1
fi
echo "Test valid boolean operators on Queries"
./Query -s test_id -r $NUM_RESULTS $HOST "test_id='addmetadata'" | grep $OID2
if (test $? -ne 0) then
	echo "Query had a problem"
	let TEST_FAIL=$TEST_FAIL+1
fi
./Query -s test_id -r $NUM_RESULTS $HOST "test_id!='addmetadata' AND test_id<='addmetadata1'" | grep addmetadata1 > /dev/null
if (test $? -ne 0) then 
	echo "Query test_id!='addmetadata' FAILED"
	let TEST_FAIL=$TEST_FAIL+1
fi
./Query -s test_id -r $NUM_RESULTS $HOST "test_id<>'addmetadata' AND test_id<='addmetadata1'" | grep addmetadata1 > /dev/null
if (test $? -ne 0) then 
	echo "Query test_id<>'addmetadata' FAILED"
	let TEST_FAIL=$TEST_FAIL+1
fi
./Query -s test_id -r $NUM_RESULTS $HOST "test_id<'addmetadata'"  | grep addmetadata1 # should not find
if (test $? -eq 0) then 
	echo "Query test_id<'addmetadata' FAILED"
	let TEST_FAIL=$TEST_FAIL+1
fi
./Query -s test_id -r $NUM_RESULTS $HOST "test_id>'addmetadata' AND test_id<='addmetadata1'"  | grep addmetadata1 > /dev/null
if (test $? -ne 0) then 
	echo "Query test_id>'addmetadata' FAILED"
	let TEST_FAIL=$TEST_FAIL+1
fi
./Query -s test_id -r $NUM_RESULTS $HOST "test_id<='addmetadata'" | grep addmetadata1 # should not find
if (test $? -eq 0) then 
	echo "Query test_id<='addmetadata' FAILED"
	let TEST_FAIL=$TEST_FAIL+1
fi
./Query -s test_id -r $NUM_RESULTS $HOST "test_id>='addmetadata'" | grep addmetadata1 > /dev/null
if (test $? -ne 0) then 
	echo "Query test_id>='addmetadata' FAILED"
	let TEST_FAIL=$TEST_FAIL+1
fi
echo "Test invalid boolean operators"
./Query -s test_id -r $NUM_RESULTS $HOST "test_id=>'addmetadata'" 
if (test $? -eq 0) then
	echo "Query with invalid boolean operator => did not return an error"
	let TEST_FAIL=$TEST_FAIL+1
fi
echo "Test logical operators"
./Query -s test_id -r $NUM_RESULTS $HOST "test_id>='addmetadata' AND test_id<='addmetadata1'" |\
grep addmetadata > /dev/null
if (test $? -ne 0) then
        echo "Query with AND FAILED"
        let TEST_FAIL=$TEST_FAIL+1
fi
./Query -s test_id -r $NUM_RESULTS $HOST "test_id='addmetadata' OR test_id='addmetadata1'" |\
egrep "addmetadata|addmetatdata1" > /dev/null
if (test $? -ne 0) then
        echo "Query with OR FAILED"
        let TEST_FAIL=$TEST_FAIL+1
fi
./Query -s test_id -r $NUM_RESULTS $HOST "test_id LIKE '%metadata%'" |\
egrep "addmetadata|addmetatdata1"
if (test $? -ne 0) then
        echo "Query with LIKE FAILED"
        let TEST_FAIL=$TEST_FAIL+1
fi
./Query -s test_id -r $NUM_RESULTS $HOST "test_id NOT LIKE '%metadata%'" |\
egrep "addmetadata|addmetatdata1"
if (test $? -eq 0) then
        echo "Query with NOT LIKE FAILED - should not find any records"
        let TEST_FAIL=$TEST_FAIL+1
fi
echo "Test invalid logical operators"
./Query -s test_id -r $NUM_RESULTS $HOST "test_id KINDALIKE '%metadata%'"
if (test $? -eq 0) then
	echo "Query with invalid logical operator KINDALIKE did not return error"
	let TEST_FAIL=$TEST_FAIL+1
fi
#
# Try with invalid metadata 
#
./AddMetadata -m noexist=xxx $HOST $OID2 > /dev/null
if (test $? -eq 0) then
	echo "Addmetadata with invalid metadata should have had error"
	let TEST_FAIL=$TEST_FAIL+1
fi
touch $SCRATCH_DIR/myfile.return.1
./RetrieveData $HOST $OID1 $SCRATCH_DIR/myfile.return.1
touch $SCRATCH_DIR/myfile.return.2
./RetrieveData $HOST $OID2 $SCRATCH_DIR/myfile.return.2
./DeleteMetadataRecord $HOST $OID1
#
# Try to Retreive data and do query for OID that does not exist.
#
./RetrieveData $HOST $OID1 $SCRATCH_DIR/myfile.return.2 > /dev/null
if (test $? -eq 0) then
	echo "RetrieveData should have had an error with nonexistant OID"
	let TEST_FAIL=$TEST_FAIL+1
fi
./DeleteMetadataRecord -v $HOST $OID1 > /dev/null
if (test $? -eq 0) then
        echo "DeleteMetadataRecord should have had error with nonexistant OID" 
        let TEST_FAIL=$TEST_FAIL+1
fi
touch $SCRATCH_DIR/myfile.return.3
./RetrieveData $HOST $OID2 $SCRATCH_DIR/myfile.return.3
diff $SCRATCH_DIR/myfile $SCRATCH_DIR/myfile.return.3
if (test $? -ne 0) then
	echo "File from RetrieveData did not compare to original"
	let TEST_FAIL=$TEST_FAIL+1
fi
if (test $TEST_FAIL -ne 0) then
	echo "Run examples failed"
	exit 1
else
	echo "Run examples passed"
	rm $SCRATCH_DIR/myfile*       # cleanup
	exit 0
fi

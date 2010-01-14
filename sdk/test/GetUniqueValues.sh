#!/bin/sh
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

SCRIPT_PATH=./Scripts
SAMPLE_FILE=./Input/SampleFile1
QUERY_VALUE=guvtest

echo "STARTING SCRIPT: GetUniqueValues.sh"
echo ""

# Store some data to test against 
echo Calling StoreFile to store some data to test against 
OIDStore=`$SCRIPT_PATH/StoreFile -m "mp3.title=$QUERY_VALUE" localhost $SAMPLE_FILE` 
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "StoreFile failed"
        exit 1  # Set script's exit status to 1
fi

# Call GetUniqueValues with valid metadata and field
echo Calling GetUniqueValues with valid metadata and field
RESULTS=`$SCRIPT_PATH/GetUniqueValues localhost "mp3.title='$QUERY_VALUE'" mp3.title`
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "GetUniqueValues failed"
        exit 1  # Set script's exit status to 1
fi
echo GetUniqueValues returned: $RESULTS

# Verify Query returned the same OID as Store
if [ "$RESULTS" != "$QUERY_VALUE" ]
then
        # Print error and quit
        echo "GetUniqueValues did not return expected results."
        echo "Query should return the unique value stored earlier"
        exit 1
fi

# Call GetUniqueValues with valid metadata but invalid field
echo Calling GetUniqueValues with valid metadata but invalid field
$SCRIPT_PATH/GetUniqueValues localhost "namespace.invalid='error'" mp3.title > ./Output/GUV_InvalidQueryString
if [ "$?" -eq 0 ]
then
        # Print error and quit
        echo "GetUniqueValues succeeded when it should have failed"
        exit 1  # Set script's exit status to 1
fi

# Call GetUniqueValues with valid metadata but invalid field
echo Calling GetUniqueValues with valid metadata but invalid field
$SCRIPT_PATH/GetUniqueValues localhost "mp3.title='$QUERY_VALUE'" namespace.invalid > ./Output/GUV_InvalidField
if [ "$?" -eq 0 ]
then
        # Print error and quit
        echo "GetUniqueValues succeeded when it should have failed"
        exit 1  # Set script's exit status to 1
fi

# Call GetUniqueValues with invalid metadata and invalid field
echo Calling GetUniqueValues with invalid metadata and invalid field
$SCRIPT_PATH/GetUniqueValues localhost "namespace.invalid='Error'" namespace.invalid > ./Output/GUV_InvalidBoth
if [ "$?" -eq 0 ]
then
        # Print error and quit
        echo "GetUniqueValues succeeded when it should have failed"
        exit 1  # Set script's exit status to 1
fi

# Call GetUniqueValues with invalid server
echo Calling GetUniqueValues with invalid server
$SCRIPT_PATH/GetUniqueValues invalidserver "mp3.title='$QUERY_VALUE'" mp3.title > ./Output/GUV_InvalidServer
if [ "$?" -eq 0 ]
then
        # Print error and quit
        echo "GetUniqueValues succeeded when it should have failed"
        exit 1  # Set script's exit status to 1
fi

# Call GetUniqueValues without enough parameters
echo Calling GetUniqueValues without enough parameters
$SCRIPT_PATH/GetUniqueValues localhost > ./Output/GUV_NotEnoughParams
if [ "$?" -eq 0 ]
then
        # Print error and quit
        echo "GetUniqueValues should have failed but returned success"
        exit 1  # Set script's exit status to 1
fi

# Call GetUniqueValues with -h option 
echo Calling GetUniqueValues with -h option
$SCRIPT_PATH/GetUniqueValues -h > ./Output/GUV_Help
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "GetUniqueValues failed"
        exit 1  # Set script's exit status to 1
fi

# Delete the data.
echo Calling DeleteObject on OID: $OIDStore
$SCRIPT_PATH/DeleteObject localhost $OIDStore
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "Delete failed"
        exit 1  # Set script's exit status to 1
fi

# Success!!!
echo GetUniqueValues Test Passed!
exit 0

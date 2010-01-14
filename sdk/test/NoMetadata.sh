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

# Store a file to honeycomb as data
echo Calling StoreFile to store: $SAMPLE_FILE
OIDStore=`$SCRIPT_PATH/StoreFile localhost $SAMPLE_FILE`
if [ "$?" -ne 0 ]
then
	# Print error and quit
	echo "StoreFile failed"
	exit 1  # Set script's exit status to 1
fi
OIDStore=`echo $OIDStore | awk '{print $1}'`
echo StoreFile returned OID: $OIDStore

# Retrieve that data from honeycomb to a file
echo Calling RetrieveData to retrieve OID: $OIDStore
$SCRIPT_PATH/RetrieveData localhost $OIDStore ./Output/NMD_DataRetrieve
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "RetrieveData failed"
        exit 1  # Set script's exit status to 1
fi


# Make sure the file that went in is the same as the file that came out
diff $SAMPLE_FILE ./Output/NMD_DataRetrieve > ./Output/NMD_RetrieveDiff

fileContents=`cat ./Output/NMD_RetrieveDiff`
if [ "$fileContents" != "" ]
then
	# Print error and quit
 	echo "File Comparision failed"
  	exit 1
fi	

# Delete data
echo Calling DeleteObject on OID: $OIDStore
$SCRIPT_PATH/DeleteObject localhost $OIDStore
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "Delete failed"
        exit 1  # Set script's exit status to 1
fi

# Retrieve deleted data
echo Calling RetrieveData on OID: $OIDStore
$SCRIPT_PATH/RetrieveData localhost $OIDStore ./Output/NMD_RetrieveDeleted > ./Output/NMD_Output
if [ "$?" -eq 0 ]
then
        # Print error and quit
        echo "Retrieve succeeded when it should have failed"
        exit 1  # Set script's exit status to 1
fi 

# Since the file was deleted there should be no output
fileContents=`cat ./Output/NMD_RetrieveDeleted`
if [ "$fileContents" != "" ]
then
        # Print error and quit
        echo "File Comparision (2) failed"
        exit 1
fi

# Success!!!
echo NoMetadata Test Passed!
exit 0

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

# Store a file with metadata (from cmdline and file) to honeycomb as data
echo Storing $SAMPLE_FILE with metadata from a file
OIDStoref=`$SCRIPT_PATH/StoreFile -m "mp3.title=honeycombmdt" -m "mp3.type=ppp" -m "mp3.album=jjj" -m "mp3.artist=111" localhost $SAMPLE_FILE`
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "StoreFile failed"
        exit 1  # Set script's exit status to 1
fi
OIDStoref=`echo $OIDStoref | awk '{print $1}'`
echo StoreFile return OID: $OIDStoref

# Retrieve that data from honeycomb to a file
echo Retrieving data for OID: $OIDStoref
$SCRIPT_PATH/RetrieveData localhost $OIDStoref ./Output/MD_DataRetrieve > ./Output/MD_MetaDataRetrieve
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "RetrieveData failed"
        exit 1  # Set script's exit status to 1
fi

# Make sure the file that went in is the same as the file that came out
diff $SAMPLE_FILE ./Output/MD_DataRetrieve > ./Output/MD_RetrieveDiff

fileContents=`cat ./Output/MD_RetrieveDiff`
if [ "$fileContents" != "" ]
then
        # Print error and quit
        echo "File comparision failed.  Data that went in not the same as data coming out."
        echo "SampleFile1 and MD_DataRetrieve should be the same" 
        exit 1
fi

# Retrieve Metadata
echo Retrieving metadata for OID: $OIDStoref
$SCRIPT_PATH/RetrieveMetadata localhost $OIDStoref > ./Output/MD_MetaDataRetrieve
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "RetrieveMetadata failed"
        exit 1  # Set script's exit status to 1
fi

# Verify that the metadata returned is what we expected
head -n 4 ./Output/MD_MetaDataRetrieve > ./Output/MD_MetaDataRetrieveNoOID
diff ./Output/MD_MetaDataRetrieveNoOID ./Expected_Results/MD_MultipleMetadata > ./Output/MD_MetaDiff

fileContents=`cat ./Output/MD_MetaDiff`
if [ "$fileContents" != "" ]
then
        # Print error and quit
        echo "Metadata comparision failed.  The metadata returned was not what was expected."
        echo "MD_MultipleMetadata and MD_MetaDataRetrieve should be the same"
        exit 1
fi

# Delete the data.
echo Calling DeleteObject on OID: $OIDStoref
$SCRIPT_PATH/DeleteObject localhost $OIDStoref
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "Delete failed"
        exit 1  # Set script's exit status to 1
fi

# Store file and metadata
echo Storing $SAMPLE_FILE
OIDStore=`$SCRIPT_PATH/StoreFile -m "mp3.title=honeycombmdt" localhost $SAMPLE_FILE`
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "StoreFile failed"
        exit 1  # Set script's exit status to 1
fi
OIDStore=`echo $OIDStore | awk '{print $1}'`
echo StoreFile return OID: $OIDStore

# Retrieve just the metadata
echo Retrieving metadata for OID: $OIDStore
$SCRIPT_PATH/RetrieveMetadata localhost $OIDStore > ./Output/MD_MetaDataOnlyRetrieve
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "RetrieveMetadata (2) failed"
        exit 1  # Set script's exit status to 1
fi

# Verify that the metadata returned is what we expected
head -n 1 ./Output/MD_MetaDataOnlyRetrieve > ./Output/MD_MetaDataOnlyRetrieveNoOID
diff ./Output/MD_MetaDataOnlyRetrieveNoOID ./Expected_Results/MD_Metadata > ./Output/MD_MetaDiff2

fileContents=`cat ./Output/MD_MetaDiff2`
if [ "$fileContents" != "" ]
then
        # Print error and quit
        echo "Metadata comparision (2) failed.  The metadata returned was not what was expected."
        echo "MD_Metadata and MD_MetaDataOnlyRetrieveNoOID should be the same"
        exit 1
fi

# Query for the data.  Verify that the correct OID is returned.
echo Querying "mp3.title='honeycombmdt'"
OIDQuery=`$SCRIPT_PATH/Query localhost "mp3.title='honeycombmdt'"`

echo Query returned OID: $OIDQuery

# Verify Query returned the same OID as Store
echo "$OIDQuery" | grep "$OIDStore" > /dev/null
if [ "$?" -ne 0 ]
then
	# Print error and quit
        echo "Query did not return expected results."
        echo "The OID returned by Query should be the same as the OID returned by Store"
        exit 1
fi

# Delete the data.
echo Calling DeleteObject on OID: $OIDStore
DeletedOID=`$SCRIPT_PATH/DeleteObject localhost $OIDStore -v`
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "Delete failed"
        exit 1  # Set script's exit status to 1
fi

# Verify DeleteObject returned the OID as  Deleted
if [ "$DeletedOID" != "Deleting $OIDStore" ]
then
        # Print error and quit
        echo "DeleteObject did not return expected results."
        echo "The OID returned by DeleteObject should be the same as the OID being deleted."
        exit 1
fi

# Retrieve deleted data
echo Calling RetrieveData on OID: $OIDStore
$SCRIPT_PATH/RetrieveData localhost $OIDStore ./Output/MD_RetrieveDeleted > ./Output/MD_Output 
if [ "$?" -eq 0 ]
then
        # Print error and quit
        echo "Retrieve succeeded when it should have failed"
        exit 1  # Set script's exit status to 1
fi

# Since the file was deleted there should be no output
fileContents=`cat ./Output/MD_RetrieveDeleted`
if [ "$fileContents" != "" ]
then
        # Print error and quit
        echo "File Comparision (2) failed"
        exit 1
fi

# Success!!!
echo Metadata Test Passed!
 

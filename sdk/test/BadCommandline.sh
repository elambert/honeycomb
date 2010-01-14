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

echo "STARTING SCRIPT: BadCommandLine.sh"
echo ""

# Call StoreFile with a file that does not exist 
echo Calling StoreFile to store a file that does not exist 
$SCRIPT_PATH/StoreFile localhost ./Input/FileDoesNotExist > ./Output/BC_StoreNoExist
if [ "$?" -eq 0 ]
then
        # Print error and quit
        echo "StoreFile should have failed but returned success"
        exit 1  # Set script's exit status to 1
fi

# Call StoreFile with a server that does not exist
echo Calling StoreFile with a server that does not exist 
$SCRIPT_PATH/StoreFile serverdoesnotexist ./Input/SampleFile1 > ./Output/BC_StoreServerNotExist
if [ "$?" -eq 0 ]
then
        # Print error and quit
        echo "StoreFile should have failed but returned success"
        exit 1  # Set script's exit status to 1
fi

# Call StoreFile without enough parameters
echo Calling StoreFile without enough parameters
$SCRIPT_PATH/StoreFile localhost > ./Output/BC_StoreNotEnoughParams
if [ "$?" -eq 0 ]
then
        # Print error and quit
        echo "StoreFile should have failed but returned success"
        exit 1  # Set script's exit status to 1
fi

# Call StoreFile with -h
echo Calling StoreFile with -h
$SCRIPT_PATH/StoreFile -h > ./Output/BC_StoreHelp
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "StoreFile failed"
        exit 1  # Set script's exit status to 1
fi

# Call RetrieveData with a server that does not exist
echo Calling RetrieveData with a server that does not exist 
$SCRIPT_PATH/RetrieveData serverdoesnotexist ./Input/SampleFile1 > ./Output/BC_RetrieveDataServerNotExist
if [ "$?" -eq 0 ]
then
        # Print error and quit
        echo "RetrieveData should have failed but returned success"
        exit 1  # Set script's exit status to 1
fi

# Call RetrieveData without enough parameters
echo Calling RetrieveData without enough parameters
$SCRIPT_PATH/RetrieveData localhost > ./Output/BC_RetrieveDataNotEnoughParams
if [ "$?" -eq 0 ]
then
        # Print error and quit
        echo "RetrieveData should have failed but returned success"
        exit 1  # Set script's exit status to 1
fi

# Call RetrieveData with -h
echo Calling RetrieveData with -h
$SCRIPT_PATH/RetrieveData -h > ./Output/BC_RetrieveDataHelp
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "RetrieveData failed"
        exit 1  # Set script's exit status to 1
fi

# Call DeleteObject with a server that does not exist
echo Calling DeleteObject with a server that does not exist 
$SCRIPT_PATH/DeleteObject serverdoesnotexist 103808404 > ./Output/BC_DeleteServerNotExist
if [ "$?" -eq 0 ]
then
        # Print error and quit
        echo "DeleteObject should have failed but returned success"
        exit 1  # Set script's exit status to 1
fi

# Call DeleteObject without enough parameters
echo Calling DeleteObject without enough parameters
$SCRIPT_PATH/DeleteObject localhost > ./Output/BC_DeleteNotEnoughParams
if [ "$?" -eq 0 ]
then
        # Print error and quit
        echo "DeleteObject should have failed but returned success"
        exit 1  # Set script's exit status to 1
fi

# Call DeleteObject with -h
echo Calling DeleteObject with -h
$SCRIPT_PATH/DeleteObject -h > ./Output/BC_DeleteHelp
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "DeleteObject failed"
        exit 1  # Set script's exit status to 1
fi

# Call Query with a server that does not exist
echo Calling Query with a server that does not exist
$SCRIPT_PATH/Query serverdoesnotexist "test" > ./Output/BC_QueryServerNotExist
if [ "$?" -eq 0 ]
then
        # Print error and quit
        echo "Query should have failed but returned success"
        exit 1  # Set script's exit status to 1
fi

# Call Query without enough parameters
echo Calling Query without enough parameters
$SCRIPT_PATH/Query localhost > ./Output/BC_QueryNotEnoughParams
if [ "$?" -eq 0 ]
then
        # Print error and quit
        echo "Query should have failed but returned success"
        exit 1  # Set script's exit status to 1
fi

# Call Query with -h
echo Calling Query with -h
$SCRIPT_PATH/Query -h > ./Output/BC_QueryHelp
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "Query failed"
        exit 1  # Set script's exit status to 1
fi

# Success!!!
echo BadCommandline script passed!
exit 0

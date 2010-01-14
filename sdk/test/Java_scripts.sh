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

echo Testing java scripts in sdk
SDK_SCRIPTS=../dist/java/scripts

# Query with spaces embedded. Verify that the Query.sh script in the sdk
# wraps the $@ passed to java with quotes.
echo Storing a file w/ 2 md fields using the sdk StoreFile.sh script
OIDStore=`cd $SDK_SCRIPTS ; \
	./StoreFile.sh localhost -m "mp3.artist=snoopy" \
		-m "mp3.title=doghouse" StoreFile.sh`
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "StoreFile.sh failed: $OIDStore"
        exit 1  # Set script's exit status to 1
fi
OIDStore=`echo $OIDStore | awk '{print $1}'`

echo stored $OIDStore

echo Querying for same oid using the Query.sh script in the sdk tree
echo to verify that Query.sh encloses args to java in quotes
OIDQuery=`cd $SDK_SCRIPTS ; \
	./Query.sh localhost "mp3.artist='snoopy' and mp3.title='doghouse'"`
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "Query.sh failed"
        exit 1  # Set script's exit status to 1
fi

# Verify Query returned the same OID as Store
if [ `echo "$OIDQuery" | grep "$OIDStore"` != "$OIDStore" ]
then
        # Print error and quit
        echo "Query.sh did not return expected results"
	echo "ret=" $OIDQuery
        echo "The OID returned by Query should include the OID returned by Store"
        exit 1
fi

echo Deleting oid to clean up
(cd $SDK_SCRIPTS ; ./DeleteRecord.sh localhost $OIDStore -v)
if [ "$?" -ne 0 ]
then
        # Print error and quit
        echo "DeleteRecord.sh failed"
        exit 1  # Set script's exit status to 1
fi

echo Java scripts in sdk passed

exit 0

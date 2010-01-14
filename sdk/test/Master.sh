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

echo "STARTING SCRIPT: Master.sh"
echo ""

echo "Cleaning Output directory..."
rm -f ./Output/*

EXIT=0

# Call the next test script 
echo === NoMetadata.sh...  
./NoMetadata.sh
if [ "$?" -ne 0 ]
then
        echo "NoMetadata.sh failed"
        EXIT=1
fi

# Call the next test script
echo === Metadata.sh...
./Metadata.sh
if [ "$?" -ne 0 ]
then
        echo "Metadata.sh failed"
        EXIT=1
fi

# Call the next test script
echo === Schema.sh...
./Schema.sh
if [ "$?" -ne 0 ]
then
        echo "Schema.sh failed"
        EXIT=1
fi

# Call the next test script
echo === BadCommandline.sh...
./BadCommandline.sh
if [ "$?" -ne 0 ]
then
        echo "BadCommandline.sh failed"
        EXIT=1
fi

# Call the next test script
echo === BadMetadata.sh...
./BadMetadata.sh
if [ "$?" -ne 0 ]
then
        echo "BadMetadata.sh failed"
        EXIT=1
fi

if [ "$EXIT" -eq 0 ] ; then
	echo === All tests in Master.sh passed!
fi
exit $EXIT

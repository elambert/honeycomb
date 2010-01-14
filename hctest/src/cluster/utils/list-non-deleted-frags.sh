#! /bin/sh
#
# $Id: list-non-deleted-frags.sh 10858 2007-05-19 03:03:41Z bberndt $
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
# Run from one of the nodes
#
# usage: list-non-deleted-frags.sh [-v]
#

# size that files end up as when they have been deleted by oa
OA_STUB_SIZE=368

if [ "${1}" = "-v" ]; then
    verbose=true
else
    verbose=false
fi

# argh!  can't seem to use find across mount points
# so must operate at the /netdisks/10.123.45.101/data/1/84
# level
for dir in `ls -d /netdisks/*/data/*/??`
do
    # if in verbose mode, print out the new disk we are searching
    cur=`basename $dir`
    if [ "${verbose}" = "true" -a "${cur}" = "00" ]; then
        echo "--> `dirname $dir`"
    fi

    # find fragment files that aren't deleted
    find $dir -type f ! -size ${OA_STUB_SIZE}c -exec ls -l {} \;

    # the following line is handy for deleting partially stored objects.
    # see bug 6188597: interrupting client during store results in partial
    # store
    #find $dir -type f ! -size ${OA_STUB_SIZE}c -exec rm -f {} \;

    # this is just an exmaple of how you can match on a file name...
    #find $dir -type f -name '*_*' ! -size ${OA_STUB_SIZE}c -exec ls -l {} \;
done

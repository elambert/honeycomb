#!/bin/sh
#
# $Id: install_solaris.sh 10853 2007-05-19 02:50:20Z bberndt $
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

# $1 is the node to install to
# $2 is the path to the SUNWhcserver.pkg file

# Debug
set -x

case $# in
1)  node="$1" pkg=SUNWhcserver.pkg
2)  node="$1" pkg="$2";;
*)  echo "usage $0 <node> <pkg>"
    exit 2
esac

echo "Installing SUNWhcserver on node $node..."

# Set the install dir
install_dir=/tmp

# Copy the package
if [ -f $pkg ]
then
    echo "Copying $pkg to $node:$install_dir..."
    scp $pkg $node:$install_dir
else
    echo "Could not find $pkg on local machine. Exiting..."
    exit 1
fi

ssh -n $node pkgtrans $install_dir/SUNWhcserver.pkg . SUNWhcserver
ssh -n $node pkgrm SUNWhcserver
ssh -n $node pkadd -d /tmp SUNWhcserver

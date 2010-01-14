#! /bin/sh
#
# $Id: startup_hadb.sh 7967 2006-04-20 01:17:55Z sarahg $
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
DIR=`cd \`dirname $0\`/..; pwd`
HADBBIN=/opt/SUNWhadb/4/bin
VAR="$DIR/var"
HADBDIR="$VAR/hadb"

echo "Creating directories..."
if [ ! -d "$VAR" ] ; then mkdir "$VAR" ; fi
if [ ! -d "$HADBDIR" ] ; then mkdir "$HADBDIR" ; fi
if [ ! -d "$HADBDIR/history" ] ; then mkdir "$HADBDIR/history" ; fi
if [ ! -d "$HADBDIR/log" ] ; then mkdir "$HADBDIR/log" ; fi
if [ ! -d "$HADBDIR/repository" ] ; then mkdir "$HADBDIR/repository" ; fi
if [ ! -d "$HADBDIR/dbdef" ] ; then mkdir "$HADBDIR/dbdef" ; fi

echo "Creating password file..."
echo "HADBM_ADMINPASSWORD=admin" > "$VAR/password"
echo HADBM_DBPASSWORD=superduper >> "$VAR/password"

echo "Creating mgt.cfg configuration file..."
sed -e 's^{HADBDIR}^'$HADBDIR'^g' < "$DIR/config/mgt.cfg.in" > "$VAR/mgt.cfg"

echo "Starting $HADBBIN/ma..."
$HADBBIN/ma "$VAR/mgt.cfg" &
echo "Waiting 20 seconds...."
sleep 20

echo "Creating domain localhost..."
$HADBBIN/hadbm createdomain --adminpasswordfile="$VAR/password" localhost
$HADBBIN/hadbm listdomain --adminpasswordfile="$VAR/password"

echo "Creating database 'honeycomb' in domain localhost..."
$HADBBIN/hadbm create --scrollprogress --minimumsize --hosts localhost,localhost --dbpasswordfile="$VAR/password" --adminpasswordfile="$VAR/password" honeycomb

echo "Checking status of database..."
$HADBBIN/hadbm status honeycomb  --adminpasswordfile="$VAR/password"
$HADBBIN/hadbm status honeycomb --nodes --adminpasswordfile="$VAR/password"
$HADBBIN/hadbm resourceinfo honeycomb  --adminpasswordfile="$VAR/password"
$HADBBIN/hadbm deviceinfo honeycomb --details --adminpasswordfile="$VAR/password"

echo
echo "HADB started successfully."
echo
exit 0


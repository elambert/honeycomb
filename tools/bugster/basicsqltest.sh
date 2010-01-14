#! /bin/sh
#
# $Id: basicsqltest.sh 10853 2007-05-19 02:50:20Z bberndt $
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
# This was a basic script to verify basic access
# worked to both the production and test db.
# It isn't really useful, but is a good starting
# point for debugging.

#
# This is a stand alone file that doesn't use the common env
#

ORACLE_HOME="/usr/dist/share/sqlplus,v8.1.7"
LD_LIBRARY_PATH="/usr/dist/share/sqlplus,v8.1.7/lib:$LD_LIBRARY_PATH"
PATH="/usr/dist/share/sqlplus,v8.1.7/bin:$PATH"
TNS_ADMIN="/export/home/sarahg/bugster"

SQLPLUS="/usr/dist/exe/sqlplus -s"
TESTDB=sbluatr
PRODUCTIONDB=sblrpt
#TARGETBTDB=$TESTDB
TARGETBTDB=$PRODUCTIONDB

export ORACLE_HOME
export LD_LIBRARY_PATH
export PATH
export TNS_ADMIN

user=honeycombsql
pass=FILLTHISIN

$SQLPLUS $user/$pass@$TARGETBTDB > bt2.out << EOF > bt2.out
select
         responsible_manager, '|',
         responsible_engineer, '|',
         cr_number, '|',
         priority, '|',
         status, '|',
         synopsis
from
         change_requests
where
         product = 'honeycomb' and
         category = 'software' and
         sub_category = 'objectarchive' and
         status not in ('10-Fix Delivered', '11-Closed') and
         priority in ('1-Very High', '2-High', '3-Medium')
order by
         responsible_manager,
         responsible_engineer,
         priority
;
exit
EOF 


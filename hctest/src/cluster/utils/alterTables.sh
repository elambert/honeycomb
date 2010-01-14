#
# $Id: alterTables.sh 10858 2007-05-19 03:03:41Z bberndt $
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
PATH=$PATH:$HOME/bin:/opt/SUNWspro/bin:/usr/sfw/bin:/usr/ccs/bin/:/opt/sfw/bin:/usr/sbin:/sbin:/usr/local/Acrobat4/:/usr/dist/exe:/usr/local/bin:/export/release/tools/bin:/dev/hctest/src/cluster/utils/:/usr/local/pgsql/bin
export PATH

LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/sfw/lib/sparcv9:/usr/sfw/lib:/usr/local/pgsql/lib:/usr/lib:/usr/lib/sparcv9:/usr/lib/iconv/sparcv9/:/usr/lib/iconv:/usr/local/pgsql/lib
export LD_LIBRARY_PATH

TERM=xterm
export TERM
unset USERNAME
alias vi=vim

psql -h hc-dev3.sfbay.sun.com -U $1 -d $1 -c "alter table obj alter column deleted drop not null"

psql -h hc-dev3.sfbay.sun.com -U $1 -d $1 -c "alter table obj add column lock varchar"

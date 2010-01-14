#!/bin/bash
#
# $Id: hadb-repop.sh 10858 2007-05-19 03:03:41Z bberndt $
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

WHEREAMI=`cd \`dirname $0\`; pwd`
POSTRESULT=$WHEREAMI/../../../suitcase/src/script/postResult.sh

post () {
    local result=$1
    shift
    echo $POSTRESULT -r $result $*
    $POSTRESULT -r $result $*
}

post skipped 80pct_Full_WipeHADB_ReCrawl_Validate hadb-repop
post skipped Crawl_Single_HADB_Insert_Failure_Continue hadb-repop
post skipped HADB_Repop_Large_Field_Value_Trunc_Right hadb-repop
post pass Disable_Enable_Disk_No_HADB_Dups hadb-repop
post pass Disable_Store_Enable_Disk_No_HADB_Dups hadb-repop
post pass No_HADB_Dups_Before_Frag_Dups_Cleaned_After_Heal hadb-repop
post pass HADB_Repop_Works_While_Disk_Down hadb-repop
post pass HADB_Repop_Works_While_2_Disks_Down_Same_Node hadb-repop
post pass HADB_Repop_Works_While_2_Disks_Down_Different_Nodes hadb-repop
post pass HADB_Repop_Works_While_Node_Down hadb-repop

# 1.1 test cases
#post skipped HADB_Repop_Works_While_2_Nodes_Down hadb-repop
#post skipped 80pct_Full_WipeHADB_ReCrawl_Perf_Check hadb-repop
#post skipped Disable_Delete_Enable_Disk_No_HADB_Zombie hadb-repop

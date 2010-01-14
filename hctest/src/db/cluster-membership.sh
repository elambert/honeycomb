#!/bin/bash
#
# $Id: cluster-membership.sh 10858 2007-05-19 03:03:41Z bberndt $
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

# The following cases have been deferred to 1.1.

-------------
-------------
CMM_Subsystem
-------------
Test Procedure: NodeBounce::FailHC

Test Case	Status	Last Run	Build	History
16-Nodes,BounceTwo,Repeatedly	skipped	2005-11-09 11:46:08	anza_1	history
16-Nodes,BounceFour,Once	skipped	2005-11-09 11:46:09	anza_1	history
16-Nodes,BounceFour,Repeatedly	skipped	2005-11-09 11:46:10	anza_1	history
16-Nodes,BounceOne,Once	skipped	2005-11-09 11:46:05	anza_1	history
16-Nodes,BounceOne,Repeatedly	skipped	2005-11-09 11:46:06	anza_1	history
16-Nodes,BounceTooMany,Once	skipped	2005-11-09 11:46:11	anza_1	history
16-Nodes,BounceTooMany,Repeatedly	skipped	2005-11-09 11:46:12	anza_1	history
16-Nodes,BounceTwo,Once	skipped	2005-11-09 11:46:07	anza_1	history
-------------
Test Procedure: MasterFailover::ViceMasterFailover

Test Case	Status	Last Run	Build	History
16-Nodes,CMM-Only,FailoverOnce	skipped	2005-09-16 17:16:53	Unknown	history
16-Nodes,CMM-Only,FailoverRepeatedly	skipped	2005-09-16 17:16:45	Unknown	history
16-Nodes,CMM-Only,FailoverTwice	skipped	2005-09-28 11:00:00	trunk	history
-------------
Test Procedure: MasterFailover::MasterFailover

Test Case	Status	Last Run	Build	History
16-Nodes,CMM-Only,FailoverOnce	skipped	2005-09-16 17:08:22	Unknown	history
16-Nodes,CMM-Only,FailoverRepeatedly	skipped	2005-09-16 17:09:31	Unknown	history
16-Nodes,CMM-Only,FailoverTwice	skipped	2005-09-16 17:09:07	Unknown	history
-------------

-------------
-------------
CMM_HC
-------------
Test Procedure: NodeBounce::FailHC

Test Case	Status	Last Run	Build	History
16-Nodes,BounceTooMany,Once	skipped	2005-11-09 11:46:12	anza_1	history
16-Nodes,BounceTooMany,Repeatedly	skipped	2005-11-09 11:46:13	anza_1	history
16-Nodes,BounceTwo,Once	skipped	2005-11-09 11:46:08	anza_1	history
16-Nodes,BounceTwo,Repeatedly	skipped	2005-11-09 11:46:09	anza_1	history
16-Nodes,BounceFour,Once	skipped	2005-11-09 11:46:10	anza_1	history
16-Nodes,BounceFour,Repeatedly	skipped	2005-11-09 11:46:11	anza_1	history
16-Nodes,BounceOne,Once	skipped	2005-11-09 11:46:06	anza_1	history
16-Nodes,BounceOne,Repeatedly	skipped	2005-11-09 11:46:07	anza_1	history

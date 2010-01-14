#!/bin/bash
#
# $Id: capi.sh 10858 2007-05-19 03:03:41Z bberndt $
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

#post pass session_create_handle_delete capi-session-ez
#post pass session_schema_compare capi-session-ez
#post pass session_bad_args capi-session-ez

#post pass store_both_nullmd capi-store-ez
#post pass store_both_md capi-store-ez
#post pass store_md capi-store-ez
#post pass store_bad_args capi-store-ez

#post pass retrieve_md capi-retrieve-ez
#post pass retrieve capi-retrieve-ez
#post pass retrieve_sizes capi-retrieve-ez
#post pass retrieve_after_delete capi-retrieve-ez
#post pass retrieve_bad_args capi-retrieve-ez

#post pass query_simple capi-query-ez
#post pass query_logical_operators capi-query-ez
#post pass query_comparison_operators capi-query-ez
#post pass query_special_operators capi-query-ez
#post pass query_after_delete capi-query-ez
#post pass query_large_results capi-query-ez
#post pass query_bad_args capi-query-ez

#post pass queryplus_bad_args capi-queryplus-ez
#post pass queryplus_simple capi-queryplus-ez
#post pass queryplus_large_results capi-queryplus-ez

#post pass selectunique_bad_args capi-selectunique-ez
#post pass selectunique_not_implemented capi-selectunique-ez

#post pass loadperf_store capi-loadperf-ez
#post pass loadperf_store_both capi-loadperf-ez
#post pass loadperf_add_metadata capi-loadperf-ez
#post pass loadperf_retrieve_md capi-loadperf-ez
#post pass loadperf_retrieve capi-loadperf-ez
#post pass loadperf_query capi-loadperf-ez
#post pass loadperf_queryplus capi-loadperf-ez

#post pass advanced_store_and_query capi-advanced-ez
#post pass advanced_torture capi-advanced-ez


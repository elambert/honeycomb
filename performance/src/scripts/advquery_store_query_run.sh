#!/bin/bash
#
# $Id: advquery_store_query_run.sh 11701 2007-12-13 15:43:18Z dr129993 $
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
#
# Convenience script to run the AdvQueryMDStore which loads the honeycomb
# system with objects and metadata for query testing. It uses the
# the single table schema. By default it runs 1 client with 20 threads.
# The output file from the run located in the <logdir>/1 directory can
# be used as input to the query tests to help validate some query results.
#
# arg #  Description
# 
# ARG1   Cluster name
# ARG2   Client
# ARG3   Output/Log directory
# ARG4   Runtime time in minutes for store operation. (Optional. Default is 60 minutes.)

if [ $# -lt 3 ]; then
   echo Invalid number of arguments.
   echo
   echo "Convenience script to run a store/load operation to load testing data and then execute the ADVQUERY query tests."
   echo "Uses the default single table schema."
   echo
   echo "Usage: advquery_store_query_run.sh <cluster> <client> <log directory> [<store-runtime-in-minutes>]"
   echo "Specifying the runtime is optional. Default is 60 minutes."
   echo 
   echo "Example: advquery_store_query_run.sh dev123 cl3 /mnt/test/advquery_testing "
   exit
fi

OUTDIR="${3}_store"

if [ $# -gt 3 ]; then
    ./adv_query_test.sh -c $1 -t $2 -l ${OUTDIR} -e $4 -p 20 -o STORE -m advquery_md_randgen_idx.txt
else
    ./adv_query_test.sh -c $1 -t $2 -l ${OUTDIR} -p 20 -o STORE -m advquery_md_randgen_idx.txt
fi

VERIFY_FILE="${OUTDIR}/1/1.$2.AdvQueryMDStore.$1.1x20.1024"
echo "File is ${VERIFY_FILE}"
PROCS=1
OUTDIR="${3}_query"

./adv_query_test.sh -c $1 -t $2 -l ${OUTDIR} -p ${PROCS} -o SS -q ADVQUERY -m advquery_md_randgen_idx.txt -v ${VERIFY_FILE}


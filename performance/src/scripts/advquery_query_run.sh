#!/bin/bash
#
# $Id: advquery_query_run.sh 11701 2007-12-13 15:43:18Z dr129993 $
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
# Convenience script to run an individual query test using the single table
# schema with indexes.
#
# arg #  Description
# 
# ARG1   Cluster name
# ARG2   Client(s)
# ARG3   Output/Log directory
# ARG4   Query to run. Possible options are:
#        COMPLEX, OREQUAL, COMPLEX2, COMPLEX3, COMPLEX4, COMPLEX5, COMPLEX6,
#        UNIQUE, EMPTY, MANY, ALL
# ARG5   Optional Number of threads - default is 1  
# ARG6   Optional - The output file from the AdvQueryMDStore 
#        containing the list of objects and associated metadata that
#        were stored. You must specify this file if you want the
#        query results verified.

if [ $# -lt 4 ]; then
   echo Invalid number of arguments.
   echo
   echo "Convenience script to run a query test. Runs test using the single query type specified and the default single table schema."
   echo
   echo "Usage: advquery_stress_run.sh <cluster> <client-list> <log directory> <query-type> [<elapsed-time>] [<num-threads>] [<AdvQueryMDStore-output-file>]"
   echo "       Query Types: COMPLEX, OREQUAL, COMPLEX2, COMPLEX3, COMPLEX4, COMPLEX5, COMPLEX6"
   echo "                    UNIQUE, EMPTY, MANY, ALL"
   echo "       Elapsed time in minutes. Default is 60 minutes."
   echo "       Number of threads per client is optional, will be 1 by default."
   echo "       The AdvQueryMDStore-output-file is optional. It is needed when the query results"
   echo "       should be verified. If used this file must exist on the client."
   echo "Example: advquery_query_run.sh dev123 cl3 /mnt/test/advquery_stress OREQUAL 120 5"
   exit
fi
PROCS=$6
ETIME=$5

if [ -z ${ETIME} ]; then
   ETIME="60"
fi
if [ -z ${PROCS} ]; then
   PROCS="1"
fi
if [ -z $7 ]; then
   ./adv_query_test.sh -c $1 -t $2 -l $3 -p ${PROCS} -o SS -q $4 -e ${ETIME} -m advquery_md_randgen_idx.txt
else
   ./adv_query_test.sh -c $1 -t $2 -l $3 -p ${PROCS} -o SS -q $4 -e ${ETIME} -m advquery_md_randgen_idx.txt -v $7
fi

#!/bin/bash
#
# $Id$
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
# Convenience script to run the AdvQuery stress tests using the single table
# schema.
#
# arg #  Description
# 
# ARG1   Cluster name
# ARG2   Client(s)
# ARG3   Output/Log directory
# ARG4   Optional Number of threads - default is 1
# ARG5   Optional - The output file from the AdvQueryMDStore 
#        containing the list of objects and associated metadata that
#        were stored. You must specify this file if you want the
#        query results verified.

if [ $# -lt 3 ]; then
   echo Invalid number of arguments.
   echo
   echo "Convenience script to run a query test. Uses the STRESS set of queries and the default
single table schema."
   echo
   echo "Usage: advquery_stress_run.sh <cluster> <client-list> <log directory> [<num-threads>] [<AdvQueryMDStore-output-file>]"
   echo "       By default the number of threads per client is 1."
   echo "       The AdvQueryMDStore-output-file is optional. It is needed when the query results"
   echo "       should be verified. If used this file must exist on the client."
   echo "Example: advquery_stress_run.sh dev123 cl3 10 /mnt/test/advquery_stress "
   exit
fi
PROCS=$4
if [ -z ${PROCS} ]; then
   PROCS="1"
fi
if [ -z $5 ]; then
   ./adv_query_test.sh -c $1 -t $2 -l $3 -p ${PROCS} -o SS -q STRESS -m advquery_md_randgen_idx.txt
else
   ./adv_query_test.sh -c $1 -t $2 -l $3 -p ${PROCS} -o SS -q STRESS -m advquery_md_randgen_idx.txt -v $5
fi

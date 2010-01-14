#!/bin/bash
#
# $Id: performance_test.sh 11199 2007-07-11 22:07:48Z hs154345 $
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
# script to run single stream and aggregate performance test suite
# takes in starting and ending iteration numbers, and the directory
# to log to
##

##
# Usage: ./performance_test.sh <starting iter> <ending iter> <logdir>
# <starting iter> - starting iteration number (for naming)
# <ending iter> - ending iteration number (for naming and number of iterations)
# <logdir> - full pathname of the directory to log results to
#            <logdir> will be created by the script, but must be a valid
#            path for all clients used
##

###################################################################
# PARAMETERS TO CONFIGURE

# cluster and number of nodes in cluster
CLUSTER=dev310
NODES=16
 
# clients to be used in the test
CLIENTS="cl151 cl152 cl153 cl154 cl155 cl156 cl157 cl158"

# number of threads per client per test iteration
PROCS="20"

# Filesizes to be used in bytes
FILESIZES="1024 512000 1048576 10485760 104857600 1073741824"

# Operations to test - comment out any tests that are not to be performed
# NOTE: Retrieve relies on Store having been run first
#       MDOnly Delete relies on AddMD having been run first
#       MDData Delete relies on MDStore having been run first
#       AddMD relies on Store having been run first
#       Query UNIQUE relies on MDStore having been run first

# Single Stream Tests
STORE_SS=true
MDSTORE_SS=true
ADDMD_SS=true
RETRIEVE_SS=true
QUERY_SS=true
DELETE_SS=true

# Aggregate Tests
STORE_AGG=true
MDSTORE_AGG=true
ADDMD_AGG=true
RETRIEVE_AGG=true
QUERY_AGG=true
DELETE_AGG=true

# Query types
QUERYTYPES="EMPTY UNIQUE SIMPLE COMPLEX2 COMPLEX3 COMPLEX4 COMPLEX5 COMPLEX6 ALL"

# Delete types
DELTYPES="MDonly MDData"

# Amount of time test should be run for each file size in minutes
# (ie, if there are 5 filesizes, and STORETIME=10, then the Store test
# will take a total of 50 minutes for one iteration of the test)
# Total test time = #filesizes * time for test * #tests

# Default time for convenience
SS_TIME=10 #minutes - time for single stream tests
AGG_TIME=10 #minutes - time for aggregate tests

# Calculation of number of filesizes - do not edit
TMPARRAY=($FILESIZES)
NUMSIZES=${#TMPARRAY[@]}

# Single stream tests (converts to seconds, replace $SS_TIME if desired):
let SS_STORETIME=$SS_TIME*60
let SS_MDSTORETIME=$SS_TIME*60
let SS_ADDMDTIME=$SS_TIME*60
let SS_RETRIEVETIME=$SS_TIME*60
#default query time is half normal runtime
let SS_QUERYTIME=$SS_TIME*60/2 
#default delete time is normal runtime divided by number of filesizes
let SS_DELTIME=$SS_TIME*60/$NUMSIZES 

# Aggregate tests (converts to seconds, replace $AGG_TIME if desired):
let AGG_STORETIME=$AGG_TIME*60
let AGG_MDSTORETIME=$AGG_TIME*60
let AGG_ADDMDTIME=$AGG_TIME*60
let AGG_RETRIEVETIME=$AGG_TIME*60
#default query time is half normal runtime
let AGG_QUERYTIME=$AGG_TIME*60/2
#default delete time is normal runtime divided by number of filesizes
let AGG_DELTIME=$AGG_TIME*60/$NUMSIZES

# END PARAMETERS TO CONFIGURE
########################################################################

# Arguments to the script

# beginning and ending iteration numbers
ITER=$1
let ITERATIONS=$2+1

# log directory
LOGDIR=$3

. `dirname $0`/performance_test_base.sh
_do_test $@

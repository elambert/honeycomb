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

##
# Usage: ./run_fill_performance.sh <starting iter> <# initial iter> <# iter per level> <logdir>
# <starting iter> - starting iteration number (for naming)
# <# initial iter> - number of iterations of performance test to run initially
# <# iter per level> - number of performance test iterations per fullness level
# <logdir> - full pathname of results directory to log results to
##

#####################################################################
# Configurable parameters
#
# if you would like to fill the cluster to certain percentage of capacity, you will need to set
# IF_IN_PERCENTAGE = true
# FILLLEVELS="10 20 30 50 60 70 80" 
#  if you would like to fill the cluster to certain number of objs, you will need to set
# IF_IN_PERCENTAGE = false 
# FILLLEVELS="1000000 1500000 2000000 2500000 3000000 4000000"

#####################################################################

# Arguments to script

IF_IN_PERCENTAGE=true
FILLLEVELS="10 20 30 50 60 70 80"

# Starting iteration number (arguments to script)
STARTITER=$1
NUMEARLY=$2  # number of initial performance test iterations
NUMITER=$3 # number of iterations to run per level of performance test
LOGDIR=$4 # directory to log results to

# First run several iterations of the performance test in a row, to capture
# performance at a finer granularity to find the cliff.  The test will fill
# the cluster itself somewhat.
    
let EARLYITER=$STARTITER+$NUMEARLY-1

if [ $NUMEARLY -gt 0 ]; then
  echo [`date`] STARTING INITIAL PERFORMANCE TEST - ITERATIONS ${STARTITER} to ${EARLYITER} into log ${LOGDIR}
  ./performance_test.sh $STARTITER $EARLYITER $LOGDIR
fi

# Amount to increment iteration number by (ie, num iterations - 1)
let INC=$NUMITER-1

let ITER=$EARLYITER+1
let ENDITER=$ITER+$INC

FILLDIR="${LOGDIR}/fill/"

for FILL in $FILLLEVELS; do
  # Fill cluster

    if [ "$IF_IN_PERCENTAGE" = "true" ];then
         echo [`date`] Starting fill to ${FILL}%, iteration ${ITER}
    else
         echo [`date`] Starting fill to ${FILL} objs, iteration ${ITER}
    fi

    ./fill_cluster.sh $ITER $FILL $FILLDIR $IF_IN_PERCENTAGE

  # Performance measurements
  echo [`date`] Starting performance test from ${ITER} to ${ENDITER}
  ./performance_test.sh $ITER $ENDITER $LOGDIR

  let ITER=$ENDITER+1
  let ENDITER=$ITER+$INC
done


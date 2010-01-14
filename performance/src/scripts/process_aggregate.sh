#!/bin/bash
#
# $Id: process_aggregate.sh 10845 2007-05-19 02:31:46Z bberndt $
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

RESDIR="/home/tt107269/Honeycomb/test/PerformanceTests/results/0608_dev310_perftests/"
CLIENTS="cl151 cl152 cl153 cl154 cl155 cl156 cl157 cl158"
FILESIZES="5120 921600 5120000 10240000 102400000"
#FILESIZES="102400 512000 1048576 10485760"
FILEBASE="dev310.8x20"

ITER=$1
let ITERSTOP=$2+1
OPER=$3

while [ $ITER -lt $ITERSTOP ]; do

  RESFILE="${RESDIR}/${ITER}.${OPER}.processed"
  for SIZE in $FILESIZES; do
    OUTFILE="${RESDIR}/${ITER}.${OPER}.${SIZE}"
    for CLIENT in $CLIENTS; do
	cat ${RESDIR}/${ITER}.${CLIENT}.${OPER}.${FILEBASE}.${SIZE} | java -classpath .:honeycomb-perftest.jar:honeycomb-client.jar:honeycomb-test.jar AnalyzeStress >> $OUTFILE
    done
    echo "Filesize: $SIZE" >> $RESFILE
    ./sum_aggregate.pl $OUTFILE >> $RESFILE
    echo >> $RESFILE
  done

  let ITER=$ITER+1
done
    

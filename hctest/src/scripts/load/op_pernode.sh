#!/bin/bash 
#
# $Id: op_pernode.sh 10858 2007-05-19 03:03:41Z bberndt $
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

TMPDIR="/tmp"
OFILE="ofile"
ENTRIES=100

op() {
node=101 
while [ $node -le `expr $2 + 100` ];
do
   conn=`cat $1 | awk {'print $4'} | grep $node | wc -l`
   ideal_load=`expr 100 / $2`
   let "load_variation=((ideal_load - conn)*100)/ideal_load"
   load_variation=`echo $load_variation | sed s/^-//g` 
   echo "node $node has $conn connections, ideal load: $ideal_load, variation: $load_variation"
   node=`expr $node + 1`
done 
} 

usage() {
   echo "$0 -d <temp. dir> -f <output file> -n <client ip/ports entries>"
   exit 1
}

if [ $# -eq 0 ]; then 
   usage 
fi

while getopts ":d:f:n:" option
do
   case "$option" in
      d) TMPDIR=$OPTARG ;;
      f) OFILE=$OPTARG ;;
      n) ENTRIES=$OPTARG ;; 
      h) usage ;;  
   esac
done

java PopulateIfile ${TMPDIR}/ifile $ENTRIES 
java LoadSpreaderSimulator ${TMPDIR}/rfile ${TMPDIR}/ifile ${TMPDIR}/4Node_${OFILE} 4 
java LoadSpreaderSimulator ${TMPDIR}/rfile ${TMPDIR}/ifile ${TMPDIR}/8Node_${OFILE} 8 
java LoadSpreaderSimulator ${TMPDIR}/rfile ${TMPDIR}/ifile ${TMPDIR}/16Node_${OFILE} 16 

sort -k 4 -o ${TMPDIR}/sorted4Node_${OFILE} ${TMPDIR}/4Node_${OFILE}
sort -k 4 -o ${TMPDIR}/sorted8Node_${OFILE} ${TMPDIR}/8Node_${OFILE}
sort -k 4 -o ${TMPDIR}/sorted16Node_${OFILE} ${TMPDIR}/16Node_${OFILE}

echo "*** 4 node configuration ***"
op ${TMPDIR}/sorted4Node_${OFILE} 4 
echo "*** 8 node configuration ***"
op ${TMPDIR}/sorted8Node_${OFILE} 8 
echo "*** 16 node configuration ***"
op ${TMPDIR}/sorted16Node_${OFILE} 16
echo
echo "${TMPDIR}/ifile has $ENTRIES client IP/PORTS"
exit 0

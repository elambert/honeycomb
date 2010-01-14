#!/bin/bash
#
# $Id: reed_solomon.sh 10858 2007-05-19 03:03:41Z bberndt $
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

for size in single-chunk multi-chunk; do
  for num in single-failure two-failures; do
    echo retrieve-reed-solomon-$size-$num
  done
done

exit

BLOCKSIZE=$[64*1024]
CHUNKSIZE=$[1000*1024*1024]

SINGLECHUNK="\
0 \
1 \
$[$BLOCKSIZE/2] \
$[$BLOCKSIZE-1] \
$BLOCKSIZE \
$[$BLOCKSIZE+1] \
$[3*$BLOCKSIZE/2] \
$[2*$BLOCKSIZE] \
$[5*$BLOCKSIZE/2] \
$[3*$BLOCKSIZE] \
$[$CHUNKSIZE/2] \
$[$CHUNKSIZE-$BLOCKSIZE-1] \
$[$CHUNKSIZE-($BLOCKSIZE/2)] \
$[$CHUNKSIZE-1] \
$[$CHUNKSIZE] \
"

MULTICHUNK=""
for i in $SINGLECHUNK; do
  MULTICHUNK="$MULTICHUNK $[$CHUNKSIZE+$i]"
  MULTICHUNK="$MULTICHUNK $[$CHUNKSIZE-$i]"
done
MULTICHUNK="$MULTICHUNK $[3*$CHUNKSIZE/2]"
for i in $SINGLECHUNK; do
  MULTICHUNK="$MULTICHUNK $[2*$CHUNKSIZE+$i]"
  MULTICHUNK="$MULTICHUNK $[2*$CHUNKSIZE-$i]"
done
MULTICHUNK="$MULTICHUNK $[5*$CHUNKSIZE/2]"
for i in $SINGLECHUNK; do
  MULTICHUNK="$MULTICHUNK $[3*$CHUNKSIZE+$i]"
  MULTICHUNK="$MULTICHUNK $[3*$CHUNKSIZE-$i]"
done

# One disk down
for size in $SINGLECHUNK $MULTICHUNK; do
  for disk_a in d0 d1 d2 d3 d4 p0 p1; do
    chunk_a=$[$size/$CHUNKSIZE]
    if [ $size -ne 0 -a $[$size%$CHUNKSIZE] -eq 0 ]; then
      chunk_a=$[$num_chunks-1]
    fi
    while [ $chunk_a -ge 0 ]; do
      echo retrieve-disk-disabled "size($size),chunk_a($chunk_a),disk_a($disk_a)";
      let chunk_a--
    done
  done
done

# Two disks down
for size in $SINGLECHUNK $MULTICHUNK; do
  for disk_a in d0 d1 d2 d3 d4 p0 p1; do
    for disk_b in d0 d1 d2 d3 d4 p0 p1; do
      chunk_a=$[$size/$CHUNKSIZE]
      if [ $size -ne 0 -a $[$size%$CHUNKSIZE] -eq 0 ]; then
        chunk_a=$[$num_chunks-1]
      fi
      while [ $chunk_a -ge 0 ]; do
        chunk_b=$[$size/$CHUNKSIZE]
        if [ $size -ne 0 ]; then
          if [ $[$size%$CHUNKSIZE] -eq 0 ]; then
            chunk_b=$[$num_chunks-1]
          fi
        fi
        while [ $chunk_b -ge 0 ]; do
          if [ $disk_a != $disk_b -o $chunk_a != $chunk_b ]; then
            echo retrieve-disk-disabled "size($size),chunk_a($chunk_a),disk_a($disk_a),chunk_b($chunk_b),disk_b($disk_b)";
          fi
          let chunk_b--
        done
        let chunk_a--
      done
    done
  done
done

#for i in $MULTICHUNK; do
  #echo $i
#done

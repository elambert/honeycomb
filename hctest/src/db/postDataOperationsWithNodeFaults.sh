#!/bin/bash
#
# $Id: postDataOperationsWithNodeFaults.sh 10858 2007-05-19 03:03:41Z bberndt $
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

WHEREAMI=`cd \`dirname $0\`; pwd`;
SVN=$WHEREAMI/../../../
POSTRESULT=$SVN/suitcase/src/script/postResult.sh

#
# NODE FAULTS
#
#
# Variables: servicing node: yes/no
#

for chunk_a in 1
do
  for frag_a in d0 d3 p1
  do
    for chunk_b in none
    do
      for frag_b in none
      do
        for size in botros-arnoud reed-solomon
        do
          $POSTRESULT -r skipped -p "fault_type(svcadm),size($size),moment(before),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" retrieve_node_down "data-op node-fault"
          $POSTRESULT -r skipped -p "fault_type(svcadm),size($size),moment(before),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" heal_node_down "data-op node-fault"
          $POSTRESULT -r skipped -p "fault_type(svcadm),size($size),moment(during),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" retrieve_node_up "data-op node-fault"
          $POSTRESULT -r skipped -p "fault_type(svcadm),size($size),moment(during),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" retrieve_node_down "data-op node-fault"
          $POSTRESULT -r skipped -p "fault_type(svcadm),size($size),moment(during),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" store_node_down "data-op node-fault"
          $POSTRESULT -r skipped -p "fault_type(svcadm),size($size),moment(during),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" store_node_up "data-op node-fault"
          $POSTRESULT -r skipped -p "fault_type(svcadm),size($size),moment(before),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" heal_node_up "data-op node-fault"
          $POSTRESULT -r skipped -p "fault_type(svcadm),size($size),moment(after),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" heal_node_up "data-op node-fault"
        done
      done
    done
  done
done

for node_a in coord remote
do
  for node_b in none
  do
    for size in botros-arnoud single-chunk multi-chunk
    do
      for moment in before during
      do
          $POSTRESULT -r skipped -p "fault_type(svcadm),size($size),moment($moment),node_a($node_a),node_b($node_b)" retrieve_node_down "data-op node-fault"
      done
      $POSTRESULT -r skipped -p "fault_type(svcadm),size($size),moment(during),node_a($node_a),node_b($node_b)" retrieve_node_up "data-op node-fault"
      
      $POSTRESULT -r skipped -p "fault_type(svcadm),size($size),moment(during),node_a($node_a),node_b($node_b)" store_node_down "data-op node-fault"
      $POSTRESULT -r skipped -p "fault_type(svcadm),size($size),moment(during),node_a($node_a),node_b($node_b)" store_node_up "data-op node-fault"

      for moment in during after
      do
          $POSTRESULT -r skipped -p "fault_type(svcadm),size($size),moment($moment),node_a($node_a),node_b($node_b)" heal_node_up "data-op node-fault"
      done
      $POSTRESULT -r skipped -p "fault_type(svcadm),size($size),moment(before),node_a($node_a),node_b($node_b)" heal_node_down "data-op node-fault"

    done
  done
done

exit 0;

#
# XXX transfer the below to node faults
#

#
# Now go to multi-chunk files and fail any fragment on a selection of chunks.
#
for chunk_a in 2 mid last-1 last
do
  for frag_a in any
  do
    for chunk_b in none
    do
      for frag_b in none
      do
        for size in reed-solomon
        do
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(before),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" retrieve_disk_down "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(during),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" retrieve_disk_up "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(during),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" retrieve_disk_down "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(during),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" store_disk_down "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(during),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" store_disk_up "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(before),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" heal_disk_up "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(after),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" heal_disk_up "data-op disk-fault"
        done
      done
    done
  done
done

#
# Now go on to dual disk failures for a selection of frag combos.  
# (An exhaustive set of combinations can be run once these pass.)
#
for chunk_a in 1
do
  for frag_a in n1 n3 m1
  do
    for chunk_b in 1
    do
      for frag_b in n2 n4 m2
      do
        for size in botros-arnoud reed-solomon
        do
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(before),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" retrieve_disk_down "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(during),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" retrieve_disk_up "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(during),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" retrieve_disk_down "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(during),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" store_disk_down "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(during),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" store_disk_up "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(before),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" heal_disk_up "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(after),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" heal_disk_up "data-op disk-fault"
        done
      done
    done
  done
done

#
# Now do dual disk failures with multi-chunk files.
#
for chunk_a in 2 last-1
do
  for frag_a in any
  do
    for chunk_b in mid last
    do
      for frag_b in any
      do
        for size in reed-solomon
        do
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(before),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" retrieve_disk_down "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(during),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" retrieve_disk_up "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(during),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" retrieve_disk_down "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(during),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" store_disk_down "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(during),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" store_disk_up "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(before),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" heal_disk_up "data-op disk-fault"
          $POSTRESULT -r skipped -p "fault_type(cli),size($size),moment(after),chunk_a($chunk_a),frag_a($frag_a),chunk_b($chunk_b),frag_b($frag_b)" heal_disk_up "data-op disk-fault"
        done
      done
    done
  done
done


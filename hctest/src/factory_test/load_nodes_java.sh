#!/bin/bash
#
# $Id: load_nodes_java.sh 10858 2007-05-19 03:03:41Z bberndt $
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
#  Launch hcload_java.sh on cluster nodes from cheat, then gather/summarize
#  the results. Copies /opt/test from cheat to cluster nodes.
#
LAUNCH=hcload_java.sh
EXEDIR=/opt/test/bin/
USAGE="$0 <max_node (101..116)> <$LAUNCH options>"

if test $# -lt 1 ; then
    echo $USAGE
    exit 1
fi

PROG=$0
MAX_NODE=$1
shift
CMD="$EXEDIR/$LAUNCH $*"

host_up()
{
    if test -z "$1" ; then
        echo "host_up() - need arg"
        exit 1
    fi

    host=$1
    if test -z "$2" ; then
        wait=0
    else
        wait=1
    fi

    echo Checking availability of $host..

    while [ 1 ] ; do

        RES=`ping -q -c 1 $host |  grep '^rtt' | awk '{print $1}'`
        if test "$RES" == "rtt" ; then
            break
        elif test $wait -eq 0 ; then
            echo "    " $host is unavailable
            return 1
        fi
        echo "  $host doesn't ping - sleep 5"
        sleep 5

    done
    echo "    " $host is available
    return 0
}

START=`date`

#echo ; echo

# launch a job to each node

#echo $MAX_NODE
#echo $CMD

c=101
while test $c -le $MAX_NODE ; do
    node=hcb$c
#   echo ssh root@${NODES[$c]}  \"pkill java\"
    ssh $node "pkill $LAUNCH"
    scp -qr /opt/test ${node}:/opt/
    echo ssh $node "$CMD > /tmp/$$.$LAUNCH.$c"
    ssh $node "$CMD > /tmp/$$.$LAUNCH.$c" &
    PIDS[$c]=$!
    c=$(( $c + 1 ))
done
echo WAITING..
failed=0
c=101
while test $c -le $MAX_NODE ; do
    wait ${PIDS[$c]}
    rc=$?
    if test $rc -ne 0 ; then
        failed=$(( $failed + 1 ))
        echo got one.. failed
    else
        echo got one..
    fi
    c=$(( $c + 1 ))
done

FINISH=`date`

echo FINISHED RUN

# collect results
/bin/rm -f $LAUNCH.*

c=101
while test $c -le $MAX_NODE ; do
    node=hcb$c
    scp -q $node:/tmp/$$.$LAUNCH.$c .
    c=$(( $c + 1 ))
done

# analyze

echo "========= results ======================="

#
#  open
#
open_max=0
for i in `grep "^open:" $$.$LAUNCH.* | awk '{print $7}'` ; do
    if test $i -gt $open_max ; then
        open_max=$i
    fi
done

open_avg=0
ct=0
for i in `grep "^open:" $$.$LAUNCH.* | awk '{print $3}'` ; do
    open_avg=$(( $open_avg + $i ))   
    ct=$(( $ct + 1 ))
done
if test $ct == 0 ; then
    echo NO CASES
    exit 1
fi
open_avg=$(( $open_avg / $ct ))

echo open_avg,max_ms $open_avg $open_max

slow_open=0.0
for i in `grep "^slow_open" $$.$LAUNCH.* | awk '{print $4}'` ; do
    slow_open=$( echo "scale=10; $slow_open + $i " | bc )
    ct=$(( $ct + 1 ))
done
slow_open=$( echo "scale=10; $slow_open / $ct " | bc )

echo slow_open $slow_open\%

#
#  write
#

write_avg_mb_s=0
ct=0
for i in `grep "^write:" $$.$LAUNCH.* | awk '{print $4}' | sed 's/(//'` ; do
    write_avg_mb_s=$( echo "scale=10; $write_avg_mb_s + $i " | bc )
    ct=$(( $ct + 1 ))
done
write_avg_mb_s=$(  echo "scale=10; $write_avg_mb_s / $ct " | bc)
echo write_avg_mb_s $write_avg_mb_s

#
#  close
#
close_avg=0
ct=0
for i in `grep "^close:" $$.$LAUNCH.* | awk '{print $3}' | sed 's/(//'` ; do
    close_avg=$(( $close_avg + $i ))
    ct=$(( $ct + 1 ))
done
close_avg=$(( $close_avg / $ct ))

close_max=0
for i in `grep "^close:" $$.$LAUNCH.* | awk '{print $7}'` ; do
    if test $i -gt $close_max ; then
        close_max=$i
    fi
done
echo close_avg,max_ms $close_avg $close_max

#
#  rename
#
rename_avg=0
ct=0
for i in `grep "^rename:" $$.$LAUNCH.* | awk '{print $3}' | sed 's/(//'` ; do
    rename_avg=$(( $rename_avg + $i ))
    ct=$(( $ct + 1 ))
done
rename_avg=$(( $rename_avg / $ct ))
                                                                                
rename_max=0
for i in `grep "^rename:" $$.$LAUNCH.* | awk '{print $7}'` ; do
    if test $i -gt $rename_max ; then
        rename_max=$i
    fi
done
echo rename_avg,max_ms $rename_avg $rename_max

#
#  total effective per-striped-file bandwidth, open..rename/delete
#
agg_bw_mb_s=0.0
ct=0
for i in `grep "agg_bw_mb_s" $$.$LAUNCH.* | awk '{print $2}'` ; do
    agg_bw_mb_s=$( echo "scale=10; $agg_bw_mb_s + $i " | bc )
    ct=$(( $ct + 1 ))
done
agg_bw_mb_s=$( echo "scale=10; $agg_bw_mb_s / $ct " | bc )
echo net_per_sfile_bw_mb_s $agg_bw_mb_s

/bin/rm -f $$.$LAUNCH.*

#echo "START: " $START
#echo "END:   " $FINISH

if test $failed -eq 0 ; then
    echo PASS
    exit 0
else
    echo FAILED
    exit 1
fi

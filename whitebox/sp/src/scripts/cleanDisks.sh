#!/bin/bash
#
# $Id: cleanDisks.sh 10857 2007-05-19 03:01:32Z bberndt $
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
#  Script to remove all stored data from cluster, plus
#  log files.
#
usage() {
    echo "Usage: $0 <num_nodes>"
    exit 1
}

if [ $# -ne 1 ] ; then
    usage
fi
if  [ $1 -ne 16 -a $1 -ne 8 ] ; then
    echo num_nodes must be 8 or 16
    usage
fi

NUM_NODES=$1
CLEANUP=/tmp/$$.cleanup.sh
#
#  make per-node disk scrub script
#
cat > $CLEANUP <<EOF
#!/bin/bash
scrub_data()
{
    find /data/\$1/[0-9]* -type f | while read f ; do
        /bin/rm \$f
    done
    /bin/rm -rf /data/\$1/MD_cache/*
}
# disable reboots, so skip
#svcadm disable honeycomb-server
sleep 5
for disk in 0 1 2 3 ; do
    scrub_data \$disk &
done
for disk in 0 1 2 3 ; do
    wait
done
/bin/rm -rf /hadb /data/0/hadb
# re-disable since stickage happens
#svcadm disable honeycomb-server
#svcadm enable honeycomb-server
#if we want to remove all traces, roll log & remove
#/bin/rm -f /var/adm/messages*
EOF
chmod +x $CLEANUP

c=1
while [ $c -le $NUM_NODES ] ; do
    node=hcb`expr 100 + $c`
    echo =================== $node
    scp $CLEANUP ${node}:/tmp
    ssh ${node} $CLEANUP &
    c=$(( $c + 1 ))
done
echo WAIT..
c=1
while [ $c -le $NUM_NODES ] ; do
    wait
    echo node done
    c=$(( $c + 1 ))
done

echo CLEANUP DONE

# can we reliably detect errors?

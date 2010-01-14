#! /bin/sh
#
# $Id: netperfb.sh 10856 2007-05-19 02:58:52Z bberndt $
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

NODES_FILE="./NODES" 


while [ "$#" -gt 0 ]
do
	case $1 in 

		-f)
			NODES_FILE=$2
			shift
			shift
			;;
		*)
			echo ""
			echo "Usage: `basename $0` [-f NODE_FILE]"
			echo "-f flag defines NODE_FILE as the file with IP addresses in it"
			echo "NODE_FILE defaults to ./NODES if not given"
			echo ""
			exit 0
			;;
	esac
done

if [ -f "$NODES_FILE" ] ; then
        NODES=`cat $NODES_FILE`
fi

if [ ! -n "$NODES" ] ; then
        echo ""
     	echo "File $NODES_FILE does not exist!"
   	echo "You must fill in the file $NODES_FILE with the NODE"
        echo "IP Addresses one IP Address per line."
        echo ""
        exit 0
fi

DATE=`date +%F-%H-%M-%S`
mkdir /root/platform-tests/netperf-l
LOG="/root/platform-tests/netperf-l/netperf.log"
/root/platform-tests/netserver -p 8888  >> /dev/null 2>&1 &

for node in $NODES
do
	# echo "netperf -H $node -p 8888 -t TCP_STREAM: " >> $LOG
	/root/platform-tests/netperf -H $node -p 8888 -t TCP_STREAM >> $LOG 3>&1 &

	# echo "netperf -H $node -p 8888 -t TCP_RR: "  >> $LOG
	/root/platform-tests/netperf -H $node -p 8888 -t TCP_RR >> $LOG 2>&1  &

	# echo "netperf -H $node -p 8888 -t TCP_RR -- -D: " >> $LOG
	/root/platform-tests/netperf -H $node -p 8888 -t TCP_RR -- -D >> $LOG 2>&1 &


done
wait

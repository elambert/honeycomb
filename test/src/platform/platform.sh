#! /bin/sh 
#
# $Id: platform.sh 10856 2007-05-19 02:58:52Z bberndt $
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

# get the node names of all the nodes in the cluster

NODES_FILE="./NODES" 

# Minimum perf. numbers for Network tests
TCP_MIN=50
REQ_RES=100

# Minimum perf numbers for hdparm tests
MIN_MIN=30
MAX_MIN=800

do_disk="true"
do_net="true"
LOG_LOC="$HOME"
interactive="true"

# a little function to run hdparm on all nodes simultaneously
hdparmer () {
	node=$1
	LOG=$2
                file_systems=`ssh root@$node cat /proc/partitions | grep -v ^m | grep sd | grep -v sd[a-z][0-9] | awk '{print $4}'`
                for fl in $file_systems
                do
                        ssh root@$node hdparm -tT /dev/$fl >> $LOG
                done
} # END FUNCTION


while [ "$#" -gt 0 ]
do
	case $1 in 
		-a)
			do_disk="true"
			echo "Disk test WILL be run"
			disk_init="true"
			echo "Disks WILL be initialized"
			do_bonnie="true"
			echo "bonnie++ WILL be run"
			do_smart="true"
			echo "SMART WILL be run"
			do_net="true"
			echo "Basic Networking WILL be run"
			do_exhaustive="true"
			echo "Exhaustive Networking WILL be run"
			break
			;;
		-b)	
			do_bonnie="true"
			echo "bonnie++ WILL be run"
			;;
		-B)
			do_bonnie="false"
			echo "Bonnie++ will NOT be run"
			;;
		-D) 
			do_disk="false"
			echo "Disk tests will NOT be run"
			disk_init="false"
			echo "Disks will NOT be initialized"
			do_bonnie="false"
			echo "Bonnie++ will NOT be run"
			do_smart="false"
			echo "SMART will NOT be run"
			;;
		-d)
			disk_init="true"
			echo "Disk test WILL be run"
			do_disk="true"
			echo "Disks WILL be initialized"
			do_bonnie="true"
			echo "bonnie++ WILL be run"
			do_smart="true"
			echo "SMART WILL be run"
			;;
		-e)
			do_exhaustive_net="true"
			echo "Exhaustive networking WILL be run"
			;;
		-E)
			do_exhaustive_net="false"
			echo "Exhaustive networking will NOT be run"
			;;
		-i)
			disk_init="true"
			echo "Disks WILL be initialized"
			;;
		-I)	
			disk_init="false"
			echo "Disks will NOT be initialized"
			;;
		-N)
			do_net="false"
			echo "Basic networking will NOT be tested"
			;;
		-n)
			do_net="true"
			do_exhaustive_net="false"
			echo "Basic networking WILL be tested"
			;;
		-q)
			interactive="false"
			;;
		-S)
			do_smart="false"
			echo "SMART will not be run"
			;;
		-s)
			do_smart="true"
			echo "SMART will NOT be run"
			;;
		-f)
			NODES_FILE=$2
			echo "NODES will be read from $NODES_FILE"
			shift
			;;
		-h)
			HOST_FILE=$2
			echo "HOSTS will be read from $HOST_FILE"
			shift
			;;
		-l)
			LOG_LOC=$2
			echo "LOGS will be consolidated in $LOG_LOC"
			shift
			;;
		*)
			echo "Usage: `basename $0` [-a] [-b|B] [-d|D] [-i|I] [-n|N] [-s|S]  [-f NODE_FILE] [-l LOG_LOCATION]"
                        echo "-a flag turns on ALL tests"
                        echo "-b flag turns ON Bonnie++ tests"
                        echo "-B flag turns OFF Bonnie++ tests"
                        echo "-d flag turns ON ALL disk tests"
                        echo "-D flag turns OFF ALL disk tests"
                        echo "-i flag turns ON disk initialization"
                        echo "-I flag turns OFF disk initialazation"
                        echo "-n flag turns ON Network tests"
                        echo "-N flag turns OFF Network tests"
                        echo "-s flag turns ON SMART Disk tests"
                        echo "-S flag turns OFF SMART Disk tests"
                        echo "No arguments runs EVERYTHING (-a)"
                        echo "-f flag defines NODE_FILE as the file with IP addresses in it"
                        echo "NODE_FILE defaults to ./NODES if not given"
			echo "-h flag defines the HOST file, if hosts are different than NODES"
                        echo "-l LOG_LOCATION defines the location to save the log files"
                        echo "Defaults to \$HOME "
                        echo ""
                        echo "NOTE: Using the -d or -D flag with other disk-test flags will"
                        echo "produce unexpected results, depending on the order of the flags."
                        echo "You have been warned."
                        echo ""
                        exit 0

			;;
	esac
	shift
done

if [ ! -n $HOST_FILE ]
then
	HOST_FILE=$NODES_FILE
fi
if [ -f $HOST_FILE ] ; then
        NODES=`cat $HOST_FILE`
fi

if [ ! -n "$NODES" ] ; then
        echo ""
     	echo "File $HOST_FILE does not exist!"
   	echo "You must fill in the file $HOST_FILE with the NODE"
        echo "IP Addresses (one IP Address per line)."
        echo ""
        exit 0
fi
echo "Testing The following Hosts"
for h in $NODES
do
	echo "       $h"
done
DATE=`date +%F-%H-%M-%S`
LOG_DIR="$LOG_LOC/LOGS.$DATE"
mkdir $LOG_DIR
for node in $NODES
do
	echo "Installing Platform tests on Node: $node"
	# ssh root@$node rm -rf /root/platform-tests
	ssh root@$node mkdir /root/platform-tests >> $LOG_DIR/platform.log
	scp -q $NODES_FILE root@$node:/root/platform-tests/$NODE_FILE >> $LOG_DIR/platform.log

	if [ $do_disk == "true" ]
	then
		if [ $disk_init == "true" ]
		then
                	echo "Initializing disks on $node ..."
                	scp -q diskinit.sh root@$node:/root/diskinit.sh >> $LOG_DIR/platform.log
                	ssh root@$node /root/diskinit.sh >> $LOG_DIR/platform.log
                	echo ""
        		echo "Disks initialized!"
		fi

		if [ $do_bonnie == "true" ]
		then
			echo "Bonnie installation in progress ..."

			# ssh bonnie++ to node
			scp -q ./bonnie++ root@$node:/root/platform-tests/bonnie++ >> $LOG_DIR/platform.log
			# and the bonnie run-script
			scp -q ./bonnie.sh root@$node:/root/platform-tests/bonnie.sh >> $LOG_DIR/platform.log
			echo "Bonnie Installed."
		fi
		if [ $do_smart == "true" ]
		then
			echo "SMART Test installation in progress ..."
			# ssh smartclt to node
			scp -q ./smartctl root@$node:/root/platform-tests/smartctl >> $LOG_DIR/platform.log
			# scp the smartctl run script to node
			scp -q ./smartctl.sh root@$node:/root/platform-tests/smartctl.sh >> $LOG_DIR/platform.log
			echo "SMART Tests installed."
		fi
		# this is the minimum disk test ...
		# scp -q ./hdparm.sh root@$node:/root/platform-tests/hdparm.sh
	fi
	if [ $do_net == "true" ] 
	then
		# install all the netperf stuff
		echo "Network test installation in progress ..."
		scp -q ./netserver root@$node:/root/platform-tests/netserver >> $LOG_DIR/platform.log
		scp -q netperf  root@$node:/root/platform-tests/netperf >> $LOG_DIR/platform.log
		if [ $do_exhaustive_net == "true" ]
		then
			scp -q arr_script root@$node:/root/platform-tests/arr_script >> $LOG_DIR/platform.log
			scp -q snapshot_script root@$node:/root/platform-tests/snapshot_script >> $LOG_DIR/platform.log
			scp -q tcp_range_script root@$node:/root/platform-tests/tcp_range_script >> $LOG_DIR/platform.log
			scp -q tcp_rr_script root@$node:/root/platform-tests/tcp_rr_script >> $LOG_DIR/platform.log
			scp -q tcp_stream_script root@$node:/root/platform-tests/tcp_stream_script >> $LOG_DIR/platform.log
			scp -q udp_rr_script root@$node:/root/platform-tests/udp_rr_script >> $LOG_DIR/platform.log
			scp -q udp_stream_script root@$node:/root/platform-tests/udp_stream_script >> $LOG_DIR/platform.log
			scp -q netperfe.sh  root@$node:/root/platform-tests/netperfe.sh >> $LOG_DIR/platform.log
		else
			scp -q netperfb.sh  root@$node:/root/platform-tests/netperfb.sh >> $LOG_DIR/platform.log
		fi
		echo "Network tests installed."

	fi
done
for node in $NODES
do
	echo "Starting to run tests on $node ..."

	if [ $do_disk == "true" ]
	then
		if [ $do_bonnie == "true" ]
		then
			# run bonnie++ on all disk partitions of node:
			echo "Running bonnie++ on $node ..."
			ssh root@$node /root/platform-tests/bonnie.sh & >> $LOG_DIR/platform.log
		fi
		if [ $do_smart == "true" ]
		then
			# run smartctl -A on node
			echo "Running smartctl on $node ..."
			ssh root@$node chmod +x /root/platform-tests/smartctl.sh >> $LOG_DIR/platform.log
			ssh root@$node /root/platform-tests/smartctl.sh& >> $LOG_DIR/platform.log
		fi
	fi
	if [ $do_net == "true" ]
	then

		# run netperf commands 
		if [ $do_exhaustive_net == "true" ]
		then
			echo "Running Exhaustive Netperf on node $node ..."
			ssh root@$node /root/platform-tests/netperfe.sh -f /root/platform-tests/$NODES_FILE & >> $LOG_DIR/platform.log >2&1
		else
			echo "Running Basic Netperf on node $node ..."
			ssh root@$node /root/platform-tests/netperfb.sh -f /root/platform-tests/$NODES_FILE & >> $LOG_DIR/platform.log >2&1
		fi
	fi
done
echo "Now waiting for Network tests to return ... "
wait

if [ $do_disk == "true" ]
then
# this is the MINIMUM disk test ...
echo -n "Testing disks ... "
mkdir $LOG_DIR/hdparm
for node in $NODES
do
		echo -n "."
		hdparmer $node $LOG_DIR/hdparm/hdparm.log.$node &
		# file_systems=`ssh root@$node cat /proc/partitions | grep -v ^m | grep sd | grep -v sd[a-z][0-9] | awk '{print $4}'`
# `ssh root@$node mount | awk '{print $1}' | grep ^\/dev\/ | grep -v ram`

		# for fl in $file_systems
		# do 
			# echo -n "."
			# ssh root@$node hdparm -tT /dev/$fl >> $LOG_DIR/hdparm/hdparm.log.$node
		# done
done
echo ""
echo -n " Now waiting for disk tests to return ..."
wait
echo ""
fi
echo "All tests complete..."
echo ""
echo -n "cleaning up netservers "
for node in $NODES
do
	echo -n "."
	ssh root@$node killall netserver >> $LOG_DIR/platform.log
done
echo ""
echo -n "Gathering log files "
if [ $do_net == "true" ]
then
	mkdir $LOG_DIR/netperf
fi
if [ $do_bonnie == "true" ]
then
	mkdir $LOG_DIR/bonnie
fi
for node in $NODES
do
	echo -n "."
	if [ $do_net == "true" ]
	then
		scp -q root@$node:/root/platform-tests/netperf-l/*log* $LOG_DIR/netperf/netperf.$node >> $LOG_DIR/platform.log
	fi
	if [ $do_bonnie == "true" ]
	then
		scp -q root@$node:/root/platform-tests/*bonnie*log* $LOG_DIR/bonnie/bonnie.$node >> $LOG_DIR/platform.log
	fi
done
echo ""
echo "Logs are available in $LOG_DIR"
cd ..
echo ""
echo -n "Cleaning up test targets "
for node in $NODES
do
	echo -n "."
	ssh root@$node rm -rf /root/platform-tests >> $LOG_DIR/platform.log
done
echo ""
echo "Platform tests Complete."
echo ""
echo -n "Evaluating results ..."
if [ "$do_disk" == "true" ]
then
	log_files=""
	for node in $NODES 
	do
		if [ ! -f $LOG_DIR/hdparm/hdparm.log.$node ]
		then
			echo "No disk results from $node! This is a serious error." >> $LOG_DIR/platform.log
		else
			log_files="$log_files $node"
		fi
	done
	for node in $log_files
	do
		echo -n "."
		min=$MAX_MIN
		while IFS= read theline
		do
			if [ ! -z $dev ]
			then
				num=`echo $theline | awk -F= '{print $2}' | awk '{print $1}' |awk -F. '{print $1}'`
				if [ $num -lt $min ]
				then
					echo "Test failed!! Throughput of $node:$dev was $num (MIN: $min)" >> $LOG_DIR/platform.log
					$failed="true"
				else
					echo "$node:$dev Passed! ($num > $min)" >> $LOG_DIR/platform.log
				fi
			
				if [ $min == $MIN_MIN ]
				then
					min=$MAX_MIN
					unset dev
				else
					min=$MIN_MIN
				fi
			fi
			if [ `echo $theline | grep ^/dev` ]
			then
				dev=$theline
			fi
		done < "$LOG_DIR/hdparm/hdparm.log.$node"
	done
fi
if [ "$do_net" == "true" ]
then
	failed=""
	log_files=""
	for node in $NODES
	do
		if [ ! -f $LOG_DIR/netperf/netperf.$node ]
		then
			echo "No Network results from $node. This is a serious error!" >> $LOG_DIR/platform.log
		else
			log_files="$log_files $node"
		fi
	done
	for node in $log_files
	do
		echo -n "."
		while IFS= read theline
                do
                        if [ -n "$port" ]
                        then
                                if [ "`echo $theline | grep .[1-9][1-9]* | grep -v [TCP,bits]`" ]
                                then
                                        result=`echo $theline | awk '{print $NF}' | awk -F. '{print $1}'`
					if [ "`echo $port | grep REQUEST`" ] 
					then
						min=$REQ_RES
					else
						min=$TCP_MIN
					fi
					if [ $result -lt $min ]
					then
						failed="true"
						echo "$port FAILED with $result ($min)" >> $LOG_DIR/platform.log
					fi
					unset port
					unset result
                                fi
                        fi
                        t=`echo $theline | grep ^TCP `
                        if [ -n "$t" ]
			then
                                port=$theline
			else
				unset t
                        fi
                                                                                        
		done < "$LOG_DIR/netperf/netperf.$node"
	done
fi
echo ""
if [ ! -n "$failed" ]
then
	echo "All tests passed" >> $LOG_DIR/platform.log
fi
if [ "$interactive" == "true" ]
then
while [ true ]
        do
                echo -n "Would you like to see all the gory details? [y|n]: "
                read input
                case $input in
                        [Y,y])
                                show_me="true"
                                break
                                ;;
                        [N,n])
                                show_me="false"
                                break
                                ;;
                        *)
                                echo "Please enter y or n"
                                ;;
                esac
        done
if [ -n "$show_me" ]
then
	echo "Here are all of your results ... (Next time, you can look at $LOG_DIR/platform.log)"
	more $LOG_DIR/platform.log
else
	echo "OK. Your results are in $LOG_DIR/platform.log if you ever want to see them."
fi
fi

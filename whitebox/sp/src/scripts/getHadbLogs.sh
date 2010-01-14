# $Id:$
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

usage () {

	echo ""
	echo "NAME"
	echo "    ${ME} -- get all HADB ma.log and histories from a cluster"
	echo ""
	echo "SYNOPSIS"
	echo "    ${ME}  <START_TIME> <END_TIME> [ <OUTDIR> ]"
	echo ""
	echo "DESCRIPTION"
	echo "    ${ME}  goes to each node in the cluster and retrieves all"
	echo "    ma.log and history files that have entries in the time period"
	echo "    specified by the user. The script will look in the current "
	echo "    HADB directories as well as the 'backup' directories created"
	echo "    when honeycomb wipes HADB ( /data/?/hadb-logs/ )."
	echo ""
        echo "    The options to this script are as follows:"
	echo ""
	echo "    <START_TIME>"
	echo "        Any ma.log or history file that does not contain an entry"
	echo "        after this time will not be included.Date format is as follows:"
	echo "        YYYY-MM-DD_HH:MM:SS"
	echo "    <END_TIME>"
	echo "        Any ma.log or history file that does not contain an entry"
	echo "        before this time will not be included.Date format is as follows:"
	echo "        YYYY-MM-DD_HH:MM:SS"
	echo "    <OUT_DIR>"
	echo "        Path to output directory where log files will be placed. Defaults"
	echo "        to ./hadb-logs. If directory does not exist, it will be created."
	echo ""
	echo "EXAMPLES"
	echo "    Here is an example that gets all the log files from October 16, 2007"
	echo "    to October 17,2007 and places them in a directory called mylogs: "
	echo ""
	echo "    ${ME} 2007-10-16_00:00:00 2007-10-17_00:00:00 ./mylogs"
	echo ""
}

compareDates () {
	# YYYY-MM-DD_HH:MM:SS
	thisDate=$1
	thatDate=$2

	#Parse this date
	thisYMD=${thisDate%_*}
	thisMD=${thisYMD#*-}
	thisYear=${thisYMD%-*-*}
	thisMonth=${thisMD%-*}
	thisDay=${thisMD#*-}

	thisHMS=${thisDate#*_}
	thisMS=${thisHMS#*:}
	thisHour=${thisHMS%:*:*}
	thisMin=${thisMS%:*}
	thisSec=${thisMS#*:}

	#Parse that date
	thatYMD=${thatDate%_*}
	thatMD=${thatYMD#*-}
	thatYear=${thatYMD%-*-*}
	thatMonth=${thatMD%-*}
	thatDay=${thatMD#*-}

	thatHMS=${thatDate#*_}
	thatMS=${thatHMS#*:}
	thatHour=${thatHMS%:*:*}
	thatMin=${thatMS%:*}
	thatSec=${thatMS#*:}

	#compare year
	if [ ${thisYear} -gt ${thatYear} ]; then
		echo 1
		return
	fi
	if [ ${thisYear} -lt ${thatYear} ]; then
		echo -1
		return
	fi

	#compare month
	if [ ${thisMonth} -gt ${thatMonth} ]; then
		echo 1
		return
	fi
	if [ ${thisMonth} -lt ${thatMonth} ]; then
		echo -1
		return
	fi

	#compare day
	if [ ${thisDay} -gt ${thatDay} ]; then
		echo 1
		return
	fi
	if [ ${thisDay} -lt ${thatDay} ]; then
		echo -1
		return
	fi

	#compare hour
	if [ ${thisHour} -gt ${thatHour} ]; then
		echo 1
		return
	fi
	if [ ${thisHour} -lt ${thatHour} ]; then
		echo -1
		return
	fi

	#compare min
	if [ ${thisMin} -gt ${thatMin} ]; then
		echo 1
		return
	fi
	if [ ${thisMin} -lt ${thatMin} ]; then
		echo -1
		return
	fi

	#compare sec
	if [ ${thisSec} -gt ${thatSec} ]; then
		echo 1
		return
	fi
	if [ ${thisSec} -lt ${thatSec} ]; then
		echo -1
		return
	fi

	# they are equal
	echo 0
}


checkFile () {

	node=$1
	file=$2
	ftype=$3

	if [ $ftype = "historyfile" ]; then
		startLine=`ssh $node "egrep '^n:' $file | head -1"`
		endLine=`ssh $node "egrep '^n:' $file | tail -1"`
		startDay=`echo $startLine | cut -d" " -f4`
		endDay=`echo $endLine | cut -d" " -f4`
		startHour=`echo $startLine | cut -d" " -f5`
		endHour=`echo $endLine | cut -d" " -f5`
	else
		startLine=`ssh $node " egrep '^[0-9][0-9][0-9][0-9]-' $file | head -1"`
		endLine=`ssh $node " egrep '^[0-9][0-9][0-9][0-9]-' $file | tail -1"`
		startDay=`echo $startLine | cut -d" " -f1`
		endDay=`echo $endLine | cut -d" " -f1`
		startHour=`echo $startLine | cut -d" " -f2`
		endHour=`echo $endLine | cut -d" " -f2`
	fi
	fileStart=${startDay}_${startHour}
	fileEnd=${endDay}_${endHour}
	echo "Checking $node:$file $ftype  START = $startDay $startHour END = $endDay $endHour"

	checkStartTime=`compareDates ${fileStart} ${END_TIME}`
	checkEndTime=`compareDates ${fileEnd} ${START_TIME}`

	if [ ${checkStartTime} -eq 1 ]; then
		return 1
	fi


	if [ ${checkEndTime} -eq -1 ]; then
		return 1
	fi
	return 0


}

processNode () {
	node=hcb$1
	echo "CHECKING NODE $node"
	ping $node
	if [ $? -ne 0 ]; then
		echo "node $node is not up, skipping!"
		return
	fi

	#process ma.log files
	mafiles=`ssh $node ls /data/?/hadb/log/ma.log 2> /dev/null | egrep -v 'No such file or directory'`;
	mafiles="${mafiles} `ssh $node ls /data/?/hadb-logs/*/ma.log 2> /dev/null | egrep -v 'No such file or directory'`"
	for i in $mafiles; do
		checkFile $node $i malog
		goodFile=$?
		if [ $goodFile -eq "0" ]; then
			echo "grabbing file $i"
			scp $node:$i ${COMPONENT_FILES}/ma.log.$fileStart.$node
		fi
	done
	listOfMAs=`ls ${COMPONENT_FILES}/ma.log.*.$node`
	if [ $? -ne 0 ]; then
		echo "It appears that we got no MA files from $node"
	else
		cat $listOfMAs > ${OUTDIR}/ma.log.$node
	fi	

	historyfiles=`ssh $node ls /data/?/hadb/history/honeycomb.out.* 2> /dev/null | egrep -v 'No such file or directory'`;
	historyfiles="${historyfiles} `ssh $node ls /data/?/hadb-logs/*/history/honeycomb.out.* 2> /dev/null | egrep -v 'No such file or directory'`"
	for i in $historyfiles; do
		checkFile $node $i historyfile
		goodFile=$?
		if [ $goodFile -eq "0" ]; then
			echo "grabbing file $i"
			scp $node:$i ${COMPONENT_FILES}/honeycomb.out.$fileStart.$node
		fi
	done
	listOfHist=`ls ${COMPONENT_FILES}/honeycomb.out.*.$node`
	if [ $? -ne 0 ]; then
		echo "It appears that we got no History files from $node"
	else
		cat $listOfHist > ${OUTDIR}/honeycomb.out.$node
	fi	

}

ME=`basename $0`

if [ $1 == "-H" ] || [ $1 == "--help" ] || [ $1 == "-h" ]; then
	usage
	exit
fi

OUTDIR=./hadb-logs
#args start_time, end_time, outdir
START_TIME=$1
END_TIME=$2
if [ $# -eq 3 ]; then
	OUTDIR=$3
fi

if [ ! -d ${OUTDIR} ]; then
	echo "Can not find output directory ${OUTDIR}"
	exit 1 
fi
COMPONENT_FILES=${OUTDIR}/component_files
mkdir ${COMPONENT_FILES}



#get number of nodes
NUM_NODES=`ssh 10.123.45.200 egrep 'honeycomb.cell.num_nodes' /config/config.properties | cut -d= -f2`


# iter through the nodes and grab all the logs
curNode=101
let finalNode=$((NUM_NODES + 100))
while [ ${curNode} -le ${finalNode} ]; do
	processNode $curNode
	let curNode=$((curNode + 1))
done
rm -rf ${COMPONENT_FILES}

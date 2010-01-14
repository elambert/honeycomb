#!/usr/bin/bash
#
# $Id: grabHADBlogs.sh 10857 2007-05-19 03:01:32Z bberndt $
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

me=`basename $0`
numNodes=16
LOG_ROOT=`pwd`/hadblogs

############
usage () { #
############

	echo "Usage: "
	echo "     ${me} [-outdir <PATH_TO_LOG_DIR> ] [-numnodes <NUM_OF_NODES>] [-help]"
	echo ""
	echo "     This script will grab all the HADB relevant logs from the cluster and "
	echo "     place them in a tar ball. Options are:"
	echo ""
	echo "     -outdir: Path to output directory where logs will copied to and tar ball"
	echo "              is generated. Defaults to \$CWD/hadblogs"
	echo ""
	echo "     -numbodes: Number of nodes in the cluster. Defaults to 16."
	echo ""
	echo "     -help: print this help"

}

#############
getArgs() { #
#############
	while [[ $# -gt 0 ]]; do
		curArg=$1; shift
		if [[ ${curArg} == "-outdir" ]]; then
			if [[ $# -lt 1 ]]; then
				echo "You must specify an outdir with ${curArg}"
				exit 1
			else 
				LOG_ROOT=$1;shift
			fi
		elif [[ ${curArg} == "-numnodes" ]]; then
			if [[ $# -lt 1 ]]; then
				echo "You must specify an outdir with ${curArg}"
				exit 1
			else 
				numNodes=$1;shift
			fi
		elif [[ ${curArg} == "-help" ]]; then
			usage
			exit 0;
		else  
			echo "Unkown options $curArg"
			exit 1
		fi
	done

}

if [[ $# -gt 0 ]]; then
	getArgs $*
fi

LOG_DIR=${LOG_ROOT}/logs
TAR_FILE=${LOG_ROOT}/hadblog.tar
mkdir -p ${LOG_DIR}

#grab malogs
let hadbNode=0;
while ((hadbNode < numNodes)); do
	let hcNodeNum=101+hadbNode 
	hcNodeName=hcb${hcNodeNum}
	scp ${hcNodeName}:/data/0/hadb/log/ma.log ${LOG_DIR}/ma.log.${hadbNode}
	let hadbNode=hadbNode+1
done

#grab history files
let hadbNode=0;
while ((hadbNode < numNodes)); do
	let hcNodeNum=101+hadbNode 
	hcNodeName=hcb${hcNodeNum}
	scp ${hcNodeName}:/data/0/hadb/history/honeycomb.out.${hadbNode} ${LOG_DIR}/honeycomb.out.${hadbNode}
	let hadbNode=hadbNode+1
done

echo admin | ssh hcb101 /opt/SUNWhadb/4/bin/hadbm listdomain > ${LOG_DIR}/listdomain.out
echo admin | ssh hcb101 /opt/SUNWhadb/4/bin/hadbm status --nodes honeycomb > ${LOG_DIR}/status.out
echo admin | ssh hcb101 /opt/SUNWhadb/4/bin/hadbm deviceinfo --details honeycomb > ${LOG_DIR}/deviceinfo.out
echo admin | ssh hcb101 /opt/SUNWhadb/4/bin/hadbm resourceinfo honeycomb > ${LOG_DIR}/resourceinfo.out
ssh admin@10.123.45.200 mdconfig -d > ${LOG_DIR}/schema.out

#tar it up
tar cf ${TAR_FILE} ${LOG_DIR}/*
gzip ${LOG_ROOT}/hadblog.tar 
echo "Tar ball generated at ${TAR_FILE}.gz"

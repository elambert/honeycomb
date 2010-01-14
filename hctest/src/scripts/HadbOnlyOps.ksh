#!/usr/bin/ksh
#
# $Id: HadbOnlyOps.ksh 10858 2007-05-19 03:03:41Z bberndt $
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
ME=$0
CWD=`dirname ${ME}`
ABSCWD=`cd ${CWD}; pwd`


#CLASSPATH_QUERY
CLASSPATH_QUERY="${ABSCWD}/honeycomb-hctest.jar:${ABSCWD}/honeycomb-server.jar:${ABSCWD}/jug.jar:${ABSCWD}/jetty-4.2.20.jar:${ABSCWD}/hadbjdbc4.jar"
CLASS_QUERY="com.sun.honeycomb.hctest.hadb.QueryPerf"

#CLASSPATH_QUERY
CLASSPATH_LOADER="${CLASSPATH_QUERY}:${ABSCWD}/honeycomb-test.jar"
CLASS_LOADER="com.sun.honeycomb.hctest.hadb.Loader"
VMARGS='-Duid.lib.path=emulator'

#CLASSPATH_DELETE
CLASSPATH_DELETE="${CLASSPATH_LOADER}:${ABSCWD}/honeycomb-common.jar"
CLASS_DELETE="com.sun.honeycomb.hctest.hadb.DeletePerf"

COMMAND=$1; shift
ARGS=$@

if [[ "${COMMAND}" == "loader" ]]; then
	CP="${CLASSPATH_LOADER}"
	CLASS="${CLASS_LOADER}"
elif [[ "${COMMAND}" == "queryperf" ]]; then
	CP="${CLASSPATH_QUERY}"
	CLASS="${CLASS_QUERY}"
elif [[ "${COMMAND}" == "delete" ]]; then
	CP="${CLASSPATH_DELETE}"
	CLASS="${CLASS_DELETE}"
else
	echo "Unknown command: ${COMMAND}";
	exit 1
fi

for i in `echo ${CP} | sed -e 's/:/ /g'`; do
	if [[ ! -f ${i} ]]; then
		echo "Missing ${i} jar file."
		exit 1
	fi
done

eval java -cp "${CP}" "${VMARGS}" "${CLASS}" "${ARGS}"


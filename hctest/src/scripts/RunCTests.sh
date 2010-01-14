#!/bin/sh

# $Id: RunCTests.sh 10858 2007-05-19 03:03:41Z bberndt $
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




# ASSUMES THAT I AM BEING RUN FROM build/hctest/dist/bin


##########
die () { #
##########

    msg=$1;
    exitcode=$2;
    echo ""
    echo "${msg}"
    if [ "${exitcode}" -eq 1 ];then
	usage
    fi
    echo ""
    exit "${exitcode}"
}


# Based on the rules from config.pl in client_c
##################
getPlatform () { #
##################

    os=`uname`

    if [ "${os}" = "Linux" ]; then
	PLATFORM="build_Linux";
    elif [ "${os}" = "SunOS" ]; then
	pr=`uname -p`;
	if [ "${pr}" = "sparc" ]; then
            PLATFORM="build_sol_sparc";
	elif [ "${pr}" = "i386" ]; then
	    PLATFORM="build_sol_x86";
	else 
	    die "Unsupported solaris platform ${pr}" 2
	fi
    elif [ "${os}" = "Darwin" ]; then
	PLATFORM="build_MacOSX";
    elif [ "${os}" = "CYGWIN_NT-5.1"  -o "${os}" = "Windows*" ]; then
	PLATFORM="build_Win32";
        HARNESS_BIN="hctestharness.exe"
        HCCLIENT_LIB="honeycomb.dll"
    else
	die "Unsupported operating system ${os}" 2
    fi


}

######################
generateTestRun () { #
######################

    tempFileName="${TMPDIR}/${MINIME}$$.qb"
    if [ -f "${tempFileName}" ]; then
	die "Can not generate QB Result ID. The name of the temp file I was going to use ${tempFileName} already exists." 2
    fi

    echo "QB.id :" > ${tempFileName}
    echo "QB.command : ${HARNESS_PATH} ${ARGS}" >> ${tempFileName}
    echo "QB.tester : ${USER}" >> ${tempFileName}
    echo "QB.start_time : ${NOW}" >> ${tempFileName}
    echo "QB.env : LD_LIBRARY_PATH ${LD_LIBRARY_PATH}" >> ${tempFileName}

    CP=${MYABSDIR}/../lib/honeycomb-test.jar
    if [ "${PLATFORM}" = "build_Win32" ]; then
	tempFileName=`cygpath -d ${tempFileName}`
	CP=`cygpath -d ${CP}`
    fi

    RUN_ID=`java -cp ${CP} com.sun.honeycomb.test.util.HttpClient run ${tempFileName}`
    qbCreateRes=$?
    if [ "${qbCreateRes}" -eq 0 ]; then
        RUN_ID=`echo "${RUN_ID}" | sed -e 's|.* = \([0-9]*\)|\1|g'`
	rm ${tempFileName}
	echo "RUN ID IS ${RUN_ID}"
    else
	die "Failed to generate QB Run Record. File used in the attempt is located at ${tempFileName}" 2
    fi

}



############
usage () { #
############
    echo ""
    echo "${ME} [-help|-h] [testname] [--ctx <test_context_options>]"
    echo ""
    echo "This script is used to drive the execution of the C API tests."
    echo ""
    echo "It's does the following: "
    echo "-Posts a run record to the QB Database (used to obtain a run id)"
    echo "-Set up the LD_LIBRARY_PATH so that the correct libraries are available."
    echo "-Finds the hctestharness appropriate for the current platform and executes it."
    echo ""
}


##################
validateEnv () { #
##################


    # Will I be able to find the libraries I need?
    if [ -d "${MYABSDIR}/../lib/${PLATFORM}" ]; then
        HCTESTLIBDIR=`(cd "${MYABSDIR}/../lib/${PLATFORM}"; pwd)`
    else 
	die "Does not appear that the C libraries for this platform have been built." 2
    fi
    if [ ! -f "${HC_CLIENT_LIB_DIR}/${HCCLIENT_LIB}" ]; then
	die "Can not find the Honeycomb Client Library ${HC_CLIENT_LIB_DIR}/${HCCLIENT_LIB}." 2
    fi

    # Do I have a harness?
    if [ ! -f "${HARNESS_PATH}" ]; then
	die "Can not find Test Harness: ${HARNESS_PATH}" 2
    fi

    # Can I find the QB scripts?
    if [ ! -f "${QB_CLI}" ]; then
	die "Can not find The QB script ${QB_CLI}." 2
    fi
    if [ ! -x "${QB_CLI}" ]; then
	die "The QB CLI script ${QB_CLI} is not executable (or perhaps you lack permission to do it)." 2
    fi


    # Will I be able to write to tmp dir?
    if [ ! -d "${TMPDIR}" ]; then
	die "The temporary directory ${TMPDIR} does not exist or is not a directory." 2
    fi
    if [ ! -w "${TMPDIR}" ]; then
	die "Don't have permission to write to the temporary directory: ${TMPDIR}." 2
    fi
}


########
# MAIN #
########

ME=$0;
MINIME=`basename "${ME}"`
TMPDIR=/tmp
MYDIR=`dirname "${ME}"`
MYABSDIR=`(cd "${MYDIR}"; pwd)`
HARNESS_BIN="hctestharness"
HCCLIENT_LIB="libhoneycomb.so"
ARGS=$*
NOW=`date "+%Y-%m-%d %H:%M:%S"`
QB_CLI="${MYABSDIR}/qb_cli.sh"

if [ "$1" = "-help" -o "$1" = "-h" ]; then
    die "usage" 1
fi



getPlatform

HARNESS_PATH=${MYABSDIR}/${PLATFORM}/${HARNESS_BIN}
HC_CLIENT_LIB_DIR=${MYABSDIR}/../../../client_c/${PLATFORM}/honeycomb/dist 

validateEnv

LD_LIBRARY_PATH="${HCTESTLIBDIR}:$LD_LIBRARY_PATH:${HC_CLIENT_LIB_DIR}"
export LD_LIBRARY_PATH
PATH="${MYABSDIR}:$PATH:$LD_LIBRARY_PATH"
export PATH

generateTestRun
if [  "${ARGS}"X != X ]; then
   ARGS="${ARGS} -qbRunID ${RUN_ID}"
else
   ARGS="-qbRunID ${RUN_ID}"
fi
eval "${HARNESS_PATH}" "${ARGS}"


#!/bin/bash
#
# $Id: postResult.sh 10856 2007-05-19 02:58:52Z bberndt $
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

#
# TPS assistant
#
DEVROOT=../../../

WORKDIR=`dirname $0`
WORKDIR=`cd $WORKDIR; pwd`

findQBCLI() {

    QB_CLI="$DEVROOT/build/test/dist/bin/qb_cli.sh"
    if [ ! -f $QB_CLI ] ; then
        QB_CLI="./qb_cli.sh"
    fi
    if [ ! -f $QB_CLI ] ; then
        QB_CLI="$WORKDIR/qb_cli.sh"
    fi


    if [ ! -f $QB_CLI ] ; then         
        echo "Cannot locate qb_cli.sh, giving up."
        exit 1
    fi

}


setupTempDir() {
    #
    # Setup directory on remote machine
    #
    RETURNVALUE=`mktemp -d`
    retcode=$?
    if [ $retcode -ne 0 ] ; then
        echo "Failed to create temp directory."
        exit 1
    fi
    TEMPDIR=$RETURNVALUE

    chmod 777 $TEMPDIR
    retcode=$?
    if [ $retcode -ne 0 ] ; then
        echo "Failed to update permissions on $TEMPDIR."
        exit 1
    fi


}

removeTempDir() {
    rm -rf $TEMPDIR
}


printUsage() {
    echo "postResults.sh [-n notes] [-d root] [-i runid] [-b build] [-u username] [-s startTime] [-r result] [-p parameters] testName tags"
    echo "   -s startTime in format: %Y-%m-%d %H:%M:%S. uses now if unspecified."
    echo "   -d uses [root] as the path to the root of the dev/build tree"
    echo "   -u uses username in place of the results from \'who am i\'"
    echo "   -n populates the notes field"
    echo "   -r result (required) Valid values are pass, fail, and skipped."
    echo "   -i runid. Assumes null, adds a runid."
    echo "   -p parameters - optional. Defaults to \"DefaultScenario\""


}

USERNAME=`who am i |  awk '{print $1}'`
RESULTS="pass"
PARAMETERS="DefaultScenario"
NOTES=""







while getopts "r:i:n:s:d:u:b:p:h" opt; do
    case $opt in
        b ) BUILD=$OPTARG
            ;;
        d ) DEVROOT=$OPTARG
            ;;
        p ) PARAMETERS=$OPTARG
            ;;
        n ) NOTES=$OPTARG
            ;;
        i ) RUNID=$OPTARG
            ;;
        u ) USERNAME=$OPTARG           
            ;;
        s ) STARTTIME=$OPTARG
            ;;
        h ) printUsage
            exit 0
            ;;
        r ) RESULTS=$OPTARG
            ;;
        \? ) printUsage
        exit 1
    esac
done
shift $(($OPTIND -1 ))



TESTNAME=$1
TAGS=$2



if [ -z "$1" ] ; then 
    echo "Specify test name"
    echo
    printUsage
    exit 1
fi



if [ -z "$2" ] ; then 
    echo "Please speficy tags."
    echo
    printUsage
    exit 1
fi



ENDTIME=`date +"%Y-%m-%d %H:%M:%S"`
if [ -z "$STARTTIME" ] ; then
    STARTTIME=$ENDTIME
fi

setupTempDir


echo "QB.id          : " > $TEMPDIR/input.tmpl
echo "QB.testproc    : $TESTNAME" >> $TEMPDIR/input.tmpl
echo "QB.parameters  : $PARAMETERS">> $TEMPDIR/input.tmpl
echo "QB.run         : $RUNID" >> $TEMPDIR/input.tmpl
echo "QB.start_time  : $STARTTIME" >> $TEMPDIR/input.tmpl
echo "QB.end_time    : $ENDTIME" >> $TEMPDIR/input.tmpl
echo "QB.status      : $RESULTS" >> $TEMPDIR/input.tmpl
echo "QB.buglist     : " >> $TEMPDIR/input.tmpl
echo "QB.taglist     : $TAGS" >> $TEMPDIR/input.tmpl
echo "QB.build       : $BUILD" >> $TEMPDIR/input.tmpl
echo "QB.submitter   : $USERNAME" >> $TEMPDIR/input.tmpl
echo "QB.logs_url    : " >> $TEMPDIR/input.tmpl
echo "QB.notes       : $NOTES" >> $TEMPDIR/input.tmpl


#cat $TEMPDIR/input.tmpl
findQBCLI

$QB_CLI result $TEMPDIR/input.tmpl
removeTempDir

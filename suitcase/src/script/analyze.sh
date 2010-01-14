#!/bin/bash 
#
# $Id: analyze.sh 10856 2007-05-19 02:58:52Z bberndt $
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

WHEREAMI=`cd \`dirname $0\`; pwd`
DBSCRIPT=$WHEREAMI/dbscript.sh
POSTRESULT=$WHEREAMI/postResult.sh
HCSUITCASEJAR=$WHEREAMI/../lib/honeycomb-suitcase.jar

printUsage() {
    echo "analyze.sh [-i runid] <dbname>"
    echo " Note - this system creates temp files in the PWD."
    echo "   -i Store results using [runid]"
}

while getopts "t:o:i:h" opt; do
    case $opt in
        i ) RUNID=$OPTARG
            ;;
        h ) printUsage
            exit 0
            ;;
        \? )  printUsage
        exit 1
    esac
done
shift $(($OPTIND -1 ))

DBNAME=$1
if [ -z $DBNAME ] ; then
    echo "Please specify a database name."
    printUsage
    exit 1
fi

ERRORS=0

dbtest () {
    local test=$1
    echo "---- $test ----"
    STARTTIME=`date +"%Y-%m-%d %H:%M:%S"`
    $DBSCRIPT -c $DBNAME -t $test
    RETCODE=$?
    if [ $RETCODE -ne 0 ] ; then 
        let ERRORS++
        RESULT=fail
        echo fail
    else 
        RESULT=pass
        echo pass
    fi
    if [ ! -z "$RUNID" ] ; then
        RUNIDARGS="-i $RUNID"
    fi
    $POSTRESULT -s "$STARTTIME" $RUNIDARGS -r $RESULT $test fragmentDb
    return $RETCODE
}

dbtest verifyLayout
if [ $? -ne 0 ]; then
    echo "This cluster has objects with missing fragments."
    echo "To view them, use \`$DBSCRIPT -c $DBNAME -t printBadLayouts\`"
    echo "or use \`$DBSCRIPT -c $DBNAME -d [oid]\` to view a particular OID in question."
fi


dbtest checkOmittedFrags
if [ $? -ne 0 ] ; then 
    echo "try \`$DBSCRIPT -c $DBNAME -t printOmittedFrags\`"
fi

dbtest checkExtraFrags
if [ $? -ne 0 ] ; then 
    echo "try \`$DBSCRIPT -c $DBNAME -t printExtraFrags\` (detail)"
    echo "try \`$DBSCRIPT -c $DBNAME -t printOtherOmitted\` (quick summary)"
fi

#
# REMOVING THE FOLLOWING SYSTEM METADATA CHECKS, SINCE THE AUDIT DB IS 
# CURRENTLY OUT OF SYNC WITH THE HARVESTER.  MUST RE-ENABLE AND USE ONCE
# WE TEST BACKUP (1.1) WHICH USES THE SYSTEM METADATA CACHE.
#
#dbtest fragmentMetadataCheck
#if [ $? -ne 0 ] ; then 
    #echo "try \`$DBSCRIPT -c $DBNAME -t fragmentMetadataDisplay\`"
#fi
#
#dbtest metadataFragmentCheck
#if [ $? -ne 0 ] ; then 
    #echo "This cluster may have missing fragments - they're in system metadata but not on disk."
    #echo "try \`$DBSCRIPT -c $DBNAME -t metadataFragmentDisplay\`"
#fi
#
#dbtest correspondanceCheck
#if [ $? -ne 0 ] ; then 
    #echo "try \`$DBSCRIPT -c $DBNAME -t correspondanceDisplay\`"
#fi

echo "---- SUMMARY ----"
echo "# errors: $ERRORS"
if [ $ERRORS -gt 0 ] ; then
    echo "Some validations failed. To do further exploration, run dbscript.sh directly as shown above."
    echo "\`$DBSCRIPT -c $DBNAME\`. The -t tests are your friend."
    echo "\`$DBSCRIPT -h\` gives detailed help."
fi

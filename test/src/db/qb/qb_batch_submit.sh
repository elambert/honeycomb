#!/bin/bash
#
# $Id: qb_batch_submit.sh 10856 2007-05-19 02:58:52Z bberndt $
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

SCRIPTDIR=`dirname $0`



printUsage() {
    echo "qb_submit.sh -u username inputfile"
    echo "Submits manual test runs in batch form. This script could obviously be expanded."
    echo "Currennly, it accepts an input file of the form:"
    echo "QB.id <lf>"
    echo "QB.notes <lf>"
    echo "<lf>"
    echo " Note that the trailing <lf> is required."
    echo "It creates a set of .tmpl files in $PWD - one per line in the input file."
    echo "Warning: This script isn't extensivley tested."
}

while getopts "u:h" opt; do
    case $opt in
        u ) USERNAME=$OPTARG
            ;;

        \? )  printUsage
        exit 1
    esac
done
shift $(($OPTIND -1 ))


if [ -z "$USERNAME" ] ; then 
    echo "Set username with -u."
    echo 
    printUsage
    exit 1
fi


if [ -z "$1" ] ; then 
    echo "Please speficy an input file."
    echo
    printUsage
    exit 1
fi

enterItem () {
    while read TESTCASE; do
        read DESCRIPTION
        read BLANKLINE
        echo testCase: $TESTCASE
        echo desciption: $DESCRIPTION
        echo Blank: $BLANKLINE

        STARTTIME=`date +"%Y-%m-%d %H:%M:%S"`
        rm -rf $TESTCASE.tmpl
        echo "QB.id       : " >> $TESTCASE.tmpl
        echo "QB.testproc    : $TESTCASE" >> $TESTCASE.tmpl
        echo "QB.parameters  : none" >> $TESTCASE.tmpl
        echo "QB.run         : " >> $TESTCASE.tmpl
        echo "QB.start_time  : $STARTTIME" >> $TESTCASE.tmpl
        echo "QB.end_time    : $STARTTIME" >> $TESTCASE.tmpl
        echo "QB.status      : skipped" >> $TESTCASE.tmpl
        echo "QB.buglist     :" >> $TESTCASE.tmpl
        echo "QB.taglist     : fragmentDb" >> $TESTCASE.tmpl
        echo "QB.build       :" >> $TESTCASE.tmpl
        echo "QB.submitter   : $USERNAME" >> $TESTCASE.tmpl
        echo "QB.logs_url    : " >> $TESTCASE.tmpl
        echo "QB.notes       : $DESCRIPTION" >> $TESTCASE.tmpl

        $SCRIPTDIR/qb_cli.sh result $TESTCASE.tmpl        
    done

} 

enterItem < $1

exit 1







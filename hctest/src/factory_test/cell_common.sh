#!/bin/sh
#
# $Id: cell_common.sh 11058 2007-06-19 23:37:55Z mgoff $
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
# Cell test common functions
#

# Set the test title
TITLE="Sun StorageTek 5800 Cell Test"

# Default config file
CONF=/export/jumpstart/config/cluster.conf

# Results
PASS="PASS"
FAIL="FAIL"

# Terminal colors
RES_COL=60
MOVE_TO_COL="echo \\033[${RES_COL}G\c"
SETCOLOR_SUCCESS="echo \\033[1;32m\c"
SETCOLOR_FAILURE="echo \\033[1;31m\c"
SETCOLOR_WARNING="echo \\033[1;33m\c"
SETCOLOR_NORMAL="echo \\033[0;39m\c"
SEPARATOR="-------------------------------------------------------------------"

# Set the logfile
LOG=/tmp/testlog.$$

echo_name() {
    name=$1
    echo >> $LOG
    echo $SEPARATOR >> $LOG
    echo "$name" >> $LOG
    echo $SEPARATOR >> $LOG
    echo $name":\c"
}

echo_success() {
    $MOVE_TO_COL
    echo "[ \c"
    $SETCOLOR_SUCCESS
    echo "PASS\c"
    $SETCOLOR_NORMAL
    echo " ]"
    echo "RESULT: PASS" >> $LOG
}

echo_failure() {
    $MOVE_TO_COL
    echo "[ \c"
    $SETCOLOR_FAILURE
    echo "FAIL\c"
    $SETCOLOR_NORMAL
    echo " ]"
    echo "RESULT: FAIL" >> $LOG
}

# Initialize the test log
init_log() {
    rm -f $LOG
    echo $SEPARATOR >> $LOG
    echo "$TITLE" >> $LOG
    echo $SEPARATOR >> $LOG
    echo >> $LOG
    DATE=`date`
    echo "Test started at $DATE" >> $LOG
}

# End the test log
finish_log() {
    DATE=`date`
    echo >> $LOG
    echo "Test finished at $DATE" >> $LOG
}

# Run the command and output stdout/stderr to the logfile
run_cmd() {
    echo "Running: $*" >> $LOG
    $* >> $LOG 2>&1
    return $?
}

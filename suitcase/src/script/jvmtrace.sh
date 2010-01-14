#!/bin/sh
#
# $Id: jvmtrace.sh 10856 2007-05-19 02:58:52Z bberndt $
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
# Get stack traces from all honeycomb JVMs, save to TRACEFILE.
#

# List of JVMs:
SERVICE_JVMS="MASTER-SERVERS PLATFORM-SERVERS IO-SERVERS LAYOUT-SERVERS API-SERVERS DATADOCTOR"
NODEMGR_JVM=NODE-SERVERS # special case

echo "Getting stack traces for HC JVMs... \c"

# Output file will be in this local directory.
NODE=`uname -n`
DIR=/tmp/state-${NODE}
rm -rf $DIR
mkdir $DIR
TRACEFILE=${DIR}/jvmtrace
rm -f $TRACEFILE
touch $TRACEFILE

# write message to logfile, accented with dividers
log_msg () {
    MSG=$1
    LOG=$2
    DIV="=========================================================";
    echo      >>$LOG
    echo $DIV >>$LOG
    echo $MSG >>$LOG
    echo $DIV >>$LOG
    echo      >>$LOG
}

# assumes that unique NAME is in the command line of running process
# returns zero (0) if the process is not found.
get_pid () {
    NAME=$1
    PID=`ps -Aaf |grep java |grep $NAME |awk '{print $2}'`
    return $PID
}

# forces the process with PID to dump stack trace to its stdout/stderr
# assumes that stdout/stderr is redirected to a known file.
trace_dump () {
    log_msg "STACK TRACE FOR JVM: $JVM" $TRACEFILE
    PID=$1
    kill -QUIT $PID
    # required pause: give the JVM time to write out trace
    sleep 10 
}

# if process was not located, log "no such" message
no_such_jvm () {
    JVM=$1
    TRACEFILE=$2
    log_msg "NO SUCH JVM: $JVM" $TRACEFILE
}

# count existing log lines to skip them in copying later 
log_skip () {
    IN_FILE=$1
    touch $IN_FILE # to ensure existence
    COUNT_LINES=`wc -l $IN_FILE |awk '{print $1}'`
    return $COUNT_LINES
}

# copy the relevant newest portion of INFILE to OUTFILE
log_copy () {
    SKIP_LINES=$1
    IN_FILE=$2
    OUT_FILE=$3
    tail +${SKIP_LINES} $IN_FILE >> $OUT_FILE
}

# special handling for NODE-SERVERS trace, output goes to /tmp/nodemgr.trace

JVM=$NODEMGR_JVM
LOGFILE=/tmp/hc_nodemgr.out # set in etc/init.d/honeycomb start script

get_pid $JVM
PID=$?
if [ "$PID" != "0" ]; then
    log_skip $LOGFILE
    SKIP=$?
    trace_dump $PID
    log_copy $SKIP $LOGFILE $TRACEFILE
else
    no_such_jvm $JVM $TRACEFILE
fi

# common handling for all other JVMs, output goes to /var/adm/messages

LOGFILE=/var/adm/messages # traces go to syslog

for JVM in $SERVICE_JVMS; do
    get_pid $JVM
    PID=$?
    if [ "$PID" != "0" ]; then 
        logger "STACK TRACE FOR JVM: $JVM" # goes to syslog
        log_skip $LOGFILE
        SKIP=$?
        trace_dump $PID
        log_copy $SKIP $LOGFILE $TRACEFILE
    else
        no_such_jvm $JVM $TRACEFILE
    fi
done

# remove syslog crud, leave just the actual stack trace
perl -pi -e "s/.*JVMProcess.flushOutput]//" $TRACEFILE

echo "Done. Output in file: $TRACEFILE"


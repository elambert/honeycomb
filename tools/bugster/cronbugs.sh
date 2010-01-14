#! /bin/sh
#
# $Id: cronbugs.sh 10853 2007-05-19 02:50:20Z bberndt $
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

# set up common env for bug scripts
DIR=`cd \`dirname $0\`; pwd` ; export DIR
COMMON="$DIR/bugscommon.sh"
if [ ! -f $COMMON ]; then
    echo "ERROR: couldn't find common env file $COMMON"
    exit 1
fi
. $COMMON

#TOADDR="sarah.gordon@sun.com"
TOADDR="hc-sw@sun.com"

####################################################
#
# the daily email about yesterday's bugs
# 
## OUT="/tmp/bugsbydate.out"
## BUGMAILMSG="/tmp/bugmail.out"
## SUBJECT="Yesterday's Bugs"

## rm -f $BUGMAILMSG $OUT

## $DIR/bugsbydate -H -l -y > $OUT

## echo "To: $TOADDR" >> $BUGMAILMSG
## echo "Subject: $SUBJECT" >> $BUGMAILMSG
## cat $OUT >> $BUGMAILMSG

# this report seems less useful...don't send for now
## mail $TOADDR < $BUGMAILMSG

# clean up in case we run as root accidentally and then try to run
# as a user, which will yield permission denied...
## rm -f $BUGMAILMSG $OUT

#####################################################
#
# the daily bug DB snapshot
#
$DIR/generatedailybugstats.sh

BUGMAILMSG="$BUGSTATSDIR/$LATEST/$LASTCOMPARE/$DAILYEMAILMSG"
SUBJECT="Summary Of Recent Bug Activity"

# truncate msg to reset
echo "To: $TOADDR" > $BUGMAILMSG
echo "Subject: $SUBJECT" >> $BUGMAILMSG

echo "================== $TYPECUR BUGS SUMMARY ========================" >> $BUGMAILMSG
echo "============== ($TYPEALL BUGS summary below) ====================\n" >> $BUGMAILMSG
cat "$BUGSTATSDIR/$LATEST/$LASTCOMPARE/$CURSUMMARY" >> $BUGMAILMSG

echo >> $BUGMAILMSG
echo >> $BUGMAILMSG
echo "================== $TYPEALL BUGS SUMMARY ========================\n" >> $BUGMAILMSG
cat "$BUGSTATSDIR/$LATEST/$LASTCOMPARE/$ALLSUMMARY" >> $BUGMAILMSG

mail $TOADDR < $BUGMAILMSG

# note that we keep this msg archived, so we don't delete it when done

exit 0

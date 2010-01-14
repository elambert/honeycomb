#
# $Id: bugscommon.sh 10853 2007-05-19 02:50:20Z bberndt $
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
# This is a common environment file for sql bugster queries
#

# variable DIR is set prior to sourcing this file
if [ ! -d "$DIR" ]; then
    echo "ERROR: variable DIR must be set prior to using common env file"
    exit 1
fi

# basic sql/db env
ORACLE_HOME="/usr/dist/share/sqlplus,v8.1.7"
LD_LIBRARY_PATH="/usr/dist/share/sqlplus,v8.1.7/lib:$LD_LIBRARY_PATH"
PATH="/usr/dist/share/sqlplus,v8.1.7/bin:$PATH"
TNS_ADMIN="$DIR"  # where tnsnames.ora is
SQLPLUS="/usr/dist/exe/sqlplus -s"
TESTDB=sbluatr
PRODUCTIONDB=sblrpt

# pick which DB to use, test or production
#TARGETBTDB=$TESTDB
TARGETBTDB=$PRODUCTIONDB

export ORACLE_HOME
export LD_LIBRARY_PATH
export PATH
export TNS_ADMIN

# for passwd
user=honeycombsql
PASSWDFILE="$DIR/bugaccess.sh"
pass="" # in bugaccess.sh

if [ ! -f $PASSWDFILE ]; then
    echo "Could not find passwd file $PASSWDFILE"
    exit 1
fi

. $PASSWDFILE

# where the output of the scripts goes
BUGSTATSDIR="/export/home/bugster/dailystats"

# type of stats we are gathering
TYPEALL="ALL"
TYPEDEV="DEV"
TYPECUR="1.1"
TYPENEXT="1.2" # currently not used
TYPEFUTURE="x.x" # currently not used

# awk files
AWKDIR="$DIR/awk"
AWKDEV="$AWKDIR/awkdevfilter"
AWKCUR="$AWKDIR/awkcurfilter"
AWKDEVALLPRIO="$AWKDIR/awkdevfilterallprio"
AWKOPEN="$AWKDIR/awkfilteropen"
AWKCLOSED="$AWKDIR/awkfilterclosed"
AWKFIXED="$AWKDIR/awkfilterfixed"
AWKRESOLVED="$AWKDIR/awkfilterresolved"
AWKPRIO="$AWKDIR/awkpriocount"
AWKSTATUS="$AWKDIR/awkstatuscount"

# misc
DATESTRING="simpledate"
DATEDETAILEDSTRING="detaileddate"
DATEREADABLESTRING="datereadable"
DATE=`date +%Y%m%d`
DATEDETAILED=`date +%Y%m%d_%H.%M.%S`
DATEREADABLE="`date`"
TIMESTAMP=timestamp.out
LATEST="latest"
LATESTLINK="$BUGSTATSDIR/$LATEST"
COMPARE="compare"
LASTCOMPARE="lastcompare"
DAILYEMAILMSG="dailybugactivitysummary.out"
NEW="new"
OLD="old"

# for backup/archive/data during script runs
LOGS="logs"
BUGSOURCES="bugsourcefiles"

# bug file fields
FIELDCR=1
FIELDPRIO=2
FIELDAREA=3
FIELDSTATUS=4
FIELDSUBCAT=5
FIELDTARGREL=6
FIELDRESPENG=7
FIELDSYNOPSIS=8
# cut/diff bug file fields from compare
CUTFIELDDIFF=1
CUTFIELDCR=2
CUTFIELDPRIO=3
CUTFIELDSTATUS=4

# genbugs files
ALLBUGSOUT="$TYPEALL-bugs.out"
DEVBUGSOUT="$TYPEDEV-bugs.out"
CURBUGSOUT="$TYPECUR-bugs.out"
ALLDEVALLSTATUSALLPRIOUT="$TYPEDEV-bugsallprio.out"
BUGMETRICSOUT="bugmetrics.out"
OPENOUT="open.out"
OPENALLPRIOOUT="openallprio.out"
FIXEDOUT="fixed.out"
CLOSEDOUT="closed.out"
RESOLVEDOUT="resloved.out"
PRIOCOUNT="priocount.out"
OPENPRIOCOUNT="openpriocount.out"
OPENALLPRIOCOUNT="openallpriocount.out"
FIXEDPRIOCOUNT="fixedpriocount.out"
CLOSEDPRIOCOUNT="closedpriocount.out"
RESOLVEDPRIOCOUNT="resolvedpriocount.out"
STATUSCOUNT="statuscount.out"

# file names for compare
SUMMARYOUT="-summary-metrics.out"
ALLSUMMARY="$TYPEALL$SUMMARYOUT"
DEVSUMMARY="$TYPEDEV$SUMMARYOUT"
CURSUMMARY="$TYPECUR$SUMMARYOUT"
NEWOPENOUT="newbugs.out"
NEWREOPENEDOUT="newreopened.out"
UNRESOLVEDSTATUSCHANGEOUT="unresolvedstatuschange.out"
INCREASEDPRIOOUT="increasedprio.out"
DECREASEDPRIOOUT="decreasedprio.out"
NEWFIXEDOUT="newfixed.out"
NEWCLOSEDOUT="newclosed.out"
NEWRESOLVEDOUT="newresloved.out"
OLDBUGNOLONGERTYPEOUT="oldbugnolongertype.out"
NEWLYTYPEOLDBUGOUT="newlytypeoldbug.out"
# formatting
DASHES="-----------------------------------------------------------------------"
SPACES="     "

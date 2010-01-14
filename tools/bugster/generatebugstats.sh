#! /bin/sh
#
# $Id: generatebugstats.sh 10853 2007-05-19 02:50:20Z bberndt $
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
# Given a file that contains all bugs, generate bug stats
#

# set up common env for bug scripts
DIR=`cd \`dirname $0\`; pwd` ; export DIR
COMMON="$DIR/bugscommon.sh"
if [ ! -f $COMMON ]; then
    echo "ERROR: couldn't find common env file $COMMON"
    exit 1
fi
. $COMMON

if [ -z "$1" ]; then
    echo "ERROR: usage: $0 allbugsfile [outputdir]"
    exit 1
fi

ALLBUGSFILE=$1
echo "Using bugs file $ALLBUGSFILE"

OUTDIR="$DIR/`date +%Y%m%d`"
if [ -n "$2" ]; then
    OUTDIR=$2
fi
echo "Using output dir $OUTDIR"
echo "Making dir $OUTDIR if needed"
BUGSOURCESDIR="$OUTDIR/$BUGSOURCES"
mkdir -p $OUTDIR
mkdir -p $BUGSOURCESDIR

if [ ! -d $OUTDIR ]; then
    echo "ERROR: failed to make output dir $OUTDIR"
    echo "specify writable output dir as arg 2"
    echo "usage: $0 allbugsfile [outputdir]"
    exit 1
fi

# make note of when we generated this data
echo "$DATESTRING $DATE" > $OUTDIR/$TIMESTAMP
echo "$DATEDETAILEDSTRING $DATEDETAILED" >> $OUTDIR/$TIMESTAMP
echo "$DATEREADABLESTRING $DATEREADABLE" >> $OUTDIR/$TIMESTAMP

ALLDEVALLSTATUS="$OUTDIR/$DEVBUGSOUT"
ALLDEVALLSTATUSALLPRIO="$OUTDIR/$ALLDEVALLSTATUSALLPRIOUT"
CURBUGS="$OUTDIR/$CURBUGSOUT"

# all 
ALLOPEN="$OUTDIR/$TYPEALL-$OPENOUT"
ALLFIXED="$OUTDIR/$TYPEALL-$FIXEDOUT"
ALLCLOSED="$OUTDIR/$TYPEALL-$CLOSEDOUT"
ALLRESOLVED="$OUTDIR/$TYPEALL-$RESOLVEDOUT"

# dev
ALLDEVOPEN="$OUTDIR/$TYPEDEV-$OPENOUT"
ALLDEVALLSTATUSALLPRIOOPEN="$OUTDIR/$TYPEDEV-$OPENALLPRIOOUT"
ALLDEVFIXED="$OUTDIR/$TYPEDEV-$FIXEDOUT"
ALLDEVCLOSED="$OUTDIR/$TYPEDEV-$CLOSEDOUT"
ALLDEVRESOLVED="$OUTDIR/$TYPEDEV-$RESOLVEDOUT"

# cur
CUROPEN="$OUTDIR/$TYPECUR-$OPENOUT"
CURFIXED="$OUTDIR/$TYPECUR-$FIXEDOUT"
CURCLOSED="$OUTDIR/$TYPECUR-$CLOSEDOUT"
CURRESOLVED="$OUTDIR/$TYPECUR-$RESOLVEDOUT"

#
# Prio count
#

# all
ALLBUGSPRIO="$OUTDIR/$TYPEALL-$PRIOCOUNT"
ALLOPENBUGSPRIO="$OUTDIR/$TYPEALL-$OPENPRIOCOUNT"
ALLFIXEDBUGSPRIO="$OUTDIR/$TYPEALL-$FIXEDPRIOCOUNT"
ALLCLOSEDBUGSPRIO="$OUTDIR/$TYPEALL-$CLOSEDPRIOCOUNT"
ALLRESOLVEDBUGSPRIO="$OUTDIR/$TYPEALL-$RESOLVEDPRIOCOUNT"

# dev
ALLDEVPRIO="$OUTDIR/$TYPEDEV-$PRIOCOUNT"
ALLDEVOPENPRIO="$OUTDIR/$TYPEDEV-$OPENPRIOCOUNT"
ALLDEVALLPRIOOPENPRIO="$OUTDIR/$TYPEDEV-$OPENALLPRIOCOUNT"
ALLDEVFIXEDPRIO="$OUTDIR/$TYPEDEV-$FIXEDPRIOCOUNT"
ALLDEVCLOSEDPRIO="$OUTDIR/$TYPEDEV-$CLOSEDPRIOCOUNT"
ALLDEVRESOLVEDPRIO="$OUTDIR/$TYPEDEV-$RESOLVEDPRIOCOUNT"

# cur
CURPRIO="$OUTDIR/$TYPECUR-$PRIOCOUNT"
CUROPENPRIO="$OUTDIR/$TYPECUR-$OPENPRIOCOUNT"
CURFIXEDPRIO="$OUTDIR/$TYPECUR-$FIXEDPRIOCOUNT"
CURCLOSEDPRIO="$OUTDIR/$TYPECUR-$CLOSEDPRIOCOUNT"
CURRESOLVEDPRIO="$OUTDIR/$TYPECUR-$RESOLVEDPRIOCOUNT"

#
# Status count
#
ALLBUGSSTATUSCOUNT="$OUTDIR/$TYPEALL-$STATUSCOUNT"
ALLDEVSTATUSCOUNT="$OUTDIR/$TYPEDEV-$STATUSCOUNT"
CURBUGSSTATUSCOUNT="$OUTDIR/$TYPECUR-$STATUSCOUNT"

# archive this for posterity and debugging!
echo "Copying master file $ALLBUGSFILE to $BUGSOURCESDIR"
GENERATE="generate-$DATEDETAILED"
cp $ALLBUGSFILE $BUGSOURCESDIR/$ALLBUGSOUT-$GENERATE

#
# Filters on status, etc
#

# all 
echo "All open bugs to $ALLOPEN"
awk -f $AWKOPEN $ALLBUGSFILE > $ALLOPEN

echo "All fixed bugs to $ALLFIXED"
awk -f $AWKFIXED $ALLBUGSFILE > $ALLFIXED

echo "All closed bugs to $ALLCLOSED"
awk -f $AWKCLOSED $ALLBUGSFILE > $ALLCLOSED

echo "All resolved bugs to $ALLRESOLVED"
awk -f $AWKRESOLVED $ALLBUGSFILE > $ALLRESOLVED

# dev
echo "All dev bugs all status all prio to $ALLDEVALLSTATUSALLPRIO"
awk -f $AWKDEVALLPRIO $ALLBUGSFILE > $ALLDEVALLSTATUSALLPRIO

echo "All dev bugs all status to $ALLDEVALLSTATUS"
awk -f $AWKDEV $ALLBUGSFILE > $ALLDEVALLSTATUS

echo "All dev bugs open status to $ALLDEVOPEN"
awk -f $AWKOPEN $ALLDEVALLSTATUS > $ALLDEVOPEN

echo "All dev bugs open status all prio to $ALLDEVALLSTATUSALLPRIOOPEN"
awk -f $AWKOPEN $ALLDEVALLSTATUSALLPRIO > $ALLDEVALLSTATUSALLPRIOOPEN

echo "All dev bugs fixed status to $ALLDEVFIXED"
awk -f $AWKFIXED $ALLDEVALLSTATUS > $ALLDEVFIXED

echo "All dev bugs closed status to $ALLDEVCLOSED"
awk -f $AWKCLOSED $ALLDEVALLSTATUS > $ALLDEVCLOSED

echo "All dev bugs resolved status to $ALLDEVRESOLVED"
awk -f $AWKRESOLVED $ALLDEVALLSTATUS > $ALLDEVRESOLVED

# cur
echo "$TYPECUR bugs all status all prio to $CURBUGS"
awk -f $AWKCUR $ALLBUGSFILE > $CURBUGS

echo "$TYPECUR bugs open status to $CUROPEN"
awk -f $AWKOPEN $CURBUGS > $CUROPEN

echo "$TYPECUR bugs fixed status to $CURFIXED"
awk -f $AWKFIXED $CURBUGS > $CURFIXED

echo "$TYPECUR bugs closed status to $CURCLOSED"
awk -f $AWKCLOSED $CURBUGS > $CURCLOSED

echo "$TYPECUR bugs resolved status to $CURRESOLVED"
awk -f $AWKRESOLVED $CURBUGS > $CURRESOLVED

#
# Priority counts
#

# all
echo "All bugs by priority to $ALLBUGSPRIO"
awk -f $AWKPRIO $ALLBUGSFILE > $ALLBUGSPRIO

echo "All bugs open by priority to $ALLOPENBUGSPRIO"
awk -f $AWKPRIO $ALLOPEN > $ALLOPENBUGSPRIO

echo "All bugs fixed by priority to $ALLFIXEDBUGSPRIO"
awk -f $AWKPRIO $ALLFIXED > $ALLFIXEDBUGSPRIO

echo "All bugs closed by priority to $ALLCLOSEDBUGSPRIO"
awk -f $AWKPRIO $ALLCLOSED > $ALLCLOSEDBUGSPRIO

echo "All bugs resolved by priority to $ALLRESOLVEDBUGSPRIO"
awk -f $AWKPRIO $ALLRESOLVED > $ALLRESOLVEDBUGSPRIO

# dev
echo "All dev bugs by priority to $ALLDEVPRIO"
awk -f $AWKPRIO $ALLDEVALLSTATUS > $ALLDEVPRIO

echo "All dev bugs open all priority to $ALLDEVALLPRIOOPENPRIO"
awk -f $AWKPRIO $ALLDEVALLSTATUSALLPRIOOPEN > $ALLDEVALLPRIOOPENPRIO

echo "All dev bugs open by priority to $ALLDEVOPENPRIO"
awk -f $AWKPRIO $ALLDEVOPEN > $ALLDEVOPENPRIO

echo "All dev bugs fixed by priority to $ALLDEVFIXEDPRIO"
awk -f $AWKPRIO $ALLDEVFIXED > $ALLDEVFIXEDPRIO

echo "All dev bugs closed by priority to $ALLDEVCLOSEDPRIO"
awk -f $AWKPRIO $ALLDEVCLOSED > $ALLDEVCLOSEDPRIO

echo "All dev bugs resolved by priority to $ALLDEVRESOLVEDPRIO"
awk -f $AWKPRIO $ALLDEVRESOLVED > $ALLDEVRESOLVEDPRIO

# cur
echo "$TYPECUR bugs by priority to $CURPRIO"
awk -f $AWKPRIO $CURBUGS > $CURPRIO

echo "$TYPECUR bugs open by priority to $CUROPENPRIO"
awk -f $AWKPRIO $CUROPEN > $CUROPENPRIO

echo "$TYPECUR bugs fixed by priority to $CURFIXEDPRIO"
awk -f $AWKPRIO $CURFIXED > $CURFIXEDPRIO

echo "$TYPECUR bugs closed by priority to $CURCLOSEDPRIO"
awk -f $AWKPRIO $CURCLOSED > $CURCLOSEDPRIO

echo "$TYPECUR bugs resolved by priority to $CURRESOLVEDPRIO"
awk -f $AWKPRIO $CURRESOLVED > $CURRESOLVEDPRIO


#
# Status counts
#
echo "All bugs status count to $ALLBUGSSTATUSCOUNT"
awk -f $AWKSTATUS $ALLBUGSFILE > $ALLBUGSSTATUSCOUNT

echo "All dev bugs status count to $ALLDEVSTATUSCOUNT"
awk -f $AWKSTATUS $ALLDEVALLSTATUS > $ALLDEVSTATUSCOUNT

echo "$TYPECUR bugs status count to $CURBUGSSTATUSCOUNT"
awk -f $AWKSTATUS $CURBUGS > $CURBUGSSTATUSCOUNT


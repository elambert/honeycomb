#! /bin/sh
#
# $Id: generatedailybugstats.sh 10853 2007-05-19 02:50:20Z bberndt $
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
# Generate the daily archive of bug stats
#

# set up common env for bug scripts
DIR=`cd \`dirname $0\`; pwd` ; export DIR
COMMON="$DIR/bugscommon.sh"
if [ ! -f $COMMON ]; then
    echo "ERROR: couldn't find common env file $COMMON"
    exit 1
fi
. $COMMON

OUTDIR="$BUGSTATSDIR/$DATE"
ALLBUGS="$OUTDIR/$ALLBUGSOUT"
LOGDIR="$OUTDIR/$LOGS"
mkdir -p $OUTDIR
mkdir -p $LOGDIR

# get the latest and greatest stats on ALL HC bugs
$DIR/bugsbydate -l -a > $ALLBUGS

# run the script to generate stats based on it
$DIR/generatebugstats.sh $ALLBUGS $OUTDIR > $LOGDIR/generatebugstats.out 2>&1

# compare to previous version to gauge find/fix rate
$DIR/comparebugstats.sh $LATESTLINK $OUTDIR > $LOGDIR/comparebugstats.out 2>&1

# adjust symlinks for calculating diff to previous version for next time
rm -f $LATESTLINK
ln -s $OUTDIR $LATESTLINK

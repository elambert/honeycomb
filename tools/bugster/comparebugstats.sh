#! /bin/sh
#
# $Id: comparebugstats.sh 10853 2007-05-19 02:50:20Z bberndt $
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
# Given two dirs that have bug stats, generate new stats
# based on their diffs.  We do stats for all bugs and
# dev bugs.
#

# set up common env for bug scripts
DIR=`cd \`dirname $0\`; pwd` ; export DIR
COMMON="$DIR/bugscommon.sh"
if [ ! -f $COMMON ]; then
    echo "ERROR: couldn't find common env file $COMMON"
    exit 1
fi
. $COMMON

echo "$0 called with args $*"

if [ -z "$1" -o -z "$2" ]; then
    echo "ERROR: usage: $0 oldbugstats newbugstats"
    exit 1
fi

OLDBUGSTATSDIR=$1
NEWBUGSTATSDIR=$2

if [ ! -d $OLDBUGSTATSDIR ]; then
    echo "Could not find old stats dir $OLDBUGSTATSDIR"
    exit 1
fi

if [ ! -d $NEWBUGSTATSDIR ]; then
    echo "Could not find new stats dir $NEWBUGSTATSDIR"
    exit 1
fi

echo "Using old stats dir $OLDBUGSTATSDIR"
echo "Using new stats dir $NEWBUGSTATSDIR"

OLDALLBUGSFILE="$OLDBUGSTATSDIR/$ALLBUGSOUT"
NEWALLBUGSFILE="$NEWBUGSTATSDIR/$ALLBUGSOUT"
OLDALLDEVBUGSFILE="$OLDBUGSTATSDIR/$DEVBUGSOUT"
NEWALLDEVBUGSFILE="$NEWBUGSTATSDIR/$DEVBUGSOUT"
OLDCURBUGSFILE="$OLDBUGSTATSDIR/$CURBUGSOUT"
NEWCURBUGSFILE="$NEWBUGSTATSDIR/$CURBUGSOUT"

if [ ! -f $OLDALLBUGSFILE ]; then
    echo "Could not find old bug file $OLDALLBUGSFILE"
    exit 1
fi

if [ ! -f $NEWALLBUGSFILE ]; then
    echo "Could not find new bug file $NEWALLBUGSFILE"
    exit 1
fi

echo "Using old bug file $OLDALLBUGSFILE"
echo "Using old new file $NEWALLBUGSFILE"

if [ ! -f $OLDALLDEVBUGSFILE ]; then
    echo "Could not find old dev bug file $OLDALLDEVBUGSFILE"
    exit 1
fi

if [ ! -f $NEWALLDEVBUGSFILE ]; then
    echo "Could not find new dev bug file $NEWALLDEVBUGSFILE"
    exit 1
fi

echo "Using old dev bug file $OLDALLDEVBUGSFILE"
echo "Using new dev bug file $NEWALLDEVBUGSFILE"

if [ ! -f $OLDCURBUGSFILE ]; then
    echo "Could not find old $TYPECUR bug file $OLDCURBUGSFILE"
    exit 1
fi

if [ ! -f $NEWCURBUGSFILE ]; then
    echo "Could not find new $TYPECUR bug file $NEWCURBUGSFILE"
    exit 1
fi

echo "Using old $TYPECUR bug file $OLDCURBUGSFILE"
echo "Using new $TYPECUR bug file $NEWCURBUGSFILE"

read_date()
{
    grep $DATEREADABLESTRING $1/$TIMESTAMP | cut -d" " -f 2-
}

DATEOLD=`read_date $OLDBUGSTATSDIR`
DATENEW=`read_date $NEWBUGSTATSDIR`

echo "Old date stamp was $DATEOLD"
echo "New date stamp was $DATENEW"

# follow the symlink to get the basename if needed
get_basename()
{
    ls -ld $1 | grep -- "->" >/dev/null
    if [ $? -eq 0 ]; then
        link=`ls -ld $1 | cut -d\> -f 2`
        echo `basename $link`
    else
        echo `basename $1`
    fi
}

OLDBASE=`get_basename $OLDBUGSTATSDIR`
NEWBASE=`get_basename $NEWBUGSTATSDIR`
OUTDIR="$NEWBUGSTATSDIR/$COMPARE-${OLDBASE}vs${NEWBASE}"
OUTDIRTMP="$OUTDIR/tmp"
BUGSOURCESDIR="$OUTDIR/$BUGSOURCES"

echo "Using output dir $OUTDIR"
echo "Making dir $OUTDIR if needed"
mkdir -p $OUTDIR
mkdir -p $OUTDIRTMP
mkdir -p $BUGSOURCESDIR

if [ ! -d $OUTDIR ]; then
    echo "ERROR: failed to make output dir $OUTDIR"
    echo "specify writable output dir as arg 2"
    exit 1
fi

echo "Backing up source bugfiles to $BUGSOURCESDIR"
COMPARESTRING="$COMPARE-$DATEDETAILED"
cp -p $OLDALLBUGSFILE $BUGSOURCESDIR/$ALLBUGSOUT-$OLD-$COMPARESTRING
cp -p $NEWALLBUGSFILE $BUGSOURCESDIR/$ALLBUGSOUT-$NEW-$COMPARESTRING
cp -p $OLDALLDEVBUGSFILE $BUGSOURCESDIR/$DEVBUGSOUT-$OLD-$COMPARESTRING
cp -p $NEWALLDEVBUGSFILE $BUGSOURCESDIR/$DEVBUGSOUT-$NEW-$COMPARESTRING
cp -p $OLDCURBUGSFILE $BUGSOURCESDIR/$CURBUGSOUT-$OLD-$COMPARESTRING
cp -p $NEWCURBUGSFILE $BUGSOURCESDIR/$CURBUGSOUT-$NEW-$COMPARESTRING

# first, trim off the fields we don't care about diffing
# we only care about the synopsis and status.
# if priority, area, or subcat changes, that's fine.
OLDALLBUGSFILECUT="$OUTDIRTMP/$ALLBUGSOUT-old-cut.out"
NEWALLBUGSFILECUT="$OUTDIRTMP/$ALLBUGSOUT-new-cut.out"
cut -d" " -f$FIELDCR,$FIELDPRIO,$FIELDSTATUS $OLDALLBUGSFILE > $OLDALLBUGSFILECUT
cut -d" " -f$FIELDCR,$FIELDPRIO,$FIELDSTATUS $NEWALLBUGSFILE > $NEWALLBUGSFILECUT
OLDALLDEVBUGSFILECUT="$OUTDIRTMP/$DEVBUGSOUT-old-cut.out"
NEWALLDEVBUGSFILECUT="$OUTDIRTMP/$DEVBUGSOUT-new-cut.out"
cut -d" " -f$FIELDCR,$FIELDPRIO,$FIELDSTATUS $OLDALLDEVBUGSFILE > $OLDALLDEVBUGSFILECUT
cut -d" " -f$FIELDCR,$FIELDPRIO,$FIELDSTATUS $NEWALLDEVBUGSFILE > $NEWALLDEVBUGSFILECUT
OLDCURBUGSFILECUT="$OUTDIRTMP/$CURBUGSOUT-old-cut.out"
NEWCURBUGSFILECUT="$OUTDIRTMP/$CURBUGSOUT-new-cut.out"
cut -d" " -f$FIELDCR,$FIELDPRIO,$FIELDSTATUS $OLDCURBUGSFILE > $OLDCURBUGSFILECUT
cut -d" " -f$FIELDCR,$FIELDPRIO,$FIELDSTATUS $NEWCURBUGSFILE > $NEWCURBUGSFILECUT

DIFFFILEALLBUGS="$NEWALLBUGSFILECUT-diff.out"
DIFFFILEALLDEVBUGS="$NEWALLDEVBUGSFILECUT-diff.out"
DIFFFILECURBUGS="$NEWCURBUGSFILECUT-diff.out"
# remove the non-interesting lines of the diff via awk
diff $OLDALLBUGSFILECUT $NEWALLBUGSFILECUT | awk '$1 ~/\>/ || $1 ~ /\</' > $DIFFFILEALLBUGS
diff $OLDALLDEVBUGSFILECUT $NEWALLDEVBUGSFILECUT | awk '$1 ~/\>/ || $1 ~ /\</' > $DIFFFILEALLDEVBUGS
diff $OLDCURBUGSFILECUT $NEWCURBUGSFILECUT | awk '$1 ~/\>/ || $1 ~ /\</' > $DIFFFILECURBUGS
echo "All bugs diffs are at $DIFFFILEALLBUGS"
echo "All dev bugs diffs are at $DIFFFILEALLDEVBUGS"
echo "All $TYPECUR bugs diffs are at $DIFFFILEALLDEVBUGS"

#
# Helper routines for assessing bug status
#
status_is_dispatched()
{
   if [ $1 = "1-Dis" ]; then
        return 0
   else
        return 1
   fi
}

status_is_fixed()
{
   if [ $1 = "10-Fi" ]; then
        return 0
   else
        return 1
   fi
}

status_is_closed()
{
   if [ $1 = "11-Cl" ]; then
        return 0
   else
        return 1
   fi
}

status_is_resolved()
{
    status_is_fixed $1
    if [ $? -eq 0 ]; then
        return 0
    fi

    status_is_closed $1
    if [ $? -eq 0 ]; then
        return 0
    fi

    return 1
}

status_is_unresolved()
{
    status_is_resolved $1
    if [ $? -eq 0 ]; then
        return 1
    else 
        return 0
    fi
}

# print to files side by side
files_side_by_side()
{
    f1=$1
    f2=$2
    i=1
    # safe to assume same length
    max=`wc -l $f1`
    while [ $i -le $max ];
    do
       # assume two values per line
       line=`head -$i $f1 | tail -1` 
       val1=`echo $line | cut -d" " -f 1`
       val2=`echo $line | cut -d" " -f 2`
       line1=`printf "%8s %5s" $val1 $val2`

       line=`head -$i $f2 | tail -1` 
       val1=`echo $line | cut -d" " -f 1`
       val2=`echo $line | cut -d" " -f 2`
       line2=`printf "%8s %5s" $val1 $val2`
       
       echo "$line1 $SPACES $line2"
       i=`expr $i + 1`
    done
}

# called with OLDDIR NEWDIR TITLE FILENAME
get_side_by_side()
{
    echo "\n$DASHES\n$3\n$DASHES"
    echo "      Previous              Current\n"
    echo "`files_side_by_side $1/$4 $2/$4`\n\n\n"
}

# called with LOGFILE TYPE
log_stats()
{
    LOGFILE=$1

    echo "current stats: numnewbugs=$numnewbugs numreopened=$numreopened numunresolvedstatuschanged=$numunresolvedstatuschanged numprioincreased=$numprioincreased numpriodecreased=$numpriodecreased numnewfixed=$numnewfixed numnewclosed=$numnewclosed numnewresolved=$numnewresolved numoldbugnolongertype=$numoldbugnolongertype numnewlytypeoldbug=$numnewlytypeoldbug for $LOGFILE" 

    # truncate on first call, append subsequently
    echo "NewBugsFiled                $numnewbugs" > $LOGFILE
    echo "BugsReopened                $numreopened" >> $LOGFILE
    echo "UnresolvedWithStatusChange  $numunresolvedstatuschanged" >> $LOGFILE
    echo "PriorityIncreased           $numprioincreased" >> $LOGFILE
    echo "PriorityDecreased           $numpriodecreased" >> $LOGFILE
    echo "NewlyFixed                  $numnewfixed" >> $LOGFILE
    echo "NewlyClosed                 $numnewclosed" >> $LOGFILE
    echo "NewlyResolved               $numnewresolved" >> $LOGFILE
    if [ $2 != $TYPEALL ]; then
        echo "OldBugNoLongTypeBug         $numoldbugnolongertype" >> $LOGFILE
        echo "OldBugThatIsNewTypeBug      $numnewlytypeoldbug" >> $LOGFILE
    fi
}

bug_metrics() 
{
    DIFFFILE=$1 # could be file of diffs of all, dev, or cur bugs
    TYPE=$2 # what type of difffile

    echo "using diff file $DIFFFILE of type $TYPE"

    if [ "$TYPE" = $TYPEALL ]; then
        OLDBUGSFILE=$OLDALLBUGSFILE
        NEWBUGSFILE=$NEWALLBUGSFILE
    elif [ "$TYPE" = $TYPEDEV ]; then
        OLDBUGSFILE=$OLDALLDEVBUGSFILE
        NEWBUGSFILE=$NEWALLDEVBUGSFILE
    elif [ "$TYPE" = $TYPECUR ]; then
        OLDBUGSFILE=$OLDCURBUGSFILE
        NEWBUGSFILE=$NEWCURBUGSFILE
    else
        echo "ERROR: invalid type $TYPE"
        exit 1
    fi

    BASENAMEFORMETRICS="$OUTDIR/$TYPE"

    BUGMETRICSFILE="$BASENAMEFORMETRICS-$BUGMETRICSOUT"
    NEWBUGMETRICSFILE="$BASENAMEFORMETRICS-$NEWOPENOUT"
    NEWREOPENEDMETRICSFILE="$BASENAMEFORMETRICS-$NEWREOPENEDOUT"
    UNRESOLVEDSTATUSCHANGEDBUGMETRICSFILE="$BASENAMEFORMETRICS-$UNRESOLVEDSTATUSCHANGEOUT"
    INCREASEDPRIOMETRICSFILE="$BASENAMEFORMETRICS-$INCREASEDPRIOOUT"
    DECREASEDPRIOMETRICSFILE="$BASENAMEFORMETRICS-$DECREASEDPRIOOUT"
    NEWFIXEDMETRICSFILE="$BASENAMEFORMETRICS-$NEWFIXEDOUT"
    NEWCLOSEDMETRICSFILE="$BASENAMEFORMETRICS-$NEWCLOSEDOUT"
    NEWRESOLVEDMETRICSFILE="$BASENAMEFORMETRICS-$NEWRESOLVEDOUT"
    OLDBUGNOLONGERTYPEMETRICSFILE="$BASENAMEFORMETRICS-$OLDBUGNOLONGERTYPEOUT"
    NEWLYTYPEOLDBUGMETRICSFILE="$BASENAMEFORMETRICS-$NEWLYTYPEOLDBUGOUT"

    # initialize file contents
    echo "New Bugs Filed\n$DASHES" > $NEWBUGMETRICSFILE
    echo "New Bugs Re-Opened\n$DASHES"  > $NEWREOPENEDMETRICSFILE 
    echo "Bugs Still Unresolved With Status Change\n$DASHES" > $UNRESOLVEDSTATUSCHANGEDBUGMETRICSFILE
    echo "Bugs Increased in Priority\n$DASHES" > $INCREASEDPRIOMETRICSFILE
    echo "Bugs Decreased in Priority\n$DASHES" > $DECREASEDPRIOMETRICSFILE
    echo "New Bugs Fixed\n$DASHES" > $NEWFIXEDMETRICSFILE
    echo "New Bugs Closed\n$DASHES" > $NEWCLOSEDMETRICSFILE 
    echo "New Bugs Resolved (doesn't include bugs already fixed and just closed)\n$DASHES" > $NEWRESOLVEDMETRICSFILE 
    echo "Bugs Filed That Are No Longer $TYPE Bugs\n$DASHES" > $OLDBUGNOLONGERTYPEMETRICSFILE 
    echo "Bugs Filed That Became $TYPE Bugs\n$DASHES" > $NEWLYTYPEOLDBUGMETRICSFILE

    numnewbugs=0
    numreopened=0
    numunresolvedstatuschanged=0
    numprioincreased=0
    numpriodecreased=0
    numnewfixed=0
    numnewclosed=0
    numnewresolved=0
    numoldbugnolongertype=0
    numnewlytypeoldbug=0
    log_stats $BUGMETRICSFILE $TYPE

    # interpret the diffs
    while read diffchar bugid prio status 
    do
        # grab line now since we use it for each of the new metric files
        fullbugline=`egrep \^$bugid $NEWALLBUGSFILE`

        echo "$diffchar.$bugid.$prio.$status.$fullbugline"

        if [ $diffchar = "<" ]; then
            #### current line is a line in the OLD file... ####

            # check for line in new file
            newline=`egrep \^$bugid $NEWBUGSFILE`
            if [ $? -ne 0 ]; then
                # this will only happen in non-all mode
                echo "line from old file is not in new file, transitioned from $TYPE"
                numoldbugnolongertype=`expr $numoldbugnolongertype + 1`
                echo "$fullbugline" >> $OLDBUGNOLONGERTYPEMETRICSFILE
            else
                echo "in new file, count this when we process the new lines"
            fi
        else
            #### current line is a line in the NEW file ####

            # check for line in old file
            oldline=`egrep \^$bugid $OLDBUGSFILE`
            if [ $? -ne 0 ]; then
                echo "line from new file is not in old file"

                if [ "$type" = $TYPEALL ]; then
                    # all mode
                    echo "not in old all file either, new bug!"
                    numnewbugs=`expr $numnewbugs + 1`
                    echo "$fullbugline" >> $NEWBUGMETRICSFILE
                else 
                    # non-all mode
                    # check for line in old all bugs master file
                    oldline=`egrep \^$bugid $OLDALLBUGSFILE`
                    if [ $? -ne 0 ]; then
                        echo "not in old all file either, new bug!"
                        numnewbugs=`expr $numnewbugs + 1`
                        echo "$fullbugline" >> $NEWBUGMETRICSFILE
                    else
                        echo "in old all file, old bug that transitioned to type"
                        numnewlytypeoldbug=`expr $numnewlytypeoldbug + 1`
                        echo "$fullbugline" >> $NEWLYTYPEOLDBUGMETRICSFILE
                        # XXX skip the status check below in some cases?
                    fi
                fi

                # new bugs can be fix/closed...count them as such
                status_is_fixed $status
                if [ $? -eq 0 ]; then
                    echo "new status is fixed"
                    numnewfixed=`expr $numnewfixed + 1`
                    numnewresolved=`expr $numnewresolved + 1`
                    echo "$fullbugline" >> $NEWFIXEDMETRICSFILE
                    echo "$fullbugline" >> $NEWRESOLVEDMETRICSFILE
                    log_stats $BUGMETRICSFILE $TYPE
                    continue
                fi

                status_is_closed $status
                if [ $? -eq 0 ]; then
                    echo "new status is closed"
                    numnewclosed=`expr $numnewclosed + 1`
                    numnewresolved=`expr $numnewresolved + 1`
                    echo "$fullbugline" >> $NEWCLOSEDMETRICSFILE
                    echo "$fullbugline" >> $NEWRESOLVEDMETRICSFILE
                    log_stats $BUGMETRICSFILE $TYPE
                    continue
                fi

                # if we are not in dispatched state, count this new bug as a change
                status_is_dispatched $status
                if [ $? -eq 1 ]; then
                    echo "new status $status is not dispatch"
                    echo "$fullbugline (previous state 1-Dis)" >> $UNRESOLVEDSTATUSCHANGEDBUGMETRICSFILE
                    numunresolvedstatuschanged=`expr $numunresolvedstatuschanged + 1`
                    log_stats $BUGMETRICSFILE $TYPE
                    continue
                fi
             else
                echo "line from new file is in old file"
            
                # do a quick priority check...we don't diff against this
                # field because it doesn't matter for metrics, but nice
                # to report in the daily email about priority
                # increase/decrease.
                oldbugprio=`echo $oldline | cut -d" " -f $FIELDPRIO`
                newbugprio=$prio
                if [ $oldbugprio -lt $newbugprio ]; then
                    echo "old prio $oldbugprio is higher new prio $newbugprio"
                    numpriodecreased=`expr $numpriodecreased + 1`
                    echo "$fullbugline (previous prio $oldbugprio)" >> $DECREASEDPRIOMETRICSFILE
                    log_stats $BUGMETRICSFILE $TYPE
                elif [ $oldbugprio -gt $newbugprio ]; then
                    echo "old prio $oldbugprio is lower than new prio $newbugprio"
                    numprioincreased=`expr $numprioincreased + 1`
                    echo "$fullbugline (previous prio $oldbugprio)" >> $INCREASEDPRIOMETRICSFILE
                    log_stats $BUGMETRICSFILE $TYPE
                fi
            
                # we need to analyze the status diff
                oldstatus=`echo $oldline | cut -d" " -f $FIELDSTATUS`
                echo "old status $oldstatus -> new status $status"
                if [ $oldstatus = $status ]; then
                    echo "Bug $bugid and status $status of new line and oldline $oldline"
                    echo "do nothing, no diff in metrics"
                    log_stats $BUGMETRICSFILE $TYPE
                    continue
                else
                    # We know we have a status diff...
                    # first check if old status is unresolved
                    status_is_unresolved $oldstatus
                    if [ $? -eq 0 ]; then
                        echo "old $oldstatus is unresolved"

                        # check if new status is also unresolved
                        status_is_unresolved $status
                        if [ $? -eq 0 ]; then
                            echo "new status is also unresolved"
                            echo "$fullbugline (previous state $oldstatus)" >> $UNRESOLVEDSTATUSCHANGEDBUGMETRICSFILE
                            numunresolvedstatuschanged=`expr $numunresolvedstatuschanged + 1`
                            log_stats $BUGMETRICSFILE $TYPE
                            continue
                        fi

                        # status of old is unresolved and new is resolved.
                        # see which case we are in.

                        status_is_fixed $status
                        if [ $? -eq 0 ]; then
                            echo "new status is fixed"
                            numnewfixed=`expr $numnewfixed + 1`
                            numnewresolved=`expr $numnewresolved + 1`
                            echo "$fullbugline" >> $NEWFIXEDMETRICSFILE
                            echo "$fullbugline" >> $NEWRESOLVEDMETRICSFILE
                            log_stats $BUGMETRICSFILE $TYPE
                            continue
                        fi

                        status_is_closed $status
                        if [ $? -eq 0 ]; then
                            echo "new status is closed"
                            numnewclosed=`expr $numnewclosed + 1`
                            numnewresolved=`expr $numnewresolved + 1`
                            echo "$fullbugline" >> $NEWCLOSEDMETRICSFILE
                            echo "$fullbugline" >> $NEWRESOLVEDMETRICSFILE
                            log_stats $BUGMETRICSFILE $TYPE
                            continue
                        fi
                    else 
                        echo "old $oldstatus is resolved"

                        # check if new status is unresolved
                        status_is_unresolved $status
                        if [ $? -eq 0 ]; then
                            echo "new status is unresolved, re-opened..."
                            numreopened=`expr $numreopened + 1`
                            echo "$fullbugline" >> $NEWREOPENEDMETRICSFILE
                            log_stats $BUGMETRICSFILE $TYPE
                            continue
                        fi

                        # status of old is resolved and new is resolved.
                        # see which case we are in.

                        status_is_fixed $status
                        if [ $? -eq 0 ]; then
                            echo "new status is fixed but from closed...???"
                            echo "count as re-opened and newly fixed and resolved"
                            # XXX this might mess up metrics...
                            numreopened=`expr $numreopened + 1`
                            numnewfixed=`expr $numnewfixed + 1`
                            numnewresolved=`expr $numnewresolved + 1`
                            echo "$fullbugline" >> $NEWREOPENEDMETRICSFILE
                            echo "$fullbugline" >> $NEWFIXEDMETRICSFILE
                            echo "$fullbugline" >> $NEWRESOLVEDMETRICSFILE
                            log_stats $BUGMETRICSFILE $TYPE
                            continue
                        fi

                        status_is_closed $status
                        if [ $? -eq 0 ]; then
                            echo "new status is closed"
                            numnewclosed=`expr $numnewclosed + 1`
                            # was already resolved...
                            echo "$fullbugline" >> $NEWCLOSEDMETRICSFILE
                            log_stats $BUGMETRICSFILE $TYPE
                            continue
                        fi
                     fi
                fi
            fi
        fi
        log_stats $BUGMETRICSFILE $TYPE
    done < $DIFFFILE

    # generate summary
    SUMMARY=$BASENAMEFORMETRICS-summary-metrics.out
    echo "Generating summary to $SUMMARY"

    # truncate on first write, then append
    echo "Summary of $TYPE bug changes between $DATEOLD and $DATENEW" > $SUMMARY
    if [ $TYPE = $TYPEDEV ]; then
        echo "($TYPE means p1,2,3 Defect (not RFE) in dev-active subcategories, ie no test, demo, investigate, etc)" >> $SUMMARY
    elif [ $TYPE = $TYPECUR ]; then
        echo "($TYPE means all prio, all Defect/RFE in dev-active subcategories, ie no test, demo, investigate, etc)" >> $SUMMARY
    fi
    echo "$DASHES" >> $SUMMARY
    cat $BUGMETRICSFILE >> $SUMMARY
    echo >> $SUMMARY
    cat $NEWBUGMETRICSFILE >> $SUMMARY
    echo >> $SUMMARY
    cat $NEWREOPENEDMETRICSFILE >> $SUMMARY
    echo >> $SUMMARY
    cat $UNRESOLVEDSTATUSCHANGEDBUGMETRICSFILE >> $SUMMARY
    echo >> $SUMMARY
    cat $INCREASEDPRIOMETRICSFILE >> $SUMMARY
    echo >> $SUMMARY
    cat $DECREASEDPRIOMETRICSFILE >> $SUMMARY
    echo >> $SUMMARY
    cat $NEWFIXEDMETRICSFILE >> $SUMMARY
    echo >> $SUMMARY
    cat $NEWCLOSEDMETRICSFILE >> $SUMMARY
    echo >> $SUMMARY
    cat $NEWRESOLVEDMETRICSFILE >> $SUMMARY
    echo >> $SUMMARY
    if [ $TYPE != $TYPEALL ]; then
        cat $OLDBUGNOLONGERTYPEMETRICSFILE >> $SUMMARY
        echo >> $SUMMARY
        cat $NEWLYTYPEOLDBUGMETRICSFILE >> $SUMMARY
        echo >> $SUMMARY
    fi

    # program review stats
    table=`get_side_by_side $OLDBUGSTATSDIR $NEWBUGSTATSDIR "$TYPE Total Priority Count" $TYPE-$PRIOCOUNT`
    echo "$table" >> $SUMMARY
    table=`get_side_by_side $OLDBUGSTATSDIR $NEWBUGSTATSDIR "$TYPE Open Priority Count" $TYPE-$OPENPRIOCOUNT`
    echo "$table" >> $SUMMARY
    if [ $TYPE = $TYPEDEV ]; then
        table=`get_side_by_side $OLDBUGSTATSDIR $NEWBUGSTATSDIR "$TYPE Open All Priority Count (usually p4 and p5 is ignored)" $TYPE-$OPENALLPRIOCOUNT`
        echo "$table" >> $SUMMARY
    fi 
    table=`get_side_by_side $OLDBUGSTATSDIR $NEWBUGSTATSDIR "$TYPE Fixed Priority Count" $TYPE-$FIXEDPRIOCOUNT`
    echo "$table" >> $SUMMARY
    table=`get_side_by_side $OLDBUGSTATSDIR $NEWBUGSTATSDIR "$TYPE Closed Priority Count" $TYPE-$CLOSEDPRIOCOUNT`
    echo "$table" >> $SUMMARY
    table=`get_side_by_side $OLDBUGSTATSDIR $NEWBUGSTATSDIR "$TYPE Resolved Priority Count" $TYPE-$RESOLVEDPRIOCOUNT`
    echo "$table" >> $SUMMARY
    table=`get_side_by_side $OLDBUGSTATSDIR $NEWBUGSTATSDIR "$TYPE Status Count" $TYPE-$STATUSCOUNT`
    echo "$table" >> $SUMMARY
}

#
# do the calculation for each type
#
bug_metrics $DIFFFILEALLBUGS $TYPEALL
bug_metrics $DIFFFILEALLDEVBUGS $TYPEDEV
bug_metrics $DIFFFILECURBUGS $TYPECUR

currdir=`pwd`
cd $NEWBUGSTATSDIR
LASTCOMPARELINK=`pwd`/$LASTCOMPARE
cd $currdir
cd $OUTDIR
TARGETLINKDIR=`pwd`
cd $currdir
echo "Adding link--ln -s $TARGETLINKDIR $LASTCOMPARELINK"
rm -f $LASTCOMPARELINK
ln -s $TARGETLINKDIR $LASTCOMPARELINK
exit 0

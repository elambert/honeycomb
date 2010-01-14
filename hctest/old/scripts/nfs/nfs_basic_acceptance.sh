#! /bin/sh 
#
# $Id: nfs_basic_acceptance.sh 10858 2007-05-19 03:03:41Z bberndt $
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
# NFS BAT -- basic acceptance
#

#
# Initialization
#
export VIP=dev112-data
export ORIGDIR=/etc
export SOURCEDIR=/etc1
# Linux options
export MOUNTOPTIONS="-t nfs -o tcp,timeo=600"
# Solaris options
# export MOUNTOPTIONS="-F nfs -o timeo=600,proto=tcp"
export HCMNT=/mnt/hc1
export TARGETHCDIR=$HCMNT/RegularDirectory/etc1
export TARGETHCTAR=$HCMNT/RegularDirectory/etc1-tar
export SLEEPTIME=180

echo "Using the following config for NFS test"
echo "VIP=$VIP"
echo "ORIGDIR=$ORIGDIR"
echo "SOURCEDIR=$SOURCEDIR"
echo "HCMNT=$HCMNT"
echo "TARGETHCDIR=$TARGETHCDIR"
echo "TARGETHCTAR=$TARGETHCTAR"
echo "SLEEPTIME=$SLEEPTIME"
echo


#
# Cleanup old run
#
echo "Starting cleanup from previous run at `date`"
cleanupstartsecs=`date +%s`
echo "Ignore any errors from umount...this can take a while"

mount $MOUNTOPTIONS $VIP:/ $HCMNT
if [ $? -ne 0 ]; then
        echo "ERROR: mount of $VIP:/ failed"
        exit 1
fi


echo "executing rm -rf $TARGETHCDIR at `date`"
rm -rf $TARGETHCDIR 
echo "executing rm -rf $TARGETHCTAR at `date`"
rm -rf $TARGETHCTAR
echo "executing rm -rf $SOURCEDIR at `date`"
rm -rf $SOURCEDIR

echo "checking that directories are actually gone at `date`"
if [ -d $TARGETHCDIR ] || [ -d $TARGETHCTAR ] || [ -d $SOURCEDIR ]; then
        echo "ERROR: failed to clean up previous tests."
        echo "$TARGETHCDIR or $TARGETHCTAR or $SOURCEDIR still exist"
        exit 1
fi

umount $HCMNT
umount $HCMNT
umount $HCMNT
umount $HCMNT
umount $HCMNT


#
# Main test
#
echo
echo "Starting test at `date`"
startsecs=`date +%s`

echo "mounting: mount $MOUNTOPTIONS $VIP:/ $HCMNT at `date`"
mount $MOUNTOPTIONS $VIP:/ $HCMNT


#
# Verify copy
#
echo
echo "checking cp works..."
echo "executing cp -rfL $ORIGDIR $SOURCEDIR -- ignore errors about /etc/make.profile if using /etc as ORIGDIR at `date`"
cp -rfL $ORIGDIR $SOURCEDIR
echo "executing cp -rfL $SOURCEDIR $TARGETHCDIR at `date`"
cp -rfL $SOURCEDIR $TARGETHCDIR

echo "waiting for things to be committed... $SLEEPTIME seconds--is that enough? at `date`"
sleep $SLEEPTIME

echo "checking ls has same number of files"
echo "counting files in $SOURCEDIR: ls -alR $SOURCEDIR at `date`"
x=`ls -alR $SOURCEDIR |wc -l`
echo "counting files in $TARGETHCDIR: ls -alR $TARGETHCDIR at `date`"
y=`ls -alR $TARGETHCDIR | wc -l`
echo "found $x files in $SOURCEDIR and $y files in $TARGETHCDIR at `date`"
if [ $x -ne $y ]; then
        echo "ERROR: ls -alR of $SOURCEDIR and $TARGETHCDIR is $x and $y"
        echo "       maybe we did not sleep long enough?"
        exit 1
fi

echo "checking diff -r $SOURCEDIR $TARGETHCDIR at `date`"
diff -r $SOURCEDIR $TARGETHCDIR
if [ $? -ne 0 ]; then
        echo "ERROR: diff found between $SOURCEDIR $TARGETHCDIR"
        exit 1
fi


#
# Verify tar
#
echo
echo "checking tar works..."
mkdir -p $TARGETHCTAR
tar c $SOURCEDIR | tar x -C $TARGETHCTAR

echo "waiting for things to be committed... $SLEEPTIME seconds--is that enough? at `date`"
sleep $SLEEPTIME

echo "counting files in $TARGETHCTAR/$SOURCEDIR: ls -alR $TARGETHCTAR/$SOURCEDIR at `date`"
z=`ls -alR $TARGETHCTAR/$SOURCEDIR | wc -l`
echo "found $x files in $SOURCEDIR and $z files in $TARGETHCTAR/$SOURCEDIR at `date`"
if [ $x -ne $z ]; then
        echo "ERROR: ls -alR of $SOURCEDIR and $TARGETHCTAR/$SOURCEDIR is $x and $z"
        echo "       maybe we did not sleep long enough?"
        exit 1
fi

echo "checking diff -r $SOURCEDIR $TARGETHCTAR/$SOURCEDIR at `date`"
diff -r $SOURCEDIR $TARGETHCTAR/$SOURCEDIR
if [ $? -ne 0 ]; then
        echo "ERROR: diff found between $SOURCEDIR $TARGETHCTAR/$SOURCEDIR"
        exit 1
fi


#
# Verifying rm
#
echo
echo "check rm -rf removes $TARGETHCDIR and $TARGETHCTAR...this can take awhile"

echo "executing rm -rf $TARGETHCDIR at `date`"
rm -rf $TARGETHCDIR
echo "executing rm -rf $TARGETHCTAR at `date`"
rm -rf $TARGETHCTAR

echo "checking that directories are actually gone at `date`"
if [ -d $TARGETHCDIR ] || [ -d $TARGETHCTAR ]; then
        echo "ERROR: failed to clean up previous tests."
        echo "$TARGETHCDIR or $TARGETHCTAR still exist"
        exit 1
fi


#
# cleanup
#
echo "executing umount $HCMNT at `date`"
umount $HCMNT

endsecs=`date +%s`
totalsecs=`expr $endsecs - $startsecs`
totalsecswithcleanup=`expr $endsecs - $cleanupstartsecs`
echo "Finished test at `date`"
echo "Test took $totalsecs seconds -- $totalsecswithcleanup seconds including cleanup"
echo "Note that we slept for $SLEEPTIME seconds twice"
echo "PASSED!"


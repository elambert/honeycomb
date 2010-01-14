#! /bin/sh
#
# $Id: bonnie.sh 10856 2007-05-19 02:58:52Z bberndt $
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


# get a list of all the data partitions mounted and run Bonnie++ on them
# with 1MB files (default)
DATE=`date +%F-%H:%M:%S`

for fs in `mount | grep data | awk '{print $3}'`
do 
	/root/platform-tests/bonnie++ -q -d $fs -u 0 > $fs.bonnie.log.$DATE 2>&1 & 
done

# wait for them all to finish
wait

DATE=`date +%F-%H:%M:%S`
# run it all again with 1GB files
for fs in `mount | grep data | awk '{print $3}'`
do 
	/root/platform-tests/bonnie++ -q -d $fs -u 0 -s 1024 > $fs.bonnie.log.$DATE 2>&1 & 
done

# wait for them all to finish
wait

# Now run them both together ...
DATE=`date +%F-%H:%M:%S`
for fs in `mount | grep data | awk '{print $3}'`
do 
	/root/platform-tests/bonnie++ -q -d $fs -u 0 -s 1024 >> $fs.bonnie.log.large.$DATE 2>&1 & 
	/root/platform-tests/bonnie++ -q -d $fs -u 0 > $fs.bonnie.log.small.$DATE 2>&1 & 
done
wait

DATE=`date +%F-%H:%M:%S`
# coalesce the log files
LOG="/root/platform-tests/bonnie.$DATE.logs"
echo "Bonnie Logs : " > $LOG
date >> $LOG
for fl in `ls /data/*log*`
do
	echo "Log for Partition $fl :" >> $LOG
	cat $fl >> $LOG
	echo "" >> $LOG
	echo "" >>$LOG
	echo "+++++++++++++++++++++++++++++++++++++++" >> $LOG
	echo "" >> $LOG
	rm $fl
done

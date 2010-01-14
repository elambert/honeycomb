#! /bin/sh
#
# $Id: start_tests.sh 10858 2007-05-19 03:03:41Z bberndt $
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
# Starts the StoreRetrieve script on a bunch of clients
#

# make sure we get a size as arg
if [ -z "$1" ]; then
        echo "usage: $0 sizeList (space separated in quotes \"1000 2000 3000\")"
        exit 1
fi

given_size=$1

# Make sure we have a cluster config file
CONFIGFILE="./ENV"
if [ ! -f $CONFIGFILE ]; then
    echo "ERROR: failed to find cluster config file: $CONFIGFILE"
    exit 1
fi

. $CONFIGFILE

# fill in any defaults that are missing from the env file
if [ -z "$TAILLENGTH" ]; then
	TAILLENGTH=25
fi

if [ -z "$INITCHAR" ]; then
	INITCHAR=c
fi

if [ -z "$TESTPROGRAMSPERCLIENT" ]; then
	TESTPROGRAMSPERCLIENT=2
fi

if [ -z "$RESULTSBASEDIR" ]; then
	RESULTSBASEDIR=/mnt/test/test__
fi

# allow the specification of a data vip to take priority
if [ -n "$SERVER_PUBLIC_DATA_VIP" ]; then
        echo "Using data vip"
        SERVER_PUBLIC_IPS=$SERVER_PUBLIC_DATA_VIP
fi

echo Using servers $SERVER_PUBLIC_IPS
echo Using clients $CLIENTS

# for getting unique logging dirs
DATE=`date +%m.%d.%y__%H.%M.%S`

# for checking status
LOCALSTORERESULTS=./STORERESULTS__${DATE}
LOCALBEGINRETRIEVE=./BEGINRETRIEVE__${DATE}
LOCALRETRIEVERESULTS=./RETRIEVERESULTS__${DATE}
LOCALBEGINQUERIES=./BEGINQUERIES__${DATE}
LOCALQUERYRESULTS=./QUERYRESULTS__${DATE}
LOCALBEGINDELETES=./BEGINDELETES__${DATE}
LOCALDELETERESULTS=./DELETERESULTS__${DATE}

# add cmdline opts in the future for these...
TESTPROG=/opt/test/bin/StoreRetrieve

# create a unique test directory per run
RESULTSDIR=${RESULTSBASEDIR}${DATE}
UPLOAD=${RESULTSDIR}/upload

# use client load balancing
ALLSERVERS=`echo $SERVER_PUBLIC_IPS | tr " " ","`

# create the file that queries for results of stores
echo "#! /bin/sh" >> $LOCALSTORERESULTS
echo "TAILLENGTH=$TAILLENGTH" >> $LOCALSTORERESULTS
echo "echo ____________ Results in $RESULTSDIR ____________" >> $LOCALSTORERESULTS
chmod 755 $LOCALSTORERESULTS

# create the file that does retrieves 
echo "#! /bin/sh" >> $LOCALBEGINRETRIEVE
chmod 755 $LOCALBEGINRETRIEVE
echo "OTHERRETRIEVEOPTS=\"$OTHERRETRIEVEOPTS\"" >> $LOCALBEGINRETRIEVE

# create the file that queries for results of retrieves
echo "#! /bin/sh" >> $LOCALRETRIEVERESULTS
echo "TAILLENGTH=$TAILLENGTH" >> $LOCALRETRIEVERESULTS
echo "echo ____________ Results in $RESULTSDIR ____________" >> $LOCALRETRIEVERESULTS
chmod 755 $LOCALRETRIEVERESULTS

# create the file that does queries 
echo "#! /bin/sh" >> $LOCALBEGINQUERIES
chmod 755 $LOCALBEGINQUERIES
echo "OTHERQUERYOPTS=\"$OTHERQUERYOPTS\"" >> $LOCALBEGINQUERIES

# create the file that queries for results of queries
echo "#! /bin/sh" >> $LOCALQUERYRESULTS
echo "TAILLENGTH=$TAILLENGTH" >> $LOCALQUERYRESULTS
echo "echo ____________ Results in $RESULTSDIR ____________" >> $LOCALQUERYRESULTS
chmod 755 $LOCALQUERYRESULTS

# create the file that does deletes 
echo "#! /bin/sh" >> $LOCALBEGINDELETES
chmod 755 $LOCALBEGINDELETES
echo "OTHERDELETEOPTS=\"$OTHERDELETEOPTS\"" >> $LOCALBEGINDELETES

# create the file that queries for results of deletes
echo "#! /bin/sh" >> $LOCALDELETERESULTS
echo "TAILLENGTH=$TAILLENGTH" >> $LOCALDELETERESULTS
echo "echo ____________ Results in $RESULTSDIR ____________" >> $LOCALDELETERESULTS
chmod 755 $LOCALDELETERESULTS

total_launched=0

############# we now allow a sizeList and iterate through it when calling StoreRetrieve ###########
num_sizes=0
size_index=0

# compute the number of sizes in the list
for s in $given_size; do
        num_sizes=`expr $num_sizes + 1`
done

# set the size we should use for a give StoreRetrieve call
set_size() {
        cnt=0
        for s in $given_size; do
                if [ $cnt -eq $size_index ]; then
                        size_index=`expr $size_index + 1`
                        if [ $size_index -ge $num_sizes ]; then
                                size_index=0
                        fi

			# use unique sizes...
                        size=`expr $s + $total_launched`
                        return
                fi
                cnt=`expr $cnt + 1`
        done
}

for client in $CLIENTS; do
        ssh $client "mkdir -p $RESULTSDIR"
        echo "echo" >> $LOCALSTORERESULTS
        echo "echo" >> $LOCALRETRIEVERESULTS
        echo "echo" >> $LOCALQUERYRESULTS

        num_left_to_launch=$TESTPROGRAMSPERCLIENT
        while [ $num_left_to_launch -gt 0 ]; do
                # XXX why does path not pick up from the environment?
		set_size
                TESTOUT=${RESULTSDIR}/test-${size}.out
		ALLSTOREOPTS="-S -b $size -k $UPLOAD -c $INITCHAR -s $ALLSERVERS $OTHERSTOREOPTS"
                TESTCMD="PATH=/opt/test/bin:/usr/lib/java/bin:/bin:/usr/bin:/opt/client/bin:$PATH nohup $TESTPROG $ALLSTOREOPTS > $TESTOUT 2>&1 &"

		echo "" >> $LOCALSTORERESULTS
                echo "##### Command used to start test" >> $LOCALSTORERESULTS
		echo "# $TESTCMD" >> $LOCALSTORERESULTS
                echo "##### " >> $LOCALSTORERESULTS
		# start the test program on the client
                ssh $client "$TESTCMD"
                if [ $? != 0 ]; then
                        echo "Failed to start test on client $client with size $size"
                fi

		# for checking status
		echo "echo" >> $LOCALSTORERESULTS
		echo "echo" >> $LOCALSTORERESULTS
                echo "echo [======= ${client}:${TESTOUT} ===]" >> $LOCALSTORERESULTS
                echo "ssh $client tail -\$TAILLENGTH $TESTOUT" >> $LOCALSTORERESULTS

		# for doing retrieves
		RETRIEVETESTOUT=${TESTOUT}-retrieve
		echo "" >> $LOCALBEGINRETRIEVE
                echo "ARGS=\`ssh $client grep -- -R $TESTOUT | cut -d \" \" -f 2-\`" >> $LOCALBEGINRETRIEVE
		echo "ssh $client \"PATH=/opt/test/bin:/usr/lib/java/bin:/bin:/usr/bin:/opt/client/bin:$PATH nohup $TESTPROG \$ARGS \$OTHERRETRIEVEOPTS > $RETRIEVETESTOUT 2>&1 &\"" >> $LOCALBEGINRETRIEVE
		echo "echo" >> $LOCALBEGINRETRIEVE
		echo "echo \"Started retrieve on client $client with $TESTPROG \$ARGS \$OTHERRETRIEVEOPTS\"" >> $LOCALBEGINRETRIEVE
		echo "echo" >> $LOCALBEGINRETRIEVE

		# for checking status of retrieves
		echo "echo" >> $LOCALRETRIEVERESULTS
		echo "echo" >> $LOCALRETRIEVERESULTS
                echo "echo [======= ${client}:${RETRIEVETESTOUT} ===]" >> $LOCALRETRIEVERESULTS
                echo "ssh $client tail -\$TAILLENGTH $RETRIEVETESTOUT" >> $LOCALRETRIEVERESULTS

		# for doing queries
		QUERYTESTOUT=${TESTOUT}-query
		echo "" >> $LOCALBEGINQUERIES
                echo "ARGS=\`ssh $client grep -- -Q $TESTOUT | cut -d \" \" -f 2-\`" >> $LOCALBEGINQUERIES
		echo "ssh $client \"PATH=/opt/test/bin:/usr/lib/java/bin:/bin:/usr/bin:/opt/client/bin:$PATH nohup $TESTPROG \$ARGS \$OTHERQUERYOPTS > $QUERYTESTOUT 2>&1 &\"" >> $LOCALBEGINQUERIES
		echo "echo" >> $LOCALBEGINQUERIES
		echo "echo \"Started query on client $client with $TESTPROG \$ARGS \$OTHERQUERYOPTS\"" >> $LOCALBEGINQUERIES
		echo "echo" >> $LOCALBEGINQUERIES

		# for checking status of queries
		echo "echo" >> $LOCALQUERYRESULTS
		echo "echo" >> $LOCALQUERYRESULTS
                echo "echo [======= ${client}:${QUERYTESTOUT} ===]" >> $LOCALQUERYRESULTS
                echo "ssh $client tail -\$TAILLENGTH $QUERYTESTOUT" >> $LOCALQUERYRESULTS

		# for doing deletes
		DELETETESTOUT=${TESTOUT}-delete
		echo "" >> $LOCALBEGINDELETES
                echo "ARGS=\`ssh $client grep -- -D $TESTOUT | cut -d \" \" -f 2-\`" >> $LOCALBEGINDELETES
		echo "ssh $client \"PATH=/opt/test/bin:/usr/lib/java/bin:/bin:/usr/bin:/opt/client/bin:$PATH nohup $TESTPROG \$ARGS \$OTHERDLETEOPTS > $DELETETESTOUT 2>&1 &\"" >> $LOCALBEGINDELETES
		echo "echo" >> $LOCALBEGINDELETES
		echo "echo \"Started delete on client $client with $TESTPROG \$ARGS \$OTHERDELETEOPTS\"" >> $LOCALBEGINDELETES
		echo "echo" >> $LOCALBEGINDELETES

		# for checking status of deletes
		echo "echo" >> $LOCALDELETERESULTS
		echo "echo" >> $LOCALDELETERESULTS
                echo "echo [======= ${client}:${DELETETESTOUT} ===]" >> $LOCALDELETERESULTS
                echo "ssh $client tail -\$TAILLENGTH $DELETETESTOUT" >> $LOCALDELETERESULTS

		echo
                echo "Started store on client $client with size $size with $TESTPROG $ALLSTOREOPTS"
		echo
                num_left_to_launch=`expr $num_left_to_launch - 1`
		total_launched=`expr $total_launched + 1`
        done
done

echo "Check status by running $LOCALSTORERESULTS"
echo "Do retrieves by running $LOCALBEGINRETRIEVE"
echo "Check status of retrieves by running $LOCALRETRIEVERESULTS"
echo "Do queries by running $LOCALBEGINQUERIES"
echo "Check status of queries by running $LOCALQUERYRESULTS"
echo "Do deletes by running $LOCALBEGINDELETES"
echo "Check status of deletes by running $LOCALDELETERESULTS"


#! /bin/sh
#
# $Id: run.sh 10856 2007-05-19 02:58:52Z bberndt $
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


# DO NOT MODIFY BELOW THIS LINE!!!!

# DEFAULTS for things that should have them
LOG_LOC=$HOME
NODES_FILE="NODES"

# get options from command line, or drop into interactive mode
while [ "$#" -gt 0 ]
do
        case $1 in 

		-a)
			# do it all with no questions
                        do_disk="true"
			disk_init="true"
			do_bonnie="true"
			do_smart="true"
			do_net="true"
			do_exhaustive_net="true"
			args="$args -b -e -i -s -d -n"
			break
			;;	
		-b)
			do_bonnie="true"
			args="$args -b"
			;;
		-B)
			do_bonnie="false"
			args="$args -B"
			;;
		-i)
			disk_init="true"
			args="$args -i"
			;;
		-I)
			disk_init="false"
			args="$args -I"
			;;
                -d) 
                        do_disk="true"
			disk_init="true"
			do_bonnie="true"
			do_smart="true"
                        args="$args -d"
                        ;;
                -D)
                        do_disk="false"
			do_bonnie="false"
			disk_init="false"
			do_smart="false"
                        args="$args -D"
                        ;;
		-e)
			do_exhaustive_net="true"
			args="$args -e"
			;;
		-E)
			do_exhaustive_net="false"
			args="$args -E"
			;;
                -n)
                        do_net="true"
                        args="$args -n"
                        ;;
                -N)     
                        do_net="false"
                        args="$args -N"
                        ;;
                -s)     
                        do_smart="true"
                        args="$args -s"
                        ;;
                -S)     
                        do_smart="false"
                        args="$args -S"
                        ;;
                -f)
                        NODES_FILE=$2
                        shift
                        ;;
                -l)
                        LOG_FILES=$2
                        shift
                        ;;
		-h)
			HOST_FILE=$2
			shift
			;;
		-t)
			DURATION=$2
			shift
			;;
                *)
			echo ""
                        echo "Usage: `basename $0` [-d|D] [-n|N] [-s|S]  [-f NODE_FILE] [-l LOG_LOCATION] [-t TEST_DURATION]"
			echo "-a flag turns on ALL tests"
			echo "-b flag turns ON Bonnie++ tests"
			echo "-B flag turns OFF Bonnie++ tests"
                        echo "-d flag turns ON ALL disk tests"
                        echo "-D flag turns OFF ALL disk tests"
			echo "-i flag turns ON disk initialization"
			echo "-I flag turns OFF disk initialazation"
                        echo "-n flag turns ON Network tests"
                        echo "-N flag turns OFF Network tests"
                        echo "-s flag turns ON SMART Disk tests"
                        echo "-S flag turns OFF SMART Disk tests"
                        echo "No arguments forces interactive mode"
                        echo "-f flag defines NODE_FILE as the file with IP addresses in it"
                        echo "NODE_FILE defaults to ./NODES if not given"
                        echo "-h flag defines HOST_FILE as the file with Host Names in it"
                        echo "-l LOG_LOCATION defines the location to save the log files"
                        echo "Defaults to \$HOME "
			echo "-t TEST_DURATION is the number of HOURS the to run the tests."
                        echo ""
			echo "NOTE: Using the -d or -D flag with other disk-test flags will"
			echo "produce unexpected results, depending on the order of the flags."
			echo "You have been warned."
			echo ""
                        exit 0
                        ;;
	esac
	shift
done

# interactive mode stuff ...
if [ ! -n "$do_disk" ]
then
# confusing as it may be, do_disk from the CLI turns on/off ALL disk tests, but here
# it just tells us if we need to ask more questions. Deal with it.
	while [ true ]
        do
                echo -n "Include Disk Tests? [y|n]: "
                read input
                case $input in
                        [Y,y])
                                do_disk="true"
                                break
                                ;;
                        [N,n])
                                do_disk="false"
				args="$args -D"
                                break
                                ;;
                        *)
                                echo "Please enter y or n"
                                ;;
                esac
        done
fi
if [ $do_disk == "true" ]
then
	if [ ! -n "$disk_init" ]
	then
		while [ true ]
        	do
                	echo -n "     Initialize Disks? [y|n]: "
                	read input
                	case $input in
                        	[Y,y])
                                	disk_init="true"
                                	args="$args -i"
                                	break
                                	;;
                        	[N,n])
                                	disk_init="false"
                                	args="$args -I"
                                	break
                                	;;
                        	*)
                                	echo "     Please enter y or n"
                                	;;
                	esac
        	done
	fi
	if [ ! -n "$do_bonnie" ]
	then
		while [ true ]
        	do
                	echo -n "     Run Bonnie++ tests? [y|n]: "
                	read input
                	case $input in
                        	[Y,y])
                                	do_bonnie="true"
                                	args="$args -b"
                                	break
                                	;;
                        	[N,n])
                                	do_bonnie="false"
                                	args="$args -B"
                                	break
                                	;;
                        	*)
                                	echo "     Please enter y or n"
                                	;;
                	esac
        	done
	fi

	if [ ! -n "$do_smart" ]
	then
		while [ true ]
        	do
                	echo -n "     Include SMART Disk Tests? [y|n]: "
                	read input
                	case $input in
                        	[Y,y])
                                	do_smart="true"
                                	args="$args -s"
                                	break
                                	;;
                        	[N,n])
                                	do_smart="false"
                                	args="$args -S"
                                	break
                                	;;
                        	*)
                                	echo "     Please enter y or n"
                                	;;
                	esac
        	done
	fi
fi
if [ ! -n "$do_net" ]
then
	while [ true ]
        do
                echo -n "Include Network Tests? [y|n]: "
                read input
                case $input in
                        [Y,y])
                                do_net="true"
                                args="$args -n"
                                break
                                ;;
                        [N,n])
                                do_net="false"
                                args="$args -N"
                                break
                                ;;
                        *)
                                echo "Please enter y or n"
                                ;;
                esac
        done
fi
if [ $do_net == "true" ]
then
	if [ ! -n "$do_exhaustive_net" ]
	then
		while [ true ]
        	do
                	echo -n "     Include Exhaustive Network Tests? [y|n]: "
                	read input
                	case $input in
                        	[Y,y])
                                	do_exhaustive_net="true"
                                	args="$args -e"
                                	break
                                	;;
                        	[N,n])
                                	do_exhaustive_net="false"
                                	args="$args -E"
                                	break
                                	;;
                        	*)
                                	echo "     Please enter y or n"
                                	;;
                	esac
        	done
	fi	
fi
if [ ! -n "$DURATION" ]
then
	while [ true ]
	do
		echo -n "Enter test duration in HOURS: "
		read input
		case $input in
		
		[.[1-9][0-9]*)
			DURATION=$input
			break
			;;
		[0-9])
			DURATION=$input
			break
			;;

		*)
			echo "Time must be a numeric value ONLY!"
			;;
		esac
	done
fi
echo -n "Current Time: "
date

# extract current day, date, month, etc. for future use ... timing.
mo=`date +%b`
day=`date +%e | awk '{print $1}'`
hr=`date +%k | awk '{print $1}'`
min=`date +%M`
sex=`date +%S`


# Months of the year. for reference later
mos="Jan Feb Mar Apr Jun Jul Aug Sep Oct Nov Dec"

# test duration conversion from hours to seconds
DUR_SECS=$(($DURATION * 3600))

# Number of days in months, in case test runs past the end of a month
case $mo in
	["Jan" | "Mar" | "May" | "Jul" | "Aug" | "Oct" | "Dec"])
		max=31
		;;
	"Feb")
		max=28
		;;
	["Apr" | "Jun" | "Sep" | "Nov"])
		max=30
		;;
esac

# in case the test runs past the end of the month ...
new_hr=$(($hr + $DURATION))
echo -n "Running for $DURATION hours ... "

if [ $new_hr -ge 24 ]
then
	new_hr=$(($new_hr - 24))
	day=$(($day + 1))
	if [ $day -gt $max ]
	then
		next=false
		day=1
		for m in $mos
		do
			if [ $next = true ]
			then
				mo=$m
				break
			fi
			if [ $mo = $m ]
			then
				next=true	
			fi
		done
	fi
fi
echo "Until approximately $mo $day $new_hr:$min"
echo "Depending on sub-script completion times, of course :-)"
echo ""
if [ $DURATION -gt 0 ]
then
	args="$args -q"
	BASE_LOG_DIR="$LOG_LOC/CONSOLIDATED-Logs-$hr-$new_hr-$min-$mo"
	echo "LOG_DIR: $BASE_LOG_DIR"
	mkdir $BASE_LOG_DIR
else
	LOG_DIR=$LOG_LOC/LOGS-$hr-$new_hr-$min-$mo
	echo "LOG_DIR: $LOG_DIR"
	mkdir $LOG_DIR
fi

# make a directory to keepthe log files for THIS run only

# if we're not using a hosts file, use the NODES_file as the host file
if [ ! -n "$HOST_FILE" ]
then
	HOST_FILE=$NODES_FILE
fi
# final additions to the arguments before we get going
args="$args -f $NODES_FILE -h $HOST_FILE "

# count the times through the loop
loop=1
base_args=$args

# run forever, or close to it
while true
do

	# this just makes it talk pretty
	echo -n "Running loop $loop"
	if [[ (`echo $loop | grep 11$`) || (`echo $loop | grep 12$`) ||  (`echo $loop | grep 13$`) ]]
	then  
			echo -n "th"
	elif [ `echo $loop | grep 1$` ]
	then
			echo -n "st"
	elif [ `echo $loop | grep 2$` ]
	then
			echo -n "nd"
	elif [ `echo $loop | grep 3$` ]
	then
			echo -n "rd"
	else
			echo -n "th"
	fi
	echo " time ..."
	if [ $DURATION -gt 0 ]
	then
		LOG_DIR="$BASE_LOG_DIR/LOOP-$loop"
		mkdir $LOG_DIR
	fi
	args="$base_args -l $LOG_DIR"
	loop=$((loop + 1))
	
	echo "Executing ./platform.sh $args ..."
	
	# here's where the work happens. All the rest is just window dressing
	./platform.sh $args
	# test for duration and end if we're over our time ($SECONDS is a shell variable)
	if [ $SECONDS -gt $DUR_SECS ]
	then
			echo "Testing Complete!"
			break
	fi
done
 

# spit out some results
echo -n "PLATFORM testing complete! Finished: "
date

#!/bin/bash

# This is a log-scraper script to get DataDoctor performance. It
# prints a table of inserts, retries and failures by time.
#
# $Id: mdinsert-stats.sh 11436 2007-08-28 19:38:05Z sm152240 $
#
# Copyright 2007 Sun Microsystems
# All Rights Reserved

# Default value
FREQ=1

PROGNAME="$0"
function usage() {
    echo "Error!" "$@"
    cat <<-EOF
	Usage: $PROGNAME [-F freq] < [log-file]
	Options:
	    -F : frequency, how many reports per hour (default = ${FREQ})
EOF
    exit 1
}

function handle_args() {
    while getopts "F:h" opt; do
        case "$opt" in
        F) FREQ="$OPTARG"
           ;;

        h) usage
           ;;

        *) usage "Option $opt unknown." ;;
        esac
    done
}

########################################################################
# main starts here

# Command-line arguments
handle_args "$@"
shift $(($OPTIND - 1))

INTERVAL="$((60/FREQ))"

# Either gawk or nawk will work here
gawk -v "INTERVAL=$INTERVAL"  '
    BEGIN {
        months["Jan"] = "01"
        months["Feb"] = "02"
        months["Mar"] = "03"
        months["Apr"] = "04"
        months["May"] = "05"
        months["Jun"] = "06"
        months["Jul"] = "07"
        months["Aug"] = "08"
        months["Sep"] = "09"
        months["Oct"] = "10"
        months["Nov"] = "11"
        months["Dec"] = "12"
    }

    # Construct a timestamp from the values in the log line
    function get_ts(month, day, sec) {
        if (length(day) < 2)
            day = "0" day;
        split(sec, tod, ":")
        minutes = int(tod[2]/INTERVAL) * INTERVAL
        if (length(minutes) < 2)
            minutes = "0" minutes
        return months[month] "/" day " " tod[1] ":" minutes
    }

    # Main loop

    /PopulateExtCache.* metadata oid .* not found .*, inserting/ {
        ts = get_ts($1, $2, $3)
        timestamps[ts] = "y"
        dd_inserts[ts] += 1
    }
    / MEAS (addmd|store_b) / {
        ts = get_ts($1, $2, $3)
        timestamps[ts] = "y"
        api_inserts[ts] += 1
    }
    /HCGlue.storeFile.* MDCREATE / {
        ts = get_ts($1, $2, $3)
        timestamps[ts] = "y"
        webdav_inserts[ts] += 1
    }
    /PopulateExtCache.* Metadata operation failed/ {
        ts = get_ts($1, $2, $3)
        timestamps[ts] = "y"
        dd_failures[ts] += 1
    }
    /HADBHook.setMetadata.* setMetadata failed for oid / {
        ts = get_ts($1, $2, $3)
        timestamps[ts] = "y"
        failures[ts] += 1
    }
    / WARNING .*\[RetryableCode.retryCall\].* Got a retryable exception\. / {
        ts = get_ts($1, $2, $3)
        timestamps[ts] = "y"
        retries[ts] += 1
    }

    # Print results
    END {
        print "# Time       Ins: DD      API   WebDAV  Retries Failures"
        for (ts in timestamps)
            printf("%11s   %6d   %6d   %6d   %6d   %6d\n",
                   ts, dd_inserts[ts], api_inserts[ts], webdav_inserts[ts],
                   retries[ts], failures[ts])
    }
' "$@"

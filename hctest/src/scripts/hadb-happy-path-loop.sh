#!/bin/bash

# A script to exercise HADB startup and shutdown (including wipe)
# repeatedly. The QA schema file (default: ./metadata_config.xml) MUST
# exist. It uses WebDAV (multiload) to provide the load, so the
# multiload executable "./multiload" MUST exist. Progress is logged to
# syslog in a manner compatible with Honeycomb log messages, with the
# tag "HHPLS" (HADB Happy Path Loop Script).
#
# This script needs to run from the cheat node because it logs to
# syslog. This is a useful grep line for /var/adm/messages to watch
# the progress of this test and the state machine:
#
#     (gzcat messages.{15..2}.gz; cat messages.[10] messages) | \
#          egrep 'WIPE|Hadbm|MasterService|HadbService|HHPLS' | \
#          egrep -v 'MAConnectionFactory.getMAConnection' | prettify-log
#
# Where "prettify-log" is a simple gawk script:
#   gawk '{
#      printf("%s %s %s %s %s ", $1, $2, $3, substr($4,0,6), substr($10, 0, 1))
#      printf("%-25s ", substr(gensub("[\] \[]", "", "g", $11), 0, 25))
#      printf("%s\n", gensub("^.* [(][0-9]+\.[0-9]+[)] ", "", "g"))
#   }'
#
# This is the outline of the loop:
#
#       load schema
#       repeat {
#           multiload PUT
# 
#           reboot -F
#           reboot all nodes except one or two
#           wait 15 minutes
#           reboot late node
# 
#           multiload GET (same URLs as earlier PUT)
# 
#           with probability 0.3 {
#               wipe
#               load schema
#           }
#       } 
#
# Usage:
#     $0 [-N num-cluster-nodes] [-S schema-file] [-n iterations] cluster-name
# (The help printed by the usage() function is definitive. Run the
# script without arguments to get the help.)
#
# Shamim Mohamed June/July 2007
#
# Copyright 2007 Sun Microsystems Inc.
# $Id: hadb-happy-path-loop.sh 11435 2007-08-28 19:23:05Z sm152240 $

NUM_NODES=16
SCHEMA_FILE="metadata_config.xml"

TAG="HHPLS"
TMPFILE="/var/tmp/${TAG}$$.tmp"
LOGSTR="0 INFO [${TAG}] (0.0) "
NUM_ITERATIONS=1000000
MULTILOAD="./multiload"

PROGNAME="$0"
CLUSTER=

function usage() {
    echo "Error!" "$@"
    cat <<-EOF
	Usage: $PROGNAME [-N num-nodes] [-S schema-file] [-n num-iterations] cluster
	   -N : number of nodes in cluster (default: 16)
	   -S : schema file to use (default: metadata_config.xml)
	   -n : number of iterations (default: unlimited)
EOF
    exit 1
}

function handle_args() {
    while getopts "N:S:n:" opt; do
        case "$opt" in
        N) NUM_NODES="$OPTARG" ;;

        S) SCHEMA_FILE="$OPTARG" ;;

        n) NUM_ITERATIONS="$OPTARG" ;;

        *) usage "Option $opt unknown." ;;
        esac
    done
    shift $(($OPTIND - 1))

    CLUSTER="$1"
    if [ -z "$CLUSTER" ]; then
        usage "Cluster name not specified!"
    fi
}

function log() {
    logger -p local0.info "0 INFO [${TAG}.${ITERATION}] (0.0)" "$@"
}

function get_domain() {
    ssh "$1" 'echo admin | hadbm status honeycomb --nodes 2>/dev/null' | \
        sed '1s/^.*: //'
}

function log_stdin() {
    while read LINE; do
        log "$LINE"
    done
}

function get_hadb_status() {
    echo -y | ssh "admin@${CLUSTER}-admin" hadb status -F | tail -1
}

function wait_hadb() {
    if [ -n "$1" ]; then
        PATTERN="^$1$"
    else
        PATTERN="^(HA)?FaultTolerant|Operational$"
    fi

    while :; do
        STATUS=$(get_hadb_status)
        if echo "$STATUS" | egrep -s "$PATTERN"; then
            log "HADB state is ${STATUS}."
            return 0
        else
            log "HADB state is ${STATUS}; waiting for $PATTERN..."
        fi
        sleep 180
    done
}

########################################################################
# main starts here

# Command-line arguments
handle_args "$@"

# Error-check arguments

if [ ! -f "$SCHEMA_FILE" ]; then
    usage "The schema file $SCHEMA_FILE does not exist!"
fi

if [ "16" != "$((NUM_NODES))" -a "8" != "$((NUM_NODES))" ]; then
    usage "Number of nodes in the cluster (${NUM_NODES}) must be 8 or 16"
fi

if ! ping "${CLUSTER}-data" || ! ping "${CLUSTER}-admin"; then
    usage "Couldn't ping ${CLUSTER}-data or ${CLUSTER}-admin -- is DNS set up?"
fi

URL="http://${CLUSTER}-data:8080/webdav/epochAlpha/{0-99999}/file{0-999}/A{0-99}/B{0-99}/C{0-99}.txt"

# The range of $RANDOM is 0..32767
RSCALE=$((32768/NUM_NODES))

# Number of simultaneous connections to make
NTHREADS="$((12*NUM_NODES))"

# Make sure the cluster has norebooot but not nohoneycomb
for ((j = 0; j < NUM_NODES; j++)); do
    NODE="hcb$((101 + j))"
    ssh "$NODE" rm /config/nohoneycomb
    ssh "$NODE" touch /config/noreboot
done

# Here we go!
for ((ITERATION = 0; ITERATION < NUM_ITERATIONS; ITERATION++)); do
    echo -e "\n"
    date
    echo -e "Starting iteration $ITERATION\n"
    log "******** Iteration $ITERATION ********"

    log "Making sure HADB is operational..."
    wait_hadb

    #  Wipe with p = 3/13 (approx. 0.23)
    if [ "$((RANDOM % 13))" -gt 2 ]; then
        log "NOT wiping."
    else
        log "Wiping."

        # Wipe
        ssh "admin@${CLUSTER}-admin" wipe -F

        log "Waiting for HAFaultTolerant after wipe..."
        wait_hadb HAFaultTolerant

        log "Loading schema."

        # Load schema
        ssh "admin@${CLUSTER}-admin" mdconfig -a < "$SCHEMA_FILE"
    fi

    log "Starting PUTs."

    SEED="happypath.$RANDOM.$RANDOM"

    # Store for 10 minutes (128 simultaneous connections)
    "$MULTILOAD" -S "$SEED" -l 1023 -t 600 -c "$NTHREADS" "$URL" | log_stdin
    sleep 10

    log "Shutting down Honeycomb."

    # Shutdown
    ssh "admin@${CLUSTER}-admin" reboot -F

    log "Waiting 5 minutes for Honeycomb shutdown..."
    sleep 300

    # Re-start

    # On a 16-node cluster, we "fail" two nodes; on an 8-node, only one.
    # These node(s) will start after everyone else.
    BADNODE1="$((RANDOM/RSCALE))"
    if [ ".$NUM_NODES" = ".16" ]; then
        BADNODE2="$BADNODE1"
        while [ ".$BADNODE1" = ".$BADNODE2" ]; do
            BADNODE2="$((RANDOM/RSCALE))"
        done
    else
        BADNODE2="666"
    fi

    for ((j = 0; j < NUM_NODES; j++)); do
        NODE="hcb$((101 + j))"

        if [ "$j" -ne "$BADNODE1" -a "$j" -ne "$BADNODE2" ]; then
            log "Rebooting ${NODE}."
            ssh "$NODE" reboot &
            GOODNODE="$NODE"
        else
            log "NOT rebooting $NODE yet." 
       fi
    done
    wait

    BADNODE1="hcb$((101 + BADNODE1))"

    log "Waiting 30 minutes for cluster start..."
    sleep 1800

    get_domain "$GOODNODE" | log_stdin

    log "HADB status is $(get_hadb_status); $BADNODE1 starting."
    ssh "$BADNODE1" reboot &

    if [ ".$NUM_NODES" = ".16" ]; then
        log "Waiting 10 more minutes to start the second late node...."
        sleep 600
        get_domain "$GOODNODE" | log_stdin

        BADNODE2="hcb$((101 + BADNODE2))"
        log "HADB status is $(get_hadb_status); $BADNODE2 starting."
        ssh "$BADNODE2" reboot &
    fi

    log "Waiting for HADB to become operational..."
    wait_hadb

    log "Starting GETs."

    # Query: 5 minutes with the same seed as was used for PUTs
    "$MULTILOAD" -O GET -S "$SEED" -l 1023 -t 300 -c "$NTHREADS" "$URL" | log_stdin

    log "Sleeping 15 minutes to let node(s) join the domain..."
    sleep 900

    get_domain "$GOODNODE" | log_stdin

done

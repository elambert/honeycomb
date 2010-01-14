#!/bin/sh
#
# $Id: readTest.sh 3495 2005-02-08 21:22:51Z wr152514 $

# A test that reads files over NFS repeatedly and does lots of
# mounts and unmounts in between

# Instructions:
# Edit DATA_VIP, MOUNT_POINT, RESULTS_DIR and VIEW_DIR
# Make sure you can ping DATA_VIP, that RESULTS_DIR, MOUNT_POINT exist
# and that VIEW_DIR is a view that has been defined in your
# cluster configuration
#
DATA_VIP="dev103-data"
MOUNT_POINT="/mnt/hc"
RESULTS_DIR="/hc/results"
VIEW_DIR="lengthAlphaOid/2/X"
#
# End of setup

LAST="${VIEW_DIR##*/}"

umount "$MOUNT_POINT" >/dev/null 2>&1

NUMBER_OF_RUNS=400
N_ITERATIONS=25

PASS=0
while [ "$PASS" -lt "$NUMBER_OF_RUNS" ]; do
    echo "mount -t nfs -o tcp,timeo=600 ${DATA_VIP}:/ $MOUNT_POINT"
    if ! mount -t nfs -o tcp,timeo=600 "${DATA_VIP}:/" "$MOUNT_POINT"; then
	echo "Couldn't mount!" >&2
        exit 1
    fi

    ITER=0
    while [ "$ITER" -lt "$N_ITERATIONS" ]; do
        echo -n "----- Test run $PASS, loop $ITER : "; date

	echo "rm -rf $RESULTS_DIR; mkdir -p $RESULTS_DIR"
	rm -rf "$RESULTS_DIR"; mkdir -p "$RESULTS_DIR"

        echo cp -R "${MOUNT_POINT}/${VIEW_DIR}" "$RESULTS_DIR"
        cp -R "${MOUNT_POINT}/${VIEW_DIR}" "$RESULTS_DIR"
	date
        echo diff -r "${RESULTS_DIR}/${LAST}" "${MOUNT_POINT}/${VIEW_DIR}"
	diff -r "${RESULTS_DIR}/${LAST}" "${MOUNT_POINT}/${VIEW_DIR}"

	echo -n "Number of files: "; find "$RESULTS_DIR" -type f -print | wc -l

        date; echo
	ITER=`expr $ITER + 1`
    done

    echo umount $MOUNT_POINT
    umount $MOUNT_POINT || echo "Couldn't unmount!" >&2
    PASS=`expr $PASS + 1`
    sleep 60
done

exit 0


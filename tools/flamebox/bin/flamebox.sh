#!/bin/bash

# A simple wrapper script for flamebox-client.
# specify the tasks.  it will run them in sequence.
# then repeat, unless /tmp/flamebox-halt is present.

TASKS=$*;

WHEREAMI=`cd \`dirname $0\`; pwd`;
FLAMEBOX_CLIENT=$WHEREAMI/flamebox-client.pl
while [ ! -e /tmp/flamebox-halt ]
do
  CMD="$FLAMEBOX_CLIENT --once $TASKS"
  echo $CMD
  $CMD
done

echo "found /tmp/flamebox-halt"
echo "exiting"

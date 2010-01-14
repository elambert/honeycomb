#!/bin/sh

GATEWAY=`grep 'honeycomb.cell.vip_gateway' /config/config.properties | cut -d= -f 2`

SLEEPTIME=10

while :
do
  if ps -Aef | grep 'DMASTER-SERVERS' > /dev/null
  then
    ping $GATEWAY > /dev/null 2>&1
  fi
  sleep $SLEEPTIME
done

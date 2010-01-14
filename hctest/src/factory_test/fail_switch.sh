#!/usr/bin/bash
usage() {
   echo "Usage: $0 -failover [p | s |sp] -p [Optional]"
   echo "       -failover p => fail primary switch"
   echo "       -failover s => fail secondary switch"
   echo "       -failover sp => fail both switches" 
   echo "       -p => re-program switch priority"  
   exit 1
}

run() {
   echo 
   echo "RUN ---> $*"
   echo
   $*
   if [ $? -ne 0 ]; then
      echo "Script encountered an error...exiting"
      exit 1
   fi
}
 
if [ $# -lt 1 ]; then
  usage
fi

case $1 in
   -failover )
   fail=$2
   option=" "
   ;;
   -p )
   option=$1
   ;;
   * )
   usage
   ;;
esac

switchip=10.123.45.1
myrsh="eval ssh -p 2222 -o StrictHostKeyChecking=no nopasswd@\$switchip"
switchdir='/etc/rcZ.d/surviving_partner'
# Verify switch connectivity 
ping $switchip
if [ $? -ne 0 ]; then
   echo "Unable to reach the switch ip: $switchip"
   exit 1
fi

# Re-program flash with new switch priority settings 
if [ $option ] && [ $option == "-p" ]; then
   echo "Re-programming the flash with new switch settings"
   run $myrsh "sed 's/-p 254/-p 253/g' $switchdir/vrrpd.conf" ">" $switchdir/vrrpd.conf.tmp 
   run $myrsh "mv $switchdir/vrrpd.conf.tmp $switchdir/vrrpd.conf"
   run $myrsh "sed 's/priority=254/priority=253/g' $switchdir/zlmd.script" ">" $switchdir/zlmd.script.tmp 
   run $myrsh "mv $switchdir/zlmd.script.tmp $switchdir/zlmd.script"
   run $myrsh "cd $switchdir; zsync -f"
   $myrsh "reboot"
   exit 0 
fi 

# Perform the switch failover
case $fail in
   p)
   echo "Performing primary switch failover" 
   $myrsh "reboot" 2>&1 1>>/dev/null     
   ;; 
   s) 
   echo "Performing secondary switch failover" 
   $myrsh run_cmd_other.sh "reboot" 
   ;;
   sp)    
   echo "Performing primary & secondary switch failover" 
   $myrsh run_cmd_other.sh "reboot"
   $myrsh "reboot" 2>&1 1>>/dev/null     
   ;;
   *)
   usage 
   ;;
esac

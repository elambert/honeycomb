#!/usr/bin/bash

###
# Tool for platform test. Runs tests to validate memory, devices, network and file system
###

if [ $# -lt 2 ]; then
   echo "$0 <0=install pkgs |1=dont install pkgs> <target host>"
   exit 1
fi

host=$2
SSHARGS="-q -o StrictHostKeyChecking=no"
interfaces=`ssh $SSHARGS $host ifconfig -a | grep UP | grep : | awk {'print $1'}`

if [ $1 -eq 0 ]; then

echo "scp SUNWvts into `hostname`"
tars="SUNWvts_VTS6.1_x86.tar SUNWvtsmn_VTS6.1_x86.tar SUNWvtsts_VTS6.1_x86.tar"
for tar in $tars
do
   scp $SSHARGS /tmp/$tar root@$host:/tmp/
   ssh $SSHARGS $host "cd /tmp; tar -xvf $tar" 
done

echo "install SUNWvts pkgs"
pkgs="SUNWvts SUNWvtsmn SUNWvtsts"
for pkg in $pkgs
do
   ssh $SSHARGS $host "cd /tmp; pkgadd -d . $pkg" 
   if [ $? -eq 0 ]; then
      echo "package $pkg successful"
   else
      echo "package $pkg FAILED"
      exit 1 
   fi  
done

echo "scp iozone into this $host"  
scp $SSHARGS /tmp/iozone root@$host:/tmp/ 

fi

### Run Platform Tests ####

### DiskTest ###
echo "Run disktest"
devices=`ssh $SSHARGS $host df -kh | grep /dev/dsk | awk {'print $1'}`
for device in $devices
do
   dev=`echo $device | awk -F/ {'print $4'} | awk -Fs {'print $1'}`
   partition=`echo $device | awk -F/ {'print $4'} | awk -Fs {'print $2'}`
   ssh $SSHARGS $host "cd /opt/SUNWvts/bin/;./disktest -sf -o dev=$dev,rawsub=Enable,rawrw=Readonly,method=AsyncIO,rawcover=100MB,partition=$partition"
   if [ $? -eq 0 ]; then
      echo "disktest AsyncIO,Read PASSED for device $device"
   else
      echo "disktest AsyncIO,Read FAILED for device $device"
   fi
done

### Physical Memory Test ###
echo "Run pmemtest"
ssh $SSHARGS $host "cd /opt/SUNWvts/bin/;./pmemtest -p 99 -sf -o size=0,section=-1,dev=mem" 
if [ $? -eq 0 ]; then
   echo "pmemtest PASSED"
else
   echo "pmemtest FAILED"
fi

### Network Test ###
echo "Run nettest"
for interface in $interfaces
do
   interface=`echo $interface | sed 's/\(.*\):/\1/g'`
   ip=`ssh $SSHARGS $host ifconfig $interface | grep inet | awk {'print $2'}`   
   if [ $ip == "0.0.0.0" ] || [ $interface == "lo0" ]; then
      continue
   fi  
   ssh $SSHARGS $host "cd /opt/SUNWvts/bin/;./nettest -p 99 -f -o dev=$interface"   
   if [ $? -eq 0 ]; then
      echo "nettest PASSED on interface $interface "
   else
      echo "nettest FAILED on interface $interface"
   fi
done

### File System Test ###
echo "Run iozone test"
ssh $SSHARGS $host "cd /tmp; ./iozone -Ra -g 100M -i 0 -i 1 2>&1 1>>/dev/null" 
if [ $? -eq 0 ]; then
   echo "iozone PASSED"
else
   echo "iozone FAILED"
fi

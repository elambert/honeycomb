#!/bin/bash
#
# $Id: install.sh 11349 2007-08-13 21:49:33Z ks202890 $
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

# Only edit this script in the repository
# under honeycomb/tools
#
# usage: install cluster [ pkgdir installtype ] 
#
# "pkgadd" means just add the pkgs on top of existing install 
# "bootnetinstall" meaning re-image off the cheat.  
#
# CAVEATS: 
# 1.  You cannot use this script for the first install.
# This script assumes the setup_install_server script has been
# run once.  If the cluster.conf file has changed, must re-run
# setup_install_server by hand to pick up new properties in
# the base file.  Similarly, you can't use this script to downgrade
# a system because the conf files might be incompatible.
#
# 2.  Changes made to the cheat node image outside of the SP pkg
# must be made manually or by re-installing the cheat.
# You can use the bootnetinstall option to do a full re-image of the nodes.
#
# 3.  This script does not wipe the data on the cluster, so if 
# there is a data format change, that must be handled manually.
#
# 4.  For pkgadd, the default is not to reboot after install, which you
# can override by setting REBOOT_AFTER_INSTALL=1 in common.sh
#
# 5.  This script must be able to find an ssh key to access the cluster.
# It will look in the workspace if possible, and copy it to a tmp location
# with the right perms. 
#
# 6.  The cheat node must have the /utils/ENV file configured correctly.
# If it's not, this script will try to copy the necessary files for you.
#
# 7. Boot net install doesn't always come back reliably due to the known
# DHCP flakiness issues + HON HW flakiness.
#
# 8.  If you don't want to install HADB, set INSTALLHADB=0 in common.sh
#
# example: install.sh dev331-cheat /export/home/sarahg/svn/build/automated-build-2005-09-14-1548/build/pkgdir bootnetinstall
# example: install.sh dev331-cheat /export/release/repository/releases/Dev/Dev-21/AUTOBUILT/pkgdir pkgadd
#

print_usage() {
    echo
    echo "usage: cluster [ pkgdir installtype ]"
    echo
    echo "  cluster     - remote system to be installed, e.g. dev321"
    echo "  pkgdir      - defaults to the build/pkgdir in this svn tree"
    echo "  installtype - pkgadd (default), bootnet or ramdisk"
    echo
}

ROOT=`dirname $0`
if [ "$ROOT" = "." ]; then
    ROOT=`pwd`
fi

. $ROOT/common.sh

CLUSTER=$1
CHEAT=$CLUSTER-cheat
PKGDIR=$2
if [ ! -z "$3" ]; then
    INSTALLTYPE=$3
elif [ -z "$INSTALLTYPE" ]; then
    INSTALLTYPE=$DEFAULTINSTALLTYPE
fi

if [ -z "$CLUSTER" ]; then
    print_usage
    exit 1
fi

if [ -z "$PKGDIR" ]; then
    PKGDIR=$DEFAULTPKGDIR
fi
if [ ! -d "$PKGDIR" ]; then
    echo "pkgdir not found: $PKGDIR"
    exit 1
fi
if [ ! -d "$PKGDIR/SUNWhcsp" ]; then
    echo "SUNWhcsp not found in pkgdir $PKGDIR"
    exit 1
fi
if [ ! -d "$PKGDIR/SUNWhcextractor" ]; then
    echo "SUNWhcextractor not found in pkgdir $PKGDIR"
    exit 1
fi

if [ "$INSTALLTYPE" != "$RAMDISKINSTALLTYPE" -a    \
     "$INSTALLTYPE" != "$BOOTNETINSTALLTYPE"  -a   \
     "$INSTALLTYPE" != "$PKGADDINSTALLTYPE" ]; then
    echo "illegal install type: $INSTALLTYPE"
    print_usage
    exit 1
fi

# set pkgdir to full path (don't use 'run' method so header below is the
# first thing that we print)
cd $PKGDIR
PKGDIR=`pwd`

echo
echo "-----------------------------------------------------------------------"
echo "  installing $CLUSTER from $PKGDIR"
echo "-----------------------------------------------------------------------"
echo
# give user a chance to Ctrl-C and quit before we do anything
pause_here

configure_ssh
ping_cheat $CHEAT


#
# check for needed config files
#
CHEAT_CONFIG_DIR=/export/honeycomb/config
CONF_FILE=cluster.conf
CONFIG_FILE=`$SSHCMD root@$CHEAT "cd $CHEAT_CONFIG_DIR; ls config.properties.*"`
echo "Checking for required config files on the cheat"
echo "   $CHEAT_CONFIG_DIR/$CONF_FILE"
echo "   $CHEAT_CONFIG_DIR/$CONFIG_FILE"
echo "...if they are not found you can't do automated install.  you must do manual install once to generate them"
run_ssh_cmd root@$CHEAT ls -l $CHEAT_CONFIG_DIR/$CONF_FILE
for CFILE in $CONFIG_FILE; do
    run_ssh_cmd root@$CHEAT ls -l $CHEAT_CONFIG_DIR/$CFILE
done

#
# check cheat environment
#
check_cheat_env $CHEAT $CLUSTER $CHEAT_CONFIG_DIR/$CONF_FILE

if [ "$INSTALLTYPE" = "$PKGADDINSTALLTYPE" ]; then
    if [ `count_hc_running $CHEAT` -gt 0 ] ; then
        echo "ERROR"
        echo "Honeycomb must not be running before performing 'pkgadd' installation."
        echo "Please stop Honeycomb and try again."
        exit 1
    fi
fi

#
# make backups
#
backup $CHEAT

print_date "Start installing build from $PKGDIR to $CHEAT:/$INSTALLPATHONCHEAT at"

echo "Checking that pkgs exist in local pkgdir"
check_pkgs_in_pkgdir $PKGDIR

echo "Copying new SUNW* packages to cheat..."
run cd $PKGDIR
for pkg in $HCPKGS
do
  run_ssh_cmd root@$CHEAT "rm -rf $INSTALLPATHONCHEAT/$pkg"
  run_scp_cmd -r $pkg root@$CHEAT:$INSTALLPATHONCHEAT
done
for pkg in $HADBPKGS
do
  run_ssh_cmd root@$CHEAT "rm -rf $INSTALLPATHONCHEAT/$pkg"
  run_scp_cmd -r $DEFAULTHADBPKGDIR/$pkg root@$CHEAT:$INSTALLPATHONCHEAT
done

echo "Checking that the required pkgs made it to the cheat"
check_pkgs_on_cheat $CHEAT

echo "Backing up current config files..."
run_ssh_cmd root@$CHEAT "rm -rf $CHEAT_CONFIG_DIR.bak"
run_ssh_cmd root@$CHEAT "cp -r $CHEAT_CONFIG_DIR $CHEAT_CONFIG_DIR.bak"

run_ssh_cmd root@$CHEAT "/export/jumpstart/bin/rm_install_server"
run_ssh_cmd root@$CHEAT "yes|pkgrm SUNWhcsp"
run_ssh_cmd root@$CHEAT "yes|pkgrm SUNWhcextractor"
run_ssh_cmd root@$CHEAT "yes|pkgadd -d $INSTALLPATHONCHEAT SUNWhcsp"
run_ssh_cmd root@$CHEAT "yes|pkgadd -d $INSTALLPATHONCHEAT SUNWhcextractor"
run_ssh_cmd root@$CHEAT "ls -l $CHEAT_CONFIG_DIR.bak/$CONF_FILE"
run_ssh_cmd root@$CHEAT "/export/jumpstart/bin/setup_install_server -f $CHEAT_CONFIG_DIR.bak/$CONF_FILE"
echo "Diffs between last config and current config:"
CONFIG_FILE_BAK=$CONFIG_FILE
CONFIG_FILE=`$SSHCMD root@$CHEAT "cd $CHEAT_CONFIG_DIR; ls config.properties.*"`
run_ssh_cmd root@$CHEAT "diff $CHEAT_CONFIG_DIR.bak/$CONFIG_FILE_BAK $CHEAT_CONFIG_DIR/$CONFIG_FILE; exit 0"
pause_here # so user can see config diffs on stdout

# sanity check on the config we just generated
clustername=`$SSHCMD root@$CHEAT "grep CLUSTERNAME /export/honeycomb/config/cluster.conf"`
if [ $clustername != "CLUSTERNAME=$CLUSTER" ]; then 
    echo "config shows CLUSTERNAME=$clustername but expected $CLUSTER"; 
    exit 1; 
fi
run_cluster_util_cmd root@$CHEAT "./copy_to_servers.sh $CHEAT_CONFIG_DIR/$CONFIG_FILE /config"
run_cluster_util_cmd root@$CHEAT "./do-servers.sh ln -sf /config/$CONFIG_FILE /config/config.properties"

# Install whitebox sp package with useful testing tools
run_ssh_cmd root@$CHEAT "if pkginfo SUNWhcwbsp > /dev/null 2>&1 ; then yes|pkgrm SUNWhcwbsp; fi"
run_ssh_cmd root@$CHEAT "yes|pkgadd -d $INSTALLPATHONCHEAT SUNWhcwbsp"

run_ssh_cmd root@$CHEAT "yes|pkgadd -d $INSTALLPATHONCHEAT SUNWhcfactorytest"

# don't do this for now.  it was failing on very large logs.
#echo "Rotating the log on the cheat"
#run_ssh_cmd root@$CHEAT "logadm /var/adm/messages -s 10b"
#run_ssh_cmd root@$CHEAT "logger -p err INSTALLING BUILD $PKGDIR"

# three choices for install: 
# 1. ramdiskinstall (Aquarius)
# 2. boot net install (HON full install)
# 3. addpkgs (HON quick install)

if [ "$INSTALLTYPE" = "$RAMDISKINSTALLTYPE" ]; then
    echo
    echo "install type: ramdisk installing all nodes"
    echo

    if [ -z $RAMDISK ]; then
        echo "Using ramdisk from local svn checkout"
        RECYCLE_RAMDISK=false
        RAMDISK_SVNREV=`svn info $RAMDISK_SVNPATH | grep 'Last Changed Rev' | cut -d ' ' -f 4`
        RAMDISK=honeycomb-node-aquarius-$RAMDISK_SVNREV.ramdisk
        run_scp_cmd $RAMDISK_SVNPATH root@$CHEAT:$INSTALLPATHONCHEAT/$RAMDISK
    fi

    echo "Updating ramdisk on the cheat node"
    run_ssh_cmd root@$CHEAT "${BINDIR}/update_ramdisk -r $INSTALLPATHONCHEAT/$RAMDISK"

    echo "Pushing ramdisk to server nodes"
    run_cluster_util_cmd root@$CHEAT "./do-servers.sh 'if ! mount | grep ^/boot/images/0; then mount /boot/images/0; fi'"

    # backup the active ramdisk
    ACTIVE_RAMDISK=`$SSHCMD root@$CHEAT "ssh hcb101 'grep honeycomb-node-aquarius /boot/images/0/boot/grub/menu.lst | head -1 | cut -f 2 | cut -d / -f 2'"`
    run_cluster_util_cmd root@$CHEAT "./do-servers.sh mv -f /boot/images/0/$ACTIVE_RAMDISK /boot/images/0/$ACTIVE_RAMDISK.bak"
    $SSHCMD root@$CHEAT "cd /utils; ./do-servers.sh \"cd /boot/images/0/boot/grub; cat menu.lst | sed -e 's/^.*honeycomb-node-aquarius.*$/  module \/$ACTIVE_RAMDISK.bak/g' > menu.lst.bak\""

    run_cluster_util_cmd root@$CHEAT "./copy_to_servers.sh $INSTALLPATHONCHEAT/$RAMDISK /boot/images/0/"
    # Configure grub to use updated ramdisk
    $SSHCMD root@$CHEAT "cd /utils; ./do-servers.sh \"cd /boot/images/0/boot/grub; cat menu.lst | sed -e 's/^.*honeycomb-node-aquarius.*$/  module \/$RAMDISK/g' > menu.lst.new; mv -f menu.lst.new menu.lst\""

    # unmount /boot/images/0
    # dump file is configured on one of the boot partitions, so umount
    # will return EBUSY. So a force umount is fine here.
    run_cluster_util_cmd root@$CHEAT "./do-servers.sh umount -f /boot/images/0"
    

    echo "---------------------------------------------------"
    echo "Ramdisk has been successfully updated on all nodes."
    echo "Cluster reboot required to activate the new image."
    echo "---------------------------------------------------"

    if $REBOOT; then
        echo "---------------------------------------------------"
        echo "Rebooting now..."
        echo "---------------------------------------------------"
        run_ssh_cmd root@$CHEAT "ssh admin@10.123.45.200 reboot --force"
    fi

    exit 0

elif [ "$INSTALLTYPE" = "$BOOTNETINSTALLTYPE" ]; then
    echo
    echo "install type: boot net installing all nodes"
    echo
    # avoid non-zero error code due to reboot
    run_cluster_util_cmd root@$CHEAT "$DOSERVERS $SSHBASEARGS /opt/honeycomb/sbin/boot_net_install -f; exit 0"

    echo "Sleeping to allow cluster to come up after reboot"
    wait_for_all_nodes_online $CHEAT $INSTALLTIME $INSTALLSLEEPTIME

    echo "Checking that the pkgs have the correct version on each node"
    verify_packages $CHEAT $PKGDIR

    echo "Workaround for bug 6326517: HC doesn't start after a network install due to ntp"
    run_cluster_util_cmd root@$CHEAT "$DOSERVERS $SSHBASEARGS svcadm enable network/ntp"

    start_and_wait_for_honeycomb_start $CHEAT

else     # $PKGADDINSTALLTYPE

    echo 
    echo "install type: adding packages"
    echo

    echo "Installing the pkgs on each node"
    install_pkgs $CHEAT

    # Verify the packages. 
    echo "Checking that the pkgs have the correct version on each node"
    verify_packages $CHEAT $PKGDIR

    echo "-----------------------------------------------------------"
    echo "Packages have been successfully updated on all nodes."
    echo "Honeycomb restart is required to activate the new packages."
    echo "-----------------------------------------------------------"
    exit 0

    ## do not reboot unless explicitly requested, as HONs don't always
    ## come back from the reboot (get stuck in boot interpreter)
    #if [ $REBOOT_AFTER_INSTALL -eq 1 ]; then
        #echo "Install done, now rebooting all nodes"
        #run_cluster_util_cmd root@$CHEAT "$DOSERVERS \"nohup reboot > /dev/null 2>&1 < /dev/null &\""
        #echo "Sleeping to allow cluster to come up after reboot"
        #wait_for_all_nodes_online $CHEAT $REBOOTTIME $SLEEPTIME
    #fi

    ## Start the honeycomb services
    #start_and_wait_for_honeycomb_start $CHEAT

    ## Wait for HADB to be in ready state
    #if [ $INSTALLHADB -eq 1 ]; then
        #echo "wait for hadb to be in ready state.. status should show"
        #echo "honeycomb FaultTolerant"
        #wait_for_hadb_online $CHEAT 
    #fi
fi

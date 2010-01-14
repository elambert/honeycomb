#!/bin/bash -x
#
# $Id: common.sh 11569 2007-10-04 17:46:18Z elambert $
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

MAXITERATIONS=10
SLEEPTIME=30
RAMDISKINSTALLTYPE="ramdisk"
BOOTNETINSTALLTYPE="bootnet"
PKGADDINSTALLTYPE="pkgadd"
DEFAULTINSTALLTYPE=$PKGADDINSTALLTYPE
INSTALLSLEEPTIME=180 # 3 minutes
INSTALLTIME=3600 # one hour

REBOOTTIME=600 # 10 minutes
UTILDIR=/utils
BINDIR=/opt/test/bin # XXX change this to UTILDIR, install as part of SUNWhcwbsp
ENVFILE=$UTILDIR/ENV
INSTALLPATHONCHEAT=/export/jumpstart/honeycomb/Honeycomb_1.0/Product
DEFAULTPARENTDIR=/export/home/build/svn
RAMDISKSRC=platform/solaris/src/ramdisk/honeycomb-node-aquarius-generic.ramdisk

SSHBASEARGS="-q -o StrictHostKeyChecking=no"
HADBHCPKG=SUNWhchadb
SPHCPKG=SUNWhcsp
WBSPHCPKG=SUNWhcwbsp
HADBPKGS="SUNWhadbc SUNWhadbe SUNWhadbv SUNWhadbx SUNWhadba SUNWhadbi SUNWhadbj SUNWhadbm SUNWhadbo SUNWhadbs"
HCEXTRACTORPKG="SUNWhcextractor"
HCPKGS="SUNWhcext SUNWhccommon SUNWhcmdcaches $SPHCPKG SUNWhcserver SUNWhcndmp \
 SUNWhcmgmt SUNWhcfs SUNWhcwbsp SUNWhcwbcluster SUNWhcfactorytest SUNWhcperf \
 SUNWhcadmingui SUNWhcadm $HADBHCPKG $HCEXTRACTORPKG" 
ALLPKGS="$HADBPKGS $HCPKGS"
ALLPKGSWITHHADB="$HADBPKGS $HCPKGS"
HADB_PASSWD='admin'
HADB_PASSSTR='FaultTolerant'
INSTALLHADB=1
REBOOT_AFTER_INSTALL=0

if [ -z "$REBOOT" ]; then
    REBOOT=false;
fi

# paths relative to script working dir; use cmd line args to override
DEFAULTSVNROOT="`cd $ROOT/../..; pwd`"
echo $DEFAULTSVNROOT
DEFAULTHADBPKGDIR=$DEFAULTSVNROOT/platform/HADB/4.6.2
DEFAULTPKGDIR="$DEFAULTSVNROOT/build/pkgdir"

# ramdisk variables
RAMDISK_SVNPATH=$DEFAULTSVNROOT/$RAMDISKSRC

# If the ENV file and other scripts are missing from the cheat node, we
# attempt to copy them from the local build tree.  
LOCAL_UTILDIR=$DEFAULTSVNROOT/hctest/src/cluster/utils
ENV_GENERATOR=$LOCAL_UTILDIR/generate_ENV.sh
ENV_PREFIX=$LOCAL_UTILDIR/ENV
ENV_TEMPLATE=$ENV_PREFIX.in

# consider changing to ./do-servers.sh if you're debugging this script,
# otherwise we ssh to all nodes in parallel (except when we need to read
# the output, which we can't do for background processes)
DOSERVERS="./do-servers-parallel-wait.sh"
# these are the scripts used by the installation process
CHEAT_UTIL_SCRIPTS="do-servers.sh reboot-servers.sh do-servers-parallel-wait.sh copy_to_servers.sh"

if [ $INSTALLHADB -eq 0 ]; then
    ALLPKGS=$HCPKGS
fi

numnodes=-1
nodesonline=0
hcprocs=0
expectedhcprocs=0

die () {
    rc=$1
    echo $2
    exit $rc
}

# Function to run commands and exit 1 on error
run() {
    echo
    echo "`date` RUN ---> $*"
    echo
    $*
    if [ $? -ne 0 ]; then 
        echo "Script encountered an error...exiting"
        exit 1
    fi
}

# called with path to svn repo (might not be right)
configure_ssh() {
    SSHKEY=$DEFAULTSVNROOT/hctest/etc/ssh/id_dsa
    chmod 600 $SSHKEY
    SSHCMD="/usr/bin/ssh $SSHBASEARGS -i $SSHKEY"
    SCPCMD="/usr/bin/scp $SSHBASEARGS -i $SSHKEY"
}

print_date () {
    echo
    echo "---> $1 `date`"
    echo
}

count_nodes () {
    if [ $numnodes != -1 ]; then
        return
    fi
    cheat=$1
    echo "Counting total nodes...\c"
    numnodes=`$SSHCMD root@$cheat "grep SERVER_PRIVATE_IPS= $ENVFILE" |grep -v \# | grep 1 | wc -l`
    numnodes=`echo $numnodes | tr -d ' '`       # rm leading whitespace
    echo $numnodes
}

# this isn't counting processes anymore but num nodes that say quorum=true
# because processes are misleading...grr
# XXX: Need to count processes too, otherwise false positive 
# when HC is not running but nodemgr mailbox has old state.
count_hc_processes_online () {
    cheat=$1
    echo "Counting hc nodes with quorum=true...\c"
    hcprocs=`$SSHCMD root@$cheat "cd $UTILDIR; ./do-servers.sh /opt/honeycomb/bin/nodemgr_mailbox.sh | grep Quorum | grep true | wc -l"`
    hcprocs=`echo $hcprocs | tr -d ' '`       # rm leading whitespace
    echo $hcprocs
    hcprocs=`echo $hcprocs`
    count_nodes $1
    expectedhcprocs=`expr $numnodes \* 1`
}

# this is called when we expect the system to be
# shutting down (heading offline)
count_hc_processes_offline () {
    cheat=$1
    echo "Counting hc processes...\c"
    hcprocs=`$SSHCMD root@$cheat "cd $UTILDIR; ./do-servers.sh $SSHBASEARGS ps -ef" |grep honey | grep -v ntpd | wc -l`
    hcprocs=`echo $hcprocs | tr -d ' '`       # rm leading whitespace
    echo $hcprocs
    hcprocs=`echo $hcprocs`
    count_nodes $1
    expectedhcprocs=0
}

count_nodes_up () {
    cheat=$1
    echo "Counting nodes that are reachable...\c"
    nodesonline=`$SSHCMD root@$cheat "cd $UTILDIR; ./do-servers.sh $SSHBASEARGS uname" | grep 'SunOS' | wc -l`
    nodesonline=`echo $nodesonline | tr -d ' '`       # rm leading whitespace
    echo $nodesonline
    nodesonline=`echo $nodesonline`
}

count_hc_running () {
    cheat=$1
    $SSHCMD root@$cheat "cd $UTILDIR; ./do-servers.sh $SSHBASEARGS \"ps -Aef | grep 'java -DNODE-SERVERS' | grep -v grep\"" | grep NODE-SERVERS | grep -v 'Executing' | wc -l
}

check_config_noreboot () {
    cheat=$1
    echo "Counting nodes with /config/noreboot..."
    count=`$SSHCMD root@$cheat "cd $UTILDIR; ./do-servers.sh ls /config/noreboot | grep noreboot | wc -l"`
    if [ $count -eq 0 ] ; then
        false
    elif [ $count -eq `count_nodes $cheat` ] ; then
        true
    else
        die 1 "error: /config/noreboot is not consistent across the nodes";
    fi
}

check_config_nohoneycomb () {
    cheat=$1
    echo "Counting nodes with /config/nohoneycomb...\c"
    count=`$SSHCMD root@$cheat "cd $UTILDIR; ./do-servers.sh ls /config/nohoneycomb | grep nohoneycomb | wc -l"`
    if [ $count -eq 0 ] ; then
        false
    elif [ $count -eq `count_nodes $cheat` ] ; then
        true
    else
        die 1 "error: /config/nohoneycomb is not consistent across the nodes";
    fi
}

wait_for_all_nodes_online () {
    cheat=$1
    maxwait=$2
    interval=$3
    waitsofar=0

    count_nodes $cheat

    while [ $waitsofar -le $maxwait ]; do
        count_nodes_up $cheat
        if [ $nodesonline -eq $numnodes ]; then
            echo "All $nodesonline nodes are reachable"
            return 0
        else 
            echo "Not all nodes reachable...waiting.  nodes reachable: $nodesonline, expected $numnodes"
            echo "max wait is $maxwait, wait so far is $waitsofar"
        fi

        run sleep $interval
        waitsofar=`expr $waitsofar + $interval`
    done
}

cli_reboot () {
    echo "Rebooting via the cli..."
    run_ssh_cmd root@$1 "ssh $SSHBASEARGS admin@10.123.45.200 reboot --force"
}

wait_for_honeycomb_stop () {
    echo "Waiting for honeycomb to stop"
    
    count_nodes $1
    offline=0
    i=0
    
    while [ $i -lt $MAXITERATIONS ]; do
        count_nodes_up $1
        if [ $nodesonline -eq $numnodes ]; then
            echo "All $nodesonline nodes are reachable"
            count_hc_processes_offline $1
            if [ $hcprocs -eq $expectedhcprocs ]; then
                echo "Honeycomb appears offline..."
                if [ $offline -eq 1 ]; then
                    echo "Considering honeycomb stopped"
                    return 0
                else 
                    echo "Will make sure this is still the case next time and then consider success"
                    offline=1
                fi
            else
                echo "Found $hcprocs hc processes...honeycomb not stopped yet"
                offline=0
            fi

            if [ $i -ge 2 -a offline -eq 0 ]; then 
                # try killing them if we've already waited twice
                echo "Trying to kill honeycomb again..."
                run_cluster_util_cmd root@$1 "$DOSERVERS $SSHBASEARGS pkill -9 -f honeycomb; exit 0"
                run_cluster_util_cmd root@$1 "$DOSERVERS $SSHBASEARGS pkill -9 -f java; exit 0"
            fi
        else 
            echo "Not all nodes reachable...waiting.  nodes reachable: $nodesonline, expected $numnodes"
            offline=0
        fi

        run sleep $SLEEPTIME
        i=`expr $i + 1`
    done

    run_cluster_util_cmd root@$1 "$DOSERVERS $SSHBASEARGS ps -ef"
    echo "Error: cluster did not get into desired state...exiting"
    exit 1
}

stop_and_wait_for_honeycomb_stop () {
    run_ssh_cmd root@$1 "logger -p err SCRIPT IS STOPPING HONEYCOMB"

    # only stop Honeycomb if it's currently running
    count_hc_processes_offline $1
    if [ $hcprocs -ne 0 ]; then 
        cli_reboot
    fi

    wait_for_honeycomb_stop
}

wait_for_hadb_online () {
    echo "Waiting for HADB"
    i=0
    while [ $i -lt $MAXITERATIONS ]; do
        cheat=$1
        hcprocs=`$SSHCMD root@$cheat "cd $UTILDIR; ./do-servers.sh \"echo admin | /opt/SUNWhadb/4/bin/hadbm status honeycomb\" | grep HAFaultTolerant | wc -l"`
        hcprocs=`echo $hcprocs | tr -d ' '`       # rm leading whitespace
        echo $hcprocs
        hcprocs=`echo $hcprocs`
        count_nodes $1
        expectedhcprocs=`expr $numnodes \* 1`
        if [ $hcprocs -ge $expectedhcprocs ]; then
            echo "HADB appears online"
            # XXX add admin access check
            return 0
        else
            echo "Expected at least $expectedhcprocs nodes with HAFaultTolerant, found $hcprocs...waiting"
        fi
        run sleep $SLEEPTIME
        i=`expr $i + 1`
    done

    echo "Error: HADB did not come online...."
    echo "Error: Run `/opt/SUNWhadb/4/bin/hadbm status honeycomb` for more information."

    exit 1
}

start_honeycomb () {
    echo "Starting honeycomb"
    run_cluster_util_cmd root@$1 "$DOSERVERS /opt/honeycomb/etc/init.d/honeycomb start"
}

wait_for_honeycomb_start () {
    echo "Waiting for honeycomb to start"
    sleep 30
    i=0
    while [ $i -lt $MAXITERATIONS ]; do
        count_hc_processes_online $1
        if [ $hcprocs -ge $expectedhcprocs ]; then
            echo "Honeycomb appears online"
            wait_for_hadb_online
            # XXX add admin access check
            return 0
        else
            echo "Expected at least $expectedhcprocs nodes with quorum=true, found $hcprocs...waiting"
        fi
        run sleep $SLEEPTIME
        i=`expr $i + 1`
    done

    echo "Error: all honeycomb nodes did not come online...."
    echo "Run the svcs -xv command to see if there is anything useful there"
    run_cluster_util_cmd root@$1 "$DOSERVERS svcs -xv"

    echo
    echo "Error: all honeycomb nodes did not come online...."
    exit 1
}

start_and_wait_for_honeycomb_start () {
    start_honeycomb $1
    wait_for_honeycomb_start $1
}

wait_for_honeycomb_online() {

    while [ $i -lt $MAXITERATIONS ]; do
        count_nodes_up $1
        if [ $nodesonline -eq $numnodes ]; then
            count_hc_processes_online $1
            if [ $hcprocs -ge $expectedhcprocs ]; then
                echo "Honeycomb back online"
                return 0
            fi
        fi
        run sleep $SLEEPTIME
        i=`expr $i + 1`
    done
    echo
    echo "Error: honeycomb does not appear to be online...."
    exit 1
}

remove_pkg_if_installed() {
    
    run_cluster_util_cmd root@$1 "$DOSERVERS $SSHBASEARGS \"if [ -d /opt/$2 ]; then yes|pkgrm $2; fi\""
}

unregister_honeycomb_from_greenline() {

    echo "Counting nodes where honeycomb-server is under greenline...\c"
    hcsvcs=`$SSHCMD root@$1 "cd $UTILDIR; ./do-servers.sh $SSHBASEARGS svcs" |grep honeycomb-server | wc -l`
    hcsvcs=`echo $hcsvcs | tr -d ' '`       # rm leading whitespace
    echo $hcsvcs
    hcsvcs=`echo $hcsvcs`
    if [ $hcsvcs -ne 0 ]; then
        echo "Unregistering honeycomb-server from greenline management"
        run_cluster_util_cmd root@$1 "$DOSERVERS $SSHBASEARGS svccfg delete -f application/honeycomb-server"
        run_cluster_util_cmd root@$1 "touch /config/noreboot; exit 0"
        run_cluster_util_cmd root@$1 "touch /config/nohoneycomb; exit 0"
    fi
}

# install pkgs on the nodes
# called with cheat pkgdironcheat
install_pkgs() {
   count_nodes $1

   echo "remove all pkgs first, even HADB"
   for pkg in $ALLPKGSWITHHADB
   do
       remove_pkg_if_installed $1 $pkg
   done
    
   echo "Installing pkgs from $1:$2"
   run_cluster_util_cmd root@$1 "$DOSERVERS $SSHBASEARGS rm -rf /tmp/SUNWhc*"
   for pkg in $ALLPKGS
   do
       # skip the sp pkg
       if [ $pkg = $SPHCPKG -o $pkg = $WBSPHCPKG ]; then
            continue
       fi
       # skip the hadb pkg if we aren't installing hadb
       if [ $INSTALLHADB -eq 0 ]; then
           if [ $pkg = $HADBHCPKG ]; then
                continue
           fi
       fi
       run_cluster_util_cmd root@$1 "./copy_to_servers.sh $INSTALLPATHONCHEAT/$pkg /tmp"
       run_cluster_util_cmd root@$1 "$DOSERVERS $SSHBASEARGS \"yes|pkgadd -d /tmp $pkg\""
   done
}

# called with cheat pkgdironbuildmachine pkgname
verify_package() {
    echo "checking pkg $3"
    count_nodes $1
    expectedpstamp=`grep PSTAMP $2/$3/pkginfo| cut -d= -f2`
    correctpkgs=`$SSHCMD root@$1 "cd $UTILDIR; ./do-servers.sh $SSHBASEARGS pkginfo -l $3" | grep PSTAMP | grep $expectedpstamp | wc -l`
    if [ $correctpkgs -ne $numnodes ]; then
        foundpkgs=`$SSHCMD root@$1 "cd $UTILDIR; ./do-servers.sh $SSHBASEARGS pkginfo -l $3" | grep PSTAMP`
        echo "Error:  package $3 was installed correctly on only $correctpkgs nodes, expected $numnodes; expected PSTAMP: $expectedpstamp; PSTAMPS found (only $correctpkgs instances): $foundpkgs... exiting"
        exit 1
    else 
        echo "Found package $3 installed correctly on $correctpkgs nodes; expected PSTAMP: $expectedpstamp"
    fi
}

# called with cheat pkgdironbuildmachine
verify_packages () {
    echo
    echo "Checking installed packages"
    for pkg in $ALLPKGS
    do
         # skip the sp pkg
         if [ $pkg = $SPHCPKG ]; then
              continue
         fi
         # skip the hadb pkg if we aren't installing hadb
         if [ $INSTALLHADB -eq 0 ]; then
             if [ $pkg = $HADBHCPKG ]; then
                  continue
             fi
         fi
         verify_package $1 $2 $pkg
    done
}

check_pkgs_in_pkgdir () {
    for pkg in $ALLPKGS
    do
        echo "Looking for $pkg...\c"

        if [ -d $1/$pkg ]; then
            echo "found it here $1/$pkg"
            continue
        fi

        echo "Looking for pkg in $DEFAULTHADBPKGDIR/$pkg"
        if [ -d $DEFAULTHADBPKGDIR/$pkg ]; then
            echo "found it here $DEFAULTHADBPKGDIR/$pkg"
            #echo "Copying HADB pkgs from $DEFAULTHADBPKGDIR to $1"
            #run cp -r $DEFAULTHADBPKGDIR/$pkg $1/$pkg
            continue
        fi

        echo "Couldn't find pkg $pkg in dir $1.  You can manually cp this to the pkgdir and re-run this script"
        exit 1
    done
}

check_pkgs_on_cheat () {
    for pkg in $ALLPKGS
    do
       # remove the hadb pkg if we aren't installing hadb
       if [ $INSTALLHADB -eq 0 ]; then
           if [ $pkg = $HADBHCPKG ]; then
                echo "not installing HADB, removing $INSTALLPATHONCHEAT/$pkg"
                run_ssh_cmd root@$1 rm -rf $INSTALLPATHONCHEAT/$pkg
                continue
           fi
       fi
       run_ssh_cmd root@$1 ls -ld $INSTALLPATHONCHEAT/$pkg
    done
}

ping_cheat () {
    echo "Making sure cheat node $1 is reachable"
    ping $1
    if [ $? != 0 ]; then
        echo "Couldn't ping $1"
        exit 1
    fi
}

check_cheat_env () {
    cheat=$1
    cluster=$2
    clusterconfig=$3

    echo
    echo "Checking the cheat node environment"
    echo "Looking for directory $cheat:$UTILDIR"
    found=`$SSHCMD root@$1 "if [ ! -d $UTILDIR ]; then echo 'no'; fi"`
    if [ "$found" = "no" ]; then
        echo "$1:$UTILDIR not found, creating it."
        run_ssh_cmd root@$1 "mkdir $UTILDIR"
    fi

    echo "Checking $cheat:$UTILDIR contains necessary scripts"
    for f in $CHEAT_UTIL_SCRIPTS; do
        found=`$SSHCMD root@$1 "if [ ! -f $UTILDIR/$f ]; then echo 'no'; fi"`
        if [ "$found" = "no" ]; then
            echo "$1:$UTILDIR/$f not found, copying it to cheat."
            if [ ! -f $LOCAL_UTILDIR/$f ]; then
                echo "cannot find $LOCAL_UTILDIR/$f"
            fi
            run_scp_cmd $LOCAL_UTILDIR/$f root@$cheat:$UTILDIR
            # be sure the file made it to the cheat node
            run_ssh_cmd root@$1 "ls -l $UTILDIR/$f"
        fi
    done

    echo "Checking for $cheat:$ENVFILE"
    found=`$SSHCMD root@$1 "if [ ! -f $ENVFILE ]; then echo 'no'; fi"`
    if [ "$found" = "no" ]; then
        echo "Didn't find $ENVFILE, so trying to generate one"    
        generate_evn_file $cheat $cluster $clusterconfig
    fi

    echo "Cheat node environment OK"
    echo
}

generate_evn_file () {
    cheat=$1
    cluster=$2
    clusterconfig=$3
     
    ENV_NEW=$ENV_PREFIX.$cluster

    if [ ! -f $ENV_NEW ]; then

        if [ ! -f $ENV_TEMPLATE ]; then
            echo "Cannot find ENV template $ENV_TEMPLATE"
            exit 1
        fi
        if [ ! -f $ENV_GENERATOR ]; then
            echo "Cannot find ENV generation script $ENV_GENERATOR"
            exit 1
        fi
    
        echo "Getting number of nodes from $cheat:$clusterconfig...\c"
        run_ssh_cmd root@$1 "ls -l $clusterconfig"
        numnodes=`$SSHCMD root@$cheat "grep CLUSTERSIZE= $clusterconfig | cut -c13- "`
        echo $numnodes
        run $ENV_GENERATOR $cluster $numnodes $DEFAULTSVNROOT

        if [ ! -f $ENV_NEW ]; then
            echo "failed to generate ENV file: $ENV_NEW"
            exit 0
        fi
    fi
    echo "Copying $ENV_NEW to $cheat:$ENVFILE"
    run_scp_cmd $ENV_NEW root@$cheat:$ENVFILE

    # be sure the file made it to the cheat node, then remove local copy
    run_ssh_cmd root@$1 "ls -l $ENVFILE"
    run rm $ENV_NEW
}

run_ssh_cmd () {
    USERATHOST=$1
    shift
    run $SSHCMD $USERATHOST "$*"
}

run_scp_cmd () {
    run $SCPCMD $*
}

# If you want to execute multiple commands via this wrapper,
# enclose them in escaped double quotes, for example:
# run_cluster_util_cmd "\"/bin/true; /bin/false\"; exit 0"
#
run_cluster_util_cmd () {
    USERATHOST=$1
    shift
    run_ssh_cmd $USERATHOST "cd $UTILDIR; $*"
}

# Use busy loop instead of 'wait' so Ctrl-C exits the script instead of
# just interrupting the wait.  Gives user a chance to stop the script.
pause_here () {
    spin=0
    # 1500 gives us about 5 seconds pause
    while [ $spin -lt 1500 ]; do
        spin=`expr $spin + 1`
    done
}

# for testing
#wait_for_honeycomb_stop dev331-cheat
#wait_for_honeycomb_start dev331-cheat
#verify_packages dev331-cheat /export/jumpstart/honeycomb/Honeycomb_1.0/Product

backup () {
    local cheat=$1

    local use=0
    local bak_use=0
    local avail=0   

    if $BACKUP ; then
        
    # backup cheat config dir
        echo "Backing up $CHEAT:$CHEAT_CONFIG_DIR to $CHEAT:$CHEAT_CONFIG_DIR.bak"
        local use=`$SSHCMD root@$CHEAT "du -sk $CHEAT_CONFIG_DIR | cut -f 1"`
        local bak_use=`$SSHCMD root@$CHEAT "du -sk $CHEAT_CONFIG_DIR.bak | cut -f 1"`
        local avail=`$SSHCMD root@$CHEAT "df -b $CHEAT_CONFIG_DIR | grep -v Filesystem | awk '{print \\$2}'"`
        if [ $use -gt $[avail-bak_use] ]; then
            echo "ERROR: unable to backup $CHEAT:$CHEAT_CONFIG_DIR (no disk space)"
            echo "ERROR: please free space and try again, or set BACKUP=false"
        else
            echo "backing up $CHEAT:$CHEAT_CONFIG_DIR..."
            run_ssh_cmd root@$CHEAT "rm -rf $CHEAT_CONFIG_DIR.bak; cp -RH $CHEAT_CONFIG_DIR $CHEAT_CONFIG_DIR.bak"
        fi
        
    # backup cheat jumpstart dir
        echo "Backing up $CHEAT:$INSTALLPATHONCHEAT to $CHEAT:$INSTALLPATHONCHEAT.bak"
        local use=`$SSHCMD root@$CHEAT "du -sk $INSTALLPATHONCHEAT | cut -f 1"`
        local bak_use=`$SSHCMD root@$CHEAT "du -sk $INSTALLPATHONCHEAT.bak | cut -f 1"`
        local avail=`$SSHCMD root@$CHEAT "df -b $INSTALLPATHONCHEAT | grep -v Filesystem | awk '{print \\$2}'"`
        if [ $use -gt $[avail-bak_use] ]; then
            echo "ERROR: unable to backup $CHEAT:$INSTALLPATHONCHEAT (no disk space)"
            echo "ERROR: please free space and try again, or set BACKUP=false"
        else
            run_ssh_cmd root@$CHEAT "rm -rf $INSTALLPATHONCHEAT.bak; cp -RH $INSTALLPATHONCHEAT $INSTALLPATHONCHEAT.bak"
        fi

    fi
}

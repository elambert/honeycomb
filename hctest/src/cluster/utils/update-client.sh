#!/bin/bash 
#
# $Id: update-client.sh 11809 2008-02-08 22:23:55Z rg162296 $
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
# push binaries to the target clients, stop any existing java processes.
# Use in the form "push-rmiclient.sh X Y" where x and y are cl numbers.
# eg, ./push-rmiclient.sh 22 34 
#
# cur in both places
#
# RMI server launch failure isn't reported

# Release names in the official build repository
RELEASELOCATION="/export/release/repository/releases"
LASTRELEASE="1.0"
THISRELEASE="1.1"
TOPLEVEL=/opt/test
DISKPATH=/mnt/test
HC_TEST_TAR_NAME="hctest.tar"
SUITCASE_TAR_NAME="suitcase.tar"
SERVER_TAR_NAME="server.tar"



#
# cleanup droppings
#
Cleanup () 
{
    if [ ! -z $TEMPDIR ]; then
        rm -rf $TEMPDIR
    fi
}

#
# update softlinks on target machine
#
IntHandler () {
    # no-op to allow return code to pass out
    echo ""
    echo "!!!!!!!!!!!!! upgrade-client interupted; cleaning up."
    Cleanup
    exit 1
}
                                                                                                                                                                                                       
trap IntHandler SIGTERM SIGKILL SIGINT SIGQUIT SIGHUP



#
# Find latest QA and dev drop location
#
FindVersions () {
    #
    # Get absolute path to the utils directory
    #
    utilsDir=`dirname $0`
    utilsDir=`cd $utilsDir; pwd`
    
    if [ ! -f $utilsDir/find-latest.pl ] ; then
        echo "Can't locate $utilsDir/find-latest.pl, fatal, exiting."
        exit 1
    fi
    if [ ! -d $RELEASELOCATION ] ; then
        echo "Cannot locate the releases directory on this machine; won't push default QA tests"
        return
    fi
    QADIRECTORY=`$utilsDir/find-latest.pl $RELEASELOCATION $LASTRELEASE`
    DEVELOPMENTDIRECTORY=`$utilsDir/find-latest.pl $RELEASELOCATION $THISRELEASE`

    DEVREVNAME=`basename $DEVELOPMENTDIRECTORY`
    QAREVNAME=`basename $QADIRECTORY`
}




#
# sets the "$HC_TEST_TAR_FILE" variable
#
setupHcTestTarfile() 
{
    HC_TEST_TAR_FILE="$ROOT/build/pkgdir/$HC_TEST_TAR_NAME"
    if [ ! -f $HC_TEST_TAR_FILE ] ; then
        echo "cannot locate $HC_TEST_TAR_FILE. Validate that 'ant tar' was run in dev/hctest."
        exit 1
    fi
}



#
# sets the "$SUITCASE_TAR_FILE" variable
#
setupSuitcaseTarfile() 
{
    SUITCASE_TAR_FILE="$ROOT/build/pkgdir/$SUITCASE_TAR_NAME"
    if [ ! -f $SUITCASE_TAR_FILE ] ; then
        echo "cannot locate $SUITCASE_TAR_FILE. Validate that 'ant tar' was run in trunk/suitcase."
        exit 1
    fi
}

#
# sets $HC_TEST_TAR_FILE to the hctest.tar given the base path.
#
packageHcTestTar() 
{
#    echo "packaging tar file. "
    HC_TEST_TAR_FILE="$1/AUTOBUILT/pkgdir/hctest.tar"
    if [ ! -f $HC_TEST_TAR_FILE ] ; then
        echo "Cannot locate $HC_TEST_TAR_FILE. So sorry. Going home."
        Cleanup
        exit 1
    fi
#    echo " Located tarfile: $HC_TEST_TAR_FILE "

}

#
# sets $SUITCASE_TAR_FILE to the suitcase.tar given the base path.
#
packageSuitcaseTar() 
{
#    echo "packaging tar file. "
    SUITCASE_TAR_FILE="$1/AUTOBUILT/pkgdir/suitcase.tar"
    if [ ! -f $SUITCASE_TAR_FILE ] ; then
        echo "Cannot locate $SUITCASE_TAR_FILE. So sorry. Going home."
        Cleanup
        exit 1
    fi
#    echo " Located tarfile: $SUITCASE_TAR_FILE "

}



#
# Copies the tarfile at $1 to $tempdir
#

setupTar() 
{
    local THISTAR=$1
    cp $THISTAR $TEMPDIR
    if [ $? -ne 0 ] ; then
        echo "Unable to copy $THISTAR to $TEMPDIR, fatal, exiting."
        Cleanup
        exit 1
    fi
}

#
# end tarfile finding
# 




runCommand ()
{
    local COMMAND
    local status
    cstring="$@"
    result=`echo $cstring | grep " > "`
    if [ ! -z "$result" ]; then
        COMMAND="$@"
    else
        COMMAND="$@ 1>&2"
    fi
                                                                                                                                                                                                       
#    echo "runcommand runnning: $COMMAND"
    ssh $TARGETCLIENT -o StrictHostKeyChecking=no -i $SSHKEYFILE -l $user $COMMAND
    status=$?
    #
    # For some reason "no route to host" errors place null in status.
    #
    if [ -z "$status" ]; then
        return 1
    fi
    return $status
}



purgeDisk() {
    runCommand "rm -rf /opt/test /mnt/test/opt"
    if [ $? -ne 0 ]; then
        echo "Couldn't purge /opt/test /mnt/test/opt on $TARGETCLIENT. Exiting."
        Cleanup
        exit 1
    fi

}


zapTemp() {
    runCommand "rm -rf /mnt/test/*tmp"
    if [ $? -ne 0 ]; then
        echo "Couldn't purge /mnt/test/ tempfiles on $TARGETCLIENT. Exiting."
        Cleanup
        exit 1
    fi

}

setupDisk() {

    local TOPPATH=`dirname $TOPLEVEL`

    #
    # Check/setup fixed disk directory paths
    #

    runCommand "if [ -d $DISKPATH ]; then exit 0; else exit 1; fi"
    if [ $? -ne 0 ]; then
        echo "Permanent storage not found at $DISKPATH on $TARGETCLIENT, aborting."
        echo "### Create /mnt/test on $TARGETCLIENT and re-run this installer. ###"
        Cleanup
        exit 1
    fi


    runCommand "if [ -d $DISKPATH/$TOPPATH ]; then exit 0; else exit 1; fi"
    if [ $? -ne 0 ]; then
        echo -n " making $DISKPATH$TOPPATH..."
        runCommand "mkdir $DISKPATH$TOPPATH"
        if [ $? -ne 0 ]; then
            echo "Unable to create directory $DISKPATH$TOPPATH on $TARGETCLIENT, aborting."
            exit 1
        fi
    fi


    runCommand "if [ -d $DISKPATH$TOPLEVEL ]; then exit 0; else exit 1; fi"
    if [ $? -ne 0 ]; then
        echo -n " Creating $DISKPATH$TOPLEVEL..."
        runCommand "mkdir $DISKPATH$TOPLEVEL"
        if [ $? -ne 0 ]; then
            echo "Unable to create directory $DISKPATH/$TOPLEVEL on $TARGETCLIENT, aborting."
            exit 1
        fi
    fi


    #
    # Check/setup ramdisk /opt/test directory.
    #
#    echo setting up $TOPPATH and $TOPLEVEL
    runCommand "if [ -d $TOPPATH ]; then exit 0; else exit 1; fi"
    if [ $? -ne 0 ]; then
        echo -n " Creating $TOPPATH..."
        runCommand "mkdir $TOPPATH"
        if [ $? -ne 0 ]; then
            echo "Unable to create directory $TOPPATH on $TARGETCLIENT, aborting."
            exit 1
        fi
    fi

    runCommand "if [ -d $TOPLEVEL ]; then exit 0; else exit 1; fi"
    if [ $? -ne 0 ]; then
        echo -n " Creating $TOPLEVEL..."
        runCommand "mkdir $TOPLEVEL"
        if [ $? -ne 0 ]; then
            echo "Unable to create directory $TOPLEVEL on $TARGETCLIENT, aborting."
            exit 1
        fi
    fi    

#    runCommand  "mkdir $DISKPATH/$TOPPATH"
}

#
# Checks to see if the directory has already been pushed there.
#
checkPushed() {
    local version=$1
    runCommand "if [ -d $DISKPATH/$TOPLEVEL/$version ]; then exit 0; else exit 1; fi"
    if [ $? -ne 0 ]; then
        echo -n " Will install $version ... "
        return 1
    else
#        echo -n " $version is there now."
        return 0
    fi
            
}


#
# Cleanup entire test target tree on dest machine, make fresh $TOPLEVEL
# This is only for the devel stuff
#

cleanDestTree() 
{
    runCommand  "rm -rf $TOPLEVEL"
    if [ $? -ne 0 ] ; then
        echo " coudld not remove old $TOPLEVEL contents from $TARGETCLIENT."
        Cleanup
        exit 
    fi

    #
    # Create a new $TOPLEVEL
    #
    runCommand  mkdir $TOPLEVEL
    if [ $? -ne 0 ] ; then
        echo " coudld not make new $TOPLEVEL on $TARGETCLIENT."
        Cleanup
        exit 
    fi

#    echo -n "made new $TOPLEVEL "
}

#
# Cleans up a test target dir datavip 10.7.225.108 316-admin 316-cheat
#
cleanSourceTree() {

    rm -rf $TEMPDIR/test
    if [ $? -ne 0 ] ; then
        echo "Can't cleanup $TEMPDIR/test. Exiting to prevent copying the wrong thing."
        Cleanup
        exit 1
    fi
    
}

makeSoftlink()
{
    local FROM=$1
    local TO=$2
    runCommand "rm -rf $TO"
#    echo "removing old softlink $TO"
    if [ $? -ne 0 ] ; then
        echo "Couldn't remove old softlink $TO on system $TARGETCLIENT"
    fi

    runCommand "ln -s $FROM $TO"
    if [ $? -ne 0 ] ; then
        echo "Error softlinking to permanent version - can't link $FROM $TO on $TARGETCLIENT"
        exit 1
    fi
}

makeTree()
{


    pushd $TEMPDIR > /dev/null 2>&1
    if [ $? -ne 0 ] ; then
        echo "Unable to cd to $TEMPDIR, fatal, exiting."    
        Cleanup
        exit 1
    fi
    
    tar -xf $HC_TEST_TAR_NAME
    if [ $? -ne 0 ] ; then
        echo "Unable to extract $HC_TAR_TAR_NAME, fatal, exiting."    
        Cleanup
        exit 1
    fi

    rm -rf $HC_TEST_TAR_NAME
    if [ $? -ne 0 ] ; then
        echo "Unable to remove the old tarfile: $HC_TEST_TAR_NAME, exiting."    
        Cleanup
        exit 1
    fi


    
    


#
# Not totally thought out - double check
#

    cd test/bin
    tar -xf ../../$SUITCASE_TAR_NAME
     
    if [ $? -ne 0 ] ; then
        echo "Unable to extract $SUITCASE_TAR_NAME, fatal, exiting."    
        Cleanup
        exit 1
    fi

    mv honeycomb-suitcase.jar ../lib

    cd ../lib
    
    if [ -e honeycomb-common.jar ]; then
	ln -s honeycomb-common.jar st5800-common.jar
    else
	echo "Warning: missing honeycomb-common.jar. Not a problem if this is 1.0"
    fi
    if [ -e honeycomb-server.jar ]; then
	ln -s honeycomb-server.jar st5800-server.jar
    else
	echo "Warning: missing honeycomb-server.jar."
    fi
    if [ -e honeycomb-suitcase.jar ]; then
	ln -s honeycomb-suitcase.jar st5800-suitcase.jar
    else
	echo "Warning: missing honeycomb-suitcase.jar."
    fi

    cd ../..

    rm -rf $SUITCASE_TAR_NAME
    if [ $? -ne 0 ] ; then
        echo "Unable to remove the old tarfile: $SUITCASE_TAR_NAME, exiting."    
        Cleanup
        exit 1
    fi

    chmod 755 $TEMPDIR/test/bin/*
    if [ $? -ne 0 ] ; then
        echo "Unable to set execute bits high on $TEMPDIR/test/bin/*. Fatal."    
        Cleanup
        exit 1
    fi


    popd > /dev/null 2>&1
}
    



pushTree() 
{

    SOURCEDIR=$1
    DESTDIR=$2
#    echo "starting pushtree. Sourcedir: $SOURCEDIR destdir: $DESTDIR"
#    ls -lath $SOURCEDIR
    #
    # Now, remove the old contents of $DESTDIR
    #
    runCommand  rm -rf $DESTDIR
    if [ $? -ne 0 ] ; then
        echo " coudld not remove old $DESTDIR contents from $TARGETCLIENT."
        Cleanup
        exit 
    fi
#    echo -n " removed old $DESTDIR"


    #
    # Create a new $DESTDIR
    #
    runCommand  mkdir $DESTDIR
    if [ $? -ne 0 ] ; then
        echo " coudld not make new $DESTDIR on $TARGETCLIENT."
        Cleanup
        exit 
    fi
#    echo -n " made new $DESTDIR"
    
    #
    # And replace them with our new spiffy tempdir contents, minus the docs.
    #

    scp -i $SSHKEYFILE -o StrictHostKeyChecking=no -r \
        $SOURCEDIR/bin \
        $SOURCEDIR/lib \
        $SOURCEDIR/etc root@$TARGETCLIENT:$DESTDIR > /dev/null 2>&1
    if [ $? -ne 0 ] ; then
        echo "couldn't copy $SOURCEDIR contents to $DESTDIR on $TARGETCLIENT."
        Cleanup
        exit 
    fi

    #
    # It helps to have this file on the client, so ConvertOid and related tools don't complain.
    #
    ssh -i $SSHKEYFILE -o StrictHostKeyChecking=no root@$TARGETCLIENT \
        "mkdir -p /config; touch /config/config_defaults.properties"

    makeSoftlink $DESTDIR/lib/honeycomb-common.jar $DESTDIR/lib/st5800-common.jar
    makeSoftlink $DESTDIR/lib/honeycomb-server.jar $DESTDIR/lib/st5800-server.jar    
    makeSoftlink $DESTDIR/lib/honeycomb-suitcase.jar $DESTDIR/lib/st5800-suitcase.jar
}


startProcesses() 
{
    DESTDIR=$1
    #
    # Now, launch the new server.
    #

    #
    # Script should handle this, but just in case....
    #
    runCommand "rm -rf /tmp/RunClntSrv.result"

    runCommand "if [ -f $DESTDIR/bin/RunClntSrv  ]  ; then exit 0 ; else exit 1; fi"
        if [ $? -ne 0 ] ; then
        echo "Fatal; couldn't locate RMI server at $DESTDIR/bin/RunClntSrv"
        Cleanup
        exit 
    fi


    runCommand  "$DESTDIR/bin/RunClntSrv  > /dev/null 2>&1 &"
    if [ $? -ne 0 ] ; then
        echo "Fatal; couldn't launch RMI server."
        Cleanup
        exit 
    fi
    echo -n "Checking RMI launch.." 
    runCommand "if [ -f /tmp/RunClntSrv.result ]  ; then exit 1 ; else exit 0; fi"
    if [ $? -ne 0 ] ; then
        echo "Fatal; couldn't launch RMI server."
        runCommand "rm -rf /tmp/RunClntSrv.result"
        Cleanup
        exit 
    fi

    
    echo -n " RMI server started. "    
}

killProcesses() {
    #
    # shutdown rmi server
    #
    shutdowncmd=`ssh $TARGETCLIENT -o StrictHostKeyChecking=no -i $SSHKEYFILE -l $user "find /opt -name ShutdownClntSrv -print | head -1"`
    if [ ! -z "$shutdowncmd" ]  ; then
        runCommand $shutdowncmd 2> /dev/null
        if [ $? -eq 0 ] ; then
            echo -n " shutdown rmi client ok."
        else
            echo -n " shutdown rmi client failed."
        fi
    fi

    #
    # Kill any processes with "honeycomb" in them
    #
    runCommand  pkill -9 -f honeycomb 
    if [ $? -eq 0 ] ; then
        echo -n " killed honeycomb processes.. "
    fi

    #
    # While we're at it, let's kill anything with "java" in it.
    #
    runCommand  pkill -9 -f java 
    if [ $? -eq 0 ] ; then
        echo -n " killed java processes.. "
    fi    
}





pushComplete()
{
    local DIRPATH=$1
#    echo pushing complete from $DIRPATH
    packageHcTestTar $DIRPATH
    packageSuitcaseTar $DIRPATH
    setupTar $HC_TEST_TAR_FILE
    setupTar $SUITCASE_TAR_FILE
    makeTree 
    local REVNAME=`basename $DIRPATH`
#    echo pushing complete to $DISKPATH/$TOPLEVEL/$REVNAME
    pushTree  "$TEMPDIR/test" ${DISKPATH}${TOPLEVEL}/$REVNAME
    # Do softlink
    runCommand "rm -rf $TOPLEVEL/$REVNAME"
    if [ $? -ne 0 ] ; then
        echo "Error returned removing $REVNAME, quitting"
        exit 1
    fi
    cleanSourceTree    
}


processClient()
{
    TARGETCLIENT=$1
    echo -n "=================== $TARGETCLIENT "
    
    if [ $islinux == "true" ] ; then 
        res=`ping -q -c 1 $cl |  grep '^rtt' | awk '{print $1}'`
        if [ ! "$res" == "rtt" ] ; then
            res=""
        fi
    else
        res=`ping $TARGETCLIENT | grep alive`
    fi
    if [ -z "$res" ] ; then
        echo " is dead, skipping."
    fi
    
    if [ -z $DONTKILL ] ; then
            killProcesses                
    fi
    
    if [ ! -z $CLEANDISK ] ; then
        purgeDisk
        echo -n " cleaned disk.. "
    fi
    
    if [ ! -z $ZAPTEMP ] ; then
        echo -n " removing temp files.. "
        zapTemp
        echo -n " done removing.. "
    fi
    
    setupDisk
    
    if [ -z "$PUSHDEV" ] && [ "$NODEFAULTBUILD" == "true" ] ; then
        cleanDestTree
    fi
    
    if [ ! -z $MASTERLINK ] ; then 
        runCommand "rm -rf $TOPLEVEL/bin"
        runCommand "rm -rf $TOPLEVEL/lib"
        runCommand "rm -rf $TOPLEVEL/etc"
        
        makeSoftlink $TOPLEVEL/$MASTERLINK/bin $TOPLEVEL/bin
        makeSoftlink $TOPLEVEL/$MASTERLINK/etc $TOPLEVEL/etc
        makeSoftlink $TOPLEVEL/$MASTERLINK/lib $TOPLEVEL/lib
    fi
    
    #
    # Renew softlinks
    #
    runCommand "rm -rf $DISKPATH/$TOPLEVEL/qa"
    runCommand "rm -rf $DISKPATH/$TOPLEVEL/dev"
    runCommand "rm -rf $TOPLEVEL/qa"
    runCommand "rm -rf $TOPLEVEL/dev"
    makeSoftlink ${DISKPATH}${TOPLEVEL}/$QAREVNAME $TOPLEVEL/qa 
    makeSoftlink ${DISKPATH}${TOPLEVEL}/$DEVREVNAME $TOPLEVEL/dev
    
    if [ -z $NODEFAULTBUILD ] ; then
        checkPushed `basename $QADIRECTORY`
        if [ $? -eq 1 ] ; then
            pushComplete $QADIRECTORY
        fi
        echo -n " QA ok. "
        
        checkPushed `basename $DEVELOPMENTDIRECTORY`
        if [ $? -eq 1 ] ; then
            pushComplete $DEVELOPMENTDIRECTORY
        fi
        echo -n " Dev ok. "
        
    fi

    if [ ! -z $ROOT ] ; then 
        setupHcTestTarfile 
        setupTar $HC_TEST_TAR_FILE
        setupSuitcaseTarfile 
        setupTar $SUITCASE_TAR_FILE
        makeTree
        
        pushTree  "$TEMPDIR/test" $TOPLEVEL/cur
        cleanSourceTree    
        echo -n " cur ok. "
    else 
        if [ ! -z $PUSHDEV ] ; then
            echo "FAILED to push current development directory... continuting."
        else
            echo -n " cur not pushed. "
        fi
    fi
    
    if [ ! -z $SERVERTYPE ] ; then             
        if [ $SERVERTYPE = "cur" ] ; then 
            if [ -z $PUSHDEV ] ; then 
                echo -n "Warning: Attempting to start development RMI server without pushing a fresh copy."
            fi
            startProcesses "/opt/test/cur"          
        elif [ $SERVERTYPE = "dev" ] ; then 
            startProcesses "$DISKPATH/$TOPLEVEL/$DEVREVNAME"
        elif [ $SERVERTYPE = "qa" ] ; then
            startProcesses "$DISKPATH/$TOPLEVEL/$QAREVNAME"
        fi
    fi
   
    echo "" 
    echo "Copying DTF to client [$TARGETCLIENT]"
    # clean up
    ssh -i $SSHKEYFILE -o StrictHostKeyChecking=no root@$TARGETCLIENT \
        'rm -fr /mnt/test/dtf > /dev/null'
    # copy over dtf
    scp -i $SSHKEYFILE -o StrictHostKeyChecking=no -r \
        dtf/dist root@$TARGETCLIENT:/mnt/test/dtf > /dev/null
        
    if [ $? -ne 0 ] ; then
        echo "couldn't copy dtf contents to /mnt/test/dtf on $TARGETCLIENT."
        Cleanup
        exit 
    fi
    
    echo " All done."
}


printUsage() {
    echo "update-client [-nsdc] [-l build] [-b build-path] -u [link type] start-node [end-node]"
    echo " Note: softlink setup no longer optional."
    echo "By default, we push the contents of /export/releases/repository - both dev and QA." 
    echo "Objects are placed in /opt/test, with softlinks to QA and dev in"
    echo "/mnt/test/opt/test." 
    echo "Any processes with \"honeycomb\" or \"java\" in the name are killed."
    echo "If the -b argument is used, the contents of an optional development directory are copied to"
    echo "the ramdisk on the target nodes at /opt/test/cur." 
    echo "   -l \'dev/qa/cur\' will launch one of three RMI servers, dev, qa, or the development server"
    echo "   -n prevents the killing of existing processes currently executing on the node"
    echo "   -s prevents pushing of the default releases (dev and QA)"
    echo "   -c cleans any existing test software off the nodes disk and ramdrive."
    echo "   -b takes a path to an active build tree, and pushes it"
    echo "   -u creates softlinks in /opt/test/bin, etc, lib to the version of choice (dev, qa, or cur)"
    echo "   -v pushes the server classes (not done by default; usually not needed)"
    echo "   -z does an rm -rf /mnt/test/*tmp on each test node"
}

# Add args to launch RMA server "l"
# add arg to supporess killing of process "n"
# add arg to suppress pushing of releases "s"
# add arg to suppress pushing of dev (or don't push if the path isn't supplied on command line)  "d"
# add "cleanout" option. "c"

while getopts ":l:b:u:nvscz" opt; do
    case $opt in
        u ) echo "Updating /opt/test symlink to point to version $OPTARG "
            MASTERLINK=$OPTARG
            if ! [ "dev" = $MASTERLINK -o "qa" = $MASTERLINK -o "cur" = $MASTERLINK ] ; then
                echo "Unknown  version $MASTERLINK - choose dev, qa, or cur."
            fi
            ;;
        b ) echo "Pushing dev dir $OPTARG" 
            ROOT=$OPTARG
            PUSHDEV=true
            if [ ! -d $ROOT ] ; then
                echo "Can't locate directory $ROOT, aborting."
                exit 1
            fi
            ;;
        l ) echo "Launching with RMI server version $OPTARG "
            SERVERTYPE=$OPTARG
            if ! [ "dev" = $SERVERTYPE -o "qa" = $SERVERTYPE -o "cur" = $SERVERTYPE ] ; then
                echo "Unknown launch version $SERVERTYPE - choose dev, qa, or cur."
            fi
            ;;
        n ) DONTKILL=true
            echo "Not killing running RMI servers";;
        s ) NODEFAULTBUILD=true
            echo "Won't push default builds";;
        c ) echo "Cleaning old versions.."
            CLEANDISK=true
            ;;
        v ) SERVERPUSH=true
            echo pushing server 
            ;;
        z ) echo "Cleaning out temp files from test disk.."
            ZAPTEMP=true
            ;;
        \? )  printUsage
        exit 1
    esac
done
shift $(($OPTIND -1 ))
        
if [ -z "$MASTERLINK" ] ; then
    echo "Softlink set up is now mandatory (fragment validation can't find the correct scripts otherwise)"
    echo "use -u option to indicate the primary test set."
    echo
    echo
    printUsage
    exit 1
fi

if [ -z "$1" ] ; then 
    echo "Please supply a start cl number"
    exit 1
fi 

if [ -z "$2" ] ; then 
    clientEnd=$1
else
    clientEnd=$2
fi

clientStart=$1

user=root




#
# Find the key! where da key?!
#

SSHKEYFILE="$ROOT/test/etc/cluster_id_dsa"
if [ ! -f $SSHKEYFILE ] ; then
    SSHKEYFILE="$HOME/.ssh/cluster_id_dsa"
    if [ ! -f $SSHKEYFILE ] ; then         
        echo -n "cannot locate cluster_id_dsa at $SSHKEYFILE"
        if [ ! -z $ROOT ]  ; then
            echo " or $ROOT/test/etc/cluster_id_dsa. "
        else 
            echo "."
        fi


        SSHKEYFILE="$HOME/.ssh/id_dsa"
        echo "Trying with \"${SSHKEYFILE}\""
        if [ ! -f "$SSHKEYFILE" ] ; then         
            echo "Sheesh, you don't even have that. I give up."
            exit 1
        fi
        
    fi
fi

TEMPDIR=`mktemp -d -p /tmp pushRmiClientUntar.XXXXXXXX`
if [ $? -ne 0 ] ; then
    echo "Unable to create $TEMPDIR, fatal, exiting."
    exit 1
fi

TEMPKEYFILE="${TEMPDIR}/cluster_id_dsa"
cp $SSHKEYFILE $TEMPKEYFILE
if [ $? -ne 0 ] ; then
    echo "Unable to copy $SSHKEYFILE to $TEMPDIR, fatal, exiting."
    Cleanup
    exit 1
fi

SSHKEYFILE=$TEMPKEYFILE
chmod 600 $SSHKEYFILE
if [ $? -ne 0 ] ; then
    echo "Unable to set permissions to 600 on $SSHKEYFILE, exiting."
    Cleanup
    exit 1
fi



islinux=`uname -a | grep -i linux`
if [ -z "$islinux" ] ; then 
    islinux='false'
else
    islinux='true'
fi


#/mnt/test is dest on clients




FindVersions
#echo got ddir: $DEVELOPMENTDIRECTORY
#echo got qadir:$QADIRECTORY

echo "### If prompted for ssh password, place key from ../test/etc/authorized_keys ###"
echo "### onto your client into root's home directory under .ssh/authorized_keys   ###"

# clients can be specified as:
# a) single hostname or IP address
# b) single numeric argument N, to be expanded to clN.sfbay.sun.com
# c) two numeric arguments M N, to be expanded to range clM..clN in sfbay.sun.com domain

isNumber="$(echo $clientStart | sed 's/[0-9]//g')"
if [ -z "$isNumber" ] ; then
    # client was specified as numeric arg, expand to clXX.sfbay.sun.com
    #
    isNumber="$(echo $clientEnd | sed 's/[0-9]//g')"
    if [ ! -z "$isNumber" ]; then
        echo "Error: start client argument is numeric, but end client argument is not, quitting"
        exit 1
    else
        while test $clientStart -le $clientEnd; do
            curCl=cl${clientStart}.sfbay.sun.com
            processClient $curCl
            clientStart=`expr $clientStart + 1`
        done
    fi
else
    # client was specified as hostname or IP, do not expand
    #
    if [ "$clientEnd" != "$clientStart" ]; then
        echo "Error: only one non-numeric client argument can be supplied, quitting"
        exit 1
    fi
    processClient $clientStart

fi

Cleanup
exit 0

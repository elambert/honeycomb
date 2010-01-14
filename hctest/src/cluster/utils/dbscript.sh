#!/bin/bash  
#
# $Id: dbscript.sh 10858 2007-05-19 03:03:41Z bberndt $
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

#
# Todo:
#   test connection to db server via psql and via ssh before proceeding
#
OS=`uname -a | awk '{ print $1 }'`
if [ "$OS" = "Linux" ] ; then
    GREP=grep
fi

if [ "$OS" = "SunOS" ] ; then
    GREP=gegrep
fi
if [ -z $GREP ] ; then
    echo "unknown OS; can't set grep - exiting"
fi


#
# Find the key! where da key?!
#
SSHKEYFILE="$ROOT/test/etc/cluster_id_dsa"
if [ ! -f $SSHKEYFILE ] ; then
    SSHKEYFILE="$HOME/.ssh/cluster_id_dsa"
fi
if [ ! -f $SSHKEYFILE ] ; then
    SSHKEYFILE="../etc/cluster_id_dsa"
fi
if [ ! -f $SSHKEYFILE ] ; then
    SSHKEYFILE="/opt/test/etc/cluster_id_dsa"
fi
if [ ! -f $SSHKEYFILE ] ; then
    SSHKEYFILE="/opt/test/cur/etc/cluster_id_dsa"
fi
if [ ! -f $SSHKEYFILE ] ; then
    SSHKEYFILE="/opt/test/dev/etc/cluster_id_dsa"
fi
if [ ! -f $SSHKEYFILE ] ; then
    SSHKEYFILE="/opt/test/qa/etc/cluster_id_dsa"
fi
if [ ! -f $SSHKEYFILE ] ; then         
    SSHKEYFILE="$HOME/.ssh/id_dsa."       
fi


if [ ! -f $SSHKEYFILE ] ; then         
    echo "Cannot locate cluster_id_dsa or id_dsa. Giving up."
    exit 1
fi



DBADMIN=honeycomb



WORKDIR=$PWD
#
# "f" fields are parsed out of the filename,
# "o" fields are extracted from the fragment itself.
#
#        f_path    varchar(100) primary key not null,\

LAYOUT_TABLE="\
        layout integer not null,\
        node integer not null,\
        disk integer not null,\
        import_time timestamp not null"



IMPORTS_TABLE="\
        import_time timestamp primary key not null,\
        donetime timestamp not null"

STATICS_TABLE="fragment_size integer not null,\
        max_chunk_size integer not null,\
        block_size integer not null,\
        write_unit integer not null"

#
# "f" fields are parsed out of the filename,
# "o" fields are extracted from the fragment itself.
#

# o_fragnum integer not null,\
FRAGMENT_TABLE="\
        f_path    varchar(100) not null,\
        import_time timestamp references imports(import_time) not null,\
        f_size    bigint not null,\
        f_time timestamp not null,\
        f_node    integer not null,\
        f_disk    integer not null,\
        f_oid     varchar(55) not null,\
        f_version integer not null,\
        f_objecttype integer not null,\
        f_chunknumber integer not null,\
        f_layoutmapid integer not null,\
        f_cellid integer not null,\
        f_fragnum integer not null,\
        f_tmpclose varchar(10) not null,\
        o_oid     varchar(55) not null,\
        o_extoid  varchar(70) not null,\
        o_layoutmapid integer not null,\
        o_size    bigint not null,\
        o_fragdatalen integer not null,\
        o_link varchar(50) not null,\
        o_ctime timestamp not null,\
        o_rtime timestamp,\
        o_etime timestamp,\
        o_sched integer not null,\
        o_checksumalg varchar(25) not null,\
        o_hashalg varchar(25),\
        o_contenthashstring varchar(50) not null,\
        o_deletedstatus varchar(10) not null,\
        o_version integer,\
        o_autoclosetime timestamp,\
        o_deletiontime timestamp,\
        o_shred integer not null,\
        o_refcount integer not null,\
        o_maxrefcount integer not null,\
        o_deletedrefs bit(1024),\
        o_numpreceedingchecksums integer not null,\
        o_chunksize integer not null,\
        o_footerchecksum varchar(25) not null,\
        o_mdfield varchar(300) not null, \
        primary key (f_path,import_time)"

#		 numChunks = objectSize / maxChunkSize + 1;
#
# "o" fields are extracted from the
# "NewObjectIdentifier" structure
#
# "m" are from the "SystemMetadata" structure.
#
# "e" fields are generated by the database
# extraction program
#




# this field went right before m_rtime
#        m_ctime timestamp not null,\
SYSTEM_METADATA_TABLE="\
        o_oid     varchar(55) not null,\
        o_version integer,\
        o_layoutmapid integer not null,\
        o_objecttype integer not null,\
        o_chunknumber integer not null,\
        o_cellid integer not null,\
        m_oid     varchar(55) not null,\
        import_time timestamp references imports(import_time) not null,\
        m_layoutmapid integer not null,\
        m_size    bigint not null,\
        m_fragCount integer not null,\
        m_redundantFragCount integer not null,\
        m_totalFragCount integer not null,\
        m_linkOid     varchar(50) not null,\
        m_rtime timestamp,\
        m_etime timestamp,\
        m_shred integer not null,\
        m_contenthashstring varchar(50) not null,\
        e_disk integer not null, \
        e_node integer not null"

OP_TYPE_TABLE="\
        id              int primary key, \
        name            varchar not null"

OP_TABLE="\
        id              bigint primary key, \
        op_type         smallint references op_type, \
        status          int not null, \
        start_time      timestamp not null, \
        end_time        timestamp, \
        duration        bigint, \
        run_id          bigint, \
        info            text, \
        api_oid         varchar, \
        api_link_oid    varchar, \
        sha1            varchar, \
        has_metadata    boolean not null, \
        num_bytes       bigint, \
        rr_offset       bigint, \
        rr_length       bigint, \
        sr_oid          varchar, \
        sr_link_oid     varchar, \
        sr_size         bigint, \
        sr_ctime        bigint, \
        sr_dtime        bigint, \
        sr_digest       varchar, \
        log_tag 		varchar, \
        hook1           int default 0, \
        hook2           int default 0"

OP_MD_LONG_TABLE="\
        op              bigint references op(id), \
        name            varchar not null, \
        value           bigint"

OP_MD_DOUBLE_TABLE="\
        op              bigint references op(id), \
        name            varchar not null, \
        value           numeric"

OP_MD_STRING_TABLE="\
        op              bigint references op(id), \
        name            varchar not null, \
        value           varchar"

OP_MD_BYTE_TABLE="\
        op              bigint references op(id), \
        name            varchar not null, \
        value           char(1)"

OBJ_TABLE="\
        api_oid         varchar primary key, \
        sys_oid         varchar, \
        sha1            varchar not null, \
        has_metadata    boolean not null, \
        size            bigint not null, \
        api_link_oid    varchar, \
        ctime           bigint not null, \
        dtime           bigint not null, \
        deleted         boolean, \
        hook1           int default 0, \
        hook2           int default 0, \
        lock           	varchar"

MD_LONG_TABLE="\
        api_oid         varchar references obj, \
        name            varchar not null, \
        value           bigint"

MD_DOUBLE_TABLE="\
        api_oid         varchar references obj, \
        name            varchar not null, \
        value           numeric"

MD_STRING_TABLE="\
        api_oid         varchar references obj, \
        name            varchar not null, \
        value           varchar"

MD_BYTE_TABLE="\
        api_oid         varchar references obj, \
        name            varchar not null, \
        value           char(1)"

PERF_MONITOR_TABLE="\
        meas_time       timestamp default now(), \
        type            char(1) not null, \
        time            bigint not null, \
        cases           int, \
        bytes           bigint"

ALERT_MONITOR_TABLE="\
        time            timestamp default now(), \
        node            integer not null, \
        name            text not null,\
        value           text not null"

runSilentDbCommandNoCheck() {
    command="$*"

    if [ -z $DEBUGMODE ] ; then 
        psql -h $DBSERVER -U $DBNAME -d $DBNAME -c "$command" > /dev/null 2>&1
    else 
        echo "running command: $command" 2>&1
        psql -U $DBNAME -d $DBNAME -c "$command" 
    fi
    return $?
}

runSilentDbCommand() {
    command="$*"
    runSilentDbCommandNoCheck $command
    if [ $? -ne 0 ] ; then
        echo "Error executing \"$command\" on database $DBNAME"
        exit 1
    fi
}
runDbCommand() {

    command="$*"
    if [ ! -z $DEBUGMODE ] ; then 
        echo "running command:     psql -h $DBSERVER -U $DBNAME -d $DBNAME -c $command"  2>&1
    fi
    psql -h $DBSERVER -U $DBNAME -d $DBNAME -c "$command"
    if [ $? -ne 0 ] ; then
        echo "Error executing \"$command\" on database $DBNAME"
        exit 1
    fi
}


#
# ssh to dbserver using user postgres with the sshkeyfile
#
runDbServer ()
{
    if [ -z  $DBSERVER ]; then 
        echo "can't ssh - set dbserver with -c flag." 2>&1
    fi
    local COMMAND
    local status   
    COMMAND=$@

    if [ ! -z $DEBUGMODE ] ; then 
        echo "runcommand runnning: ssh $DBSERVER -l postgres -i key -o StrictHostKeyChecking=no -q $COMMAND" 2>&1
    fi
    export RETURNVALUE=`ssh $DBSERVER -l postgres -i $SSHKEYFILE -o StrictHostKeyChecking=no -q $COMMAND`
    status=$?
#    echo -n "runDbServer returnvalue $RETURNVALUE " 2>&1
#    echo "exitcode: $status" 2>&1
        #
        # For some reason "no route to host" errors place null in status.
        #
    if [ -z "$status" ]; then
        return 1
    fi
    return $status

}


setupTempDir() {
   #
    # Setup directory on remote machine
    #
    runDbServer "mktemp -p /export/tmp -d"
    retcode=$?
    if [ $retcode -ne 0 ] ; then
        echo "Failed to create temp directory on $DBSERVER."
        exit 1
    fi
    TEMPDIR=$RETURNVALUE
    runDbServer "chmod 777 $TEMPDIR"
    retcode=$?
    if [ $retcode -ne 0 ] ; then
        echo "Failed to update permissions on $DBSERVER:$TEMPDIR."
        exit 1
    fi

    echo "made tempdir on $DBSERVER:$TEMPDIR"     2>&1
}

removeTempDir() {
    runDbServer "rm -rf $TEMPDIR"
}

copyToTemp() {
    #
    # copy target file to remote machine
    #
#    export RETURNVALUE=`ssh $DBSERVER -l postgres -i $SSHKEYFILE -o StrictHostKeyChecking=no -q $COMMAND`
#        scp -o StrictHostKeyChecking=no -q -i $SSHKEYFILE $SUITCASE root@$cheat:/ 
    local BNAME=`basename $1`
    scp  -i $SSHKEYFILE -o StrictHostKeyChecking=no  $1 postgres@$DBSERVER:$TEMPDIR/$BNAME
    retcode=$?
    if [ $retcode -ne 0 ] ; then
        echo "Failed to copy $importFile to $DBSERVER:$TEMPDIR."
        exit 1
    fi

}

serialImport() {
    tablename=$2
    local THISFILE=$1
    local BNAME=`basename $THISFILE`
    setupTempDir 
    copyToTemp $THISFILE
    if [ -z $APPEND ] ; then 
        echo "Removing old $tablename in databse $DBNAME" 2>&1
        tableReset $tablename
    fi     
    CURTEMPFILE=$TEMPDIR/$BNAME
    runDbCommand "copy $tablename from '$CURTEMPFILE' DELIMITER  ',' NULL '' "
    removeTempDir
}

parallelImport() {
    importFile=$1
    tableName=$2
    setupTempDir
 
    copyToTemp $importFile
    local BNAME=`basename $importFile`    

    #
    # Split file on remote machine 
    #

    NUMSPLITS=10 
    NUMLINES=`wc -l $importFile | awk '{ print $1 }'`
    
    SPLITLINES=`expr $NUMLINES + $NUMSPLITS`
    SPLITLINES=`expr $SPLITLINES + 5`
    SPLITLINES=`expr $SPLITLINES / $NUMSPLITS`
    echo -n "Splitting into $NUMSPLITS fragments..." 2>&1
    runDbServer "split -l $SPLITLINES $TEMPDIR/$BNAME $TEMPDIR/tempOut"
    echo " done." 2>&1

    if [ -z $APPEND ] ; then 
        echo "Removing old $tableName in databse $DBNAME" 2>&1
        tableReset $tableName
    fi     

    
    #
    # Start importing. Note that while the "copy" command is run locally, 
    # it sources files from the dbserver. Guess how long that took to figure out...
    #
    cur=0        
    echo -n "importing data..." 2>&1
    echo 2>&1
    alphaArray[0]=a
    alphaArray[1]=b
    alphaArray[2]=c
    alphaArray[3]=d
    alphaArray[4]=e
    alphaArray[5]=f
    alphaArray[6]=g
    alphaArray[7]=h
    alphaArray[8]=i
    alphaArray[9]=j
    alphaArray[10]=k
    alphaArray[11]=l
    alphaArray[12]=m
    alphaArray[13]=n
    alphaArray[14]=o
    while test $cur -lt $NUMSPLITS; do

        CURTEMPFILE=$TEMPDIR/tempOuta${alphaArray[$cur]}
        runDbServer "if [  -f $CURTEMPFILE ]; then echo present; else echo missing; fi;"
        retcode=$?
        if [ $RETURNVALUE = "present" ] ; then
#            echo "looks like $CURTEMPFILE is valid, importing." 2>&1
            runDbCommand "copy $tableName from '$CURTEMPFILE' DELIMITER ',' NULL '' " &
            pidArray[$cur]=$!
        else
#            echo "Not importing missing file $CURTEMPFILE" 2>&1
            pidArray[$cur]=0
        fi
        cur=`expr $cur + 1`
    done
    echo "Importing to $DBNAME with $NUMSPLITS processes" 2>&1
    i=0
    while test $i -lt $NUMSPLITS; do
#        echo waiting for ${pidArray[$i]} 2>&1
        if [ ${pidArray[$i]} -ne 0 ] ; then 
            wait ${pidArray[$i]}
            retcode=$?
            if [ $retcode -ne 0 ] ; then
                echo "Failed to import from file ${alphaArray[$i]}. retcode: $retcode. Aborting."2>&1
                removeTempDir
                exit 1
            fi
        fi 
        i=`expr $i + 1`
    done
    removeTempDir
    echo -n " done with import". 2>&1
}


systemSetup()
{    
    if [ -z `echo $PATH | grep pgsql` ] ; then 
        export PATH=`echo "$PATH:/usr/local/pgsql/bin"`
    fi
    if [ -z `echo $LD_LIBRARY_PATH | grep pgsql` ] ; then 
        export LD_LIBRARY_PATH=`echo "$LD_LIBRARY_PATH:/usr/sfw/lib/sparcv9:/usr/sfw/lib:/usr/local/pgsql/lib:/usr/lib:/usr/lib/sparcv9:/usr/lib/iconv/sparcv9/:/usr/lib/iconv:/usr/local/pgsql/lib"`
        echo "added critical library path elements to LD_LIBRARY_PATH" 1>&2
    fi

}

deleteDb() {
    runSilentDbCommandNoCheck "drop user $DBNAME"
    if [ ! -z "`psql -h $DBSERVER -U $DBADMIN -l | grep $DBNAME | awk '{print $1}'`" ] ; then

        if [ -z $DEBUGMODE ] ; then 
            dropdb -h $DBSERVER -U $DBADMIN $DBNAME > /dev/null 2>&1
        else
            dropdb -h $DBSERVER -U $DBADMIN $DBNAME
        fi
        if [ $? -ne 0 ] ; then
            echo "Can't drop database $DBNAME" 2>&1
        else
            echo "Database drop complete" 2>&1
        fi

        if [ -z $DEBUGMODE ] ; then 
            dropuser -h $DBSERVER  -U $DBADMIN $DBNAME > /dev/null 2>&1
        else
            dropuser -h $DBSERVER -U $DBADMIN $DBNAME
        fi
        if [ $? -ne 0 ] ; then
            echo "Can't drop db user, $DBNAME" 2>&1
        else
            echo "DB user drop complete" 2>&1
        fi
    else 
        echo "Database $DBNAME doesn't exist; no drop executed." 2>&1
    fi

    
}
tableReset() {
    runSilentDbCommandNoCheck "delete from $1"
}
tableSetup() {
    runSilentDbCommand "create table imports (\
        $IMPORTS_TABLE
    )"
    runSilentDbCommand "create table fragments (\
        $FRAGMENT_TABLE
    )"

    runSilentDbCommand "create table layout (\
        $LAYOUT_TABLE
    )"

    runSilentDbCommand "create table statics (\
        $STATICS_TABLE
    )"
    runSilentDbCommand "create table system_metadata (\
        $SYSTEM_METADATA_TABLE
    )"
    #
    # this doesn't seem to work.
    #
    runSilentDbCommand "create index uidindex on fragments (f_oid,import_time)"
#    runSilentDbCommand "create index layoutindex on layout (layout,import_time)"

    runSilentDbCommand "create table op_type (\
        $OP_TYPE_TABLE
    )"
    runSilentDbCommand "insert into op_type values (1, 'store')"
    runSilentDbCommand "insert into op_type values (2, 'link')"
    runSilentDbCommand "insert into op_type values (3, 'retrieve')"
    runSilentDbCommand "insert into op_type values (4, 'retrieve_range')"
    runSilentDbCommand "insert into op_type values (5, 'retrieve_md')"
    runSilentDbCommand "insert into op_type values (6, 'delete')"
    runSilentDbCommand "insert into op_type values (7, 'query')"



    runSilentDbCommand "create table op (\
        $OP_TABLE
    )"
    runSilentDbCommand "create sequence op_id_seq"
    runSilentDbCommand "create index op_api_oid_i on op (api_oid)"
    runSilentDbCommand "create index op_type_i on op (op_type)"

    runSilentDbCommand "create table op_md_long (\
        $OP_MD_LONG_TABLE
    )"
    runSilentDbCommand "create table op_md_double (\
        $OP_MD_DOUBLE_TABLE
    )"
    runSilentDbCommand "create table op_md_string (\
        $OP_MD_STRING_TABLE
    )"
    runSilentDbCommand "create table op_md_byte (\
        $OP_MD_BYTE_TABLE
    )"
    runSilentDbCommand "create table perf_monitor (\
        $PERF_MONITOR_TABLE
    )"
    runSilentDbCommand "create index perf_time_i on perf_monitor (meas_time)"
    runSilentDbCommand "create table alert_monitor (\
        $ALERT_MONITOR_TABLE
    )"
    runSilentDbCommand "create index alert_time_i on alert_monitor (time)"

    runSilentDbCommand "create table obj (\
        $OBJ_TABLE
    )"
    runSilentDbCommand "create index obj_api_oid_i on obj (api_oid)"
    runSilentDbCommand "create index obj_sys_oid_i on obj (sys_oid)"

    runSilentDbCommand "create table md_long (\
        $MD_LONG_TABLE
    )"
    runSilentDbCommand "create table md_double (\
        $MD_DOUBLE_TABLE
    )"
    runSilentDbCommand "create table md_string (\
        $MD_STRING_TABLE
    )"
    runSilentDbCommand "create table md_byte (\
        $MD_BYTE_TABLE
    )"
}

dbSetup()
{

    PRESENT=false

    if [ "`psql -h $DBSERVER -U $DBADMIN -l | awk '{print $1}' | $GREP -x $DBNAME`" == $DBNAME ] ; then
        PRESENT=true
    fi

    if [ $PRESENT == false ] ; then
        echo "No $DBNAME db present; creating."  1>&2
        createuser  -h $DBSERVER -U $DBADMIN -a -D $DBNAME  1>&2
        if [ $? -ne 0 ] ; then
            echo "WARNING: error creating db user, $DBNAME. possible that this user already exists." 2>&1
        fi       
        createdb  -h $DBSERVER -U $DBADMIN --encoding=UNICODE -O $DBNAME $DBNAME  1>&2
        if [ $? -ne 0 ] ; then
            echo "Error creating db, $DBNAME - fatal, exiting." 2>&1
            exit 1
        fi       
        createlang -h $DBSERVER  -U $DBADMIN plpgsql $DBNAME  1>&2
        if [ $? -ne 0 ] ; then
            echo "Error installing language plpgsql on database $DBNAME - fatal, exiting." 2>&1
            exit 1
        fi 
        tableSetup
    fi
}

preconditions(){
    systemSetup
    dbSetup
}


runTest() {
    if [ "printExtraFrags" = $1 ] ; then 
        printExtraFrags
    elif [ "checkExtraFrags" = $1 ] ; then 
        checkExtraFrags
    elif [ "printOtherOmitted" = $1 ] ; then 
        printOtherOmitted

    elif [ "printOmittedFrags" = $1 ] ; then 
        printOmittedFrags        
    elif [ "checkOmittedFrags" = $1 ] ; then 
        checkOmittedFrags        
    elif    [ "verifyLayout" = $1 ] ; then 
        verifyLayout
    elif    [ "lastImport" = $1 ] ; then 
        lastImport
        result=$?
        echo "$MOSTRECENTIMPORT"
        exit $result
    elif    [ "printBadLayouts" = $1 ] ; then 
        printBadLayouts
    elif [ "countOids" = $1 ] ; then 
        runDbCommand "SELECT COUNT(DISTINCT f_oid) FROM fragments"
    elif [ "fragmentMetadataCheck" = $1 ] ; then 
        fragmentMetadataCheck
    elif [ "fragmentMetadataDisplay" = $1 ] ; then 
        fragmentMetadataDisplay
    elif [ "metadataFragmentCheck" = $1 ] ; then 
        metadataFragmentCheck
    elif [ "metadataFragmentDisplay" = $1 ] ; then 
        metadataFragmentDisplay
    elif [ "filesizeDisplay" = $1 ] ; then 
        filesizeDisplay
    elif [ "filesizeCheck" = $1 ] ; then 
        filesizeCheck
    elif [ "filesizeDiskDisplay" = $1 ] ; then 
        filesizeDiskDisplay
    elif [ "dateDiskDisplay" = $1 ] ; then 
        dateDiskDisplay
    elif [ "correspondanceDisplay" = $1 ] ; then 
        correspondanceDisplay
    elif [ "correspondanceCheck" = $1 ] ; then 
        correspondanceCheck

    elif [ "blackCorrespondanceDisplay" = $1 ] ; then 
        blackCorrespondanceDisplay
    elif [ "blackCorrespondanceCheck" = $1 ] ; then 
        blackCorrespondanceCheck
    else 
        echo "No such test: $1" 2>&1
    fi        
}



EXTRAQUERY="select fragments.f_oid,fragments.f_layoutmapid, fragments.f_fragnum, fragments.f_node, fragments.f_disk, layout.node as layout_node, \
               layout.disk as layout_disk from fragments \
               left outer join layout on \
               layout.node=fragments.f_node and layout.disk=fragments.f_disk \
               and fragments.f_layoutmapid=layout.layout"

EXTRAQUERY="select * from ($EXTRAQUERY) as foo where foo.layout_node is null or foo.layout_disk is null"


printExtraFrags(){
    runDbCommand "$EXTRAQUERY"
    exit 1
    
}

checkExtraFrags(){
    BADCOUNT=`psql -h $DBSERVER -U $DBNAME -d $DBNAME -t -q  -c \
              "select count(foo.f_oid) from ($EXTRAQUERY) as foo" |  head -1 | awk '{ print \$1 }'`
    
    if [ "$BADCOUNT" -ne 0 ] ; then
        echo "There are $BADCOUNT extra fragments. Use printExtraFrags to see the details." 2>&1
        exit 1
    fi
    exit 0


    
}

OTHERQUERY="select  f_oid, f_layoutmapid, count(f_oid) as numfrags \
               from layout
               join fragments on \
               layout.node=fragments.f_node and layout.disk=fragments.f_disk \
               and fragments.f_layoutmapid=layout.layout group by f_oid,f_layoutmapid"


OTHERQUERY="select f_oid, f_layoutmapid from ($OTHERQUERY) as foo where numfrags < 7"


printOtherOmitted() {
    echo "Chunks which are missing fragments. See printOmittedFrags for details. This runs quickly, but prints less data." 2>&1
    runDbCommand "$OTHERQUERY"
    exit 1
    
}

checkOmittedFrags() {
    BADCOUNT=`psql -h $DBSERVER -U $DBNAME -d $DBNAME -t -q  -c \
              "select count(foo.f_oid) from ($OTHERQUERY) as foo" |  head -1 | awk '{ print \$1 }'`
    
    if [ "$BADCOUNT" -ne 0 ] ; then
        echo "There are $BADCOUNT missing fragments. Use printOmittedFrags to see the details." 2>&1
        exit 1
    fi
    exit 0

}

setupOmitted() {
   #
   # possible to fix this with a simpler "not in" table join? Or maybe speed part of it up.
   # The good news here is that the long running part of this query is dependent on 
   # the number of missing frags, not the number of total frags.
   # early versions of this had a counter that would limit the number
   # of results; might not be a bad idea to put that back in.
   #
   # see also "not exists" - gotta be a better way to do this!
   # Assuming that what you're asking for is "all records in q not in p", I'd try:

   #Select a,b from queue
   #Minus
   #Select a,b from pee

   #This'll work in oracle, but not sure which db you're in (postgres?), so you could also do:

   #Select a, b from queue where a||b not in (select a||b from pee)

   #That's assuming that || is the concat operator in your db.  If not, sub with whatever concat operator you have.

   #Now, "not in" is the most expensive method, so you could do an outer join (once again, adjust for non-oracle syntax):

   #Select q.a, q.b from queue q, pee p
   #where q.a = p.a (+)
   #And q.b = p.b (+)
   #And (p.a is null or p.b is null)

   #
   FINDMISSINGCHUNKSEGMENTS="Select f_oid, num_frags, f_chunknumber from (select f_oid,f_chunknumber, count(*)\
                  as num_frags from fragments group by f_oid,f_chunknumber) fragcount where \
                  fragcount.num_frags <> 7"

    MISSING_MORE_DATA="select  missingchunks.f_oid, missingchunks.num_frags, \
                  missingchunks.f_chunknumber, fragments.f_fragnum,\
                  fragments.f_node, fragments.f_disk, fragments.f_layoutmapid from \
                   ($FINDMISSINGCHUNKSEGMENTS) as missingchunks join fragments on \
                   missingchunks.f_oid=fragments.f_oid and \
                   missingchunks.f_chunknumber=fragments.f_chunknumber \
                   order by missingchunks.f_oid,missingchunks.f_chunknumber,\
                   fragments.f_fragnum,fragments.f_node,fragments.f_oid,fragments.f_layoutmapid"



#    runSilentDbCommandNoCheck "drop type missing_frags "
    runSilentDbCommandNoCheck "create type missing_frags as (f_oid varchar(50), \
                               f_node integer, f_disk integer,f_layoutmapid integer)"

    GET_BAD_OIDS="select missing.f_oid,fragments.f_layoutmapid from \
                  ($FINDMISSINGCHUNKSEGMENTS) as missing join fragments on fragments.f_oid = \
                  missing.f_oid group by missing.f_oid,fragments.f_layoutmapid"
#    GET_BAD_OIDS="select f_oid,f_layoutmapid from ($GET_BAD_OIDS) as missing where missing.f_oid=\'54c9b124-35f3-11da-9a51-080020e3695c\'"   

    FUNCTION="\
              CREATE OR REPLACE FUNCTION fragscompletelong() \
              RETURNS setof missing_frags \
              AS ' \
              DECLARE    \
                  returnRow missing_frags; \
                  curoid record; \
                  curLayout record; \
                  allfrags record; \
                  notFound BOOLEAN; \
              BEGIN \
                  FOR curoid in $GET_BAD_OIDS loop \  
                      for curLayout in select layout,node,disk from layout where curoid.f_layoutmapid=layout.layout loop \ 
                      notFound=true;  
                          for allfrags in select f_oid, f_chunknumber,f_layoutmapid,f_node,f_disk from fragments where \ 
                                       curoid.f_oid=fragments.f_oid and \
                                       curoid.f_layoutmapid=fragments.f_layoutmapid loop \
                              if((curLayout.node = allfrags.f_node) and (curlayout.disk = allfrags.f_disk) ) then \
                                  notFound=false;  
                              end if;
                          end loop;\
                       if (notFound) then \ 
                         returnRow=(curoid.f_oid,curLayout.node,curLayout.disk,curoid.f_layoutmapid);\    
                         return next returnRow; \
                       end if;

                      END LOOP; \
                  END LOOP; \
                  RETURN; \
              END; \
              ' LANGUAGE plpgsql;"
    runSilentDbCommand $FUNCTION


    GET_DELETED_STATUS="select f_oid,o_deletedstatus,f_layoutmapid,f_chunknumber from fragments \
                        group by f_oid,o_deletedstatus,f_layoutmapid,f_chunknumber"
    OMITTEDQUERY="Select missing.f_oid, f_node, f_disk,missing.f_layoutmapid,o_deletedstatus  \
                   from fragscompletelong() as missing join ($GET_DELETED_STATUS) as deleted \
                   on deleted.f_oid=missing.f_oid and \
                   deleted.f_layoutmapid=missing.f_layoutmapid \
                   order by f_oid, f_layoutmapid "

}

printOmittedFrags() {
    echo "Prints omitted fragments (less than seven and greater than 0) for each chunk that's missing a fragment." 2>&1
    echo " Doesn't print extra (> 7) fragments. For that, use printExtraFrags"  2>&1

     setupOmitted
     runDbCommand "$OMITTEDQUERY"
}




                 
#lastImport() {
#
    #MOSTRECENTIMPORT=`psql -h $DBSERVER -U $DBNAME -d $DBNAME -t -q -c "\
         #select import_time from imports order by import_time desc limit 1;" \
         #|  head -1 | awk '{ print \$1 " " \$2 }'`
#
    #return $?
#}






BADLAYOUTQUERY="select countedFrags.f_oid, countedFrags.fragcount,countedFrags.f_layoutmapid from \
               (select count(*) as fragcount,fragments.f_oid,fragments.f_layoutmapid from fragments, layout where \
               layout.node=fragments.f_node and layout.disk=fragments.f_disk \
               and fragments.f_layoutmapid=layout.layout  \
               group by fragments.f_oid,fragments.f_layoutmapid) as countedFrags \
               where countedFrags.fragcount <> 7"


printBadLayouts() {
    echo "Printing bad layouts... Shows any chunks that don't conform to layout. Extras are ignored" 1>&2  2>&1
    runDbCommand "$BADLAYOUTQUERY order by \
                      f_oid,f_layoutmapid"
    exit 0
}



verifyLayout() {
    BADFRAGCOUNT=`psql -h $DBSERVER -U $DBNAME -d $DBNAME -t -q  -c \
               "select count(f_oid) from ($BADLAYOUTQUERY) as foo" |  head -1 | awk '{ print \$1 }'`
    
    if [ "$BADFRAGCOUNT" -ne 0 ] ; then
        echo "This cluster has $BADFRAGCOUNT chunks which are missing or or don't match the layout." 2>&1
        echo "Print them with printBadLayouts. To see the missing fragments, use printOmittedFrags." 2>&1
        echo "To see extra fragments, use PrintExtraFrags." 2>&1
        exit 1
    fi
    exit 0



}





SYSTEMFRAGJOIN="fragments.f_oid=system_metadata.o_oid and \
           fragments.f_disk=system_metadata.e_disk and\
           fragments.f_node=system_metadata.e_node and\
           fragments.f_chunknumber=system_metadata.o_chunknumber and\
           fragments.import_time=system_metadata.import_time "

#
# Check system_metadata and footer correspondance#
# f_path,o_size, fragments.f_size,
#           f_path,o_size, fragments.f_size,
#           fragments.f_fragnum , fragments.o_fragnum,\
CORRESPONDANCEQUERY="select f_oid,f_path, 
           fragments.o_layoutmapid, system_metadata.o_layoutmapid,\
           system_metadata.m_layoutmapid,\
           fragments.o_version,system_metadata.o_version,\
           fragments.f_objecttype , system_metadata.o_objecttype,\
           fragments.f_chunknumber, system_metadata.o_chunknumber\
           from system_metadata,fragments where \
           $SYSTEMFRAGJOIN and (\
           fragments.o_version != system_metadata.o_version or \
           fragments.o_layoutmapid != system_metadata.o_layoutmapid or \
           fragments.f_layoutmapid != system_metadata.o_layoutmapid or \
           system_metadata.m_layoutmapid != system_metadata.o_layoutmapid or \
           fragments.f_objecttype != system_metadata.o_objecttype
           )"


correspondanceDisplay() {
    echo "Does a diff between the fragment footer, the parsed filename, and the system metadata." 2>&1
    echo "only prints where there isn't a match." 2>&1
    echo "Displays (or, in the case of correspondanceCheck, checks only) the following invariants:" 2>&1
    echo "  fragments.o_version == system_metadata.o_version  " 2>&1
    echo "  fragments.o_layoutmapid == system_metadata.o_layoutmapid  " 2>&1
    echo "  fragments.f_layoutmapid == system_metadata.o_layoutmapid  " 2>&1
    echo "  system_metadata.m_layoutmapid == system_metadata.o_layoutmapid  " 2>&1
    echo "  fragments.f_objecttype == system_metadata.o_objecttype  " 2>&1
    echo 2>&1
    runDbCommand "$CORRESPONDANCEQUERY order by fragments.f_oid"
}


correspondanceCheck() {
    BADCOUNT=`psql -h $DBSERVER -U $DBNAME -d $DBNAME -t -q  -c \
              "select count(foo.f_oid) from ($CORRESPONDANCEQUERY) as foo" |  head -1 | awk '{ print \$1 }'`
    
    if [ "$BADCOUNT" -ne 0 ] ; then
        echo "This has $BADCOUNT fragments with bad correspondance." 2>&1
        exit 1
    fi
    exit 0
}

#kilroy was here
dumpOidData() {
    oid=$1
    
}

#


#
# Check dates
#
DATEDISKQUERY="select f_oid, f_time, o_ctime from fragments where f_time!=o_ctime"

dateDiskDisplay() {
    echo "Shows the difference between the fragment footer time and actual file system" 2>&1
    echo "creation time. These are never the same, but I'm leaving this query in" 2>&1
    echo "because I think it's interesting - what's the delta under heavy load? any change?" 2>&1
    echo " This query could be expanded to be smarter, but for now it is what it is." 2>&1
    echo 2>&1
    runDbCommand "$DATEDISKQUERY"
}

#
# Check disk file size correspondance
#


FILESIZEDISKQUERY="select f_oid,f_size,o_size \
               from fragments \
               where f_size != o_size"



filesizeDiskDisplay() {
    runDbCommand "$FILESIZEDISKQUERY"
}

#
# Check file size correspondance
#


FILESIZEQUERY="select f_oid,fragments.f_size,fragments.o_size,\
               system_metadata.m_size from fragments,system_metadata\
               where m_size != o_size and
               fragments.f_oid=system_metadata.o_oid and \
               fragments.f_disk=system_metadata.e_disk and\
               fragments.f_node=system_metadata.e_node and\
               fragments.import_time=system_metadata.import_time"


filesizeCheck() {
    BADCOUNT=`psql -h $DBSERVER -U $DBNAME -d $DBNAME -t -q  -c \
              "select count(foo.f_oid) from ($FILESIZEQUERY) as foo" |  head -1 | awk '{ print \$1 }'`
    
    if [ "$BADCOUNT" -ne 0 ] ; then
        echo "This has wack filesize info. It has $BADCOUNT fragments with hosed sizes." 2>&1
        exit 1
    fi
    exit 0
}

filesizeDisplay() {
    runDbCommand "$FILESIZEQUERY"
}




#
# Fragments that have no entries in the system_metadata
#
#  The EXCEPT operator computes the set of rows that are in the result of 
# the left SELECT statement but not in the result of the right one.

GET_DELETED_STATUS="select f_oid,o_deletedstatus,f_layoutmapid from fragments \
                    group by f_oid,o_deletedstatus,f_layoutmapid"


NOMDQUERY="select f_oid,f_layoutmapid,f_disk,f_node  \
           from fragments except (select \
           system_metadata.o_oid, system_metadata.m_layoutmapid,e_disk,e_node \
           from system_metadata)"

NOMDQUERY="select missing.f_oid,missing.f_layoutmapid, missing.f_disk,
           missing.f_node, fragments.f_fragnum from ($NOMDQUERY) as missing, fragments where\
           missing.f_oid=fragments.f_oid and \
           missing.f_layoutmapid=fragments.f_layoutmapid and \
           missing.f_disk=fragments.f_disk and \
           missing.f_node=fragments.f_node order by missing.f_oid"


#NOMDQUERY="select * \
#           from ($NOMDQUERY) as missing, ($GET_DELETED_STATUS) as deleted \
#           where missing.f_oid=deleted.f_oid and \
#           missing.f_layoutmapid=deleted.f_layoutmapid"


NOMDQUERY="select missing.f_oid,missing.f_layoutmapid, missing.f_disk, missing.f_fragnum, \
           missing.f_node,deleted.o_deletedstatus \
           from ($NOMDQUERY) as missing, ($GET_DELETED_STATUS) as deleted \
           where missing.f_oid=deleted.f_oid and \
           missing.f_layoutmapid=deleted.f_layoutmapid"

NOMDQUERY="select * from ($NOMDQUERY) as missing where missing.f_fragnum < 3"

fragmentMetadataCheck() {
    BADCOUNT=`psql -h $DBSERVER -U $DBNAME -d $DBNAME -t -q  -c \
              "select count(foo) from ($NOMDQUERY) as foo" |  head -1 | awk '{ print \$1 }'`
    
    if [ "$BADCOUNT" -ne 0 ] ; then
        echo "This cluster has missing metadata. It has $BADCOUNT fragments without metadata entries." 2>&1
        exit 1
    fi
    exit 0
}


fragmentMetadataDisplay() {
    #
    # Check to see correspondance between deletedness and missingness.
    #
    echo "All of these fragments exist on disk but lack a corresponding" 2>&1
    echo "entry in any system_metadata database." 2>&1


    runDbCommand "$NOMDQUERY"
    exit 0

}


#
# Entries in system_metadata that don't have corresponding fragments
#

NOFRAGQUERY="select o_oid,m_layoutmapid,e_disk,e_node  \
           from system_metadata except select \
           fragments.f_oid, fragments.f_layoutmapid,fragments.f_disk,\
           fragments.f_node \
           from fragments"


metadataFragmentCheck() {
    BADCOUNT=`psql -h $DBSERVER -U $DBNAME -d $DBNAME -t -q  -c \
               "select count(foo) from ($NOFRAGQUERY) as foo" |  head -1 | awk '{ print \$1 }'`
    if [ "$BADCOUNT" -ne 0 ] ; then
        echo "This cluster has metadata that referrs to missing fragments. It has $BADCOUNT fragless metadata entries." 2>&1
        exit 1
    fi
    exit 0
}

metadataFragmentDisplay() {
    echo "These fragments exist in the system_metadata databases but not on disk." 2>&1
    runDbCommand $NOFRAGQUERY
    exit 0
}

printUsage() {
    echo "dbscript.sh [-d dbserver] [-u runid] [-p statics-import-file] [-m import-timestamp-file] [-i import-file] [-s system-metadata-file] [-l layout-file] [-c cluster-name] [-r] [-t testname] [-d oid]"
    echo "   -i Do parallel import from CSV file import-file (erases existing fragments)"
    echo "   -a turn on append mode for all imports"
    echo "   -m add this import timestamp. Critical precondition for importing import-file or system metadata"
    echo "   -l Do an import from CSV file layout-file into the layout table"
    echo "   -p Do an import from CSV file statics into the statics table"
    echo "   -s Do an import from CSV file system metadata into the system metadata table "
    echo "   -c Creates a database and user by this name.  By default, your username is used."
    echo "   -n Do not verify database preconditions or create db if absent"
    echo "   -d DBSERVER - defaults to hc-dev3"
    echo "   -t [testname] run test 'testname'. 'check' functions have return codes, 'print' display only."
    echo "              'print' versions show more complete test documentation"
    echo "              'exp' notation means that the test or query is still in development; use at your own risk."
    echo "              The multiple image feature isn't currently validated."
    echo "              'done' notation means that the query is complete, validated, and documented"
    echo "      correspondanceDisplay - checks footer/system metadata correspondance "
    echo "      correspondanceCheck - checks footer/system metadata correspondance "
    echo "      dateDiskDisplay - show oids and dates where footer dates don't match on disk dates [known to fail] "
    echo "      filesizeDiskDisplay - show oids and filesizes where footer filesizes don't match on disk filesizes (don't match, but still a fun query)"
    echo "      filesizeCheck - check oids and filesizes where metadata filesizes don't match footer filesizes (exp)"
    echo "      filesizeDisplay - show oids and filesizes where metadata filesizes don't match footer filesizes (exp)"
    echo "      fragmentMetadataCheck - Check for fragments that lack metadata"
    echo "      fragmentMetadataDisplay- Display fragments that lack metadata"
    echo "      metadataFragmentCheck - Check for metadata that lack fragments"
    echo "      metadataFragmentDisplay - Display metadata that lack fragments"
    echo "      countOids - display number of unique OIDs on the system "
    echo "      printExtraFrags - prints frags that aren't on any layouts"
    echo "      checkExtraFrags - returns count of frags that aren't on any layouts"
    echo "      printOmittedFrags - inverse of above query - shows exactly which frags are missing.  (slow)"
    echo "      printOtherOmitted - same as above, runs quickly, only prints OIDs. "
    echo "      checkOmittedFrags - prints the number of missing frags, return fail if any. Otherwise return 0 "
    echo "      printBadLayouts - quick running test that identifies and prints chunks with missing or non-layout-conforming fragments."
    echo "      verifyLayout - same as above (printBadLayouts) but only prints the quantity of messed up chunks. this test + checkExtraFrags are a compete layout/fragment check."
    #echo "      lastImport - prints the date of the last import into the validation db "
    echo "      blackCorrespondanceCheck - check black box corresponance. Requires -u (runid) be set"
    echo "      blackCorrespondanceDisplay - check black box corresponance. Requires -u (runid) be set"
    echo "   -d [oid] dump layout info about oid"
    echo "   -r Drop and reset database-name"
    echo "   -u Provide run id for certain queries."
    echo "   -b debug mode - prints all the database transactions."
}
while getopts "u:w:t:d:i:p:m:d:s:l:c:at:hrnb" opt; do
    case $opt in
        t ) TESTNAME=$OPTARG
            ;;
        d ) DUMPOID=$OPTARG
            ;;

        u ) RUNID=$OPTARG
            ;;
        p ) echo "importing statics from file $OPTARG" 2>&1
            STATICSFILE=$OPTARG
            if [ ! -f "$STATICSFILE" ] ; then 
                echo "No import file at $STATICSFILE, aborting." 2>&1
                exit 1
            fi

            ;;
            
        m ) echo "Importing timestamp from $OPTARG"  2>&1
            IMPORTTIMESTAMPFILE=$OPTARG
            if [ ! -f "$IMPORTTIMESTAMPFILE" ] ; then 
                echo "No import file at $IMPORTTIMESTAMPFILE, aborting." 2>&1
                exit 1
            fi
            ;;
        i ) echo "Importing from file $OPTARG"  2>&1
            IMPORTFILE=$OPTARG
            if [ ! -f "$IMPORTFILE" ] ; then 
                echo "No import file at $IMPORTFILE, aborting." 2>&1
                exit 1
            fi

            ;;

        l ) echo "Importing layout from file $OPTARG"  2>&1
            LAYOUTFILE=$OPTARG
            if [ ! -f "$LAYOUTFILE" ] ; then 
                echo "No import file at $LAYOUTFILE, aborting." 2>&1
                exit 1
            fi

            ;;

        s ) echo "Importing system metadata from file $OPTARG"  2>&1
            SYSTEMMETADATAFILE=$OPTARG
            if [ ! -f "$SYSTEMMETADATAFILE" ] ; then 
                echo "No import file at $SYSTEM METADATAFILE, aborting." 2>&1
                exit 1
            fi
            ;;

        a ) echo "turning on append mode"  2>&1
            APPEND=true
            ;;
        d ) 
            DBSERVER=$OPTARG
            ;;
        c ) 
            DBNAME=$OPTARG
            DBUSER=$OPTARG
            ;;
        r ) echo "Deleting database" 2>&1
            DELETEDB=true
            ;;
        n ) echo "No preconditions verified " 2>&1
            NOCHECK=true
            ;;
        h ) printUsage
            exit 0
            ;;
        b ) DEBUGMODE=true
            ;;
        \? )  printUsage
        exit 1
    esac
done

shift $(($OPTIND -1 ))


if [ -z $DBSERVER ] ; then
    DBSERVER="hc-dev3"
fi


if [ -z $DBNAME ] ; then
    echo "Database name not set with -c - dbscript exiting."
    exit 1
fi


if [ -z $DBUSER ] ; then
    echo "Database user not set with -c - dbscript exiting."
    exit 1
fi


if [ ! -z $DELETEDB ] ; then
    systemSetup
    deleteDb
fi

if [ -z $NOCHECK ] ; then
    preconditions
fi





# One of these does nothing, I think.
#

BLACKCORRESPONDANCEQUERY1="select f_oid from fragments where o_extoid not in (select op.api_oid from op where op.run_id='$RUNID')"
BLACKCORRESPONDANCEQUERY2="select api_oid from op where run_id='$RUNID' and api_oid not in (select o_extoid from fragments)"



blackCorrespondanceDisplay() {
    echo "Checks that the op table and the fragment table correspond for a given run." 2>&1
    echo 2>&1
    runDbCommand "$BLACKCORRESPONDANCEQUERY1"
    runDbCommand "$BLACKCORRESPONDANCEQUERY2"
}


blackCorrespondanceCheck() {
    echo "checking $BLACKCORRESPONDANCEQUERY1" 2>&1
    BADCOUNT=`psql -h $DBSERVER -U $DBNAME -d $DBNAME -t -q  -c \
              "select count(*) from ($BLACKCORRESPONDANCEQUERY1) as foo" |  head -1 | awk '{ print \$1 }'`
    
    if [ "$BADCOUNT" -ne 0 ] ; then
        echo "This has $BADCOUNT fragments that bad blackCorrespondance." 2>&1
        exit 1
    fi


    BADCOUNT=`psql -h $DBSERVER -U $DBNAME -d $DBNAME -t -q  -c \
              "select count(*) from ($BLACKCORRESPONDANCEQUERY2) as foo" |  head -1 | awk '{ print \$1 }'`
    
    if [ "$BADCOUNT" -ne 0 ] ; then
        echo "This has $BADCOUNT fragments with bad blackCorrespondance." 2>&1
        exit 1
    fi

    exit 0
}



if [ ! -z $IMPORTTIMESTAMPFILE ] ; then 

    serialImport $IMPORTTIMESTAMPFILE imports
    echo "done setting import timestamp" 2>&1
fi

if [ ! -z $IMPORTFILE ] ; then 
    parallelImport $IMPORTFILE fragments
    echo "done importing data." 2>&1
fi

if [ ! -z $LAYOUTFILE ] ; then 
    parallelImport $LAYOUTFILE layout
    echo "done importing layout." 2>&1
fi


if [ ! -z $STATICSFILE ] ; then 
    serialImport $STATICSFILE statics
    echo "done importing statics." 2>&1
fi


if [ ! -z $SYSTEMMETADATAFILE ] ; then 
    parallelImport $SYSTEMMETADATAFILE system_metadata
    echo "done importing system metadata." 2>&1
fi

if [ ! -z $TESTNAME ] ; then 
    runTest $TESTNAME
fi

if [ ! -z $DUMPOID ] ; then 
    dumpOidData $DUMPOID
fi


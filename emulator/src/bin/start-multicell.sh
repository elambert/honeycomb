#!/bin/bash
#
# $Id:
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

# Directory on top of this script
DIR=`cd \`dirname $0\`/..; pwd`


# default cellid
DEFAULT_CELLID=0

# default number of nodes
DEFAULT_NUMNODES=16


# default protocol port
DEFAULT_PROTOCOL_PORT=8080

# default mgmt server port
DEFAULT_MGMT_PORT=9000

# default local address
DEFAULT_LOOPBACK="127.0.0.1"

# default log level for multicell
DEFAULT_MC_LOG_LEVEL=2

# default frequency to refresh the load from each cell : 30 seconds
DEFAULT_MC_SILO_POT_REFRESH=30000

#
#
# Arguments
#
DATA_VIP="$DEFAULT_LOOPBACK:$DEFAULT_PROTOCOL_PORT"
ADMIN_VIP="$DEFAULT_LOOPBACK:$DEFAULT_MGMT_PORT"
SP_VIP=$DEFAULT_LOOPBACK
GATEWAY_VIP=$DEFAULT_LOOPBACK
CELLID=$DEFAULT_CELLID
NUMNODES=$DEFAULT_NUMNODES
RESTART="false"
WIPE="false"

DATA_PORT=$DEFAULT_PROTOCOL_PORT
MGMT_PORT=$DEFAULT_MGMT_PORT

# properties that need to be patched/added
PROP_CELLID="honeycomb.silo.cellid"
PROP_NUMNODES="honeycomb.cell.num_nodes"
PROP_PROTOCOL_PORT="honeycomb.protocol.port"
PROP_MGMT_PORT="honeycomb.cell.mgmt.port"
PROP_EMULATOR_ROOT="honeycomb.emulator.root"
PROP_MC_LOG_LEVEL="honeycomb.multicell.loglevel"
PROP_MC_SILO_POT_REFRESH="honeycomb.cell.pot.refresh"

# add dummy silo props for multicell validity checking
PROP_NTP_SERVER="honeycomb.cell.ntp"
PROP_SMTP_SERVER="honeycomb.cell.smtp.server"
PROP_SMTP_PORT="honeycomb.cell.smtp.port"
PROP_AUTH_CLIENTS="honeycomb.security.authorized_clients"
PROP_EXT_LOGGER="honeycomb.cell.external_logger"
PROP_DNS_CONFIGURED="honeycomb.cell.dns"
PROP_DOMAIN_NAME="honeycomb.cell.domain_name"
PROP_DNS_SEARCH="honeycomb.cell.dns_search"
PROP_DNS_PRIMARY_SERVER="honeycomb.cell.primary_dns_server"
PROP_DNS_SECONDARY_SERVER="honeycomb.cell.secondary_dns_server"
PROP_HONEYCOMB_SOFTWARE_VERSION="honeycomb.software.version"

DUMMY_NTP_SERVER="10.10.10.10"
DUMMY_SMTP_SERVER="11.11.11.11"
DUMMY_SMTP_PORT="25"
DUMMY_AUTH_CLIENTS="all"
DUMMY_EXT_LOGGER="12.12.12.12"
DUMMY_DNS_CONFIGURED="n"
DUMMY_DOMAIN_NAME="dns_dummy"
DUMMY_DNS_SEARCH="dummy.com"
DUMMY_DNS_PRIMARY_SERVER="14.14.14.14"
DUMMY_DNS_SECONDARY_SERVER="15.15.15.15"
DUMMY_HONEYCOMB_SOFTWARE_VERSION="Emulator"

# classpath...
CLASSPATH=\
$DIR/lib/bsh-2.0b2.jar:\
$DIR/lib/honeycomb-common.jar:\
$DIR/lib/servlet-4.2.19.jar":\
$DIR/lib/jetty-4.2.20.jar:\
$DIR/lib/concurrent.jar:\
$DIR/lib/honeycomb-emulator.jar":\
$DIR/lib/jug.jar:\
$DIR/lib:\
$DIR/lib/derby-10.1.1.0.jar:\
$DIR/lib/servlet-4.2.19.jar:\
$DIR/lib/honeycomb-mgmt.jar:\
$DIR/lib/activation.jar:\
$DIR/lib/honeycomb-server.jar:\
$DIR/lib/servicetags-api.jar:\



SILO_INFO_SCRIPT=./create_multicell_config

EMULATOR_ROOT=
EMULATOR_CONFIG_FILENAME=emulator.config

create_directory() {
    if [ ! -d $1 ]; then
        mkdir $1;
    fi
}

set_data_port() {
    echo $DATA_VIP | grep ":" > /dev/null
    if [ $? -eq 0 ]; then
        DATA_PORT=`echo $DATA_VIP | cut -d ":" -f 2`
    else 
        DATA_PORT=$DEFAULT_PROTOCOL_PORT
    fi
}

set_mgmt_port() {
    echo $ADMIN_VIP | grep ":" > /dev/null
    if [ $? -eq 0 ]; then
        MGMT_PORT=`echo $ADMIN_VIP | cut -d ":" -f 2`
    else 
        MGMT_PORT=$DEFAULT_MGMT_PORT
    fi
}


prepare_directory_structure() {
    create_directory $DIR/$CELLID
    create_directory $DIR/$CELLID/config
}

add_property_if_missing() {

    local prop=$1
    local val=$2
    local property_file=$3
    # Do a word grep match since some properties
    # share the same name
    grep -w $prop $property_file > /dev/null
    if [ $? -ne 0 ]; then 
        echo "$prop = $val" >> $property_file
    fi
}

patch_property_file() {

    local property_file=$1
    local new_file=/tmp/new_config_file.$$
    local cur_prop
    local cur_val
    local cur_line

    rm -f $new_file
    
    cat $property_file | while read line; do
        echo $line | grep $PROP_EMULATOR_ROOT > /dev/null
        if [ $? -eq 0 ]; then 
            continue
        fi
        echo $line | egrep "($PROP_CELLID|$PROP_NUMNODES|$PROP_PROTOCOL_PORT|$PROP_MGMT_PORT)" > /dev/null
        if [ $? -eq 0 ]; then 
            cur_prop=`echo $line | cut -d "=" -f 1`
            if [ $cur_prop ==  $PROP_CELLID ]; then
                cur_line="$cur_prop = $CELLID"
            fi
            if [ $cur_prop ==  $PROP_NUMNODES ]; then
                cur_line="$cur_prop = $NUMNODES"
            fi
            if [ $cur_prop ==  $PROP_PROTOCOL_PORT ]; then
                cur_line="$cur_prop = $DATA_PORT"
            fi
            if [ $cur_prop ==  $PROP_MGMT_PORT ]; then
                cur_line="$cur_prop = $MGMT_PORT"
            fi
        else
            cur_line=$line
        fi
        echo $cur_line >> $new_file
    done

    add_property_if_missing $PROP_EMULATOR_ROOT $EMULATOR_ROOT $new_file
    add_property_if_missing $PROP_CELLID $CELLID $new_file
    add_property_if_missing $PROP_NUMNODES $NUMNODES $new_file
    add_property_if_missing $PROP_PROTOCOL_PORT $DATA_PORT $new_file
    add_property_if_missing $PROP_MGMT_PORT $MGMT_PORT $new_file
    add_property_if_missing $PROP_MC_LOG_LEVEL $DEFAULT_MC_LOG_LEVEL $new_file
    add_property_if_missing $PROP_MC_SILO_POT_REFRESH  $DEFAULT_MC_SILO_POT_REFRESH $new_file


    add_property_if_missing $PROP_NTP_SERVER  $DUMMY_NTP_SERVER $new_file
    add_property_if_missing $PROP_SMTP_SERVER $DUMMY_SMTP_SERVER  $new_file
    add_property_if_missing $PROP_SMTP_PORT  $DUMMY_SMTP_PORT $new_file
    add_property_if_missing $PROP_AUTH_CLIENTS  "$DUMMY_AUTH_CLIENTS" $new_file
    add_property_if_missing $PROP_EXT_LOGGER  $DUMMY_EXT_LOGGER $new_file
    add_property_if_missing $PROP_DNS_CONFIGURED  $DUMMY_DNS_CONFIGURED $new_file
    add_property_if_missing $PROP_DOMAIN_NAME $DUMMY_DOMAIN_NAME $new_file
    add_property_if_missing $PROP_DNS_SEARCH $DUMMY_DNS_SEARCH $new_file
    add_property_if_missing $PROP_DNS_PRIMARY_SERVER $DUMMY_DNS_PRIMARY_SERVER $new_file
    add_property_if_missing $PROP_DNS_SECONDARY_SERVER $DUMMY_DNS_SECONDARY_SERVER $new_file
    add_property_if_missing $PROP_HONEYCOMB_SOFTWARE_VERSION $DUMMY_HONEYCOMB_SOFTWARE_VERSION $new_file
    mv $new_file $property_file
}


populate_config_files() {

    local config_dir=$DIR/config
    local cell_config_dir=$DIR/$CELLID/config
    local tmp

    # copy metdata config files
    if [ -f $config_dir/metadata_config_factory.xml ]; then
        cp $config_dir/metadata_config_factory.xml $cell_config_dir
    fi

    if [ -f $config_dir/metadata_config.xml ]; then
        cp $config_dir/metadata_config.xml $cell_config_dir
    fi

    # copy property file
    if [ -f $config_dir/cluster_config.properties ]; then
        cp $config_dir/cluster_config.properties $cell_config_dir/$EMULATOR_CONFIG_FILENAME
    fi

    # copy adm emulator files
    emConfigs=$(ls $config_dir/admEmulator*)
    for config in $emConfigs; do
        cp $config $cell_config_dir
    done

    

#    if [ -f $config_dir/admEmulator.config ]; then
#        
#        cp $config_dir/admEmulator.config $cell_config_dir/admEmulator.config
#    fi

    # patch default config file
    patch_property_file $cell_config_dir/$EMULATOR_CONFIG_FILENAME

    # create silo_info.xml
    rm -f $cell_config_dir/silo_info*
    $SILO_INFO_SCRIPT -d $cell_config_dir -n emulator-$CELLID -a $ADMIN_VIP -t $DATA_VIP -s $SP_VIP -g $GATEWAY_VIP -z $DEFAULT_LOOPBACK -c $CELLID
    tmp=`ls $cell_config_dir | grep silo_info.xml`
    cd $cell_config_dir
    ln -s $tmp silo_info.xml
}

start_emulator() {
    java -Xms64m -classpath "$CLASSPATH" -Dsun.io.useCanonCaches=false \
	-Djava.library.path="$DIR/lib"  \
	-Demulator.root="$EMULATOR_ROOT" \
	-Dmd.cache.path="$DIR/lib/md_caches" \
	-Dfscache.class=com.sun.honeycomb.fscache.DerbyFileCache $DEBUG_FLAGS \
	-Duid.lib.path=emulator com.sun.honeycomb.cm.NodeMgr 
    if [ $? -ne 0 ]; then
        echo "A problem was encountered in starting the emulator"
        exit 1
    fi
}

save_logs() {
    if [ -f $DIR/$CELLID/logs/emulator.log ]; then
        cp $DIR/$CELLID/logs/emulator.log $DIR/$CELLID/logs/emulator.log.$$
    fi
}

wipe_data() {
    if [ -d $DIR/$CELLID/var/data ]; then
        rm -f $DIR/$CELLID/var/data/*
    fi    
}

wipe_metadata() {
    return;
}

usage() {
    echo "$0 -c <cellid> -d <data_vip> -a <admin_vip> [options]"
    echo
    echo "Options:"
    echo " -c <cellid>		The cell id to use for this emulator instance"
    echo " -a <admin_ip>          Set the Administrative IP.  This is the address"
    echo "                        the cli will have to connect to to work against"
    echo "                        this cell."
    echo " -d <data_ip>		Set the Data IP Address"
    echo " -s <sp_ip>		Set the Service Node IP Address"
    echo " -g <gateway_ip>  	Set the Gateway Address"
    echo " -n [8|16]		Number of nodes"
    echo " -r 			Restart the emulator without recreating the "
    echo "                        directory structure."
    echo " -w			Wipe the schema"
    echo
    exit 1
}


#
# TODO: Need to add some basic validation
# to parameters passed in by user
#
while getopts ":d:a:s:g:c:n:r" options; do
    case $options in
    d ) DATA_VIP=$OPTARG
        ;;
    a ) ADMIN_VIP=$OPTARG
        ;;
    s ) SP_VIP=$OPTARG
	;;
    g ) GATEWAY_VIP=$OPTARG
	;;
    c ) CELLID=$OPTARG
	;;
    n ) NUMNODES=$OPTARG;;
    r ) RESTART="true";;
    w ) WIPE="true";;
    h ) usage;;
    * ) usage;;
    esac
done

EMULATOR_ROOT=$DIR/$CELLID
echo EMULATOR_ROOT is $EMULATOR_ROOT

set_data_port
set_mgmt_port

echo " will emulator for cell $CELLID, num nodes $NUMNODES, adminVIP = $ADMIN_VIP (port $MGMT_PORT) dataVIP = $DATA_VIP (port = $DATA_PORT) spVIP = $SP_VIP gatewayVIP = $GATEWAY_VIP"

if [ $RESTART == "true" ]; then
    save_logs
    if [ $WIPE == "true" ]; then
        wipe_data
        wipe_metadata
    fi
else
    prepare_directory_structure
    populate_config_files
    wipe_data
    wipe_metadata
    echo " created initial configuration for emulator-cell-$CELLID"
fi

start_emulator

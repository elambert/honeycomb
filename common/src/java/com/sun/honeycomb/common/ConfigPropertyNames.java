/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



package com.sun.honeycomb.common;

public interface ConfigPropertyNames {


    static final public String PROP_NTP_SERVER  = "honeycomb.cell.ntp";
    static final public String PROP_SMTP_PORT   = "honeycomb.cell.smtp.port";
    static final public String PROP_SMTP_SERVER = "honeycomb.cell.smtp.server";

    // Spreader properties.
    static final public String PROP_AUTH_CLI =
        "honeycomb.security.authorized_clients";
    static final public String PROP_AUTH_NUM_RULES =
        "honeycomb.switch_mgr.spreader.authrules";
    static final public String PROP_NUMNODES =
        "honeycomb.cell.num_nodes";
    static final public String PROP_SWITCH_ARP_DEST =
        "honeycomb.switch_mgr.arp.destination";
    static final public String PROP_SWITCH_ICMP_DEST =
        "honeycomb.switch_mgr.icmp.destination";
    static final public String PROP_SWITCH_VERIFY =
        "honeycomb.switch_mgr.rules.verify";
    static final public String PROP_RESET_FAILURE_LIMIT =
        "honeycomb.switch_mgr.reset.failure.limit";
    static final public String PNAME_AUTH_CLIENTS =
        "honeycomb.security.authorized_clients";
    static final public String PROP_SPREADER_ADDRBITS =
        "honeycomb.switch_mgr.spreader.addrbits";
    static final public String PROP_SPREADER_AUTHRULES =
        "honeycomb.switch_mgr.spreader.authrules";
    static final public String NDMP_INBOUND_DATA_PORT =
        "honeycomb.ndmp.InboundDataPort";
    static final public String NDMP_OUTBOUND_DATA_PORT =
        "honeycomb.ndmp.OutboundDataPort";


    static final public String PROP_EXT_LOGGER  = "honeycomb.cell.external_logger";
    static final public String PROP_DISK_CHECK_INTERVAL =
        "honeycomb.disks.check.interval";
    static final public String PROP_DISK_CM_NOTIFY_LIMIT =
        "honeycomb.disks.cm.notify.tries";
    static final public String PROP_DISK_DEBUG_ERROR_RATE =
        "honeycomb.disks.simulate.error.rate";
    static final public String PROP_DISK_FSCK_TIMEOUT =
        "honeycomb.disks.fsck.timeout";
    static final public String PROP_DISK_KERNEL_LOG =
        "honeycomb.disks.log.kernel_errors";
    static final public String PROP_DISK_MOUNT_OPTIONS =
        "honeycomb.disks.mount.options";
    static final public String PROP_DISK_NEWFS_ON_FAILED_FSCK =
        "honeycomb.disks.newfs.on_failed_fsck";
    static final public String PROP_DISK_NEWFS_ON_FAILED_MOUNT =
        "honeycomb.disks.newfs.on_failed_mount";
    static final public String PROP_DISK_NEWFS_OPTIONS =
        "honeycomb.disks.newfs.options";
    static final public String PROP_DISK_NEWFS_TIMEOUT =
        "honeycomb.disks.newfs.timeout";
    static final public String PROP_DISK_FSCK_CHECK_COMMAND =
        "honeycomb.disks.fsck.check_command";
    static final public String PROP_DISK_FSCK_REPAIR_COMMAND =
        "honeycomb.disks.fsck.repair_command";
    static final public String PROP_DISK_FSCK_MIN_TRIES =
        "honeycomb.disks.fsck.min_tries";
    static final public String PROP_DISK_FSCK_MAX_TRIES =
        "honeycomb.disks.fsck.max_tries";
    static final public String PROP_DISK_FSCK_NUM_CONSEC =
        "honeycomb.disks.fsck.num_consec";
    static final public String PROP_DISK_POLL_INTERVAL =
        "honeycomb.disks.poll_interval";
    static final public String PROP_DISK_THRESHOLD =
        "honeycomb.disks.error.threshold";
    static final public String PROP_DISK_UNMOUNT_ON_EXIT =
        "honeycomb.disks.on_exit.unmount";
    static final public String PROP_DISK_UNMOUNT_ON_EXIT_DELAY =
        "honeycomb.disks.on_exit.unmount.delay";
    static final public String PROP_DISK_USAGE_CAP =
        "honeycomb.disks.usage.cap";

    static final public String PROP_DNS ="honeycomb.cell.dns";
    static final public String PROP_DOMAIN_NAME ="honeycomb.cell.domain_name";
    static final public String PROP_DNS_SEARCH ="honeycomb.cell.dns_search";
    static final public String PROP_PRIMARY_DNS_SERVER ="honeycomb.cell.primary_dns_server";
    static final public String PROP_SECONDARY_DNS_SERVER ="honeycomb.cell.secondary_dns_server";
    static final public String PROP_SMTP_CC     = "honeycomb.alerts.smtp.cc";
    static final public String PROP_SMTP_TO     = "honeycomb.alerts.smtp.to";
    static final public String PROP_SMTP_FROM     = "honeycomb.alerts.smtp.from_name";
    static final public String PROP_CLIENT_PORT = "honeycomb.client.port";
    static final public String PROP_ADMIN_PASSWD = "honeycomb.admin.password";
    static final public String PROP_ADMIN_PUBKEY = "honeycomb.admin.pubkey";
    static final public String PROP_ADMIN_USER = "honeycomb.admin.user";
    static final public String PROP_LANGUAGE    = "honeycomb.language";
    static final public String PROP_CELLID      = "honeycomb.silo.cellid";
    static final public String PROP_DATA_LOSS   = "honeycomb.datadoctor.possible_data_loss";
    static final public String PROP_LICENSE     = "honeycomb.admin.license";
    static final public String PROP_ACCEPT_8_NODES = "honeycomb.multicell.support_8_nodes";
    static final public String PROP_NUM_NODES   = "honeycomb.cell.num_nodes";
    static final public String PROP_WEDEV_AUTH_HASH   = "honeycomb.webdav.auth.hash";
    static final public String PROP_WEDEV_AUTH_DELIM = "honeycomb.webdav.auth.delimiter";
    static final public String PROP_CONFIG_VERSION = "honeycomb.config.version";
    
    // Phome Home properties
    static final public String PROP_PHONEHOME_SMTP_TO = 
	"honeycomb.alerts.phonehome.smtp.to";
    static final public String PROP_PHONEHOME_FREQUENCY = 
	"honeycomb.alerts.phonehome.frequency";
    static final public String PROP_PHONEHOME_LAST_TIMESTAMP = 
	"honeycomb.alerts.phonehome.timestamp";
    
    /*
     * Cluster expansion
     */
    static final public String PROP_EXP_STATUS  = "honeycomb.layout.expansion_status";
    static final public String PROP_EXP_START   = "honeycomb.layout.expansion_start_timestamp";
    static final public String PROP_EXP_ENABLED_MASK   = "honeycomb.layout.expansion.enabled.disks";
    static final public String PROP_EXP_AVAILABLE_MASK = "honeycomb.layout.expansion.available.disks";

    /*
     * CM properties
     */
    static final public String PROP_CM_SINGLEMODE = "honeycomb.cm.cmm.singlemode";
    static final public String PROP_CM_CFGNODES = "honeycomb.cm.cmm.nodes";
    static final public String PROP_CM_DOQUORUM = "honeycomb.cm.quorum.do_check";
    static final public String PROP_CM_QUORUM_THRESHOLD = "honeycomb.cm.quorum.threshold";
    static final public String PROP_CM_FAILURE_CHECK = "honeycomb.cm.unhealedfailures.do_check";
    
    /*
     * Threads Pool runtime config.
     */
    static final String PROP_PROTOCOL_MINTHREADS = "honeycomb.protocol.jetty.minthreads";
    static final String PROP_PROTOCOL_MAXTHREADS = "honeycomb.protocol.jetty.maxthreads";
    static final String PROP_PROTOCOL_MAXIDLETIME = "honeycomb.protocol.maxidletime";
    static final String PROP_MDSERVER_MAXTHREADS = "honeycomb.mdserver.threads";
    static final String PROP_CM_NUMTHREADS = "honeycomb.cm.cmagent.num_threads";
    static final String PROP_CM_MAXTHREADS = "honeycomb.cm.cmagent.max_threads";
    static final String PROP_OA_MAXPOOLS = "honeycomb.oa.pools.max";
    
    /*
     * Data Doctor Constants
     */
    
    static final String PROP_REMOVE_TEMP_WAIT_WINDOW = 
                      "honeycomb.datadoctor.remove_temp_frags.step_wait_window";

    /*
     * HADB config properties
     */

    static final String HADB_DATABASE_SIZE_PROPERTY =
        "honeycomb.hadb.database_size";
    static final String HADB_DATABASE_RELALG_PROPERTY =
        "honeycomb.hadb.relalg_size";
    static final String HADBM_MAX_MA_LOOPS =
        "honeycomb.hadb.ma.max_loops";
    
    /**
     * Time of last HADB create,
     * or 0 if db is currently wiped or being created.
     */
    static final String PROP_HADBM_LAST_CREATE_TIME =
        "honeycomb.hadb.last_create_time";
    
    /**
     * The amount of time the HADB periodic check gives the 
     * node to be in a stopped state before trying to start it
     */
    static final String PROP_HADBM_NODE_IS_STOPPED_TIMEOUT =
        "honeycomb.hadb.node_is_stopped_timeout";
    
    /**
     * The amount of time the HADB periodic check will allow a node to be in 
     * non-running, non-stopped state before it wipes it
     */
    static final String PROP_HADBM_WIPE_NODE_TIMEOUT =
        "honeycomb.hadb.wipe_node_timeout";
    /**
     * The amount of time will wait for an HADB node 
     * to start or be reconfigured 
     */
    static final String PROP_HADBM_NODE_START_TIMEOUT =
        "honeycomb.hadb.node_start_timeout";
    
    /*
     *  Config properties for the Explorer tool required to run logdump command
     */
    static final String PROP_LOGDUMP_EXP_LIB = "honeycomb.logdump.exp.lib";
    static final String PROP_LOGDUMP_EXP_PATH = "honeycomb.logdump.exp.path"; 
    static final String PROP_LOGDUMP_EXP_VERSION = 
            "honeycomb.logdump.exp.version";
    static final String PROP_LOGDUMP_EXP_TRANSPORT = 
            "honeycomb.logdump.exp.transport";
    static final String PROP_LOGDUMP_EXP_HTTPS_INTERVAL = 
            "honeycomb.logdump.exp.https.interval";
    static final String PROP_LOGDUMP_EXP_HTTPS_TRIES = 
            "honeycomb.logdump.exp.https.tries";
    static final String PROP_LOGDUMP_EXP_CONTACT_NAME = 
            "honeycomb.logdump.exp.contact.name"; 
    static final String PROP_LOGDUMP_EXP_CONTACT_PHONE = 
            "honeycomb.logdump.exp.contact.phone"; 
    static final String PROP_LOGDUMP_EXP_CONTACT_EMAIL = 
            "honeycomb.logdump.exp.contact.email";
    static final String PROP_LOGDUMP_EXP_PROXY_SERVER = 
            "honeycomb.logdump.exp.proxy.server";
    static final String PROP_LOGDUMP_EXP_PROXY_PORT = 
            "honeycomb.logdump.exp.proxy.port";
    static final String PROP_LOGDUMP_EXP_GEO =
            "honeycomb.logdump.exp.contact.geo";

    /**
     * Flag used to tell the service tag service whether it
     * is enabled or not when running as a single cell system.
     * This value is expected to always be false except in the rare
     * condition where two cells share the same top level assembly component 
     * # (ie there in the same rack) but are not joined into a hive.
     * In this case the service tag service needs to be disabled on one
     * of the cells to prevent both cells from ansering a service tag
     * registration request.  Reporting the same information twice to
     * the service tag registration client is seen as an invalid configuration.
     */
    static final String PROP_SERVICE_TAG_SERVICE_DISABLED =
        "honeycomb.servicetags.service_disabled";
};

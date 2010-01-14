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

/**
 *  Contains static constants for the message keys in
 *  the AdminResources properties file located in server
 */
public interface AdminResourcesConstants {
    
    public static final String RESOURCE_BUNDLE_NAME = "AdminResources";

    // Managing Alerts
    public static final String MSG_KEY_SET_TO_EMAIL = "info.adm.setToEmail";
    public static final String MSG_KEY_SET_CC_EMAIL = "info.adm.setCCEmail";
    public static final String MSG_KEY_ADD_TO_EMAIL = "info.adm.addToEmail";
    public static final String MSG_KEY_ADD_CC_EMAIL = "info.adm.addCCEmail";
    public static final String MSG_KEY_DEL_TO_EMAIL = "info.adm.delToEmail";
    public static final String MSG_KEY_DEL_CC_EMAIL = "info.adm.delCCEmail";
    
    // Cell Config Properties
    public static final String MSG_KEY_SET_ADMIN_IP = "info.adm.setAdminIP";
    public static final String MSG_KEY_SET_DATA_IP = "info.adm.setDataIP";
    public static final String MSG_KEY_SET_SUBNET_IP = "info.adm.setSubnetIP";
    public static final String MSG_KEY_SET_GATEWAY_IP = "info.adm.setGatewayIP";
    public static final String MSG_KEY_SET_SUBNET_MASK =
            "info.adm.setSubnetMask";
    public static final String MSG_KEY_SET_SERVICE_NODE_IP =
            "info.adm.setServiceNodeIP";
    public static final String MSG_KEY_SETUP_CELL_IP_CONFIG =
            "info.adm.setupCellConfig";
    public static final String MSG_KEY_SETUP_CELL_HIVE_CONFIG =
            "info.adm.setupCellHiveConfig";
    public static final String MSG_KEY_SETUP_CELL_DNS_ENABLED =
            "info.adm.setupCellDNSEnabled";
    public static final String MSG_KEY_SETUP_CELL_DNS_DISABLED =
            "info.adm.setupCellDNSDisabled";
    
    // Cell Admin
    public static final String MSG_KEY_START_EXPANSION =
            "info.adm.startExpansion";
    
    // Silo Properties - hivecfg
    public static final String MSG_KEY_SET_SMTP_SERVER = 
            "info.adm.setSMTPServer";
    public static final String MSG_KEY_SET_SMTP_PORT = "info.adm.setSMTPPort";
    public static final String MSG_KEY_SET_NTP_SERVER = "info.adm.setNTPServer";
    public static final String MSG_KEY_SET_AUTH_CLIENTS =
            "info.adm.setDataClients";
    public static final String MSG_KEY_SET_DNS = "info.adm.setDNS";
    public static final String MSG_KEY_SET_EXT_LOGGER =
            "info.adm.setExternalLogger";
    public static final String MSG_KEY_SET_DNS_PARAMS =
            "info.adm.setDNSParams";
    public static final String MSG_KEY_SET_DOMAIN_NAME =
            "info.adm.setDomainName";
    public static final String MSG_KEY_SET_PRIMARY_DNS_SERVER =
            "info.adm.setPrimaryDNSServer";
    public static final String MSG_KEY_SET_SECONDARY_DNS_SERVER =
            "info.adm.setSecondaryDNSServer";
    public static final String MSG_KEY_SET_DNS_SEARCH_PATH =
            "info.adm.setDNSSearchPath";
    
    // Hive adm
    public static final String MSG_KEY_ADD_CELL = "info.adm.addCell";
    public static final String MSG_KEY_REMOVE_CELL = "info.adm.removeCell";
    
    // HW Config
    public static final String MSG_KEY_WIPE_CELL_DISKS =
            "info.adm.wipeCellDisks";
    public static final String MSG_KEY_NODE_POWER_ON =
            "info.adm.powerOnNode";
    
    public static final String MSG_KEY_NODE_POWER_OFF =
            "info.adm.powerOffNode";
    
    public static final String MSG_KEY_NODE_POWER_OFF_IPMI =
            "info.adm.powerOffNodeIpmi";
    
    public static final String MSG_KEY_ENABLE_DISK =
            "info.adm.enableDisk";
    
    public static final String MSG_KEY_DISABLE_DISK =
            "info.adm.disableDisk";
    
    public static final String MSG_KEY_ONLINE_DISK =
            "info.adm.onlineDisk";
    
    public static final String MSG_KEY_OFFLINE_DISK =
            "info.adm.offlineDisk";
    
    public static final String MSG_KEY_WIPE_DISK =
            "info.adm.wipeDisk";
    
    // Schema changes
    public static final String MSG_KEY_UPDATE_SCHEMA ="info.adm.updateSchema";
    public static final String MSG_KEY_RETRY_UPDATE_SCHEMA =
            "info.adm.retryUpdateSchema";
    public static final String MSG_KEY_WIPE_SCHEMA =
            "info.adm.wipeSchema";
    
    // Login/logout
    public static final String MSG_KEY_LOGIN = "info.adm.login";
    public static final String MSG_KEY_LOGOUT = "info.adm.logout";
    
    // Data Doctor Configuration ddcfg
    public static final String MSG_KEY_DATA_DOC_SET_DEFAULTS =
            "info.adm.DataDoc.setDefaults";
    public static final String MSG_KEY_DATA_DOC_SET_OFF =
            "info.adm.DataDoc.setOff";
    public static final String MSG_KEY_DATA_DOC_SET_FULLSPEED =
            "info.adm.DataDoc.setFullspeed";
    public static final String MSG_KEY_DATA_DOC_SET =
            "info.adm.DataDoc.set";
    public static final String MSG_KEY_DATA_DOC_SET_DUP_FRAGS  =
            "info.adm.DataDoc.setDupFrags";
    public static final String MSG_KEY_DATA_DOC_SET_TEMP_FRAGS  =
            "info.adm.DataDoc.setTempFrags";
    public static final String MSG_KEY_DATA_DOC_SET_POP_SYS_CACHE  =
            "info.adm.DataDoc.setPopSysCache";
    public static final String MSG_KEY_DATA_DOC_SET_POP_EXT_CACHE  =
            "info.adm.DataDoc.setPopExtCache";
    public static final String MSG_KEY_DATA_DOC_SET_RECOVER_LOST_FRAGS  =
            "info.adm.DataDoc.setRecoverLostFrags";
    public static final String MSG_KEY_DATA_DOC_SET_SCAN_FRAGS  =
            "info.adm.DataDoc.setScanFrags";
    public static final String MSG_KEY_DATA_DOC_SET_SLOSH_FRAGS  =
            "info.adm.DataDoc.setSloshFrags";
    
    // HADB Config
    public static final String MSG_KEY_SET_CLEAR_HADB_FAILURE =
            "info.adm.clearHadbFailure";
    
    // Miscellaneous
    public static final String MSG_KEY_REBOOT = "info.adm.reboot";
    public static final String MSG_KEY_REBOOT_SWITCH = "info.adm.rebootSwitch";
    public static final String MSG_KEY_REBOOT_SERVICE_PROCESSOR =
            "info.adm.rebootServiceProcessor";
    public static final String MSG_KEY_REBOOT_ALL = "info.adm.rebootAll";
    public static final String MSG_KEY_REBOOT_NODE = "info.adm.rebootNode";
    
    public static final String MSG_KEY_SHUTDOWN = "info.adm.shutdown";
    public static final String MSG_KEY_SHUTDOWN_IPMI = 
            "info.adm.shutdown.ipmi";
    public static final String MSG_KEY_SHUTDOWN_ALL = "info.adm.shutdownAll";
    public static final String MSG_KEY_SHUTDOWN_ALL_IPMI = 
            "info.adm.shutdownAll.ipmi";
    public static final String MSG_KEY_WIPE = "info.adm.wipe";
    public static final String MSG_KEY_SET_PASSWORD = "info.adm.setPassword";
    public static final String MSG_KEY_SET_PROTOCOL_PASSWORD =
            "info.adm.setProtocolPassword";

    // Upgrade 
    public static final String MSG_KEY_START_UPGRADE = "info.adm.startUpgrade";
    public static final String MSG_KEY_UPGRADE_VERSION = "info.adm.upgradeVersion";
    
    public static final String MSG_KEY_SET_LICENSE =
            "info.adm.setLicense";
    public static final String MSG_KEY_SET_LOCALE =
            "info.adm.setLocale";
    
    public static final String MSG_KEY_START_CELL_EXPANSION =
            "info.adm.startCellExpansion";
    public static final String MSG_KEY_CANCEL_CELL_EXPANSION =
            "info.adm.cancelCellExpansion";
    
    public static final String MSG_KEY_SERVICE_TAG_UPDATE_CELL =
            "info.adm.updateCellServiceTagData";
    
    public static final String MSG_KEY_SERVICE_TAG_REFRESH_REGISTRY =
            "info.adm.refreshServiceTagRegistry";
    public static final String MSG_KEY_SERVICE_TAG_DISABLE_REGISTRY =
            "info.adm.disableServiceTagRegistry";
    public static final String MSG_KEY_SERVICE_TAG_ENABLE_REGISTRY =
            "info.adm.enableServiceTagRegistry";
    public static final String KEY_SERVICE_TAG_PRODUCT_NAME =
            "servicetag.productName";
    public static final String KEY_SERVICE_TAG_PRODUCT_SOFTWARE_NAME =
            "servicetag.productSoftwareName";
    public static final String KEY_SERVICE_TAG_INSTANCE_ID =
            "servicetag.productInstanceID";
    
    /**
     * This is used to value that we use to search the service tag
     * registry to tell whether it needs updating or not
     */
    public static final String KEY_SERVICE_TAG_SEARCH_STR =
            "servicetag.searchKey";
    /**
     * The prefix key that is used to look up the description for a part #
     * For example a lookup in the message file for
     * PREFIX_KEY_SERVICE_TAG_PART_NUM_TO_PRODUCT_NAME+594-4516-02 will return
     * the description for that part number if it's known
     */
    public static final String PREFIX_KEY_SERVICE_TAG_PART_NUM_TO_PRODUCT_NAME=
            "servicetag.desc.";
    /**
     * The prefix key that is used to look up the marketing # for a part #
     */
    public static final String PREFIX_KEY_SERVICE_TAG_PART_NUM_TO_MARKETING_NUMBER=
            "servicetag.marketingNum.";
    
    
    //
    // Alert Message Constants
    //
    public static final String MSG_KEY_ALERT_LOST_QUORUM =
            "alertMail.lostQuorum";
    public static final String MSG_KEY_ALERT_GAINED_QUORUM =
            "alertMail.gainedQuorum";
    public static final String MSG_KEY_ALERT_DISK_INIT_ERR_DISABLED =
            "alertMail.err.disk.disabled";
    
    /*
     *  Other messages
     */
    public static final String MSG_KEY_BIOS_VERSION =
            "info.platform.biosVersion";
}

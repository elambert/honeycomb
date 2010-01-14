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


public interface CliConstants {

    /**
     * General command success status code
     */
    public static final int SUCCESS = 0;

    /**
     * General command failure status code
     */
    public static final int FAILURE = -1;

    /**
     * General failure for not found
     */
    public static final int NOT_FOUND = -2;
    
    // service tag return code
    /**
     * Return code that indicates the service tag registry was not
     * updated.  This isn't preceived as a failure since on a multi-cell
     * we can't update the service tag registry automatically.
     * 
     * But on a single cell system we can.
     */
    public static final int SERVICE_TAGS_MULTI_CELL_NOT_UPDATED = 1;
    
    /**
     * A hard failure on the service tag registry
     */
    public static final int SERVICE_TAG_REGISTRY_UPDATE_FAILURE = -2;
    
    /**
     * A validation error
     */
    public static final int SERVICE_TAG_REGISTRY_VALIDATION_FAILURE = -3;

    /**
     * Unable to delete the service tag registration file
     */
    public static final int SERVICE_TAG_REGISTRY_DELETE_FAILURE = -4;
    
    /**
     * The status strings outputed by the cli for the status of the   
     * service tag registry. These values need to be keep in line with the 
     * SERVICE_TAG_REGISTRY_$VALUES defined
     */
    public static final String[] SERVICE_TAG_REGISTRY_STATUS_STR =
            new String[] { "CURRENT", "NEEDS UPDATING", "UNKNOWN" };
   
    public static final int SERVICE_TAG_REGISTRY_ENTRIES_FOUND = 0; 
    public static final int SERVICE_TAG_REGISTRY_NO_ENTRIES_FOUND = 1;
    public static final int SERVICE_TAG_REGISTRY_STATUS_UNKNOWN = 2;
    
    /**
     * The status strings outputed by the cli for the status of the   
     * service tag service. These values need to be keep in line with the 
     * SERVICE_TAG_SERVICE_STATUS_$VALUES defined
     */ 
    public static final String[] SERVICE_TAG_SERVICE_STATUS_STR = {
        "DISABLED","ENABLED","UNKNOWN"};
 
    public static final int SERVICE_TAG_SERVICE_STATUS_DISABLED=0;
    public static final int SERVICE_TAG_SERVICE_STATUS_ENABLED=1;
    public static final int SERVICE_TAG_SERVICE_STATUS_UNKNOWN=2;
    
    //
    // mgmt return code
    //
    public static final int MGMT_OK                         = 0;
    public static final int MGMT_SCHEMA_VERIFICATION_FAILED = -1;
    public static final int MGMT_CMM_CONFIG_UPDATE_FAILED   = -2;
    public static final int MGMT_HADB_LOAD_SCHEMA_FAILED    = -3;
    public static final int MGMT_CANT_RETRIEVE_SCHEMA       = -4;         
    public static final int MGMT_FAILED_TO_CONNECT          = -5;
    public static final int MGMT_RETRY_SCHEMA_FAILED        = -6;
    public static final int MGMT_CLEAR_SCHEMA_FAILED        = -7;
    public static final int MGMT_ALREADY_LOGGED             = -8;
    public static final int MGMT_NOT_MASTER_CELL            = -9;
    public static final int MGMT_SCHEMA_UPLOAD_FAILED       = -10;
    public static final int MGMT_CANT_RETRIEVE_LOG           = -11;         
    
    // max of the string in an envelope
    public static final int MAX_BYTE_SCHEMA_PER_ENVELOPE    = 4096;

    // mask to indicate if the envelope is the first/last/median message
    public static final byte MDCONFIG_FIRST_MESSAGE = 1;
    public static final byte MDCONFIG_LAST_MESSAGE  = 2;
    //
    // getExpansionStatus return codes
    //
    public static final int EXPAN_UNKNOWN  = -2; 
    public static final int EXPAN_NT_RDY   = -1; // may not ever be used
    public static final int EXPAN_READY    =  0;
    public static final int EXPAN_EXPAND   =  1;
    public static final int EXPAN_DONE     =  2;

    /**
     * The node was successfully powered on but
     * the honeycomb services failed to start on the node.
     */
    public static final int POWER_ON_HONEYCOMB_SERVICES_DOWN = -2;

    /**
     * String outputed by the cli for an unknown fru status or type
     */
    public static final String HCFRU_UNKNOWN_STR = "UNKNOWN";
  
    /**
     * The status strings outputed by the cli for the various fru
     * status.  These values need to be keep in line with the
     * HCFRU_STATUS_$VALUES defined
     */ 
    public static final String[] HCFRU_STATUS_STR = {
        "DISABLED","ENABLED","ONLINE","OFFLINE", "MISPLACED", "ABSENT"};
 
    public static final int HCFRU_STATUS_DISABLED=0;
    public static final int HCFRU_STATUS_ENABLED=1;
    public static final int HCFRU_STATUS_AVAILABLE=2;
    public static final int HCFRU_STATUS_OFFLINE=3;
    public static final int HCFRU_STATUS_MISPLACED=4;
    public static final int HCFRU_STATUS_ABSENT=5;

    /**
     * The status strings outputed by the cli for the various node states.
     * These values need to be keep in sync with the HCNODE_STATUS_$VALUES defined
     */
    public static final String[] HCNODE_STATUS_STR= {"OFFLINE","ONLINE","AVAILABLE","OFFLINE"};

    public static final int HCNODE_STATUS_OFFLINE=0;
    public static final int HCNODE_STATUS_ONLINE=1;
    public static final int HCNODE_STATUS_POWERED_DOWN = 3;

    /**
     * The status strings outputed by the cli for the various switch states.
     * These values need to be keep in sync with the HC_SWITCH_$VALUES defined
     */
    public static final String[] HCSWITCH_STATUS_STR = {"OFFLINE", "ACTIVE", "STANDBY"};

    public static final int HCSWITCH_STATUS_OFFLINE=0;
    public static final int HCSWITCH_STATUS_ACTIVE=HCFRU_STATUS_ENABLED;
    public static final int HCSWITCH_STATUS_STANDBY=HCFRU_STATUS_AVAILABLE;

    /**
     * The type strings outputed by the cli that correspond to the HCFRU_TYPE_$VALUES defined
     */
    public static final String[] HCFRU_TYPE_STR= {"DISK","NODE","PWRSPLY","FAN","SWITCH","SN"};

    public static final int HCFRU_TYPE_DISK=0;
    public static final int HCFRU_TYPE_NODE=1;
    public static final int HCFRU_TYPE_PWRSPLY=2;
    public static final int HCFRU_TYPE_FAN=3;
    public static final int HCFRU_TYPE_SWITCH=4;
    public static final int HCFRU_TYPE_SP=5;

    /**
     * Disk mode flags in HCDisk.java
     */
    public static final int HCDISK_MODE_NORMAL=0;
    public static final int HCDISK_MODE_ABNORMAL=1;
    public static final int HCDISK_MODE_EVACUATE=2;
    public static final int HCDISK_MODE_CHECKING=3;
    
    
    public static final int CNS_REGISTER_SUCCESS=SUCCESS;
    
    /**
     * The CNS server lookup failed due to a UnknownHostException
     * This implies DNS is not enabled or a proxy server may 
     * need to be configured.
     */
    public static final int CNS_REGISTER_SERVER_LOOKUP_FAILED=-2;
    
    /**
     * The CNS registration was successful but the saving of the
     * successful state in the configuration properties failed.
     * This implies that the system will still see the product
     * as unregistered.
     */
    public static final int CNS_REG_SUCCESS_CONFIG_UPDATE_FAILED=-3;


    /**
     * The maximum number of authorization clients  
     * that can be specified
     */
    public static final int MAX_AUTH_CLIENTS = 20;

    /**
     * Extended Cache Status flags in HCHadbAdapter.getCacheStatus()
     */
    public static final int HCHADB_STATUS_UNKNOWN = 0;
    public static final int HCHADB_STATUS_FAILED = 1;
    public static final int HCHADB_STATUS_CONNECTING = 2;
    public static final int HCHADB_STATUS_INITIALIZING = 3;
    public static final int HCHADB_STATUS_UPGRADING = 4;
    public static final int HCHADB_STATUS_SETTING_UP = 5;
    public static final int HCHADB_STATUS_RUNNING = 6;

    /**
     * Status flags for upgrade - used in bitwise operations, must be
     * powers of two 
     */
    // OK state
    public static final int UPGRADE_SUCCESS = 1;
    // Got an error but confirm with user if should continue
    public static final int UPGRADE_ERROR_CONTINUE = 2;
    // Got an error but can continue without confirmation
    public static final int UPGRADE_ERROR = 4;
    // Got a failure, upgrade should stop
    public static final int UPGRADE_FAILURE = 8;
    // Reboot has occurred
    public static final int UPGRADE_REBOOTED = 16;

    /*
     * Upgrade types
     */
    // DVD upgrade
    public static final int UPGRADE_SPDVD = 1;
    // HTTP upgrade
    public static final int UPGRADE_HTTP = 2;
    // Download only upgrade
    public static final int UPGRADE_DOWNLOAD_ONLY = 3;
    // Downloaded upgrade (upgrade previously downloaded image)
    public static final int UPGRADE_DOWNLOADED = 4;

    /**
     * Constant for one second in milliseconds
     */
    public static final int ONE_SECOND = 1000;
    
    
    /**
     * Constant for one minute in milliseconds
     */
    public static final int ONE_MINUTE = 60 * ONE_SECOND;
}

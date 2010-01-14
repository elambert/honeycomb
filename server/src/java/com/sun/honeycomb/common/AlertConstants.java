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

import com.sun.honeycomb.protocol.server.ProtocolProxy;

/**
 * Constant file for Alert constants.  The values in this
 * file are currently only used the by HCPerfStatsAdapter and
 * HCPerfStatsAdapterBase class.
 */
public class AlertConstants
{
    
    /**
     * Creates a new instance of AlertConstants
     */
    private AlertConstants() {}
    
    /** 
     * Alert Branch key for root
     */
    public static final String ROOT_BRANCH_KEY= "root";
    
    /** 
     * Alert Branch key for protocol
     */
    public static final String PROTOCOL_BRANCH_KEY = "Protocol";
    
    /**
     * Alert Branch key for WebDAV
     */
    public static final String WEBDAV_BRANCH_KEY = "Webdav";
    
    /**
     * Alert Branch key for PlatformService
     */
    public static final String PLATFORM_SERVICE_BRANCH_KEY = "PlatformService";
    
    /**
     * Alert Branch key for System Info
     */
    public static final String SYSTEM_INFO_BRANCH_KEY = "systemInfo";
    
    /**
     * Alert Branch key for LoadStats
     */
    public static final String LOAD_STATS_BRANCH_KEY = "loadStats";
    
    /**
     * Alert Branch key for memoryInfo.memoryStats
     */
    public static final String MEMORY_INFO_BRANCH_KEY = "memoryInfo";
    
    /**
     * Alert Branch key for memoryStats
     */
    public static final String MEMORY_STATS_BRANCH_KEY = "memoryStats";
    
    
    /**
     * Suffix for retreiving alert object held in the Load Stats Branch
     * ROOT_KEY + "." + nodeId + "." must be prepended to this value
     * to get the full key recorded in the alert tree.   
     */
    public static final String LOAD_STATS_BRANCH_LOOKUP_KEY=
	new StringBuffer(PLATFORM_SERVICE_BRANCH_KEY).append(".")
	    .append(SYSTEM_INFO_BRANCH_KEY).append(".")
	    .append(LOAD_STATS_BRANCH_KEY).toString();
    
    /**
     * Suffix for retreiving alert object held in the Memory Stats Branch
     * ROOT_KEY + "." + nodeId + "." must be prepended to this value
     * to get the full key recorded in the alert tree.   
     */
    public static final String MEMORY_STATS_BRANCH_LOOKUP_KEY = 
	new StringBuffer(PLATFORM_SERVICE_BRANCH_KEY).append(".")
	    .append(MEMORY_INFO_BRANCH_KEY).append(".")
	    .append(MEMORY_STATS_BRANCH_KEY).toString();
    
    /**
     * Suffix for retreiving the Store BW performance statistics.
     * ROOT_KEY + "." + nodeId + "." must be prepended to this value
     * to get the full key recorded in the alert tree.   
     */
    public static final String PERF_STORE_BRANCH_LOOKUP_KEY =  
	new StringBuffer(PROTOCOL_BRANCH_KEY).append(".")
	    .append(ProtocolProxy.STORE_BW).toString();

    
    /**
     * Suffix for retreiving the Store MD BW performance statistics.
     * ROOT_KEY + "." + nodeId + "." must be prepended to this value
     * to get the full key recorded in the alert tree.   
     */
    public static final String PERF_STORE_MD_BRANCH_LOOKUP_KEY = 
	new StringBuffer(PROTOCOL_BRANCH_KEY).append(".")
	    .append(ProtocolProxy.STORE_MD_BW).toString();
    
    /**
     * Suffix for retreiving the Store Both BW performance statistics.
     * ROOT_KEY + "." + nodeId + "." must be prepended to this value
     * to get the full key recorded in the alert tree.   
     */
    public static final String PERF_STORE_BOTH_BRANCH_LOOKUP_KEY = 
	new StringBuffer(PROTOCOL_BRANCH_KEY).append(".")
	    .append(ProtocolProxy.STORE_BOTH_BW).toString();
    
    /**
     * Suffix for retreiving the Store Both BW performance statistics.
     * ROOT_KEY + "." + nodeId + "." must be prepended to this value
     * to get the full key recorded in the alert tree.   
     */
    public static final String PERF_STORE_MD_SIDE_BRANCH_LOOKUP_KEY = 
	new StringBuffer(PROTOCOL_BRANCH_KEY).append(".")
	    .append(ProtocolProxy.STORE_MD_SIDE_TIME).toString();
    
    /**
     * Suffix for retreiving the Retrieve BW performance statistics.
     * ROOT_KEY + "." + nodeId + "." must be prepended to this value
     * to get the full key recorded in the alert tree.   
     */
    public static final String PERF_RETRIEVE_BRANCH_LOOKUP_KEY = 
	new StringBuffer(PROTOCOL_BRANCH_KEY).append(".")
	    .append(ProtocolProxy.RETRIEVE_BW).toString();
    
    
    /**
     * Suffix for retreiving the Retrieve MD BW performance statistics.
     * ROOT_KEY + "." + nodeId + "." must be prepended to this value
     * to get the full key recorded in the alert tree.   
     */
    public static final String PERF_RETRIEVE_MD_BRANCH_LOOKUP_KEY = 
	new StringBuffer(PROTOCOL_BRANCH_KEY).append(".")
	    .append(ProtocolProxy.RETRIEVE_MD_BW).toString();
    
    /**
     * Suffix for retreiving the Query performance statistics.
     * ROOT_KEY + "." + nodeId + "." must be prepended to this value
     * to get the full key recorded in the alert tree.   
     */
    public static final String PERF_QUERY_TIME_BRANCH_LOOKUP_KEY = 
	new StringBuffer(PROTOCOL_BRANCH_KEY).append(".")
	    .append(ProtocolProxy.QUERY_TIME).toString();
    
    /**
     * Suffix for retreiving the Delete performance statistics.
     * ROOT_KEY + "." + nodeId + "." must be prepended to this value
     * to get the full key recorded in the alert tree.   
     */
    public static final String PERF_DELETE_TIME_BRANCH_LOOKUP_KEY = 
	new StringBuffer(PROTOCOL_BRANCH_KEY).append(".")
	    .append(ProtocolProxy.DELETE_TIME).toString();
    
    /**
     * Suffix for retreiving the Get Schema performance statistics.
     * ROOT_KEY + "." + nodeId + "." must be prepended to this value
     * to get the full key recorded in the alert tree.   
     */
    public static final String PERF_GET_SCHEMA_TIME_BRANCH_LOOKUP_KEY = 
	new StringBuffer(PROTOCOL_BRANCH_KEY).append(".")
	    .append(ProtocolProxy.GET_SCHEMA_TIME).toString();
    
    /**
     * Suffix for retreiving the WebDAV Put performance statistics.
     * ROOT_KEY + "." + nodeId + "." must be prepended to this value
     * to get the full key recorded in the alert tree.   
     */    
    public static final String PERF_WEBDAV_PUT_BRANCH_LOOKUP_KEY = 
	new StringBuffer(WEBDAV_BRANCH_KEY).append(".")
	    .append(ProtocolProxy.WEBDAV_PUT_BW).toString();
    
        
    /**
     * Suffix for retreiving the WebDAV Get performance statistics.
     * ROOT_KEY + "." + nodeId + "." must be prepended to this value
     * to get the full key recorded in the alert tree.   
     */
    public static final String PERF_WEBDAV_GET_BRANCH_LOOKUP_KEY = 
	new StringBuffer(WEBDAV_BRANCH_KEY).append(".")
	    .append(ProtocolProxy.WEBDAV_GET_BW).toString();
    
}

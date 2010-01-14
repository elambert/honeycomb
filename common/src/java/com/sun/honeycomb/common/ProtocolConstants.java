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

import java.util.Date;

/**
 * Consolidate the constants used by both client and server.
 */
public interface ProtocolConstants {

    static final String PROTOCOL_VERSION = "1";
    static final String HONEYCOMB_VERSION = "1";
    static final String HONEYCOMB_MINOR_VERSION = "1";
    static final String REQUIRED_HTTP_VERSION = "HTTP/1.1";

    static final int DEFAULT_PORT = 8080;
    static final int WEBDAV_PORT = 8080;

    static final String TRUE = "true";
    static final String FALSE = "false";

    static final Date DATE_UNSET = new Date(0);
    static final Date DATE_UNSPECIFIED = new Date(-1);

    // Honeycomb services
    static final String STATUS_PATH = "/status";
    static final String STORE_PATH = "/store";
    static final String STORE_BOTH_PATH = "/store-both";
    static final String STORE_METADATA_PATH = "/store-metadata";
    static final String RETRIEVE_PATH = "/retrieve";
    static final String RETRIEVE_METADATA_PATH = "/retrieve-metadata";
    static final String DELETE_PATH = "/delete";
    static final String QUERY_PATH = "/query";
    static final String QUERY_PLUS_PATH = "/query-select";
    static final String QUERY_ALL_PATH = "/query-all";
    static final String GET_VALUES_PATH = "/get-values";
    static final String GET_CACHE_CONFIG_PATH = "/get-configuration";
    static final String EVAL_PATH = "/eval";
    static final String THIN_CLIENT_PATH = "/GUI";
    static final String POWER_OF_TWO_PATH = "/power-of-two";
    static final String WEBDAV_PATH = "/webdav";
    static final String CHECK_INDEXED_PATH = "/check-indexed";

    // Compliance paths
    static final String SET_RETENTION_PATH = "/set-retention";
    static final String SET_RETENTION_RELATIVE_PATH = "/set-retention-relative";
    static final String GET_RETENTION_PATH = "/get-retention";

    static final String ADD_HOLD_PATH = "/add-hold";
    static final String REMOVE_HOLD_PATH = "/remove-hold";
    static final String GET_DATE_PATH = "/get-date";

    // Parameter identifiers
    static final String ID_PARAMETER = "id";
    //    static final String OFFSET_PARAMETER = "offset";
    //static final String LENGTH_PARAMETER = "length";
    static final String MDLENGTH_PARAMETER = "metadata-length";

    static final String CACHE_PARAMETER = "metadata-type";
    static final String QUERY_PARAMETER = "where-clause";
    static final String SELECT_PARAMETER = "select-clause";
    static final String QUERY_BODY_CONTENT = "query-body-content";
    static final String QUERY_CLAUSE_IN_BODY = "where-in-body";
    static final String COOKIE_IN_BODY = "cookie-in-body";
    static final String XML_IN_BODY = "xml-in-body";
    static final String SELECT_CLAUSE_HEADER = "select-clause";
    static final String COOKIE_HEADER = "honeycomb-query-cookie";
    static final String WHERE_CLAUSE_HEADER = "where-clause";
    static final String MULTICELL_CONFIG_VERSION_HEADER = "Honeycomb-Multicell-Config-Version";
    static final String HONEYCOMB_MULTICELL_HEADER = "Expect-Multicell-Config";
    static final String KEY_PARAMETER = "key";
    static final String MAX_RESULTS_PARAMETER = "maxresults";
    static final String BINARY_PARAMETER = "binary";
    static final String ZIP_PARAMETER = "zip";
    static final String LOG_TAG_PARAMETER = "logtag";
    static final String COOKIE_PARAMETER = "cookie";

    // Compliance parameters
    static final String DATE_PARAMETER = "date";
    static final String HOLD_TAG_PARAMETER = "hold-tag";
    static final String RETENTION_LENGTH_PARAMETER = "retention-length";

    // Test parameters
    static final String TEST_DELAY_SECS = "test-delay-secs";


    // Header names
    static final String PROTOCOL_VERSION_HEADER = "Protocol-Version";
    static final String HONEYCOMB_VERSION_HEADER = "Honeycomb-Version";
    static final String HONEYCOMB_MINOR_VERSION_HEADER = "Honeycomb-Minor-Version";
    static final String HONEYCOMB_NODE_HEADER = "Honeycomb-Node";
    static final String USER_AGENT_HEADER = "User-Agent";
    static final String RETRY_COUNT = "Retry-Count";
    static final String RESULT_COUNT = "Result-Count";
    static final String SELECT_FIELD = "select-field";
    static final String RANGE_HEADER = "range";
    static final String BYTES_PREFIX = "bytes=";
    static final char   RANGE_SEPARATOR = '-';
    static final char   RANGE_DELIMITER = ',';
    static final String CHUNKSIZE_HEADER = "commit-chunksize-bytes";
    static final String SEND_STACK_TRACE = "send-stack-trace";

    static final String DEPTH_HEADER = "depth";
    static final String INFINITY = "infinity";

    static final String TRAILER_STATUS = "trailer-status";
    static final String TRAILER_REASON = "trailer-reason";
    static final String BODY_CONTAINS_ERROR_STRING_PROPERTY = "error-string-in-body";
    static final String BODY_CONTAINS_STACK_TRACE_PROPERTY = "stack-trace-in-body";


    static final String ASCII_ENCODING = "ASCII";
    static final String UTF8_ENCODING = "UTF-8";

    static final String XML_TYPE = "text/xml";
    static final String PLAIN_TEXT_TYPE = "text/plain";
    static final String OCTET_STREAM_TYPE =
	"application/x-octet-stream";

    // System Record tags
    static final String IDENTIFIER_TAG = "system.object_id";
    static final String LINK_TAG = "system.object_link";
    static final String SIZE_TAG = "system.object_size";
    static final String CTIME_TAG = "system.object_ctime";
    static final String DATA_CONTENT_DIGEST_TAG = "system.object_hash";
    static final String HASH_ALGORITHM_TAG = "system.object_hash_alg";
    static final String QUERY_READY_TAG = "system.query_ready";


    // Configurable properties
    static final String API_SERVER_PORT_PROPERTY = "honeycomb.protocol.port";
    static final String SENDSTACKTRACE_PROPERTY = "honeycomb.protocol.sendStackTrace";
    static final String JETTYLOG_PROPERTY = "honeycomb.protocol.jettylog";
    static final String EVAL_PROPERTY = "honeycomb.protocol.eval";

    //
    // Multicell constants
    //
    //
    static final public String TAG_MC_DESC = "Multicell-Descriptor";
    static final public String TAG_CELL = "Cell";
    static final public String TAG_RULE = "Rule";
    static final public String TAG_SERVICETAG = "ServiceTagData";
    
    // Descriptor tags
    static final public String ATT_VERSION_MAJOR = "version-major";
    // used for clients only
    static final public String ATT_VERSION_MINOR = "version-minor";


    // Cell tags
    static final public String ATT_CELLID = "id";
    static final public String ATT_DOMAIN_NAME = "domain-name";
    static final public String ATT_DATA_VIP = "data-vip";
    static final public String ATT_ADMIN_VIP = "admin-vip";
    static final public String ATT_SP_VIP = "sp-vip";
    static final public String ATT_SUBNET = "subnet";
    static final public String ATT_GATEWAY = "gateway";
    
    /**
     * Service tags - Product number - of the top level assembly associated
     * with the cell.
     */
    static final public String ATT_PRODUCT_NUM = "product-number";
    
    /**
     * Service tags - Product serial number - of the top level assembly associated
     * with the cell.
     */
    static final public String ATT_PRODUCT_SERIAL_NUM = "product-serial-number";
    
    /**
     * Service tags - Marketing number - of the top level assembly associated
     * with the cell.
     */
    static final public String ATT_MARKETING_NUM = "marketing-number";
    
    /**
     * Service tags - Instance URN - the instance URN for the service tag
     * entry.  This value doesn't change unless a value in the service tag
     * changes.
     */
    static final public String ATT_INSTANCE_URN = "instanceURN";

    // additional cell tags for clients only
    static final public String ATT_USED_CAPACITY = "used-capacity";
    static final public String ATT_TOTAL_CAPACITY = "total-capacity";

    // Rule tags
    static final public String ATT_RULEID = "id";
    static final public String ATT_ORIGIN_CELLID = "origin-cellid";
    static final public String ATT_INITIAL_CAPACITY = "initial-capacity";
    static final public String ATT_START = "start";
    static final public String ATT_END = "end";    
}

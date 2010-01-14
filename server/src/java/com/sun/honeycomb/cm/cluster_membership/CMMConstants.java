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



package com.sun.honeycomb.cm.cluster_membership;

/**
 * The CMM tunables/parameters.
 * Define all parameters of CMM; max number of nodes, frame mtu,
 * recovery timeouts, socket ports used by API and Ring protocol.
 */
interface CMMConstants {

    /**
     * maximum number of nodes in a cell 
     */
    public static final int MAX_NODES = 16;
    /**
    * maximum transmit unit of CMM in bytes
    */
    public static final int FRAME_MTU = (20 * 1024);   // 20k
    /**
     * heartbeat interval between nodes in milliseconds
     */
    public static final int HEARTBEAT_INTERVAL = 1000; // 1s
    /**
     * heartbeat timeout in milliseconds to declare a node dead.
     */
    public static final int HEARTBEAT_TIMEOUT = 5000; // 5s
    /**
     * connection timeout in seconds.
     * a healthy node is expected to sucessfully establish
     * a TCP connection in this amount of time.
     */
    public static final int CONNECT_TIMEOUT = 15000;  // 15s
    /**
     * maximum number of CMM restarts (HA)
     */
    public static final int MAX_RESTARTS = 5;
    /**
     * restart time window in milliseconds (HA)
     */
    public static final int RESTART_WINDOW = (60 * 1000); // 1mn
    /**
     * transport Ip port.
     */
    public static final int RING_PORT = 4400;
    public static final int SNIFF_OFFSET = 100;
    /**
     * API Ip port.
     */
    public static final int API_PORT = 4401;
    /**
     * logging interval
     */
    public static final int LOGGING_INTERVAL = (2 * 60 * 1000); // 2mn
    /**
     * purge interval to clean up history of config update files.
     */
    public static final int PURGE_INTERVAL = (2 * 60 * 60 * 1000); // 2h
    /**
     * number of config update history files to keep around
     */
    public static final int MAX_CONFIG_HISTORY = 5; // 5 previous config update
    /**
     * default quorum threshold when not defined by config
     */
    public static final int DEFAULT_THRESHOLD = 75;
    /**
     * max retry count for config update
     */
    public static final int CONFIG_UPDATE_RETRY_COUNT = 3;
    /**
     * retry interval between config update
     */
    public static final int CONFIG_UPDATE_RETRY_INTERVAL = 30000; // 30s
    /**
     * timeout for a config update
     */
    public static final int CONFIG_UPDATE_TIMEOUT = 15000; // 15s
    /**
     * internal watchdog timeout to detect the local node is dead.
     */
    public static final int WATCHDOG_TIMEOUT = (5 * 60 * 1000); // 5mn
}

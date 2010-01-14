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


package com.sun.honeycomb.hctest.rmi.spsrv.common;

public class SPSrvConstants {

    public static final int RMI_REGISTRY_PORT = 3135;
    public static final int RMI_SVC_PORT = 3136;

    public static final String SERVICE = "SPSrvIF";  // name of srv class

    // cluster node status per SPSrv
    public static final int NODE_UNTRIED = 0;
    public static final int NODE_INACCESSIBLE = 1;
    public static final int NODE_NO_SERVER = 2;
    public static final int NODE_HAS_SERVER = 3;
    public static final int NODE_ASSUMED_NONEXISTENT = 4;

    public static String nodeStatus(int status) {
        switch (status) {
            case NODE_UNTRIED: return "untried";
            case NODE_INACCESSIBLE: return "inaccessible";
            case NODE_NO_SERVER: return "no_server";
            case NODE_HAS_SERVER: return "has_server";
            case NODE_ASSUMED_NONEXISTENT: return "assumed_nonexistent";
            default: return "undefined_status_code";
        }
    }

    // command params
    public static final int RANDOM_NODE = -1;  // must be < 0
    public static final int RANDOM_NODE_FULLDECK = -2;  // must be < 0
    public static final int MASTER_NODE = -3;  // must be < 0
    public static final int VICE_MASTER_NODE = -4;  // must be < 0
}

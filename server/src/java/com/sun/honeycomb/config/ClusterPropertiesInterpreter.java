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



package com.sun.honeycomb.config;

import com.sun.honeycomb.emd.server.SysCache;

/**
 * This class is to be used for interpretation of property values in
 * the cluster configuration file.
 *
 * As an example:
 * SysCache.SYSTEM_CACHE_STATE has about 6 states but for the NodeMgr
 * to know if it should keep the Protocol servers running or not it
 * only needs a boolean value returend by the method keepProtocolUp
 * so the interpretation of those values from config is done in this
 * class.
 *
 *
 */
public class ClusterPropertiesInterpreter {

    /**
     * Decide wether or not based on the system cache if we should
     * have the API Servers running.
     *
     * @return
     */
    public static boolean acceptRequests()  {
        ClusterProperties props = ClusterProperties.getInstance();
        String sysState = props.getProperty(SysCache.SYSTEM_CACHE_STATE);

        // if the state of the system cache is restoring.firstape or
        // corrupted then we should not have the protocol layer up yet
        if (sysState != null &&
            (sysState.equals(SysCache.CORRUPTED) ||
             sysState.equals(SysCache.RESTOR_FT)))
            return false;

        return true;
    }
}

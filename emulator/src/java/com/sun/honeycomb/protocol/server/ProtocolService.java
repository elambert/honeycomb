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



package com.sun.honeycomb.protocol.server;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.mortbay.http.HttpContext;

import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.cm.EmulatedService;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.multicell.lib.MultiCellLib;

public class ProtocolService extends ProtocolBase
    implements EmulatedService {

    private static boolean isReady = false;

    private static ProtocolService protocolService = null;
    static public ProtocolService getProtocolService() {
        return protocolService;
    }

    public String getName() {
        return("Protocol");
    }

    public ProtocolService() throws IOException {
        super();
        protocolService = this;
        registerAdminContext();
    }

    /**
     * Implements the EmulatedService/Runnable interface
     */
    public void run() {
        logger.info("Going to running state");
        try {
            init(null);
            server.start();
            isReady = true;
        } catch (Exception e) {
            logger.severe("Failed to run: " + e.getMessage());
            throw new InternalException(e);
        }
    }

    public void shutdown() {
        logger.info("Shutdown");
        isReady = false;
        try {
            server.stop(false);         //6571818 - server.stop() was blocking,
                                        // so now set graceful=false
            server.join();
            server.destroy();
        } catch (InterruptedException e) {
            logger.severe("Failed to shutdown: " + e.getMessage());
        }
    }
    
    public static boolean isReady() {
        return(isReady);
    }

    public static boolean isRunning() {
        return isReady();
    }

    private void registerAdminContext() {
        HttpContext context = new HttpContext();
        context.addHandler(new AdminHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        context.setContextPath("/admin");
        ContextRegistration ctxReg = new ContextRegistration("/admin",
                                                             context,
                                                             false);
        registerContext(ctxReg);
    }
}

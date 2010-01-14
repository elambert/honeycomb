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



package com.sun.honeycomb.admin.mgmt.server;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.honeycomb.admin.mgmt.Utils;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.util.BundleAccess;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.common.AlertConstants;
import java.math.BigInteger;
import com.sun.honeycomb.protocol.server.ProtocolProxy;

/**
 *  Audit Logger Base Class
 *  Logs messages using external log levels defined in ExtLevel so messages
 *  will be logged to both the internal and external logs.
 *
 */
public class HCAuditLoggerAdapterBase implements HCAuditLoggerAdapterInterface {
    protected static transient final Logger auditLogger =
            Logger.getLogger(HCAuditLoggerAdapterBase.class.getName());
    
    protected final String classFullname;
    
    /** Creates a new instance of HCAuditLoggerAdapterBase */
    public HCAuditLoggerAdapterBase() {
        this.classFullname = this.getClass().getName();
    }

    /**
     *  Simple log method that logs a message that has already been
     *  formatted.
     *  @param options currently not used
     *  @param message formatted message string to log
     *
     *  @returns BigInteger zero if successful
     */
    public BigInteger log(BigInteger options, String message) {
        auditLogger.info(message);
        return BigInteger.valueOf(0);
        
    }

    /**
     *  Audit log messages are sent to the external log as well as the
     *  internal log.
     *  @param  level       string representation of logging level, either
     *                      an ExtLevel or a Java Level value 
     *  @param  localMsg    translated message
     *  @param  className   name of the class requesting the log message
     *  @param  methodName  method name that requested the log message
     *
     *  @returns BigInteger zero if successful
     */
    public BigInteger logExt(String level, String localMsg, String className,
            String methodName) {
        Level logLevel = null;
        try {
            // Should be a ExtLevel or Level defined value
            logLevel = ExtLevel.parse(level);
        } catch (IllegalArgumentException ex) {
            auditLogger.warning("Invalid external log level argument: " +
                    level);
            logLevel = ExtLevel.EXT_INFO;
        }
        
        if ((className == null || className.length() == 0) ||
            (methodName == null || methodName.length() == 0)) {
            className = this.classFullname;
            methodName = "logExt";
        }
        
        // Log the message
        auditLogger.logp(logLevel,
                className, 
                methodName, localMsg);
        return BigInteger.valueOf(0);
        
    }

    public BigInteger getDummy() throws MgmtException {
        return BigInteger.valueOf(0);
    }
    
    /**
     *  Placeholder, do not need to do anything to load logger
     */
    public void loadHCAuditLogger()
        throws InstantiationException {
        
    }
    
}

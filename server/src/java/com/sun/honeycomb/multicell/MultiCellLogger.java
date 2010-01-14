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




package com.sun.honeycomb.multicell;


import java.util.Observable;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.config.ClusterProperties;

//
// Small utility to ease the debugging...
//
public class MultiCellLogger {

    public static final int MULTICELL_LOG_LEVEL_UNINITIALIZED = -1; 
    public static final int MULTICELL_LOG_LEVEL_DEFAULT       = 1; 
    public static final int MULTICELL_LOG_LEVEL_VERBOSE       = 2; 
    public static final int MULTICELL_LOG_LEVEL_EXTRA_VERBOSE = 3;
    public static final String MULTICELL_LOG_PREFIX = "MC:";
 
    private Logger logger;

    public MultiCellLogger(Logger log) {

        if (log == null) {
            throw new MultiCellError("MC: logger is null");            
        }
        ClusterProperties config = ClusterProperties.getInstance();
        int logLevel = MultiCellConfig.getLogLevel();
        if (logLevel == MULTICELL_LOG_LEVEL_UNINITIALIZED) {
            throw new MultiCellError("log level has no been initialized");
        }
        if (logLevel < MULTICELL_LOG_LEVEL_DEFAULT ||
            logLevel > MULTICELL_LOG_LEVEL_EXTRA_VERBOSE) {
            throw new MultiCellError("invalid log level");
        }
        log.info(MULTICELL_LOG_PREFIX + " initialized with level = " + 
                    logLevel);
        logger = log;
    }

    public Logger getLogger() {
        return logger;
    }

    public void logDefault(String logTrace) {
        logInfo(logTrace, MULTICELL_LOG_LEVEL_DEFAULT);
    }

    public void logVerbose(String logTrace) {
        logInfo(logTrace, MULTICELL_LOG_LEVEL_VERBOSE);
    }

    public void logExtVerbose(String logTrace) {
        logInfo(logTrace, MULTICELL_LOG_LEVEL_EXTRA_VERBOSE);
    }
    
    public void logSevere(String logTrace) {
        logger.severe(MULTICELL_LOG_PREFIX + logTrace);
    }


    public void logWarning(String logTrace) {
        logger.warning(MULTICELL_LOG_PREFIX + logTrace);
    }

    private void logInfo(String logTrace, int level) {
        if (MultiCellConfig.getLogLevel() >= level) {
            if (logger.isLoggable(Level.INFO)) {
                logger.info(MULTICELL_LOG_PREFIX + logTrace);
            }        
        }
    }
}

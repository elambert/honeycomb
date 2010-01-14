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



package com.sun.honeycomb.util;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Level;
import java.util.StringTokenizer;

import com.sun.honeycomb.common.ThreadPropertyContainer;
import com.sun.honeycomb.common.LogEscapeFormatter;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.util.AuditLevel;


/**
 * A log handler that writes to UNIX SysLog
 */
public final class SysLog extends Handler {

    // Static constants.
    public static final int FACILITY_INTERNAL = 0;
    public static final int FACILITY_EXTERNAL = 1;
    public static final int FACILITY_AUDIT    = 2;

    private static final int MILLISECONDS_IN_SECOND = 1000;

    private static final int LOG_EMERG = 0;
    private static final int LOG_CRIT = 2;
    private static final int LOG_ERR = 3;
    private static final int LOG_WARNING = 4;
    private static final int LOG_NOTICE = 5;
    private static final int LOG_INFO = 6;
    private static final int LOG_DEBUG = 7;

    static {
        System.loadLibrary("jsyslog");
    }

    /** You don't ever want to close syslog so we do nothing here.
     * Later if we add buffering, we might de-allocate resources here,
     * but probably the gc will do that for us.
     */
    public void close() {}

    /** We don't currently buffer, but if we decide to, we'll use
     * this */
    public void flush() {}
    
    /** Log a line to SysLog - currently we don't buffer */
    public synchronized void publish(final LogRecord logRecord) {
        if (!super.isLoggable(logRecord)) {
            return;
        }
        
        StringBuffer prefix = new StringBuffer();
        StringBuffer msg = new StringBuffer();
        
        // Start the message w/ stack trace if this is an exception
        Throwable thrown = logRecord.getThrown();
        if (thrown != null) {
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                thrown.printStackTrace(pw);
                pw.close();
                msg.append(sw.toString().replace('\t', ' ')); // esc tabs
                msg.append("\n");
            } catch (Exception ignore) {
            }
        }   
        
        // Add in the message itself
        String theMessage = logRecord.getMessage();

        if (theMessage != null) {

            // remove extraneous arg from Logger.entering/exiting msg
            if (theMessage.equals("ENTRY {0}")) {
                theMessage = "ENTRY ";
            }
            if (theMessage.equals("RETURN {0}")) {
                theMessage = "RETURN ";
            }
            msg.append(theMessage);
        } 
        
        // Add in parameters
        Object theParams[] = logRecord.getParameters();
        if (theParams != null) {
            for (int i=0; i < theParams.length; i++) {
                msg.append(theParams[i].toString() + " ");
            } 
        }

        //Once the message is built, convert it to Native2Ascii format
        String msgString = LogEscapeFormatter.native2ascii(msg.toString());

        // Build the prefix
        
        // syslog provides the timestamp except for milliseconds
        prefix.append(Long.toString(logRecord.getMillis() % 
                                    MILLISECONDS_IN_SECOND));
     
        prefix.append(" ");
        prefix.append(logRecord.getLevel().getName());
        prefix.append(" [");
        
        String sourceClassName = logRecord.getSourceClassName();
        if (sourceClassName != null) {
            // remove the package name for brevity
            int lastDotPos = sourceClassName.lastIndexOf('.');
            if (lastDotPos > 0) {
                sourceClassName = sourceClassName.substring(lastDotPos + 1);
            }
        } else {
            sourceClassName = logRecord.getLoggerName();
        }
        prefix.append(sourceClassName);
        
        String sourceMethodName = logRecord.getSourceMethodName();
        if (sourceMethodName != null) {
            prefix.append(".");
            prefix.append(sourceMethodName);
        }
        // Append LogTag for the current request.
        if (ThreadPropertyContainer.getLogTag() == null)
        	prefix.append("] (");
        else 
        	prefix.append("] [" + ThreadPropertyContainer.getLogTag() + "] (");
        
        prefix.append(logRecord.getSequenceNumber());
        prefix.append(".");
        // Log one line to syslog for each line in msgString
        

    int level = logRecord.getLevel().intValue();

    if (level == AuditLevel.AUDIT_INFO.intValue() 
        || level == AuditLevel.AUDIT_WARNING.intValue() 
        || level == AuditLevel.AUDIT_SEVERE.intValue()) {
 	    printTrace(logRecord, prefix.toString(), msgString.toString(), SysLog.FACILITY_AUDIT);

    }

 	else if (logRecord.getLevel().intValue() <= Level.SEVERE.intValue()) {
 	    printTrace(logRecord, prefix.toString(), msgString.toString(), SysLog.FACILITY_INTERNAL);
 	} else {
 	    printTrace(logRecord, prefix.toString(), msgString.toString(), SysLog.FACILITY_EXTERNAL);
 	}
    }


    private void printTrace(LogRecord logRecord,
      String prefix, String msg, int facility) {
	StringTokenizer st = new StringTokenizer(msg, "\n");
	int count = 1;
	while(st.hasMoreTokens()) {
	    println(convertLevel(logRecord.getLevel()), facility,
	      prefix + (count++) + ") " + st.nextToken());
	}
	
    }


    /** Here is where we map each Java level to a SysLog level:
     *  JAVA                        -> SYSLOG
     * --------------------------------------
     *  SEVERE                      -> ERR
     *  WARNING                     -> WARNING
     *  INFO                        -> INFO
     *  CONFIG, FINE, FINER, FINEST -> DEBUG
     */
    private int convertLevel(final Level javaLogLevel) {

        if(javaLogLevel == ExtLevel.EXT_SEVERE) {
            return LOG_ERR;
        } else if(javaLogLevel == ExtLevel.EXT_WARNING) {
            return LOG_WARNING;
        }  else if(javaLogLevel == ExtLevel.EXT_INFO) {
            return LOG_INFO;
        } 

        if(javaLogLevel == AuditLevel.AUDIT_SEVERE) {
            return LOG_ERR;
        } else if(javaLogLevel == AuditLevel.AUDIT_WARNING) {
            return LOG_WARNING;
        }  else if(javaLogLevel == AuditLevel.AUDIT_INFO) {
            return LOG_INFO;
        } 

        if(javaLogLevel == Level.SEVERE) {
            return LOG_ERR;
        } else if(javaLogLevel == Level.WARNING) {
            return LOG_WARNING;
        }  else if(javaLogLevel == Level.INFO) {
            return LOG_INFO;
        } 
        // CONFIG, FINE, FINER, and FINEST -> DEBUG
        return LOG_DEBUG;
    }
                                         
    /**
     * Routine to print to the system log.
     * @param priority int priority, which can be any of the above
     * stated variables, optionally logically OR'ed together.
     * @param message Message to send to the log.
     */
    private static native void println(final int priority,
				   final int facility,
			           final String message);
}

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



package com.sun.honeycomb.datadoctor;

import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.layout.LayoutProxy;
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.oa.DeletedFragmentException;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.cache.SystemCacheConstants;
import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.ObjectCorruptedException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.Cookie;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.ArrayList;

/** 
 * Each task should create an instance of this class, and use its
 * methods instead of the default logger. This way, taskName (which
 * includes the nodeId:diskId pair) gets added to all log messages.
 * Otherwise, we don't know on which disk the task is operating.
 *
 * TODO: should we be a subclass of Java Logger?
 */
class TaskLogger {

    /** 
     * example of calling this constructor:  
     *     TaskLogger(MyTask.class.getName(), "myTask");
     */
    TaskLogger(String taskClass, String taskName) {
        this.taskName = taskName;
        logger = Logger.getLogger(taskClass);
    }

    /** convenience methods for various logging levels */
    void info(String msg) { log(Level.INFO, msg); }
    void warning(String msg) { log(Level.WARNING, msg); }
    void severe(String msg) { log(Level.SEVERE, msg); }
    void fine(String msg) { log(Level.FINE, msg); }

    /** prepend task name to log message */
    void log(Level level, String msg) {

        msg = taskName + " " + msg;
        logger.log(level, msg);
    }

    /** prepend task name to log message */
    void log(Level level, String msg, Throwable t) {
        msg = taskName + " " + msg;
        logger.log(level, msg, t);
    }

    boolean isLoggable(Level level) {
        return logger.isLoggable(level);
    }
        
    private String taskName;
    private Logger logger;
}
 


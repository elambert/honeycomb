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



package com.sun.honeycomb.oa.bulk;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream; 
import java.io.IOException; 

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.SystemMetadata;

public class BackupRestoreMain {
    private static Logger LOG = Logger.getLogger(BackupRestoreMain.class.getName());
  
    public static void doOperation(String type, String filename, long t1, long t2) throws Throwable {
        exception = null;
        int options = Session.NO_OPTIONS;
        
        if (type.equals("backup"))  {
            options = Session.OBJECT_BACKUP_OPTION | 
                      Session.CLUSTER_CONFIG_BACKUP_OPTION |  
                      Session.SCHEMA_BACKUP_OPTION | 
                      Session.SYSCACHE_BACKUP_OPTION |  
                      Session.SILO_CONFIG_BACKUP_OPTION;
        }

        if (type.equals("restore")) {
            options = Session.SYSCACHE_RESTORE_OPTION;
        }
        
        doOperation(type,
                    filename,
                    t1,
                    t2, 
                    options);
    }
   
    private static Throwable exception = null;
    public static void doOperation(String type, 
                                   String filename, 
                                   long t1, 
                                   long t2, 
                                   int options) throws Throwable {
        exception = null;
        NotifyCallback callback = new NotifyCallback();
        File file = new File(filename);
        
        if (type.equals("backup")) {
            
            FileOutputStream fos = null;
            
            try {
                fos = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                LOG.severe("Couldn't output to file " + file.getAbsolutePath());
                return;
            }
            
            try {
                // Add this to prevent the call to NodeMgrService$Proxy.getMasterNode
                options |= Session.FORCE_BACKUP;

                BackupRestore.startBackupSession(t1, t2,
                                                 fos.getChannel(), 
                                                 callback, 
                                                 options);
            } catch (SessionException e) {
                LOG.log(Level.SEVERE,"Session exception ",e);
                return;
            }
            
            try {
                fos.close();
            } catch (IOException e) {
                LOG.severe("Failure on closing output stream." + e.getMessage());
            }
            
        } else if (type.equals("restore")) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                LOG.severe("Couldn't output to file " +  file.getAbsolutePath());
                return;
            }

            LOG.info("Starting restore...");            
            BackupRestore.startRestoreSession(fis.getChannel(),
                                              callback,
                                              options);
            try {
                fis.close();
            } catch (IOException e) {
                LOG.severe("Failure on closing output stream." + e.getMessage());
            }
            
        } else  {
            LOG.severe("first argument must be either 'backup' or 'restore'.");
            return;
        }
    
        if (exception != null) 
            throw exception;
        
        LOG.info("All done.");
    }
    
    public static class NotifyCallback implements Callback {

        public void callback(CallbackObject callback) {
            
            if (callback.getCallbackType() == CallbackObject.OBJECT_CALLBACK) {
                SystemMetadata sm = callback.getObjectCallback();
                long atime = (sm.getCTime() > sm.getDTime() ? sm.getCTime() : sm.getDTime());
                LOG.info("Processing OID: " + sm.getOID() + 
                         " atime: " +  atime + 
                         " offset: " + callback.getStreamOffset());
            }
            
            if (callback.getCallbackType() == CallbackObject.SYS_CACHE_CALLBACK) {
                LOG.info("Processed System " + callback.getCacheCount() + " cache(s).");
            }
            
            if (callback.getCallbackType() == CallbackObject.CLUSTER_CONFIG_CALLBACK) {
                LOG.info("Processed Cluster Config.");
            }
            
            if (callback.getCallbackType() == CallbackObject.SCHEMA_CONFIG_CALLBACK) {
                LOG.info("Processed Cluster Schema.");
            }
            
            if (callback.getCallbackType() == CallbackObject.SILO_CONFIG_CALLBACK) {
                LOG.info("Processed Silo Config.");
            }
            
            if (callback.getCallbackType() == CallbackObject.SESSION_COMPLETED) {
                try {
                    callback.checkStatus();
                } catch (Exception e) { 
                    LOG.log(Level.SEVERE,"Session failed.",e);
                    exception = e;
                }
                LOG.info("Session completed.");
            }
        } 
    }
}

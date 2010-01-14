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

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.Observable;
import java.util.Observer;

import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.messages.api.ConfigChangeNotif;
import com.sun.honeycomb.cm.jvm_agent.CMAException;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.emd.common.SysCacheException;
import com.sun.honeycomb.emd.server.SysCache;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.oa.bulk.stream.ContentBlockParser;

public class BackupSession extends BaseBackupSession implements Observer {

    private boolean _changes = true;
    private ArrayList _pendingChanges;

    public BackupSession(long start,
                         long end,
                         WritableByteChannel channel, 
                         String streamFormat, 
                         Callback callback,
                         int options) throws SessionException{
        super(start, end, channel, streamFormat, callback, options);
        _pendingChanges = new ArrayList();
        if (optionChosen(CLUSTER_CONFIG_BACKUP_OPTION))
            try {
                ServiceManager.register(ServiceManager.CONFIG_EVENT, this);
           } catch (CMAException e) {
                throw new SessionException(e);
            }
    }
   
    boolean checkSysCacheState(long endTime) {
        try{
            if (optionChosen(SYSCACHE_BACKUP_OPTION)) {
                SysCache sysCache = SysCache.getInstance();
                if (sysCache == null)  {
                    String msg = "Could not deterimine system state";
                    CallbackObject cl = 
                        new CallbackObject(new ReportableException(msg), 
                                           CallbackObject.SESSION_COMPLETED);
                    callback(cl);
                    return false;
                }
                String state = sysCache.getStateFromMaster();
                if (!state.equals(SysCache.RUNNING))  {
                    String msg = null;
                    if (state.equals(SysCache.WAITFORREPOP)) {
                        long firstError = sysCache.getFirstErrorTime();
                        if (firstError == Long.MIN_VALUE 
                                || endTime < firstError) {
                            // We're good to start backup provided that
                            // either the 1st error is unset (Long.MIN_VALUE)
                            // or the window the user selected to backup is
                            // before our 1st insert error, e.g., the ctime
                            // of the last object to backup is earlier than
                            // the time the syscache state machine has for 
                            // the 1st insertion error.
                            return true;
                        } else {
                            msg = "Cannot start backup. Syscache is  "
                                + " repopulating with a first error at " 
                                + new Date (firstError) + " which is before"
                                + " the backup ending selected "
                                + new Date (endTime);
                        }
                    } else {
                        msg = "Backup can only start from "
                            + "running or waitforrepop state not '" 
                            + state + "'";
                    }
                    CallbackObject cl = 
                        new CallbackObject(new ReportableException(msg), 
                                           CallbackObject.SESSION_COMPLETED);
                    callback(cl);
                    return false;
                }
            }
            return true;
        } catch (SysCacheException e) {
            callback(new CallbackObject(e,CallbackObject.SESSION_COMPLETED));
            return false;
        }
    }


    public void checkPendingChanges() 
        throws SerializationException, ArchiveException, OAException, IOException {
        // check for configuration updates and stream them out if they exist.
        synchronized(_pendingChanges) {
            if (_changes) {
                while(_pendingChanges.size() != 0) {
                    ConfigChangeNotif change = (ConfigChangeNotif)_pendingChanges.remove(0);
                    CMMApi.ConfigFile config = change.getFileUpdated();
                    
                    if (config == CMMApi.UPDATE_DEFAULT_FILE) {
                        _streamParser.writeBlock(ContentBlockParser.CLCONF_BLOCK_PARSER, _writer, null);
                    } else if (config == CMMApi.UPDATE_METADATA_FILE) {
                        _streamParser.writeBlock(ContentBlockParser.SCHEMA_BLOCK_PARSER, _writer, null);
                    } else if (config == CMMApi.UPDATE_SILO_FILE) {
                        _streamParser.writeBlock(ContentBlockParser.SILOCONF_BLOCK_PARSER, _writer, null);
                    }
                }
            }
            _changes = false;
        }
    }

    public synchronized void update(Observable o, Object arg) {
        if (arg instanceof ConfigChangeNotif) {
            synchronized(_pendingChanges) {
                _changes = true;
                _pendingChanges.add(arg);
            }
        }
    }
}

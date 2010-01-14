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

import java.util.logging.Level;
import java.util.logging.Level;
import java.util.Date;
import java.text.ParseException;
import java.nio.channels.ReadableByteChannel;

import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.SysCacheUtils;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.server.SysCache;
import com.sun.honeycomb.emd.common.SysCacheException;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.oa.bulk.stream.Constants;

public class RestoreSession extends BaseRestoreSession {

    static private final String PROP_ENABLE_RESTORE_WITH_OBJS = 
                                    "honeycomb.oa.bulk.enable.restore.withobjs";
    
    public static final String PROP_RESTORE_SESSION_IN_PROGRESS = 
                                "honeycomb.oa.bulk.restore.session.in.progress";

    public RestoreSession(ReadableByteChannel channel, 
                          String streamFormat, 
                          Callback callback,
                          int options) throws SessionException {
        super(channel, streamFormat, callback, options);
    }
    
    public static void restoreInCourse() throws SysCacheException { 
        ClusterProperties props = ClusterProperties.getInstance();
        try {
            props.put(PROP_RESTORE_SESSION_IN_PROGRESS, "true");
        } catch (ServerConfigException e) {
            throw new SysCacheException("failed to set restore in progress property.",e);
        }
    }


    boolean confirmStateBeforeRestore() throws SysCacheException {
        if (optionChosen(SYSCACHE_RESTORE_OPTION)) {
            SysCache sc = SysCache.getInstance();
            String state = sc.getStateFromMaster();
            ClusterProperties props = ClusterProperties.getInstance();
            if (props.getPropertyAsBoolean(PROP_RESTORE_SESSION_IN_PROGRESS)) {
                // if a previous session is in course and we were asked to start
                // then the cache should be in the corrupted state.
                if (!state.equals(SysCache.CORRUPTED)) {
                    SysCache.changeState(SysCache.CORRUPTED);
                    state = SysCache.CORRUPTED;
                }
            }
            
            boolean empty;
            try {
                String query = "getChanges " + 0 + " " + Long.MAX_VALUE;
                MetadataClient.QueryResult results =
                    MetadataClient.getInstance().query(CacheClientInterface.SYSTEM_CACHE, query, null, 1);
                empty = results.results.size() == 0;
            } catch(ArchiveException ae) {
                throw new SysCacheException("failed to query system cache", ae);
            }

            if (state.equals(SysCache.STARTING) || state.equals(SysCache.STOPPED)) {
                callback(new CallbackObject(new ReportableException("Cannot start restore " +
                                                                    "from the current system state: " + state), 
                                           CallbackObject.SESSION_COMPLETED));
                return false;
            }
            
            boolean enabledRestoreObjs = 
              props.getPropertyAsBoolean(
                  PROP_ENABLE_RESTORE_WITH_OBJS);
            
            if (enabledRestoreObjs) {
                LOG.info("Restore is enabled when there are existing objects");
            } else if (state.equals(SysCache.RUNNING)) {
                if (!empty) {
                    callback(new CallbackObject(new ReportableException("Restore only supported to an empty cluster."), 
                                                CallbackObject.SESSION_COMPLETED));
                    return false;
                }
            }

            if (state.equals(SysCache.RUNNING) || 
              state.equals(SysCache.CORRUPTED)) {
                // First tape restore we must replay the objects and
                // put system caches and config in place.
                setOption(REPLAY_BACKUP_OPTION);
            }
            else if (state.equals(SysCache.RESTORING)){
                String restoringToDate = props.getProperty(SysCache.RESTOR_FT_DATE);
                // Might be old format
                if (restoringToDate != null){
                    try{
                        Date restoringTo = Constants.DATE_FORMAT.parse(restoringToDate);
                        if (getCreationDate() > restoringTo.getTime()){
                            callback(new CallbackObject(new ReportableException("Attempt to restore backups out of order: " + 
                                                                                new Date(getCreationDate()) + " is more recent than " + restoringTo), 
                                                        CallbackObject.SESSION_COMPLETED));
                            return false;
                        }

                    }
                    catch (ParseException pe){
                        callback(new CallbackObject(new SessionException("Restore unable to parse date " + 
                                                                         new Date(getCreationDate()) + " " + restoringToDate), 
                                                    CallbackObject.SESSION_COMPLETED));
                        return false;                    
                    }
                }
            }
            
            restoreInCourse();
        }
        return true;
    }


    private void deleteUnrestorableRecords() throws SysCacheException, ArchiveException, EMDException, ParseException {
        // Find backup range; this header was added in 1.1.1
        String endString = (String)getHeader(Constants.END_TIME);
        if (endString != null){
            long count = 0;
            long end = Constants.DATE_FORMAT.parse(endString).getTime();
            SysCacheOIDIterator iter =
                new SysCacheOIDIterator(end - 1000 * 60, 
                                        System.currentTimeMillis(),
                                        true);
            while (iter.hasNext()){
                NewObjectIdentifier oid = iter.next();
                SystemMetadata smd = SysCacheUtils.retrieveRecord(oid);
                if (!smd.isRestored()){
                    count++;
                    SysCacheUtils.removeRecord(oid);
                }
            }
            if (true /*count != 0 */)
                LOG.info("Removed " + count + " unrestorable reference from SysCache");
        }
    }


    boolean confirmStateAfterRestore() throws SysCacheException {
        if (optionChosen(SYSCACHE_RESTORE_OPTION)) {
            String state = SysCache.getInstance().getStateFromMaster();
            if (state.equals(SysCache.RESTOR_FT)) {
                // First tape restore has finished 

                try {
                    // Any objects ingested after the end of the backup range 
                    // are lost forever; remove them from the system cache
                    deleteUnrestorableRecords();
                } catch (Exception e) {
                    callback(new CallbackObject(new SessionException
                                                ("Error clearing unrestorable objects", e), 
                                                CallbackObject.SESSION_COMPLETED));
                    return false;
                }

                try {
                    //transition into RESTORING state
                    SysCache.changeState(SysCache.RESTORING);
                } catch (SysCacheException e) {
                    callback(new CallbackObject(new SessionException
                                                ("Error changing system cache state to: " +
                                                 SysCache.RESTORING, e), 
                                                CallbackObject.SESSION_COMPLETED));
                    return false;
                }
            }
            // mark this restore session as done.
            ClusterProperties props = ClusterProperties.getInstance();
            try {
                props.put(PROP_RESTORE_SESSION_IN_PROGRESS, "false");
            } catch (ServerConfigException e) {
                throw new SysCacheException("failed to set restore in progress property.",e);
            }
        }
        return true;
    }

    void setCorruptedState() {
        try {
            String state = SysCache.getInstance().getStateFromMaster();
            if ((state.equals(SysCache.RESTOR_FT)) || 
              (state.equals(SysCache.RESTORING))) {
                SysCache.changeState(SysCache.CORRUPTED);
            }
        } catch (SysCacheException syse) {
            LOG.log(Level.SEVERE, "Failed to change the state of the " +
              "system to corrupted", syse);
        }
    }
}


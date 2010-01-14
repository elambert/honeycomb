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

import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Date;

import com.sun.honeycomb.common.ArchiveException;

public class BackupRestore {

    static final public String STREAM_OA_OBJ = "OA Object Stream Format";
    static final public String STREAM_7_FRAGS = "7 Fragment Stream Format";


    private static final int TAPE_BACKUP_OPTIONS = 
        Session.CLUSTER_CONFIG_BACKUP_OPTION |
        Session.SILO_CONFIG_BACKUP_OPTION    |
        Session.SCHEMA_BACKUP_OPTION         |
        Session.SYSCACHE_BACKUP_OPTION       |
        Session.OBJECT_BACKUP_OPTION;

    /**
     * Start a backup session.
     * 
     * @param oids OIDIterator that contains the oids to be backed up.
     * @param channel The channell to stream the current backup to.
     * @param streamFormat The stream format comes from the constants defined above.
     * @param callback Callback object is used to notify the caller of startbackupSession
     *                 on all events ranging from an object being backed up to the backup 
     *                 session having terminated (sucessfully or not). 
     * @param options options are specified in the Session class and they define which 
     *                objects (OA objects, Config, System Caches) we should backup in this 
     *                Session.
     *                
     * @return Session object for session monitoring and control.
     */
    static public Session startBackupSession(long start, long end,
                                             WritableByteChannel channel, 
                                             Callback callback,
                                             boolean force) throws Exception {
        int options = TAPE_BACKUP_OPTIONS;
        if (force)
            options |= Session.FORCE_BACKUP;
        return startBackupSession(start, end, channel, callback, options);
    }

    // used by unit test
    static Session startBackupSession(long start, long end,
                                      WritableByteChannel channel, 
                                      Callback callback,
                                      int options) throws Exception {
        
        BackupSession session = new BackupSession(start, 
                                                  end,
                                                  channel,
                                                  STREAM_OA_OBJ,
                                                  callback,
                                                  options);
        
        boolean force = ((options & Session.FORCE_BACKUP) != 0);
        if (!force && !session.checkSysCacheState(end)) {
            throw new ReportableException(
                "Not safe to backup to " + new Date (end));
        }

        session.startSession();
        return session;
    }

    private static final int TAPE_RESTORE_OPTIONS = 
        Session.SYSCACHE_RESTORE_OPTION;

    /**
     * Start a restore session.
     * 
     * @param channel a ReadableBytechannel is used to stream the data into OA
     * @param streamFormat a value from the BackupRestore constants that 
     *                       defines the stream format
     * @param callback the callback object used to notify the calling 
     *                   component of all restore activity
     * @param options these options are used to identify wether we should 
     *                replay objects int the current stream and which types of
     *                objects to process from the stream. This is usefull to
     *                avoid replaying system caches from older backup sessions.
     *                
     * @return Session object for session monitoring and control.
     * @throws SessionException 
     */
    static public Session startRestoreSession(ReadableByteChannel channel,
                                              Callback callback) throws Exception {

        return startRestoreSession(channel, callback, TAPE_RESTORE_OPTIONS);
    }

    // used by unit test
    static Session startRestoreSession(ReadableByteChannel channel,
                                       Callback callback, 
                                       int options) throws Exception {
        
        Session session = new RestoreSession(channel,
                                             STREAM_OA_OBJ,
                                             callback,
                                             options);
        session.startSession();
        return session;
    }
}

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

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Date;
import java.util.Map;

import com.sun.honeycomb.oa.bulk.stream.StreamParser;
import com.sun.honeycomb.oa.bulk.stream.StreamReader;

/** 
 * Session class is used an passed through all of the stream handlesr and in 
 * this manner it would allow you to know which version of the stream is being
 * used and initialize the correct objects based on that.
 *
 */
public abstract class Session {
    
    private final static String OA_BULK_STREAM_VERSION_UNENCODED_DELETED_REFS = "OA Bulk Stream v1";
    public final static String OA_BULK_STREAM_VERSION = "1";
   
    // Be sure when adding options to keep incrementing by powers of two
    public static int NO_OPTIONS                      =  0;
    public static int CLUSTER_CONFIG_BACKUP_OPTION    =  1;
    public static int SILO_CONFIG_BACKUP_OPTION       =  2;
    public static int SCHEMA_BACKUP_OPTION            =  4;
    public static int SYSCACHE_BACKUP_OPTION          =  8;
    public static int OBJECT_BACKUP_OPTION            = 16;
    public static int FORCE_BACKUP                    = 128;
    
    // replay option for first tape restore
    // by using this we treat the first tape differently and we make
    // sure to place the current system cache and config blocks in the
    // stream on the cluster and then we replay all the objects stored
    // on this stream.
    public static int REPLAY_BACKUP_OPTION            = 32;
    
    // this option means that we must be pay attention to the system
    // cache state machine's state before doing anything with the 
    // stream about to be restored
    public static int SYSCACHE_RESTORE_OPTION         = 64;
    
    protected String _backupVersion = null;
    protected String _streamFormat = null; 
    long _creationDate = -1;
    protected StreamParser _streamParser = null;
    protected Callback _callback = null;
   
    protected boolean _timeToStop = false;
    
    private int _options = NO_OPTIONS; 

    private Map _headers = null;
    
    private boolean _checkDisksForObject = true;
 
    /**
     * 
     * @param streamFormat
     * @param callback
     */
    protected Session(String streamFormat, 
                      Callback callback,
                      int options) {
        _backupVersion = OA_BULK_STREAM_VERSION;
        _streamFormat = streamFormat;
        _streamParser = new StreamParser(this);
        _callback = callback;
        _headers = new Hashtable();
        _options = options;
    }
   
    /**
     * 
     * @throws AbortedSessionException
     */
    protected void checkAbort() throws AbortedSessionException {
        if (_timeToStop) { 
            throw new AbortedSessionException("Session aborted by user");
        }
    }
   
    /**
     * 
     *
     */
    protected abstract void startSession() throws Exception;
   
    /**
     * 
     *
     */
    public final void endSession() {
        _timeToStop = true;
    }
   
    /**
     * 
     * @param obj
     */
    public void callback(CallbackObject obj) {
        _callback.callback(obj);
    }
   

    /**
     * 
     * @return
     */
    public String getStreamFormat() {
        return _streamFormat;
    }
   
    /**
     * 
     * @return
     */
    public String getBackupVersion() {
        return  _backupVersion;
    }
   
    /**
     * 
     * @param version
     */
    public void setBackupVersion(String version) {
        _backupVersion = version;
    }
    
    
    /**
     * 
     * @param option
     * @return
     */
    public boolean optionChosen(int option) { 
        return ((_options & option) != 0);
    }
    
    /**
     * 
     * @param option
     */
    public void setOption(int option) { 
        _options |= option;
    }


    void setHeaders(Map map) {
        _headers = map;
    }

    /**
     * 
     * @param key
     * @return
     */
    String getHeader(String key) {
        return (String)_headers.get(key);
    }


    /**
     * This check is used to know if we should read from the disks to 
     * check for the existence of the currently being processed object
     * This is helpfull when replaying already restored data, and will
     * only impact on the first objects restore if all of the objects
     * on this tape need restoring because then it should be set to 
     * false so we don't hit the disks any longer and just restore
     * the remaining content of this tape. 
     * 
     * @return
     */
    public boolean checkDiskForObject() { 
        return _checkDisksForObject;
    }
   
    /**
     * 
     * @param value
     */
    public void setCheckDiskForObject(boolean value) { 
        _checkDisksForObject = value;
    }


    public long getCreationDate(){
        return _creationDate;
    }

    public abstract long getBytesProcessed();

    long setCreationDate(long creationDate){
        return _creationDate = creationDate;
    }
}

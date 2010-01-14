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


import com.sun.honeycomb.common.SystemMetadata;

/** 
 * Utility class used to hold and identify the callback object returned.
 * The emulator version does not have the event logic.
 */
public class CallbackObject {
    
    // why object ? well it gaurantees you passed in exactly the right
    // flag and not some int value you decided to call the constructor
    // with 
    public static final Object OBJECT_CALLBACK             = new Object();
    public static final Object SYS_CACHE_CALLBACK          = new Object();
    public static final Object CLUSTER_CONFIG_CALLBACK     = new Object();
    public static final Object SILO_CONFIG_CALLBACK        = new Object();
    public static final Object SCHEMA_CONFIG_CALLBACK      = new Object();
    public static final Object SESSION_COMPLETED           = new Object();
 
    private Object _type = -1;
    private SystemMetadata _sr = null;
    
    // OBJECT CALLBACK
    private long _streamOffset = -1;
   
    // SYSTEM CACHE CALLBACK
    private RestoreState _restoreState = null;
    
    // SESSION CALLBACK
    private Exception _exception = null;
    
    public CallbackObject(Object o, Object type) throws IllegalArgumentException {
        _type = type;
        if (type == OBJECT_CALLBACK) {
            _sr = (SystemMetadata) o;
        }
        if (type == SYS_CACHE_CALLBACK) {
            _restoreState = (RestoreState) o;
        }
        if (type == SESSION_COMPLETED) {
            _exception = (Exception) o;
        }
    }
   
    /**
     * @return returns the offset in bytes in the stream for the current callback object. 
     */
    public long getStreamOffset() { 
        return _streamOffset;
    }
   
    /**
     * @return returns the callback type for this current callback object.
     */
    public Object getCallbackType() {
        return _type;
    }
    
    /**
     * @param offset set stream offset.
     */
    public void setStreamOffset(long offset) { 
        _streamOffset = offset;
    }

    /** OBJECT CALLBACK **/


    final String[] names = {};
    final String[] values = {};

    /**
     * Object callback should always have a SystemMetadata record for the 
     * callback action
     */
    public SystemMetadata getObjectCallback() {
        return _sr;
    }
   
    /** SYSTEM CACHE CALLBACK **/
    /**
     * Get the number of system caches that were handled for the current 
     * backup/restore of the system cache 
     * 
     * @return the number of caches that were restored or backed up. 
     */
    public long getCacheCount() {
        return 1;
    }

    /**
     * System Cache callback should always have a Restore State for the 
     * callback action
     */
    public RestoreState getRestoreState() {
        return _restoreState;
    }

    
    public void setCacheCount(int cacheCount) { 
    }
    
    /** SESSION CALLBACK **/
    /**
     * Checks if the current session completed successfully if the current Session has 
     * completed with an error or was aborted then this method will throw an exception.
     *  
     * @throws Exception if there was an error or abort during the current session.
     */
    public void checkStatus() throws Exception{
        if (_exception != null)
            throw _exception;
    }
}

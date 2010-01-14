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


package com.sun.honeycomb.oa.bulk.stream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.oa.bulk.CallbackObject;
import com.sun.honeycomb.oa.bulk.SerializationException;
import com.sun.honeycomb.oa.bulk.Session;

public abstract class BaseConfigSerializer extends ContentSerializer {
  
    private File _file = null;
    // comes from CMMApi constants
    CMMApi.ConfigFile _configType = CMMApi.UPDATE_UNDEFINED_FILE;

    /**
     * 
     * @param session
     */
    public BaseConfigSerializer(Session session, CMMApi.ConfigFile configType) {
        super(session);
        _configType = configType;
    }
   
    public void init(Object obj) throws SerializationException {
        String config = getPath();
        _file = new File(config);
    }

    public CallbackObject serialize(StreamWriter writer) throws SerializationException {
        if (_file.exists()) {
	        try {
		        FileInputStream fis = new FileInputStream(_file);
		        fis.getChannel().transferTo(0, _file.length(), writer);
	        } catch(IOException e) {
	            throw new SerializationException(e);
	        }
        }

        CallbackObject obj = null;
        
        if (_configType == CMMApi.UPDATE_DEFAULT_FILE)
            obj = new CallbackObject(null, CallbackObject.CLUSTER_CONFIG_CALLBACK);
        else if  (_configType == CMMApi.UPDATE_METADATA_FILE)
            obj = new CallbackObject(null, CallbackObject.SCHEMA_CONFIG_CALLBACK);
        else if  (_configType == CMMApi.UPDATE_SILO_FILE)
            obj = new CallbackObject(null, CallbackObject.SILO_CONFIG_CALLBACK);
        
        return obj;
    }
        
    public CallbackObject deserialize(StreamReader reader, Map headers)
            throws SerializationException {
        int length = Integer.parseInt((String)headers.get(Constants.CONTENT_LENGTH_HEADER));
       
        if (length != -1) {
	        String config = getPath();
            long timestamp = System.currentTimeMillis();
            String configname = config + "." + timestamp;
	        try {
	            
	            if (_session.optionChosen(Session.REPLAY_BACKUP_OPTION)) { 
	                FileOutputStream fos = new FileOutputStream(configname);
	                fos.getChannel().transferFrom(reader, 0, length);
                    fos.close();
                    /*
                     * Activate config.
                     */
                    updateConfig(timestamp, configname);  
	            } else { 
	                // only restore config on first tape none other..  
	                // but we still have to read it from the stream
                    skip(reader, length);
	            }
	        } catch (IOException e) {
	            throw new SerializationException(e);
            }
        }
        
        CallbackObject obj = null;
        
        if (_configType == CMMApi.UPDATE_DEFAULT_FILE)
            obj = new CallbackObject(null, CallbackObject.CLUSTER_CONFIG_CALLBACK);
        else if  (_configType == CMMApi.UPDATE_METADATA_FILE)
            obj = new CallbackObject(null, CallbackObject.SCHEMA_CONFIG_CALLBACK);
        else if  (_configType == CMMApi.UPDATE_SILO_FILE)
            obj = new CallbackObject(null, CallbackObject.SILO_CONFIG_CALLBACK);
        
        return obj;
    }

    public long getContentLength() {
        if (_file.exists())
            return _file.length(); 
        else 
            return -1;
    }
    
    abstract String getPath();

    abstract void skip(StreamReader reader, int length) throws IOException;

    abstract void updateConfig (long timestamp, String configname) 
        throws SerializationException, IOException;
}

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
import java.util.Date;
import java.util.Properties;

import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import com.sun.honeycomb.cm.cluster_membership.CfgUpdUtil;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import com.sun.honeycomb.emd.server.SysCache;
import com.sun.honeycomb.oa.bulk.SerializationException;
import com.sun.honeycomb.oa.bulk.CallbackObject;
import com.sun.honeycomb.oa.bulk.RestoreSession;
import com.sun.honeycomb.oa.bulk.Session;

/** This class is broken out so that the emulator can share the parent class 
 */

public class ConfigSerializer extends BaseConfigSerializer {
  

    public ConfigSerializer(Session session, CMMApi.ConfigFile configType) {
        super(session, configType);
    }

    String getPath(){
        if (_configType != null) {
            return _configType.name();
        }
        return null;
    }


    void updateConfig(long timestamp, String configname) 
        throws SerializationException, IOException {    
        if (_configType == CMMApi.UPDATE_DEFAULT_FILE) {


            /* 
             * If it was the default config then we have to set the 
             * system cache state to restoring.firsttape because 
             * otherwise this value would be wiped by the current 
             * config update. Also we need to set the 
             * PROP_RESTORE_SESSION_IN_PROGRESS to true so that we 
             * don't lose that as well. 
             * 
             * Read the existing not activated config file.
             */
            Properties props = new Properties();
            FileInputStream fis = new FileInputStream(configname);
            props.load(fis);
            fis.close();
                        
            /*
             * Inject necessary properties
             */
            props.put(RestoreSession.PROP_RESTORE_SESSION_IN_PROGRESS, 
                      "true");
            props.put(SysCache.SYSTEM_CACHE_STATE, 
                      SysCache.RESTOR_FT);
            props.put(SysCache.RESTOR_FT_DATE, 
                      Constants.DATE_FORMAT.format(new Date(_session.getCreationDate())));
            /*
             * Write out the new config.
             */
            FileOutputStream fos = new FileOutputStream(configname);
            props.store(fos, CfgUpdUtil.generateComments(timestamp));
            fos.close();
        }
        try {
            CMM.getAPI().storeConfig(_configType, timestamp, "0000000000000000");
        } catch (CMMException e) {
            throw new SerializationException(e);
        } catch (ServerConfigException e) {
            throw new SerializationException(e);
        }
    }

    void skip(StreamReader reader, int length) throws IOException{
        FileOutputStream fos = new FileOutputStream("/dev/null");
        fos.getChannel().transferFrom(reader, 0, length);
        fos.close();
    }
}

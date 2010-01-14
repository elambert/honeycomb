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



package com.sun.honeycomb.cm.cluster_membership;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;
import java.util.Map;

import com.sun.honeycomb.cm.cluster_membership.messages.api.ConfigChange;
import com.sun.honeycomb.stressconfig.ConfigStresserIntf;

public class ApiImplStressTest extends ApiImpl {

    public ApiImplStressTest(String hostname, Integer nPort)
        throws CMMException {
        super(hostname, nPort);
        logger.info(CMMApi.LOG_PREFIX + 
          "class ApiImplStressTest has been loaded");
    }

    protected synchronized void configChange(Map newProps, 
                                             CMMApi.ConfigFile fileToUpdate,
                                             boolean clearMode, 
                                             long vers, 
                                             String md5sum)
        throws CMMException,ServerConfigException 
    {
        int tryCount = CMM.CONFIG_UPDATE_RETRY_COUNT;
        boolean success = false;
        long version = vers;
        if (newProps != null) {
            version = CfgUpdUtil.getInstance().createFile(fileToUpdate, newProps);
        }

        logger.info(CMMApi.LOG_PREFIX + "*config/change version = " + version); 

        ConfigChange change = configChangeProlog(fileToUpdate, 
                                                 clearMode, 
                                                 version, 
                                                 md5sum);
        while ((tryCount > 0) && (!success)) {
            tryCount--;
            success = configChangeRequest(change);

            if ((!success) && (tryCount > 0)) {
                logger.info(CMMApi.LOG_PREFIX + 
                  " Config/update failed, retry the request ...");

                // This is the only difference with the base class
                hookConfigUpdate(version);

                try {
                    Thread.sleep(CMM.CONFIG_UPDATE_RETRY_INTERVAL);
                } catch (InterruptedException e) {
                }
            }
        }

        if (!success) {
            throw new ServerConfigException(CMMApi.LOG_PREFIX + 
              "Failed to update config after internal retry.");
        }
    }

    private void hookConfigUpdate(long version) {

        logger.info(CMMApi.LOG_PREFIX + "hookConfigUpdate, version = " + 
          version);
        ConfigStresserIntf api =
          ConfigStresserIntf.Proxy.getConfigStresserAPI();
        if (api == null) {
            logger.severe(CMMApi.LOG_PREFIX + 
              "Failed to retrieve ConfigStresser API, abort retry");
            return;
        }
        try {
            api.resetFailure(version);
        } catch (IOException ioe) {
            logger.severe(CMMApi.LOG_PREFIX + 
              "Failed to reset the failure, abort retry");
            return;                    
        }
    }

}

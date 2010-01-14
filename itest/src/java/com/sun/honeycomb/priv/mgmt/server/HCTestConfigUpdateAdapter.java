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



package com.sun.honeycomb.priv.mgmt.server;

import java.math.BigInteger;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.stressconfig.ConfigStresserIntf;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.common.MgmtException;

public class HCTestConfigUpdateAdapter 
    implements HCTestConfigUpdateAdapterInterface {

    private ConfigStresserIntf cfgstr = null;

    private static transient final Logger logger = 
      Logger.getLogger(HCTestConfigUpdateAdapter.class.getName());

    public void loadHCTestConfigUpdate()
        throws InstantiationException {
        
        cfgstr = ConfigStresserIntf.Proxy.getConfigStresserAPI();
        if (cfgstr == null) {
            throw new InstantiationException("Can't initialize adapter " +
                "HCTestConfigUpdate");
        }
    }

    /*
    * This is the list of accessors to the object
    */
    public BigInteger getDummy() throws MgmtException {
        return BigInteger.valueOf(-1);
    }

    /*
     * This is the list of custom actions
     */
    public BigInteger startExecutor(Long delayConfig,
      Byte createConfig,
      Byte nodeFailure,
      BigInteger rateFailure) throws MgmtException {

        int res = 0;
        try {
            res = cfgstr.startConfigUpdate(delayConfig.longValue(),
              ((createConfig == 0) ? false : true),              
              ((nodeFailure == 0) ? false : true),
              rateFailure.intValue());
            logger.info("successfully started executor, id = " + res);
        } catch(IOException ioe) {
            logger.log(Level.SEVERE,
              "Failed to start cfgupdate executor", ioe);
              throw new MgmtException("Failed to start executor");
        }
        return BigInteger.valueOf(res);
    }

    public String stopExecutor(BigInteger executorId)
        throws MgmtException {

        String status;

        try {
            status = cfgstr.stopConfigUpdate(executorId.intValue());
            logger.info("successfully stopped executor, id = " + 
              executorId.intValue());
        } catch(IOException ioe) {
            logger.log(Level.SEVERE,
              "Failed to stop cfgupdate executor " + executorId.intValue() +
              " ", ioe);
              throw new MgmtException("Failed to stop executor " + 
                executorId.intValue());
        }
        return status;
    }
}

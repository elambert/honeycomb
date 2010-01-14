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

import org.w3c.dom.Document;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.common.MgmtException;
import java.math.BigInteger;
import java.util.List;

public class HCTestConfigUpdateAdapter 
    implements HCTestConfigUpdateAdapterInterface {

    public void loadHCTestConfigUpdate()
        throws InstantiationException {
        
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
      Byte createInterface,
      Byte nodeFailure,
      BigInteger rateFailure) throws MgmtException {
        System.err.println("startExecutor : delay = " + 
          delayConfig.longValue() + ", nodeFailure = " + 
          nodeFailure.byteValue() + ", rateFailure " +
          rateFailure.intValue());

        return BigInteger.valueOf(0);
    }

    public String stopExecutor(BigInteger executorId)
        throws MgmtException {
        System.err.println("stopExecutor : id = " + executorId.intValue());
        return "stopped executor ";
    }

}

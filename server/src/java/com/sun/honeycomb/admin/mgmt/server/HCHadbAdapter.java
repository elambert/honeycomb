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



package com.sun.honeycomb.admin.mgmt.server;

import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.admin.mgmt.HadbUtils;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.common.CliConstants;

import java.math.BigInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

public class HCHadbAdapter
    implements HCHadbAdapterInterface {

    private static transient final Logger logger = 
      Logger.getLogger(HCHadbAdapter.class.getName());

    public void loadHCHadb()
        throws InstantiationException {
    }

    /*
    * This is the list of accessors to the object
    */
    public Boolean getClusterSane() throws MgmtException {
	return new Boolean(HadbUtils.getClusterSane());
    }

    public BigInteger getDummy() {
        return BigInteger.valueOf(0);
    }

    /*
     * This is the list of custom actions
     */

    public BigInteger clearHadbFailure(BigInteger dummy) throws MgmtException {
        try {
            MetadataClient.getInstance().clearFailure();
        } catch (EMDException e) {
            MgmtException newe = new MgmtException(e.getMessage());
            newe.initCause(e);
            throw newe;
        }
        return BigInteger.valueOf(0);
    }

    public BigInteger getCacheStatus (BigInteger dummy) throws MgmtException {
        try {
            int result = MetadataClient.getInstance().getCacheStatus();
            return BigInteger.valueOf(result);
        } catch (EMDException e) {
            logger.log(Level.SEVERE,"Internal error getting hadb status " +
                       "- is HADB master service available?", e);

            throw new MgmtException("Cannot access HADB.");
        }
    }

    public Long getLastCreateTime (BigInteger dummy) throws MgmtException {
        try {
            long result = MetadataClient.getInstance().getLastCreateTime();
            return new Long(result);
        } catch (Exception e) {
            logger.log(Level.SEVERE,"Internal error getting hadb create time " +
                       "- is HADB master service available?", e);

            throw new MgmtException("Cannot access HADB.");
        }
    }

    public String getHadbStatus(BigInteger dummy) throws MgmtException {
	return HadbUtils.getHadbStatus();
    }


}

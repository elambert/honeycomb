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

import java.math.BigInteger;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.multicell.lib.Cell;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.ConfigPropertyNames;


public class HCSiloAdapter implements HCSiloAdapterInterface {

    private static final Logger logger =
      Logger.getLogger(HCSiloAdapter.class.getName());


    private Cell [] cells = null;
    private MultiCellLib multicellLib = null;
    private MgmtServer  mgmtServer;


    public void loadHCSilo() throws InstantiationException {
        multicellLib = MultiCellLib.getInstance();
        mgmtServer = MgmtServer.getInstance();
        cells = multicellLib.getCells();
        if (cells == null) {
            throw new InstantiationException("can't get list of cells");
        }
    }

    public void populateCellIds(List<Byte> array) throws MgmtException {
        for (int i =0; i < cells.length; i++) {
            Byte cellId = new Byte((byte) cells[i].getCellid());
            array.add(cellId);
        }
    }


    public BigInteger getNoCells() throws MgmtException {
        return BigInteger.valueOf(cells.length);
    }

    //
    // All a big potential security hole - see note in AdminClientImpl.java
    // fix by requiring a cryptedpass parameter.
    //
    public BigInteger login(BigInteger sessionId) throws MgmtException {

        if (!multicellLib.isCellStandalone() && !multicellLib.isCellMaster()) {
            return BigInteger.valueOf(CliConstants.MGMT_NOT_MASTER_CELL);
        }
        if(mgmtServer.getSessionId()==sessionId.longValue()) {
            mgmtServer.updateSessionTime();            
            return BigInteger.valueOf(CliConstants.MGMT_OK);
        } else {
            //
            // Session id is either someone else or nobody
            //
            if (mgmtServer.getSessionId()==-1 || mgmtServer.isTimedOut()) {
                //
                // nobody, or someone who is timed out.
                //
                mgmtServer.updateSessionTime();
                mgmtServer.setSessionId(sessionId.longValue());               
                return BigInteger.valueOf(CliConstants.MGMT_OK);
            } else {
                //
                // Someone else, and they're not timed out.
                //
                return BigInteger.valueOf( CliConstants.MGMT_ALREADY_LOGGED);
            }
        }
    }

    public BigInteger logout(BigInteger sessionId) throws MgmtException {

        if(mgmtServer.getSessionId()==sessionId.longValue()) {
            mgmtServer.clearSessionTime();
            mgmtServer.setSessionId(-1);

        }
        return BigInteger.valueOf(0);
    }

    public BigInteger loggedIn(BigInteger sessionId) throws MgmtException {
        if(mgmtServer.getSessionId()==sessionId.longValue()) {            
            mgmtServer.updateSessionTime();
            return BigInteger.valueOf(0);
        } else {
            if(!mgmtServer.isTimedOut()) {
                return BigInteger.valueOf(-1);
            } else {
                return login(sessionId);
            }
        }

    }

    /**
     * Set the protocol password used for webdev
     * @param realmName the realm name
     * @param userName the user name
     * @param hash the hashed password
     * @return BigInteger 
     * CliConstants.SUCCESS for success, 
     * CliConstants.FAILURE for failure
     * @throws MgmtException for Illegal realm or user name
     */
    public BigInteger setProtocolPassword(String realmName,
      String userName,
      String hash) throws MgmtException {
        ClusterProperties config = ClusterProperties.getInstance();
        byte[] hashedPasswd=ByteArrays.toByteArray(hash);
	
        String delimeter = config.getProperty(
            ConfigPropertyNames.PROP_WEDEV_AUTH_DELIM);
        if (delimeter == null || delimeter.length() == 0) {
            delimeter = ",";
            try {
                config.put(ConfigPropertyNames.PROP_WEDEV_AUTH_DELIM, 
                  delimeter);
            } catch (ServerConfigException e) {
                return BigInteger.valueOf(CliConstants.FAILURE);
            }
        }
	
        if (userName.contains(delimeter)) {
            throw new MgmtException("Illegal user name of " + userName 
              + " specified.  '" 
              + delimeter + "' character is not allowed.");
        }
	
        if (realmName.contains(delimeter)) {
            throw new MgmtException("Illegal realm name of " + realmName
              + " specified.  '" 
              + delimeter + "' character is not allowed.");
        }
	
	    // Check to ensure that 
        //
        // Warning - duplicated code from HCUserRealm in filesystem fixme
        // everything builds cleanly.
        //
        StringBuffer hbuf = new StringBuffer();
        
        hbuf.append(realmName);
        hbuf.append(delimeter).append(userName).append(delimeter);
        hbuf.append(ByteArrays.toHexString(hashedPasswd));

        String hashFull = hbuf.toString();

        logger.info("Saving password string \"" + hashFull + "\"");

        try {
            config.put(ConfigPropertyNames.PROP_WEDEV_AUTH_HASH, hashFull);
        } catch (ServerConfigException e) {
            return BigInteger.valueOf(CliConstants.FAILURE);
        }
        return BigInteger.valueOf(CliConstants.SUCCESS);
    }

    public Byte getCellId() throws MgmtException {
        ClusterProperties config = ClusterProperties.getInstance();
        return new Byte((byte) 
          Byte.parseByte(config.getProperty(ConfigPropertyNames.PROP_CELLID)));
    }

    public void populateAdminVIPs(List<String> array) throws MgmtException {
        for (int i =0; i < cells.length; i++) {
            String adminVIP = cells[i].getAdminVIP();
            array.add(adminVIP);
        }
    }
}


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

import com.sun.honeycomb.ndmp.NDMPProxyIF;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;

import java.math.BigInteger;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HCNDMPAdapter implements HCNDMPAdapterInterface{

    Logger logger = Logger.getLogger(getClass().getName());
    NDMPProxyIF ndmp = null;

    public void loadHCNDMP()
        throws InstantiationException
    {
        if (logger.isLoggable(Level.FINE))
            logger.fine("NDMP instantiating HCNDMPAdapter");
    }
    
    public HCNDMPAdapter(){
        if (logger.isLoggable(Level.INFO))
            logger.info("NDMP instantiating HCNDMPAdapter");
    }

    private void getProxy(){
        if (ndmp != null)
            return;
        try{
            NodeMgrService.Proxy proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            if (proxy == null) {
                logger.severe("NDMP Can't get NodeMgr proxy!");
            }
            else{
                Node master = proxy.getMasterNode();
                Object o = ServiceManager.proxyFor(master.nodeId(), "NDMP").getAPI();
                ndmp = (NDMPProxyIF) ServiceManager.proxyFor(master.nodeId(), "NDMP").getAPI();
                if (ndmp == null) {
                    logger.severe("NDMP Can't get NDMP proxy!");
                }
            }
        }
        catch (RuntimeException e){
            logger.log(Level.SEVERE, "NDMP " + e.getMessage(), e);
        }
    }

    public String getBackupStatus() throws MgmtException{
        getProxy();
        if (ndmp == null)
            return "unknown";
        else{        
            return ndmp.getStatus();
        }
    }
   




            //     private final static BigInteger UNKNOWN_PORT = BigInteger.valueOf(-1);


            //     public BigInteger getBackupControlPort() throws MgmtException{
            //         getProxy();
            //         if (ndmp == null)
            //             return UNKNOWN_PORT;
            //         else
            //             return BigInteger.valueOf(ndmp.getControlPort());
            //     }

            //     public void setBackupControlPort(BigInteger port) throws MgmtException{
            //         getProxy();
            //         if (ndmp != null)
            //             ndmp.setControlPort(port.intValue());
            //     }


            //     public BigInteger getBackupInboundDataPort() throws MgmtException{
            //         getProxy();
            //         if (ndmp == null)
            //             return UNKNOWN_PORT;
            //         else
            //             return BigInteger.valueOf(ndmp.getInboundDataPort());
            //     }

            //     public void setBackupInboundDataPort(BigInteger port) throws MgmtException{
            //         getProxy();
            //         if (ndmp != null)
            //             ndmp.setInboundDataPort(port.intValue());
            //     }


            //     public BigInteger getBackupOutboundDataPort() throws MgmtException{
            //         getProxy();
            //         if (ndmp == null)
            //             return UNKNOWN_PORT;
            //         else
            //             return BigInteger.valueOf(ndmp.getOutboundDataPort());
            //     }

            //     public void setBackupOutboundDataPort(BigInteger port) throws MgmtException{
            //         getProxy();
            //         if (ndmp != null)
            //             ndmp.setOutboundDataPort(port.intValue());
            //     }


            //     public Boolean getProceedAfterError () throws MgmtException{
            //         getProxy();
            //         if (ndmp == null)
            //             return Boolean.FALSE;
            //         else
            //             return Boolean.valueOf(ndmp.getProceedAfterError());
            //     }

            //     public void setProceedAfterError (Boolean proceed) throws MgmtException{
            //         getProxy();
            //         if (ndmp != null)
            //             ndmp.setProceedAfterError(proceed.booleanValue());
            //     }
        }


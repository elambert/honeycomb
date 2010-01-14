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

import com.sun.honeycomb.admin.mgmt.Reassure;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.multicell.MultiCellIntf;
import com.sun.honeycomb.multicell.MultiCellException;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.multicell.lib.Cell;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.multicell.lib.MultiCellLibError;
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagCellData;
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagData;
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagsGenerator;
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagsRegistry;
import com.sun.honeycomb.util.SolarisRuntime;

import com.sun.servicetag.ServiceTag;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

//
// codecs - encode/decode support
//
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.net.QuotedPrintableCodec;

public abstract class HCCellAdapterBase implements HCCellAdapterInterface {
    //
    // All properties (not including those in HCCellProps, which are 
    // handled differently.
    //
    protected static String[] cellProps = {
        ConfigPropertyNames.PROP_NTP_SERVER,
        ConfigPropertyNames.PROP_SMTP_SERVER,
        ConfigPropertyNames.PROP_SMTP_PORT,
        ConfigPropertyNames.PROP_EXT_LOGGER,
        ConfigPropertyNames.PROP_AUTH_CLI,        
        ConfigPropertyNames.PROP_DNS,
        ConfigPropertyNames.PROP_DOMAIN_NAME,
        ConfigPropertyNames.PROP_DNS_SEARCH,
        ConfigPropertyNames.PROP_PRIMARY_DNS_SERVER,
        ConfigPropertyNames.PROP_SECONDARY_DNS_SERVER};

    public static final String EXPAN_STR_READY  = "none";
    public static final String EXPAN_STR_EXPAND = "sloshing";
    public static final String EXPAN_STR_DONE   = "complete";
   
    private static transient final Logger logger = 
      Logger.getLogger(HCCellAdapterBase.class.getName());

    private static final int BUFSIZE = 524288;

    protected ClusterProperties    config;
    protected MgmtServer           mgmtServer;
    protected int                  numNodes;
    protected byte                 localCellid;

    public HCCellAdapterBase() {
        config = null;
    }

    public void loadHCCellBase()
        throws InstantiationException {

        config = ClusterProperties.getInstance();
        mgmtServer = MgmtServer.getInstance();
        numNodes = ClusterProperties.getInstance().getPropertyAsInt(
            ConfigPropertyNames.PROP_NUM_NODES);
        localCellid = Byte.parseByte(
            config.getProperty(ConfigPropertyNames.PROP_CELLID));

    }

    protected static QuotedPrintableCodec codec = new QuotedPrintableCodec();
    protected static String encode (String s) throws EncoderException {
        return codec.encode(s);
    }

    protected static String decode (String s) throws DecoderException{
        return codec.decode(s);
    }

    public Byte getCellId() throws MgmtException {
        return new Byte(localCellid);
    }

    public BigInteger getNumNodes() {
        return BigInteger.valueOf(numNodes);
    }
    /**
     * placeholoder - adds "isAlive" variable to the structure
     * so it can be populated in case we're returning 
     * a dummy structure on the ADM side.
     */
    public Boolean getIsAlive() throws MgmtException {
        return new Boolean(true);
    }

    public HCCellProps getCellProps() throws MgmtException {
        HCCellProps cellProps = new HCCellProps();
        cellProps.setAdminVIP(MultiCellLib.getInstance().getAdminVIP());
        cellProps.setDataVIP(MultiCellLib.getInstance().getDataVIP());
        cellProps.setSpVIP(MultiCellLib.getInstance().getSPVIP());
        cellProps.setSubnet(MultiCellLib.getInstance().getSubnet());
        cellProps.setGateway(MultiCellLib.getInstance().getGateway());
        return cellProps;
    }

    //
    // Externally available, for pushing actual cell props only.
    // Currently, all of these require both switch and cheat updating,
    // so there's no-op logic in there. 
    //
    // Note that setCellProperties(Map) method only handles properties
    // that end up in the config/config.properties. This handles
    // those properites that end up in the silo_props.xml (side node -
    // is that a misnomer? they're actually cell level properties that
    // affect silos, but oh well.)
    //
    public BigInteger setCellProps(EventSender evt, 
                                   HCCellProps value, 
                                   Byte cellid) throws MgmtException {

        boolean updateSwitch = false;
        boolean updateSp = false;
        boolean isUpdateLocal = (localCellid == cellid.byteValue()) ?
          true : false;

        HashMap map = new HashMap();
        if (!value.getAdminVIP().equals(
                MultiCellLib.getInstance().getAdminVIP())) {
            updateSp = true;
            updateSwitch = true;
            map.put(MultiCellLib.PROP_ADMIN_VIP, value.getAdminVIP());
        } 
        if (!value.getDataVIP().equals(
                MultiCellLib.getInstance().getDataVIP())) {
            updateSp = true;
            updateSwitch = true;
            map.put(MultiCellLib.PROP_DATA_VIP, value.getDataVIP());
        } 
        if (!value.getSpVIP().equals(MultiCellLib.getInstance().getSPVIP())) {
            updateSwitch = true;
            updateSp = true;
            map.put(MultiCellLib.PROP_SP_VIP, value.getSpVIP());
        }
        if (!value.getSubnet().equals(MultiCellLib.getInstance().getSubnet())) {
            updateSwitch = true;
            updateSp = true;
            map.put(MultiCellLib.PROP_SUBNET, value.getSubnet());
        }
        if (!value.getGateway().equals(
                MultiCellLib.getInstance().getGateway())) {
            updateSwitch = true;
            updateSp = true;
            map.put(MultiCellLib.PROP_GATEWAY, value.getGateway());
        }
        if (map.size() != 0) {
            if (!isUpdateLocal) {
                // Update the of silo_info.xml properties on the local cell
                MultiCellLib.getInstance().updateProperties(
                    cellid.byteValue(), map);

                try {
                    evt.sendAsynchronousEvent("successfully updated the " +
                      "configuration [cell " + localCellid + "]");
                } catch (MgmtException e) {
                    logger.severe("failed to send synchronous event " + e);   
                }
                //
                // Other cells-- on which no configuration change occur need to
                // notify Multicell about those changes.
                //
                Cell updatedCell = new Cell(cellid.byteValue(), 
                  value.getAdminVIP(), value.getDataVIP(), value.getSpVIP(),
                  null, value.getSubnet(), value.getGateway());
                
                // TODO: This code isn't valid for the emulator
                // Proxy calls don't seem to be supported.  The
                // call to api.cahngeCellCfg() will fail with a class not
                // found exception.  Need to fix for the emulator
                MultiCellIntf api = MultiCellIntf.Proxy.getMultiCellAPI();
                if (api == null) {
                    logger.severe("failed to grab multicellAPI");
                    if (MultiCellLib.getInstance().isCellMaster()) {
                        throw new MgmtException("Internal error while " +
                          "notifying services on master cell. Will " +
                          " require a reboot of the master cell [cell " + 
                          localCellid + "]");
                    } else {
                        return BigInteger.valueOf(-1);
                    }
                }
                try {
                    api.changeCellCfg(updatedCell);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, 
                      "failed to update Multicell service", e);
                    if (MultiCellLib.getInstance().isCellMaster()) {
                        throw new MgmtException("Internal error while " +
                          "notifying services on master cell. Will " +
                          " require a reboot of the master cell [cell " + 
                          localCellid + "]");
                    } else {
                        return BigInteger.valueOf(-1);
                    }
                }
            } else {
                //
                // We need to preform those config/update and any subsequent
                // operation in an async way because the dataVIP may be
                // reconfigured under our feet and the CLI gets screwed.
                //
                try {
                    evt.sendAsynchronousEvent("will update the configuration " +
                      " and reboot the cell [cell " + localCellid + "]");
                } catch (MgmtException e) {
                    logger.severe("failed to send synchronous event " + e);   
                }
                updatePropertiesAndRebootCell(map, updateSwitch, updateSp);
            }
        }
        return BigInteger.valueOf(0);
    }


    public HCAlertAddr getAlertAddr() throws MgmtException {
        HCAlertAddr alertAddr = new HCAlertAddr();
        alertAddr.setSmtpTo(config.getProperty(ConfigPropertyNames.PROP_SMTP_TO));
        alertAddr.setSmtpCC(config.getProperty(ConfigPropertyNames.PROP_SMTP_CC));
        return alertAddr;
    }
    

    public void setAlertAddr(HCAlertAddr value) throws MgmtException {
        HashMap map = new  HashMap();


        if(value.getSmtpTo() != null) {
            map.put(ConfigPropertyNames.PROP_SMTP_TO, value.getSmtpTo());    
	}

        if(value.getSmtpCC() != null) {
            map.put(ConfigPropertyNames.PROP_SMTP_CC,value.getSmtpCC());
	}


        setCellProperties(map);      

    }
    public String getLicense() throws MgmtException {
        String license = config.getProperty(ConfigPropertyNames.PROP_LICENSE);
        if(null== license){
            //
            // Auto generated code can't pass back a null
            // 
            return "";
        } else {
            return license;
        }
    }

    public void setLicense(String value) throws MgmtException {
        HashMap map = new  HashMap();        
        map.put(ConfigPropertyNames.PROP_LICENSE, value);
        setCellProperties(map);
    }

    protected void setCellProperties(Map properties) throws MgmtException {

        try {
            config.putAll(properties);
        } catch (ServerConfigException e) {
            logger.severe("failed to update properties..." + e);
        }
    }
    



    //
    // Calls the uncondiotnal version - external call part of setupcell
    //
    public BigInteger updateSwitch(BigInteger dummy) 
        throws MgmtException {
        updateSwitchUnconditionally();
        return BigInteger.valueOf(0);
    }    

    public BigInteger setPublicKey(String publicKey) throws MgmtException {
        HashMap map = new HashMap();
        map.put(ConfigPropertyNames.PROP_ADMIN_PUBKEY, publicKey);
        setCellProperties(map);        
        return BigInteger.valueOf(0);
    }




    public BigInteger addCell(EventSender evt, 
      String adminVIP, String dataVIP) throws MgmtException {

        logger.info("MC: addCell adminVip " + adminVIP +
                               ", dataVIP = " + dataVIP);
        byte res = -1;

        //
        // First check this cell has 16 nodes
        //
        boolean acceptMC8Nodes = 
          config.getPropertyAsBoolean(ConfigPropertyNames.PROP_ACCEPT_8_NODES);
        //
        // FIXME - use MAX NODES property
        //
        if (numNodes < 16) {
              if (acceptMC8Nodes) {
                  logger.info("MC: cluster is not 16 nodes but is " +
                    "configured to accept mc configuration");
              } else {
                  try {
                      evt.sendAsynchronousEvent("Need to 16 nodes cell to be " +
                        "able to add more cells");
                  } catch (Exception ignore) {
                      logger.severe("failed to send the async " + 
                        "event ");
                  }
                 return BigInteger.valueOf(-1);
              }
          }

        MultiCellIntf api = MultiCellIntf.Proxy.getMultiCellAPI();
        if (api == null) {
            logger.severe("failed to grab multicellAPI");
            return BigInteger.valueOf(-1);
        }
        try {
            res = api.addCellStart(adminVIP, dataVIP);
            evt.sendAsynchronousEvent("Successfully established connection with "
              + "the remote cell");

            res = api.addCellSchemaValidation();
            evt.sendAsynchronousEvent("Successfully checked schema is " +
              "identical on both cells");

            res = api.addCellPropertiesValidation();
            evt.sendAsynchronousEvent("Successfully checked properties are " +
              "identical on both cells");

            res = api.addCellUpdateHiveConfig();
            evt.sendAsynchronousEvent("Successfully updated the hive "+
              "configuration ");
            
            return BigInteger.valueOf(res);
        } catch (IOException ioe) {
            logger.log(Level.SEVERE,
              "failed to add cell in the hive", ioe);
            //
            // Internationalize here
            //
            throw new MgmtException("Internal error while adding the new cell "+
              "in the hive");
        } catch (MultiCellException mue) {
            logger.log(Level.SEVERE,
              "failed to add cell in the hive", mue);
            throw new MgmtException(mue.getMessage());
        } catch (ManagedServiceException mse) {
            logger.log(Level.SEVERE,
              "failed to add cell in the hive", mse);
            throw new MgmtException("Internal error while adding the new cell "+
              "in the hive");
        }
    }

    public BigInteger delCell(Byte cellId) throws MgmtException {
        logger.info("MC: delCell cell ID = " + cellId);
        MultiCellIntf api = MultiCellIntf.Proxy.getMultiCellAPI();
        if (api == null) {
            logger.severe("failed to grab multicellAPI");
            return BigInteger.valueOf(-1);
        }
        try {
            api.removeCell(cellId);
            return BigInteger.valueOf(0);
        } catch (IOException ioe) {
            logger.log(Level.SEVERE,
              "failed to delete cell from the hive", ioe);
            throw new MgmtException("Internal error while deleting the cell "+
              "from the hive");
        } catch (MultiCellException mue) {
            logger.log(Level.SEVERE,
              "failed to delete cell from the hive", mue);
            throw new MgmtException(mue.getMessage());
        } catch (ManagedServiceException mse) {
            logger.log(Level.SEVERE,
              "failed to delete cell from the hive", mse);
            throw new MgmtException("Internal error while deleting the cell "+
              "from the hive");            
        }
    }




    public BigInteger setEncryptedPasswd(String encryptedPasswd) 
        throws MgmtException{

        HashMap map = new HashMap();
        try {
            map.put(ConfigPropertyNames.PROP_ADMIN_PASSWD, decode(encryptedPasswd));
        } catch(DecoderException e) {
            logger.log(Level.SEVERE,
              "failed to decode string " + encryptedPasswd);
            //
            // Internationalize here
            //
            throw new MgmtException ("Internal error, failed to decode password.");
        }
        setCellProperties(map);        

        return BigInteger.valueOf(0);
    }


    public String getCryptedPassword() throws MgmtException {
        String passwd = config.getProperty (ConfigPropertyNames.PROP_ADMIN_PASSWD);
        if (null == passwd) {
            return "NotDefined";
        } else {
            try {
                return encode(passwd);
            } catch(EncoderException e) {
                logger.log(Level.SEVERE,
                           "failed to encode string " + passwd);
                //
                // Internationalize here
                //
                throw new MgmtException ("Internal error, failed to encode password.");
            }
        }

    }

    public HCSiloProps getSiloProps() throws MgmtException {
        HCSiloProps siloProps = new HCSiloProps();

        siloProps.setSmtpServer(config.getProperty(ConfigPropertyNames.PROP_SMTP_SERVER));
        siloProps.setNtpServer(config.getProperty(ConfigPropertyNames.PROP_NTP_SERVER));
        siloProps.setSmtpPort(config.getProperty(ConfigPropertyNames.PROP_SMTP_PORT));
        siloProps.setDns(config.getProperty(ConfigPropertyNames.PROP_DNS));
        siloProps.setDomainName(config.getProperty(ConfigPropertyNames.PROP_DOMAIN_NAME));
        siloProps.setDnsSearch(config.getProperty(ConfigPropertyNames.PROP_DNS_SEARCH));
        siloProps.setPrimaryDnsServer(config.getProperty(ConfigPropertyNames.PROP_PRIMARY_DNS_SERVER));
        siloProps.setSecondaryDnsServer(config.getProperty(ConfigPropertyNames.PROP_SECONDARY_DNS_SERVER));
        siloProps.setAuthorizedClients(config.getProperty(ConfigPropertyNames.PROP_AUTH_CLI));
        siloProps.setNumAuthRules(config.getProperty(ConfigPropertyNames.PROP_AUTH_NUM_RULES));
        siloProps.setExtLogger(config.getProperty(ConfigPropertyNames.PROP_EXT_LOGGER));

        return siloProps;
    }

    public void setSiloProps(HCSiloProps value) throws MgmtException {
        HashMap map = new  HashMap();

        if(value.getSmtpServer() != null) {
            if (null != config.getProperty(ConfigPropertyNames.PROP_SMTP_SERVER)) {
                if (!value.getSmtpServer().equals(config.getProperty(ConfigPropertyNames.PROP_SMTP_SERVER))) {
                    map.put(ConfigPropertyNames.PROP_SMTP_SERVER, value.getSmtpServer());
                }   
            } else {
                map.put(ConfigPropertyNames.PROP_SMTP_SERVER, value.getSmtpServer());
            }
        }
        if(value.getSmtpPort() != null) {
            if (null != config.getProperty(ConfigPropertyNames.PROP_SMTP_PORT)) {
                if (!value.getSmtpPort().equals(config.getProperty(ConfigPropertyNames.PROP_SMTP_PORT))) {
                    map.put(ConfigPropertyNames.PROP_SMTP_PORT, value.getSmtpPort());
                }
            } else {
                map.put(ConfigPropertyNames.PROP_SMTP_PORT, value.getSmtpPort());
            }
        }


        if(value.getNtpServer() != null) {
            if (null != config.getProperty(ConfigPropertyNames.PROP_NTP_SERVER)) {
                if (!value.getNtpServer().equals(config.getProperty(ConfigPropertyNames.PROP_NTP_SERVER))) {
                    map.put(ConfigPropertyNames.PROP_NTP_SERVER, value.getNtpServer());
                }
            } else {
                map.put(ConfigPropertyNames.PROP_NTP_SERVER, value.getNtpServer());
            }
        }

        if(value.getDns() != null) {
            if (null != config.getProperty(ConfigPropertyNames.PROP_DNS)) {
                if (!value.getDns().equals(config.getProperty(ConfigPropertyNames.PROP_DNS))) {
                    map.put(ConfigPropertyNames.PROP_DNS, value.getDns());
                }
            } else {
                map.put(ConfigPropertyNames.PROP_DNS, value.getDns());
            }
        }



        if(value.getDomainName() != null) {
            if (null != config.getProperty(ConfigPropertyNames.PROP_DOMAIN_NAME)) {
                if (!value.getDomainName().equals(config.getProperty(ConfigPropertyNames.PROP_DOMAIN_NAME))) {
                    map.put(ConfigPropertyNames.PROP_DOMAIN_NAME, value.getDomainName());
                }
            } else {
                map.put(ConfigPropertyNames.PROP_DOMAIN_NAME, value.getDomainName());
            }
        }


        if(value.getDnsSearch() != null) {
            if (null != config.getProperty(ConfigPropertyNames.PROP_DNS_SEARCH)) {
                if (!value.getDnsSearch().equals(config.getProperty(ConfigPropertyNames.PROP_DNS_SEARCH))) {
                    map.put(ConfigPropertyNames.PROP_DNS_SEARCH, value.getDnsSearch());
                }
            } else {
                map.put(ConfigPropertyNames.PROP_DNS_SEARCH, value.getDnsSearch());
            }
        }

        if(value.getPrimaryDnsServer() != null) {
            if (null != config.getProperty(ConfigPropertyNames.PROP_PRIMARY_DNS_SERVER)) {
                if (!value.getPrimaryDnsServer().equals(config.getProperty(ConfigPropertyNames.PROP_PRIMARY_DNS_SERVER))) {
                    map.put(ConfigPropertyNames.PROP_PRIMARY_DNS_SERVER, value.getPrimaryDnsServer());
                }
            } else {
                map.put(ConfigPropertyNames.PROP_PRIMARY_DNS_SERVER, value.getPrimaryDnsServer());
            }
        }

        if(value.getSecondaryDnsServer() != null) {
            if (null != config.getProperty(ConfigPropertyNames.PROP_SECONDARY_DNS_SERVER)) {
                if (!value.getSecondaryDnsServer().equals(config.getProperty(ConfigPropertyNames.PROP_SECONDARY_DNS_SERVER))) {
                    map.put(ConfigPropertyNames.PROP_SECONDARY_DNS_SERVER, value.getSecondaryDnsServer());
                }
            } else {
                map.put(ConfigPropertyNames.PROP_SECONDARY_DNS_SERVER, value.getSecondaryDnsServer());
            }
        }

        if(value.getAuthorizedClients() != null) {
            if (null!=config.getProperty(ConfigPropertyNames.PROP_AUTH_CLI)) {
                if (!value.getAuthorizedClients().equals(config.getProperty(ConfigPropertyNames.PROP_AUTH_CLI))) {
                    map.put(ConfigPropertyNames.PROP_AUTH_CLI, value.getAuthorizedClients());
                }
            } else {
                map.put(ConfigPropertyNames.PROP_AUTH_CLI, value.getAuthorizedClients());
            }
        }
        if(value.getNumAuthRules() != null) {
            if (null!=config.getProperty(ConfigPropertyNames.PROP_AUTH_NUM_RULES)) {
                if (!value.getNumAuthRules().equals(config.getProperty(ConfigPropertyNames.PROP_AUTH_NUM_RULES))) {
                    map.put(ConfigPropertyNames.PROP_AUTH_NUM_RULES, value.getNumAuthRules());
                }
            } else {
                map.put(ConfigPropertyNames.PROP_AUTH_NUM_RULES, value.getNumAuthRules());
            }
        }
        if(value.getExtLogger()!= null) {
            if (null!=config.getProperty(ConfigPropertyNames.PROP_EXT_LOGGER)) {
                if (!value.getExtLogger().equals(config.getProperty(ConfigPropertyNames.PROP_EXT_LOGGER))) {
                    map.put(ConfigPropertyNames.PROP_EXT_LOGGER, value.getExtLogger());
                }
            } else {
                map.put(ConfigPropertyNames.PROP_EXT_LOGGER, value.getExtLogger());
            }
        }
	if (map.size() != 0) {
	    setCellProperties(map);
	    updateSwitch(map);
	}
    }
    
    public static final String SERVICE_TAG_REGISTRY_IS_NOT_AVAILABLE_MSG =
            "Failed to update service tag registry file."
            + "\nService Tag packages are not installed. ";
    
    
    /**
     * Clear the service tag registry of any existing service tag entries
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    protected void clearRegistry()
    throws MgmtException {
        // One a live system.  We are the only entities in the file
        // Do a brute force remove of the file
        ServiceTagsRegistry.remove();
    }
    
    /**
     * Update the service tag data associated with a single cell.   This routine
     * must be called on ever cell in order to keep the silo_info.xml.  On
     * the master node this will clear the registry file.  If the cell is
     * a single cell system the registry file will be updated.  An update
     * does not happen on multi-cell since we can't update all the instanceIDs
     * on all cells.  Therefore we required the operator to do a 
     * "servicetags --refresh"
     * @param evt the callback handle
     * @param cellData the service tag data to update
     * @return BigInteger, 0 for SUCCESS, -1 for failure, -2 for failed
     * to update registry file on a single cell system.   
     * Currently always returns 0
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    public BigInteger updateServiceTagData(EventSender evt,
        HCServiceTagCellData cellData)
    throws MgmtException {
    
        MultiCellLib multiCell = MultiCellLib.getInstance();
        boolean isStandAlone = multiCell.isCellStandalone();
        StringBuffer buf = new StringBuffer();
        buf.append(" service tag data on cell");
        if (isStandAlone == false) {
            buf.append(" ").append(cellData.getCellId()).append(" on cell ");
            buf.append(getCellId());
        }
        buf.append(".");
        Reassure reassureThread = new Reassure(evt);
        try {
            reassureThread.start();
            boolean isSupported = ServiceTagsRegistry.isSupported();
            boolean isMaster = multiCell.isCellMaster();
            if (isMaster) {
                // If we modify an service tag data the service tag registry
                // is considered out of date.  As such we clear the registry
                // of all ST5800 service tags.
                if (isSupported)
                    clearRegistry();
            }
            String instanceURN = null;
            if (isStandAlone) {
                // When we have a single cell system we can automatically
                // update the service tag registry.  On a multi-cell hive
                // this isn't possible since we can't update all the 
                // instance information on all the cells.  The operator
                // in that scenario must follow up with a "servicetag --refresh"
                // command.
                instanceURN = ServiceTag.generateInstanceURN();
            }
            ServiceTagData data = new ServiceTagData(
                    cellData.getProductNumber(), 
                    cellData.getProductSerialNumber(), 
                    cellData.getMarketingNumber(),
                    instanceURN);

            multiCell.updateServiceTagData(cellData.getCellId(), data);
            buf.insert(0, "Successfully updated");
            logger.log(Level.INFO, buf.toString());
            //evt.sendAsynchronousEvent(buf.toString());
            
            if (isStandAlone) {
                // Reset the buf to new action in case of exception
                buf = new StringBuffer(" service tag registry.");
                
                
                ClusterProperties props = ClusterProperties.getInstance();
                boolean disabled = props.getPropertyAsBoolean(
                    ConfigPropertyNames.PROP_SERVICE_TAG_SERVICE_DISABLED);
                if (disabled) {
                    evt.sendAsynchronousEvent(
                            "Service Tag service is disabled service tag "
                            + "registry file will not be updated.");
                    return BigInteger.valueOf(
                            CliConstants.SERVICE_TAG_REGISTRY_UPDATE_FAILURE);
                            
                }
                
                // This is a single cell system.
                // We can automatically update the service tag registry
                if (isSupported == false) {
                    evt.sendAsynchronousEvent(
                            SERVICE_TAG_REGISTRY_IS_NOT_AVAILABLE_MSG);
                    return BigInteger.valueOf(
                            CliConstants.SERVICE_TAG_REGISTRY_UPDATE_FAILURE);
                }
                ServiceTagCellData[] cells = new ServiceTagCellData[] {
                    new ServiceTagCellData(getCellId(), data)
                };
                ServiceTagsGenerator generator = 
                    new ServiceTagsGenerator(cells);
                if (generator.isValid() == false) {
                    // This should never happen on a single cell system
                    buf = new StringBuffer()
                        .append("Failed to update service tag registry due to a ")
                        .append("validation error.\nSee logs for further details.");
                    // Validation errors found via ServiceTagsGenerator are
                    // logged by default
                    evt.sendAsynchronousEvent(buf.toString()); 
                    return BigInteger.valueOf(
                            CliConstants.SERVICE_TAG_REGISTRY_VALIDATION_FAILURE);
                } else {
                    ServiceTagsRegistry.update(generator.getServiceTags());
                    buf.insert(0, "Successfully updated");
		    evt.sendAsynchronousEvent(buf.toString());
                    logger.log(Level.INFO, buf.toString());
                }
            }
            return BigInteger.ZERO;
        } 
        catch (MgmtException me) { 
            buf.insert(0, "Failed to update");
            logger.log(Level.SEVERE, buf.toString(), me);
            buf.append(" Reason: ");
            buf.append(me.getMessage());
	    buf.append(".\n");
            buf.append("Ensure that the servicetag entries are all valid by invoking the command,");
	    buf.append("'servicetags --refresh'");
            evt.sendAsynchronousEvent(buf.toString()); 
            return BigInteger.valueOf(CliConstants.FAILURE);
        }
        catch (Exception ex) {
            buf.insert(0, "Failed to update");
            logger.log(Level.SEVERE, buf.toString(), ex);
            buf.append(" Reason: ");
            buf.append(ex.getMessage());
	    buf.append(".\n");
            buf.append("Ensure that the servicetag entries are all valid by invoking the command,");
            buf.append("'servicetags --refresh'");

            evt.sendAsynchronousEvent(buf.toString()); 
            return BigInteger.valueOf(CliConstants.FAILURE);    
        } 
        finally {
            if (reassureThread != null)
                reassureThread.safeStop();
        }
    }
    
    /**
     * Update the service tag data associated with a single cell.   This routine
     * must be called on ever cell in order to keep the silo_info.xml
     * up to date.
     * <P>
     * On the master cell this routine will clear the service tag registry
     * and attempt to repopulate it with the new service tag registry 
     * information.
     * @param evt the callback handle
     * @param tagData the service tag data to update
     * @param updateRegistry boolean to indicate whether the registry
     * file should be rebuilt.  If the callee already knows the 
     * service tag data is invalid they will call this value with a
     * value of 0.  In this case this api is getting used to clear the
     * registry and the instanceURNs
     * @return BigInteger, 0 for SUCCESS, -1 for failure.  
     * Currently always returns 0
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    public BigInteger updateAllServiceTagData(EventSender evt,
        HCServiceTags tagData, BigInteger updateRegistry)
    throws MgmtException {
        
        boolean updateRegistryFile = updateRegistry.intValue() == 1;
        MultiCellLib multiCell = MultiCellLib.getInstance();
        boolean isStandAlone = multiCell.isCellStandalone();
        StringBuffer buf = new StringBuffer();
        buf.append(" service tag data for cell");
        if (isStandAlone == false) {
            buf.append(" ").append(getCellId());
        }
        buf.append(".");
        Reassure reassureThread = null;
        try {
            reassureThread = new Reassure(evt);
            reassureThread.start();
            boolean isSupported = ServiceTagsRegistry.isSupported();
            boolean isMaster = multiCell.isCellMaster();
            if (isSupported && isMaster) {
                // If we modify an service tag data the service tag registry
                // is considered out of date.  As such we clear the registry
                // of all ST5800 service tags.
                clearRegistry();
            }
            
            List<HCServiceTagCellData> list = tagData.getData();
            HCServiceTagCellData[] hCellData = 
                    (HCServiceTagCellData[])list.toArray(
                        new HCServiceTagCellData[list.size()]);
            ServiceTagCellData[] cellData = 
                    new ServiceTagCellData[hCellData.length];
            for (int i=0; i < hCellData.length; i++) {
                ServiceTagData data = new ServiceTagData(
                    hCellData[i].getProductNumber(), 
                    hCellData[i].getProductSerialNumber(), 
                    hCellData[i].getMarketingNumber(),
                    hCellData[i].getInstanceURN());
                ServiceTagCellData cell = 
                    new ServiceTagCellData(hCellData[i].getCellId(), data);
                cellData[i] = cell;
            }
            
            
            multiCell.updateServiceTagData(cellData);
            buf.insert(0, "Successfully updated");
            logger.log(Level.INFO, buf.toString());
            //evt.sendAsynchronousEvent(buf.toString()); 
            
            if (updateRegistryFile && isMaster) {
                // Reset the buf to new action in case of exception
                buf = new StringBuffer(" service tag registry.");
                if (isSupported == false) {
                    evt.sendAsynchronousEvent(
                            SERVICE_TAG_REGISTRY_IS_NOT_AVAILABLE_MSG);
                    return BigInteger.valueOf(
                            CliConstants.FAILURE);
                }
                // The silo_info.xml has been updated.
                // Now update the service tag registry
                ServiceTagsGenerator generator = 
                        new ServiceTagsGenerator(cellData);
                if (generator.isValid() == false) {
                    // This should technically never happen is everything is
                    // done via the cli since the validation check on the cli
                    // side should of failed and prevented this call from
                    // ever occurring
                    buf = new StringBuffer();
                    buf.append("Failed to update service tag registry due to a ");
                    buf.append("validation error.\nSee logs for further details.");
                    // Validation errors found via ServiceTagsGenerator are
                    // logged by default
                    evt.sendAsynchronousEvent(buf.toString());
                    return BigInteger.valueOf(
                            CliConstants.SERVICE_TAG_REGISTRY_VALIDATION_FAILURE);
                } else {
                    ServiceTagsRegistry.update(generator.getServiceTags());
                    buf.insert(0, "Successfully updated");
                    logger.log(Level.INFO, buf.toString());
                }
            }
            return BigInteger.ZERO;
        } 
        catch (MultiCellLibError mcle) { 
            buf.insert(0, "Failed to update");
            logger.log(Level.SEVERE, buf.toString(), mcle);
            buf.append(" Reason: ");
            buf.append(mcle.getMessage());
	    buf.append(".\n");
            buf.append("Ensure that the servicetag entries are all valid by invoking the command,\n");
            buf.append("'servicetags --refresh'.");

            evt.sendAsynchronousEvent(buf.toString()); 
            return BigInteger.valueOf(CliConstants.FAILURE); 
        }
        catch (Exception ex) {
            buf.insert(0, "Failed to update");
            logger.log(Level.SEVERE, buf.toString(), ex);
            buf.append(" Reason: ");
            buf.append(ex.getMessage());
	    buf.append(".\n");
            buf.append("Ensure that the servicetag entries are all valid by invoking the command,\n");
            buf.append("'servicetags --refresh'");
            evt.sendAsynchronousEvent(buf.toString()); 
            return BigInteger.valueOf(CliConstants.FAILURE);        
        } finally {
            if (reassureThread != null)
                reassureThread.safeStop();
        }
    }

    public String getBackupStatus() throws MgmtException{
        return "unknown";
    }

    public String getLanguage() throws MgmtException {
        return config.getProperty(ConfigPropertyNames.PROP_LANGUAGE);
    }

    public void setLanguage(String value) throws MgmtException {
        HashMap map = new  HashMap();
        map.put(ConfigPropertyNames.PROP_LANGUAGE, value);
        setCellProperties(map);
    }

    public abstract void updateSwitchUnconditionally()
        throws MgmtException;
    public abstract BigInteger updateServiceProcessor(BigInteger dummy)
        throws MgmtException;
    public abstract  void updateSwitch(Map changedProps)
        throws MgmtException;
    protected abstract void updatePropertiesAndRebootCell(Map props,
      boolean updateSwitch, boolean updateSP);

    protected abstract void rebootCell(EventSender evt,
                                       boolean switches, 
                                       boolean sp) throws MgmtException;
    /**
     * Method that executes the explorer script from the master node.
     * @param evt required for interactive commands
     * @param cmd explorer command to execute
     * @param cellId cell the explorer script is run on
     * @param expPath fully qualified path to explorer defaults file
     */                                    
    protected BigInteger execExplorer(EventSender evt, 
                                        String cmd,
                                        String cellId) throws MgmtException {
        Reassure reassureThread = null;
        SolarisRuntime sr = new SolarisRuntime();
        
        try {  
            reassureThread = new Reassure(evt);
            reassureThread.start();
            // Instituting a time delay is a hack for 1.1.1 since the automatic
            // explorer output file naming convention doesn't ensure uniqueness
            // for output files generated from cells of the same hive - time delay
            // is an attempt to change the timestamp, which is included as part 
            // of the output file name.  Trying to ensure that the minutes differ
            // for cells in a multicell system -- filed RFE 6663999 with the
            // explorer team
            int id = getCellId().intValue();
            long timeDelay = 0;
            if (id <= 10) {
                timeDelay = id * 60;
            } else if (id <= 128) {
                timeDelay = id%10 * 60;
            }
            logger.log(Level.INFO, "CELL-" + cellId + ":TIME DELAY = " + 
                    String.valueOf(timeDelay) + " sec.");
            try {
                Thread.sleep(timeDelay * CliConstants.ONE_SECOND);
            } catch (InterruptedException ex) {
                logger.log(Level.WARNING, "CELL-" + cellId + 
                        ":Unable to delay execution of explorer script for " +
                            String.valueOf(timeDelay) + " sec.");
            }
            sr.exec(cmd);
            InputStream pin = sr.getInputStream();     // process data
            OutputStream pout = sr.getOutputStream();  // write to the process
            InputStream perr = sr.getErrorStream();    // process errors
            byte[] buf = new byte[BUFSIZE]; // 512k buffer
            int read = CliConstants.FAILURE;
            
            System.in.skip (System.in.available());

            
            while(sr.processExists()) {
                // read from proc stdout
                int avail = pin.available();
                if (avail > buf.length) {
                    avail = buf.length;
                }
                if (avail > 0) {
                    // reading in the data from the running process
                    for(int idx = 0; idx < BUFSIZE; idx++) {
                        buf[idx] = '\0';
                    }
                    read = pin.read(buf, 0, avail);
                    if (read != CliConstants.FAILURE) {
                        // write proc data out to user
                        String reportString = new String(buf);
                        String reports[] = reportString.split("\n");
                        for (int i = 0; i < reports.length; i++) {
                            String output = reports[i].trim();
                            if (output.length() >= 1) {
                                logger.log(Level.INFO, "CELL-" + cellId + 
                                        ":Logdump stdout: \"" + output + "\"");
                                // spare user from seeing the WARNING msgs
                                if (!output.startsWith("WARNING")) {
                                    reassureThread.setMessage(
                                            "CELL-" + cellId + ":" + output);
                                }
                            }
                        }
                    }
                }		
                avail = perr.available();
                if (avail > buf.length){
                    avail = buf.length;
                }
                if (avail > 0) {
                    for (int idx = 0; idx < BUFSIZE; idx++) {
                        buf[idx]='\0';
                    }
                    read = perr.read(buf, 0, avail);
                    if (read != CliConstants.FAILURE) {
                        String reportString = new String(buf);
                        String reports[] = reportString.split("\n");
                        for (int i = 0; i < reports.length; i++) {
                            String output = reports[i].trim();
                            if (output.length() >= 1) {
                                logger.log(Level.INFO, "CELL-" + cellId + 
                                        ":Logdump stderr: \"" + output + "\"");
                                reassureThread.setMessage(
                                        "CELL-" + cellId + ":" + output);
                            }
                        }
                    }
                }   
            }
            reassureThread.safeStop();
        } catch (IOException e) {
            logger.log(Level.SEVERE, 
                    "CELL-" + cellId + ":Error running scripts: ", e);
            return BigInteger.valueOf(CliConstants.FAILURE);        
        } finally {
            reassureThread.safeStop();
            sr.cleanUp();
        }
        logger.log(Level.INFO, 
                "CELL-" + cellId + ":Successfully ran explorer script");
        return BigInteger.valueOf(CliConstants.MGMT_OK);
    }
    /**
     * @return HCExpProps the current explorer configuration values
     */
    public HCExpProps getExpProps() throws MgmtException {
        HCExpProps props = new HCExpProps();

        props.setContactName (config.getProperty (
                        ConfigPropertyNames.PROP_LOGDUMP_EXP_CONTACT_NAME));
        try {
            props.setContactPhone (new BigInteger(config.getProperty (
                        ConfigPropertyNames.PROP_LOGDUMP_EXP_CONTACT_PHONE)));
        } catch (NumberFormatException nfe) {
            props.setContactPhone (BigInteger.valueOf(0));
        }
        props.setContactEmail (config.getProperty (
                        ConfigPropertyNames.PROP_LOGDUMP_EXP_CONTACT_EMAIL));
        props.setGeoLocale(config.getProperty(
                        ConfigPropertyNames.PROP_LOGDUMP_EXP_GEO));
        props.setProxyServer (config.getProperty (
                        ConfigPropertyNames.PROP_LOGDUMP_EXP_PROXY_SERVER));
        try {
            props.setProxyPort (
                new BigInteger (config.getProperty (
                        ConfigPropertyNames.PROP_LOGDUMP_EXP_PROXY_PORT)));
        } catch (NumberFormatException nfe) {
            props.setProxyPort (BigInteger.valueOf(8080));
        }
        return props;
    }



    /**
     * Update the explorer configuration values stored in the cluster
     * @param props updated explorer configuration values to store in the 
     * cluster config properties file
     * @throws MgmtException when unable to save configuration settings
     */
    public void setExpProps(HCExpProps props) throws MgmtException {

        Map map = new HashMap();
        map.put(ConfigPropertyNames.PROP_LOGDUMP_EXP_CONTACT_NAME,
          props.getContactName());
        map.put(ConfigPropertyNames.PROP_LOGDUMP_EXP_CONTACT_PHONE,
          props.getContactPhone().toString());
        map.put(ConfigPropertyNames.PROP_LOGDUMP_EXP_CONTACT_EMAIL,
          props.getContactEmail());
        map.put(ConfigPropertyNames.PROP_LOGDUMP_EXP_GEO,
          props.getGeoLocale());
        map.put(ConfigPropertyNames.PROP_LOGDUMP_EXP_PROXY_SERVER,
          props.getProxyServer());
        map.put(ConfigPropertyNames.PROP_LOGDUMP_EXP_PROXY_PORT,
          props.getProxyPort().toString());
        try {
            config.putAll(map);
        } catch (ServerConfigException e) {
            logger.log(Level.SEVERE, "Failed to update explorer configuration" + 
                                        " properties...", e);
	    throw new MgmtException("Failed to save logdump settings.");
        }
    }

    protected class CellCfgAsync extends Thread {
        
        private boolean updateSwitch;
        private boolean updateSp;
        private Map props;

        CellCfgAsync(Map props, boolean updateSwitch, boolean updateSp) {
            this.props = props;
            this.updateSwitch = updateSwitch;
            this.updateSp = updateSp;
        }

        public void run() {
            try {
                MultiCellLib.getInstance().updateProperties(localCellid, props);
                logger.info("Updated cell props successfully");

                if (updateSwitch) {
                    updateSwitchUnconditionally();
                    logger.info("Updated the switch successfully");
                }

                if (updateSp) {
                    updateServiceProcessor(BigInteger.valueOf(0));
                    logger.info("Updated the service processor successfully");
                }
                rebootCell(null, true, true);
                 

            } catch (MgmtException e) {
                logger.log(Level.SEVERE, "failed to update the cell props ", e);
            }
        }
    }
}

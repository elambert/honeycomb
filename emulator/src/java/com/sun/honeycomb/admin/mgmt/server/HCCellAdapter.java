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

import com.sun.honeycomb.cm.NodeMgr;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.config.ExplorerDefaultsWriter;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagCellData;
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagsRegistry;
import com.sun.honeycomb.util.Exec;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;
import java.util.List;
import java.util.logging.Level;

public class HCCellAdapter extends HCCellAdapterBase {
    static private StringBuffer schemaBuffer = null;
    static private long schemaTimestamp = -1;

    private static transient final Logger logger = 
      Logger.getLogger(HCCellAdapter.class.getName());

    private static final String EXPLORER_FILE = 
                                        "/etc/opt/SUNWexplo/default/explorer";
    
    private MgmtServer           mgmtServer;

    public HCCellAdapter() {
        super();
    }

    public void loadHCCell()
        throws InstantiationException {
        loadHCCellBase();
    }


   /*
    * This is the list of accessors to the object
    */


    public void populateLanguages(List<String> array) throws MgmtException {
        array.clear();
        array.addAll(ValuesRepository.getInstance().getCell().getLanguages());
    }




    public Boolean getPossibleDataLoss() throws MgmtException {
        return ValuesRepository.getInstance().getCell().isPossibleDataLoss();
    }

    public BigInteger getNoUnhealeadFailures() throws MgmtException {
        return ValuesRepository.getInstance().getCell().getNoUnhealeadFailures();

    }

    public BigInteger getNoUnhealeadUniqueFailures() throws MgmtException {
        return ValuesRepository.getInstance().getCell().getNoUnhealeadUniqueFailures();
    }



    public Long getEndTimeLastRecoverCycle() throws MgmtException {
        return ValuesRepository.getInstance().getCell().getEndTimeLastRecoverCycle();
    }

    public Long getQueryIntegrityTime() throws MgmtException {
        return new Long(0);
    }

    public BigInteger verifyNtpServers(String ntpServers) throws MgmtException {
        return BigInteger.valueOf(0);
    }

    public Boolean getQuorumReached() throws MgmtException {
        return new Boolean(true);        
    }

    /**
     * @returns the maximum nummber of disks per node in this cell configuration.
     */
    public BigInteger getNumDisksPerNode() 
        throws MgmtException{
        return ValuesRepository.getInstance().getCell().getNumDisksPerNode();
    }


    public Boolean getClusterSane() throws MgmtException {
        return new Boolean(true);        
    }






    public String getHadbStatus(BigInteger dummy) {
        return "dummy";
    }

    /*
     * This is the list of custom actions
     */

    public BigInteger wipe(EventSender evt, BigInteger dummy) throws MgmtException {
        mgmtServer.logger.info("wipe: ");
        return BigInteger.valueOf(0);
    }

    public BigInteger reboot(EventSender evt,
      BigInteger _switches, BigInteger _sp) throws MgmtException {
        mgmtServer.logger.info("reboot: ");
        return BigInteger.valueOf(0);
    }
    public BigInteger powerOff(EventSender evt, 
      BigInteger _useIpmi, BigInteger _sp) throws MgmtException {
        mgmtServer.logger.info("powerOff: ");
        return BigInteger.valueOf(0);
    }

    public BigInteger powerOn() throws MgmtException {
        mgmtServer.logger.info("powerOn: ");
        return BigInteger.valueOf(0);
    }


    public BigInteger powerNodeOn(EventSender evt, BigInteger nodeId) throws MgmtException {
        HCNode node = ValuesRepository.getInstance().getNode(nodeId.intValue());
        node.setIsAlive(true);
        node.setStatus(BigInteger.valueOf(1));
        return BigInteger.valueOf(0);
        
    }



    public BigInteger powerNodeOff(EventSender evt,BigInteger nodeId,BigInteger _useIpmi) 
    throws MgmtException {

        mgmtServer.logger.info("powernodeOff: ");
        HCNode node = ValuesRepository.getInstance().getNode(nodeId.intValue());
        node.setIsAlive(false);
        node.setStatus(BigInteger.ZERO);
        
        // TODO: Verify node is not master/vice master and if it is switch 
        // pass role to another node
        return BigInteger.valueOf(0);
    }





    public BigInteger expansionStatus(BigInteger dummy) throws MgmtException {
        return BigInteger.valueOf(0);
    }



    public BigInteger startExpansion(BigInteger dummy) throws MgmtException {

        mgmtServer.logger.info("startExpansion: ");
        return BigInteger.valueOf(0);
    }

    public BigInteger stopExpansion(BigInteger dummy) throws MgmtException {
        mgmtServer.logger.info("stopExpansion: ");
        return BigInteger.valueOf(0);
    }
    public BigInteger updateSchema(EventSender evt,
      String schema, Long stamp, Byte lastMessage, 
      Byte updateSchema) throws MgmtException {

        boolean isFirstSchemaPiece = 
          ((lastMessage.byteValue() & CliConstants.MDCONFIG_FIRST_MESSAGE) == 
            CliConstants.MDCONFIG_FIRST_MESSAGE);
        boolean isLastSchemaPiece = 
          ((lastMessage.byteValue() & CliConstants.MDCONFIG_LAST_MESSAGE) == 
            CliConstants.MDCONFIG_LAST_MESSAGE);
        
        if ((schemaBuffer != null) && 
          (stamp.longValue() != schemaTimestamp)) {
            System.err.println("Unexpected schema");
            return BigInteger.valueOf(-1);
        } else {
            if (isFirstSchemaPiece) {
                System.err.println("New schema");
                schemaBuffer = new StringBuffer();
                schemaTimestamp = stamp.longValue();
            }
        }

        System.err.println("Received schema stamp = " +
          stamp.longValue() + 
          ", update = :" +
          ((updateSchema.byteValue() == 0) ? " false" : " true"));

        schemaBuffer.append(schema);
        if (!isLastSchemaPiece) {
            System.err.println("Received new piece of schema message");
            return BigInteger.valueOf(0);
        } else {
            System.err.println("Received last piece of schema");
        }

        String newSchema = schemaBuffer.toString();
        schemaBuffer = null;
        schemaTimestamp = -1;

        System.err.println("newSchema :\n");
        System.err.println(newSchema);
        System.err.println("");

        if (updateSchema.byteValue() == 0) {
            System.err.println("validation only, returns now");
            return BigInteger.valueOf(0);
        }

        String reply = null;
        try {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
            }
            reply = evt.sendSynchronousEvent("validation successful");
        } catch (MgmtException e) {
            e.printStackTrace();
        }


        try {
            String schemaPath = 
                    NodeMgr.getEmulatorRoot() + "/config/metadata_config.xml";
            FileOutputStream out = new FileOutputStream(schemaPath);
            out.write(newSchema.getBytes("UTF-8"));
            out.flush();
            out.close();
              
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
            }
            reply = evt.sendSynchronousEvent("CMM config update successful");
        } catch (Exception e) {
            e.printStackTrace();
        }


        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
        }
        return BigInteger.valueOf(0);
    }

    public BigInteger clearSchema(BigInteger dummy) throws MgmtException {
        System.err.println("clearSchema");
        return BigInteger.valueOf(0);
    }

    public BigInteger retrySchema(BigInteger dummy) throws MgmtException {
        System.err.println("retrySchema");
        return BigInteger.valueOf(0);
    }

    public BigInteger retrieveSchema(EventSender evt,
      Byte templateSchema) throws MgmtException {
        System.err.println("Retrieve schema, template = :" +
          ((templateSchema.byteValue() == 0) ? "false" : "true"));

        String schemaPath = NodeMgr.getEmulatorRoot() + "/config/";
        if (templateSchema.byteValue() == 0) {
            schemaPath += "metadata_config.xml";
        } else {
            schemaPath += "metadata_config_factory.xml";
        }

        StringBuffer buffer = new StringBuffer();
        try {
            FileInputStream fin = new FileInputStream(schemaPath);
            BufferedInputStream in = new BufferedInputStream(fin);
            int res = -1;
            do {
                res = in.read();
                if (res == -1) {
                    break;
                }
                buffer.append((char) res);
            } while (res != -1);
        } catch (Exception e) {
            System.err.println("can't open file " + schemaPath + " " + e);
        }

        int nbEvents = buffer.length() / 
          CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE;
        nbEvents = 
          (buffer.length() % CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE == 0) ?
          nbEvents : (nbEvents + 1);
  
        System.err.println("Will send " + nbEvents + " events");

        int start = 0;
        int end = CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE;
        for (int i = 0; i < nbEvents; i++) {
            String schemaPiece = null;
            if (i < (nbEvents - 1)) {
                schemaPiece = buffer.substring(start, end);
              } else {
                  schemaPiece = buffer.substring(start);
            }

            System.err.println(schemaPiece);

            try {
                evt.sendSynchronousEvent(schemaPiece);
            } catch (Exception ignore) {
                mgmtServer.logger.severe("failed to send the async " + 
                  "event ");
                return
                  BigInteger.valueOf(CliConstants.MGMT_CANT_RETRIEVE_SCHEMA);
            }
            start = end;
            end += CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE;
        }
        return BigInteger.valueOf(CliConstants.MGMT_OK);
    }

    public String getDate() {
        return (new Date()).toString();
    }

    public BigInteger getSwitchesState(BigInteger dummy) throws MgmtException {
        return BigInteger.valueOf(0);
    }

    public   void updateSwitch(Map changedProps) throws MgmtException {
    }

    public BigInteger updateServiceProcessor(BigInteger dummy)
        throws MgmtException {
        return  BigInteger.valueOf(0);
    }

    protected void updatePropertiesAndRebootCell(Map props,
                                                 boolean updateSwitch, 
                                                 boolean updateSP) {
        

        HCCellAdapterBase.CellCfgAsync executor = 
          new HCCellAdapterBase.CellCfgAsync(props, updateSwitch, updateSP);
        executor.start();
    }

    protected  void rebootCell(EventSender evt,
                               boolean switches, 
                               boolean sp) throws MgmtException {
    }


    public void updateSwitchUnconditionally() throws MgmtException{
	
        //
        // If any properties get added here, update the static cellProps strucre at the top 
        // of this file
        //
        ClusterProperties props = ClusterProperties.getInstance();
	MultiCellLib cellLib = MultiCellLib.getInstance();
        String adminIp = cellLib.getAdminVIP();
        String dataIp = cellLib.getDataVIP();
        String spIp = cellLib.getSPVIP();
        String subnet = cellLib.getSubnet();
        String gateway = cellLib.getGateway();

        String ntp = props.getProperty(ConfigPropertyNames.PROP_NTP_SERVER);
        String smtp = props.getProperty(ConfigPropertyNames.PROP_SMTP_SERVER);
        String smtp_port = props.getProperty(ConfigPropertyNames.PROP_SMTP_PORT);
        String extlogger = props.getProperty(ConfigPropertyNames.PROP_EXT_LOGGER);
        String authClients = props.getProperty(ConfigPropertyNames.PROP_AUTH_CLI);

        String dns = props.getProperty(ConfigPropertyNames.PROP_DNS);
        String domain_name = props.getProperty(ConfigPropertyNames.PROP_DOMAIN_NAME);
        String dns_search = props.getProperty(ConfigPropertyNames.PROP_DNS_SEARCH);
        String primary_dns_server = props.getProperty(ConfigPropertyNames.PROP_PRIMARY_DNS_SERVER);
        String secondary_dns_server = props.getProperty(ConfigPropertyNames.PROP_SECONDARY_DNS_SERVER);

        System.out.println("updating switch: with " +
                    "dataIp:"+ dataIp+
                    " adminIp:"+adminIp+
                    " spIp:"+spIp+
                    " smtp:"+smtp+
                    " smtp_port:"+smtp_port+
                    " ntp:"+ntp+
                    " subnet:"+subnet+
                    " gateway:"+gateway+
                    " extlogger:"+extlogger+
                    " authClients:"+authClients+
                    " dns:"+dns+
                    " domain_name:"+domain_name+
                    " dns_search:"+dns_search+
                    " primary_dns_server:"+primary_dns_server+
                    " secondary_dns_server:"+secondary_dns_server);
    }
    
    /**
     * Clear the service tag registry of any existing service tag entries
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    protected void clearRegistry()
    throws MgmtException {
        // For the emulator the service tag registry may contains some
        // entries for things besides what we are interested in
        // Therefore clear the registry via the instance URNS
        //
        // The one problem that exists is that there's no way for us
        // to clean up old instances of the service tag registry
        // In order to do that a Registry.getServiceTags() method
        // needs to be added.   Then we could walk through the list
        // and look for service tags for the 5800 System and delete
        // them.   That's not possible with the current API set.
        // CR6661826 filed against the Java ServiceTag api to add this
        // functionality.
        ServiceTagCellData[] entries = 
                MultiCellLib.getInstance().getServiceTagDataForAllCells();
        String[] instanceURNs = ServiceTagsRegistry.getInstanceURNs(entries);
        ServiceTagsRegistry.clear(instanceURNs);
    }

    /**
     * <B>IMPORTANT:</B> The SUNWexplo and SUNWexplu packages need to be 
     * installed on the machine the emulator is running on or else the following
     * code will fail.  Also, check to see if the http_proxy environmental
     * variable is set for the shell (echo $http_proxy).  If it is set, make 
     * sure its a valid value.  The multicell startup and cli startup scripts 
     * must be run as root since explorer must be run as root too.<BR>
     * <P>
     * Creates the explorer defaults file which is required to run the explorer
     * tool.  If successful, the explorer script is executed to collect log
     * information and send it back to Sun Service via HTTPS.
     * @param evt required for interactive commands
     * @param expProps master cell explorer config params
     * @return BigInteger exit value for logdump -- success/failure
     * @throws MgmtException when unable to save configuration settings
     */
    public BigInteger scrapeLogs(EventSender evt, HCExpProps expProps) 
                                                        throws MgmtException {
        String strCellId =  getCellId().toString(localCellid);
        int cellId = getCellId().intValue();
        String eRootFilePath = NodeMgr.getEmulatorRoot();
        
        // create explorer defaults file based on the configuration info set
        // in the cluster properties file on the master cell
        if (!ExplorerDefaultsWriter.createDefaultsTestOnly(
                                    "/etc/opt/SUNWexplo/default", expProps)) {
            logger.log(Level.SEVERE, "Unable to create explorer defaults" +
                    " file for CELL-" + strCellId);
            return BigInteger.valueOf(CliConstants.FAILURE);  
        }
        logger.log(Level.INFO, "Successfully created explorer defaults file");
        // looks for explorer defaults file under /etc/opt/SUNWexplo/default
        // when the -d argument isn't specified
        String cmd = "/opt/SUNWexplo/bin/explorer -P -D -t /var/adm";
        logger.log(Level.INFO, "CELL-" + strCellId + 
                                ":Invoking explorer script with " + cmd);
        try {  
            return execExplorer(evt, cmd, strCellId);
        } catch (Exception e) {
            logger.log(Level.SEVERE, 
                    "CELL-" + strCellId + ":Error running scripts: ", e);
            return BigInteger.valueOf(CliConstants.FAILURE);        
        } finally {
            // remove explorer defaults file -- no longer needed
            try {
                Exec.exec ("/usr/bin/rm " + EXPLORER_FILE);
            } catch (IOException ioe) {
                logger.log (Level.WARNING,
                            "Unable to remove: " + EXPLORER_FILE);
            }
        }
    }

}

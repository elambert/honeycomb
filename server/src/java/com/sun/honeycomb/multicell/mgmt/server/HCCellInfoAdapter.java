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



package com.sun.honeycomb.multicell.mgmt.server;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;

import com.sun.honeycomb.multicell.MultiCellIntf;
import com.sun.honeycomb.multicell.MultiCellLogger;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.multicell.CellInfo;
import com.sun.honeycomb.multicell.lib.Rule;
import com.sun.honeycomb.multicell.lib.Rule.Interval;
import com.sun.honeycomb.multicell.schemas.SchemaCompare;
import com.sun.honeycomb.emd.config.EMDConfigException;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagData;

public class HCCellInfoAdapter implements HCCellInfoAdapterInterface 
{

	static private final String PROP_NTP_SERVER = 
	  "honeycomb.cell.ntp";
	static private final String PROP_SMTP_SERVER = 
	  "honeycomb.cell.smtp.server";
	static private final String PROP_SMTP_PORT = 
	  "honeycomb.cell.smtp.port";
	static private final String PROP_AUTH_CLIENTS = 
	  "honeycomb.security.authorized_clients";
	static private final String PROP_EXT_LOGGER = 
	  "honeycomb.cell.external_logger";
	static private final String PROP_DNS_CONFIGURED = 
	  "honeycomb.cell.dns";
    static private final String PROP_DOMAIN_NAME =
      "honeycomb.cell.domain_name";
	static private final String PROP_DNS_SEARCH = 
	  "honeycomb.cell.dns_search";
	static private final String PROP_DNS_PRIMARY_SERVER = 
	  "honeycomb.cell.primary_dns_server";
	static private final String PROP_DNS_SECONDARY_SERVER = 
	  "honeycomb.cell.secondary_dns_server";

    static private final String   PROP_PRINT_SCHEMAS     =
      "honeycomb.multicell.print.schemas";

    static private final int NB_PROPERTIES = 10;

    static final private String PROP_ACCEPT_8_NODES =
      "honeycomb.multicell.support_8_nodes";
    static final private String PROP_NUM_NODES   =
      "honeycomb.cell.num_nodes";

    private static transient final Logger logger = 
        Logger.getLogger(HCCellInfoAdapter.class.getName());

    static private StringBuffer schemaBuffer = null;

    private MultiCellIntf multicellAPI;
    private CellInfo cellInfo;
    private ClusterProperties config;
    private boolean debug;

    public void loadHCCellInfo()
        throws InstantiationException {

        InstantiationException instExc;
        multicellAPI = MultiCellIntf.Proxy.getMultiCellAPI();
        if (multicellAPI == null) {
            logger.severe("failed to get Multicell API");
            instExc = 
              new InstantiationException("Internal Error on remote cell ");
            throw instExc;
        } 

        config = ClusterProperties.getInstance();
        boolean acceptMC8Nodes = 
          config.getPropertyAsBoolean(PROP_ACCEPT_8_NODES);
        int numNodes = config.getPropertyAsInt(PROP_NUM_NODES, 16);
        if (numNodes < 16) {
              if (acceptMC8Nodes) {
                  logger.info("MC: cluster is not 16 nodes but is " +
                    "configured to accept mc configuration");
              } else {
                  instExc = new InstantiationException("Remote cell is not " +
                    "a 16 nodes cluster");
                  throw instExc;
              }
        }
        boolean debug = config.getPropertyAsBoolean(PROP_PRINT_SCHEMAS);

        try {
            cellInfo = multicellAPI.getCellInfo();
        } catch (Exception exc) {
            logger.log(Level.SEVERE, "failed to get object CellInfo", exc);
            instExc =
              new InstantiationException("Internal error on remote cell");
            instExc.initCause(exc);
            throw instExc;
        }
    }

    /*
     * This is the list of accessors to the object
     */
    public Byte getCellid() throws MgmtException {
        return new Byte(cellInfo.getCellid());
    }

    public String getDomainName() throws MgmtException {
        return cellInfo.getDomainName();
    }

    public String getAdminVIP() throws MgmtException {
        return cellInfo.getAdminVIP();
    }

    public String getDataVIP() throws MgmtException {
        return cellInfo.getDataVIP();
    }

    public String getSpVIP() throws MgmtException {
        return cellInfo.getSPVIP();
    }

    public String getSubnet() throws MgmtException {
        return cellInfo.getSubnet();
    }

    public String getGateway() throws MgmtException {
        return cellInfo.getGateway();
    }

    public Long getTotalCapacity() throws MgmtException {
        return new Long(cellInfo.getTotalCapacity());
    }

    public Long getUsedCapacity() throws MgmtException {
        return new Long(cellInfo.getUsedCapacity());
    }
    
    /**
     * Get the service tag data associated with this cell
     * @return HCServiceTagData the data associated with this cell
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     */
    public HCServiceTagInfo getServiceTagInfo() throws MgmtException {
        ServiceTagData data = cellInfo.getServiceTagData();
        assert(data != null);
        return getHCServiceTagInfoFrom(data);
    }

    public void populateRules(List<HCRule> array) throws MgmtException {
        List rules = cellInfo.getRules();
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = (Rule) rules.get(i);
            HCRule hcRule = new HCRule();
            hcRule.setRuleNumber(rule.getRuleNumber());
            hcRule.setOriginCellId(rule.getOriginCellid());
            hcRule.setStart(rule.getInterval().getStart());
            hcRule.setEnd(rule.getInterval().getEnd());
            hcRule.setDistance(rule.getInterval().getDistance());
            hcRule.setInitialCapacity(rule.getInterval().getInitialCapacity());
            array.add(hcRule);
        }
    }    


    public Byte checkSchema(String schemaPiece, Byte mask)
        throws MgmtException {

        boolean isFirstSchemaPiece = 
          ((mask.byteValue() & CliConstants.MDCONFIG_FIRST_MESSAGE) == 
            CliConstants.MDCONFIG_FIRST_MESSAGE);
        boolean isLastSchemaPiece = 
          ((mask.byteValue() & CliConstants.MDCONFIG_LAST_MESSAGE) == 
            CliConstants.MDCONFIG_LAST_MESSAGE);

        if (isFirstSchemaPiece) {
            logger.info( MultiCellLogger.MULTICELL_LOG_PREFIX +
              "Received first piece schema");
            schemaBuffer = new StringBuffer();
        }

        schemaBuffer.append(schemaPiece);
        if (!isLastSchemaPiece) {
           logger.info( MultiCellLogger.MULTICELL_LOG_PREFIX +
             "Received new piece of schema message");
           return new Byte((byte) 0);
        } else {
            logger.info( MultiCellLogger.MULTICELL_LOG_PREFIX +
              "Received last piece of schema");
        }

        String newSchema = schemaBuffer.toString();
        schemaBuffer = null;
        ByteArrayInputStream stream = null;
        try {
            stream = new ByteArrayInputStream(newSchema.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException ue) {
            logger.log(Level.SEVERE,
              MultiCellLogger.MULTICELL_LOG_PREFIX +
              "Unsupported encoding type : UTF-8", ue);
            throw new MgmtException("Internal error, can't validate schema");
        }

        RootNamespace namespace = null;
        try {
            namespace = new RootNamespace();
            namespace.readConfig(stream, false);
        } catch (EMDConfigException e) {
            logger.log(Level.SEVERE,
              MultiCellLogger.MULTICELL_LOG_PREFIX +
              "Can't create new Instance of schema from using XML from master",
                e);
            throw new MgmtException("Internal error while parsing the "  +
              "schema on the remote cell");
        }

        try {
            SchemaCompare schemaCompare = new SchemaCompare(namespace, debug);
            schemaCompare.checkSchema();
        } catch (EMDConfigException emde) {
            // We expect that one if the schema are indeed different.
            logger.info( MultiCellLogger.MULTICELL_LOG_PREFIX +
              " " + emde.getMessage());
            throw new MgmtException("Schema validation failed: " +
              emde.getMessage());
        }
        return new Byte((byte) 0);        
    }


    public Byte checkConfig(HCSiloProps siloProps) throws MgmtException {
	
        int totalPropertyValidated = 0;

         compareProperty(config,
          PROP_NTP_SERVER, siloProps.ntpServer);
         compareProperty(config,
          PROP_SMTP_SERVER, siloProps.smtpServer);
         compareProperty(config,
          PROP_SMTP_PORT, siloProps.smtpPort);
         compareProperty(config,
          PROP_AUTH_CLIENTS, siloProps.authorizedClients);
         compareProperty(config,
          PROP_EXT_LOGGER, siloProps.extLogger);
         compareProperty(config,
          PROP_DNS_CONFIGURED, siloProps.dns);
        
        boolean dnsConfigured = 
          config.getPropertyAsBoolean(PROP_DNS_CONFIGURED);
        if (dnsConfigured) {
             compareProperty(config,
              PROP_DNS_SEARCH, siloProps.dnsSearch);
             compareProperty(config,
              PROP_DOMAIN_NAME, siloProps.domainName);
             compareProperty(config,
              PROP_DNS_PRIMARY_SERVER, 
              siloProps.primaryDnsServer);
             compareProperty(config,
              PROP_DNS_SECONDARY_SERVER, 
              siloProps.secondaryDnsServer);
        }
        return new Byte((byte) 0);        
    }
    
    private void compareProperty(ClusterProperties config,
      String name, String master) throws MgmtException {


        String cur = config.getProperty(name);

        logger.info(MultiCellLogger.MULTICELL_LOG_PREFIX +
          "check property " + name + ", local value = " + cur +
          "hive value = " + master);

        boolean res = compareStringArrays(parseValue(cur), parseValue(master));
        if (!res) {
            logger.info(MultiCellLogger.MULTICELL_LOG_PREFIX +
              "failed to compare property; " + name);
            throw new MgmtException("Failed to validate hive properties : " +
                "property " + name + " is set to " + master + " on the hive " +
              ", set to " + cur + " on the remote cell");
        }
    }

    private String [] parseValue(String value) {
        if (value == null || value.equals("")) {
            return null;
        }
        String [] res  = value.split("[ \t,]+");
        return res;
    }

    private boolean compareStringArrays(String [] cur, String [] master) {

        if (cur == null && master == null) {
            return true;
        }

        if (((cur == null) && (master != null)) ||
          ((cur != null) && (master == null)) ||
          (cur.length != master.length)) {
            return false;
        }
	
        for (int i = 0; i < cur.length; i++) {
            String curVal = cur[i];
            boolean found = false;
            for (int j = 0; j < master.length; j++) {
                String curMaster = master[j];
                if (curVal.equals(curMaster)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                logger.info(MultiCellLogger.MULTICELL_LOG_PREFIX +
                  "missing value " + curVal);
                return false;
            }
        }
        return true;
    }

    public Byte addCell(HCCellInfo newCell, Long version) throws MgmtException {

        byte res = -1;
        try {
            res = multicellAPI.addNewCell(populateCellInfo(newCell), 
              version.longValue());
        } catch (Exception exc /* IOException, MultiCellException */) {
            logger.log(Level.SEVERE,
              MultiCellLogger.MULTICELL_LOG_PREFIX + 
              "failed to add new cell ", exc);
            throw new MgmtException("Internal error while propagating " +
              " the new cell config to the remote cell " + 
              cellInfo.getCellid());
        }
        return new Byte((byte) res);
    }


    public Byte delCell(Byte cellid, Long version) throws MgmtException {

        logger.info(MultiCellLogger.MULTICELL_LOG_PREFIX +
          " delCell cellid = " + cellid + 
          ", version = " + version.longValue());

        byte res = -1;
        try {
            res = multicellAPI.rmExistingCell(cellid.byteValue(),
              version.longValue());
        } catch (Exception exc /* IOException, MultiCellException */) {
            logger.log(Level.SEVERE,
              MultiCellLogger.MULTICELL_LOG_PREFIX + 
              "failed to delete existing cell ", exc);
            throw new MgmtException("Internal error while removing " +
              " the cell " + cellid +
              " from the config on to the remote cell " + 
              cellInfo.getCellid());
        }
        return new Byte((byte) res);
    }


    public Byte pushInitConfig(HCSiloInfo init, Long majorVersion)
        throws MgmtException {

        byte res = -1;
        List<HCCellInfo> hcCells = init.getCells();
        List cells = new ArrayList();
        for (int i = 0; i < hcCells.size(); i++) 
        {
            cells.add(populateCellInfo(hcCells.get(i)));
        }
        try {
            res = multicellAPI.pushInitConfig(cells, majorVersion.longValue());
        } catch (Exception exc /* IOException, MultiCellException */) {
            logger.log(Level.SEVERE,
              MultiCellLogger.MULTICELL_LOG_PREFIX + 
              "failed to push multicell init config ", exc);
            throw new MgmtException("Internal error while propagating the " +
                " state of the hive");
        }
        return new Byte((byte) res);
    }

   
    private CellInfo populateCellInfo(HCCellInfo hcCellInfo) {

        List rules = new ArrayList();
        List<HCRule> hcRules = hcCellInfo.getRules();

        for (int i = 0; i < hcRules.size(); i++) {
            HCRule hcRule = hcRules.get(i);
            Interval interval = new Interval(hcRule.getStart(),
              hcRule.getEnd(),
              hcRule.getInitialCapacity());
            Rule curRule = new Rule(hcRule.getOriginCellId(),
              hcRule.getRuleNumber(),
              interval);
            rules.add(curRule);
        }
        
        HCServiceTagInfo tagInfo = hcCellInfo.getServiceTagInfo();
        ServiceTagData tagData = getServiceTagDataFrom(tagInfo);
        CellInfo cell = new CellInfo(hcCellInfo.getCellid(),
          hcCellInfo.getAdminVIP(),
          hcCellInfo.getDataVIP(),
          hcCellInfo.getSpVIP(),
          hcCellInfo.getDomainName(),
          hcCellInfo.getSubnet(),
          hcCellInfo.getGateway(),
          rules,
          tagData);
        cell.setUsedCapacity(hcCellInfo.getUsedCapacity());
        cell.setTotalCapacity(hcCellInfo.getTotalCapacity());
        return cell;
    }

    
    /**
     * Server Conversion routine
     * @param tagInfo the representation that's sent via XMLRPC
     * @return ServiceTagData Internal representation of the service tag data
     */
    private ServiceTagData getServiceTagDataFrom(HCServiceTagInfo tagInfo) {
        ServiceTagData tagData = new ServiceTagData();
        tagData.setInstanceURN(tagInfo.getInstanceURN());
        tagData.setMarketingNumber(tagInfo.getMarketingNumber());
        tagData.setProductNumber(tagInfo.getProductNumber());
        tagData.setProductSerialNumber(tagInfo.getProductSerialNumber());
        return tagData;
    }
    
    /**
     * Server Conversion routine.
     * @param tagData Internal representation of the service tag data
     * @return HCServiceTagInfo the representation that's sent via XMLRPC
     */
    private HCServiceTagInfo getHCServiceTagInfoFrom(ServiceTagData tagData) {
        HCServiceTagInfo tagInfo = new HCServiceTagInfo();
        tagInfo.setInstanceURN(tagData.getInstanceURN());
        tagInfo.setMarketingNumber(tagData.getMarketingNumber());
        tagInfo.setProductNumber(tagData.getProductNumber());
        tagInfo.setProductSerialNumber(tagData.getProductSerialNumber());
        return tagInfo;
    }
    
    public static String toString(HCCellInfo cell) {
        StringBuffer buf = new StringBuffer();
        buf.append("Cell " + cell.getCellid());
        buf.append(toString(cell.getServiceTagInfo()));
        return buf.toString();
    }
    
    public static String toString(CellInfo cell) {
        StringBuffer buf = new StringBuffer();
        buf.append("Cell ").append(cell.getCellid()).append(" ");
        buf.append(toString(cell.getServiceTagData()));
        return buf.toString();
    }
    
    public static String toString(HCServiceTagInfo info) {
        StringBuffer buf = new StringBuffer();
        buf.append("ServiceTagInfo [ Marketing Number=");
        buf.append(info.getMarketingNumber());
        buf.append(", Product Number=");
        buf.append(info.getProductNumber());
        buf.append(", Product Serial # =");
        buf.append(info.getProductSerialNumber());
        buf.append("]");
        return buf.toString();
    }
    
    public static String toString(ServiceTagData info) {
        StringBuffer buf = new StringBuffer();
        buf.append("ServiceTagInfo [ Marketing Number=");
        buf.append(info.getMarketingNumber());
        buf.append(", Product Number=");
        buf.append(info.getProductNumber());
        buf.append(", Product Serial # =");
        buf.append(info.getProductSerialNumber());
        buf.append("]");
        return buf.toString();
    }
}

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


package com.sun.honeycomb.multicell.mgmt.client;

import java.util.List;
import java.util.ArrayList;

import com.sun.honeycomb.multicell.mgmt.client.Fetcher;
import com.sun.honeycomb.multicell.mgmt.client.HCCellInfo;
import com.sun.honeycomb.multicell.mgmt.client.HCRule;
import com.sun.honeycomb.multicell.mgmt.client.HCSiloProps;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.config.ClusterProperties;

import com.sun.honeycomb.multicell.lib.Rule;
import com.sun.honeycomb.multicell.MultiCellException;
import com.sun.honeycomb.multicell.MultiCellLogger;
import com.sun.honeycomb.multicell.CellInfo;
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagData;


/**
 * This class is used by the MutliCell routines for fetching the HCCellInfo
 * object from a remote cell.   The HCCellInfo object allows retrieval of
 * properties from the remote cell and actions to be performed upon the remote
 * cell.
 */
public class GetHCCellInfo {

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

    private String            destination = null;
    private HCCellInfo        cell = null;
    private MultiCellLogger   logger = null;
    private ClusterProperties config = null;


    /**
     * @param logger the logger that log messages will be outputed to
     * @param destination the url of the remote cell to connect to
     * @throws com.sun.honeycomb.multicell.MultiCellException
     */
    public GetHCCellInfo(MultiCellLogger logger, String destination) 
        throws MultiCellException {

        this.destination = destination;
        this.logger = logger;
        this.config = ClusterProperties.getInstance();

        try {
            cell = Fetcher.fetchHCCellInfo(destination);
        } catch (MgmtException mgmtExc) {
            logger.logDefault("failed to fetch remote cell :"  +
              mgmtExc.getMessage());
            throw new MultiCellException(mgmtExc.getMessage());
        }
    }

    public CellInfo getCellInfo() {

        List rules = getRulesFromHCRules(cell.getRules());
        ServiceTagData data = getServiceTagDataFrom(cell.getServiceTagInfo());
        CellInfo cellInfo = new CellInfo(cell.getCellid(),
                                         cell.getAdminVIP(),
                                         cell.getDataVIP(),
                                         cell.getSpVIP(),
                                         cell.getDomainName(),
                                         cell.getSubnet(),
                                         cell.getGateway(),
                                         rules,
                                         data);
        cellInfo.setUsedCapacity(cell.getUsedCapacity());
        cellInfo.setTotalCapacity(cell.getTotalCapacity());
        return cellInfo;
    }
    
    public byte checkSiloProps() 
        throws MultiCellException {

        HCSiloProps siloProps = new HCSiloProps();
        siloProps.setNtpServer(config.getProperty(PROP_NTP_SERVER));
        siloProps.setSmtpServer(config.getProperty(PROP_SMTP_SERVER));
        siloProps.setSmtpPort(config.getProperty(PROP_SMTP_PORT));
        siloProps.setDns(config.getProperty(PROP_DNS_CONFIGURED));
        if (config.getPropertyAsBoolean(PROP_DNS_CONFIGURED)) {
            logger.logVerbose("checkSiloProps : DNS  configured ");
            siloProps.setDomainName(config.getProperty(PROP_DOMAIN_NAME));
            siloProps.setDnsSearch(config.getProperty(PROP_DNS_SEARCH));
            siloProps.setPrimaryDnsServer(
                config.getProperty(PROP_DNS_PRIMARY_SERVER));
            siloProps.setSecondaryDnsServer(
                config.getProperty(PROP_DNS_SECONDARY_SERVER));
        } else {
            logger.logVerbose("checkSiloProps : DNS not configured ");
            siloProps.setDomainName("not-configured");
            siloProps.setDnsSearch("not-configured");
            siloProps.setPrimaryDnsServer("not-configured");
            siloProps.setSecondaryDnsServer("not-configured");
        }
        siloProps.setAuthorizedClients(config.getProperty(PROP_AUTH_CLIENTS));
        siloProps.setExtLogger(config.getProperty(PROP_EXT_LOGGER));

        try {
            cell.checkConfig(siloProps);
        } catch (MgmtException mgmtExc) {
            logger.logDefault(mgmtExc.getMessage());
            throw new MultiCellException(mgmtExc.getMessage());
        }
        return 0;
    }

    public byte checkSchema(String schemaPiece, byte mask)
        throws MultiCellException {

        try {
            cell.checkSchema(schemaPiece, new Byte(mask));
        } catch (MgmtException mgmtExc) {
            logger.logDefault(mgmtExc.getMessage());
            throw new MultiCellException(mgmtExc.getMessage());
        }
        return 0;
    }

    public byte pushInitConfig(List cells, long majorVersion)
        throws MultiCellException {

        logger.logVerbose("push init config");

        HCSiloInfo init = new HCSiloInfo();
        List<HCCellInfo> cellInfo = init.getCells();

        Byte res = null;

        for (int i = 0; i < cells.size(); i++) {

            CellInfo curCell = (CellInfo) cells.get(i);
            HCCellInfo curHCCellInfo = getHCCellInfo(curCell);
            logger.logVerbose("push cell (cell) " +  curCell.getCellid() +
                              "(hcCell)" + curHCCellInfo.getCellid());
            cellInfo.add(curHCCellInfo);

        }
        try {
            res  = cell.pushInitConfig(init, new Long(majorVersion));
        } catch (MgmtException mgmtExc) {
            throw new MultiCellException(mgmtExc.getMessage());
        }
        return res.byteValue();        
    }

   public byte addCell(CellInfo newCell, long version)
        throws MultiCellException {

       Byte res = null;
       try {
           res  = cell.addCell(getHCCellInfo(newCell), new Long(version));
       } catch (MgmtException mgmtExc) {
           throw new MultiCellException(mgmtExc.getMessage());
       }
       return res.byteValue();        
   }

   public byte delCell(byte cellid, long version)
        throws MultiCellException {

       Byte res = null;
       try {
           res  = cell.delCell(new Byte(cellid), new Long(version));
       } catch (MgmtException mgmtExc) {
           throw new MultiCellException(mgmtExc.getMessage());
                                        
       }
       return res.byteValue();        
   }

    private HCCellInfo getHCCellInfo(CellInfo cellInfo) {

        HCCellInfo hcCellInfo = new HCCellInfo();

        hcCellInfo.setCellid(cellInfo.getCellid());
        hcCellInfo.setDomainName(cellInfo.getDomainName());
        hcCellInfo.setAdminVIP(cellInfo.getAdminVIP());
        hcCellInfo.setDataVIP(cellInfo.getDataVIP());
        hcCellInfo.setSpVIP(cellInfo.getSPVIP());
        hcCellInfo.setSubnet(cellInfo.getSubnet());
        hcCellInfo.setGateway(cellInfo.getGateway());
        hcCellInfo.setTotalCapacity(cellInfo.getTotalCapacity());
        hcCellInfo.setUsedCapacity(cellInfo.getUsedCapacity());


        List<HCRule> hcRules = hcCellInfo.getRules();
        List rules = cellInfo.getRules();
        for (int j = 0; j < rules.size(); j++) {
            Rule rule = (Rule) rules.get(j);
            HCRule hcRule = new HCRule();
            hcRule.setRuleNumber(rule.getRuleNumber());
            hcRule.setOriginCellId(rule.getOriginCellid());
            hcRule.setStart(rule.getInterval().getStart());
            hcRule.setEnd(rule.getInterval().getEnd());
            hcRule.setDistance(rule.getInterval().getDistance());
            hcRule.setInitialCapacity(rule.getInterval().getInitialCapacity());
            hcRules.add(hcRule);
        }
        ServiceTagData tagData = cellInfo.getServiceTagData();
        HCServiceTagInfo tagInfo = getHCServiceTagInfoFrom(tagData);
        hcCellInfo.setServiceTagInfo(tagInfo);
        return hcCellInfo;
    }

    private List getRulesFromHCRules(List<HCRule> hcRules) {

        List rules = new ArrayList();
        for (int i = 0; i < hcRules.size(); i++) {
            HCRule curHCRule = hcRules.get(i);
            Rule.Interval curInterval = 
                new Rule.Interval(curHCRule.getStart(),
                                  curHCRule.getEnd(),
                                  curHCRule.getInitialCapacity());
            Rule curRule = new Rule(curHCRule.getOriginCellId(),
                                    curHCRule.getRuleNumber(),
                                    curInterval);
            rules.add(curRule);
        }
        return rules;
    }

    /**
     * Client Conversion routine
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
     * Client Conversion routine.
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
}



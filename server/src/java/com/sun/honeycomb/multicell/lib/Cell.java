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


package com.sun.honeycomb.multicell.lib;

import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagData;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.io.PrintStream;

import com.sun.honeycomb.common.ProtocolConstants;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cell properties
 *
 * The product number, product serial number, and marketing number values
 * are used to support service tags.
 */
public class Cell implements java.io.Serializable {

    protected byte    cellid;
    protected String  adminVIP;
    protected String  dataVIP;
    protected String  spVIP;
    protected String  subnet;
    protected String  gateway;
    protected String  domainName;
    protected List    rules;
    protected ServiceTagData serviceTagData;
    
    public Cell(byte cellid,
                String adminVIP,
                String dataVIP) {
        this.cellid = cellid;
        this.adminVIP = adminVIP;
        this.dataVIP = dataVIP;
    }

    public Cell(byte cellid,
                String adminVIP,
                String dataVIP,
                String spVIP,
                String domainName,
                String subnet,
                String gateway) {
        this(cellid, adminVIP, dataVIP);
        this.spVIP = spVIP;
        this.domainName = domainName;
        this.subnet = subnet;
        this.gateway = gateway;
        rules = new ArrayList();
        serviceTagData = new ServiceTagData();
    }

    public String getGateway() {
        return gateway;
    }                                         

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }


    public String getSubnet() {
        return subnet;
    }

    public void setSubnet(String subnet) {
        this.subnet = subnet;
    }

    public byte getCellid() {
        return cellid;
    }

    public void resetCellid(byte cellid) {
        this.cellid = cellid;
        if (rules.size() != 1) {
            throw new MultiCellLibError("invalid configuration file " +
              MultiCellLib.getInstance().getConfigFileName());
        }
        Rule curRule = (Rule) rules.get(0);
        curRule.resetOriginCellid(cellid);
    }

    public String getDataVIP() {
        return dataVIP;
    }

    public void setDataVIP(String dataVIP) {
        this.dataVIP = dataVIP;
    }

    public String getAdminVIP() {
        return adminVIP;
    }

    public void setAdminVIP(String adminVIP) {
        this.adminVIP = adminVIP;
    }

    public String getSPVIP() {
        return spVIP;
    }

    public void setSPVIP(String spVIP) {
        this.spVIP = spVIP;
    }

    public List getRules() {
        return rules;
    }

    public void setRules(List rules) {
        this.rules = rules;
    }

    public String getDomainName() {
        return domainName;
    }
    
    /**
     * Get the service tag descriptor data associated with this cell
     * @return ServiceTagData the service tag information
     */
    public ServiceTagData getServiceTagData() {
        return this.serviceTagData;
    }
    
    /**
     * Set the service tag descriptor data to associate with this cell.
     * @param serviceTagData the service tag information
     */
    public void setServiceTagData(ServiceTagData serviceTagData) {
        this.serviceTagData = serviceTagData;
    }
 
    /**
     * @return String a printable representation of this object
     */
    public String toString() {
        StringBuffer buf = new StringBuffer("id = ").append(cellid);
        buf.append(", dataVIP = ").append(dataVIP);
        buf.append(", adminVIP = ").append(adminVIP);
        buf.append(", spVIP = ").append(spVIP);
        buf.append(", gateway = ").append(gateway);
        buf.append(", subnet = ").append(subnet);
        buf.append(", ");
        buf.append(serviceTagData.toString());
        return buf.toString();
     }

    public boolean equals(Cell cell)
        throws MultiCellLibException {
        
        if (cell.getCellid() != cellid) {
            return false;
        }
	if (!compareStrings(adminVIP, cell.getAdminVIP())) {
            throw new MultiCellLibException("cells have same cellid but " +
                                            "!= adminVIP");
        }
	if (!compareStrings(dataVIP, cell.getDataVIP())) {
            throw new MultiCellLibException("cells have same cellid, adminVIP" +
                                            " but != dataVIP");
        }
	if (!compareStrings(spVIP, cell.getSPVIP())) {
            throw new MultiCellLibException("cells have same cellid, adminVIP," +
                                            " dataVIP but != spVIP");
        }
	if (!compareStrings(subnet, cell.getSubnet())) {
            throw new MultiCellLibException("cells have same cellid, adminVIP," +
                                            " dataVIP, spVIP, name but " +
                                            " != subnet");
        }
	if (!compareStrings(gateway, cell.getGateway())) {	
            throw new MultiCellLibException("cells have same cellid, adminVIP," +
                                            " dataVIP, spVIP, name, subnet " +
                                            "but != gateway ");
        }
        if (rules.size() != cell.getRules().size()) {
            throw new MultiCellLibException("cells don't have the same number " +
                                            "of rules");
        }
        for (int i = 0; i < rules.size(); i++) {
            boolean res =
                ((Rule) rules.get(i)).equals(((Rule) cell.getRules().get(i)));
            if (res == false) {
                throw new MultiCellLibException("cells have differnt rules ");
            }
        }
	if (getServiceTagData().equals(cell.getServiceTagData()) == false) {
            throw new MultiCellLibException("cells have same cellid, adminVIP," +
                " dataVIP, spVIP, name, subnet, gate, rules " +
                "but different service tag data.");
        }
        return true;
    }


    //
    // Returns the orginCellid associated with the rule which match
    // the following ruleNumber and siloLocation. Returns -1 if no
    // match.
    //
    public byte getOriginCellid(byte ruleNumber, short siloLocation) {
        for (int i = 0; i < rules.size(); i++) {
            Rule curRule = (Rule) rules.get(i);
            if (curRule.getRuleNumber() == ruleNumber) {
                if ((curRule.getInterval().getStart() < siloLocation) &&
                    (curRule.getInterval().getEnd() >= siloLocation)) {
                    return curRule.getOriginCellid();
                }
            }
        }
        return -1;
    }

    //
    // Returns the orginCellid associated with the rule which match
    // the following ruleNumber and siloLocation. Returns -1 if no
    // match.
    //
    public byte getRuleNumber(byte originCellid, short siloLocation) {
        for (int i = 0; i < rules.size(); i++) {
            Rule curRule = (Rule) rules.get(i);
            if (curRule.getOriginCellid() == originCellid) {
                if ((curRule.getInterval().getStart() < siloLocation) &&
                    (curRule.getInterval().getEnd() >= siloLocation)) {
                    return curRule.getRuleNumber();
                }
            }
        }
        return -1;
    }

    public void generateXMLServer(PrintStream printer, int indent) {

        HashMap map = new HashMap();
        map.put(ProtocolConstants.ATT_CELLID, String.valueOf((int) cellid));
        map.put(ProtocolConstants.ATT_DOMAIN_NAME, domainName);
        map.put(ProtocolConstants.ATT_ADMIN_VIP, adminVIP);
        map.put(ProtocolConstants.ATT_DATA_VIP, dataVIP);
        map.put(ProtocolConstants.ATT_SP_VIP, spVIP);
        map.put(ProtocolConstants.ATT_GATEWAY, gateway);
        map.put(ProtocolConstants.ATT_SUBNET, subnet);

        XMLWriter.openTag(ProtocolConstants.TAG_CELL,
                          map, indent++, false, printer);            

        for (int i = 0; i < rules.size(); i++) {
            Rule curRule = (Rule) rules.get(i);
            map = new HashMap();
            map.put(ProtocolConstants.ATT_ORIGIN_CELLID,
                    String.valueOf((int) curRule.getOriginCellid()));
            map.put(ProtocolConstants.ATT_RULEID,
                    String.valueOf((int) curRule.getRuleNumber()));
            map.put(ProtocolConstants.ATT_START,
                    String.valueOf((int) curRule.getInterval().getStart()));
            map.put(ProtocolConstants.ATT_END,
                    String.valueOf((int) curRule.getInterval().getEnd()));
            map.put(ProtocolConstants.ATT_INITIAL_CAPACITY,
                    String.valueOf(curRule.getInterval().getInitialCapacity()));
            XMLWriter.openTag(ProtocolConstants.TAG_RULE,
                              map, indent, true, printer);
        }
        generateXMLServiceTagData(printer, indent);
        XMLWriter.closeTag(ProtocolConstants.TAG_CELL, --indent, printer);
        printer.flush();
    }    
    
    /**
     * Output the service tag xml data to silo_info.xml
     * @param printer the output stream
     * @param indent the indent level
     */
    protected void generateXMLServiceTagData(PrintStream printer, int indent) {
        
        // Output service tag information
        Map serviceTagMap = new HashMap();
        
        // Service tag information may be null.  We don't want to store
        // a null as "null" string so remap to empty string
        serviceTagMap.put(ProtocolConstants.ATT_PRODUCT_NUM, 
                serviceTagData.getProductNumber() == null ? "" : 
		serviceTagData.getProductNumber());
        serviceTagMap.put(ProtocolConstants.ATT_PRODUCT_SERIAL_NUM, 
		serviceTagData.getProductSerialNumber() == null ? "" :
		serviceTagData.getProductSerialNumber());
        serviceTagMap.put(ProtocolConstants.ATT_MARKETING_NUM, 
		serviceTagData.getMarketingNumber() == null ? "" :
		serviceTagData.getMarketingNumber());
	serviceTagMap.put(ProtocolConstants.ATT_INSTANCE_URN,
		serviceTagData.getInstanceURN() == null ? "" :
		serviceTagData.getInstanceURN());
        XMLWriter.openTag(ProtocolConstants.TAG_SERVICETAG,
                              serviceTagMap, indent, true, printer);
    }

    private static boolean compareStrings(String a, String b) {
	if (a == null && b == null) {
	    return true;
	}
	if (a == null && b != null) {
	    return false;
	}
	return a.equals(b);
    }
}

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
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.SAXException;
import org.xml.sax.Attributes;

import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.multicell.lib.Rule.Interval;
import java.util.logging.Level;
import java.util.logging.Logger;


public class XMLConfigHandler
    extends DefaultHandler {

    private Cell curCell;

    private XMLParser parser;
    
    public XMLConfigHandler(XMLParser parser) {
        this.parser = parser;
        curCell = null;
    }

    public void startElement(String namespaceURI,
                             String localName,
                             String qName,
                             Attributes atts) 
        throws SAXException {
        
         if (qName.equals(ProtocolConstants.TAG_MC_DESC)) {
             createVersion(atts);
         } else if (qName.equals(ProtocolConstants.TAG_CELL)) {
             createNewCell(atts);
         } else if (qName.equals(ProtocolConstants.TAG_RULE)) {
             createNewRule(atts);
         } else if (qName.equals(ProtocolConstants.TAG_SERVICETAG)) {
             createNewServiceTag(atts);
         }
    }



    private void createVersion(Attributes atts) {
        for (int i = 0; i < atts.getLength(); i++) {
            String aName = atts.getQName(i);
            String aValue = atts.getValue(i);
            if (aName.equals(ProtocolConstants.ATT_VERSION_MAJOR)) {
                parser.setVersion(Long.parseLong(atts.getValue(0)));
            }
        }
    }

    private void createNewCell(Attributes atts) {
        
        byte cellid = 0;
        String domainName = "unknown";
        String adminVIP  = "unknown";
        String dataVIP  = "unknown";
        String spVIP  = "unknown";
        String subnet  = "unknown";
        String gateway  = "unknown";

        if (atts.getLength() != 7) {
            throw new MultiCellLibError("cell requires 7 attributes and only " +
                atts.getLength() + " have been defined");
        }
        
        for (int i = 0; i < atts.getLength(); i++) {
            String aName = atts.getQName(i);
            String aValue = atts.getValue(i);
            if (aName.equals(ProtocolConstants.ATT_CELLID)) {
                cellid = Byte.parseByte(aValue);
            } else if (aName.equals(ProtocolConstants.ATT_DOMAIN_NAME)) {
                domainName = aValue;
            } else if (aName.equals(ProtocolConstants.ATT_DATA_VIP)) {
                dataVIP = aValue;
            } else if (aName.equals(ProtocolConstants.ATT_ADMIN_VIP)) {
                adminVIP = aValue;
            } else if (aName.equals(ProtocolConstants.ATT_SP_VIP)) {
                spVIP = aValue;
            } else if (aName.equals(ProtocolConstants.ATT_SUBNET)) {
                subnet = aValue;
            } else if (aName.equals(ProtocolConstants.ATT_GATEWAY)) {
                gateway = aValue;
            }
        }
        curCell = new Cell(cellid, adminVIP, dataVIP,
                           spVIP, domainName, subnet, gateway);
    }
                    

    private void createNewRule(Attributes atts) {

        byte ruleNumber = 0;
        byte originCellid = 0;
        short start = 0;
        short end = 0;
        long initCapacity = 0;

        if (atts.getLength() != 5) {
            throw new MultiCellLibError("rule requires 5 attributes and only " +
                atts.getLength() + " have been defined");
        }
        for (int i = 0; i < atts.getLength(); i++) {

            String aName = atts.getQName(i);
            String aValue = atts.getValue(i);
            
            if (aName.equals(ProtocolConstants.ATT_ORIGIN_CELLID)) {
                originCellid = Byte.parseByte(aValue);
            } else if (aName.equals(ProtocolConstants.ATT_START)) {
                start = Short.parseShort(aValue);
            } else if (aName.equals(ProtocolConstants.ATT_END)) {
                end = Short.parseShort(aValue);
            } else if (aName.equals(ProtocolConstants.ATT_INITIAL_CAPACITY)) {
                initCapacity = Long.parseLong(aValue);
            } else if (aName.equals(ProtocolConstants.ATT_RULEID)) {
                ruleNumber = Byte.parseByte(aValue);
            }
        }
        Interval interval = new Interval(start, end, initCapacity);
        Rule curRule = new Rule(originCellid, ruleNumber, interval);
        curCell.rules.add(curRule);
    }
    
    /**
     * Parse the servicetag xml tag to retrieve the service tag data.  Add
     * the data to the cell object associated with this object.
     * @param atts the attributes associcated with the servicetag tag in
     * silo_info.xml
     */
    private void createNewServiceTag(Attributes atts) {

        String productNumber = null;
        String productSerialNumber = null;
        String marketingNumber = null;
        String instanceURN = null;

        if (atts.getLength() != 4) {
            throw new MultiCellLibError(
                "<servicetag> tag requires 4 attributes and only " +
                atts.getLength() + " have been defined.");
        }
        for (int i = 0; i < atts.getLength(); i++) {

            String aName = atts.getQName(i);
            String aValue = atts.getValue(i);
            
            // Service tag values if empty should be "" in the xml
            // We want to set these values to null for easier comparisons
            if (aValue.equals("") || aValue.equals("null"))
                aValue = null;
            if (aName.equals(ProtocolConstants.ATT_MARKETING_NUM)) {
                marketingNumber = aValue;
            } else if (aName.equals(ProtocolConstants.ATT_PRODUCT_NUM)) {
                productNumber = aValue;
            } else if (aName.equals(ProtocolConstants.ATT_PRODUCT_SERIAL_NUM)) {
                productSerialNumber = aValue;
            } else if (aName.equals(ProtocolConstants.ATT_INSTANCE_URN)) {
                instanceURN = aValue;
            }
        }
        ServiceTagData data = 
                new ServiceTagData(productNumber, productSerialNumber, 
                    marketingNumber, instanceURN);
        curCell.setServiceTagData(data);
    }

    public void endElement(String namespaceURI,
                           String localName,
                           String qName)
        throws SAXException {

        if (qName.equals(ProtocolConstants.TAG_CELL)) {
            parser.addCell(curCell);
            curCell = null;
        }
    }

}

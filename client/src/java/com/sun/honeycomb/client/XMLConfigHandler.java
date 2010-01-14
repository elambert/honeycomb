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



package com.sun.honeycomb.client;


import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.SAXException;
import org.xml.sax.Attributes;
import java.util.Stack;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.common.ProtocolConstants;
//import com.sun.honeycomb.multicell.lib.Rule.Interval;


public class XMLConfigHandler
    extends DefaultHandler {

    private MultiCell mcell;
    
    private Cell curCell;

    public XMLConfigHandler(MultiCell mcell) {
        this.mcell = mcell;
        curCell = null;
    }

    public void startElement(String namespaceURI,
                             String localName,
                             String qName,
                             Attributes atts) 
        throws SAXException {
        /*
        System.out.println("qName = " + qName);
        for (int i = 0; i < atts.getLength(); i++) {
            System.out.println("- att name = " + atts.getQName(i) +
                               ", type = " + atts.getType(i) +
                               ", value = " + atts.getValue(i));
        }
        */
        if (qName.equals(ProtocolConstants.TAG_MC_DESC)) {
            createVersion(atts);
        } else if (qName.equals(ProtocolConstants.TAG_CELL)) {
            createNewCell(atts);
        } else if (qName.equals(ProtocolConstants.TAG_RULE)) {
            //createNewRule(atts);
        }
    }



    private void createVersion(Attributes atts) {
        for (int i = 0; i < atts.getLength(); i++) {
            String aName = atts.getQName(i);
            String aValue = atts.getValue(i);
            if (aName.equals(ProtocolConstants.ATT_VERSION_MAJOR)) {
                mcell.setMajorVersion(Long.parseLong(atts.getValue(i)));
            } else if (aName.equals(ProtocolConstants.ATT_VERSION_MINOR)) {
                mcell.setMinorVersion(Long.parseLong(atts.getValue(i)));
            }
        }
    }

    private void createNewCell(Attributes atts) {
        
        byte cellid = 0;
        String dataVIP = null;
        int port = 8080;
        long usedCapacity = -1;
        long totalCapacity = -1;
        
        for (int i = 0; i < atts.getLength(); i++) {

            String aName = atts.getQName(i);
            String aValue = atts.getValue(i);

            // this print of raw strings is useful if the numbers are doubted
            //System.out.println("name=" + aName + "  value=" + aValue);
            if (aName.equals(ProtocolConstants.ATT_CELLID)) {
                cellid = Byte.parseByte(aValue);
            } else if (aName.equals(ProtocolConstants.ATT_DATA_VIP)) {
                dataVIP = aValue;
                int ix = dataVIP.indexOf(":");
                if (ix != -1) {
                    port = Integer.parseInt(dataVIP.substring(ix+1));
                    dataVIP = dataVIP.substring(0, ix);
                }
            } else if (aName.equals(ProtocolConstants.ATT_USED_CAPACITY)) {
                usedCapacity = Long.parseLong(aValue);
            } else if (aName.equals(ProtocolConstants.ATT_TOTAL_CAPACITY)) {
                totalCapacity = Long.parseLong(aValue);
            }
        }
        curCell = new Cell(cellid, dataVIP, port, totalCapacity, usedCapacity);
    }
/*
    private void createNewRule(Attributes atts) {

        short ruleNumber = 0;
        byte originCellid = 0;
        short start = 0;
        short end = 0;
        long initCapacity = 0;

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
                ruleNumber = Short.parseShort(aValue);
            }
        }
        Interval interval = new Interval(start, end, initCapacity);
        Rule curRule = new Rule(originCellid, ruleNumber, interval);
        curCell.rules.add(curRule);
    }
*/
    public void endElement(String namespaceURI,
                           String localName,
                           String qName)
        throws SAXException {

        if (qName.equals(ProtocolConstants.TAG_CELL)) {
            mcell.addCell(curCell);
            curCell = null;
        }
    }

}

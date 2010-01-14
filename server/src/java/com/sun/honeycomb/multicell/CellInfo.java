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



package com.sun.honeycomb.multicell;


import java.util.List;
import java.util.HashMap;
import java.io.PrintStream;

import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.multicell.lib.Cell;
import com.sun.honeycomb.multicell.lib.XMLWriter;
import com.sun.honeycomb.multicell.lib.Rule;

//
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagData;
// Keep information about each cell.
//
public class CellInfo extends Cell
    implements Comparable, java.io.Serializable {

    static private final String        HTTP_HEADER = "http://";

    static private final double        DEVIATION_MAX = 0.01;

    // Default state of the cell
    static public final int            CELL_ENABLED          = 1;
    //
    // If master can't communicate or received an error status
    // which makes this cell have an inconsistent state.
    // (for e.g config, schema, lst of cells.)
    //
    static public final int            CELL_CONFIG_FAILED    = 2;
    static public final int            CELL_SCHEMA_FAILED    = 3;
    static public final int            CELL_ADD_CELL_FAILED  = 4;
    static public final int            CELL_DEL_CELL_FAILED  = 5;

    private int                        status;
    
    //
    // Consistent view across cell
    //
    private long                       totalCapacity;
    private long                       usedCapacity;

    // Latest status about capacity for the cells.
    private long                       curTotalCapacity;
    private long                       curUsedCapacity;

    private String                     destination;
    
    public CellInfo(byte cellId,
                     String adminVIP,
                     String dataVIP) {
        super(cellId, adminVIP, dataVIP);
    }
    
    //
    // Initialize new Cell
    //
    public CellInfo(byte cellId,
                     String adminVIP,
                     String dataVIP,
                     String spVIP,
                     String domainName,
                     String subnet,
                     String gateway,
                     List rules,
                     ServiceTagData serviceTagData) {

        super (cellId, adminVIP, dataVIP, spVIP, domainName, subnet, gateway);
        this.rules = rules;
        this.totalCapacity  = 0;
        this.usedCapacity = 0;
        this.curTotalCapacity  = 0;
        this.curUsedCapacity = 0;
        this.serviceTagData = serviceTagData;
        status = CELL_ENABLED;
    }

    //
    // Use to initialize object from config file using Base Object.
    //
    public CellInfo(Cell cell) {
        this(cell.getCellid(),
             cell.getAdminVIP(),
             cell.getDataVIP(),
             cell.getSPVIP(),
             cell.getDomainName(),
             cell.getSubnet(),
             cell.getGateway(),
             cell.getRules(),
             cell.getServiceTagData());
    }

    // copy CTOR
    public CellInfo(CellInfo cell) {
        this(cell.getCellid(),
             cell.getAdminVIP(),
             cell.getDataVIP(),
             cell.getSPVIP(),
             cell.getDomainName(),
             cell.getSubnet(),
             cell.getGateway(),
             cell.getRules(),
             cell.getServiceTagData());
        this.totalCapacity  = cell.getTotalCapacity();
        this.usedCapacity = cell.getUsedCapacity();
        this.curTotalCapacity  = cell.getCurTotalCapacity();
        this.curUsedCapacity = cell.getCurUsedCapacity();
        this.status = cell.getStatus();
    }

        
    //
    // Overides base class to generate xml for client
    //
    public void generateXMLClient(PrintStream printer, int indent) {
        HashMap map = new HashMap();
        map.put(ProtocolConstants.ATT_CELLID, String.valueOf((int) cellid));
        map.put(ProtocolConstants.ATT_DOMAIN_NAME, domainName);
        map.put(ProtocolConstants.ATT_ADMIN_VIP, adminVIP);
        map.put(ProtocolConstants.ATT_DATA_VIP, dataVIP);
        map.put(ProtocolConstants.ATT_SP_VIP, spVIP);
        map.put(ProtocolConstants.ATT_GATEWAY, gateway);
        map.put(ProtocolConstants.ATT_SUBNET, subnet);
        map.put(ProtocolConstants.ATT_USED_CAPACITY,
                String.valueOf(usedCapacity));
        map.put(ProtocolConstants.ATT_TOTAL_CAPACITY,
                String.valueOf(totalCapacity));
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
            XMLWriter.openTag(ProtocolConstants.TAG_RULE,
                              map, indent, true, printer);
        }
        generateXMLServiceTagData(printer, indent);
        XMLWriter.closeTag(ProtocolConstants.TAG_CELL, --indent, printer);
    }

    
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void setAdminVIP(String newAdminVIP) {
        super.setAdminVIP(newAdminVIP);
    }    
    
    public long getTotalCapacity() {
        return totalCapacity;
    }
        
    public long getUsedCapacity() {
        return usedCapacity;
    }

    public long getCurTotalCapacity() {
        return curTotalCapacity;
    }
        
    public long getCurUsedCapacity() {
        return curUsedCapacity;
    }

    public boolean isEnabled() {
        return (status == CELL_ENABLED);
    }

    public void setTotalCapacity(long c) {
        totalCapacity = c;
    }

    public void setUsedCapacity(long c) {
        usedCapacity = c;
    }

    public void setCurTotalCapacity(long c) {
        curTotalCapacity = c;
    }

    public void setCurUsedCapacity(long c) {
        curUsedCapacity = c;
    }

    public boolean isCellCapacityDeviated() {
        double cur = ((double) curUsedCapacity / (double) curTotalCapacity);
        double adv = ((double) usedCapacity / (double) totalCapacity);
        double diff = (cur - adv > 0) ? (cur - adv) : (adv - cur);
        return (diff > DEVIATION_MAX);
    }

    public boolean updateWithLatestCapacity(boolean newConfig) {

        boolean uninitializedCell = false;

        if (totalCapacity == 0 || usedCapacity == 0) {
            uninitializedCell = true;
        }
        if (uninitializedCell || newConfig) {
            totalCapacity = curTotalCapacity;
            usedCapacity = curUsedCapacity;
        }
        return uninitializedCell;
    }

    public void update(Cell cell, MultiCellLogger logger) {

        StringBuffer buf = new StringBuffer();

        buf.append("update parameters for cell " + cell.getCellid() + ": ");
        if (cell.getAdminVIP() != null) {
            setAdminVIP(cell.getAdminVIP());
            synchronized (this) {
                destination = null;
            }
            buf.append(" new adminVIP = " + adminVIP);
        }
        if (cell.getDataVIP() != null) {
            setDataVIP(cell.getDataVIP());
            buf.append(" new dataVIP = " + dataVIP);
        }
        if (cell.getSPVIP() != null) {
            setSPVIP(cell.getSPVIP());
            buf.append(" new spVIP = " + spVIP);
        }
        if (cell.getSubnet() != null) {
            setSubnet(cell.getSubnet());
            buf.append(" new subnet = " + subnet);
        }
        if (cell.getGateway() != null) {
            setGateway(cell.getGateway());
            buf.append(" new gateway = " + gateway);
        }
        if (logger != null) {
            logger.logDefault(buf.toString());
        }
    }

    public String capacityString() {

        double curLoad = (double) curUsedCapacity / (double) curTotalCapacity;
        double advLoad = (double) usedCapacity / (double) totalCapacity;

        StringBuffer buf = new StringBuffer();
        buf.append("(cellId = " + cellid);
        buf.append(", curLoad = " + curLoad);
        buf.append(", advertisedLoad = " + advLoad + ")");
        return buf.toString();
    }

    public int compareTo(Object obj) {
        if (! (obj instanceof CellInfo)) {
            return -1;
        }
        CellInfo cell = (CellInfo) obj;
        return (this.cellid - cell.getCellid());
    }

    public String getDestination() {
        synchronized (this) {
            if (destination == null) {
                computeDestination();
            }
            return destination;
        }
    }

    private void computeDestination() {
        StringBuffer str = new StringBuffer();
        str.append(HTTP_HEADER);
        str.append(adminVIP);
        String [] parts = adminVIP.split(":");
        if (parts.length == 2) {
            destination = str.toString();
        } else {
            str.append(":");
            str.append(MultiCell.getInstance().getMgmtPort());
            destination = str.toString();
        }
    }
}

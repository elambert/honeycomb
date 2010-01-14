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
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagCellData;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import org.xml.sax.SAXException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import java.util.List;
import java.io.File;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;


import com.sun.honeycomb.multicell.lib.Rule.Interval;

public class XMLParser {

    private long version;
    private Cell setupCell;
    private List cells;
    private File configFile;
    private String configFileName;


    static protected Logger logger =
        Logger.getLogger(XMLParser.class.getName());

    public XMLParser(String configFileName) {
        this.version = 0;
        this.cells = new ArrayList();
        this.configFileName = configFileName;
        this.configFile = new File(configFileName);
        this.setupCell = null;
    }


    public boolean readConfig() {

        boolean result = false;
        InputStream stream = null;
        try {
            stream = new FileInputStream(configFileName);
            result = readConfig(stream);
        } catch(FileNotFoundException e) {
            throw new MultiCellLibError("can't find file " + configFileName, e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch(IOException ioe) {
                    stream = null;
                }
            }
        }
        return result;
    }

    public long getLastModified() {
        return configFile.lastModified();
    }

    public void setSetupCell(byte newCellid, String newAdminVIP,
      String newDataVIP, String newSpVIP, String newSubnet, String newGateway) {

        if (cells.size() != 1) {
            throw new MultiCellLibError("invalid operation, can't " +
                " setup existing cell a hive configuration");
        }

        Cell existingCell = (Cell) cells.get(0);
        List rules = existingCell.getRules();
        if (rules.size() != 1) {
            throw new MultiCellLibError("invalid operation, can't " +
              " setup existing cell a hive configuration");                
        }
        Rule existingRule = (Rule) rules.get(0);
        
        // Get the current settings of servicetag.   Preserve
        // any settings that are currently set for cell. This must
        // be done to ensure that any setting set by manufacturing are retained.
        ServiceTagData existingServiceTagData = existingCell.getServiceTagData();

        setupCell = new Cell(newCellid, newAdminVIP, newDataVIP,
          newSpVIP, existingCell.getDomainName(), newSubnet, newGateway);

        Rule newRule = new Rule(existingRule);
        newRule.resetOriginCellid(newCellid);
        List newRules = new ArrayList();
        newRules.add(newRule);
        setupCell.setRules(newRules);
        
        // Update the new cell settings with the service tag data that
        // was saved earlier
        setupCell.setServiceTagData(existingServiceTagData);

        logger.info(MultiCellLib.MCLIB_LOG_PREFIX + "setupCell: " +
          setupCell);
    }


    private boolean readConfig(InputStream stream) {

        List oldCells = cells;
        cells = new ArrayList();
        try {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            SAXParser parser = parserFactory.newSAXParser();
            XMLConfigHandler handler = new XMLConfigHandler(this);
            parser.parse(stream, handler);
        } catch (SAXException e) {
            throw new MultiCellLibError("can't parse input file ", e);
        } catch (IOException e) {
            throw new MultiCellLibError("io error wile parsing input file ", e);
        } catch (ParserConfigurationException e) {
            throw new MultiCellLibError("error wile parsing input file ", e);
        }
        StringBuffer buf = new StringBuffer(MultiCellLib.MCLIB_LOG_PREFIX);
        buf.append("read config ( old cells, size = ");
        buf.append(oldCells.size());
        buf.append(", new cells size = ");
        buf.append(cells.size());
        logger.info(buf.toString());


        if ((oldCells.size() == 1) &&
          (cells.size() == 1)) {
            Cell oldCell = (Cell) oldCells.get(0);
            Cell newCell = (Cell) cells.get(0);

            buf = new StringBuffer(MultiCellLib.MCLIB_LOG_PREFIX);
            buf.append("read config ( old cell id  = ");
            buf.append(oldCell.getCellid());             
            buf.append(" new cell id  = ");
            buf.append(newCell.getCellid()).append(")");
            logger.info(buf.toString());
              

            if (oldCell.getCellid() != newCell.getCellid()) {
                return false;
            }
        }
        return true;
    }

    public long getVersion() {
        return version;
    }


    public void setVersion(long version) {
        this.version = version;
    }

    public void rmCell(byte cellid) {
        synchronized(cells) {
            for (int i = 0; i < cells.size(); i++) {
                Cell cur = (Cell) cells.get(i);
                if (cur.getCellid() == cellid) {
                    cells.remove(i);
                }
            }
        }
    }

    public void addCell(Cell cell) {
        synchronized(cells) {
            cells.add(cell);
        }
    }

    public Interval getMajorInterval(byte cellid) {
        Cell cell = getCell(cellid);
        return ((Rule) cell.rules.get(0)).getInterval();        
    }

    public String getDataVIP(byte cellid) {
        Cell cell = getCell(cellid);
        return cell.getDataVIP();
    }

    public String getAdminVIP(byte cellid) {
        Cell cell = getCell(cellid);
        return cell.getAdminVIP();
    }

    public String getSPVIP(byte cellid) {
        Cell cell = getCell(cellid);
        return cell.getSPVIP();
    }

    public String getClusterName(byte cellid) {
        Cell cell = getCell(cellid);
        return cell.getDomainName();
    }

    public String getSubnet(byte cellid) {
        Cell cell = getCell(cellid);
        return cell.getSubnet();
    }

    public String getGateway(byte cellid) {
        Cell cell = getCell(cellid);
        return cell.getGateway();
    }

    public List getHiveConfig() {
        List result = null;
        if (setupCell != null) {
            logger.info(MultiCellLib.MCLIB_LOG_PREFIX + "getHiveCfg " +
              " return setupCell ");
            result = new ArrayList();
            result.add(setupCell);
            setupCell = null;
        } else {
            result = cells;
        }
        return result;
    }

    public List getCells() {
        return cells;
    }

    public void setDataVIP(byte cellid, String dataVIP) {
        Cell cell = getCell(cellid);
        cell.setDataVIP(dataVIP);
    }

    public void setAdminVIP(byte cellid, String adminVIP) {
        Cell cell = getCell(cellid);
        cell.setAdminVIP(adminVIP);
    }

    public void setSPVIP(byte cellid, String spVIP) {
        Cell cell = getCell(cellid);
        cell.setSPVIP(spVIP);
    }

    public void setSubnet(byte cellid, String subnet) {
        Cell cell = getCell(cellid);
        cell.setSubnet(subnet);
    }

    public void setGateway(byte cellid, String gateway) {
        Cell cell = getCell(cellid);
        cell.setGateway(gateway);
    }

    public void resetCellid(byte cellid, byte newCellid) {
        Cell cell = getCell(cellid);
        cell.resetCellid(newCellid);
    }

    public byte getOriginCellid(byte cellid, byte ruleNumber, 
      short siloLocation)
        throws MultiCellLibException {

        Cell cell = getCell(cellid);
        byte res = cell.getOriginCellid(ruleNumber, siloLocation);
        if (res == -1) {
            throw new MultiCellLibException("tuple ruleNumber " + ruleNumber +
              ", siloLocation " + siloLocation +
              " does not match any rules");
        }
        return res;
    }
    
    /**
     * Get the service tag data that is associated with the specified cell
     * @param cellid the cell to retrieve the service tag data for
     * @return ServiceTagData the service tag data for the specified cell
     */
    public ServiceTagData getServiceTagData(byte cellid) {
        Cell cell = getCell(cellid);
        return cell.getServiceTagData();
    }
    
    /**
     * Set the service tag data to associate with the specified cell.
     * @param cellid the cell to set the service tag data for
     * @param data the service tag data
     */
    public void setServiceTagData(byte cellid, ServiceTagData data) {
        Cell cell = getCell(cellid);
        cell.setServiceTagData(data);
    }
    
    
    
    public byte getRuleNumber(byte originCellid, short siloLocation)
        throws MultiCellLibException {

        synchronized(cells) {
            for (int i = 0; i < cells.size(); i++) {
                Cell cur = (Cell) cells.get(i);
                byte res = cur.getRuleNumber(originCellid, siloLocation);
                if (res != -1) {
                    return res;
                }
            }
        }
        throw new MultiCellLibException("tuple originCellid " + originCellid +
                                     ", siloLocation " + siloLocation +
                                     " does not match any rules");
    }

    private Cell getCell(byte cellid) {
        synchronized (cells) {
            for (int i = 0; i < cells.size(); i++) {
                Cell curCell = (Cell) cells.get(i);
                if (curCell.cellid == cellid) {
                    return curCell;
                }
            }
        }
        throw new MultiCellLibError("can't find cell " + cellid);
    }

    /**
     * Fetch the service tag data for all the cells in the hive
     * @return List<ServiceTagCellData> list of service tag data for all
     * the cells in the hive.
     */
    List<ServiceTagCellData> getServiceTagDataForAllCells() {
        List<ServiceTagCellData> list = new ArrayList();
        synchronized (cells) {
            for (int i = 0; i < cells.size(); i++) {
                Cell curCell = (Cell) cells.get(i);
                ServiceTagCellData data = 
                        new ServiceTagCellData(curCell.getCellid(), 
                            curCell.getServiceTagData());
                list.add(data);
            }
        }
        return list;
    }
}

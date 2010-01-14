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



package com.sun.honeycomb.adm.client;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.math.BigInteger;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.admin.mgmt.client.HCSilo;
import com.sun.honeycomb.adm.cli.config.CliConfigProperties;
import com.sun.honeycomb.admin.mgmt.client.Fetcher;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.admin.mgmt.client.HCCellProps;
import com.sun.honeycomb.common.CliConstants;


import java.util.logging.Logger;
import java.util.logging.Level;

/* 
 * Caching silo information so we don't have to keep pounding
 * the server over and over
 */ 

public class SiloInfo {

    private static transient final Logger logger = 
      Logger.getLogger(SiloInfo.class.getName());
    private static final int SILO_CACHE_TIMEOUT_SECS=5;

    static private final String PROP_SYS_EMULATOR = "cli.emulator";
    static private final String PROP_SERVER_EMULATOR = "cli.emulator.server";

    private static SiloInfo _instance = null;
    private static String siloUrl = null;

    private List cells = null;
    private boolean isEmulatorCli;
    private byte curCellId;

    private static long siloInfoTimestamp = -1;

    public static synchronized SiloInfo getInstance()
        throws MgmtException, ConnectException {
        //
        // Does some caching to prevent slamming the server with 
        // requests. However, admin info for non master cells
        // can change, so it is allowed to time out every 5 seconds.
        //
        if (_instance == null || 
            System.currentTimeMillis() - siloInfoTimestamp > 
            SILO_CACHE_TIMEOUT_SECS * CliConstants.ONE_SECOND){

            siloInfoTimestamp = System.currentTimeMillis();
            _instance = new SiloInfo();
        }
        return(_instance);
    }
    
    /**
     * A change the number of cells has occurred.  Force silo
     * info to reload so we get the true state of the world on
     * the next getInstance() call.
     */
    public static synchronized void forceReload() {
        _instance = null;
    }

    private SiloInfo()
        throws MgmtException, ConnectException {

        String cliEmulator = System.getProperty(PROP_SYS_EMULATOR);
        if (cliEmulator != null && cliEmulator.equals("true")) {
            isEmulatorCli = true;
        } else {
            isEmulatorCli = false;
        }
        siloUrl = createSiloUrl();

        cells = new ArrayList();


        HCSilo silo = null;
        try { 
            silo=Fetcher.fetchHCSilo(siloUrl);
        } catch (MgmtException e) {
            throw new ConnectException (siloUrl);
        }
        curCellId = silo.getCellId();

        Iterator<String> adminIter = silo.getAdminVIPs().iterator();
        Iterator<Byte> cellIdIter = silo.getCellIds().iterator();

        while (cellIdIter.hasNext()) {
            Cell curCell = new Cell(cellIdIter.next(), adminIter.next());
            cells.add(curCell);
        }

        Collections.sort(cells);
        if (!isMasterCell()) {
            throw new MgmtException("Administrative commands need to " +
              "be run from the master cell " + getMasterCell().adminVIP);
        }

    }

    public synchronized void addCell(byte cellid, String adminVIP) {
        Cell newCell = null;
        newCell = new Cell(cellid, adminVIP);
        cells.add(newCell);
        Collections.sort(cells);
    }

    public synchronized void rmCell(byte cellid) {
        for (int i = 0; i < cells.size(); i++) {
            Cell curCell = (Cell) cells.get(i);
            if (curCell.cellId == cellid) {
                cells.remove(i);
                break;
            }
        }
    }

    public synchronized boolean isCellExist(byte cell) {
        for (int i = 0; i < cells.size(); i++) {
            Cell curCell = (Cell) cells.get(i);
            if (curCell.cellId == cell) {
                return true;
            }
        }
        return false;
    }

    private synchronized boolean isMasterCell() {
        if (curCellId ==  ((Cell) cells.get(0)).cellId) {
            return true;
        } else {
            return false;
        }
    }

    public synchronized Cell getMasterCell() {
        return (Cell) cells.get(0);
    }

    public synchronized byte getUniqueCellId() {
        return ((Cell) cells.get(0)).cellId;
    }
    
    public List getCells() {
        return cells;
    }

    public int getCellCount() {
        return cells.size();
    }

    public synchronized String getAdminVip(byte cellId) {
        
        for (int i = 0; i < cells.size(); i++) {
            
            Cell curCell = (Cell) cells.get(i);


            if (curCell.cellId == cellId) {
                return curCell.adminVIP;
            }
        }
        return null;
    }


    private String createSiloUrl() {
        StringBuffer url = new StringBuffer("http://");
	String destination = null;
        if (isEmulatorCli) {
            destination = System.getProperty(PROP_SERVER_EMULATOR);
	}
        if ((destination == null) || (destination.equals("null")
            || destination.length() == 0)) {
            String server =
            	CliConfigProperties.getInstance().getProperty(
                      "cli.adm.server.ip");
            String port =           
                  CliConfigProperties.getInstance().getProperty(
                      "cli.adm.server.port");
            url.append(server).append(":").append(port);
        } else {
            url.append(destination);
        }
        return url.toString();
    }

    public static String getSiloUrl() {
        return siloUrl;
    }
    
    public String getServerUrl(byte cellId) {
        
        String result = null;
        if (cellId == curCellId) {
            result = siloUrl;
        } else {
            String adminVip = getAdminVip(cellId);
            String [] parts = adminVip.split(":");
            if (parts.length == 1) {
                adminVip = new StringBuffer(adminVip).append(":")
                    .append(CliConfigProperties.getInstance().getProperty(
                        "cli.adm.server.port")).toString();
            }
            result = new StringBuffer("http://").append(adminVip).toString();
        }
        return result;
    }

    public String getMasterUrl() {
        return getServerUrl(((Cell) cells.get(0)).cellId);
    }


    public synchronized void updateCell(HCCell oldHCCell,
                                   HCCellProps newProps) {
        if(cells.size()>0) {
            Iterator iter = cells.iterator();
            while (iter.hasNext()) {
                Cell curCell =(Cell)iter.next();                    
                if(curCell.adminVIP.equals(oldHCCell.getCellProps().getAdminVIP())) {

                    curCell.adminVIP = newProps.getAdminVIP();                    
                }
            }
        }        
    }

    public boolean isEmulated() {
        return  isEmulatorCli;

    }
    
    
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("silourl=").append(siloUrl).append("\n");
        buf.append("isEmualtorCli").append(isEmulatorCli).append("\n");
        for (int i = 0; i < cells.size(); i++) {
            Cell curCell = (Cell) cells.get(i);
            buf.append(curCell.toString());
            buf.append("\n");
        }
        return buf.toString();
    }

    class Cell implements Comparable {
        //
        // For real hive this is actually the dataVIP-- but CLI
        // client does not need to know that. Just use the IP we give
        // here.
        //
        String adminVIP = null;
        byte cellId = -1;

        Cell(byte cellId, String adminVip) {
            this.cellId = cellId;
            this.adminVIP = adminVip;
        }

        public String toString() {
            String res = "cell = " + cellId + ", adminVIP = " + adminVIP;
            return res;
        }

        public int compareTo(Object obj) {
            if (! (obj instanceof Cell)) {
                return -1;
            }
            Cell cell = (Cell) obj;
            return (this.cellId - cell.cellId);
        }
    }
    
}

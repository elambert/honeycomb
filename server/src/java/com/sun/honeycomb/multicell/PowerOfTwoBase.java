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
import java.util.ArrayList;
import java.util.Collections;

import com.sun.honeycomb.multicell.mgmt.client.GetHCPowerOfTwo;
import com.sun.honeycomb.multicell.lib.MultiCellLib;


public abstract class PowerOfTwoBase
{
    protected CellInfo              thisCell = null;;
    protected MultiCellLogger       logger = null;
    protected MultiCell             multicellSvc = null;
    protected List                  cellOrder = null;
    
    protected long                  minorVersion = -1;

    public PowerOfTwoBase() {
        this.multicellSvc = MultiCell.getInstance();
        this.logger = multicellSvc.getMCLogger();
        this.thisCell = multicellSvc.getCell();
        this.minorVersion = 0;
    }

    public PowerOfTwoBase(boolean dummy) {
    }

    public long getMinorVersion() {
        synchronized(thisCell) {
            return minorVersion;
        }
    }

    public void setMinorVersion(long minor) {
        synchronized(thisCell) {
            minorVersion = minor;
        }
    }

    public void refreshPowerOfTwo(boolean masterCell) {

        updateLocalDiskCapacity();

        List cells = multicellSvc.getCells(MultiCell.SHALLOW_COPY, true);

        logger.logVerbose("refreshPowerOfTwo isMaster = " +
	    masterCell + ", cell size = " + cells.size());
	        if (!masterCell ||  cells.size() <= 1) {
            return;
        }
        
        logger.logDefault("refreshPowerOfTwo");
        long highestMinorRmt = -1;
        for (int i = 1; i < cells.size(); i++) {
            long minor = updateRemoteDiskCapacity((CellInfo) cells.get(i));
            if (minor > highestMinorRmt) {
                highestMinorRmt = minor;
            }
        }
        if (highestMinorRmt > getMinorVersion()) {
            logger.logVerbose("Detected a greater minor version from remote " +
                "cells, increment minor to " + highestMinorRmt);
            setMinorVersion(highestMinorRmt + 1);
        }

        boolean updatePowerOfTwo = checkCellOrdering(cells);
        if (!updatePowerOfTwo && cells.size() == 2) {
            updatePowerOfTwo = checkVariationOn2Cells(cells);
        }
        boolean uninitializedCells = false;
        for (int i = 0; i < cells.size(); i++) {
            CellInfo cur = (CellInfo) cells.get(i);
            boolean update = cur.updateWithLatestCapacity(updatePowerOfTwo);
            if (update) {
                logger.logVerbose("Cell " + cur.getCellid() + 
                  " was not initialized with current capacity");
                uninitializedCells = true;
            }
        }
        if (uninitializedCells) {
            incMinorVersion();
            updatePowerOfTwo = true;
        }

        logger.logDefault("push powerOfTwo config on all cells, " +
          "version = " + MultiCellLib.getInstance().getMajorVersion() +
          "." + getMinorVersion() + ", new ordering = " + updatePowerOfTwo);

        for (int i = 1; i < cells.size(); i++) {
            
            CellInfo cell = (CellInfo) cells.get(i);
            
            try {
                String destination = cell.getDestination();
                GetHCPowerOfTwo getHCPOT = 
                  new GetHCPowerOfTwo(logger, destination);
                
                logger.logVerbose("Retrieved getHCPOT from cell " + 
                  cell.getCellid());
                
                getHCPOT.pushPowerOfTwoConfig(cells,
                  MultiCellLib.getInstance().getMajorVersion(),
                  getMinorVersion());
                logger.logVerbose("Pushed new successfully new POT " +
                  " config to cell " + 
                  cell.getCellid());
                
            } catch (MultiCellException mce) {
                logger.logSevere(mce.toString());
                return;
            }
        }
    }

    private boolean checkVariationOn2Cells(List cells) {
        for (int i = 0; i < cells.size(); i++) {
            CellInfo curCell = (CellInfo) cells.get(i);
            if (curCell.isCellCapacityDeviated()) {
                incMinorVersion();
                logger.logDefault("Detected bias in cell capacity for cell " +
                  curCell.getCellid() + ", increment minor version to " +
                  getMinorVersion());
                return true;
            }
        }
        return false;
    }

    protected boolean checkCellOrdering(List cells) {

        boolean updatePowerOfTwo = false;

        ArrayList newCellOrder = new ArrayList();
        for (int i = 0; i < cells.size(); i++) {
            CellInfo curCell = (CellInfo) cells.get(i);
            PotCell potCell = new PotCell(curCell.getCellid(),
                                          curCell.getCurTotalCapacity(),
                                          curCell.getCurUsedCapacity());
            newCellOrder.add(potCell);
        }
        Collections.sort(newCellOrder);
        if (cellOrder != null) {
            if (newCellOrder.size() == cellOrder.size()) {
                for (int i = 0; i < newCellOrder.size(); i++) {
                    if (((PotCell) newCellOrder.get(i)).cellid != 
                        ((PotCell) cellOrder.get(i)).cellid) {
                        incMinorVersion();
                        logger.logDefault("increment minor version to " +
                                       getMinorVersion());
                        updatePowerOfTwo = true;
                        break;

                    }
                }
            } else {
                //
                // We added (or deleted) a cell; version major should
                // have been incremented. reset minor version
                //
                logger.logDefault("reset minor version, prev = " +
                  cellOrder.size() + 
                  " cells, new = " + newCellOrder.size() + " cells");
                resetMinorVersion();
                updatePowerOfTwo = true;
            }
        } else {
            incMinorVersion();
            updatePowerOfTwo = true;
        }
        cellOrder = newCellOrder;

        PotCell potCell = null;
        try {
            StringBuffer buf = new StringBuffer();
            buf.append("Cell ordering (load) : [ version = ");
            buf.append(MultiCellLib.getInstance().getMajorVersion());
            buf.append("." + getMinorVersion() + "]");
            
            for (int i = 0; i < cellOrder.size(); i++) {
                potCell = (PotCell) cellOrder.get(i);
                CellInfo cell = multicellSvc.getCell(potCell.cellid);
                buf.append(cell.capacityString());
            }
            logger.logDefault(buf.toString());
        } catch (MultiCellException mce) {
            logger.logSevere("Can't print current cell ordering " + 
                "missing cell " + potCell.cellid);
        }
        return updatePowerOfTwo;
    }

    protected long updateRemoteDiskCapacity(CellInfo peerCell) {
        
        String destination = peerCell.getDestination();

        logger.logDefault("update Remote Disk Capacity from cell " + 
                          peerCell.getCellid() + ", destination = " +
                          destination);

        long minor = -1;
        try {
            GetHCPowerOfTwo getHCPOT = 
                new GetHCPowerOfTwo(logger, destination);

            minor = getHCPOT.updateCellInfo(peerCell, 
              MultiCellLib.getInstance().getMajorVersion(), getMinorVersion());
            logger.logVerbose("Updated successfully disk capacity for cell " + 
                              peerCell.getCellid());
        } catch (MultiCellException mce) {
            logger.logSevere(mce.toString());
        }
        return minor;
    }
    
    private void resetMinorVersion() {
        synchronized(thisCell) {
            minorVersion = 0;
        }
    }
    
    private void incMinorVersion() {
        synchronized(thisCell) {
            minorVersion++;
        }
    }

    public abstract void updateDiskCapacity(CellInfo cell);

    protected void updateLocalDiskCapacity() {
        updateDiskCapacity(thisCell);
        logger.logVerbose("updateLocalDiskCapacity: used = " + 
          thisCell.getCurUsedCapacity() + 
          ", total = " + thisCell.getCurTotalCapacity());
    }

    public static class PotCell implements Comparable, java.io.Serializable {

        public byte cellid;
        public long totalCapacity;
        public long usedCapacity;
        transient double load;

        public PotCell(byte cellid, long totalCapacity, long usedCapacity) {
            this.cellid = cellid;
            this.usedCapacity = usedCapacity;
            this.totalCapacity = totalCapacity;
            this.load = (double) usedCapacity / (double) totalCapacity;
        }

        public int compareTo(Object o) {
            if (! (o instanceof PotCell)) {            
                throw new MultiCellError("invalid object to compare with");
            }
            PotCell potCell = (PotCell) o;
            double res = load - potCell.load;
            return (res < 0) ? (-1) : 1;
        }
    }
}

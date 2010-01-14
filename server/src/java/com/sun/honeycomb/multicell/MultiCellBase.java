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

import java.lang.Thread;
import java.util.logging.Logger;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.ListIterator;
import java.io.IOException;

import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.multicell.lib.Cell;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.multicell.lib.MultiCellLibException;
import com.sun.honeycomb.multicell.mgmt.client.GetHCCellInfo;
import com.sun.honeycomb.multicell.schemas.SchemaCreate;
import com.sun.honeycomb.multicell.PowerOfTwoBase.PotCell;
import com.sun.honeycomb.emd.config.EMDConfigException;
import com.sun.honeycomb.common.CliConstants;


// CM does not like my fancy abstract class so let the child class
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagData;
// implement the interface
public abstract class MultiCellBase // implements MultiCellIntf
{
    static public final int         MAX_CELLS = Byte.MAX_VALUE;

    static public final int         NO_COPY      = 0;
    static public final int         SHALLOW_COPY = 1;
    static public final int         DEEP_COPY    = 2;
    
    protected volatile boolean      keepRunning = false;
    protected Thread                thr = null;
    protected List                  cells = null;
    protected boolean            [] existingCellIds = null;
    protected MultiCellConfig       config = null;
    protected MultiCellLogger       logger = null;
    protected List                  intervals = null;;
    protected PowerOfTwo            powerOfTwo = null;

    private GetHCCellInfo           pendingHCCell = null;

    public MultiCellLogger getMCLogger() {
        if (logger == null) {
            throw new MultiCellError("multicell logger is not initialized");
        }
        return logger;
    }

    public MultiCellBase() throws MultiCellException {

        cells = new ArrayList();
        intervals = new ArrayList();

        existingCellIds = new boolean[MAX_CELLS + 1];
        for (int i = 0; i < MAX_CELLS + 1; i++) {
            existingCellIds[i] = false;
        }

        config = new MultiCellConfig(this);
        Logger mcLogger = Logger.getLogger(MultiCell.class.getName());
        mcLogger.info("MC: start Multicell...");
        logger = new MultiCellLogger(mcLogger);
        config.init();
        config.loadSiloConfig();
        keepRunning = true;
    }

    public abstract void shutdown();

    public void run() {

        int delay = 5000;  // 5 sec
        int curLoop = 0;
        long potRefresh = config.getPotRefresh();
        long refreshLoop = potRefresh / delay;

        //
        // When the service transition back to READY, it first hits
        // INIT, and then gets restarted in a new thread context
        // 
        thr = Thread.currentThread();

        while (keepRunning) {

            if (potRefresh != config.getPotRefresh()) {
                potRefresh = config.getPotRefresh();
                curLoop = 0;
                refreshLoop = potRefresh / delay;
            }

            if (curLoop == 0) {
                powerOfTwo.refreshPowerOfTwo(isMasterCell());
            }
            executeInRunLoop();

            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {
            }
            curLoop = (curLoop == refreshLoop) ? 0 : ++curLoop;
        }
    }


    //
    //  RMI CALLS : CLI
    //
    public List getExistingCells()
        throws IOException, MultiCellException, ManagedServiceException {
        return getCells(NO_COPY, false);
    }


    // 
    // Fix 'chocolate code': What happens of we can't update a remote cell 
    //  while adding/removing a cell. 
    //

    public byte addCellStart(String adminVIP, String dataVIP)
        throws IOException, MultiCellException, ManagedServiceException {
        
        
        logger.logDefault("trying to add new cell, adminVIP = " + 
          adminVIP + ", dataVIP = " + dataVIP);

        sanityCheck(dataVIP, adminVIP);

        configureInterface(adminVIP, true);

        logger.logVerbose("successfully checked this cell does not exist");
        //
        // Establish connection with remote cell and retrieve cell info
        //
        try {
            // Create CellInfo object with the adminVIP and dataVIP
            // This will provide us with the destination URL that
            // we will then use to connect to the remote cell to retrieve
            // a handle to that cell and it's current settings
            CellInfo tmpCell = new CellInfo((byte) 0, adminVIP, dataVIP);
            String destination = tmpCell.getDestination();
            pendingHCCell = new GetHCCellInfo(logger, destination);
        } catch (MultiCellException mce) {
            logger.logDefault(mce.toString());
            throw mce;
        }
        logger.logVerbose("successfully retrieved cell info from remote cell");
        return ((byte) 0);
    }

    public byte addCellSchemaValidation()
        throws IOException, MultiCellException, ManagedServiceException {

        if (pendingHCCell == null) {
            logger.logSevere("Inconsistent state in Multicell server");
            throw new MultiCellException("Internal error while adding the " +
                "new cell into the hive");
        }

        SchemaCreate schemaCreate = null;
        try {
            schemaCreate = new SchemaCreate(logger);
        } catch(EMDConfigException eme) {
            logger.logSevere("failed to create schema on master cell " +
                eme);
            throw new MultiCellException("Internal error while comparing " +
                "schemas between cells");
        }
        try {
            for (int i = 0; i < schemaCreate.getNbSchemaPieces(); i++) {
                String schemaPiece = schemaCreate.getNextSchemaPiece();
                byte mask = 0;
                if (i == 0) {
                    mask =  CliConstants.MDCONFIG_FIRST_MESSAGE;
                }
                if (i == (schemaCreate.getNbSchemaPieces() - 1)) {
                    mask |= CliConstants.MDCONFIG_LAST_MESSAGE;                
                }
                pendingHCCell.checkSchema(schemaPiece, mask);
            }
            logger.logVerbose("successfully validated remote schema ");        
            return ((byte) 0);
        } finally {
            schemaCreate.freeResources();
        }
    }

    public byte addCellPropertiesValidation()
        throws IOException, MultiCellException, ManagedServiceException {
        if (pendingHCCell == null) {
            logger.logSevere("Inconsistent state in Multicell server");
            throw new MultiCellException("Internal error while adding the " +
                "new cell into the hive");
        }

        pendingHCCell.checkSiloProps();
        logger.logVerbose("successfully validated properties");
        return ((byte) 0);
    }

    public byte addCellUpdateHiveConfig()
        throws IOException, MultiCellException, ManagedServiceException {

        if (pendingHCCell == null) {
            logger.logSevere("Inconsistent state in Multicell server");
            throw new MultiCellException("Internal error while adding the " +
                "new cell into the hive");
        }

        //
        // Push existing silo config to the new cell
        //
        pendingHCCell.pushInitConfig(getCells(DEEP_COPY, true),
          MultiCellLib.getInstance().getMajorVersion() + 1);

        logger.logVerbose("successfully pushed existing config to new cell");

        CellInfo newCell = pendingHCCell.getCellInfo();        
        //
        // Update local configuration 
        //
        MultiCellLib.getInstance().addCell(newCell, 
          MultiCellLib.getInstance().getMajorVersion() + 1);
        logger.logVerbose("successfully updated silo_config.xml ");
        //
        // Notify existing cells about this new cell.
        //
        boolean success = true;
        StringBuffer failures = new StringBuffer();    
        synchronized (cells) {

            failures.append("Failed to update config of cells : ");

            for (int i = 1; i < cells.size(); i++) {
                CellInfo curCell = (CellInfo) cells.get(i);
                try {
                    String destination = curCell.getDestination();
                    GetHCCellInfo getHCCellInfo =
                      new GetHCCellInfo(logger, destination);
                    getHCCellInfo.addCell(newCell, 
                      MultiCellLib.getInstance().getMajorVersion());
                } catch (MultiCellException mce) {
                    success = false;
                    failures.append(curCell.getCellid() + " ");
                    logger.logSevere(mce.toString());
                    curCell.setStatus(CellInfo.CELL_ADD_CELL_FAILED);
                }
            }
        }
        if (success) {
            logger.logVerbose("successfully notified remote cells " +
              "about this new cell");
        } else {
            logger.logSevere(failures.toString());
            failures.append(". Hive may be in an inconsistent state...");
            //
            // At that point we need to fail and let  the admin know
            // we may have reached an inconsistent state
            //
            throw new MultiCellException(failures.toString());
        }
        //
        // Add cell in the list
        //
        addCell(newCell, false);

        logger.logDefault("cell " + newCell.getCellid() + 
          " successfully joined the silo");
        return newCell.getCellid();
    }


    public void removeCell(byte cellid)
        throws IOException, MultiCellException, ManagedServiceException {

        logger.logDefault("trying to remove cell, cellid = " + cellid); 
        
        if (cellid == getMasterCell().getCellid()) {
            throw new MultiCellException("can't remove master cell " +
              " from the hive");
        }
              
        // sanity check
        if (!existingCellIds[cellid]) {
            throw new MultiCellException("cell is not part of the silo");
        }

        boolean success = true;
        StringBuffer failures = new StringBuffer();
        synchronized (cells) {
            failures.append("Failed to remove cell " + cellid +
              " from config of  cells : ");

            for (int i = 1; i < cells.size(); i++) {
                CellInfo curCell = (CellInfo) cells.get(i);

                try {
                    String destination = curCell.getDestination();
                    GetHCCellInfo getHCCellInfo =
                      new GetHCCellInfo(logger, destination);
                    getHCCellInfo.delCell(cellid,
                      MultiCellLib.getInstance().getMajorVersion() + 1);

                } catch (MultiCellException mce) {
                    success = false;
                    failures.append(curCell.getCellid() + " ");
                    logger.logSevere(mce.toString());
                    curCell.setStatus(CellInfo.CELL_DEL_CELL_FAILED);
                }                
            }
        }
        if (success) {
            logger.logVerbose("successfully removed cell " + cellid +
              " on remote cells");
        } 

        //
        // Update local configuration 
        //
        MultiCellLib.getInstance().rmCell(cellid, 
          MultiCellLib.getInstance().getMajorVersion() + 1);
        logger.logVerbose("successfully updated silo_config.xml ");

        rmCell(cellid);

        if (!success) {
            logger.logSevere(failures.toString());
            failures.append(". Hive may be in an inconsistent state...");
            throw new MultiCellException(failures.toString());
        } else {
            logger.logDefault("successfully delete cell " + cellid);
        }
    }


    public void changeCellCfg(Cell cell)
        throws IOException, MultiCellException, ManagedServiceException {

        boolean updateRoute = false;

        logger.logDefault("changeCellCfg cellid = " + cell.getCellid());
        
        //
        // There is no point in locking the cell; if there is a concurrent
        // operation from multicell (PowerOfTwo) that will fail anyway
        // because the remote cell is rebooting.
        //
        CellInfo updateCell = getCell(cell.getCellid());
        if (updateCell == null) {
            throw new MultiCellException("Cell " + cell.getCellid() +
                " does not exit ");
        }
        if (!cell.getAdminVIP().equals(updateCell.getAdminVIP())) {
            updateRoute = true;
        }
        if (updateRoute) {
            configureInterface(updateCell.getAdminVIP(), false);
        }
        updateCell.update(cell, logger);
        if (updateRoute) {
            configureInterface(updateCell.getAdminVIP(), true);            
        }
    }


    //
    // RMI calls : Mgmt remote invocation.
    //
    public CellInfo getCellInfo()
        throws IOException, ManagedServiceException {
        return getCell();
    }

    public byte addNewCell(CellInfo newCell, long version)
        throws IOException, MultiCellException, ManagedServiceException {
        logger.logDefault("addNewCell cellid = " + newCell.getCellid() +
          ", version = " + version);

        if (isMasterCell()) {
            throw new MultiCellException("invalid call addNewCell() on " +
              "Master cell");
        }

        if (existingCellIds[newCell.getCellid()]) {
            throw new MultiCellException("trying to add an already " + 
              "existing cell " +
              newCell.getCellid());
        }

        MultiCellLib.getInstance().addCell(newCell, version);
        addCell(newCell, true);
        logger.logDefault("successfully added  new cell " +
          newCell.getCellid());
        return 0;
    }    


    public byte rmExistingCell(byte cellid, long version)
        throws IOException, MultiCellException, ManagedServiceException {

        logger.logDefault("rmExistingCell cellid = " + cellid +
          ", version = " + version);

        if (isMasterCell()) {
            throw new MultiCellException("invalid call rmExistingCell() " +
              "on Master cell");
        }

        if (!existingCellIds[cellid]) {
            throw new MultiCellException("trying to remove unexisting cell" +
              cellid);
        }

        // 
        // If this is me, i am not part of the silo anymore.
        // In this case remove all other cells from my config.
        if (getCell().getCellid() == cellid) {
            List otherCells = new ArrayList();
            synchronized (cells) {
                for (int i = 0; i < cells.size(); i++) {
                    CellInfo cur = (CellInfo) cells.get(i);
                    if (cur.getCellid() != cellid) {
                        otherCells.add(cur);
                    }
                }
            }
            MultiCellLib.getInstance().rmCells(otherCells);
            logger.logVerbose("successfully updated silo_config.xml : " +
              " removed all cells");
            rmOtherCells(cellid);

        } else {

            MultiCellLib.getInstance().rmCell(cellid, version);
            logger.logVerbose("successfully updated silo_config.xml : " +
              " removed cell " + cellid);
            rmCell(cellid);
        }

        logger.logDefault("successfully handled call to remove cell " + cellid);
        return 0;
    }    


    public byte pushInitConfig(List existingCells, long versionMajor)
        throws IOException, MultiCellException, ManagedServiceException {

        logger.logDefault("pushInitConfig " + ", version = " + versionMajor);

        StringBuffer buf = new StringBuffer();
        MultiCellLib.getInstance().addCells(existingCells, versionMajor);
        for (int i = 0; i < existingCells.size(); i++) {
            CellInfo cell = (CellInfo) existingCells.get(i);
            addCell(cell, true);
            buf.append(" cell " + cell.getCellid());
            if (i < (existingCells.size() - 1)) {
                buf.append(",");
            }
        }
        logger.logDefault("successfully added existing cells ");

        logger.logVerbose("Existing cells are : " + buf.toString());
        return 0;
    }

    public byte updateNewPowerOfTwoConfig(List potCells, 
      long major, long minor)
        throws IOException, MultiCellException, ManagedServiceException {
        
        logger.logVerbose("updateNewPowerOfTwoConfig() version = " +
            major + "." + minor);

        if (potCells.size() != cells.size()) {
            throw new MultiCellException("expected " + cells.size() +
              ", received " + potCells.size() +
              " cells");
        }

        //
        // Should never hit that case, but if that ever happens we want
        // to correct the 'bias' so all cells report the same config.
        //
        if (major != MultiCellLib.getInstance().getMajorVersion()) {
            logger.logWarning("Master cell version = " +
                 major + "." + minor + 
                ", running version = " +
                MultiCellLib.getInstance().getMajorVersion() + "." +
                powerOfTwo.getMinorVersion() + " fix major version!!");
            MultiCellLib.getInstance().setMasterCellVersion(major);
        }
        
        if ((major == MultiCellLib.getInstance().getMajorVersion()) &&
          (minor == powerOfTwo.getMinorVersion())) {
            logger.logVerbose("Already run at version " +
              major + "." + minor + ", nothing to do.");
            return 0;
        }
        
        //
        // Otherwise there is a change in the configuration so
        // update our list of cells.
        //
        for (int i = 0; i < potCells.size(); i++) {
            PotCell curPot = (PotCell) potCells.get(i);
            CellInfo curCell = getCell(curPot.cellid);
            curCell.setTotalCapacity(curPot.totalCapacity);
            curCell.setUsedCapacity(curPot.usedCapacity);
            logger.logVerbose("update capacity cell = " +
              curPot.cellid +
              ", total = " + curPot.totalCapacity +
              ", used " + curPot.usedCapacity);
        }
        if (minor != (powerOfTwo.getMinorVersion() + 1)) {
            logger.logWarning("Master cell version = " +
              major + "." + minor + 
              ", running version = " +
              MultiCellLib.getInstance().getMajorVersion() + "." +
              powerOfTwo.getMinorVersion() + " fix minor version!!");
        }
        powerOfTwo.setMinorVersion(minor);
	
        logger.logDefault("successfully updated new POT config to version " +
          MultiCellLib.getInstance().getMajorVersion() + "." + minor);
        return 0;
    }


    public CellInfo loadCell(Cell cell, byte thisCellid) 
        throws MultiCellException {

        boolean addRoute = true;
        CellInfo newCell = new CellInfo(cell);
        if (thisCellid == newCell.getCellid()) {
            PowerOfTwo tmpPot = new PowerOfTwo(true);
            tmpPot.updateDiskCapacity(newCell);
            addRoute = false;
        }
        addCell(newCell, addRoute);
        return newCell;
    }


    public CellInfo getCell() {
        return config.getThisCell();
    }

    public CellInfo getCell(byte cellId) 
        throws MultiCellException {
        synchronized(cells) {
            for (int i = 0; i < cells.size(); i++) {
                CellInfo cell = (CellInfo) cells.get(i);
                if (cell.getCellid() == cellId) {
                    return cell;
                }
            }
            throw new MultiCellException("no such cell in the silo");
        }
    }

    public List getCells(int copyType, boolean enabledOnly) {

        ArrayList res = null;

        switch (copyType) {
        case NO_COPY:
            if (enabledOnly) {
                throw new MultiCellError("can't set enabledOnly with NO_COPY");
            }
            return cells;

        case SHALLOW_COPY:
            res = new ArrayList();
            synchronized(cells) {
                for (int i = 0; i < cells.size(); i++) {
                    if (!enabledOnly || ((CellInfo) cells.get(i)).isEnabled()) {
                        res.add(cells.get(i));
                    }
                }
            }
            return res;

        case DEEP_COPY:
            res = new ArrayList();
            synchronized(cells) {
                for (int i = 0; i < cells.size(); i++) {
                    if (!enabledOnly || ((CellInfo) cells.get(i)).isEnabled()) {
                        CellInfo newCell = new CellInfo((CellInfo) cells.get(i));
                        res.add(newCell);
                    }
                }
            }
            return res;

        default:
            throw new MultiCellError("unexpected copy type");
        }
    }

    public int getNbCells() {
        return cells.size();
    }

    public long getMinorVersion() {
        return powerOfTwo.getMinorVersion();
    }

    public int getMgmtPort() {
        return config.getMgmtPort();
    }

    public CellInfo getMasterCell() {
        return (CellInfo) cells.get(0);
    }

    ////////////////////////////////////////////////////////////////////
    //                                                                //
    //                           PROTECTED                            //
    //                                                                //
    ////////////////////////////////////////////////////////////////////

    
    protected abstract void executeInRunLoop();

    //
    // Helpers for adding/removal of cells
    //
    protected void rmCell(byte cellid)
        throws MultiCellException {

        synchronized(cells) {
            int nbCells = cells.size();
            for (int i = 0; i < nbCells; i++) {
                CellInfo cell = (CellInfo) cells.get(i);
                if (cell.getCellid() == cellid) {
                    try {
                        configureInterface(cell.getAdminVIP(), false);
                    } catch (MultiCellException mue) {
                        logger.logWarning("Failed to remove the route " +
                          "for the cell " + cellid);
                    }
                    cells.remove(i);
                    break;
                }
            }
            existingCellIds[cellid] = false;
        }
    }


    protected void rmOtherCells(byte thisCellId)
        throws MultiCellException {

        synchronized(cells) {
            ListIterator it = cells.listIterator();
            while (it.hasNext()) {
                CellInfo cur = (CellInfo) it.next();
                if (cur.getCellid() != thisCellId) {
                    try {
                        configureInterface(cur.getAdminVIP(), false);
                    } catch (MultiCellException mue) {
                        logger.logWarning("Failed to remove the route " +
                          "for the cell " + cur.getCellid());
                    }
                    it.remove();
                    existingCellIds[cur.getCellid()] = false;
                }
            }
        }
    }

    protected void sanityCheck(String dataVIP, String adminVIP)
        throws MultiCellException{

        synchronized(cells) {

            int nbCells = cells.size();
            for (int i = 0; i < nbCells; i++) {
                CellInfo cell = (CellInfo) cells.get(i);
                if (dataVIP.equals(cell.getDataVIP())) {
                    throw new MultiCellException("Cell " + cell.getCellid() +
                      " is already configured " +
                      " with dataVIP = " + dataVIP);
                }
                if (adminVIP.equals(cell.getAdminVIP())) {
                    throw new MultiCellException("Cell " + cell.getCellid() +
                      " is already configured " +
                      " with adminVIP = " + 
                      adminVIP);
                }
            }
        }
    }

    // cells are ordered by cellid and master cell coresponds to lower cellid
    protected boolean isMasterCell() {
        synchronized (cells) {
            if (config.getThisCell().getCellid() ==
              ((CellInfo) cells.get(0)).getCellid()) {
                return true;
            } else {
                return false;
            }
        }
    }


    abstract protected void configureInterface(String adminVIP, boolean add) 
        throws MultiCellException;

    protected void addCell(CellInfo newCell, boolean addRoute)
        throws MultiCellException {
        synchronized(cells) {
            int nbCells = cells.size();
            for (int i = 0; i < nbCells; i++) {
                CellInfo curCell = (CellInfo) cells.get(i);
                try {
                    boolean res = curCell.equals(newCell);
                    if (res) {
                        throw new MultiCellException("can ' t add new cell :" +
                          " cell with cellid " +
                          newCell.getCellid() +
                          " already exists");
                    }
                } catch (MultiCellLibException mcelExc) {
                    throw new MultiCellException("can ' t add new cell :" +
                      mcelExc.getMessage());
                }
            }

            if (addRoute) {
                configureInterface(newCell.getAdminVIP(), true);
            }

            logger.logDefault("add cell : cellid = " + newCell.getCellid() +
              ", adminVIP = " + newCell.getAdminVIP() +
              ", dataVIP = " + newCell.getDataVIP() +
              ", spVIP = " + newCell.getSPVIP() +
              ", name = " + newCell.getDomainName());

            cells.add(newCell);            
            Collections.sort(cells);
            logger.logVerbose("Cell order...");
            for (int i = 0; i < cells.size(); i++) {
                logger.logVerbose("cell " +
                  ((CellInfo) cells.get(i)).getCellid());
            }
            existingCellIds[newCell.getCellid()] = true;
        }
    }
}

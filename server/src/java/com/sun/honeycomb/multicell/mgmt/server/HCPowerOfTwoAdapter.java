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

import java.math.BigInteger;
import java.util.List;
import java.util.ArrayList;

import com.sun.honeycomb.multicell.MultiCellLogger;
import com.sun.honeycomb.multicell.MultiCellIntf;
import com.sun.honeycomb.multicell.MultiCellException;
import com.sun.honeycomb.multicell.CellInfo;
import com.sun.honeycomb.multicell.PowerOfTwoBase.PotCell;
import com.sun.honeycomb.mgmt.common.MgmtException;

import java.util.logging.Logger;
import java.util.logging.Level;

public class HCPowerOfTwoAdapter implements HCPowerOfTwoAdapterInterface {

    private List cells;
    private long minorVersion;
    private long majorVersion;
    private MultiCellIntf multicellAPI;

    private static transient final Logger logger = 
        Logger.getLogger(HCPowerOfTwoAdapter.class.getName());

    public void loadHCPowerOfTwo() 
        throws InstantiationException {

        InstantiationException instExc = null;

        multicellAPI = MultiCellIntf.Proxy.getMultiCellAPI();
        if (multicellAPI == null) {
            logger.severe(MultiCellLogger.MULTICELL_LOG_PREFIX +
              "failed get multicell API");
            instExc = new InstantiationException("Internal error : can't " +
                " initialize HCPOwerOFTwoAdapter");
            throw instExc;
        } 

        MultiCellIntf.Proxy proxy = MultiCellIntf.Proxy.getProxy();
        if (proxy == null) {
            logger.severe(MultiCellLogger.MULTICELL_LOG_PREFIX +
              "failed get multicell Proxy");
            instExc = new InstantiationException("Internal error : can't " +
                " initialize HCPOwerOFTwoAdapter");
            throw instExc;
        } 
        try {
            cells = proxy.getCells();
            minorVersion = proxy.getMinorVersion();
            majorVersion = proxy.getMajorVersion();
        } catch (Exception exc) {
            logger.log(Level.SEVERE,
              MultiCellLogger.MULTICELL_LOG_PREFIX +
              "failed get multicell Proxy ", exc);
            instExc = new InstantiationException("Internal error : can't " +
              " initialize HCPOwerOFTwoAdapter");
            throw instExc;
        }
    }

    //
    // Returns all the cells known. Currently we only care about
    // the local cell-- see updateCellInfo in GetHCPowerOfTwo--,
    // but if we decide to use a ring we may need that extension.
    // Note thatwe return the 'cur' capacity, that is the capacity
    // as computed the most recently by Multicell
    //
    public void populateCellCapacity(List<HCCellCapacity> array)
        throws MgmtException {


        for (int i = 0; i < cells.size(); i++) {
            CellInfo curCell = (CellInfo) cells.get(i);
            HCCellCapacity curCellCapacity = new HCCellCapacity();
            curCellCapacity.setVersionMinor(minorVersion);
            curCellCapacity.setVersionMajor(majorVersion);
            curCellCapacity.setCellid(curCell.getCellid());
            curCellCapacity.setUsedCapacity(curCell.getCurUsedCapacity());
            curCellCapacity.setTotalCapacity(curCell.getCurTotalCapacity());

            logger.info(MultiCellLogger.MULTICELL_LOG_PREFIX +
              " populate Cell capacity, cell = " +
              curCell.getCellid() + 
              ", curTotal = " + 
              curCell.getCurTotalCapacity() +
              ", curUsed " + 
              curCell.getCurUsedCapacity() +
              ", [total = " + 
              curCell.getTotalCapacity() +
              ", used " + 
              curCell.getUsedCapacity() + "]");

            array.add(curCellCapacity);
        }      
    }
    

    public Byte pushNewPowerOfTwo(HCPowerOfTwo newPot)
        throws MgmtException {

        StringBuffer buf = new StringBuffer();
        buf.append(MultiCellLogger.MULTICELL_LOG_PREFIX + "pushNewPowerOfTwo ");

        List potCells = new ArrayList();
        HCCellCapacity curCellCapacity = null;
        long majorVersion = -1;
        long minorVersion = -1;

        List<HCCellCapacity> list = newPot.getCellCapacity();	
        for (int i = 0; i < list.size(); i++) {

            curCellCapacity = list.get(i);
            
            if (i == 0) {
                majorVersion = curCellCapacity.getVersionMajor();
                minorVersion = curCellCapacity.getVersionMinor();

                buf.append(", version = " + majorVersion +
                  "." + minorVersion + ": ");
            }

            //
            // Set with the 'advertized' capacity 
            //
            PotCell curPot = new PotCell(curCellCapacity.getCellid(),
                                         curCellCapacity.getTotalCapacity(),
                                         curCellCapacity.getUsedCapacity());
            potCells.add(curPot);

            buf.append("[cell " + curCellCapacity.getCellid() +
              ", used = " + curCellCapacity.getUsedCapacity() +
              ", total = " + curCellCapacity.getTotalCapacity() + "] ");
        }

        logger.info(buf.toString());

        try {
            multicellAPI.updateNewPowerOfTwoConfig(potCells,
              majorVersion, minorVersion);
        } catch (Exception exc /* IOException, MultiCellException */) {
            logger.log(Level.SEVERE,
              MultiCellLogger.MULTICELL_LOG_PREFIX + 
              "failed to update new powerOfTwo config ", exc);
            throw new MgmtException("Internal error while pushing new " +
                " powerOfTwo configuration");
        }
        return new Byte((byte) 0);
    }
}

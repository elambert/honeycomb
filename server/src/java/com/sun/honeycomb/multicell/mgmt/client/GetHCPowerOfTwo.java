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



/*
 * This example fetches the HCCellInfo object from the management server and
 * print its values
 */

package com.sun.honeycomb.multicell.mgmt.client;

import java.util.List;
import java.math.BigInteger;

import com.sun.honeycomb.multicell.mgmt.client.Fetcher;
import com.sun.honeycomb.multicell.mgmt.client.HCPowerOfTwo;
import com.sun.honeycomb.multicell.mgmt.client.HCCellCapacity;
import com.sun.honeycomb.mgmt.common.MgmtException;

import com.sun.honeycomb.multicell.PowerOfTwoBase.PotCell;
import com.sun.honeycomb.multicell.MultiCellException;
import com.sun.honeycomb.multicell.MultiCellLogger;
import com.sun.honeycomb.multicell.CellInfo;

public class GetHCPowerOfTwo {

    private String       destination = null;
    private HCPowerOfTwo pot = null;
    private MultiCellLogger logger;


    public GetHCPowerOfTwo(MultiCellLogger logger, String destination)
        throws MultiCellException {
        this.logger = logger;
        this.destination = destination;
        try {
            pot = Fetcher.fetchHCPowerOfTwo(destination);
        } catch(MgmtException mgmtExc) {
            throw new MultiCellException(mgmtExc.getMessage());
        }
    }

    public long updateCellInfo(CellInfo cellInfo, long major, long minor)
        throws MultiCellException {

        long minorResult = -1;

        HCCellCapacity curCellCapacity = null;
        List<HCCellCapacity> list = pot.getCellCapacity();
        for (int j = 0; j < list.size() ; j++) {
            curCellCapacity = list.get(j);
            //
            // Skip all cells but the remote target.
            //
            if (curCellCapacity.getCellid() == cellInfo.getCellid()) {
                break;
            }
        }
        if (curCellCapacity == null) {
            throw new MultiCellException("cell " + cellInfo.getCellid() +
              " has not been returned by peer");
        }

        if (curCellCapacity.getVersionMajor() != major) {
            throw new MultiCellException("remote cell " + 
              cellInfo.getCellid() + " [version = " +
              curCellCapacity.getVersionMajor() + "." + 
              curCellCapacity.getVersionMinor() + "]" +
              ", master cell [version = " +
              major + "." + minor + "]");
        }

        if (curCellCapacity.getVersionMinor() > minor) {
            logger.logWarning("remote cell " + 
              cellInfo.getCellid() + " [version = " +
              curCellCapacity.getVersionMajor() + "." + 
              curCellCapacity.getVersionMinor() + "]" +
              ", master cell [version = " +
              major + "." + minor + "], need to update master cell");
            minorResult = curCellCapacity.getVersionMinor();
        }
        

        logger.logVerbose("remote cell " + cellInfo.getCellid() + 
          ", curTotal capacity = " + 
          curCellCapacity.getTotalCapacity() +
          ", curUsed capacity = " +
          curCellCapacity.getUsedCapacity());
        cellInfo.setCurUsedCapacity(curCellCapacity.getUsedCapacity());
        cellInfo.setCurTotalCapacity(curCellCapacity.getTotalCapacity());

        return minorResult;
    }

    public void pushPowerOfTwoConfig(List cells,
      long versionMajor, long versionMinor) throws MultiCellException {
        
        HCCellCapacity curCellCapacity = null;
        HCPowerOfTwo newPot = new HCPowerOfTwo();
        List<HCCellCapacity> list = newPot.getCellCapacity();

        StringBuffer buf = new StringBuffer();
        buf.append("pushPowerOfTwoConfig ");
        for (int i = 0; i < cells.size(); i++) {
            CellInfo cur = (CellInfo) cells.get(i);
            curCellCapacity = new HCCellCapacity();
            curCellCapacity.setVersionMajor(versionMajor);
            curCellCapacity.setVersionMinor(versionMinor);
            curCellCapacity.setCellid(cur.getCellid());
            curCellCapacity.setTotalCapacity(cur.getTotalCapacity());
            curCellCapacity.setUsedCapacity(cur.getUsedCapacity());
            list.add(curCellCapacity);

            if (i == 0) {
                buf.append(", version = " + versionMajor + 
                  "." + versionMinor + ": ");

            }
            buf.append("[cell " + curCellCapacity.getCellid() +
              ", used = " + curCellCapacity.getUsedCapacity() +
              ", total = " + curCellCapacity.getTotalCapacity() + "] ");
        }
        logger.logVerbose(buf.toString());

        try {
            pot.pushNewPowerOfTwo(newPot);
        } catch(MgmtException mgmtExc) {
            throw new MultiCellException("can't push new POT config to " +
              " cell peer " + 
              curCellCapacity.getCellid(), mgmtExc);
        }
    }
}



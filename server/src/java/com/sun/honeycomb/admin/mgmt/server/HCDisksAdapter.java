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




package com.sun.honeycomb.admin.mgmt.server;


import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.honeycomb.layout.LayoutProxy;
import com.sun.honeycomb.common.ConfigPropertyNames;
import org.w3c.dom.Document;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.common.MgmtException;
import java.math.BigInteger;
import java.util.List;
import com.sun.honeycomb.admin.mgmt.Utils;
import com.sun.honeycomb.layout.LayoutProxy;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.admin.mgmt.AdminException;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.cm.node_mgr.Node;

//
// codecs - encode/decode support
//
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.net.QuotedPrintableCodec;
/**
 * HCDisks handles only the node objects for the cell. 
 * It also incorporates a few calls that pertain to/handle 
 * disks, such as the number of alive disks, and the system
 * capacity.
 */
public  class HCDisksAdapter implements HCDisksAdapterInterface {

    private static transient final Logger logger = 
      Logger.getLogger(HCDisksAdapter.class.getName());
    protected ClusterProperties    config;
    private Disk []              disks;


    public void loadHCDisks()
        throws InstantiationException {
        config = ClusterProperties.getInstance();

        try {                        
            disks=Utils.getDisks();
        } catch (AdminException ae) {
            logger.log(Level.SEVERE,"Internal cluster errror setting up HCDisks.", ae);
            throw new InstantiationException("Internal cluster error : " +
              "failed to instantiate the adapater HCDisks");
        }

    }

    /*
    * This is the list of accessors to the object
    */
        
    public void populateDisksList(List<HCDisk>  array) throws MgmtException {
        try {
            for (int i = 0; i < Utils.getNumNodes(); i++) {
                Disk [] disks = Utils.getDisksOnNode(Utils.NODE_BASE + i);
            
                for (int j = 0; j < Utils.getDisksPerNodes(); j++) {
                    Disk curDisk=null;
                    if(null != disks) {
                        curDisk=disks[j];
                    }
                    HCDisk disk = FruCreators.createHCDisk(curDisk,Utils.NODE_BASE+i,j);
                    array.add(disk);
                }
            }
        } catch (AdminException e) {

            logger.severe("Canot populate disk array:"+e);
            //
            // Internationalize here
            //
            throw new MgmtException ("Unable to fetch disk information.");
        }
    }

    //
    // placeholder for mgmt
    //
    public void setDisksList(List<HCDisk> ignore) throws MgmtException {
    }


    /**
     * Get the estimated free disk space for the cell.
     * This routine should not be used for reporting physical space.
     * This routine returns takes into account the high watermark for hard 
     * drives and makes a basic guess about the 5+2 fragments that the
     * 5800 system uses, it won't be totally accurate but it will give a 
     * good indication of the amount of space available. This adjustment
     * has been requested by marketing.  If stores are blocked by the
     * system the routine will return 0 for estimated disk space
     * @return String MBybtes of available space adjusted for system use.
     * @throws MgmtException
     */
    public String getEstimatedFreeDiskSpace() throws MgmtException {
	
	if (LayoutProxy.getBlockStores()) {
	    // If stores are blocked then report 0 for the estimated
	    // free disk space
	    return "0";
	}
	
	double highWatermark = 80;  	
	try { 	
	    highWatermark = Double.parseDouble( 	
		config.getProperty(ConfigPropertyNames.PROP_DISK_USAGE_CAP));
	} catch (NumberFormatException e) { 	
	    logger.log(Level.SEVERE, "Badly formatted number in " 
		+ ConfigPropertyNames.PROP_DISK_USAGE_CAP); 	
	} catch (NullPointerException npe) {
	    logger.log(Level.SEVERE, "No value specified for property "
		+ ConfigPropertyNames.PROP_DISK_USAGE_CAP);
	}
	long total = getTotalCapacity();
	long used = getUsedCapacity();
	
	// Adjust for high watermarks for hard drives
	// and make a basic guess about the 5+2 fragments that
	// the system uses.
	double adjustedTotal = total * highWatermark/100;
	double adjustment = (adjustedTotal - used) * ((double)5)/7;
	if (adjustment < 0)
	    adjustment = 0;
        return Double.toString(adjustment);
    }
    



    /**
     * @return Long the total capacity of all enabled disks on the cell.
     * Size is expressed in MB
     * @throws MgmtException
     */
    public Long getTotalCapacity() throws MgmtException {

        long totalCapacity = 0;
        for (int i = 0; i < disks.length; i++) {
            if (disks[i] != null && disks[i].getStatus() == Disk.ENABLED) {
                totalCapacity += disks[i].getDiskSize();
            }
        }
        return new Long(totalCapacity);
    }

    /**
     * @return Long the used capacity of all enabled disks on the cell.
     * Size is expressed in MB
     * @throws MgmtException
     */
    public Long getUsedCapacity() throws MgmtException {
        long usedCapacity = 0;
        for (int i = 0; i < disks.length; i++) {
            if (disks[i] != null && disks[i].getStatus() == Disk.ENABLED) {
                usedCapacity += disks[i].getUsedSize();
            }
        }
        return new Long(usedCapacity);
    }




}


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
import com.sun.honeycomb.multicell.SpaceRemaining;

//
// codecs - encode/decode support
//
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.net.QuotedPrintableCodec;

public  class HCDisksAdapter implements HCDisksAdapterInterface{

    private static transient final Logger logger = 
      Logger.getLogger(HCDisksAdapter.class.getName());
    private Disk []              disks;

    public void loadHCDisks()
        throws InstantiationException {
        
    }


    public void populateDisksList(List<HCDisk>  array) throws MgmtException {
        array.clear();
        array.addAll(ValuesRepository.getInstance().getDisks().getDisksList());
    }

    //
    // placeholder for mgmt
    //
    public void setDisksList(List<HCDisk> ignore) throws MgmtException {
    }

    public String getEstimatedFreeDiskSpace() throws MgmtException {
	double total = getTotalCapacity().doubleValue() * .80;
	double adjustedTotal = (total - getUsedCapacity().doubleValue()) * 5/7;
	if (adjustedTotal < 0)
	    adjustedTotal = 0;
	return Double.toString(adjustedTotal);
    }

    



    /**
     * @return Long the total capacity of all enabled disks on the cell.
     * Size is expressed in TB
     * @throws MgmtException
     */
    public Long getTotalCapacity() throws MgmtException {        
        return SpaceRemaining.getInstance().getTotalCapacityBytes()/(1024*1024);

    }



    /**
     * @return Long the used capacity of all enabled disks on the cell.
     * Size is expressed in TB
     * @throws MgmtException
     */
    public Long getUsedCapacity() throws MgmtException {
        return SpaceRemaining.getInstance().getUsedCapacityBytes()/(1024*1024);
    }







}


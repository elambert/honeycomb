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

import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagData;
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagCellData;


/**
 * Fetches the data associated with a single cell.
 * 
 * Ideally we would have extended HCServiceTagCellData instead of ServiceTagData 
 * but the mof compiler doesn't generate the server mof file properly
 * for sint8 types
 */
public class HCServiceTagCellDataAdapter
extends ServiceTagData
implements HCServiceTagCellDataAdapterInterface {
   
    private Byte cellId; 
    
    public void loadHCServiceTagCellData()
    throws InstantiationException {
        ServiceTagCellData cellEntry = MultiCellLib.getInstance().getServiceTagCellData();
        ServiceTagData data = cellEntry.getServiceTagData();
        setCellId(cellEntry.getCellId());
        setMarketingNumber(data.getMarketingNumber());
        setProductNumber(data.getProductNumber());
        setProductSerialNumber(data.getProductSerialNumber());
        setInstanceURN(data.getInstanceURN());
    }

    public Byte getCellId() {
	return cellId;
    }

    public void setCellId(Byte cellId) {
	this.cellId = cellId;
    }
}


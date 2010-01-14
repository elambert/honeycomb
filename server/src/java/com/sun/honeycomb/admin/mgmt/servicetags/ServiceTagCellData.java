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


package com.sun.honeycomb.admin.mgmt.servicetags;

import com.sun.honeycomb.multicell.lib.*;

/**
 * This object represent the service tag data for a given cell
 */
public class ServiceTagCellData {

    private Byte cellId;
    private ServiceTagData payload;
    
    public ServiceTagCellData() {
        super();
    }
    
    /**
     * @param cellId the cell id that the service tag <code>data</code>
     * corresponds to
     * @param data the service tag data.
     */
    public ServiceTagCellData(byte cellId, ServiceTagData data) {
        this(Byte.valueOf(cellId), data);
    }
    
    /**
     * @param cellId the cell id that the service tag <code>data</code>
     * corresponds to
     * @param data the service tag data.
     */
    public ServiceTagCellData(Byte cellId, ServiceTagData data) {
        this.cellId = cellId;
        this.payload = data;
    }
    
    /**
     * @return byte the cell id that this service tag data contained in this
     * object corresponds to
     */
    public Byte getCellId() {
        return cellId;
    }
    
    /**
     * @param cellId the cell id that this service tag data contained in this
     * object corresponds to
     */
    public void setCellId(Byte cellId) {
        this.cellId = cellId;
    }
    
    /**
     * Get the service tag data that corresponds to the cell
     * @return ServiceTagData the service tag data that corresponds to the cell
     */
    public ServiceTagData getServiceTagData() {
        return payload;
    }
    
    /**
     * Set the service tag data that corresponds to the cell
     * @param payload the service tag data that corresponds to the cell
     */
    public void setServiceTagData(ServiceTagData payload) {
        this.payload = payload;
    }
            
    /**
     * Indicates whether some other object is "equal to" this one.
     * @param data the object to compare
     * @return boolean, true if objects are equal, false otherwise
     */
    public boolean equals(ServiceTagCellData data) {
        if (getCellId().equals(data.getCellId()) == false)
            return false;
        return payload.equals(data.getServiceTagData());
    }
}

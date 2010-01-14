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

import com.sun.honeycomb.multicell.mgmt.client.HCServiceTagInfo;
import java.io.Serializable;

public class ServiceTagData implements Serializable {
    
    private String productNumber;
    private String productSerialNumber;
    private String marketingNumber;
    private String instanceURN;

    /**
     * Create a new service tag data object
     */
    public ServiceTagData() {
        // All values are empty string
        this.productNumber = "";
        this.productSerialNumber = "";
        this.marketingNumber = "";
        this.instanceURN = "";
    }
    
    /**
     * Create a new service tag data object
     * @param productNumber
     * @param productSerialNumber
     * @param marketingNumber
     * @param instanceURN
     */
    public ServiceTagData(
            String productNumber, 
            String productSerialNumber, 
            String marketingNumber) {
        this(productNumber, productSerialNumber, marketingNumber, null);
    }
    
    /**
     * Create a new service tag data object
     * @param productNumber
     * @param productSerialNumber
     * @param marketingNumber
     * @param instanceURN
     */
    public ServiceTagData(
            String productNumber, 
            String productSerialNumber, 
            String marketingNumber,
            String instanceURN) {
        if (productNumber == null) {
            productNumber = "";
        }
        if (productSerialNumber == null) {
            productSerialNumber = "";
        }
        if (marketingNumber == null) {
            marketingNumber = "";
        }
        this.productNumber = productNumber;
        this.productSerialNumber = productSerialNumber;
        this.marketingNumber = marketingNumber;
        this.instanceURN = instanceURN;
    }

    public ServiceTagData(ServiceTagData copy) {
        this.productNumber = copy.getProductNumber();
        this.productSerialNumber = copy.getProductSerialNumber();
        this.marketingNumber = copy.getMarketingNumber();
        this.instanceURN = copy.getInstanceURN();
    }
    
    /**
     * Clear the instanceURN.  This field should be cleared when ever any field
     * associated with this object changes. 
     * <P>
     * When cleared this field will get updated with a instanceURN when
     * the service tag registry entries associated with this object are
     * updated.
     */
    public void clearInstanceURN() {
        instanceURN = null;
    }
    
    /**
     * @return String the instanceURN of the service tag registry entry
     * this information corresponds to.  A null values indicates 
     * the service tag registry has not been populated.
     */
    public String getInstanceURN() {
        return instanceURN;
    }
    
    /**
     * Set the instanceURN of the service tag registry entry this information
     * corresponds to.
     * @param the instanceURN
     */
    public void setInstanceURN(String instanceURN) {
        this.instanceURN = instanceURN;
    }
  
    /**
     * @return String the Marketing number for the top level assembly # that
     * the cell resides in.
     */
    public String getMarketingNumber() {
        return marketingNumber;
    }
    
    /**
     * Set the Marketing number for the top level assembly # that
     * the cell resides in.  Clears the instanceURN field as a side effect to 
     * indicate that a change to this object has occurred.
     * @param the marketing number
     */
    public void setMarketingNumber(String marketingNumber) {
        this.marketingNumber = marketingNumber;
        clearInstanceURN();
    }
    
    /**
     * @return String the Manufacturing product number for the top level 
     * assembly # that cells resides in.
     */
    public String getProductNumber() {
        return productNumber;
    }
    
    /**
     * Set the product manufacturing number that the cell resides in
     * Clears the instanceURN field as a side effect to indicate that
     * a change to this object has occurred.
     * @param productNumber the Manufacturing product number
     */
    public void setProductNumber(String productNumber) {
        this.productNumber = productNumber;
        clearInstanceURN();
    }
    
    /**
     * @return String the product serial # that the cell resides in.  Today 
     * this corresponds to the top level assembly #.
     */
    public String getProductSerialNumber() {
        return productSerialNumber;
    }
    
    /**
     * Set the product serial # that the cell resides in.
     * Clears the instanceURN field as a side effect to indicate that
     * a change to this object has occurred.
     * @param productSerialNumber the product serial #
     */
    public void setProductSerialNumber(String productSerialNumber) {
        this.productSerialNumber = productSerialNumber;
        clearInstanceURN();
    }
    
    
    /**
     * Compare whether <code>data</code> is equal to this object.
     * @param data the service tag data object to compare
     * @return boolean true if equal, false otherwise
     */
    public boolean equals(ServiceTagData data) {
        if (productNumber != data.getProductNumber()) {
            return false;
        }
        if (productSerialNumber != data.getProductSerialNumber()) {
            return false;
        }
        if (marketingNumber != data.getMarketingNumber()) {
            return false;
        }
        return (instanceURN == data.getInstanceURN());
    }

    /**
     * @return String a printable representation of this object
     */
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("instanceURN=").append(getInstanceURN());
        buf.append(", marketingNumber=").append(getMarketingNumber());
        buf.append(", productNumber=").append(getProductNumber());
        buf.append(", productSerialNumber=").append(getProductSerialNumber());
        return buf.toString();
    }
}

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

import org.w3c.dom.Document;

public class HCCellInfoAdapter 
    implements HCCellInfoAdapterInterface {

    public void loadHCCellInfo() 
        throws InstantiationException {

    }

    /*
    * This is the list of accessors to the object
    */
    public Byte getCellid() {
        return new Byte((byte) 1);
    }
    
    public String getDomainName() {
        return "unknown";
    }

    public String getAdminVIP() {
        return "unknown";
    }

    public String getDataVIP() {
        return "unknown";
    }

    public String getSpVIP() {
        return "unknown";
    }

    public String getSubnet() {
        return "unknown";
    }

    public String getGateway() {
        return "unknown";
    }

    public Long getTotalCapacity() {
        return new Long(1000);
    }

    public Long getUsedCapacity() {
        return new Long(1000);
    }

    public void populateRules(List<HCRule> array) {
        HCRule rule = new HCRule();
        rule.setRuleNumber((byte) 5);
        array.add(rule);
    }


    public Byte checkSchema(Document doc) {
        System.out.println("check schema: received xml document ...");
        return new Byte((byte)0);        
    }

    public Byte checkConfig(HCSiloProps props) {
        return new Byte((byte)0);	
    }

    public Byte pushInitConfig(HCSiloInfo siloInfo, Long version) {
        System.out.println("pushInitConfig");
        return new Byte((byte)0);
    }

    public Byte addCell(HCCellInfo cellInfo, Long version) {
        System.out.println("addCell");        
        return new Byte((byte)0);
    }

    public Byte delCell(Byte cellid, Long version) {
        System.out.println("delCell");        
        return new Byte((byte)0);
    }

}

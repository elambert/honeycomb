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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.sun.honeycomb.mgmt.common.MgmtException;

public class HCFruAdapter implements HCFruAdapterInterface {

    protected List<HCFru> frus;
    
    public void loadHCFru()
        throws InstantiationException {
        frus = ValuesRepository.getInstance().getFrus().getFrusList();
    }

    /*
    * This is the list of accessors to the object
    */
    public BigInteger getFruType() throws MgmtException {
        return BigInteger.valueOf(1);
    }

    public String getTypeStr() throws MgmtException {
        return "fru-type";
    }

    public String getFruName() throws MgmtException {
        return "fru-name";
    }

    public String getFruId() throws MgmtException {
        return "fru-XXXX";
    }

    public BigInteger getStatus() throws MgmtException {
        return BigInteger.valueOf(1);
    }
}

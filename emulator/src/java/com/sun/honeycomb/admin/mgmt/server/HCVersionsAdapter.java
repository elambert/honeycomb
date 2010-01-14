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

import java.util.List;

import com.sun.honeycomb.mgmt.common.MgmtException;

public class HCVersionsAdapter
        implements HCVersionsAdapterInterface {
    
    static final private String HC_VERSION_FILE  = "/opt/honeycomb/version";
    
    int numNodes;
    public void loadHCVersions() throws InstantiationException {
        numNodes = ValuesRepository.getInstance().
                                getNodes().getNumAliveNodes().intValue();
    }
    /*
    * This is the list of accessors to the object
    */

    public String getSpBios() throws MgmtException {
        return ValuesRepository.getInstance().getVersions().getSpBios();
    }    
    public String getSpSmdc() throws MgmtException {
        return ValuesRepository.getInstance().getVersions().getSpSmdc();
    }
    public String getSwitchOneOverlay() throws MgmtException {
        return ValuesRepository.getInstance().
                                        getVersions().getSwitchOneOverlay();
    }
    public String getSwitchTwoOverlay() throws MgmtException {
        return ValuesRepository.getInstance().
                                        getVersions().getSwitchTwoOverlay();
    }
    public void populateBios(List<String> array) throws MgmtException {
        ValuesRepository.getInstance().getNodeBiosList(array);
    }
    public void populateSmdc(List<String> array) throws MgmtException {
        ValuesRepository.getInstance().getNodeSmdcList(array);
    }

    public String getVersion() throws MgmtException {
        return ValuesRepository.getInstance().getVersions().getVersion();
    }
}


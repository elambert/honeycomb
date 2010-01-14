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


package com.sun.honeycomb.admingui.client;

/**
 * encapsulates firmware version information for one cell
 */
public class Versions {

    protected String cellVer, spBios, spSmdc;
    protected String sw1Overlay, sw2Overlay;
    protected String bios[], smdc[];
    
    Versions() {
        cellVer = spBios = spSmdc = sw1Overlay = sw2Overlay = "";
        bios = new String[0];
        smdc = new String[0];
    }

    public Versions(String cellVer, String spBios, String spSmdc,
        String sw1Overlay, String sw2Overlay, String[] bios, String[] smdc) {
        this.cellVer = cellVer;
        this.spBios = spBios;
        this.spSmdc = spSmdc;
        this.sw1Overlay = sw1Overlay;
        this.sw2Overlay = sw2Overlay;
        this.bios = bios;
        this.smdc = smdc;
    }

    // cell version
    public String getCellVer() {
        return cellVer;
    }

    // service node bios
    public String getSPBios() {
        return spBios; 
    }
    // service node smdc
    public String getSPSmdc() {
        return spSmdc;
    }

    // switch overlay
    public String getSwitch1Overlay() {
        return sw1Overlay;
    }
    /** this is the top switch */
    public String getSwitch2Overlay() {
        return sw2Overlay;
    }

    // node bios
    public String[] getBios() {
        return bios;
    }
    // node smdc 
    public String[] getSmdc() {
        return smdc;
    }
}

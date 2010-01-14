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

import com.sun.honeycomb.common.CliConstants;
import com.sun.nws.mozart.ui.utility.GuiResources;
import java.awt.Color;
import java.awt.Image;
import javax.swing.ImageIcon;

/**
 * encapsulates fru info common to all components
 */
public class Fru {    
    public static final int TYPE_SWITCH = CliConstants.HCFRU_TYPE_SWITCH;
    public static final int TYPE_SP     = CliConstants.HCFRU_TYPE_SP;
    public static final int TYPE_DISK   = CliConstants.HCFRU_TYPE_DISK;
    public static final int TYPE_NODE   = CliConstants.HCFRU_TYPE_NODE;
    
    public static final int STATUS_UNAVAILABLE = -1;
    
    protected String objId;  // this is the id for the disk, node, switch, SP
    protected String fru;
    protected int type;
    protected int status;
    protected Cell theCell;
    protected AdminApi api;
    
    public static String getStatusAsString(int type, int status) {
        try {
            if (status == STATUS_UNAVAILABLE) {
                return GuiResources.getGuiString("cellProps.unavailable");
            }
            if (type == TYPE_NODE  || type == TYPE_SP) {
                return CliConstants.HCNODE_STATUS_STR[status];
            }
            if (type == TYPE_SWITCH) {
                return CliConstants.HCSWITCH_STATUS_STR[status];
            }
            return CliConstants.HCFRU_STATUS_STR[status];
        } catch (ArrayIndexOutOfBoundsException ae) {
            return CliConstants.HCFRU_UNKNOWN_STR;
        }
    }
    
    /** Creates a new instance */
    Fru(AdminApi api, Cell c, String id, int type, int status, String fru) {
        this.api = api;
        this.theCell = c;
        this.objId = id;
        this.type = type;
        this.status = status;
        this.fru = fru;
    }

    public Cell getCell() { return theCell; }
    public String getID() { return objId; }
    public int getType() { return type; }
    public int getStatus() { return status; }
    public String getFRU() { return fru; }

    public String toString() {
        return "fru{" + objId + ",type=" + type + ",fru=" + fru + "/" +
               ",status=" + String.valueOf(status) + ",cell=" + "/" +
                String.valueOf(theCell.getID()) + "}";
    }
}

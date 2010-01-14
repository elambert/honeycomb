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



package com.sun.honeycomb.multicell;

import java.util.List;
import java.io.IOException;

import com.sun.honeycomb.cm.EmulatedService;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.cm.ManagedServiceException;


public interface MultiCellIntf extends EmulatedService
{

    public CellInfo getCellInfo()
        throws IOException, ManagedServiceException;

    //
    // CLI
    //
    public byte addCellStart(String adminVIP, String dataVIP)
        throws IOException, MultiCellException, ManagedServiceException;

    public byte addCellSchemaValidation()
        throws IOException, MultiCellException, ManagedServiceException;

    public byte addCellPropertiesValidation()
        throws IOException, MultiCellException, ManagedServiceException;

    public byte addCellUpdateHiveConfig()
        throws IOException, MultiCellException, ManagedServiceException;

    public void removeCell(byte cellid)
        throws IOException, MultiCellException, ManagedServiceException;


    //
    // Mgmt remote invocation.
    //
    public byte addNewCell(CellInfo cellInfo, long version)
        throws IOException, MultiCellException, ManagedServiceException;        

    public byte rmExistingCell(byte cellid, long version)
        throws IOException, MultiCellException, ManagedServiceException;        

    public byte pushInitConfig(List existingCells, long version)
        throws IOException, MultiCellException, ManagedServiceException;

    public byte updateNewPowerOfTwoConfig(List potCells, long major, long minor)
        throws IOException, MultiCellException, ManagedServiceException;


    public class Proxy {

        static private Proxy proxy = null;

        private MultiCell multicellSvc = null;

        static public MultiCellIntf getMultiCellAPI() {
            return MultiCell.getInstance();
        }

        static public Proxy getProxy() {
            synchronized(Proxy.class) {
                if (proxy == null) {
                    proxy = new Proxy(MultiCell.getInstance());
                }
                return proxy;
            }
        }

        public Proxy(MultiCell multicellSvc) {
            this.multicellSvc = multicellSvc;
        }

        public List getCells() {
            return multicellSvc.getCells(MultiCell.DEEP_COPY, true);            
        }

        public long getMinorVersion() {
            return multicellSvc.getMinorVersion();
        }

        public long getMajorVersion() {
            return MultiCellLib.getInstance().getMajorVersion();
        }
        
    }
}

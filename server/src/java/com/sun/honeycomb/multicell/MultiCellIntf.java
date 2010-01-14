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

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.multicell.lib.Cell;


//
// Interface for MultiCell server
//
public interface MultiCellIntf
    extends ManagedService.RemoteInvocation, ManagedService {

    //
    // debug only
    //
    public List getExistingCells()
        throws IOException, MultiCellException, ManagedServiceException;

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

    public void changeCellCfg(Cell cell)
        throws IOException, MultiCellException, ManagedServiceException;

    //
    // Mgmt remote invocation.
    //
    public CellInfo getCellInfo()
        throws IOException, ManagedServiceException;

    public byte addNewCell(CellInfo cellInfo, long version)
        throws IOException, MultiCellException, ManagedServiceException;        

    public byte rmExistingCell(byte cellid, long version)
        throws IOException, MultiCellException, ManagedServiceException;        

    public byte pushInitConfig(List existingCells, long version)
        throws IOException, MultiCellException, ManagedServiceException;

    public byte updateNewPowerOfTwoConfig(List potCells, long major, long minor)
        throws IOException, MultiCellException, ManagedServiceException;


    public class Proxy extends ManagedService.ProxyObject {

        // Debug only
        static private final String  TEST_FILE_CONFIG = 
          "/tmp/config_client.xml";

        private long versionMajor = 0;
        private long versionMinor = 0;
        private List cells;

        static public MultiCellIntf getMultiCellAPI() {
            Proxy proxy = getProxy();
            if (proxy == null) {
                return null;
            }
            Object api = proxy.getAPI();
            if (! (api instanceof MultiCellIntf)) {
                return null;
            }
            return  (MultiCellIntf) api;
        }

        static public Proxy getProxy() {
            Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            if (! (obj instanceof NodeMgrService.Proxy)) {
                return null;
            }
            NodeMgrService.Proxy nodeMgr = (NodeMgrService.Proxy) obj;
            Node master = ((NodeMgrService.Proxy) obj).getMasterNode();
            if (master == null) {
                return null;
            }
            ManagedService.ProxyObject proxy =
                ServiceManager.proxyFor(master.nodeId(), "MultiCell");
            if (! (proxy instanceof MultiCellIntf.Proxy)) {
                return null;
            }
            return (MultiCellIntf.Proxy) proxy;
        }

        public Proxy(MultiCell multicellSvc) {
            super();
            this.cells = multicellSvc.getCells(MultiCell.DEEP_COPY, true);
            this.versionMajor = MultiCellLib.getInstance().getMajorVersion();
            this.versionMinor = multicellSvc.getMinorVersion();
            //printClientConfig();
        }

        public List getCells() {
            return cells;
        }

        public long getMajorVersion() {
            return versionMajor;
        }

        public long getMinorVersion() {
            return versionMinor;
        }

        private void printClientConfig() {
            if (MultiCellConfig.getLogLevel() > 
                MultiCellLogger.MULTICELL_LOG_LEVEL_DEFAULT) {
                MultiCellLib.getInstance().printClientConfig(TEST_FILE_CONFIG);
            }
        }
    }
}

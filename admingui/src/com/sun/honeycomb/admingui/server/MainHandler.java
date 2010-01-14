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



package com.sun.honeycomb.admingui.server;

import com.sun.honeycomb.common.CliConstants;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.PipedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.BufferedWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.logging.Logger;
import java.util.Vector;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Iterator;
import java.util.Date;
import java.util.Random;
import java.math.BigInteger;
import java.util.logging.Level;

import org.mortbay.http.handler.AbstractHttpHandler;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;

import org.apache.xmlrpc.XmlRpcServer;
//import org.apache.xmlrpc.XmlRpcHandler;

import com.sun.honeycomb.adm.client.AdminClient;
import com.sun.honeycomb.adm.client.AdminClientImpl;
import com.sun.honeycomb.adm.client.ClientUtils;
import com.sun.honeycomb.adm.cli.AdmException;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.common.FruObjects;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.admin.mgmt.client.*;
import com.sun.honeycomb.common.md5.MD5Check;

import com.sun.honeycomb.admingui.XmlRpcParamDefs;
import java.util.ArrayList;

public class MainHandler extends AbstractHttpHandler
    implements XmlRpcParamDefs {
    
    
    private static final Logger LOG =
        Logger.getLogger(MainHandler.class.getName());
    
    // EOF marker for the medatadata configuration file
    private static final String MDCFG_ENDTAG = "</metadataConfig>";

    private static final int MAX_GUI_CLIENTS = 50;
     
    private boolean emulMode = false;
    private XmlRpcServer xmlrpc = null;
    private final static String[] emptyStrArr = new String[0];
    private final static Vector emptyVect = new Vector(0);
    private Hashtable idToFruObjMap = new Hashtable();

    // SIDs that are found to have not been used after MAX_INACTIVITY seconds
    // will be discarded
    static final int MAX_INACTIVITY = 1800; 
    static Random rand = new Random();

    AdminClient newApi() throws MgmtException, ConnectException,
                                PermissionException {
        AdminClient api = new AdminClientImpl();
        if (api != null)
            api = (AdminClient) AdminClientProxy.newProxy(api, LOG); 
        return api;
    }
    
    public MainHandler(boolean emulMode) {
        this.emulMode = emulMode;
        LOG.info("Initializing admingui MainHandler; emulator mode is " +
                (emulMode ? "ON" : "off"));
        xmlrpc = new XmlRpcServer();
        xmlrpc.addHandler("$default", this);
    }

    
    public int identifyRequest(HttpRequest request) throws IOException {
        LOG.info("Content-length: " + request.getContentLength());
        InputStream is = request.getInputStream();
        InputStreamReader rd = new InputStreamReader(is);
        BufferedReader bufReader = new BufferedReader(rd);
        String s = "Request body:";
        while (null != s) {
           System.out.println(s);
           s = bufReader.readLine();
        }
        return 0;
    }

    void logRes(String res) {
        int maxLogmsgLen = 200;
        if (emulMode) {
            maxLogmsgLen = 10000;
            //System.out.print("FULL RESPONSE (debug):");
            //System.out.write(result); // debug
            //System.out.println();
        }
        String logMsg = res.
            replaceFirst("<\\?xml version=\"1.0\"\\?><methodResponse>","gui<=").
            replaceAll("</param><param>",",").
            replaceAll("<array>","[").replaceAll("</array>","]").
            replaceAll("</value><value>",",").
            replaceAll("<struct>","{").replaceAll("</struct>","}").
            replaceAll("</member><member>",",").
            replaceAll("<[^=][^>]*>", "");
        if (logMsg.length() < maxLogmsgLen) {
            LOG.info(logMsg);
        } else {
            LOG.info(logMsg.substring(0, maxLogmsgLen) + "... +" +
                (logMsg.length() - maxLogmsgLen));
        }
    }

    public void handle(final String pathInContext,
                       final String pathParams,
                       final HttpRequest request,
                       final HttpResponse response)
        throws IOException {

        if (pathInContext.equals("/request")) {
            //System.out.println("authType:" + request.getAuthType() + 
            //        " authUser:" + request.getAuthUser());
            
            // first log the incoming call
            AdminGUIMain.logRequest(request);

            // process xml-rpc request. this XML-RPC will map the incoming
            // call to one of the methods defined in the section below
            byte[] result = xmlrpc.execute(request.getInputStream());

            logRes(new String(result));

            // send result back
            request.setHandled(true);
            response.setContentType("text/xml");
            response.setContentLength(result.length);

            OutputStream out = response.getOutputStream();
            out.write(result);
            out.flush();
        }
    }
    
    String[] split(String s) { return (s == null) ? null : s.split("[, ] *");
    }
    String checkNull(String arg) { return (arg == null) ? "" : arg; }
    final String UTF8 = "UTF-8";

    /*
     * XML-RPC methods (public) and related helper methods (non-public).
     * IMPORTANT: method names must match constants defined in XmlRpcParamDefs
     */


    // ------------------- password and session management ---------------------

    /**
     * need one API instance per GUI client instance,
     * since locking for writes is done inside the API layer
     * 
     * SIDs holds (session ID, api) pairs - one pair per GUI client instance
     */
    Hashtable<Integer,AdminClient> SIDs = new Hashtable();
    
    // last access time for each session
    Hashtable<Integer,Date> atime = new Hashtable();

    AdminClient getApi(int sid) { 
        Integer SID = Integer.valueOf(sid);
        AdminClient a = SIDs.get(SID);
        if (a == null)
            throw new RuntimeException(FAULT_INVALID_SID + sid);
        atime.put(SID, new Date());
        return a;
    }

    // return a new non-zero session id
    int newSID() {
        int sid = rand.nextInt();
        if (sid == 0 || SIDs.containsKey(Integer.valueOf(sid)))
            sid = newSID();
        return sid;
    }

    // recycle session ids in case some GUI client(s) did not exit cleanly
    // we don't want to keep around unused AdminClient references.
    // recycle them after SID_RECYCLTIME seconds
    void recycleSIDs() {
        Date crt = new Date();
        long inactiveFor; // seconds

        Iterator<Integer> it = SIDs.keySet().iterator();
        Integer SID;
        while (it.hasNext()) {
            SID = it.next();
            inactiveFor = (crt.getTime() - atime.get(SID).getTime()) / 1000;
            if (inactiveFor > MAX_INACTIVITY) {
                it.remove();
                atime.remove(SID);
                LOG.info("gui: SID " + SID + " recycled - inactive for " + 
                        inactiveFor + "s");
            }        
        }
    }

    /**
     * if (sid == 0) then generate a new session id & validate password,
     * otherwise just validate the password
     *
     * @return 0 if password incorrect or session id if password correct
     */
    public int verifyPasswd(int sid, String passwd)
        throws MgmtException, ConnectException, PermissionException {

        AdminClient api = null;
        // step 1 - obtain api instance
        if (sid == 0) { // new GUI login
            api = newApi();            
        } else {
            api = getApi(sid); // verify that sid is valid
        }

        // step 2 - validate password
        String cryptedPass = api.getCryptedPasswd();
        if (!MD5Check.check(cryptedPass, passwd)) { // wrong password
            LOG.info("gui: Incorrect password provided");
            return 0;
        }
        if (sid != 0) // validation request from existing client
            return sid;

        // step 3 - if new GUI client that provided correct password
        recycleSIDs();
        if (SIDs.size() == MAX_GUI_CLIENTS) // prevent DOS attacks
            throw new RuntimeException("Max. number of GUI clients reached:"
                    + MAX_GUI_CLIENTS);
        sid = newSID();

        SIDs.put(Integer.valueOf(sid), api);
        LOG.info("gui: New client - sid " + sid +
                     ". (total " + SIDs.size() + ")");
        return sid;
    } 

    public int setPasswd(int sid, byte[] cryptedPass)
        throws MgmtException, ConnectException, PermissionException {
        //LOG.info("rcvd.password:" + new String(cryptedPass));
        getApi(sid).setPasswd(new String(cryptedPass));

        return 0;
    }

    public boolean login(int sid)
        throws MgmtException, ConnectException, PermissionException {
        return true;//todo getApi().login();
    }
    public boolean loggedIn(int sid) throws MgmtException, ConnectException,
        PermissionException {
        return getApi(sid).loggedIn();
    }
    public int logout(int sid) throws MgmtException, ConnectException {

        // release locks (if any)
        AdminClient api = null;
        try {
            api = getApi(sid);
        } catch (RuntimeException e) {
            LOG.info("logout request for expired session " + sid + " ignored");
            // is session already expired, don't bother reporting an error
            return 0;
        }
        api.logout();

        SIDs.remove(Integer.valueOf(sid));
        atime.remove(Integer.valueOf(sid));
        return 0;
    }

    // ---------------------------------------------------------------

    public String getAPIVersion(int sid) {
        return APIVER;
    }

    public int getNumOfUnhealedFailures(int sid, int cellId)
        throws MgmtException, ConnectException {
        try {
            return getApi(sid).getUnhealedFailures((byte)cellId);
        } catch (MgmtException e) {
            LOG.warning("Can't get unhealed failures - "+
                           "data services likely offline" + 
                           e.getMessage());
            return -1;
        }
    }
    public boolean hasQuorum(int sid, int cellId)
        throws MgmtException, ConnectException {
        return getApi(sid).hasQuorum((byte)cellId);
    }

    public boolean getSwitchesOk(int sid, int cellId)
        throws MgmtException, ConnectException {
        return getApi(sid).getSwitchesState((byte)cellId);
    }
    public Vector getCachedCells(int sid) throws MgmtException, ConnectException {
        if (!hasCachedCells(sid)) {
            return getCells(sid);
        } else {
            Enumeration e = idToFruObjMap.keys();
            Vector v = new Vector();
            while (e.hasMoreElements()) {
                FruObjects fObjs = (FruObjects) idToFruObjMap.get(e.nextElement());
                Integer cId = new Integer(fObjs.getCellId());
                Hashtable cInfo = new Hashtable();
                cInfo.put(ID, cId);
                cInfo.put(NUM_NODES, new Integer(fObjs.getNodes().length));
                String estFree = getApi(sid).getFreeDiskSpace(cId.byteValue()) + "";
                cInfo.put(ALIVE, fObjs.isCellAlive());
                if (estFree == null)
                    estFree = "-1";
                cInfo.put(FREE, estFree);
                v.add(cInfo);
            } // end while
            return v;
        } // end else
    }
    public boolean hasCachedCells(int sid) throws MgmtException, ConnectException {
        return !idToFruObjMap.isEmpty();
    }
    public int getNumCells(int sid) throws MgmtException, ConnectException {
        return getApi(sid).getNumCells();
    }
    public Vector getCells(int sid) throws MgmtException, ConnectException {
        Hashtable tmpHashTbl = new Hashtable();
        HCCell[] cells = getApi(sid).getCells(true);
        Vector v = new Vector(cells.length);
        for (int i = 0; i < cells.length; i++) {  
            Integer cellId = new Integer(cells[i].getCellId());
            HCFru[] frus = null;
            boolean cellAlive = cells[i].isIsAlive();
            int numNodes = 16;
            String estFree = null;
            if (cellAlive) {
                HCFrus fruObj = null;
                try {
                    fruObj = getApi(sid).getFRUs(cells[i].getCellId());
                    frus = (HCFru [])fruObj.getFrusList().
                        toArray(new HCFru[fruObj.getFrusList().size()]);

                    numNodes = cells[i].getNumNodes().intValue();
                    estFree = getApi(sid).getFreeDiskSpace(
                                            cells[i].getCellId()) + "";
                } catch (Exception e) {
                    LOG.warning("Error retrieving FRU information" + 
                             " on CELL-" + String.valueOf(cellId.intValue()) +
                                " -- marking cell as down in GUI. Error msg = "
                                + e.getMessage());
                    cellAlive = false;
                }
            } else {
                LOG.warning("CELL-" + String.valueOf(cellId.intValue()) + 
                                                                    " is down");
            }
            FruObjects fruObjs = 
                    new FruObjects(frus, cellId.intValue(), cellAlive);
            tmpHashTbl.put(cellId, fruObjs);
            
            Hashtable cellInfo = new Hashtable();
            cellInfo.put(ID, cellId);
            cellInfo.put(NUM_NODES, new Integer(numNodes));            
            cellInfo.put(ALIVE, cellAlive);
            cellInfo.put(FREE, estFree != null ? estFree : "-1");
            v.add(cellInfo);
        }
        /**
         * IMPORTANT:
         * This should be the ONLY method that clears or populates the 
         * idToFruObjMap -- its purpose is to improve performance based on
         * the amount of time the adm calls take to return
         */
        synchronized(this) {
            idToFruObjMap.clear();
            for (int idx = 0; idx < tmpHashTbl.size(); idx++) {
                Enumeration e = tmpHashTbl.keys();
                while (e.hasMoreElements()) {
                    Integer id = (Integer)e.nextElement();
                    idToFruObjMap.put(id, tmpHashTbl.get(id));
                }
            }
        }
        return v;
    }
    static Hashtable emptyNode = new Hashtable();
    static {
        emptyNode.put(ID, "");
        emptyNode.put(ALIVE, Boolean.FALSE);
        emptyNode.put(FRU, "");
        emptyNode.put(STATUS, new Integer(CliConstants.HCNODE_STATUS_OFFLINE));
        emptyNode.put(DISKS, new Vector(0));
    }
    private Hashtable _getNode(int sid, byte cellId, HCNode n)
        throws MgmtException, ConnectException {
        if (n == null) {
            LOG.warning("getNode returned null");
            return emptyNode;
        }
        Hashtable nodeInfo = new Hashtable(); // info for one node
        BigInteger idObj = n.getNodeId();
        if (idObj == null) {
            LOG.warning("null nodeid");
            idObj = new BigInteger("0");
        }
        int id = idObj.intValue();
        nodeInfo.put(ID, new Integer(id));
        nodeInfo.put(ALIVE, new Boolean(n.isIsAlive()));
        nodeInfo.put(STATUS, new Integer(n.getStatus().intValue()));
        String fru = n.getFruId();
        nodeInfo.put(FRU, (fru == null) ? "n/a" : fru);
        nodeInfo.put(DISKS, (id == 0) ? emptyVect : getDisks(sid, cellId, id));
        return nodeInfo;
    }
    public Hashtable getNode(int sid, int cellId, int nodeid)
        throws MgmtException, ConnectException {
        HCNode node = null;
        try {
            node = getApi(sid).getNode((byte)cellId, nodeid);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "exception thrown by AdminClient.getNode("
                    + cellId + "," + nodeid + ")", e.fillInStackTrace());
        }
        return _getNode(sid, (byte)cellId, node);
    }
    public Vector getNodes(int sid, int cellId) throws MgmtException, ConnectException {
        HCNode[] nodes = null;
        FruObjects fruHelper = null;
        Integer cId = new Integer(cellId);
        if (idToFruObjMap == null || idToFruObjMap.isEmpty()) {
            try {
                HCNodes nodeObj = getApi(sid).getNodes((byte)cellId);      
                nodes = (HCNode [])nodeObj.getNodesList().
                    toArray(new HCNode[nodeObj.getNodesList().size()]);
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "exception thrown by AdminClient.getNodes("
                        + cellId + ")", e.fillInStackTrace());
            }
        } else {
            nodes = ((FruObjects)idToFruObjMap.get(cId)).getNodes();
        }
        if (nodes == null) {
            LOG.severe("getNodes(" + cellId + ") returned null");
            return emptyVect;
        }
        Vector v = new Vector(nodes.length);
        try {
            for (int n = 0; n < nodes.length; n++)
                v.add(_getNode(sid, (byte)cellId, nodes[n]));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "exception thrown in _getNode ", e.fillInStackTrace());
        }
        return v;
    }

    private final int NODEID_GET_ALL = -1;

    private Vector _getDisks(AdminClient api, byte cellId, int nodeid)
        throws MgmtException, ConnectException {
        HCDisk[] disks = null;
        try{ 
            if (idToFruObjMap == null || idToFruObjMap.isEmpty()) {
                try {
                    if (nodeid==-1) {
                        HCDisks diskObj = api.getDisks(cellId);      
                        disks = (HCDisk [])diskObj.getDisksList().
                            toArray(new HCDisk[diskObj.getDisksList().size()]);
                    } else {
                        disks = api.getDisksOnNode(cellId, nodeid);
                    }

                } catch (RuntimeException e) {
                    LOG.log(Level.SEVERE, "exception thrown by getDisks("
                            + cellId + ")", e.fillInStackTrace());
                }
            } else {
                disks = (nodeid == -1) ? 
                        ((FruObjects)idToFruObjMap.get(
                            new Integer(cellId))).getDisks() :
                              ((FruObjects)idToFruObjMap.get(
                                new Integer(cellId))).getDisksOnNode(nodeid);
            }
            
        } catch (Exception e) {
            LOG.severe("unexpected exception in _getDisks(cell" +
                    cellId + ",node" + nodeid + "): " + e);
        }
        if (disks == null) {
            LOG.warning("null disk array returned by _getDisks(cell" +
                    cellId + ",node" + nodeid);
            return emptyVect;
        }
        return buildVectDiskInfo(disks, cellId, nodeid);
    }
    private Vector _getDisksOnNode(AdminClient api, byte cellId, int nodeid)
        throws MgmtException, ConnectException {
        HCDisk[] disks = null;
        try{ 
            try {
                disks = api.getDisksOnNode(cellId, nodeid);
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "exception thrown by getDisksOnNode("
                        + cellId + ")", e.fillInStackTrace());
            }
        } catch (Exception e) {
            LOG.severe("unexpected exception in _getDisksOnNode(cell" +
                    cellId + ",node" + nodeid + "): " + e);
        }
        if (disks == null) {
            LOG.warning("null disk array returned by _getDisksOnNode(cell" +
                    cellId + ",node" + nodeid);
            return emptyVect;
        }
        return buildVectDiskInfo(disks, cellId, nodeid);
    }
    private Vector buildVectDiskInfo(HCDisk[] disks, byte cellId, int nodeid) {
        Vector v = new Vector(disks.length);
        for (int d = 0; d < disks.length; d++) {
            Hashtable diskInfo = new Hashtable(); // info for one disk
            if (disks[d] == null) {
                LOG.warning("null disk obj inside array. buildVectDiskInfo(" +
                        cellId + "," + nodeid + ")");
                continue;
            }
            String id = disks[d].getDiskId();
            diskInfo.put(DISKID, (id == null) ? "n/a" : id);
            diskInfo.put(STATUS, new Integer(disks[d].getStatus().intValue()));
            // all values form Disk[] seem to be in MB, despite method names
            diskInfo.put(CAPUSED,
                new Integer((int)(disks[d].getUsedCapacity()))); // MB?
            diskInfo.put(CAPTOTAL,
                new Integer((int)(disks[d].getTotalCapacity()))); // MB?
            String fru = disks[d].getFruId();
            diskInfo.put(FRU, (fru == null) ? "n/a" : fru);
            v.add(diskInfo);
        }
        return v;
    }
    public Vector getDisks(int sid, int cellId)
        throws MgmtException, ConnectException {
        return _getDisks(getApi(sid), (byte)cellId, NODEID_GET_ALL);
    }
    public Vector getDisks(int sid, int cellId, int nodeid)
        throws MgmtException, ConnectException {
        return _getDisks(getApi(sid), (byte)cellId, nodeid);
    }
    public Vector getDisksOnNode(int sid, int cellId, int nodeid)
        throws MgmtException, ConnectException {
        return _getDisksOnNode(getApi(sid), (byte)cellId, nodeid);
    }
    static Hashtable emptySp = new Hashtable();
    static {
        emptySp.put(ID, "SP");
        emptySp.put(ALIVE, Boolean.FALSE);
        emptySp.put(FRU, "SP_ID");
        emptySp.put(STATUS, new Integer(CliConstants.HCNODE_STATUS_OFFLINE));
    }
    private Hashtable _getSp(int sid, byte cellId, HCSP sp)
        throws MgmtException, ConnectException {
        Boolean alive = Boolean.FALSE;
        if (sp == null) {
            LOG.warning("getSp returned null");
            return emptySp;
        }
        Hashtable spInfo = new Hashtable();
        spInfo.put(ID, sp.getFruName());
        int spStatus = sp.getStatus().intValue();
        if (spStatus == CliConstants.HCNODE_STATUS_ONLINE) {
            alive = Boolean.TRUE;
        }
        spInfo.put(ALIVE, alive);
        spInfo.put(STATUS, new Integer(spStatus));
        String fruId = sp.getFruId();
        spInfo.put(FRU, (fruId == null) ? "n/a" : fruId);

        return spInfo;
    }
    public Hashtable getSp(int sid, int cellId) 
        throws MgmtException, ConnectException {
        HCSP fru = null;
        try {
            fru = getApi(sid).getSp((byte)cellId);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "exception thrown by AdminClient.getSp("
                    + cellId + ")", e.fillInStackTrace());
        }
        return _getSp(sid, (byte)cellId, fru);
    }
    private Hashtable _getSwitch(int sid, byte cellId, HCSwitch s)
        throws MgmtException, ConnectException {
        
        Hashtable switchInfo = new Hashtable();
        try {
            // switch name and fru name are identical
            String fruID = s.getFruId();
            fruID = fruID == null ? "n/a" : fruID;
            String name = s.getFruName();
            name = name == null ? "n/a" : name;
            BigInteger status = s.getStatus();
            int switchStatus = status == null ? -1 : status.intValue();
            String version = s.getVersion();
            version = version == null ? "n/a" : version;
            switchInfo.put(ID, name);
            switchInfo.put(STATUS, switchStatus);
            switchInfo.put(VER, version);
            switchInfo.put(FRU, fruID);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "_getSwitch for cell = " + cellId +
                    " threw an exception --", e.fillInStackTrace());
        }
       
        return switchInfo;
    }
     public Vector getSwitches(int sid, int cellId) 
                    throws MgmtException, ConnectException {
        HCSwitch[] switches = null;
        try {
            switches = getApi(sid).getSwitches((byte)cellId);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "exception thrown by AdminClient.getSwitches("
                    + cellId + ")", e.fillInStackTrace());
        }
        if (switches == null) {
            LOG.log(Level.SEVERE, "getSwitches(" + cellId + ") returned null");
            return emptyVect;
        }
        Vector v = new Vector(switches.length);
        try {
            for (int idx = 0; idx < switches.length; idx++) {
                v.add(_getSwitch(sid, (byte)cellId, switches[idx]));
            }
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "exception thrown in _getSwitch ", 
                                                e.fillInStackTrace());
        }
        return v;
    }
    public Hashtable getSwitch(int sid, int cellId, String switchid)
        throws MgmtException, ConnectException {
        // the switchid is the name of the switch (e.g. SWITCH-1 or SWITCH-2)
        HCSwitch fru = null;
        try {
           fru = getApi(sid).getSwitch((byte)cellId, switchid); 
        } catch (RuntimeException re) {
           LOG.log(Level.SEVERE, "exception thrown by AdminClient.getSwitch("
                    + cellId + "," + switchid + ")", re.fillInStackTrace()); 
        }
        return _getSwitch(sid, (byte)cellId, fru);
    }
    public Vector getServices(int sid, int cellId, int nodeid)
        throws MgmtException, ConnectException {
/*
        Service[] svcs = new Service[0];
        //    api.getServices((byte)cellId, nodeid);
        if (svcs == null)
            return emptyVect;
        Vector v = new Vector(svcs.length);
        for (int i = 0; i < svcs.length; i++) {
            Hashtable svcInfo = new Hashtable(); // info for service i
            svcInfo.put(SVCNAME, svcs[i].getName());
            svcInfo.put(ALIVE, new Boolean(svcs[i].isRunning()));
            v.add(svcInfo);
        }
        return v;
 */
        return emptyVect;
    }
    public Hashtable getSensors(int sid, int cellId, int nodeid)
        throws MgmtException, ConnectException {
        Hashtable sens = new Hashtable();
        HCSensor s = getApi(sid).getSensors((byte)cellId, nodeid);

        sens.put(ID, s.getNodeid());
        // values currently include units (e.g "2 volts" etc)
        sens.put(DDR_V, s.getDdrVoltage());
        sens.put(CPU_V, s.getCpuVoltage());
        sens.put(MB3V_V, s.getThreeVCC());
        sens.put(MB5V_V, s.getFiveVCC());
        sens.put(MB12V_V, s.getTwelveVCC());
        sens.put(BAT_V, s.getBatteryVoltage());
        sens.put(CPU_T, s.getCpuTemperature());
        sens.put(SYS_T, s.getSystemTemperature());
        sens.put(CPU_F, s.getCpuFanSpeed());
        sens.put(SYSF1_F, s.getSystemFan1Speed());
        sens.put(SYSF2_F, s.getSystemFan2Speed());
        sens.put(SYSF3_F, s.getSystemFan3Speed());
        sens.put(SYSF4_F, s.getSystemFan4Speed());
        sens.put(SYSF5_F, s.getSystemFan5Speed());
        return sens;
    }
    public Hashtable getVersions(int sid, int cellid)
        throws MgmtException, ConnectException {
        Hashtable vers = new Hashtable();
        HCVersions fwver = getApi(sid).getVersions((byte)cellid);
        vers.put(VER, checkNull(fwver.getVersion()));
        vers.put(SPBIOS, checkNull(fwver.getSpBios()));
        vers.put(SPSMDS, checkNull(fwver.getSpSmdc()));
        vers.put(SW1OVRLAY, checkNull(fwver.getSwitchOneOverlay()));
        vers.put(SW2OVRLAY, checkNull(fwver.getSwitchTwoOverlay()));
        List<String> lst;
        lst = fwver.getBios();
        vers.put(BIOS, (lst == null) ? emptyVect : new Vector(lst));
        lst = fwver.getSmdc();
        vers.put(SMDC, (lst == null) ? emptyVect : new Vector(lst));
        return vers;
    }
    
    public Hashtable getCellProps(int sid, int cellId)
        throws MgmtException, ConnectException {
        Hashtable h = new Hashtable();
        HCCellProps cp = getApi(sid).getCellProps((byte)cellId);
        
        // in the case of using the management emulator, the VIPs contain the
        // port number in the format of ip:port
        h.put(ADMIP,  removePortFromVIP(checkNull(cp.getAdminVIP())));
        h.put(DATAIP, removePortFromVIP(checkNull(cp.getDataVIP())));
        h.put(SPIP,   removePortFromVIP(checkNull(cp.getSpVIP())));
        h.put(GTWYIP, removePortFromVIP(checkNull(cp.getGateway())));
        h.put(SUBNET, removePortFromVIP(checkNull(cp.getSubnet())));
        return h;
    }
    
    private String removePortFromVIP(String vip) {
        String[] strArray = vip.split(":");
        return strArray[0];
    }

    public String getALL() {
        return ALL;
    }

    public int setCellProps(int sid, int cellId, Hashtable newCP)
        throws MgmtException, ConnectException, PermissionException  {
        HCCellProps cp = getApi(sid).getCellProps((byte)cellId);
        if (cp != null && newCP != null){
            Object ip = newCP.get(ADMIP);
            if (ip != null)
                cp.setAdminVIP((String)ip);
            ip = newCP.get(DATAIP);
            if (ip != null)
                cp.setDataVIP((String)ip);
            ip = newCP.get(SPIP);
            if (ip != null)
                cp.setSpVIP((String)ip);
            getApi(sid).setCellProps((byte)cellId, cp);
        }
        return 0;
    }

    public String getAdminIP(int sid, int cellId)
        throws MgmtException, ConnectException {
        return getApi(sid).getCellProps((byte)cellId).getAdminVIP();
    }    
    public String getDataIP(int sid, int cellId)
        throws MgmtException, ConnectException {
        return getApi(sid).getCellProps((byte)cellId).getDataVIP();
    }   
    public String getServiceNodeIP(int sid, int cellId)
        throws MgmtException, ConnectException {
        return getApi(sid).getCellProps((byte)cellId).getSpVIP();
    }

    public int powerNodeOn(int sid, int cellId, int nodeid)
        throws MgmtException, ConnectException, PermissionException {
        getApi(sid).powerNodeOn((byte)cellId, nodeid);
        return 0;
    }
    public int powerNodeOff(int sid, int cellId, int nodeid)
        throws MgmtException, ConnectException, PermissionException {
        getApi(sid).powerNodeOff((byte)cellId, nodeid, false);
        return 0;
    }
    public int rebootNode(int sid, int cellId, int nodeid)
        throws MgmtException, ConnectException, PermissionException {
        getApi(sid).rebootNode((byte)cellId, nodeid);
        return 0;
    }
    public int rebootCell(int sid, int cellId, boolean switches, boolean sp)
        throws MgmtException, ConnectException, AdmException, PermissionException {
        
        AdminClient api = getApi(sid);
        if (cellId == -1) { // reboot all cells
            HCCell[] cells = api.getCells(true);
            byte masterId = api.getMasterCellId();
            for (HCCell cell : cells) {
                if (cell.getCellId() != masterId && cell.isIsAlive())
                    api.rebootCell(cell, switches, sp);
            }
            api.rebootCell(masterId, switches, sp);

        } else
            api.rebootCell((byte)cellId, switches, sp);
        return 0;
    }
    public int powerCellOff(int sid, int cellId, boolean switches, boolean sp)
        throws MgmtException, ConnectException, AdmException, PermissionException {
        getApi(sid).powerOff((byte)cellId,  switches, false); // don't use ipmi
        return 0;
    }
    public int wipeDisks(int sid) throws MgmtException, ConnectException, 
            AdmException, PermissionException {
        return getApi(sid).wipeDisks();
    }
    
    public int addCell(int sid, String admIP, String dataIP)
        throws MgmtException, ConnectException, PermissionException {
        return getApi(sid).addCell(admIP, dataIP);
    }
    
    public int delCell(int sid, int cellId)
        throws MgmtException, ConnectException, PermissionException {
        return getApi(sid).delCell((byte) cellId);
    }

/*
    protected HCDisk findDisk(HCNode node, String dskid)
        throws MgmtException, ConnectException {
        HCDisk dsk = null,
            dsks[] = api.getDisksOnNode(node.getCellId(), node.getNodeId());
        if (dsks != null)
            for (int d = 0; d < dsks.length; d++)
                if (dsks[d].getDiskId().equals(dskid))
                    return dsks[d];
        return null;
    }
*/
    public int enableDisk(int sid, int cellId, int nodeid, String diskid)
        throws MgmtException, ConnectException, PermissionException {
        getApi(sid).enableDisk((byte)cellId, diskid);
        return 0;
    }
    public int disableDisk(int sid, int cellId, int nodeid, String diskid)
        throws MgmtException, ConnectException, PermissionException {
        getApi(sid).disableDisk((byte)cellId, diskid);
        return 0;
    }


 /*
    protected String _setIP(int cellId, String propName, String ip)
        throws MgmtException, ConnectException {
        if (cellId == -1) // per-silo IP
            api.setProperty(propName, ip);
        else
            api.setProperty((byte)cellId, propName, ip);
        return (ip == null) ? "null" : ip;
    }
*/    
    public String getGatewayIP(int sid, int cellId)
        throws MgmtException, ConnectException {
        return checkNull(getApi(sid).getCellProps((byte)cellId).getGateway());
    }
    public String setGatewayIP(int sid, int cellId, String ip)
        throws MgmtException, ConnectException, PermissionException {
        HCCellProps cp = getApi(sid).getCellProps((byte)cellId);
        if (cp != null) {
            cp.setGateway(ip);
            getApi(sid).setCellProps((byte)cellId, cp);
        }
        return ip;
    }

    public String getSubnet(int sid, int cellId)
        throws MgmtException, ConnectException {
        return checkNull(getApi(sid).getCellProps((byte)cellId).getSubnet());
    }
    public String setSubnet(int sid, int cellId, String ip)
        throws MgmtException, ConnectException, PermissionException {
        HCCellProps cp = getApi(sid).getCellProps((byte)cellId);
        if (cp != null) {
            cp.setSubnet(ip);
            getApi(sid).setCellProps((byte)cellId, cp);
        }
        return ip;
    }    

    public boolean isDnsEnabled(int sid) throws MgmtException, ConnectException {
        return getApi(sid).getSiloProps().getDns().equalsIgnoreCase("y");
    }   
    public int disableDns(int sid) throws MgmtException, ConnectException,
        PermissionException {
        HCSiloProps sp = new HCSiloProps();
        sp.setDns("n");
        getApi(sid).setSiloProps(sp);
        return 0;
    }
    /*
     * client should ensure that domainame, primarydnsserver
     * secondarydnsserver? are set before this is called
     */
    public int enableDns(int sid) throws MgmtException, ConnectException,
        PermissionException {
        HCSiloProps sp = new HCSiloProps();
        sp.setDns("y");
        getApi(sid).setSiloProps(sp);
        return 0;
    }

    public String getDomainName(int sid)
        throws MgmtException, ConnectException {
        String domainName = getApi(sid).getSiloProps().getDomainName();
        return (domainName == null) ? "n/a" : domainName;
    }
    public String setDomainName(int sid, String domainName)
        throws MgmtException, ConnectException, PermissionException {
        HCSiloProps sp = new HCSiloProps();  // this will change-percell
        sp.setDomainName(domainName);
        getApi(sid).setSiloProps(sp);
        return (domainName == null) ? "" : domainName;
    }

    public Vector getDnsSearch(int sid) throws MgmtException, ConnectException {
        return strArray2Vect(split(getApi(sid).getSiloProps().getDnsSearch()));
    }
    public int setDnsSearch(int sid, Vector dnsSearch)
        throws MgmtException, ConnectException, PermissionException {
        if (dnsSearch == null)
            return -1;
        HCSiloProps sp = new HCSiloProps();
        sp.setDnsSearch(
            strArray2Str((String[])dnsSearch.toArray(emptyStrArr), ','));
        getApi(sid).setSiloProps(sp);
        return (dnsSearch.size());
    }

    public String getPrimaryDnsIP(int sid)
        throws MgmtException, ConnectException {
        return checkNull(getApi(sid).getSiloProps().getPrimaryDnsServer());
    }
    public String setPrimaryDnsIP(int sid, String primary_dns_server)
        throws MgmtException, ConnectException, PermissionException {
        HCSiloProps sp = new HCSiloProps();
        sp.setPrimaryDnsServer(primary_dns_server);
        getApi(sid).setSiloProps(sp);
        return primary_dns_server;
    }
    public String getSecondaryDnsIP(int sid) 
        throws MgmtException, ConnectException {
        return checkNull(getApi(sid).getSiloProps().getSecondaryDnsServer());
    }
    public String setSecondaryDnsIP(int sid, String secondary_dns_server)
        throws MgmtException, ConnectException, PermissionException {
        HCSiloProps sp = new HCSiloProps();
        sp.setSecondaryDnsServer(secondary_dns_server);
        getApi(sid).setSiloProps(sp);        
        return secondary_dns_server;
    }
    
    public String getSMTPIP(int sid) throws MgmtException, ConnectException {
        String ip = getApi(sid).getSiloProps().getSmtpServer();
        return checkNull(ip);
    }
    public String setSMTPIP(int sid, String ip)
        throws MgmtException, ConnectException, PermissionException {
        HCSiloProps sp = new HCSiloProps();
        sp.setSmtpServer(ip);
        getApi(sid).setSiloProps(sp);
        return ip;
    }
    public Vector getNTPIPs(int sid) throws MgmtException, ConnectException {
        return strArray2Vect(split(getApi(sid).getSiloProps().getNtpServer()));
    }
    public int setNTPIPs(int sid, Vector ips)
        throws MgmtException, ConnectException, PermissionException {

        if (ips == null)
            return -1;
        HCSiloProps sp = new HCSiloProps();
        sp.setNtpServer(
             strArray2Str((String[])ips.toArray(emptyStrArr), ','));
        getApi(sid).setSiloProps(sp);
        return (ips.size());
    }

    /* unlocalized date. string also includes compliance information */
    public String getDate(int sid, int cellId)
        throws MgmtException, ConnectException {
        return checkNull(getApi(sid).getDate((byte)cellId));
    }    
    public int getMaxNumAuthClients(int sid)
        throws MgmtException, ConnectException {
        return getApi(sid).getMaxNumAuthClients(getApi(sid).getSiloProps());
    }
    public String getLogIP(int sid) throws MgmtException, ConnectException {
        String ip = getApi(sid).getSiloProps().getExtLogger();
        return checkNull(ip);
    }
    public String setLogIP(int sid, String ip)
        throws MgmtException, ConnectException, PermissionException {
        HCSiloProps sp = new HCSiloProps();
        sp.setExtLogger(ip);
        getApi(sid).setSiloProps(sp);
        return ip;
    }
    public static final int DEFAULT_SMTP_PORT = 25;
    public int getSMTPPort(int sid) throws MgmtException, ConnectException {
        int port = DEFAULT_SMTP_PORT;
        try {
            port = Integer.parseInt(getApi(sid).getSiloProps().getSmtpPort());
        } catch (NumberFormatException e) {
            LOG.severe("cannot retrieve SMTP port: " + e);
        }
        return port;
    }
    public String setSMTPPort(int sid, int port)
        throws MgmtException, ConnectException, PermissionException {
        HCSiloProps sp = new HCSiloProps();
        sp.setSmtpPort(String.valueOf(port));
        getApi(sid).setSiloProps(sp);
        return String.valueOf(port);
    }   
 
    static Vector strArray2Vect(String[] strArray) {
        return (strArray == null) ? emptyVect :
            new Vector(Arrays.asList(strArray));

    }
    static String strArray2Str(String[] arr, char delim) {
        StringBuffer sbuf = new StringBuffer();
        if (arr == null)
            return "";
        else {
            if (arr.length == 0)
                return "";
            int i;
            for (i = 0; i < arr.length - 1; i++)
                 sbuf.append(arr[i]).append(delim);
            sbuf.append(arr[i]);
        }
        return sbuf.toString();
    }

    static final String ALL = "all";
    
    public Vector getClients(int sid) throws MgmtException, ConnectException {

        String clientsStr = getApi(sid).getSiloProps().getAuthorizedClients();
        return (ALL.equals(clientsStr) ? 
                    emptyVect :
                    strArray2Vect(ClientUtils.getListFromCSV(clientsStr).
                                  toArray(emptyStrArr)));
    }
    public int setClients(int sid, Vector newClients)
        throws MgmtException, ConnectException, PermissionException {

        if (newClients == null)
            return -1;
        HCSiloProps sp = getApi(sid).getSiloProps();
        List<String> crtLst = ClientUtils.getListFromCSV(sp.getAuthorizedClients());

        Iterator<String> it = crtLst.iterator();
        String client;

        // remove all items from crtList which are not in clients
        while (it.hasNext()) {
            client = it.next();
            if (newClients.contains(client)) {
                newClients.remove(client);
            } else
                it.remove();
        }
        // add the new clients
        for (Object newClient : newClients) {
            crtLst.add((String)newClient);
        }
        // commit changes to silo props
        sp.setAuthorizedClients(crtLst.isEmpty()
                                ? ALL : ClientUtils.getCSVFromList(crtLst));
        getApi(sid).setSiloProps(sp);
        return (newClients.size());
    }
    public Vector getEmailTo(int sid) throws MgmtException, ConnectException {
        return strArray2Vect(getApi(sid).getAlertTo());
    }
    public Vector getEmailCc(int sid) throws MgmtException, ConnectException {
        return strArray2Vect(getApi(sid).getAlertCc());
    } 
    protected int _setEmail(AdminClient api, Vector emails, boolean to)
        throws MgmtException, ConnectException, PermissionException {

        if (emails == null)
            return -1;
        String[] emailsArr = (String[])emails.toArray(emptyStrArr);
        if (to)
            api.setAlertTo(emailsArr);
        else
            api.setAlertCc(emailsArr);
        return (emails.size());
    }
    public int setEmailTo(int sid, Vector emails)
        throws MgmtException, ConnectException, PermissionException {
        return _setEmail(getApi(sid), emails, true);
    }
    public int setEmailCc(int sid, Vector emails)
        throws MgmtException, ConnectException, PermissionException {
        return _setEmail(getApi(sid), emails, false);
    }

    public String getHADBStatus(int sid, int cellId)
        throws MgmtException, ConnectException {
        return getApi(sid).getHadbStatus((byte)cellId);
    }

    public String getLicense(int sid, int cellId)
        throws MgmtException, ConnectException {
        return checkNull(getApi(sid).getLicense((byte)cellId));
    }
    public int setLicense(int sid, int cellId, String license)
        throws MgmtException, ConnectException, PermissionException {
        getApi(sid).setLicense((byte)cellId, license);
        return 1;
    }

    String perfElem2Str(HCPerfElement pe) {
        String delim = ",";
        String str = "";
        if (pe == null)
            return delim + delim;
        return (str + pe.getKbSec() + delim  +
                pe.getOps() + delim +
                pe.getOpSec());
    }
    Hashtable getStats(HCPerfStats stats) {
        Hashtable res = new Hashtable();
        if (stats != null) {
            res.put(PFS_STORE, perfElem2Str(stats.getStoreOnly()));
            res.put(PFS_STOREMD, perfElem2Str(stats.getStoreMd()));
            res.put(PFS_STOREBOTH, perfElem2Str(stats.getStoreBoth()));
            res.put(PFS_RETRV, perfElem2Str(stats.getRetrieveOnly()));
            res.put(PFS_RETRVMD, perfElem2Str(stats.getRetrieveMd()));
            res.put(PFS_QUERY, perfElem2Str(stats.getQuery()));
            res.put(PFS_DEL, perfElem2Str(stats.getDelete()));
            res.put(PFS_WDAVGET, perfElem2Str(stats.getWebdavGet()));
            res.put(PFS_WDAVPUT, perfElem2Str(stats.getWebdavPut()));
            res.put(PFS_LOAD1MIN, stats.getLoadOneMinute());
            res.put(PFS_LOAD5MIN, stats.getLoadFiveMinute());
            res.put(PFS_LOAD15MIN, stats.getLoadFifteenMinute());
        }
        return res;
    }
    
    public Hashtable getPerfStats(int sid, int interval, int cellId)
        throws MgmtException, ConnectException {
        return
            getStats(getApi(sid).getClusterPerfStats(interval, (byte)cellId));
    }
    public Hashtable getPerfStats(int sid, int interval, int cellId, int nodeId)
        throws MgmtException, ConnectException {
        return
            getStats(getApi(sid).getNodePerfStats(nodeId, interval, (byte)cellId));
    }

    public byte[] getMetadataCfg(int sid)
        throws MgmtException, ConnectException, 
                                            PermissionException, IOException {

        final StringBuffer mdCfg = new StringBuffer();
        PipedOutputStream pos = new PipedOutputStream();

        try {
            PipedInputStream pis = new PipedInputStream(pos);
            final BufferedReader reader =
                new BufferedReader(new InputStreamReader(pis, UTF8));
 
            // must do the reading from a separate thread
            new Thread() {
                public void run() {
                    synchronized (mdCfg) {
                        System.out.println("running reader thread");
                        String line;
                        try {
                            do {
                                line = reader.readLine();
                                if (line != null) {
                                    mdCfg.append(line);
                                    System.out.println("line=" + line);
                                }
                            } while (line != null &&
                                     !line.endsWith(MDCFG_ENDTAG));
                        } catch (IOException io) {
                            throw new RuntimeException(
                                "Cannot read metadata configuration. "
                                + io.getMessage());
                        }
                    }
                }
            }.start();
            
        } catch (IOException io) {
            throw new RuntimeException("Cannot read metadata configuration. " +
                    io.getMessage());
        }
        getApi(sid).getSchema(new PrintStream(pos, true, UTF8), false);    

        synchronized (mdCfg) {
            return mdCfg.toString().getBytes(UTF8);
        }
    }
    
    public int setMetadataCfg(int sid, byte[] metadataCfg)
        throws MgmtException, ConnectException, 
                                    PermissionException, IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter osw = new OutputStreamWriter(baos, UTF8);
        try {
            BufferedWriter bufw = new BufferedWriter(osw);
            bufw.write(new String(metadataCfg, UTF8));
            bufw.flush();
        } catch (IOException io) {
            throw new RuntimeException("Cannot write metadata configuration. " +
                    io.getMessage());
        }
        ByteArrayInputStream bais =
            new ByteArrayInputStream(baos.toByteArray());

        getApi(sid).pushSchema(bais, false);

        return 0;
    }
}

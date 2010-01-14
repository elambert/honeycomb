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

import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.honeycomb.admingui.present.exploreritems.ExplItemCells;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.ExplorerItem;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.io.IOException;
// next 2 used by the metadata config API-s
import java.io.PrintWriter;
import java.io.ByteArrayOutputStream;

import java.util.Vector;
import java.util.Hashtable;
import java.util.Arrays;
import java.util.ArrayList;

import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcClientException;

import com.sun.honeycomb.admingui.XmlRpcParamDefs;
import com.sun.honeycomb.emd.config.EMDConfigException;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.Namespace;
import com.sun.honeycomb.emd.config.Field;

import com.sun.honeycomb.common.md5.MD5Check;
import com.sun.honeycomb.common.md5.MD5Crypt;
import java.io.OutputStreamWriter;

import java.util.HashSet;
import java.util.Set;

/**
 * XML-RPC based implementation of the AdminApi interface
 */
public class AdminApiXML implements AdminApi, XmlRpcParamDefs {

    private String hcAdminIP;
    private URL url;
    private HttpURLConnection conn = null;
    private XmlRpcClient client;
    private int sid = 0; // session id

    private final static String UTF8 = "UTF8";
    private final static Vector emptyVect = new Vector(0);
    private final static String[] emptyStrArr = new String[0];
    private final static HashSet reservedNamespaceNames = new HashSet();
    static {
        reservedNamespaceNames.add("system");
        reservedNamespaceNames.add("system.test");
        reservedNamespaceNames.add("filesystem");
        // Represents the root namespace which is effectively no namespace
        reservedNamespaceNames.add("");
    }
    
    static void printHeaderFields(HttpURLConnection c) {
        for (int i = 0; ; i++) {
            String s = c.getHeaderFieldKey(i);
            if (s == null)
                return;
            System.out.println(i + ": " + c.getHeaderField(i));
        }
    }

    /** instantiate the API for a specific URL */
    public AdminApiXML(String hcAdminIPandPort) throws MalformedURLException {
        this.hcAdminIP = hcAdminIP;
            
        url = new URL("https://" + hcAdminIPandPort + "/RPC2/request");
    }
 

    /* ----------------------- TypeUtils --------------------------------- */
 
    static Object _getHVal(Hashtable hash, String keyName)
        throws ClientException {
        Object val = hash.get(keyName);
        if (val == null)
            throw new ClientException("invalid format - missing data (\"" +
                        keyName + "\")");
        return val;
    }
    static int getHValInt(Hashtable hash, String keyName)
        throws ClientException {
        return ((Integer)_getHVal(hash, keyName)).intValue();
    }
    static double getHValDouble(Hashtable hash, String keyName) {
        double val = -1;
        try {
            val = ((Double)_getHVal(hash, keyName)).doubleValue();
        } catch (Exception e) {
            System.out.println(e);
        }
        return val;
    }
    static boolean getHValBool(Hashtable hash, String keyName)
        throws ClientException {  
        return ((Boolean)_getHVal(hash, keyName)).booleanValue();
    }
    static String getHValString(Hashtable hash, String keyName) {
        Object val = hash.get(keyName);
        if (val == null)
           return new String("");
        else
           return (String) val;
    }

    static Vector getHValVector(Hashtable hash, String keyName) {
        return (Vector)hash.get(keyName);
    }
    static Vector str2Vect(String str) {
        Vector v = new Vector(1);
        v.addElement(str);
        return v;
    }
    static Vector strstr2Vect(String str, String str2) {
        Vector v = new Vector(2);
        v.addElement(str);
        v.addElement(str2);
        return v;
    }    
    static Vector strArray2Vect(String[] strArray) {
        return (strArray == null) ? null :
            new Vector(Arrays.asList(strArray));
    }
    static Vector bytearr2Vect(byte[] bytearr) {
        Vector v = new Vector(1);
        v.addElement(bytearr);
        return v;
    }
    static Vector int2Vect(int val) {
        Vector v = new Vector(1);
        v.addElement(new Integer(val));
        return v;
    }
    static Vector intint2Vect(int val1, int val2) {
        Vector v = new Vector(2);
        v.addElement(new Integer(val1));
        v.addElement(new Integer(val2));
        return v;
    }
    static Vector intintint2Vect(int val1, int val2, int val3) {
        Vector v = intint2Vect(val1, val2);
        v.addElement(new Integer(val3));
        return v;
    }
    static Vector intstr2Vect(int val1, String str) {
        Vector v = new Vector(2);
        v.addElement(new Integer(val1));
        v.addElement(str);
        return v;
    }    
    static Vector intintStr2Vect(int val1, int val2, String str) {
        Vector v = new Vector(2);
        v.addElement(new Integer(val1));
        v.addElement(new Integer(val2));
        v.addElement(str);
        return v;
    }
    static Vector intbool2Vect(int val1, boolean val2) {
        Vector v = new Vector(2);
        v.addElement(new Integer(val1));
        v.addElement(new Boolean(val2));
        return v;
    }   
    static Vector intboolbool2Vect(int val1, boolean val2, boolean val3) {
        Vector v = new Vector(2);
        v.addElement(new Integer(val1));
        v.addElement(new Boolean(val2));
        v.addElement(new Boolean(val3));
        return v;
    }   
    static Vector vect2Vect(Vector addme) {
        Vector v = new Vector(1);
        v.addElement(addme);
        return v;
    }
    static Vector obj2Vect(Object addme) {
        Vector v = new Vector(1);
        v.addElement(addme);
        return v;
    }
    static Vector intObj2Vect(int i, Object o) {
        Vector v = int2Vect(i);
        v.addElement(o);
        return v;
    }
    static void checknull(Object o, String name) throws ClientException {
        if (o == null)
            throw new ClientException("Internal error: null " + name);
    }

    /* -------------------- less generic helper functions ---------------- */
    
    protected Cell getHValCell(Hashtable cellHash)
        throws ClientException {
        return new Cell(getHValInt(cellHash, ID),
                        getHValBool(cellHash, ALIVE),
                        getHValString(cellHash, FREE),
                        getHValInt(cellHash, NUM_NODES));
    }
    protected Node getHValNode(Hashtable nodeHash, Cell cell)
        throws ClientException {
        Node node = new Node(this, cell,
                             getHValInt(nodeHash, ID),
                             getHValBool(nodeHash, ALIVE),
                             getHValInt(nodeHash, STATUS),
                             getHValString(nodeHash, FRU));
        node.setDisks(_getDisks(getHValVector(nodeHash, DISKS), node));
        return node;
    }
    protected Disk getHValDisk(Hashtable dskHash, Node node)
        throws ClientException {
        return new Disk(this,
                        node,
                        getHValString(dskHash, DISKID),
                        getHValInt(dskHash, STATUS),
                        getHValInt(dskHash, CAPTOTAL),
                        getHValInt(dskHash, CAPUSED),
                        getHValString(dskHash, FRU));
    }
    protected Switch getHValSwitch(Hashtable sHash, Cell cell)
        throws ClientException {
        return new Switch(this,
                        cell,
                        getHValString(sHash, ID),
                        getHValInt(sHash, STATUS),
                        getHValString(sHash, VER),
                        getHValString(sHash, FRU));
    }
    protected ServiceNode getHValServiceNode(Hashtable spHash, Cell cell)
        throws ClientException {
        return new ServiceNode(this,
                        cell,
                        getHValString(spHash, ID),
                        getHValBool(spHash, ALIVE),
                        getHValInt(spHash, STATUS),
                        getHValString(spHash, FRU));
    }
            
    protected static String portType2metName(int portType, boolean get) {
        switch (portType) {
            case PORTT_SMTP:
                return get ? GETSMTPPORT : SETSMTPPORT;
            default:
                throw new Error("Internal error: unknown port type: "
                            + portType);
        }
    }
    protected static String addrType2metName(int addrType, boolean get) {
        switch (addrType) {
            case ADDRT_ADMIN_IP:
                return get ? GETADMIP : SETADMIP;
            case ADDRT_DATA_IP:
                return get ? GETDATAIP : SETDATAIP;
            case ADDRT_SP_IP:
                return get ? GETSPIP : SETSPIP;
            case ADDRT_GTWAY_IP:
                return get ? GETGTWYIP : SETGTWYIP;
             case ADDRT_NET:
                return get ? GETNETIP : SETNETIP;
            case ADDRT_SUBNET:
                return get ? GETSUBNET : SETSUBNET;
            case ADDRT_LOG_IP:
                return get ? GETLOGIP : SETLOGIP;
            case ADDRT_SMTP_IP:
                return get ? GETSMTPIP : SETSMTPIP;
            case ADDRT_PRIDNS_IP:
                return get ? GETPRIDNSIP : SETPRIDNSIP;
            case ADDRT_SECDNS_IP:
                return get ? GETSECDNSIP : SETSECDNSIP;
            default:
                throw new Error("Internal error: unknown address type: "
                            + addrType);
        }
    }      

    protected Object _execute(String metName, Vector params)
        throws ClientException, ServerException {
        Object result = null;
        Vector v = (Vector)params.clone();
        v.insertElementAt(new Integer(sid), 0);
        try {
            // System.out.println(params.size() + " params");
            result = client.execute(metName, v);
            System.out.println(metName + "::result=" + result); // debug
        } catch (IOException e) {
            e.printStackTrace(); // debug
            throw new CommException(e.getLocalizedMessage());
        } catch (XmlRpcClientException e) {
            e.printStackTrace(); // debug
            throw new ClientException(e.getLocalizedMessage());
        } catch (XmlRpcException e) {
            e.printStackTrace(); // debug
            String msg = e.getLocalizedMessage();
            if (msg != null && (msg.indexOf(FAULT_NO_PERM) != -1))
                throw new PermException(msg);
            else
                if (msg != null &&
                    (msg.indexOf(FAULT_INVALID_SID) != -1)) {
                    sid = 0; // will get new one upon re-login
                    throw new TimeoutException(msg);
                }
                else
                    throw new ServerException(msg);
        }
        return result;
    }

    /* -------------------------   API   --------------------------------- */
    
    public String getClientAPIVer() { return APIVER; }

    public String getServerAPIVer() throws ClientException, ServerException {
        return (String)_execute(GETAPIVER, emptyVect);
    }

    public boolean reqFullAccess()
        throws ClientException, ServerException {
        return ((Boolean)_execute(LOGIN, emptyVect)).booleanValue();
    }
    public boolean fullAccess() // if false, then read-only
        throws ClientException, ServerException {
        return ((Boolean)_execute(LOGGEDIN, emptyVect)).booleanValue();
    }
    public void logout() throws ClientException, ServerException {
        _execute(LOGOUT, emptyVect);
        sid = 0;
    }
/*
    public String getVersion() throws ClientException, ServerException {
        return (String)_execute(GETVER, emptyVect);
    }
 */
    public boolean areSwitchesOk(int cellId)
        throws ClientException, ServerException {
        return ((Boolean)_execute(GETSWOK, int2Vect(cellId))).booleanValue();
    }

    public String getDate(int cellId)
        throws ClientException, ServerException {
        return ((String)_execute(GETDATE, int2Vect(cellId)));
    }
    public int getMaxNumAuthClients() throws ClientException, ServerException {
        return ((Integer)_execute(GETMAXNOAUTHCLIENTS, emptyVect)).intValue();
    }
    public int getNumOfUnhealedFailures(int cellId)
        throws ClientException, ServerException {
        return ((Integer)_execute(GETNOUFAILRS, int2Vect(cellId))).intValue();
    }
    public boolean hasQuorum(int cellId)
        throws ClientException, ServerException {
        return ((Boolean)_execute(HASQUORUM, int2Vect(cellId))).booleanValue();
    }
    public String getHADBStatus(Cell cell)
        throws ClientException, ServerException {
        checknull(cell, "cell");
        return (String)_execute(GETHADBSTATUS, int2Vect(cell.getID()));
    }

    public String getAddress(int addrType)
        throws ClientException, ServerException {
        if (addrType == ADDRT_DATA_IP ||
            addrType == ADDRT_ADMIN_IP ||
            addrType == ADDRT_SP_IP ||
            addrType == ADDRT_GTWAY_IP ||
            addrType == ADDRT_SUBNET) {
            System.out.println("getAddress() deprecated for ADDRT " + addrType
                    + ". Please use getCellProps() instead");
            return getCellAddress((getCells()[0]).getID(), addrType);
        }
        return (String)_execute(addrType2metName(addrType, true), emptyVect);
    }
    public String getCellAddress(int cellId, int addrType)
        throws ClientException, ServerException {
        return (String)_execute(addrType2metName(addrType, true),
                                int2Vect(cellId));
    }
    public CellProps getCellProps(int cellId) 
        throws ClientException, ServerException {
        CellProps cp = new CellProps();
        Hashtable h = (Hashtable)_execute(GETCELLPROPS, int2Vect(cellId));
        cp.adminIP   = getHValString(h, ADMIP);
        cp.dataIP    = getHValString(h, DATAIP);
        cp.spIP      = getHValString(h, SPIP);
        cp.gatewayIP = getHValString(h, GTWYIP);
        cp.subnet    = getHValString(h, SUBNET);
        return cp;
        
    }
    public int getNumOfCells() throws ClientException, ServerException {
        return ((Integer)_execute(GETNOCELLS, emptyVect)).intValue();
    }
    Hashtable cellProps2Hash(CellProps cp) {
        Hashtable h = new Hashtable();
        if (cp != null) {
            if (cp.adminIP != null)
                h.put(ADMIP, cp.adminIP);
            if (cp.dataIP != null)
                h.put(DATAIP, cp.dataIP);
            if (cp.spIP != null)
                h.put(SPIP, cp.spIP);
        }
        return h;
    } 
    public void setCellProps(int cellId, CellProps cp)
        throws ClientException, ServerException {
        _execute(SETCELLPROPS, intObj2Vect(cellId, cellProps2Hash(cp)));
    }
    public void setAddress(int addrType, String addr)
        throws ClientException, ServerException {
        if (addrType == ADDRT_DATA_IP ||
            addrType == ADDRT_ADMIN_IP ||
            addrType == ADDRT_SP_IP ||
            addrType == ADDRT_GTWAY_IP ||
            addrType == ADDRT_SUBNET) {
            System.out.println("setAddress() deprecated for ADDRT " + addrType
                    + ". Please use setCellAddress() instead");
            setCellAddress((getCells()[0]).getID(), addrType, addr);
        } else
            _execute(addrType2metName(addrType, false), str2Vect(addr)); 
    }
    public void setCellAddress(int cellId, int addrType, String addr)
        throws ClientException, ServerException {
        _execute(addrType2metName(addrType, false),
                 intstr2Vect(cellId, addr)); 
    }

    public int getPort(int portType)
        throws ClientException, ServerException {
        return ((Integer)_execute(
            portType2metName(portType, true), emptyVect)).intValue();
    }
    public void setPort(int portType, int portNum)
        throws ClientException, ServerException {
        _execute(portType2metName(portType, false), int2Vect(portNum));
    }
    
    public String[] getNTPAddrs()
        throws ClientException, ServerException {
        return (String[])
            ((Vector)_execute(GETNTPIPS, emptyVect)).toArray(emptyStrArr);
    }
    public void setNTPAddrs(String ips[])
        throws ClientException, ServerException {
        _execute(SETNTPIPS, vect2Vect(strArray2Vect(ips)));
    }



    // DSN configuration
    
    public void enableDns() throws ClientException, ServerException {
        _execute(SETDNSON, emptyVect);
    }
    public void disableDns() throws ClientException, ServerException {
        _execute(SETDNSOFF, emptyVect);
    };
    public boolean isDnsEnabled() throws ClientException, ServerException {
        return ((Boolean)_execute(ISDNSON, emptyVect)).booleanValue();
    }
    public String getDomainName() throws ClientException, ServerException {
        return (String)_execute(GETDOMAIN_NAME, emptyVect);
    }
    public void setDomainName(String domain)
        throws ClientException, ServerException {
        _execute(SETDOMAIN_NAME, str2Vect(domain));
    }
    public String[] getDnsSearch() throws ClientException, ServerException {
        return (String[])
            ((Vector)_execute(GETDNS_SEARCH, emptyVect)).toArray(emptyStrArr);
    }
    public void setDnsSearch(String[] searchList)
        throws ClientException, ServerException {
        _execute(SETDNS_SEARCH, vect2Vect(strArray2Vect(searchList)));
    }

    public boolean newSessionAndVerifyPasswd(String passwd)
        throws ClientException, ServerException {
        System.out.println("connecting to " + url);
        client = new XmlRpcClient(url);        
        sid = ((Integer)_execute(VERIFYPSWD, str2Vect(passwd))).intValue();
        System.out.println("sessionid=" + sid); // debug
        return (sid != 0);
    }
 
    public boolean verifyPasswd(String passwd)
        throws ClientException, ServerException {

        return
            (0 != ((Integer)_execute(VERIFYPSWD, str2Vect(passwd))).intValue());
    }
    public void setPasswd(String newPasswd)
        throws ClientException, ServerException {
        _execute(SETPSWD, bytearr2Vect(MD5Crypt.crypt(newPasswd).getBytes()));
    }
    
    public String[] getClients() throws ClientException, ServerException {
        return (String[])
            ((Vector)_execute(GETCLIENTS, emptyVect)).toArray(emptyStrArr);
    }
    public void setClients(String ips[])
        throws ClientException, ServerException {
        _execute(SETCLIENTS, vect2Vect(strArray2Vect(ips)));
    }
    
    public String[] getEmailsTo() throws ClientException, ServerException {
        return (String[])
            ((Vector)_execute(GETEMAILTO, emptyVect)).toArray(emptyStrArr);
    }
    public String[] getEmailsCc() throws ClientException, ServerException {
        return (String[])
            ((Vector)_execute(GETEMAILCC, emptyVect)).toArray(emptyStrArr);
    }
    public void setEmailsTo(String[] emails)
        throws ClientException, ServerException {
        _execute(SETEMAILTO, vect2Vect(strArray2Vect(emails)));
    }
    public void setEmailsCc(String[] emails)
        throws ClientException, ServerException {
        _execute(SETEMAILCC, vect2Vect(strArray2Vect(emails)));
    }   

    public Cell[] _getCells(Vector v) throws ClientException, ServerException {
        Hashtable cellInfo = null;
        Cell cell = null;
        Cell[] cells = null;
        int retryCnt = 1;
        int numRetries = 24;  // 2 minutes = 5 sec. thread sleep * 24
        
        // If the previous call to GETCELLS populated the cell information in
        // MainHandler, then the call below will return true.  Otherwise, calls
        // to GETCACHEDCELLS will occur periodically until the cell information
        // is either populated or the time elapsed is ~2 minutes.  If the cell
        // information has not been populated in MainHandler after 2 minutes,
        // the vector, v, returned will be null and a NPE will be thrown below.
        boolean hasInfo = 
                ((Boolean)_execute(HASCACHEDCELLS, emptyVect)).booleanValue();
        while ((!hasInfo || (v == null)) && (retryCnt < numRetries)) {
            try {
                if (!hasInfo) {
                    Thread.currentThread().sleep(5000);
                    retryCnt++;
                }
            } catch (InterruptedException ex) {
                // ignore
            }
            hasInfo = 
                ((Boolean)_execute(HASCACHEDCELLS, emptyVect)).booleanValue();
            if (hasInfo) {
                // if SP is down or there was a delay in retrieving cell info
                // then the vector passed in from the previous GETCELLS method
                // call is potentially equal to null --
                v = (Vector)_execute(GETCACHEDCELLS, emptyVect);
                retryCnt++;


            }
        }
        // vector, v, will be null if the above loop has timed out without 
        // having retrieved the cell information from the server
        cells = new Cell[v.size()];
        int masterCellIdx = 0;
        int minCellId = 10000;
        for (int i = 0; i < v.size(); i++) {
            cellInfo = (Hashtable)v.elementAt(i);
            cells[i] = getHValCell(cellInfo);
            if (cells[i] != null) {
                int crtid = cells[i].getID();
                if (crtid < minCellId) {
                    masterCellIdx = i;
                    minCellId = crtid;
                }
            }
        }
        cells[masterCellIdx].setMaster(true);
        return cells;
    }
    public Cell[] getCells() throws ClientException, ServerException {
        boolean success = false;
        Cell[] cells = null;
        try {          
            cells = _getCells((Vector)_execute(GETCELLS, emptyVect));
            ObjectFactory.setGetCellsError(false);
            success = true;
        } finally {
            if (!success) {
                try {
                    System.out.println("Failed to retrieve cell(s) info");
                    ObjectFactory.setGetCellsError(true); 
                    // Need to change the current ExplorerItem's isModified
                    // flag to false, otherwise, the removeExplorerItemChildren
                    // call will cause a treeStructureChanged event to fire
                    // and the treeSelectionListener code will pop up a dialog
                    // box asking the user if he/she wants to save values --
                    // normally, this is behavior we want if user leaves a GUI
                    // page and clicks on a tree node.  In this case, however,
                    // we can't get cell info and we want to leave the page and
                    // ultimately show the home page.
                    ExplorerItem explItem = (ExplorerItem) MainFrame.
                                                getMainFrame().getCurrentItem();
                    explItem.setIsModified(false);
                    MainFrame.getMainFrame().removeExplorerItemChildren(
                                                        ExplItemCells.class);
                    MainFrame.getMainFrame().refreshExplorerItemData(
                                                        ExplItemCells.class);
                    ObjectFactory.setRefreshExplItemCells(true);
                } catch (Exception e){}
            }
        }
        // Only refresh the cell data for the ExplItemCells node if it hasn't
        // already been refreshed
        if (ObjectFactory.isRefreshExplItemCells()) {
            MainFrame.getMainFrame().refreshExplorerItemData(ExplItemCells.class);
        }
        return cells;
    } 
    private Disk[] _getDisks(Vector v, Node node) throws ClientException {
        Hashtable diskInfo = null;
        Disk disk, disks[] = new Disk[v.size()];
        for (int i = 0; i < v.size(); i++) {
            diskInfo = (Hashtable)v.elementAt(i);
            disks[i] = getHValDisk(diskInfo, node);
        }
        return disks;
    }
    public Switch[] getSwitches(Cell theCell)
        throws ClientException, ServerException {
        Vector result = null;
        Hashtable nodeInfo = null;
        Switch s, switches[];

        checknull(theCell, "cell");
        result = (Vector)_execute(GETSWITCHES, int2Vect(theCell.getID()));
        switches = new Switch[result.size()];
        for (int i = 0; i < result.size(); i++) {
            switches[i] = getHValSwitch((Hashtable)result.elementAt(i), theCell);
        }
        return switches;
    }
    public Switch getSwitch(Cell cell, String switchId)
        throws ClientException, ServerException {
        checknull(cell, "cell");
        Hashtable sHash =
             (Hashtable)_execute(GETSWITCH,
                                 intstr2Vect(cell.getID(), switchId));
        return getHValSwitch(sHash, cell);
    }
    public ServiceNode getSp(Cell cell)
        throws ClientException, ServerException {
        checknull(cell, "cell");
        Hashtable spHash = (Hashtable)_execute(GETSP, int2Vect(cell.getID()));
        return ((spHash == null) ? null : getHValServiceNode(spHash, cell));
    }
    public Node getNode(Cell cell, int nodeid)
        throws ClientException, ServerException {
        checknull(cell, "cell");
        Hashtable nodeHash =
             (Hashtable)_execute(GETNODE,
                                 intint2Vect(cell.getID(), nodeid));
        return getHValNode(nodeHash, cell);
    }
 
    public Node[] getNodes(Cell theCell)
        throws ClientException, ServerException {
        Vector result = null;
        Hashtable nodeInfo = null;
        Node node, nodes[];

        checknull(theCell, "cell");
        result = (Vector)_execute(GETNODES, int2Vect(theCell.getID()));
        nodes = new Node[result.size()];
        for (int i = 0; i < result.size(); i++)
            nodes[i] = getHValNode((Hashtable)result.elementAt(i), theCell);
        return nodes;
    }
    public Disk[] getDisks(Node theNode)
        throws ClientException, ServerException {
        return getDisks(theNode, true);
    }
    public Disk[] getDisks(Node theNode, boolean cachedDisks)
        throws ClientException, ServerException {
        checknull(theNode, "node");
        Vector disksOnNode = null;
        if (cachedDisks) {
            disksOnNode = (Vector)_execute(GETDISKS,
                                          intint2Vect(theNode.getCell().getID(),
                                                      theNode.getNodeID()));
        } else {
            disksOnNode =  (Vector)_execute(GETDISKSONNODE,
                                          intint2Vect(theNode.getCell().getID(),
                                                      theNode.getNodeID()));
        }
        return _getDisks(disksOnNode, theNode);
    }
    public Disk[] getDisks(Cell theCell)
        throws ClientException, ServerException {
        checknull(theCell, "cell");
        return _getDisks((Vector)_execute(GETDISKS,
                                          int2Vect(theCell.getID())), null);
    }
    public Service[] getServices(Node theNode)
        throws ClientException, ServerException {
        checknull(theNode, "node");
        Vector result = null;
        Hashtable svcInfo = null;
        Service svc, svcs[];

        result = (Vector)_execute(GETSVCS,
                                  intint2Vect(theNode.getCell().getID(), 
                                              theNode.getNodeID()));
        svcs = new Service[result.size()];
        for (int i = 0; i < result.size(); i++) {
            svcInfo = (Hashtable) result.elementAt(i);
            svc = new Service(getHValString(svcInfo, SVCNAME),
                               getHValBool(svcInfo, ALIVE));
            svcs[i] = svc;
        }
        return svcs;
    }

    public Versions getFwVersions(Cell theCell)
        throws ClientException, ServerException {
        Object result = null;
        Hashtable versInfo = null;
        Versions vers;

        checknull(theCell, "cell");
        result = _execute(GETVERS, int2Vect(theCell.getID()));
        if (result == null)
            return new Versions();
        versInfo = (Hashtable) result;
        vers = new Versions(
                getHValString(versInfo, VER),
                getHValString(versInfo, SPBIOS),
                getHValString(versInfo, SPSMDS),
                getHValString(versInfo, SW1OVRLAY),
                getHValString(versInfo, SW2OVRLAY),
                (String[])getHValVector(versInfo, BIOS).toArray(emptyStrArr),
                (String[])getHValVector(versInfo, SMDC).toArray(emptyStrArr));
        return vers;
    }

    public Sensor[] getSensors(Node node)
        throws ClientException, ServerException {
        Object result = null;
        Hashtable sensInfo = null;
        Sensor[] sensors = new Sensor[0];

        checknull(node, "node");
        result = _execute(GETSENSORS,
                intint2Vect(node.getCell().getID(), node.getNodeID()));

        if (result == null) {
            return sensors;
        }
        sensInfo = (Hashtable) result;
        ArrayList list = new ArrayList();
        list.add(new Sensor(Sensor.DDR_V,   getHValString(sensInfo, DDR_V)));
        list.add(new Sensor(Sensor.CPU_V,   getHValString(sensInfo, CPU_V)));
        list.add(new Sensor(Sensor.MB3V_V,  getHValString(sensInfo, MB3V_V)));
        list.add(new Sensor(Sensor.MB5V_V,  getHValString(sensInfo, MB5V_V)));
        list.add(new Sensor(Sensor.MB12V_V, getHValString(sensInfo, MB12V_V)));
        list.add(new Sensor(Sensor.BAT_V,   getHValString(sensInfo, BAT_V)));
        list.add(new Sensor(Sensor.CPU_T, getHValString(sensInfo, CPU_T)));
        list.add(new Sensor(Sensor.SYS_T, getHValString(sensInfo, SYS_T)));
        list.add(new Sensor(Sensor.SYSF1_F, getHValString(sensInfo, SYSF1_F)));
        list.add(new Sensor(Sensor.SYSF2_F, getHValString(sensInfo, SYSF2_F)));
        list.add(new Sensor(Sensor.SYSF3_F, getHValString(sensInfo, SYSF3_F)));
        list.add(new Sensor(Sensor.SYSF4_F, getHValString(sensInfo, SYSF4_F)));
        list.add(new Sensor(Sensor.SYSF5_F, getHValString(sensInfo, SYSF5_F)));

        return (Sensor[])list.toArray(sensors);
    }

    
    double[] getCpuLoad(Hashtable hash) {
        double[] load = new double[] { 0, 0, 0 };
        try {
            load[0] = Double.parseDouble(getHValString(hash, PFS_LOAD1MIN));
            load[1] = Double.parseDouble(getHValString(hash, PFS_LOAD5MIN));
            load[2] = Double.parseDouble(getHValString(hash, PFS_LOAD15MIN));
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
        return load;
    }

    PerfStats hash2PerfStats(Hashtable hash) {
        OpnStats[] emptyOpnStats = new OpnStats[0];
        if (hash == null)
            return new PerfStats(emptyOpnStats, new double[0]);
        ArrayList stats = new ArrayList();
        stats.add(new OpnStats(OpnStats.OP_STORE_DATA,
                               getHValString(hash, PFS_STORE)));
        stats.add(new OpnStats(OpnStats.OP_STORE_MD,
                               getHValString(hash, PFS_STOREMD)));
        stats.add(new OpnStats(OpnStats.OP_STORE_BOTH,
                               getHValString(hash, PFS_STOREBOTH)));
        stats.add(new OpnStats(OpnStats.OP_RETRIEVE_MD,
                               getHValString(hash, PFS_RETRVMD)));
        stats.add(new OpnStats(OpnStats.OP_RETRIEVE_DATA,
                               getHValString(hash, PFS_RETRV)));
        stats.add(new OpnStats(OpnStats.OP_QUERY,
                               getHValString(hash, PFS_QUERY)));
        stats.add(new OpnStats(OpnStats.OP_DELETE,
                               getHValString(hash, PFS_DEL)));
        stats.add(new OpnStats(OpnStats.OP_WEBDAV_GET,
                               getHValString(hash, PFS_WDAVGET)));
        stats.add(new OpnStats(OpnStats.OP_WEBDAV_PUT,
                               getHValString(hash, PFS_WDAVPUT)));
        return new PerfStats((OpnStats[])stats.toArray(emptyOpnStats),
                             getCpuLoad(hash));
    }

    public PerfStats getPerfStats(int interval, Cell theCell)
        throws ClientException, ServerException {
        return hash2PerfStats((Hashtable)_execute(GETPERFSTATS,
                              intint2Vect(interval, theCell.getID())));
    }

    public PerfStats getPerfStats(int interval, Node theNode)
        throws ClientException, ServerException {
        return hash2PerfStats((Hashtable)_execute(GETPERFSTATS,
                              intintint2Vect(interval,
                                             theNode.getCell().getID(),
                                             theNode.getNodeID())));
    }
    

    public void powerNodeOn(Node node)
        throws ClientException, ServerException {
        checknull(node, "node");
        _execute(POWERNODEON,
                 intint2Vect(node.getCell().getID(), node.getNodeID()));
    }
    public void powerNodeOff(Node node)
        throws ClientException, ServerException {
         checknull(node, "node");       
        _execute(POWERNODEOFF,
                 intint2Vect(node.getCell().getID(), node.getNodeID()));
    }
    public void rebootNode(Node node)
        throws ClientException, ServerException {
        checknull(node, "node");       
        _execute(REBOOTNODE, intint2Vect(node.getCell().getID(), node.getNodeID()));
    }
    public void reboot(Cell cell, boolean switches, boolean sp)
        throws ClientException, ServerException {
//        checknull(cell, "cell"); 
// cell may be null to reboot all, although no impl on device
        _execute(REBOOTCELL,
            intboolbool2Vect((cell == null) ? -1 : cell.getID(), switches, sp));
    }
    public void powerOff(Cell cell,  boolean switches, boolean sp)
        throws ClientException, ServerException {
        checknull(cell, "cell");
        _execute(POWERCELLOFF,
            intboolbool2Vect((cell == null) ? -1 : cell.getID(), switches, sp));
    }
    public void wipeDisks()
        throws ClientException, ServerException {
        _execute(WIPEDISKS, emptyVect);
    }
    
    public int addCell(String admIP, String dataIP)
        throws ClientException, ServerException {
        return
            ((Integer)_execute(ADDCELL, strstr2Vect(admIP, dataIP))).intValue();
    }
    public int delCell(Cell cell)
        throws ClientException, ServerException {
        return ((Integer)_execute(DELCELL, int2Vect(cell.getID()))).intValue();
    }

    public void enableDisk(Disk d)
        throws ClientException, ServerException {
        checknull(d, "disk");
        Node n = d.getNode();
        checknull(n, "node");
        _execute(ENABLEDISK, intintStr2Vect(n.getCell().getID(),
                n.getNodeID(), d.getDiskId()));
    }
    public void disableDisk(Disk d)
        throws ClientException, ServerException {   
        checknull(d, "disk");
        Node n = d.getNode();
        checknull(n, "node");     
        _execute(DISABLEDISK, intintStr2Vect(n.getCell().getID(),
                n.getNodeID(), d.getDiskId()));
    }
    
    static boolean init = false;
    static int perc = 0;  // todo implement this
    public int getRecoveryCompletionPercent(Cell cell)
        throws ClientException, ServerException {
        if (!init) {
            perc = ((cell.getID() + 1) * 20) % 100;
            init = true;
        }
        if (perc == 100)
            perc = 0;
        return (++perc);
    }
    public RootNamespace getMetadataConfig()
        throws ClientException, ServerException {
        // step 1/2: retrieve metadataConfig remotely
        String mdcfg = "";
        RootNamespace nameSpace = null;
        try {
            mdcfg = new String((byte[])_execute(GETMDCFG, emptyVect), "UTF-8");
            System.out.println("mdcfg= " + mdcfg);
            // step 2/2: create the tree structure
            nameSpace = RootNamespace.getInstance(mdcfg);
        } catch (EMDConfigException e) {
            throw new ClientException(e.getLocalizedMessage());
        } catch (java.io.UnsupportedEncodingException uee) {
            throw new ClientException(uee.getLocalizedMessage());
        }
        return nameSpace;
    }
    public void setMetadataConfig(RootNamespace rootNamespace)
        throws ClientException, ServerException {
        
        // write metadata config info to a byte array
        ByteArrayOutputStream baos =  new ByteArrayOutputStream();
        try {
            OutputStreamWriter osw = new OutputStreamWriter(baos, UTF8);
            rootNamespace.export(osw, true);
            osw.flush();
        } catch (IOException io) {
            throw new RuntimeException(
                      "Cannot read metadata configuration. " + io.getMessage());
        }
        _execute(SETMDCFG, bytearr2Vect(baos.toByteArray()));
    }
    
    public Set getReservedNamespaceNames() {
        return reservedNamespaceNames;
    }

    public CNS getCNSInfo() throws ClientException, ServerException {
        Hashtable res = (Hashtable)_execute(GETCNS, emptyVect);
        CNS cns = new CNS(getHValString(res, CNSACCT));
        cns.setProxy(getHValString(res, PROXYSRV));
        cns.setProxyPort(getHValInt(res, PROXYPORT));
        boolean authenticate = false;
        int authProxy = getHValInt(res, USEPROXYAUTH);
        if (authProxy == 1) {
            authenticate = true;
        }
        cns.setUseProxyAuth(authenticate);
        cns.setProxyUser(getHValString(res, PROXYUSR));
        return cns;
    }
    public void setCNSInfo(CNS cns) throws ClientException, ServerException {
        Hashtable hash = new Hashtable();
        hash.put(CNSACCT, cns.getAccount());
        hash.put(PROXYSRV, cns.getProxy());
        hash.put(PROXYPORT, new Integer(cns.getProxyPort()));
        hash.put(PROXYUSR, cns.getProxyUser());
        _execute(SETCNS, obj2Vect(hash));
    }
    public boolean isCNSRegistered() throws ClientException, ServerException {
        return ((Boolean)_execute(ISCNSREG, emptyVect)).booleanValue();
    }
    public int doCNSRegistration(String sunPasswd, String proxyAuthPasswd) 
        throws ClientException, ServerException {
        return ((Integer)_execute(DOCNSREG, 
                        strstr2Vect(sunPasswd, proxyAuthPasswd))).intValue();
    }

    /* testing */
    public static void printArr(Object[] arr) {
        if (arr != null)
            for (int i = 0; i < arr.length; i++)
                System.out.println(arr[i]);
    }
    
    /* used for testing only */
    public static void main(String args[]) throws Exception {
        
        String ip = (args.length == 1) ? args[0] : "rumble.east:8090";
            //"dev307-admin.sfbay:8090"; // "10.8.54.28:8090";

        System.out.println("Initializing...");
        AdminApi api = new AdminApiXML(ip);

        if (api.verifyPasswd("honeycomb"))
            System.out.println("password correct");
        else
            System.out.println("password incorrect");

        //System.out.println("Quorum=" + (api.hasQuorum(1) ? "y" : "n"));
        System.out.println("CellProps for 1st cell =" +
            api.getCellProps(api.getCells()[0].getID()));
        System.out.println("AdminIP=" + api.getAddress(ADDRT_ADMIN_IP));
        System.out.print("DataIP=" + api.getAddress(ADDRT_DATA_IP));
        System.out.print("SProcIP=" + api.getAddress(ADDRT_SP_IP));
 
        // System.out.print("setting DataIP...");
        // api.setAddress(ADDRT_DATA_IP, "202.203.204.222");
        // System.out.print("New DataIP=" + api.getAddress(ADDRT_DATA_IP));
        System.out.println("GatewayIP=" + api.getAddress(ADDRT_GTWAY_IP) +
                // "\nNetwork=  " + api.getAddress(ADDRT_NET) +
                "\nSubnet=   " + api.getAddress(ADDRT_SUBNET));
        // api.setAddress(ADDRT_SUBNET, "255.255.0.0");
        // System.out.println("New subnet=" + api.getAddress(ADDRT_SUBNET));
        System.out.println("NTPIPs="); 
        String[] ntps = api.getNTPAddrs(); printArr(ntps);
//        System.out.println("changing first ntp addrs...");
//        if (ntps != null && ntps.length > 0) {
//            ntps[0] = "3.4.4.4";
//            api.setNTPAddrs(ntps);
//        }
//        System.out.println("New NTPIPs="); printArr(api.getNTPAddrs());
        System.out.print("SMTPIP=" + api.getAddress(ADDRT_SMTP_IP) + " port="
                + api.getPort(PORTT_SMTP));
//        api.setPort(PORTT_SMTP, 25);
//        System.out.println("  port changed to "  + api.getPort(PORTT_SMTP));
        System.out.println("DNS is "
            + (api.isDnsEnabled() ? "enabled" : "disabled"));

        System.out.println("domain=" + api.getDomainName());
        System.out.println("search list=");
        String[] dnsSuffixes = api.getDnsSearch();
        printArr(dnsSuffixes);
        String[] newSuffixes = new String[dnsSuffixes.length + 1];
        for (int i = 0; i < dnsSuffixes.length; i++)
            newSuffixes[i] = dnsSuffixes[i];
        newSuffixes[dnsSuffixes.length] = new String("new.suffix");
        // System.out.println("NEW search list=");
        // api.setDnsSearch(newSuffixes);
        // printArr(api.getDnsSearch());
        
        System.out.println("Primary DNS=" + api.getAddress(ADDRT_PRIDNS_IP));
        System.out.println("Secondary DNS=" + api.getAddress(ADDRT_SECDNS_IP));
        //System.out.println("Verify passwd=" + api.verifyPasswd("honeycomb"));
        String[] clients = api.getClients();
        System.out.println("Auth.clients= "); printArr(clients); 
//        String firstClient[] = new String[] { clients[0], clients[1] };
//        System.out.println("Keeping first 2 clients only. New auth.clients=");
//        api.setClients(firstClient);
//        printArr(api.getClients()); 
 
        System.out.print("ToAddrs="); printArr(api.getEmailsTo());
        System.out.print("CcAddrs="); printArr(api.getEmailsCc());

//        System.out.println("Changing ToAddrs");
//        String newEmails[] = new String[] { "dontwriteme@sun"};
//        api.setEmailsTo(newEmails);
//        System.out.println("New ToAddrs="); printArr(api.getEmailsTo());
        
        System.out.println("gettin cells...");
        Cell c = api.getCells()[0];
        System.out.println("UnhealedFailure (cell " + c.getID() + ")="
            + api.getNumOfUnhealedFailures(c.getID()));
        
        System.out.println(c);
        Node node, nodes[] = api.getNodes(c);
        printArr(nodes);

        node = api.getNode(c, nodes[0].getNodeID());
        System.out.println("First node: " + node);
        /*
        System.out.println("shutdown node 5...");
        api.powerNodeOff(nodes[4]);
        System.out.println("done. new node:");
        System.out.println(nodes[4]);
         */
        
        Disk[] dsks = node.getDisks();
//        System.out.println("Disabling first disk");
//        dsks[0].disable();
//        node.refresh();
//        System.out.println("First node after first refresh: " + node);
//        System.out.println("powering node off"); api.powerNodeOff(node);
//        System.out.println("refreshing node info"); node.refresh();
//        System.out.println("First node after refresh:" + node);
        System.out.println("All disks in cell " + c.getID());
        dsks = api.getDisks(c);
        printArr(dsks);
//        System.out.println("\n Disabling one disk - will fail");
//        try {
//            dsks[0].disable();
//            dsks = api.getDisks(c);
//            printArr(dsks);
//        } catch (Exception e) {
//            System.out.println("failed - " + e.getLocalizedMessage());
//        }
        System.out.println("Metadata configuration: ");
        RootNamespace ns = api.getMetadataConfig();
//        ArrayList al = new ArrayList();
//        ns.getChildren(al, true);
//        System.out.print("Namespaces: ");
//        java.util.Iterator it = al.iterator();
//        while (it.hasNext()) {
//            System.out.print(((Namespace)it.next()).getName() + " ");
//        }
//        System.out.println();
        ns.export(new PrintWriter(System.out), true);

        System.out.print("updating schema...");
        Namespace newns = new Namespace(ns, "andrei_ns", true, true, false);
        newns.addField(new Field(newns, "myfield", Field.TYPE_STRING, true));
        ns.registerChild(newns);
        
        System.out.println("update cancelled... re-retrieving schema");
        ns = api.getMetadataConfig();
        ns.export(new PrintWriter(System.out), true);       
        /*                
        api.setMetadataConfig(ns);
        System.out.println("new schema:");
        ns = api.getMetadataConfig();
        ns.export(new PrintWriter(System.out), true);
        */
        
        System.out.println(reservedNamespaceNames.toString());

        Versions vers = api.getFwVersions(c);
        System.out.println("cellVer=" + vers.getCellVer() +
                           ",spBios=" + vers.getSPBios() +
                           ",spSmdc=" + vers.getSPSmdc() +
                           ",switch1=" + vers.getSwitch1Overlay() +
                           ",switch2=" + vers.getSwitch2Overlay());
        System.out.print("node bios versions="); printArr(vers.getBios());
        System.out.print("node smdc versions="); printArr(vers.getSmdc());
        System.out.print("IPMI Sensor information:");
        printArr(api.getSensors(node));

        System.out.println("CNS reg: " + api.getCNSInfo() + 
                "registered = " + (api.isCNSRegistered() ? "y" : "n"));

        System.out.println("Cell perfstats: ");
        System.out.println(api.getPerfStats(5, c));
        System.out.println("Node perfstats: ");
        System.out.println(api.getPerfStats(10, node));
        
        System.out.println("added cell" + api.addCell("admIP", "dataIP"));
        System.out.println("removed cell" + api.delCell(c));
    }

}

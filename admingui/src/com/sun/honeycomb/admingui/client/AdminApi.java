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


/* root class for the tree structure describing the metadata configuration */
import com.sun.honeycomb.emd.config.RootNamespace;
import java.util.Set;

/**
 * Specifies the internal API that sits between the presentation layer and 
 * the transport layer inside the AdminGUI; this interface hides the specifics 
 * of the transport protocol used to communicate with the managed cells.
 * @author Andrei Dancus
 */
public interface AdminApi {
        
    /* cell address types */
    public static final int ADDRT_ADMIN_IP   = 1;
    public static final int ADDRT_DATA_IP    = 2;
    public static final int ADDRT_SP_IP      = 8;
    public static final int ADDRT_GTWAY_IP   = 3;
    public static final int ADDRT_SUBNET     = 5;
    
    /* silo address types */
    
    public static final int ADDRT_NET        = 4;  
    public static final int ADDRT_SMTP_IP    = 6;
    public static final int ADDRT_LOG_IP     = 7;
    public static final int ADDRT_PRIDNS_IP  = 10;
    public static final int ADDRT_SECDNS_IP  = 11;
    public static final int PORTT_SMTP = 0;

    /* number of nodes in half-cell and full-cell configurations*/
    public static final int HALF_CELL_NODES  = 8;
    public static final int FULL_CELL_NODES  = 16;
    
    /* number of disks per node */
    public static final int DISKS_PER_NODE = 4;

    /**
     * @return protocol version used by the GUI client
     */
    public String getClientAPIVer();
    /**
     * API version used by the managed appliance.
     * In 1.1 this should be the same as the value returned by getClientAPIVer()
     */
    public String getServerAPIVer() throws ClientException, ServerException;

    /**
     * request full write access to the box.
     * This API should be used carefully since it may unnecessarly starve
     * other admin clients temporarly. Not normally needed since all
     * write type operations automatically aquire the lock
     */
    public boolean reqFullAccess()
        throws ClientException, ServerException;
    /**
     * @return true if already in read/write mode
     */
    public boolean fullAccess() // if false, then read-only
        throws ClientException, ServerException;
    /**
     * logout from the managed box, releasing the write lock (if held)
     */
    public void logout()
        throws ClientException, ServerException; 

    public boolean areSwitchesOk(int cellId)
        throws ClientException, ServerException;

    /**
     * @return the system time for the specified cell
     */
    public String getDate(int cellId)
        throws ClientException, ServerException;
    /**
     * @return the maximum number of allowed authorized data clients
     */
    public int getMaxNumAuthClients() 
        throws ClientException, ServerException;
    /**
     * @return the number of unhealed failures
     */
    public int getNumOfUnhealedFailures(int cellId)
        throws ClientException, ServerException;
    /**
     * @return true if the cell has quorum, false otherwise
     */
    public boolean hasQuorum(int cellId)
        throws ClientException, ServerException;

    public String getHADBStatus(Cell c)
        throws ClientException, ServerException;

    public String getAddress(int addrType)
        throws ClientException, ServerException;
    public void setAddress(int addrType, String addr)
        throws ClientException, ServerException;


    /**
     * @return return cell properties (IP addresses)
     */
    public CellProps getCellProps(int cellId)
        throws ClientException, ServerException;
    /**
     * sets cell properties (IP addresses). triggers a cell reboot in backend
     */  
    public void setCellProps(int cellId, CellProps props)
        throws ClientException, ServerException;
    
    /**
     * set the specified cell address. triggers a cell reboot in backend
     */
    public void setCellAddress(int cellId, int addrType, String addr)
        throws ClientException, ServerException;

    public int getPort(int portType)
        throws ClientException, ServerException;
    public void setPort(int portType, int portNum)
        throws ClientException, ServerException;
    public String[] getNTPAddrs()
        throws ClientException, ServerException;
    public void setNTPAddrs(String ips[])
        throws ClientException, ServerException;

    public boolean newSessionAndVerifyPasswd(String passwd)
        throws ClientException, ServerException;
    public boolean verifyPasswd(String passwd)
        throws ClientException, ServerException;
    public void setPasswd(String newPasswd)
        throws ClientException, ServerException;
    
    public String[] getClients() throws ClientException, ServerException;
    public void setClients(String ips[])
        throws ClientException, ServerException;
    
    public String[] getEmailsTo() throws ClientException, ServerException;
    public void setEmailsTo(String[] emails)
        throws ClientException, ServerException;
    public String[] getEmailsCc() throws ClientException, ServerException;
    public void setEmailsCc(String[] emails)
        throws ClientException, ServerException;

    public Cell[] getCells()
        throws ClientException, ServerException;
    public Node[] getNodes(Cell theCell)
        throws ClientException, ServerException;
    public Node getNode(Cell theCell, int nodeid)
        throws ClientException, ServerException;
    public Disk[] getDisks(Node theNode)
        throws ClientException, ServerException;
    public Disk[] getDisks(Node theNode, boolean cachedDisks)
        throws ClientException, ServerException;
    public Disk[] getDisks(Cell theCell)
        throws ClientException, ServerException; 
    public Switch[] getSwitches(Cell theCell)
        throws ClientException, ServerException;
    public Switch getSwitch(Cell cell, String switchId)
        throws ClientException, ServerException;
    public ServiceNode getSp(Cell cell)
        throws ClientException, ServerException;
    public Service[] getServices(Node theNode)
        throws ClientException, ServerException;
    public Versions getFwVersions(Cell theCell)
        throws ClientException, ServerException;
    public Sensor[] getSensors(Node theNode)
        throws ClientException, ServerException;
    public int getNumOfCells() throws ClientException, ServerException;
    
    // DNS methods. use set/getAddress() to set/get primary/sec. DNS IPs
    public void enableDns() throws ClientException, ServerException;
    public void disableDns() throws ClientException, ServerException;
    public boolean isDnsEnabled() throws ClientException, ServerException;
    public String getDomainName() throws ClientException, ServerException;
    public void setDomainName(String domain)
        throws ClientException, ServerException;
    public String[] getDnsSearch() throws ClientException, ServerException;
    public void setDnsSearch(String[] searchList)
        throws ClientException, ServerException;

    // API-s for more disrupting operations

    public void powerNodeOn(Node node) throws ClientException, ServerException;
    public void powerNodeOff(Node node) throws ClientException, ServerException;
    public void rebootNode(Node node) throws ClientException, ServerException;
    // pass null cell to reboot/poweroff ALL cells
    public void reboot(Cell cell, boolean switches, boolean sp)
        throws ClientException, ServerException;
    public void powerOff(Cell cell, boolean switches, boolean sp)
        throws ClientException, ServerException;
    public void wipeDisks()
        throws ClientException, ServerException;

    public int addCell(String admIP, String dataIP)
        throws ClientException, ServerException;
    public int delCell(Cell cell)
        throws ClientException, ServerException;

    public void enableDisk(Disk d) throws ClientException, ServerException;
    public void disableDisk(Disk d) throws ClientException, ServerException;

    public int getRecoveryCompletionPercent(Cell cell)
        throws ClientException, ServerException;
    
    public RootNamespace getMetadataConfig()
        throws ClientException, ServerException;
    public void setMetadataConfig(RootNamespace rootNamespace)
        throws ClientException, ServerException;
    
    public Set getReservedNamespaceNames(); // hack to be removed

    public PerfStats getPerfStats(int interval, Cell theCell)
        throws ClientException, ServerException;
    public PerfStats getPerfStats(int interval, Node theNode)
        throws ClientException, ServerException;
    
    // CNS registrations API-s

    public CNS getCNSInfo() throws ClientException, ServerException;
    public void setCNSInfo(CNS cns) throws ClientException, ServerException;
    public boolean isCNSRegistered() throws ClientException, ServerException;
    
    // these values must match those defined on server side in AdminClient.java
    public int doCNSRegistration(String sunPasswd, String proxyAuthPasswd) 
                                        throws ClientException, ServerException;
}

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


package com.sun.honeycomb.adm.client;

import com.sun.honeycomb.adm.common.Validate;
import com.sun.honeycomb.admin.mgmt.client.Fetcher;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.admin.mgmt.client.HCUpgrade;
import com.sun.honeycomb.admin.mgmt.client.HCFrus;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.admin.mgmt.client.HCNode;
import com.sun.honeycomb.admin.mgmt.client.HCNodes;
import com.sun.honeycomb.admin.mgmt.client.HCHadb;
import com.sun.honeycomb.admin.mgmt.client.HCDisk;
import com.sun.honeycomb.admin.mgmt.client.HCDisks;
import com.sun.honeycomb.admin.mgmt.client.HCSilo;
import com.sun.honeycomb.admin.mgmt.client.HCDDTasks;
import com.sun.honeycomb.admin.mgmt.client.HCCellProps;
import com.sun.honeycomb.admin.mgmt.client.HCSiloProps;
import com.sun.honeycomb.admin.mgmt.client.HCExpProps;
import com.sun.honeycomb.admin.mgmt.client.HCSensor;
import com.sun.honeycomb.admin.mgmt.client.HCVersions;
import com.sun.honeycomb.admin.mgmt.client.HCAlertAddr;
import com.sun.honeycomb.admin.mgmt.client.HCPerfStats;
import com.sun.honeycomb.admin.mgmt.client.HCSetupCell;
import com.sun.honeycomb.admin.mgmt.client.HCNDMP;
import com.sun.honeycomb.admin.mgmt.client.HCAuditLogger;

import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.common.AdminResourcesConstants;
import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.adm.cli.*;
import com.sun.honeycomb.adm.client.MultiCellLogDump.ExpPropsCookie;

import com.sun.honeycomb.adm.client.MultiCellSchemaUpdate.SchemaUpdateCookie;
import com.sun.honeycomb.adm.client.MultiCellSetCellProps.SetCellPropsCookie;

import com.sun.honeycomb.admin.mgmt.client.HCSP;
import com.sun.honeycomb.admin.mgmt.client.HCServiceTagCellData;
import com.sun.honeycomb.admin.mgmt.client.HCSwitch;

import com.sun.honeycomb.util.ExtLevel;

import java.io.PrintStream;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Constructor;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.net.QuotedPrintableCodec;

/**
 * This class executes various cli commands.  The methods that executed
 * in this class correspond to the
 * com.sun.honeycomb.admin.mgmt.server.HC*Adapter
 */
public class AdminClientImpl implements AdminClient {

    private static final Logger logger =
        Logger.getLogger(AdminClientImpl.class.getName());

    private static HCAuditLogger auditLogger = null;
    
    private final String classFullname;
    
    //
    // Perfstats related
    //
    static HCPerfStats _stats = null;
    static int _nodeId = -1;
    static int _interval = -1;
    static byte _cellId = -1;


    private final static int RETRY_COUNT= 3;

    //
    // Login semaphore management
    //
    protected Date _lastChecked = null;
    protected static final long LOGIN_TIMEOUT = 1000 * 60;
    protected long _sessionId = AdminClient.NOT_LOGGED_IN_SESSION_ID;
    protected byte _cachedCellId = -1;
    protected HCCell _cellInfo = null;
    protected Locale _loginLocale = null;


    // Identify internal calls to reboot
    private boolean _internalCall = false;
    
    // Max rules that can be setup on switch
    private static final short MAX_RULES = 120;
    // Rules needed for icmp, udp,arp etc. 20 is big enough
    private static final short REQUIRED_RULES = 20;
    private static final int HALF_CELL_NODES = 8;


    public int verifyNtpServers(String ntpServers)
        throws MgmtException, ConnectException{

        HCCell cell = getCell(SiloInfo.getInstance().getMasterUrl());
        BigInteger retCode=cell.verifyNtpServers(ntpServers);        
        return(retCode.intValue());

    }

    /**
     * Return the max number of Auth clients that can be specified
     * @param siloProps the current configuration properties of the hive
     * @return int the max number of Authorized clients that can be specified 
     */
    public int getMaxNumAuthClients(HCSiloProps siloProps) 
        throws MgmtException, ConnectException {
        int num_rules = 0;
        HCCell cell = getCell(SiloInfo.getInstance().getMasterUrl());
        //
        // Inefficent; has to fetch all the nodes
        //
        int num_nodes = getNumNodes(cell.getCellId());
        try {
            num_rules = Integer.parseInt(siloProps.getNumAuthRules());
        } catch (NumberFormatException e) {
	    logger.log(Level.SEVERE, 
                       "CLI Internal: configuration error. "+
                       "Failed to parse authNumRules."+
                       " authNumRules=" + 
                       siloProps.getNumAuthRules());
            throw new 
                MgmtException("Configuration error. Failed to parse authNumRules");
        }
        if (HALF_CELL_NODES == num_nodes && num_rules > num_nodes)
            return ((MAX_RULES - REQUIRED_RULES)/((num_nodes * 2) + 1));
        else
            return ((MAX_RULES - REQUIRED_RULES)/(num_rules + 1));
    }

    /**
     * Get the date for the specified cellid
     * @param cellId the id of the cell to retrieve the date from
     * @return String the date string
     */
    public String getDate(byte cellId)
        throws MgmtException, ConnectException {

        HCCell cell = getCell(cellId);
        return(cell.getDate());

    }

    public String[] getLanguages() 
        throws MgmtException,ConnectException {
        ArrayList languages = new ArrayList();
        HCCell cell = getCell(SiloInfo.getInstance().getMasterUrl());
        Iterator<String> ite = cell.getLanguages().iterator();
        while (ite.hasNext()) {
            String curLanguage=ite.next();
            languages.add(curLanguage);
        }
        String[] example = new String[languages.size()];
        return (String[])languages.toArray(example);

    }

    //
    // Implement retry and make this geenric fixme
    //
    public void setLocale(String newLanguageString)
        throws MgmtException,ConnectException {
        HCCell cells[] = getCells(false);
        
        Object [] params = {Long.toString(this._sessionId),
                            newLanguageString};
        this.extLog(ExtLevel.EXT_INFO,
                AdminResourcesConstants.MSG_KEY_SET_LOCALE,
                params, "setLocale");

        for(int i=0;i<cells.length;i++) {
            cells[i].setLanguage(newLanguageString);
            cells[i].push();
        }
    }

    public String getLocale()
        throws MgmtException,ConnectException {
        HCCell cell = getCell(SiloInfo.getInstance().getMasterUrl());
        return cell.getLanguage();

    }

    public AdminClientImpl()
        throws MgmtException, ConnectException, PermissionException {
        super();
        classFullname = this.getClass().getName();
    }


    public HCSilo getSilo(String url) 
        throws MgmtException,ConnectException {
        HCSilo silo = Fetcher.fetchHCSilo(url);
        return silo;
    }

    public byte getMasterCellId()
        throws MgmtException,ConnectException {
        SiloInfo.Cell masterCell = SiloInfo.getInstance().getMasterCell();
        return masterCell.cellId;
    }

    
    public int getNumCells()
        throws MgmtException,ConnectException {
        return SiloInfo.getInstance().getCellCount();
    }

    public String getAdminVip(byte cellId)
        throws MgmtException,ConnectException {
        return SiloInfo.getInstance().getAdminVip(cellId);
    }



    public HCUpgrade getUpgrade(byte cellId)
        throws MgmtException,ConnectException  {        
        HCUpgrade upgrade =
          Fetcher.fetchHCUpgrade(SiloInfo.getInstance().getServerUrl(cellId));
        return upgrade;
    }
    
    private HCAuditLogger getAuditLogger() 
        throws MgmtException,ConnectException  {
        auditLogger =
            Fetcher.fetchHCAuditLogger(SiloInfo.getInstance().
                getServerUrl(getMasterCellId()));
        return auditLogger;
    }
    
    private void clearHCAuditLogger()    {
        auditLogger = null;
    }

    private HCPerfStats getPerfstats(byte cellId)
        throws MgmtException,ConnectException  {
        HCPerfStats stats =
          Fetcher.fetchHCPerfStats(SiloInfo.getInstance().getServerUrl(cellId));
        return stats;
    }

    public HCDDTasks getDdTasks(byte cellId) 
        throws MgmtException,ConnectException  {
        HCDDTasks tasks =
          Fetcher.fetchHCDDTasks(SiloInfo.getInstance().getServerUrl(cellId));
        return tasks;
    }

    public HCVersions getVersions(byte cellId)
        throws MgmtException,ConnectException  {
        HCVersions versions =
            Fetcher.fetchHCVersions(SiloInfo.getInstance().getServerUrl(cellId));
        return versions;
    }

    public HCCell[] getCells(boolean ignoreDeadCells)
        throws MgmtException,ConnectException {

        HCCell[] cells = new HCCell[getNumCells()];
        Iterator iter = SiloInfo.getInstance().getCells().iterator();
        int i = 0;
        while (iter.hasNext()) {
            SiloInfo.Cell curCell = (SiloInfo.Cell) iter.next();
            try {
                cells[i]=getCell(SiloInfo.getInstance().getServerUrl(curCell.cellId));
            } catch (Exception e) {
                //
                // Sadly and lamely, mgmt is throwing a generic exception when
                // we fail to connect.
                //
                if(ignoreDeadCells) {
                    //
                    // Failed to get the cell; we'll generate a dummy
                    //
                    cells[i] = new HCCell();
                    cells[i].setIsAlive(false);
                    cells[i].setCellId(curCell.cellId);
                    logger.log(Level.WARNING,"Ignoring dead cell: " + curCell.cellId , e);
                } else {
                    //
                    // if we're not ignoring trouble, pass it on up the stack
                    //
                    logger.log(Level.SEVERE,"Dead cell: " + curCell.cellId , e);

                    throw new MgmtException(e.getMessage());
                }
            }
            i++;
        }
        //
        // Check that at least one cell is valid. If not, throw an exception
        //
        for(i=0;i < cells.length;i++) {
            if(true == cells[i].isIsAlive()) 
                return cells;
        }
        throw new MgmtException("No cells reachable, fatal");
    }


    public int addCell(String adminVIP, String dataVIP)
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
        
        HCCell cell = getCell(SiloInfo.getInstance().getMasterUrl());
        Object [] params ={Long.toString(this._sessionId),
                            adminVIP, dataVIP};
        this.extLog(ExtLevel.EXT_INFO, AdminResourcesConstants.MSG_KEY_ADD_CELL,
                params, "addCell");
        BigInteger result = cell.addCell(new StatusCallback(),
          adminVIP, dataVIP);
        return result.intValue();
    }

    public int delCell(byte cellId) 
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
        HCCell cell = getCell(SiloInfo.getInstance().getMasterUrl());
        Object []  params = {Long.toString(this._sessionId),
                                 Byte.toString(cellId)};
        this.extLog(ExtLevel.EXT_INFO,
                AdminResourcesConstants.MSG_KEY_REMOVE_CELL,
                params, "delCell");
        BigInteger result = cell.delCell(new Byte(cellId));
        return result.intValue();
    }



    public int getNumNodes(byte cellId)
        throws MgmtException,ConnectException {
        return getNumNodes(getCell(SiloInfo.getInstance().getServerUrl(cellId)));
    }


    public int getNumNodes(HCCell cell)
        throws MgmtException,ConnectException {
        return cell.getNumNodes().intValue();
    }

    public int getNumAliveNodes(byte cellId)
        throws MgmtException,ConnectException {
        return getNumAliveNodes(getNodes(SiloInfo.getInstance().getServerUrl(cellId)));
    }


    public int getNumAliveNodes(HCNodes nodes)
        throws MgmtException,ConnectException {
        return nodes.getNumAliveNodes().intValue();
    }




    
    
    /**
     * @see AdminClient#getSwitch(byte, String)
     */
    public HCSwitch getSwitch(byte cellId, String fruId)
        throws MgmtException, ConnectException {
        return Fetcher.fetchHCSwitch(SiloInfo.getInstance().getServerUrl(cellId), fruId);
    }

    /**
     * @see AdminClient#getSwitches(byte)
     */
    public HCSwitch[] getSwitches(byte cellId)
        throws MgmtException, ConnectException {
        List switches = new ArrayList();
	for (int i=1; i <= 2; i++) {
	    String fruId = new StringBuffer("SWITCH-").append(i).toString(); 
	    HCSwitch fru = getSwitch(cellId, fruId);
	    switches.add(fru);
        }
        return (HCSwitch[])switches.toArray(new HCSwitch[switches.size()]);
    }

    
    /**
     * @see AdminClient#getSp(byte)
     */
    public HCSP getSp(byte cellId)
        throws MgmtException, ConnectException {
        return Fetcher.fetchHCSP(SiloInfo.getInstance().getServerUrl(cellId));
    }


    public HCNode getMaster(byte cellId)
        throws MgmtException,ConnectException {
        return (getMaster(SiloInfo.getInstance().getServerUrl(cellId)));
    }

    public HCNode getMaster(HCNodes nodes)
        throws MgmtException,ConnectException {
        return nodes.getNodesList().get(nodes.getMasterNode().intValue());
    }

    public HCNode getMaster(String url) 
        throws MgmtException,ConnectException {
        return (getMaster(getNodes(url)));
    }


    /**
     * @see AdminClinet#getNumDisksPerNode(HCCell)
     */
    public int getNumDisksPerNode(HCCell cell)
        throws MgmtException, ConnectException {
	return cell.getNumDisksPerNode().intValue();
    }

    public HCSetupCell getSetupCell()
        throws MgmtException, ConnectException {

        // Fetch dummy object from server.
        HCSetupCell props = 
          Fetcher.fetchHCSetupCell(SiloInfo.getInstance().getSiloUrl());

        //
        // Fill initial values from HCCell and HCSilo adapters
        //
        HCCellProps cellProps = 
          getCellProps(SiloInfo.getInstance().getUniqueCellId());

        HCSiloProps siloProps = getSiloProps();
        props.setCellId(Byte.toString(SiloInfo.getInstance().getUniqueCellId()));

        if (cellProps.getAdminVIP() != null) {       
            props.setAdminVIP(cellProps.getAdminVIP());        
        } else {
            props.setAdminVIP("uninitialized");
        }
        
        if (cellProps.getDataVIP() != null) {       
            props.setDataVIP(cellProps.getDataVIP());        
        } else {
            props.setDataVIP("uninitialized");  
        }

        if (cellProps.getSpVIP() != null) {       
            props.setSpVIP(cellProps.getSpVIP());        
        } else {
            props.setSpVIP("uninitialized");        
        }

        if (cellProps.getSubnet() != null) {       
            props.setSubnet(cellProps.getSubnet());        
        } else {
            props.setSubnet("uninitialized");
        }

        if (cellProps.getGateway() != null) {       
            props.setGateway(cellProps.getGateway());        
        } else {
            props.setGateway("uninitialized");
        }

        if (siloProps.getNtpServer() != null) {       
            props.setNtpServer(siloProps.getNtpServer());        
        } else {
            props.setNtpServer("uninitialized");
        }
        if (siloProps.getSmtpServer() != null) {       
            props.setSmtpServer(siloProps.getSmtpServer());
        } else {
            props.setSmtpServer("uninitialized");
        }

        if (siloProps.getSmtpPort() != null) {       
            props.setSmtpPort(siloProps.getSmtpPort());
        } else {
            props.setSmtpPort("uninitialized");
        }

        props.setAuthorizedClients("all");
        if (siloProps.getAuthorizedClients() != null) {
            props.setAuthorizedClients(siloProps.getAuthorizedClients());
        } 

        if (siloProps.getExtLogger() != null) {   
            props.setExtLogger(siloProps.getExtLogger());        
        } else {
            props.setExtLogger("uninitialized");       
        }

        if (siloProps.getDns() != null) {       
            props.setDns(siloProps.getDns());  
        } else {
            props.setDns("uninitialized");
        }

        if (siloProps.getDomainName() != null) {       
            props.setDomainName(siloProps.getDomainName());
        } else {
            props.setDomainName("uninitialized");
        }
        if (siloProps.getDnsSearch() != null) {
            props.setDnsSearch(siloProps.getDnsSearch());
        } else {
            props.setDnsSearch("uninitialized");
        }

        if (siloProps.getPrimaryDnsServer() != null) {
            props.setPrimaryDnsServer(siloProps.getPrimaryDnsServer());
        } else {
            props.setPrimaryDnsServer("uninitialized");
        }
        if (siloProps.getSecondaryDnsServer() != null ) {
            props.setSecondaryDnsServer(siloProps.getSecondaryDnsServer());
        } else {
            props.setSecondaryDnsServer("uninitialized");
        }
	if (siloProps.getNumAuthRules() != null ) {
            props.setNumAuthRules(siloProps.getNumAuthRules());
        } else {
            props.setSecondaryDnsServer("uninitialized");
        }
        return props;
    }

    public void setSetupCell(HCSetupCell newProps) 
        throws MgmtException, ConnectException,
        PermissionException, AdmException {

        if(!loggedIn()) 
            throw new PermissionException();

        byte thisCellId = SiloInfo.getInstance().getUniqueCellId(); 
	HCCell cell =getCell(SiloInfo.getInstance().getServerUrl(thisCellId));

	// This push call should be changed in a future release to
	// invoke a method on the HCSetupCellAdapter instead of doing the
	// push.  See CR6684437
	newProps.push();
        
        String methodName = "setSetupCell";
      	logSetupCellProps(Byte.toString(thisCellId), newProps, methodName);
	try {
	    System.out.print("\nUpdating Switches ...");
	    cell.updateSwitch(BigInteger.valueOf(0));
	    System.out.print("\nUpdating Service Node ...");
	    cell.updateServiceProcessor(BigInteger.valueOf(0));
            this._internalCall = true;
	    System.out.println("\nRebooting nodes, switches, and service node ...");
            rebootCell(cell, true, true);
        } finally {
	    System.out.println();
            this._internalCall = false;
        }
    }

    public boolean getSwitchesState(byte cellId) 
        throws MgmtException,ConnectException {
        HCCell cell = getCell(cellId);
        if(cell.getSwitchesState(BigInteger.valueOf(0)).intValue()==0)
            return true;
        else
            return false;
    }

    public HCCellProps getCellProps(byte cellId) 
        throws MgmtException,ConnectException {

        HCCell cell = Fetcher.fetchHCCell(SiloInfo.getInstance().getServerUrl(cellId));
        return getCellProps(cell);
    }
    

    public HCCellProps getCellProps(HCCell cell)
        throws MgmtException, ConnectException {
        HCCellProps cellProps=null;
        cellProps = cell.getCellProps();            
        return cellProps;       
    }

    public void setCellProps(byte cellId, HCCellProps newProps) 
        throws MgmtException, ConnectException, PermissionException {
        HCCell cell = getCell(SiloInfo.getInstance().getServerUrl(cellId));
        setCellProps(cell,newProps);
    }

    public void setCellProps(HCCell cell, HCCellProps newProps) 
        throws MgmtException, ConnectException, PermissionException {

        if (!loggedIn()) 
            throw new PermissionException();
        
        boolean update = false;
        Level logLevel = ExtLevel.EXT_INFO;
        String logMethod = "setCellProps";
        String logCellId = Byte.toString(cell.getCellId());
        String logSessionId = Long.toString(this._sessionId);

        HCCellProps oldProps = cell.getCellProps();
        if (newProps.getAdminVIP() == null) {
            newProps.setAdminVIP(oldProps.getAdminVIP());
        } else {
            update = true;
            this.logCellPropsChange(logLevel, 
                    AdminResourcesConstants.MSG_KEY_SET_ADMIN_IP,
                    logSessionId, logCellId, newProps.getAdminVIP(), logMethod);
        }
        
        if (newProps.getDataVIP() == null) {
            newProps.setDataVIP(oldProps.getDataVIP());
        } else {
            update = true;
            this.logCellPropsChange(logLevel, 
                    AdminResourcesConstants.MSG_KEY_SET_DATA_IP,
                    logSessionId, logCellId, newProps.getDataVIP(), logMethod);
        }

        if (newProps.getSpVIP() == null) {
            newProps.setSpVIP(oldProps.getSpVIP());
        } else {
            update = true;
            this.logCellPropsChange(logLevel, 
                    AdminResourcesConstants.MSG_KEY_SET_SERVICE_NODE_IP,
                    logSessionId, logCellId, newProps.getSpVIP(), logMethod);
        }

        if (newProps.getSubnet() == null) {
            newProps.setSubnet(oldProps.getSubnet());
        } else {
            update = true;
            this.logCellPropsChange(logLevel, 
                    AdminResourcesConstants.MSG_KEY_SET_SUBNET_MASK,
                    logSessionId, logCellId, newProps.getSubnet(), logMethod);
        }

        if (newProps.getGateway() == null) {
            newProps.setGateway(oldProps.getGateway());
        } else {
            update = true;
            this.logCellPropsChange(logLevel, 
                    AdminResourcesConstants.MSG_KEY_SET_GATEWAY_IP,
                    logSessionId, logCellId, newProps.getGateway(), logMethod);
        }
        if (update == false) {
            return;
        }

        MultiCellOp [] setCellProps = 
          allocateMultiCellOp(MultiCellSetCellProps.class);
        SetCellPropsCookie cookie = new SetCellPropsCookie(newProps,
          cell.getCellId());
        MultiCellRunner runner = new MultiCellRunner(setCellProps);
        runner.setCookie(cookie);
        runner.start();
        runner.waitForResult();
        SiloInfo.getInstance().updateCell(cell, newProps);
    }
    
    private void logCellPropsChange(Level logLevel, String msgKey,
            String logSessionId, String cellId, String propValue,
            String logMethod)
            throws MgmtException, ConnectException {
        Object[] params = {logSessionId, propValue, cellId};
        this.extLog(logLevel, msgKey, params, logMethod);
        
    }

    /*
     *  Audit log of setup cell property settings
     */
    protected void logSetupCellProps(String cellId, HCSetupCell newProps, 
            String logMethod) {
        
        String methodName = "setSetupCell";
        Object [] params;
        String logSessionId = Long.toString(this._sessionId);
        String msgKey = "";
        Level logLevel = ExtLevel.EXT_INFO;       
        
        // Cell config properties
        msgKey = AdminResourcesConstants.MSG_KEY_SETUP_CELL_IP_CONFIG;
        params = new Object[] {logSessionId, cellId, newProps.getAdminVIP(),
                    newProps.getDataVIP(), newProps.getSpVIP(),
                    newProps.getSpVIP(), newProps.getSubnet()};
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) {
                params[i] = "";
            }
        }
        this.extLog(logLevel, msgKey, params, logMethod);
        
        // Hive configuration
        msgKey = AdminResourcesConstants.MSG_KEY_SETUP_CELL_HIVE_CONFIG;
        params = new Object[] {logSessionId, cellId, 
                        newProps.getNtpServer(), newProps.getSmtpServer(),
                        newProps.getSmtpPort(), newProps.getExtLogger(),
                        newProps.getAuthorizedClients()};
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) {
                params[i] = "";
            }
        } 
        
        this.extLog(logLevel, msgKey, params, logMethod);
        
        //  DNS Configuration
        params = new Object[] {logSessionId, cellId, newProps.getDomainName(),
                    newProps.getPrimaryDnsServer(),
                    newProps.getSecondaryDnsServer(),
                     newProps.getDnsSearch()};
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) {
                params[i] = "";
            }
        }
        String dnsSetting = newProps.getDns();
        if (dnsSetting != null && 
                (dnsSetting.compareToIgnoreCase("y") == 0 ||
                 dnsSetting.compareToIgnoreCase("Yes") == 0)) {
            msgKey = AdminResourcesConstants.MSG_KEY_SETUP_CELL_DNS_ENABLED;
         } else {
            msgKey = AdminResourcesConstants.
                    MSG_KEY_SETUP_CELL_DNS_DISABLED;
         }
         
        this.extLog(logLevel, msgKey, params, logMethod);

}
    
    public HCSiloProps getSiloProps()
        throws MgmtException,ConnectException {
        return getCell(SiloInfo.getInstance().getMasterUrl()).getSiloProps();
    }

    public int setSiloProps(HCSiloProps newProps) 
        throws MgmtException, ConnectException, PermissionException {

        if(!loggedIn()) 
            throw new PermissionException();

        MultiCellOp [] setSiloProps = 
          allocateMultiCellOp(MultiCellSetSiloProps.class);
        MultiCellRunner runner = new MultiCellRunner(setSiloProps);
        runner.setCookie(newProps);
        try {
            this.logSiloPropsChanges(newProps, "setSiloProps");
        }  catch (Exception ex) {
            // log
            logger.logp(Level.WARNING, AdminClientImpl.class.getName(),
                    "setSiloProps", "Unable to log message", ex);            
        }
        runner.start();
        return runner.waitForResult();
    }

    /*
     *  Log the silo properties that have changed. 
     *  Note: The GUI sends all the properties whether or not they've changed
     *  so must compare new value to existing values to determine if a new
     *  value is really being set.
     */
    private void logSiloPropsChanges(HCSiloProps newProps, String logMethod)
        throws MgmtException, ConnectException {
        
        Object [] params;
        String logSessionId = Long.toString(this._sessionId);
        String propValue;
        String msgKey = "";
        Level logLevel = ExtLevel.EXT_INFO;
        
        for (int prop = 0; prop < 10; prop++) {
            // reset
            propValue = null;
            
            switch (prop) {
                case 0:
                    propValue = newProps.getNtpServer();
                    msgKey = AdminResourcesConstants.MSG_KEY_SET_NTP_SERVER;
                    break;
                case 1:
                    propValue = newProps.getSmtpServer();
                    msgKey = AdminResourcesConstants.MSG_KEY_SET_SMTP_SERVER;
                    break;
                case 2:
                    propValue = newProps.getSmtpPort();
                    msgKey = AdminResourcesConstants.MSG_KEY_SET_SMTP_PORT;
                    break;
                case 3:
                    propValue = newProps.getExtLogger();
                    msgKey = AdminResourcesConstants.MSG_KEY_SET_EXT_LOGGER;
                    break;
                case 4:
                    propValue = newProps.getAuthorizedClients();
                    msgKey = AdminResourcesConstants.MSG_KEY_SET_AUTH_CLIENTS;
                    break;
                case 5:
                    propValue = newProps.getDns();
                    msgKey = AdminResourcesConstants.MSG_KEY_SET_DNS;
                    break;
                case 6:
                    propValue = newProps.getDomainName();
                    msgKey = AdminResourcesConstants.MSG_KEY_SET_DOMAIN_NAME;
                    break;
                case 7:
                    propValue = newProps.getPrimaryDnsServer();
                    msgKey = AdminResourcesConstants.
                                MSG_KEY_SET_PRIMARY_DNS_SERVER;
                    break;
                case 8:
                    propValue = newProps.getSecondaryDnsServer();
                    msgKey = AdminResourcesConstants.
                                MSG_KEY_SET_SECONDARY_DNS_SERVER;
                    break;
                case 9:
                    propValue = newProps.getDnsSearch();
                    msgKey = AdminResourcesConstants.
                                MSG_KEY_SET_DNS_SEARCH_PATH;
                    break;
                default :
                    //ignore
                    break;
                    
            }   // end switch on silo property values
            
            if (propValue != null) {
                params = new Object[] {logSessionId, propValue};
                this.extLog(logLevel, msgKey, params, logMethod);
            }
        }   // end for
    }

    public int getNumAliveDisks(byte cellId)
        throws MgmtException,ConnectException {
        return getNodes(SiloInfo.getInstance().getServerUrl(cellId)).
            getNumAliveDisks().intValue();
    }

    public int getNumAliveDisks(HCNodes nodes)
        throws MgmtException,ConnectException {
        return nodes.getNumAliveDisks().intValue();
    }

     /** 
     * @see com.sun.honeycomb.adm.client.AdminClient#getFrus(byte)
     */
    public HCFrus getFRUs(byte cellId ) 
        throws MgmtException,ConnectException {
        return getFRUs(SiloInfo.getInstance().getServerUrl(cellId));
    }
    
    public HCFrus getFRUs(String url) 
        throws MgmtException,ConnectException {
        return Fetcher.fetchHCFrus(url);
    }



    public boolean getPossibleDataLoss(byte cellId)
        throws MgmtException,ConnectException {
        return getCell(SiloInfo.getInstance().getServerUrl(cellId)).
            isPossibleDataLoss();
    }
    public boolean getPossibleDataLoss(HCCell cell)
        throws MgmtException,ConnectException {
        return cell.isPossibleDataLoss();
    }

    public int getUnhealedFailures (byte cellId)
        throws MgmtException,ConnectException {
        return getCell(SiloInfo.getInstance().getServerUrl(cellId)).
            getNoUnhealeadFailures().intValue();
    }
    public int getUnhealedFailures (HCCell cell)
        throws MgmtException,ConnectException {
        return cell.getNoUnhealeadFailures().intValue();
    }

    public int getUnhealedFailuresUnique (byte cellId)
        throws MgmtException,ConnectException {
        return getCell(SiloInfo.getInstance().getServerUrl(cellId)).
            getNoUnhealeadUniqueFailures().intValue();
    }
    public int getUnhealedFailuresUnique (HCCell cell)
        throws MgmtException,ConnectException {
        return cell.getNoUnhealeadUniqueFailures().intValue();
    }

    public long getEndTimeLastRecoveryCycle (byte cellId)
        throws MgmtException,ConnectException {
        return getCell(SiloInfo.getInstance().getServerUrl(cellId)).
            getEndTimeLastRecoverCycle();
    }
    public long getEndTimeLastRecoveryCycle (HCCell cell)
        throws MgmtException,ConnectException {
        return cell.getEndTimeLastRecoverCycle();
    }

    public long getQueryIntegrityTime (byte cellId)
        throws MgmtException,ConnectException {
        return getCell(SiloInfo.getInstance().getServerUrl(cellId)).
            getQueryIntegrityTime();
    }
    public long getQueryIntegrityTime (HCCell cell)
        throws MgmtException,ConnectException {
        return cell.getQueryIntegrityTime();
    }


    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#getFreeDiskSpace(HCCell)
     */
    public double getFreeDiskSpace (HCDisks disks) 
        throws MgmtException,ConnectException {
	 return Double.valueOf(disks.getEstimatedFreeDiskSpace()).
             doubleValue();
    }

    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#getFreeDiskSpace(byte)
     */
    public double getFreeDiskSpace (byte cellId)
        throws MgmtException, ConnectException {
	 HCDisks disks= getDisks(SiloInfo.getInstance().getServerUrl(cellId));
	 return getFreeDiskSpace(disks);
    }


    public int wipeDisks() 
        throws MgmtException, ConnectException, PermissionException {
        if (!loggedIn()) 
            throw new PermissionException();
        
        MultiCellOp [] ops = allocateMultiCellOp(MultiCellWipe.class);
        MultiCellRunner runner = new MultiCellRunner(ops);
        Object [] params = {Long.toString(this._sessionId)};
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_WIPE, params, "wipeDisks");
        runner.start();
        return runner.waitForResult();
    }


    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#wipeDisk(byte)
     */
    public int wipeDisks (byte cellId) 
        throws MgmtException, ConnectException, PermissionException {

        HCCell cell= getCell(SiloInfo.getInstance().getServerUrl(cellId));
	return wipeDisks(cell);
    }

    /**
     * Wipe disk for the specified cell
     * @param cell the cell to wipe the disks on
     * @return int 0 on success, -1 on failure
     */
    public int wipeDisks (HCCell cell)
        throws MgmtException, ConnectException, PermissionException{

        if(!loggedIn()) 
            throw new PermissionException();

        Object [] params = {Long.toString(this._sessionId),
                            Byte.toString(cell.getCellId())};
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_WIPE_CELL_DISKS, params,
                "wipeDisks");

        BigInteger result = cell.wipe(new StatusCallback(),BigInteger.valueOf(0));
	return result.intValue();
    }

    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#enableDisk(byte,String)
     */
    public int enableDisk(byte cellId,String diskId) 
        throws MgmtException, ConnectException, PermissionException {
	
        HCDisk disk = getDisk(cellId, diskId);
	return enableDisk(disk);
    }

    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#enableDisk(HCDisk)
     */
    public int enableDisk(HCDisk disk)
        throws MgmtException, ConnectException, PermissionException {
	
        if (!loggedIn()) 
            throw new PermissionException();
        if (null==disk)
            throw new MgmtException("Attempted to enable nonexistent disk.");

        Object [] params = {Long.toString(this._sessionId), disk.getDiskId(),
                            disk.getNodeId().toString()};
        
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_ENABLE_DISK, params,
                "enableDisk");

	BigInteger result = disk.enable(new StatusCallback(),BigInteger.valueOf(0));
        return result.intValue();
    }

    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#disableDisk(byte,String)
     */
    public int disableDisk(byte cellId, String diskId)
        throws MgmtException, ConnectException, PermissionException {
	
        HCDisk disk = getDisk(cellId, diskId);
	return disableDisk(disk);
    }

    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#disableDisk(HCDisk)
     */
    public int disableDisk(HCDisk disk)
        throws MgmtException, ConnectException, PermissionException {
	
        if (!loggedIn()) 
            throw new PermissionException();
	if (null == disk)
            throw new MgmtException("Attempted to disable nonexistent disk.");

        Object [] params = {Long.toString(this._sessionId), disk.getDiskId(),
                            disk.getNodeId().toString()};
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_DISABLE_DISK, params,
                "disableDisk");

	BigInteger result = disk.disable(new StatusCallback(),BigInteger.valueOf(0));
        return result.intValue();
    }

    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#onlineDisk(byte,String)
     */
    public int onlineDisk(byte cellId, String diskId)
        throws MgmtException, ConnectException, PermissionException {
	
        HCDisk disk = getDisk(cellId, diskId);
        return onlineDisk(disk);
    }

    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#onlineDisk(HCDisk)
     */
    public int onlineDisk(HCDisk disk)
        throws MgmtException, ConnectException, PermissionException {
	
        if (!loggedIn()) 
            throw new PermissionException();
        if (null == disk)
            throw new MgmtException("Attempted to online nonexistent disk.");

        Object [] params = {Long.toString(this._sessionId), disk.getDiskId(),
                            disk.getNodeId().toString()};
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_ONLINE_DISK, params,
                "onlineDisk");
        
        BigInteger result = disk.online(new StatusCallback(),
            BigInteger.valueOf(0));
	return result.intValue();
    }

    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#offlineDisk(byte,String)
     */
    public int offlineDisk(byte cellId, String diskId)
        throws MgmtException, ConnectException, PermissionException {
	
        HCDisk disk = getDisk(cellId, diskId);
        return offlineDisk(disk);
    }

    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#offlineDisk(HCDisk)
     */
    public int offlineDisk(HCDisk disk)
        throws MgmtException, ConnectException, PermissionException {
	
        if (!loggedIn()) 
            throw new PermissionException();
        if (null == disk)
            throw new MgmtException("Attempted to offline nonexistent disk.");

        Object [] params = {Long.toString(this._sessionId), disk.getDiskId(),
                            disk.getNodeId().toString()};
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_OFFLINE_DISK, params,
                "offlineDisk");
        
        BigInteger result = disk.offline(BigInteger.valueOf(0));
        return result.intValue();
    }

    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#wipeDisk(byte,String)
     */
    public int wipeDisk(byte cellId, String diskId)
        throws MgmtException, ConnectException, PermissionException {

        HCDisk disk = getDisk(cellId, diskId);
        return wipeDisk(disk);
    }


    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#wipeDisk(HCDisk)
     */
    public int wipeDisk(HCDisk disk)
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
        if (null == disk)
            throw new MgmtException("Attempted to wipe nonexistent disk.");
        
        Object [] params = {Long.toString(this._sessionId), disk.getDiskId(),
                            disk.getNodeId().toString()};
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_WIPE_DISK, params,
                "wipeDisk");
        BigInteger result = disk.wipe(new StatusCallback(),BigInteger.valueOf(0));
        return result.intValue();
    }


    public String getCryptedPasswd() 
        throws MgmtException, ConnectException {

        return decode(getCell(SiloInfo.getInstance().getMasterUrl())
                      .getCryptedPassword());

    } 


    public boolean setPasswd(String cryptedPass)
        throws MgmtException, ConnectException, PermissionException{
        if(!loggedIn()) 
            throw new PermissionException();
        String passwd= new String(cryptedPass);
        HCCell cells[] = getCells(false);

        Object[] params = {Long.toString(this._sessionId)};
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_SET_PASSWORD, params,
                "setPasswd");
        for(int i=0;i<cells.length;i++) {
            boolean success=false;
            MgmtException failEx=null;
            for (int j=0; (j < RETRY_COUNT) && (false==success);j++) {
                try {
                    cells[i].setEncryptedPasswd(encode(passwd));
                    cells[i].push();
                    success=true;
                } catch (MgmtException e) {
                    failEx=e;
                    //
                    // We'll retry here
                    //
                }
            }
            if(false == success) {
                throw failEx;
            }
        }
        return true;
    } // write mode
    
    public void setPublicKey(String key)
        throws  MgmtException, ConnectException, PermissionException{
        if(!loggedIn()) 
            throw new PermissionException();
        HCCell cells[] = getCells(false);
        for(int i=0;i<cells.length;i++) {
            cells[i].setPublicKey(key);
            cells[i].push();
        }
    }
    

    /**
     * Validate the specified email address.  
     * @param emailAddress the email address to check
     * @throws MgmtException if email address is not valid
     */
    private void validateEmailAddress(String emailAddress) 
    throws MgmtException {
	if (Validate.isValidEmailAddress(emailAddress) == false) {
	    throw new MgmtException("Invalid email address of " + emailAddress
		+ " specified.");
	}
    }
    
    /**
     * Validate the specified email addresses.  
     * @param emailAddresses the email addresses to check
     * @throws MgmtException if an email address is not valid
     */
    private void validateEmailAddress(String[] emailAddresses) 
    throws MgmtException {
	for (int i=0; i < emailAddresses.length; i++) {
	    validateEmailAddress(emailAddresses[i]);
	}
    }
    
    /*
     * Alerts - fixme lots of repeated code, it would be nice 
     * if this could be made cleaner.
     * requires retry.
     */
    public int setAlertTo(String[] alertAddresses)
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
	validateEmailAddress(alertAddresses);
        HCCell cells[] = getCells(false);
	if (cells.length == 0)
	    return CliConstants.FAILURE;

        Object[] params = {Long.toString(this._sessionId),
                            getCsvFromArray(alertAddresses)};

        HCAlertAddr alertAddrOld = cells[0].getAlertAddr();
        alertAddrOld.setSmtpTo(getCsvFromArray(alertAddresses));
 
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_SET_TO_EMAIL, params,
                "setAlertTo");
        
        for(int i=0;i<cells.length;i++) {
            HCAlertAddr alertAddr = cells[i].getAlertAddr();
            alertAddr.setSmtpTo(getCsvFromArray(alertAddresses));
            cells[i].setAlertAddr(alertAddr); 
            cells[i].push();
        }
	return CliConstants.SUCCESS;
    }
    
    private String getCsvFromArray (String[] myArray) {
        String addrs=new String();
        for(int i=0;i<myArray.length;i++){
            if(i >= 1) {
                addrs+=",";
            }
            addrs+=myArray[i];
        }
        return addrs;

    }
    
    /**
     * @see AdminClient#setAlertCc(String)
     */
    public int setAlertCc(String[] alertAddresses)
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
	validateEmailAddress(alertAddresses);
        HCCell cells[] = getCells(false);
	if (cells.length == 0)
	    return CliConstants.FAILURE;
                
        String nullParam = null;
        Object [] params = {Long.toString(this._sessionId),
                            getCsvFromArray(alertAddresses)};        
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_SET_CC_EMAIL, params,
                "setAlertCc");
        
        for(int i=0;i<cells.length;i++) {
            HCAlertAddr alertAddr = cells[i].getAlertAddr();
            alertAddr.setSmtpCC(getCsvFromArray(alertAddresses));
            cells[i].setAlertAddr(alertAddr); 
            cells[i].push();
        }
        return CliConstants.SUCCESS;
    }

    /**
     * @see AdminClient#addAlertTo(String)
     */
    public int addAlertTo(String alertEmail)
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
	validateEmailAddress(alertEmail);
        HCCell cells[] = getCells(false);
	if (cells.length == 0)
	    return CliConstants.FAILURE;
        
        Object[]  params = {Long.toString(this._sessionId), alertEmail};
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_ADD_TO_EMAIL, params,
                "addAlertTo");
        
        for(int i=0;i<cells.length;i++) {
            HCAlertAddr alertAddr = cells[i].getAlertAddr();            
            String smtpTo = alertAddr.getSmtpTo();
	    if (smtpTo == null || smtpTo.length() == 0)
		smtpTo = alertEmail;
	    else
		smtpTo = 
                    new StringBuffer(smtpTo).append(",").append(alertEmail).toString();
            alertAddr.setSmtpTo(smtpTo);
            cells[i].setAlertAddr(alertAddr); 
            cells[i].push();
        }
        return CliConstants.SUCCESS;        
    }
    
    /**
     * @see AdminClient#addAlertCc(String)
     */
    public int addAlertCc(String alertEmail)
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
	validateEmailAddress(alertEmail);
        HCCell cells[] = getCells(false);
	if (cells.length == 0)
	    return CliConstants.FAILURE;
        
        Object [] params = {Long.toString(this._sessionId), alertEmail};    
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_ADD_CC_EMAIL, params,
                "addAlertCc");
        
        for(int i=0;i<cells.length;i++) {
            HCAlertAddr alertAddr = cells[i].getAlertAddr();

            String smtpCc = alertAddr.getSmtpCC();
	    if (smtpCc == null || smtpCc.length() == 0)
		smtpCc = alertEmail;
	    else
		smtpCc = 
                    new StringBuffer(smtpCc).append(",").append(alertEmail).toString();


            alertAddr.setSmtpCC(smtpCc);
            cells[i].setAlertAddr(alertAddr); 
            cells[i].push();
        }
        return CliConstants.SUCCESS;
    }
    
    /**
     * @see AdminClient#deleteAlertTo(String)
     */
    public int delAlertTo(String alertEmail)
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
	// Don't valid email on deletes since we always want to
	// allow the customer to delete what ever has been set
        HCCell cells[] = getCells(false);
	if (cells.length == 0)
	    return CliConstants.FAILURE;
        
        Object [] params = {Long.toString(this._sessionId), alertEmail};
        boolean msgLogged = false;
        
        for(int curCell=0;curCell<cells.length;curCell++) {
            HCAlertAddr alertAddr = cells[curCell].getAlertAddr();
            List<String> smtpTo = ClientUtils.getListFromCSV(alertAddr.getSmtpTo());
            
	    int deletePos = (smtpTo != null ? smtpTo.indexOf(alertEmail) : -1);
	    if (deletePos == -1) {
		return CliConstants.NOT_FOUND;
	    } if (!msgLogged) {
                // Log message done here to avoid logging a message on
                // an invalid delete request
                msgLogged = true;
                this.extLog(ExtLevel.EXT_INFO, 
                        AdminResourcesConstants.MSG_KEY_DEL_TO_EMAIL, params,
                        "delAlertTo");
            }
            smtpTo.remove(deletePos);
	    String addresses = ClientUtils.getCSVFromList(smtpTo);
	    if (addresses == null)
		addresses = "";
            alertAddr.setSmtpTo(addresses);
            cells[curCell].setAlertAddr(alertAddr); 
            cells[curCell].push();
        }
	return CliConstants.SUCCESS;
    }

    /**
     * @see AdminClient#deleteAlertCc(String)
     */
    public int delAlertCc(String alertEmail)
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
	// Don't validate email on deletes since we always want to
	// allow the customer to delete what ever has been set
        HCCell cells[] = getCells(false);
	if (cells.length == 0)
	    return CliConstants.FAILURE;
        
        Object [] params = {Long.toString(this._sessionId), alertEmail};
        boolean msgLogged = false;
        
        for(int curCell=0;curCell<cells.length;curCell++) {
            HCAlertAddr alertAddr = cells[curCell].getAlertAddr();
            List<String> smtpCc = ClientUtils.getListFromCSV(alertAddr.getSmtpCC());

	    int deletePos = (smtpCc != null ? smtpCc.indexOf(alertEmail) : -1);
	    if (deletePos == -1) {
		return CliConstants.NOT_FOUND;
	    } if (!msgLogged) {
                // Log message done here to avoid logging a message on
                // an invalid delete request
                msgLogged = true;
                this.extLog(ExtLevel.EXT_INFO, 
                    AdminResourcesConstants.MSG_KEY_DEL_CC_EMAIL, params,
                    "delAlerCc");
            }
            smtpCc.remove(deletePos);
	    String addresses = ClientUtils.getCSVFromList(smtpCc);
	    if (addresses == null)
		addresses = "";
            alertAddr.setSmtpCC(addresses);
            cells[curCell].setAlertAddr(alertAddr); 
            cells[curCell].push();
        }
	return CliConstants.SUCCESS;
    }

    /**
     * @see AdminClient#getAlertTo()
     */
    public String[] getAlertTo()
        throws MgmtException,ConnectException {
        HCCell cell = getCell(SiloInfo.getInstance().getMasterUrl());

        if(cell == null || null==cell.getAlertAddr().getSmtpTo())
            return new String[0];
        List alertList = ClientUtils.
            getListFromCSV(cell.getAlertAddr().getSmtpTo());
        return (String [])alertList.toArray(new String[alertList.size()]);

    }
    
    /**
     * @see AdminClient#getAlertCc()
     */
    public String[] getAlertCc()
        throws MgmtException,ConnectException {

        HCCell cell = getCell(SiloInfo.getInstance().getMasterUrl());
        if(cell == null || null==cell.getAlertAddr().getSmtpCC())
            return new String[0];
        List alertList = ClientUtils.
            getListFromCSV(cell.getAlertAddr().getSmtpCC());
        return (String [])alertList.toArray(new String[alertList.size()]);

    }

    /*
     * License
     */

    public void setLicense(byte cellId, String license) 
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();

        HCCell cell=getCell(SiloInfo.getInstance().getServerUrl(cellId));
        cell.setLicense(license);

        Object [] params = {Long.toString(this._sessionId)};
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_SET_LICENSE, params,
                "setLicense");

        cell.push();
    }
    public String getLicense(byte cellId)
        throws MgmtException,ConnectException {
        return getCell(SiloInfo.getInstance().getServerUrl(cellId)).getLicense();
    } // returns null if unset

    /*
     * versions
     */

    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#powerNodeOn(byte,int)
     */
    public int powerNodeOn (byte cellId, int nodeId)
        throws MgmtException, ConnectException, PermissionException {
        //
        // We are inconsistently using node nomenclature
        // sometimes nodeids are 101-116 and sometime they're
        // 0-15.
        //
        if(nodeId < 100) 
            nodeId +=101;           

        if(!loggedIn())
            throw new PermissionException();
        HCCell cell=getCell(SiloInfo.getInstance().getServerUrl(cellId));
	return powerNodeOn(cell, nodeId);
    }

    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#powerNodeOn(cell,int)
     */
    public int powerNodeOn (HCCell cell, int nodeId)
        throws MgmtException, ConnectException, PermissionException {
        //
        // We are inconsistently using node nomenclature
        // sometimes nodeids are 101-116 and sometime they're
        // 0-15.
        //
        if(nodeId < 100) 
            nodeId +=101;           
        if(!loggedIn())
            throw new PermissionException();

        Object [] params = {Long.toString(this._sessionId),
                            Integer.toString(nodeId),
                            Integer.toString(cell.getCellId())};
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_NODE_POWER_ON, params,
                "powerNodeOn");

        BigInteger result =
	    cell.powerNodeOn(new StatusCallback(),BigInteger.valueOf(nodeId));
	return result.intValue();
    }

    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#powerNodeOff(cell,int,boolean)
     */
    public int powerNodeOff (HCCell cell, int nodeId,boolean useIpmi) 
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
        //
        // We are inconsistently using node nomenclature
        // sometimes nodeids are 101-116 and sometime they're
        // 0-15.
        //
        if(nodeId < 100) 
            nodeId +=101;           

        BigInteger result;
        

        String msg = (useIpmi ? 
            AdminResourcesConstants.MSG_KEY_NODE_POWER_OFF_IPMI :
            AdminResourcesConstants.MSG_KEY_NODE_POWER_OFF);
        
        Object [] params = {Long.toString(this._sessionId),
                            Integer.toString(nodeId),
                            Integer.toString(cell.getCellId())};
        this.extLog(ExtLevel.EXT_INFO, msg, params, "powerNodeOff");

        if(useIpmi) {
            result = cell.powerNodeOff(new StatusCallback(),BigInteger.valueOf(nodeId),
                              BigInteger.valueOf(0));
        } else {
            result = cell.powerNodeOff(new StatusCallback(),BigInteger.valueOf(nodeId),
                              BigInteger.valueOf(1));
        }
	return result.intValue();
    }

    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#powerNodeOff(byte,int,boolean)
     */
    public int powerNodeOff (byte cellId, int nodeId,boolean useIpmi) 
        throws MgmtException, ConnectException, PermissionException {
        //
        // We are inconsistently using node nomenclature
        // sometimes nodeids are 101-116 and sometime they're
        // 0-15.
        //
        if(nodeId < 100) 
            nodeId +=101;           
        if(!loggedIn()) 
            throw new PermissionException();
        HCCell cell=getCell(SiloInfo.getInstance().getServerUrl(cellId));
        return powerNodeOff(cell, nodeId, useIpmi);
    }

    public void rebootNode (byte cellId, int nodeId) 
        throws MgmtException, ConnectException, PermissionException{
        //
        // We are inconsistently using node nomenclature
        // sometimes nodeids are 101-116 and sometime they're
        // 0-15.
        //
        if(nodeId < 100) 
            nodeId +=101;           

        if(!loggedIn()) 
            throw new PermissionException();
        HCNode node = getNode(cellId,nodeId);
        
        Object[] params = {Long.toString(_sessionId),
                            Byte.toString(cellId),
                            Integer.toString(nodeId)};
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_REBOOT_NODE,
                params, "rebootNode");
        node.reboot(BigInteger.valueOf(0));

    }


    /**
     * perf statistics gathering
     */
    public HCPerfStats getNodePerfStats(int nodeId, int interval, byte cellId) 
        throws MgmtException,ConnectException {
        //
        // We are inconsistently using node nomenclature
        // sometimes nodeids are 101-116 and sometime they're
        // 0-15.
        //
        if(nodeId < 100) 
            nodeId +=101;           

        if (null == _stats || nodeId!=_nodeId || 
          _interval != interval || cellId != _cellId) {
	    // Fetch stats this will set our baseline
            _stats=getPerfstats(cellId);
            _stats.setNewInterval(BigInteger.valueOf(interval));
            _stats.setNodeId(BigInteger.valueOf(nodeId));

            _nodeId=nodeId;
            _cellId=cellId;
            _interval = interval;
            
            _stats.push();
        }
        _stats.nodeCollect(BigInteger.valueOf(0));
        _stats=getPerfstats(cellId);
	
        return _stats;
    }
    
    public HCPerfStats getClusterPerfStats(int interval, byte cellId) 
        throws MgmtException,ConnectException {
	
        if(null == _stats || _interval != interval || cellId != _cellId) {
	    // Fetch stats this will set our baseline
            _stats=getPerfstats(cellId);
            _stats.setNewInterval(BigInteger.valueOf(interval));
            _stats.setNodeId(BigInteger.valueOf(-1));
            _interval = interval;
            _nodeId=-1;            
            _cellId=cellId;
            _stats.push();
        }

        _stats.cellCollect(BigInteger.valueOf(0));
        _stats=getPerfstats(cellId);

        return _stats;

    }
    public void rebootCell(byte cellId, boolean _switches, boolean _sp)
        throws MgmtException,ConnectException ,AdmException,PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();

        HCCell cell=getCell(SiloInfo.getInstance().getServerUrl(cellId));
        
        rebootCell(cell, _switches, _sp);
    
    }

    //
    // check isClusterSane and hasQuroum first.
    // if both aren't true, it's not safe to shutdown
    //

    public void rebootCell(HCCell cell, boolean _switches, boolean _sp) 
        throws MgmtException,ConnectException ,AdmException,PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();

        BigInteger switches;
        BigInteger sp;
        
        String msg = AdminResourcesConstants.MSG_KEY_REBOOT;
        if(_switches) {
            switches=BigInteger.valueOf(0);
            msg = AdminResourcesConstants.MSG_KEY_REBOOT_SWITCH;
        } else {
            switches=BigInteger.valueOf(1);
        }
        if(_sp) {
            sp=BigInteger.valueOf(0);
            msg = (_switches ? AdminResourcesConstants.MSG_KEY_REBOOT_ALL :
                    AdminResourcesConstants.MSG_KEY_REBOOT_SERVICE_PROCESSOR);
        } else {
            sp=BigInteger.valueOf(1);
        }

        if (!_internalCall) {
            Object [] params = {Long.toString(this._sessionId),
                            Byte.toString(cell.getCellId())};
            extLog(ExtLevel.EXT_INFO, msg, params, "rebootCell");
        }
        
        _internalCall = false;
        cell.reboot(new StatusCallback(), switches,sp);
    }

    /**
     * @see com.sun.honeycomb.adm.client.AdminClient#powerOff(byte,boolean,boolean)
     */
    public int powerOff(byte cellId,boolean _sp,boolean _useIpmi) 
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
        
        HCCell cell=getCell(SiloInfo.getInstance().getServerUrl(cellId));
        return powerOff(cell, _sp,_useIpmi);
    }
        
    /**
     *
     * @see com.sun.honeycomb.adm.client.AdminClient#powerOff(cell,boolean,boolean)
     */
    public int powerOff(HCCell cell, boolean _sp, boolean _useIpmi) 
        throws MgmtException, ConnectException, PermissionException {
        
        if(!loggedIn()) 
            throw new PermissionException();

        BigInteger useIpmi;
        BigInteger sp;
        
        if(_useIpmi) {
            useIpmi=BigInteger.valueOf(0);
        } else {
            useIpmi=BigInteger.valueOf(1);
        }

        if(_sp) {
            sp=BigInteger.valueOf(0);
        } else {
            sp=BigInteger.valueOf(1);
        }
       
        Object [] params = {Long.toString(this._sessionId),
                            Byte.toString(cell.getCellId())};
        String msgKey = AdminResourcesConstants.MSG_KEY_SHUTDOWN;
        if (_sp) {
            if (_useIpmi) {
                msgKey = AdminResourcesConstants.MSG_KEY_SHUTDOWN_ALL_IPMI;
            } else {
                msgKey = AdminResourcesConstants.MSG_KEY_SHUTDOWN_ALL;
            }
        } else if (_useIpmi) {
            msgKey = AdminResourcesConstants.MSG_KEY_SHUTDOWN_IPMI;
        }
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_SHUTDOWN, params,
                "powerOff");
        BigInteger result = cell.powerOff(new StatusCallback(), useIpmi, sp);
	return result.intValue();

    }


    public boolean getProtocolStatus (byte cellId) 
        throws MgmtException,ConnectException {

        HCNodes nodes=getNodes(SiloInfo.getInstance().getServerUrl(cellId));
        return getProtocolStatus(nodes);
    }
    public boolean getProtocolStatus (HCNodes nodes)
        throws MgmtException,ConnectException {

        return nodes.isProtocolRunning();
    }

    public boolean hasQuorum (byte cellId) 
        throws MgmtException,ConnectException {

        HCCell cell=getCell(SiloInfo.getInstance().getServerUrl(cellId));
        return hasQuorum(cell);
    }
    public boolean hasQuorum (HCCell cell)
        throws MgmtException,ConnectException {
        return cell.isQuorumReached();
    }

    public boolean isClusterSane (byte cellId)
        throws MgmtException,ConnectException {
        HCHadb hadb =
          Fetcher.fetchHCHadb(SiloInfo.getInstance().getServerUrl(cellId));

        return hadb.isClusterSane();        
    }

    /**
     * @deprecated. use getCacheStatus instead.
     * strings are english only, and limited to "HAFaultTolerant" and
     * "unknown".
     */
    public String getHadbStatus (byte cellId)        
        throws MgmtException,ConnectException{
        HCHadb hadb =
          Fetcher.fetchHCHadb(SiloInfo.getInstance().getServerUrl(cellId));
        return hadb.getHadbStatus(BigInteger.valueOf(0));
    }

    public BigInteger getCacheStatus (byte cellId)        
        throws MgmtException,ConnectException{
        BigInteger result;

        HCHadb hadb =
          Fetcher.fetchHCHadb(SiloInfo.getInstance().getServerUrl(cellId));
        result = hadb.getCacheStatus(BigInteger.valueOf(0));

        return result;
    }

    public long getLastHADBCreateTime (byte cellId)        
        throws MgmtException,ConnectException{
        long result;

        HCHadb hadb =
          Fetcher.fetchHCHadb(SiloInfo.getInstance().getServerUrl(cellId));
        result = hadb.getLastCreateTime(BigInteger.valueOf(0)).longValue();

        return result;
    }

    public int getSchema(PrintStream out, boolean template)
        throws MgmtException, ConnectException, IOException {
        BigInteger wsRet =  BigInteger.valueOf(CliConstants.MGMT_OK);
        HCCell cell = getCell(SiloInfo.getInstance().getMasterUrl());
        try {
            wsRet = cell.retrieveSchema(new PrintStreamCallback(out),
              (template) ? (byte) 1 : (byte) 0);
        } catch (Exception e) {
            return CliConstants.MGMT_CANT_RETRIEVE_SCHEMA;
        }
        return wsRet.intValue();
    }
    
    public int pushSchema(InputStream input, boolean validateOnly)
        throws MgmtException, ConnectException, PermissionException,IOException {
        if(!loggedIn()) 
            throw new PermissionException();

        int res = -1;

        InputStream is = new BufferedInputStream(input);

        // tmp buffer used to read schema from client
        byte  [] buffer  = new byte[CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE];
        int      length  = CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE;
        int      offset  = 0;
        byte     mask = CliConstants.MDCONFIG_FIRST_MESSAGE;

        // piece of schema sent in one WS-management envelope
        String   schemaPiece  = null;

        // Runner, timestamp for 'commit'
        MultiCellSchemaUpdate [] schemaUpdate = null;
        MultiCellRunner runner = null;
        long timestamp = -1;

        // Cell to target for 'validation only'
        HCCell masterCell = null;
        if (validateOnly) {
            masterCell = getCell(SiloInfo.getInstance().getMasterUrl());
        } else {
            timestamp = System.currentTimeMillis();
            MultiCellOp [] ops = 
              allocateMultiCellOp(MultiCellSchemaUpdate.class);
            runner = new MultiCellRunner(ops);
            // Nothing will hapeen until we set the cookie with the
            // first buffer we read.
            runner.start();
        }

        int nbPiece = 0;
        do {
            res = is.read(buffer, offset, length);
            if (res == -1) {
                break;
            }
            offset += res;
            length -= res;
            if (offset == CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE) {
                schemaPiece = new String(buffer, "UTF-8");
                if (validateOnly) {
                    try {
                        BigInteger wsRet = masterCell.updateSchema(null,
                          schemaPiece, (long) -1, mask, (byte) 0);
                        if (wsRet.intValue() !=  CliConstants.MGMT_OK) {
                            return  CliConstants.MGMT_SCHEMA_UPLOAD_FAILED;
                        }
                    } catch (Exception e) {
                        throw new MgmtException("Failed to validate schema:\n" +
                            e.getMessage());
                    }
                } else {
                    nbPiece++;
                    SchemaUpdateCookie cookie = 
                      new SchemaUpdateCookie(schemaPiece,
                        nbPiece, timestamp, mask);
                    runner.setCookie(cookie);
                    int r = runner.waitForPartialResult();
                    if (r != 0) {
                        return  CliConstants.MGMT_SCHEMA_UPLOAD_FAILED;
                    }
                }
                mask = 0;
                buffer = new byte[CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE];
                offset = 0;
                length = CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE;
            }
        } while (res != -1);

        mask |= CliConstants.MDCONFIG_LAST_MESSAGE;
        if (offset > 0) {
            schemaPiece = new String(buffer, 0, offset,"UTF-8");
        } else {
            // we still send a message with a null schemaPiece to 
            // send the mask with the value MDCONFIG_LAST_MESSAGE
            schemaPiece = null;
        }
        if (validateOnly) {
            BigInteger wsRet = BigInteger.valueOf(CliConstants.MGMT_OK);
            try {
                wsRet = masterCell.updateSchema(null,
                  schemaPiece, (long) -1, mask, (byte) 0);
            } catch (Exception e) {
                throw new MgmtException("Failed to validate schema:\n" +
                    e.getMessage());
                //throwException(e, "updateSchema");
            }
            return wsRet.intValue();
        } else {
            SchemaUpdateCookie cookie = 
              new SchemaUpdateCookie(schemaPiece,
                nbPiece, timestamp, mask);
            runner.setCookie(cookie);
            
            Object [] params = {Long.toString(this._sessionId)};
            this.extLog(ExtLevel.EXT_INFO, 
                    AdminResourcesConstants.MSG_KEY_UPDATE_SCHEMA,
                    params, "pushSchema");
            
            return runner.waitForResult();
        }
    }

    /**
     * @deprecated. use either getSchema or pushSchema instead. 
     * Otherwise you can't inspect the schema when not logged in.
     */
    public int updateSchema(InputStream input,
                             PrintStream clientOutput,
                             boolean commit,
                             boolean dump,
                             boolean template)
        throws MgmtException, ConnectException, PermissionException, 
        IOException {

        if(!loggedIn()) 
            throw new PermissionException();

        if (dump) {
            return getSchema(clientOutput, false);
        } else if (template) {
            return getSchema(clientOutput, true);
        } else if (!commit) {
            return pushSchema(input, true);
        } else {
            return pushSchema(input, false);
        }
    }

    

    public int clearSchema()
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();

        MultiCellOp []  clearSchema = 
          allocateMultiCellOp(MultiCellClearSchema.class);
        MultiCellRunner runner = new MultiCellRunner(clearSchema);
        
        Object [] params = {Long.toString(this._sessionId)};
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_WIPE_SCHEMA,
                params, "clearSchema");
        
        runner.start();
        return runner.waitForResult();
    }

    public int retrySchema()
        throws MgmtException, ConnectException, PermissionException,
        MgmtException {
        if(!loggedIn()) 
            throw new PermissionException();

        MultiCellOp []  retrySchema = 
          allocateMultiCellOp(MultiCellRetrySchema.class);
        MultiCellRunner runner = new MultiCellRunner(retrySchema);

        Object [] params = {Long.toString(this._sessionId)};
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_RETRY_UPDATE_SCHEMA,
                params, "retrySchema");
        
        runner.start();
        return runner.waitForResult();
    }

    public HCSensor getSensors (byte cellId,int nodeId) 
        throws MgmtException, ConnectException{
        //
        // We are inconsistently using node nomenclature
        // sometimes nodeids are 101-116 and sometime they're
        // 0-15.
        //
        if(nodeId < 100) 
            nodeId +=101;           
        String nodeIdString=(""+nodeId);
        HCSensor sensor = 
          Fetcher.fetchHCSensor(SiloInfo.getInstance().getServerUrl(cellId), 
            nodeIdString);
        return sensor;
    }


    /*
     * Expansion functions
     */


    public void startExpansion(byte cellId)
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
        HCCell cell=getCell(SiloInfo.getInstance().getServerUrl(cellId));
        startExpansion(cell);
    }

    public void startExpansion(HCCell cell)
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
        
        Object[] params = {Long.toString(_sessionId)};
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_START_CELL_EXPANSION,
                params, "startExpansion");
        cell.startExpansion(BigInteger.valueOf(0));

    }


    public void cancelExpansion(byte cellId)
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
        HCCell cell=getCell(SiloInfo.getInstance().getServerUrl(cellId));
        cancelExpansion(cell);
    }

    public void cancelExpansion(HCCell cell)
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
        Object[] params = {Long.toString(_sessionId)};
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_CANCEL_CELL_EXPANSION,
                params, "cancelExpansion");
       cell.stopExpansion(BigInteger.valueOf(0));

    }

    public int getExpansionStatus(byte cellId)
        throws MgmtException, ConnectException, PermissionException{
        if(!loggedIn()) 
            throw new PermissionException();
        HCCell cell=getCell(SiloInfo.getInstance().getServerUrl(cellId));
        return getExpansionStatus(cell);
        
    }

    public int getExpansionStatus(HCCell cell)
        throws MgmtException, ConnectException, PermissionException{
        if(!loggedIn()) 
            throw new PermissionException();

        return cell.expansionStatus(BigInteger.valueOf(0)).intValue();


    }
    
    public static void throwException(Throwable e, String method)
        throws MgmtException {

        if (e instanceof IllegalArgumentException) {
            throw new MgmtException("failed to invoke method " + 
              method + " " + e);
            
        } else if (e instanceof InvocationTargetException) {
            Throwable th = ((InvocationTargetException)e).getTargetException();
            String err = "failed to invoke method " + 
              method + " " +  "Message : " + th.getMessage();
            throw new MgmtException(err+ "Stack trace : " + th.getStackTrace());
        } else if (e instanceof ExceptionInInitializerError) {
            throw new MgmtException("failed to invoke method " + 
              method + " " + e);
        } else if (e instanceof Exception) {
            throw new MgmtException("failed to invoke method " + 
              method + " " + e);
        }
    }
    
    public int setProtocolPassword(String realmName,String userName, byte[] hash) 
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn())
            throw new PermissionException();
        
        Object[] params = {Long.toString(_sessionId), realmName, userName};
        
        this.extLog(ExtLevel.EXT_INFO, 
                AdminResourcesConstants.MSG_KEY_SET_PROTOCOL_PASSWORD,
                params, "setProtocolPassword");
        BigInteger retValue = 
          getSilo(SiloInfo.getInstance().getSiloUrl()).setProtocolPassword(
              realmName, userName, ByteArrays.toHexString(hash));
        return retValue.intValue();
    }

    public int mountIso(byte cellid, boolean spDvd)  
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
        String spDvds;
        if(spDvd) {
            spDvds="true";
        } else {
            spDvds="false";
        }
        return getUpgrade(cellid).mountIso(spDvds).intValue();

    }


    public int uMountIso(byte cellid, boolean spDvdb) 
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
        String spDvd;
        if(spDvdb) {
            spDvd="true";
        } else {
            spDvd="false";
        }

        return getUpgrade(cellid).uMountIso(spDvd).intValue();

    }

    public int statusCheck(byte cellid) 
	throws MgmtException, ConnectException, PermissionException {
	if (!loggedIn())
	    throw new PermissionException();
	return getUpgrade(cellid).statusCheck(new StatusCallback(), 
					      BigInteger.valueOf(0)).intValue();
    }

    public int initializeUpgrader(byte cellid)
	throws MgmtException, ConnectException, PermissionException {
	if (!loggedIn())
	    throw new PermissionException();
	return getUpgrade(cellid).initializeUpgrader(BigInteger.valueOf(0)).intValue();
    }

    public String getNextQuestion(byte cellid)
	throws MgmtException, ConnectException, PermissionException {
	if (!loggedIn())
	    throw new PermissionException();
	return getUpgrade(cellid).getNextQuestion(BigInteger.valueOf(0));
    }

    public String getConfirmQuestion(byte cellid)
	throws MgmtException, ConnectException, PermissionException {
	if (!loggedIn())
	    throw new PermissionException();
	return getUpgrade(cellid).getConfirmQuestion(BigInteger.valueOf(0));
    }

    public int invokeNextMethod(byte cellid, boolean answer)
	throws MgmtException, ConnectException, PermissionException {
	if (!loggedIn())
	    throw new PermissionException();
	BigInteger intresponse;
	if (answer) {
	    intresponse = BigInteger.valueOf(0);
	} else {
	    intresponse = BigInteger.valueOf(1);
	}
	
	return getUpgrade(cellid).invokeNextMethod
	    (new StatusCallback(), intresponse).intValue();
    }
    
    public int setForceOptions(byte cellid)
	throws MgmtException, ConnectException, PermissionException {
	if (!loggedIn())
	    throw new PermissionException();
	return getUpgrade(cellid).setForceOptions(new StatusCallback(), 
						  BigInteger.valueOf(0)).intValue();
    }

    public int downloadJar(byte cellid, String src) 
	throws MgmtException, ConnectException, PermissionException {
	if (!loggedIn())
	    throw new PermissionException();
	return getUpgrade(cellid).downloadJar(new StatusCallback(), src).intValue();
    }

    public int copyJar(byte cellid) 
	throws MgmtException, ConnectException, PermissionException {
	if (!loggedIn())
	    throw new PermissionException();
	return getUpgrade(cellid).copyJar(BigInteger.valueOf(0)).intValue();
    }

    public int httpFetch(byte cellid,String url) 
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn()) 
            throw new PermissionException();
        return getUpgrade(cellid).httpFetch(new StatusCallback(),url).intValue();

    }

    public int startUpgrade(byte cellid, boolean spDvdb)
	throws MgmtException, ConnectException, PermissionException {
	if (!loggedIn())
	    throw new PermissionException();
        String spDvd;
        if(spDvdb) {
            spDvd="true";
        } else {
            spDvd="false";
        }

	String msg = AdminResourcesConstants.MSG_KEY_START_UPGRADE;

	Object[] params = {Long.toString(_sessionId)};
	this.extLog(ExtLevel.EXT_INFO, msg, params, "startUpgrade");

        return getUpgrade(cellid).startUpgrade
	    (new StatusLoginCallback(this), spDvd,new Byte(cellid)).intValue();
    }

    public int finishUpgrade(byte cellid, int type, boolean success)
	throws MgmtException, ConnectException, PermissionException {
	if (!loggedIn())
	    throw new PermissionException();
	BigInteger intresponse;
	if (success) {
	    intresponse = BigInteger.valueOf(0);
	} else {
	    intresponse = BigInteger.valueOf(1);
	}

	return getUpgrade(cellid).finishUpgrade(new StatusLoginCallback(this), 
						BigInteger.valueOf(type), 
						intresponse,
						new Byte(cellid)).intValue();

    }
    
    /**
     * This class creates an instance of the specified class
     * for each cell in the hive and passes in the HCCell
     * object
     * @param cls The class to instantiate.  Must extends MutliCellOp
     * and have a contructor with a single argument of HCCell
     * @return MultiCellOp[] array of instantiated MultiCellOp classes
     * @throws com.sun.honeycomb.mgmt.common.MgmtException
     * @throws com.sun.honeycomb.adm.cli.ConnectException
     */
    private MultiCellOp [] allocateMultiCellOp(Class clazz) 
        throws MgmtException, ConnectException {
        return MultiCellUtils.allocateMultiCellOp(clazz, getCells(false));
    }

    public String getBackupStatus (byte cellId)
        throws MgmtException,ConnectException {
        HCNDMP ndmp = Fetcher.fetchHCNDMP(SiloInfo.getInstance().getServerUrl(cellId));
        return ndmp.getBackupStatus();
    }

//     public boolean getProceedAfterError (byte cellId)
//         throws MgmtException,ConnectException {
//         HCNDMP ndmp = Fetcher.fetchHCNDMP(SiloInfo.getInstance().getServerUrl(cellId));
//         return ndmp.isProceedAfterError ();
//     }

//     public void setProceedAfterError (byte cellId, boolean proceed)
//         throws MgmtException,ConnectException {
//         HCNDMP ndmp = Fetcher.fetchHCNDMP(SiloInfo.getInstance().getServerUrl(cellId));
//         ndmp.setProceedAfterError(proceed);
//     }


//     public int getBackupControlPort (byte cellId)
//         throws MgmtException,ConnectException {
//         HCNDMP ndmp = Fetcher.fetchHCNDMP(SiloInfo.getInstance().getServerUrl(cellId));
//         return ndmp.getBackupControlPort ().intValue();
//     }

//     public void setBackupControlPort (byte cellId, int port)
//         throws MgmtException,ConnectException {
//         HCNDMP ndmp = Fetcher.fetchHCNDMP(SiloInfo.getInstance().getServerUrl(cellId));
//         ndmp.setBackupControlPort(BigInteger.valueOf(port));
//     }

//     public int getBackupOutboundDataPort (byte cellId)
//         throws MgmtException,ConnectException {
//         HCNDMP ndmp = Fetcher.fetchHCNDMP(SiloInfo.getInstance().getServerUrl(cellId));
//         return ndmp.getBackupOutboundDataPort ().intValue();
//     }

//     public void setBackupOutboundDataPort (byte cellId, int port)
//         throws MgmtException,ConnectException {
//         HCNDMP ndmp = Fetcher.fetchHCNDMP(SiloInfo.getInstance().getServerUrl(cellId));
//         ndmp.setBackupOutboundDataPort(BigInteger.valueOf(port));
//     }

//     public int getBackupInboundDataPort (byte cellId)
//         throws MgmtException,ConnectException {
//         HCNDMP ndmp = Fetcher.fetchHCNDMP(SiloInfo.getInstance().getServerUrl(cellId));
//         return ndmp.getBackupInboundDataPort ().intValue();
//     }

//     public void setBackupInboundDataPort (byte cellId, int port)
//         throws MgmtException,ConnectException {
//         HCNDMP ndmp = Fetcher.fetchHCNDMP(SiloInfo.getInstance().getServerUrl(cellId));
//         ndmp.setBackupInboundDataPort(BigInteger.valueOf(port));
//     }



    private static QuotedPrintableCodec codec = new QuotedPrintableCodec();
    private static String encode (String s){
        try {
            return codec.encode(s);
        }
        catch (EncoderException e){throw new RuntimeException(e);}
    }


    private static String decode (String s){
        try {
            return codec.decode(s);
        }
        catch (DecoderException e){throw new RuntimeException(e);}
    }


    public boolean loggedIn()
        throws MgmtException, ConnectException, PermissionException {

        if (_sessionId==AdminClient.NOT_LOGGED_IN_SESSION_ID) {
            return login();
        }
        if (null==_lastChecked) {
            throw new RuntimeException("Internal login state inconsistent");
        }
        Date now = new Date();
        
        if ((_lastChecked.getTime()+LOGIN_TIMEOUT) > now.getTime()) {
            return true;
        } else {
            if (getSilo(SiloInfo.getInstance().getSiloUrl()).
                loggedIn(BigInteger.valueOf(_sessionId)).equals(
                    BigInteger.valueOf(0))) {
                _lastChecked=now;
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     *  Get session id of logged in user. A value of -1 means user is not
     *  not logged in.
     */
    public long getLoggedInSessionId() {
        return _sessionId;
    }


    private  boolean login()
        throws MgmtException, ConnectException, PermissionException {
        if (_sessionId == AdminClient.NOT_LOGGED_IN_SESSION_ID) {
            Date now=new Date();
            BigInteger loginResult =
              getSilo(SiloInfo.getInstance().getSiloUrl()).
                login(BigInteger.valueOf(now.getTime()));
            switch (loginResult.intValue()) {
            case CliConstants.MGMT_OK:
                _sessionId=now.getTime();
                _lastChecked=now;
                Object[] params = {Long.toString(_sessionId)};
                this.extLog(ExtLevel.EXT_INFO, 
                        AdminResourcesConstants.MSG_KEY_LOGIN, params, "login");
                this._cachedCellId = this.getMasterCellId();
                getAuditLogger();
                return true;                                

            case CliConstants.MGMT_ALREADY_LOGGED:
                return false;
                
            case CliConstants.MGMT_NOT_MASTER_CELL:
                throw new PermissionException("Need to log on the master " +
                    "cell to perform this command");
            default:
                throw new RuntimeException("Unexpected error code from server");
            }
        } else {
            return loggedIn();
        }
    }

    public  void logout()
        throws MgmtException,ConnectException {
        if (AdminClient.NOT_LOGGED_IN_SESSION_ID != _sessionId) {
            Object[] params = {Long.toString(_sessionId)};
            this.extLog(ExtLevel.EXT_INFO, 
                    AdminResourcesConstants.MSG_KEY_LOGOUT, params, "logout");
            getSilo(SiloInfo.getInstance().getSiloUrl()).
                logout(BigInteger.valueOf(_sessionId));
            _lastChecked=null;
            _sessionId=AdminClient.NOT_LOGGED_IN_SESSION_ID;
            clearHCAuditLogger();
        }
    }

    public boolean isEmulated()  
        throws MgmtException,ConnectException {
        return SiloInfo.getInstance().isEmulated();
    }




    /**
     * accessors for cells and disks
     * get*array methods are provided to support
     * older code. 
     */
    public  HCCell getCell(String url)
        throws MgmtException,ConnectException  {
        HCCell cell = Fetcher.fetchHCCell(url);
        return cell;
    }

    public HCCell getCell(byte cellId) throws MgmtException,ConnectException { 
        return getCell(SiloInfo.getInstance().getServerUrl(cellId));
    }


    public HCNodes getNodes(byte cellId) 
        throws MgmtException,ConnectException {
        return getNodes(SiloInfo.getInstance().getServerUrl(cellId));
    }

    public HCNodes getNodes(String url) 
        throws MgmtException,ConnectException {
        return Fetcher.fetchHCNodes(url);        
    }



    /**
     * Convienence method
     */
    public  HCNode getNode (byte cellId,int nodeId)
        throws  MgmtException,ConnectException {  
        //
        // We are inconsistently using node nomenclature
        // sometimes nodeids are 101-116 and sometime they're
        // 0-15.
        //
        if(nodeId < 100) 
            nodeId +=101;             
        return Fetcher.fetchHCNode(SiloInfo.getInstance().getServerUrl(cellId),
                                   BigInteger.valueOf(nodeId));        
    }

    /**
     * Direct all to fetch one and only one
     * node. Expects input in the form of
     * node-xxx (where xxx is 101-116)
     * or just the number alone (101 through 116)
     */
    public  HCNode getNode (byte cellId,String fru)
        throws  MgmtException,ConnectException {           
        String lowerName = fru.toLowerCase();

        if (lowerName.startsWith("node-")){
            lowerName=lowerName.substring(5);
        }
        int nodeId =Integer.decode(lowerName).intValue();
        return getNode(cellId,nodeId);

    }



    /**
     * Fetches HCDisks object witha single call
     */
    public HCDisks getDisks(String url)  
        throws MgmtException,ConnectException  {
        HCDisks disks = Fetcher.fetchHCDisks(url);
        return disks;
    }

    /**
     * Convienence method
     */
    public HCDisks getDisks(byte cellId) 
        throws MgmtException,ConnectException {  
        return getDisks(SiloInfo.getInstance().getServerUrl(cellId));
    }



    /**
     * Single call across the mgmt layer to fetch this and only
     * this object. Will throw an exception if it's not available.
     */
    public HCDisk getDisk(byte cellId, String fru)
        throws MgmtException,ConnectException {
        String lowerName = fru.toLowerCase();
        if (lowerName.startsWith("disk-")){
            lowerName=lowerName.substring(5);
        }
        HCDisk disk=null;
        disk = Fetcher.fetchHCDisk(SiloInfo.getInstance().getServerUrl(cellId), 
                                   lowerName);
        return disk;
    }

    /**
     * More efficent if you have an HCDisks object hanging around
     */

    public HCDisk[] getDisksOnNode (HCDisks disks, int nodeId)
        throws MgmtException, ConnectException {
        //
        // We are inconsistently using node nomenclature
        // sometimes nodeids are 101-116 and sometime they're
        // 0-15.
        //
        if(nodeId < 100) 
            nodeId +=101;           

        ArrayList diskList = new ArrayList();
        Iterator<HCDisk> it = disks.getDisksList().iterator();
        while (it.hasNext()) {
            HCDisk disk = it.next();
            if(disk.getNodeId().intValue()==nodeId) {
                diskList.add(disk);
            }
        }



        HCDisk[] example = new HCDisk[diskList.size()];
        return (HCDisk[])diskList.toArray(example);
    }
    /**
     */
    public HCDisk[] getDisksOnNode (byte cellId,int nodeId)
        throws MgmtException, ConnectException {
        //
        // We are inconsistently using node nomenclature
        // sometimes nodeids are 101-116 and sometime they're
        // 0-15.
        //
        if(nodeId < 100) 
            nodeId +=101;           

        return getDisksOnNode(getDisks(cellId),nodeId);

    }






    
    /*
     *  Log message to external log file.
     *  @param  level   the external log level, Should be obtained from ExtLevel
     *  @param  msgKey  the message key in the properties file
     *  @param  params  the message parameters (all strings)
     *  @param  methodName  the method name that called extLog or the method
     *                      that should be displayed in the log message
     */
    public void extLog(Level level, String msgKey, Object [] params,
            String methodName) {
        String msg = "";
        BigInteger retVal = null;
        if (!ExtLevel.isExtLevel(level)) {
            logger.warning("Invalid External Audit Log Level: " +
                    level.getName() + "  setting level to: " +
                    ExtLevel.EXT_INFO.getName());
            level = ExtLevel.EXT_INFO;
        }
        try {
            // Since only have one session at a time should be able
            // to get locale and resource bundle at login time.
            Locale locale = new Locale(getLocale());
            ResourceBundle bundle = ResourceBundle.getBundle(
                    AdminResourcesConstants.RESOURCE_BUNDLE_NAME, locale);
            String pattern = bundle.getString(msgKey);
            msg = MessageFormat.format(pattern, params);
        } catch (Exception ex) {
            String tmpMsg = "Message not logged." +
                    " Message lookup or message formatting failed." +
                    "  Message key: " + msgKey +
                    "  Message String: " + msg;
            logger.logp(Level.WARNING, AdminClientImpl.class.getName(),
                    "extLog", tmpMsg, ex);
            return;
        }
        
        try {
            if (auditLogger == null) {
                getAuditLogger();
            }
            retVal = auditLogger.logExt(level.getName(), msg,
                    this.classFullname, methodName);
        } catch (Exception ex) {
            String tmpMsg = "Unexpected error. Message not logged to external" +
                    " logger: " + msg;
            logger.logp(Level.WARNING, AdminClientImpl.class.getName(),
                    "extLog", tmpMsg, ex);
            return;
        }
    }
    
    /**
     * @see AdminClient#dumpLog()
     */
    public int dumpLog()
                throws MgmtException, ConnectException, PermissionException { 
        if (!loggedIn()) 
            throw new PermissionException();
        HCExpProps masterExpProps = 
                getCell(SiloInfo.getInstance().getMasterUrl()).getExpProps();        
        MultiCellOp [] ops = allocateMultiCellOp(MultiCellLogDump.class);
        ExpPropsCookie cookie = new ExpPropsCookie(masterExpProps);
        MultiCellRunner runner = new MultiCellRunner(ops);
        runner.setCookie(cookie);
        runner.start();
        return runner.waitForResult();
    }
    /**
     * @see AdminClient#getExpProps() 
     */
    public HCExpProps getExpProps()
        throws MgmtException, ConnectException {
        return getCell(SiloInfo.getInstance().getMasterUrl()).getExpProps();
    }
    
    /**
     * @see AdminClient#setExpProps(HCExpProps) 
     */
    public void setExpProps(HCExpProps newProps)
        throws MgmtException, ConnectException, PermissionException {
        if(!loggedIn())
            throw new PermissionException();

        HCCell cell = getCell(SiloInfo.getInstance().getMasterUrl());
        cell.setExpProps(newProps);
        cell.push();
    }

}


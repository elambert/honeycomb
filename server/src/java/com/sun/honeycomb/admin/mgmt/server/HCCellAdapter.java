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

import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.config.ClusterProperties;

import com.sun.honeycomb.admin.mgmt.Reassure;
import com.sun.honeycomb.admin.mgmt.server.HCCellAdapterBase.CellCfgAsync;
import com.sun.honeycomb.admin.mgmt.AdminException;
import com.sun.honeycomb.admin.mgmt.Utils;
import com.sun.honeycomb.admin.mgmt.ClusterManagement;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.config.ExplorerDefaultsWriter;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.XMLConfigHandler;
import com.sun.honeycomb.emd.config.EMDConfigException;
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.time.Time;
import com.sun.honeycomb.time.TimeManagedService;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.ServiceProcessor;
import com.sun.honeycomb.util.Switch;
import com.sun.honeycomb.util.BundleAccess;


import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.util.Iterator;
import java.util.MissingResourceException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


public class HCCellAdapter extends HCCellAdapterBase {
    private ClusterManagement    cltMgmt;

    // Used for mdconfig because we send the schema into pieces
    static private StringBuffer schemaBuffer = null;
    static private long schemaTimestamp = -1;
    
    private static transient final Logger logger = 
      Logger.getLogger(HCCellAdapter.class.getName());

    private static final String EXPLORER_FILE = "/opt/honeycomb/share/explorer";
    
    //
    // Implements HCCellAdapterInterface
    //
    public HCCellAdapter() {
        super();


    }

    public void loadHCCell()
        throws InstantiationException {
        loadHCCellBase();
        cltMgmt = ClusterManagement.getInstance();
    }


   /*
    * This is the list of accessors to the object
    */


    public void populateLanguages(List<String> array) throws MgmtException {
        array.clear();
        String languages[]=BundleAccess.getInstance().getAvailableLanguages();
        for(int i=0;i<languages.length;i++) {
            array.add(languages[i]);
        }
    }


    

    public Boolean getPossibleDataLoss() throws MgmtException {
        return new Boolean(config.getPropertyAsBoolean(ConfigPropertyNames.PROP_DATA_LOSS));
    }
    // Throws mgmtexception if data services unavailable


    public BigInteger getNoUnhealeadFailures() throws MgmtException {
        try {
            if (!Utils.getNodeMgrProxy().hasQuorum()) {
                //
                // Value not available when quotrm not present, but non-fatal.
                //
                return BigInteger.valueOf(-1);

            }
            return BigInteger.valueOf(
                Utils.getDataDocProxy().unhealedFailuresTotal());
        } catch (AdminException ae) {
            //
            // Internationalize here
            //
            logger.log(Level.WARNING,"can't get number of unhealed failures",ae);
            throw new MgmtException("Failed to retrieve number of " +
                "unhealed failures.");
        }
    }
    // Throws mgmtexception if data services unavailable
    public BigInteger getNoUnhealeadUniqueFailures() throws MgmtException {
        try {
            if (!Utils.getNodeMgrProxy().hasQuorum()) {
                //
                // Value not available when quotrm not present, but non-fatal.
                //
                return BigInteger.valueOf(-1);
            }
            return BigInteger.valueOf(
                Utils.getDataDocProxy().unhealedFailuresUnique());
        } catch (AdminException ae) {
            //
            // Internationalize here
            //
            logger.log(Level.WARNING,"can't get number of unhealed unique failures",ae);
            throw new MgmtException("Failed to retrieve number of " +
                "unhealed failures.");
        }
    }

    // Throws mgmtexception if data services unavailable
    public Long getEndTimeLastRecoverCycle() throws MgmtException {
        try {
            if (!Utils.getNodeMgrProxy().hasQuorum()) {
                //
                // Value not available when quotrm not present, but non-fatal.
                //
                return new Long(-1);
            }
            return new Long(
                Utils.getDataDocProxy().timeFinished("RecoverLostFrags"));
        } catch (AdminException ae) {
            //
            // Internationalize here
            //
            logger.log(Level.WARNING,"can't get time finished from data doctor",ae);
            throw new MgmtException("Failed to retrieve the time of " +
                "the last recovery cycle.");
        }
    }

    public Long getQueryIntegrityTime() throws MgmtException {
        long queryIntegrityTime = MgmtServer.getQueryIntegrityTime();
        return new Long(queryIntegrityTime);
    }

    public Boolean getQuorumReached() throws MgmtException {
        try {
            return new 
              Boolean(Utils.getNodeMgrProxy().hasQuorum());
        } catch (AdminException ae) {
            logger.log(Level.WARNING,"can't get quorum state",ae);
            throw new MgmtException("Failed to retrieve the quorum state.");
        }
    }



    public BigInteger setEncryptedPasswd(String encryptedPasswd) 
        throws MgmtException{
        BigInteger result =super.setEncryptedPasswd(encryptedPasswd);


        String msg = getLocalString("cli.alert.password");       
        Utils.notifyChangeCli(msg);
        return result;
    }
    /**
     * @returns the maximum nummber of disks per node in this cell configuration.
     */
    public BigInteger getNumDisksPerNode() 
        throws MgmtException{
        return BigInteger.valueOf(Utils.getDisksPerNodes());        
    }

    public String getLicense() throws MgmtException {
        String license = config.getProperty(ConfigPropertyNames.PROP_LICENSE);
        if(null== license){
            //
            // Auto generated code can't pass back a null
            // 
            return "";
        } else {
            return license;
        }
    }

    public void setLicense(String value) throws MgmtException {
        HashMap map = new  HashMap();   
	
        map.put(ConfigPropertyNames.PROP_LICENSE, value);
        setCellProperties(map);
    }


    public String getLanguage() throws MgmtException {
        return config.getProperty(ConfigPropertyNames.PROP_LANGUAGE);
    }

    public void setLanguage(String value) throws MgmtException {
        HashMap map = new  HashMap();
        map.put(ConfigPropertyNames.PROP_LANGUAGE, value);
        setCellProperties(map);
    }


    /*
     * This is the list of custom actions
     */



    public BigInteger startExpansion(BigInteger dummy) throws MgmtException {
        if (doExpansionCheck(dummy).intValue() != CliConstants.EXPAN_READY) {
            logger.warning ("Cluster not ready for expansion: cannot continue");
            return BigInteger.valueOf(-1);
        }

        DiskMask preSloshMask = Utils.getCurrentDiskMask();
        HashMap map = new HashMap();

        map.put(ConfigPropertyNames.PROP_NUM_NODES, "16");
        
        map.put(ConfigPropertyNames.PROP_EXP_STATUS, EXPAN_STR_EXPAND);
        map.put(ConfigPropertyNames.PROP_EXP_ENABLED_MASK,   preSloshMask.getEnabledMask());
        map.put(ConfigPropertyNames.PROP_EXP_AVAILABLE_MASK, preSloshMask.getAvailableMask());
        long expansionStart = System.currentTimeMillis();
        map.put(ConfigPropertyNames.PROP_EXP_START, Long.toString(expansionStart));

        setCellProperties(map);

        logger.info ("Started cluster expansion at " 
                      + new Date (expansionStart) + 
                      " with pre-slosh diskmask: " + preSloshMask.toString());
        return BigInteger.valueOf(0);
    }

    public BigInteger stopExpansion(BigInteger dummy) throws MgmtException {
        //
        // fixme - not part of 1.1
        //
        logger.warning ("stopExpansion not yet implemented ");
        return BigInteger.valueOf(0);
    }

    public BigInteger expansionStatus(BigInteger dummy) throws MgmtException {
        try {
            return doExpansionCheck(dummy);
        } catch (Exception e) {
            return BigInteger.valueOf(CliConstants.EXPAN_NT_RDY);
        }
    }
    
    private BigInteger doExpansionCheck (BigInteger dummy) throws MgmtException {
        
        // check to see if we've already expanded (expansion state machine)
        String status = config.getProperty(ConfigPropertyNames.PROP_EXP_STATUS);
        if (status.equals(EXPAN_STR_DONE)) {
            logger.warning ("This cluster has already been expanded");
            return BigInteger.valueOf(CliConstants.EXPAN_DONE);
        } 
        
        if (status.equals(EXPAN_STR_EXPAND)) {
            logger.warning ("This cluster is currently expanding");
            return BigInteger.valueOf(CliConstants.EXPAN_EXPAND);
        }

        NodeMgrService.Proxy nodeMgr = null;
		
		try {
			nodeMgr = Utils.getNodeMgrProxy();
		} catch (AdminException ae) {
			throw new MgmtException ("Clustered services unavailable.");
		}

        // Don't expand if in Maintenance mode
        if (nodeMgr.isMaintenance()) {
            logger.warning ("Cluster in maintenance mode");
			throw new MgmtException ("Cluster in maintenance mode.");
        }

        // Check quorum
        if (! nodeMgr.hasQuorum()) {
            logger.warning ("Cluster does not have quorum");
			throw new MgmtException ("Cluster does not have disk quorum.");
        }


        // Now, check to see if we're really ready for expansion:
        // num_nodes should be 8, and we need to ensure that CMM sees
        // the high 8 nodes.
       
        if (numNodes != 8) {
            logger.warning ("Cluster size is not 8 nodes");
	    throw new MgmtException ("Cluster size is not 8 nodes.");
        }
        
        // check CMM to see how many nodes it sees
        Node[] cmmNodes = nodeMgr.getNodes();

        // If CMM doesn't see 16 possible nodes in the ring, we're not ready
        if (cmmNodes.length < 16) {
            logger.warning ("Expansion nodes not detected");
	    throw new MgmtException ("Required expansion nodes not detected.");
        }

        // Make sure the high 8 nodes are alive before allowing expansion
        for (int i = 9; i < 16; i++) {
            int nodeid = cmmNodes[i].nodeId();
            if (cmmNodes[i] == null || ! cmmNodes[i].isAlive()) {
                logger.warning ("Expansion node (" + nodeid + ") down");
		throw new MgmtException ("Required expansion node (" + nodeid + ") not detected.");
            }

            // Check disks on uppper 8nodes. all 32 should beonline
            Disk[] disks = null;
			try {
				disks = Utils.getDisksOnNode(nodeid);
			} catch (AdminException ae) {
				logger.warning ("Unable to verfiy state of expansion disks");
				throw new MgmtException ("Unable to verify state of expansion disks.");
			}

			if (disks == null) {
				logger.warning ("Unable to verfiy state of expansion disks");
				throw new MgmtException ("Unable to verify state of expansion disks.");
			}

            for (int j = 0; j < disks.length; j++) {
                if (disks[j] == null || ! disks[j].isEnabled()) {
                    logger.warning (
                        "Non-enabled disk (DISK-" + nodeid + ":" + j 
                        + ") on expansion node.");
			throw new MgmtException ("Required disk (DISK-" 
			    + nodeid + ":" + j +") offline.");
                }
            }
        }

        return BigInteger.valueOf(CliConstants.EXPAN_READY);
    }

    public BigInteger wipe(EventSender evt, BigInteger dummy)
        throws MgmtException {

        Reassure reassureThread = new Reassure(evt);
        try {
            cltMgmt.wipeDisks(evt, reassureThread);
        } finally {
            reassureThread.safeStop();
        }
        return BigInteger.valueOf(0);
    }


    public BigInteger verifyNtpServers(String ntpServers) throws MgmtException {
        /*
         * The following checks will be done to validate a ntp server
         * 1. server should be running ntp
         * 2. ntp server should be synced
         * 3. ntp server should not be locally synced
         * 4. for a given ntp server, offset < 5 seconds
         */
        String ntpCmd = Time.SCRIPT_NAME + ntpServers;
        int retcode = Time.SERVER_NOT_VERIFIED;
        try {
            retcode = Exec.exec(ntpCmd);
        } catch(IOException e) {
            logger.log(Level.SEVERE, "Unable to execute " +Time.SCRIPT_NAME, e);
        }
        return BigInteger.valueOf(retcode); 
    }

    /**
     * Reboot the cluster
     * @param evt the event associated with this action
     * @param _switches boolean flag that indicates whether to reboot the switches
     * @param _sp boolean flag that indicates whether to reboot the service processor
     */
    public BigInteger reboot(EventSender evt,
                             BigInteger _switches, 
                             BigInteger _sp) throws MgmtException {

        Reassure reassureThread = null;
        String rebootLogString = "Reboot requested. ";


        try{
            reassureThread = new Reassure(evt);
            reassureThread.start();

            boolean switches=false;
            boolean sp=false;
            if(_sp.intValue() == 0) {
                sp=true;
            } else {
                sp=false;
            }

            if(_switches.intValue() == 0) {
                switches=true;
            } else {
                switches=false;
            }

            if (true == switches) 
                rebootLogString = rebootLogString + " Rebooting switches.";
            else 
                rebootLogString = rebootLogString + " Not rebooting switches.";

            if (true == sp) 
                rebootLogString = rebootLogString + " Rebooting service node.";
            else 
                rebootLogString = rebootLogString + " Not rebooting service node.";

            logger.info(rebootLogString);
            cltMgmt.rebootCell(evt, switches,sp);        
        } catch (Exception e) {
            mgmtServer.logger.log(Level.SEVERE, 
                                  "Failed to reboot cell.",e);
            throw new MgmtException("Failed to reboot cell.");        
        } finally {
            if (reassureThread != null)
                reassureThread.safeStop();
        }		
        return BigInteger.valueOf(0);
    }

    final static int ONE_SECOND = 1000;
    final static int POWER_ON_OFF_SLEEP_TIME = ONE_SECOND * 10;
    final static int MAX_ON_WAIT_MINUTES = 4;
    final static int MAX_ON_WAIT_TIME = ONE_SECOND * 60 * MAX_ON_WAIT_MINUTES;
    final static int MAX_REACHABLE_TIME = ONE_SECOND;

    /***
     * Power node on
     * @param evt the event associated with this action
     * @param node the node identifier of the node to power on
     * @returns BigInteger CliConstants.SUCCESS for success, 
     * CliConstants.FAILURE for general failure, 
     * CliConstants.POWER_ON_HONEYCOMB_SERVICES_DOWN to indicate that
     * the node powered up but the honeycomb services failed to start
     * 
     * @see CliConstants
     */
    public BigInteger powerNodeOn(EventSender evt,BigInteger node) 
    throws MgmtException {


        Reassure reassureThread = null;
	boolean running = false;
        try {
            reassureThread = new Reassure(evt);
            reassureThread.start();

            logger.info("Issuing node power on.");
            cltMgmt.powerNodeOn(node.intValue());
	    
            long endTime = new Date().getTime() + MAX_ON_WAIT_TIME;
	    int nodeNum = node.intValue();
            do {
 		try {
                    if((running=Utils.getNodeMgrProxy(nodeNum)
                        .getNode().isAlive()) == true) {
                        break;
                    }
                    Thread.currentThread().sleep(POWER_ON_OFF_SLEEP_TIME);
                }
                catch (ThreadDeath td) {
                    if (reassureThread != null)
                        reassureThread.safeStop();
                    throw td;
                }
                catch (Throwable ignore) {
                    // Lookup of node manager proxy will fail when the honeycomb
                    // services on the node are down.
                   Thread.currentThread().sleep(POWER_ON_OFF_SLEEP_TIME);
                }
            } while (running == false && new Date().getTime() < endTime);
	    if (running)
		return BigInteger.valueOf(CliConstants.SUCCESS);

	    // Honeycomb services aren't running.
	    // Is the node up?
            running = cltMgmt.getNodePowerStatus(nodeNum);
	    if (running == false) {
		// Node is DOWN, return failure error
		mgmtServer.logger.severe("Failed to power on NODE-" + nodeNum
               	    + ". Maximum wait time of " + MAX_ON_WAIT_MINUTES
               	    + " expired and node is still down.");
               	return BigInteger.valueOf(CliConstants.FAILURE);
	    }
	    //
	    // Node is up
	    //
	    return BigInteger.valueOf(CliConstants.POWER_ON_HONEYCOMB_SERVICES_DOWN);
        } 
	catch (ThreadDeath td) {
	    throw td;
	}
	catch (Throwable e) {
            mgmtServer.logger.log(Level.SEVERE, 
		"Failed to power on NODE-" + node, e);
            throw new MgmtException("Failed to power on NODE-" + node 
		+ "Reason: " + e.getMessage());
        }
        finally {        
            if (reassureThread != null)
                reassureThread.safeStop();
        }
    }

    final static int MAX_OFF_WAIT_MINUTES = 4;
    final static int MAX_OFF_WAIT_TIME = ONE_SECOND * 60 * MAX_OFF_WAIT_MINUTES;
    final static int HACK_WAIT_TIME = ONE_SECOND * 60;


    public BigInteger powerOff(EventSender evt,
                               BigInteger _useIpmi, 
                               BigInteger _sp) throws MgmtException {

        boolean useIpmi=false;
        boolean sp=false;

        if(_sp.intValue() == 0) {
            sp=true;
        } else {
            sp=false;
        }

        if (_useIpmi.intValue() == 0) {
            useIpmi=true;
        } else {
            useIpmi=false;
        }
        String shutdownLogString = "Shutdown requested. ";

        if (true == useIpmi) 
            shutdownLogString = shutdownLogString + " Using ipmi for shutdown..";
        else 
            shutdownLogString = shutdownLogString + " Not using ipmi for shutdown.";
        
        if (true == sp) 
            shutdownLogString = shutdownLogString + " Shutting down service node.";
        else 
            shutdownLogString = shutdownLogString + " Not shutting down service node.";
        
        logger.info(shutdownLogString);

        cltMgmt.powerOff(evt, useIpmi, sp);
        return BigInteger.valueOf(CliConstants.SUCCESS);
    }


    /***
     * Power node off
     * @param evt the event associated with this action
     * @param node the node identifier of the node to power on
     * @param _useIpmi boolean flag.  true indicates use ipmi to power node off,
     * false otherwise
     * @returns BigInteger 
     * CliConstants.SUCCESS (0) for success,
     * CliConstants.FAILURE (-1) for general failure
     */
    public BigInteger powerNodeOff(EventSender evt, BigInteger node, BigInteger _useIpmi)
        throws MgmtException {
 

        boolean useIpmi=false;
        if(_useIpmi.intValue()==0)
            useIpmi=true;
        else
            useIpmi=false;

        Reassure reassureThread = null;
        try {
            reassureThread = new Reassure(evt);
            reassureThread.start();
	    
	    int nodeNum = node.intValue();
	    boolean isMaster = MultiCellLib.getInstance().isCellMaster()
		&& Utils.getNodeMgrProxy(nodeNum).getNode().isMaster();
	    
            cltMgmt.powerNodeOff(node.intValue(),useIpmi);
	    
	    if (isMaster) {
		// Since powering down the master node kills the JVM
		// we don't wait for confirmation of the success or
		// failure.  We return immediately.
		return BigInteger.valueOf(CliConstants.SUCCESS);
	    }

            boolean running=false;
            long endTime = new Date().getTime() + MAX_OFF_WAIT_TIME;
            do {
                try {
		    // Returns true if node is up
		    running = cltMgmt.getNodePowerStatus(nodeNum);
		    
		    if(running) {
                        Thread.currentThread().sleep(POWER_ON_OFF_SLEEP_TIME); 
                    }
                } 
		catch (ThreadDeath td) {
		    throw td;
		}
		catch (Throwable ignore) {
                }
            } while (running == true && new Date().getTime() < endTime);

            if (running) {	
                mgmtServer.logger.severe("Failed to power off NODE-" + node
                    + ".  Maximum wait time of " + MAX_OFF_WAIT_MINUTES
                    + " expired and node is still offline.");
                return BigInteger.valueOf(CliConstants.FAILURE);
            }

	    
	    // System has been successfully powered off.  There is however, a problem
	    // that the cache that holds the state of the node takes some
	    // time before it realizes the node is offline.  Ideally we'd like
	    // to make a call and force it to realize this state.  But it's not
	    // clear how to do this.  If we return now the command hwstat will 
	    // still show the node as being online.  Hack fix call 
	    // getNodeMgrProxy till it tells us it's dead
	    try
	    {
		for (int i=0; i < 10; i++) {
		    if (Utils.getNodeMgrProxy(nodeNum).getNode().isAlive() == false) {
			break;
		    }
		    Thread.currentThread().sleep(POWER_ON_OFF_SLEEP_TIME); 
		}
	    }
	    catch (Exception ignore) {
		// The proxy lookup will fail when the node is down.  This
		// will mean that it's safe to return since the system will see
		// that we are offline
	    }
        } 
	catch (Exception e) {
            mgmtServer.logger.log(Level.SEVERE, 
		"Failed to power off node:" + node, e);
            throw new MgmtException("Failed to power off NODE-" + node);        
        }
        finally
        {
            if (reassureThread != null)
                reassureThread.safeStop();
        }		
        return BigInteger.valueOf(CliConstants.SUCCESS);
    }


    public BigInteger clearSchema(BigInteger dummy) throws MgmtException {

        logger.info("clearSchema");
        cltMgmt.clearSchema(-1);
        String msg = getLocalString("cli.alert.clearSchema");       
        Utils.notifyChangeCli(msg);
        return BigInteger.valueOf(CliConstants.MGMT_OK);
    }

    public BigInteger retrySchema(BigInteger dummy) throws MgmtException {

        logger.info("retrySchema");

        try {
            MetadataClient.getInstance().updateSchema();
        } catch (EMDException e) {
             logger.severe("Failed to update the schema ["+
               e.getMessage()+"]");
             return BigInteger.valueOf(CliConstants.MGMT_RETRY_SCHEMA_FAILED);
        }
        return BigInteger.valueOf(CliConstants.MGMT_OK);
    }


    public BigInteger updateSchema(EventSender evt, String schema,
      Long timestamp, Byte mask, Byte updateSchema)
        throws MgmtException {
        
        boolean isValidationOnly = (updateSchema.byteValue() == 0) ? 
          true : false;
        boolean isFirstSchemaPiece = 
          ((mask.byteValue() & CliConstants.MDCONFIG_FIRST_MESSAGE) == 
            CliConstants.MDCONFIG_FIRST_MESSAGE);
        boolean isLastSchemaPiece = 
          ((mask.byteValue() & CliConstants.MDCONFIG_LAST_MESSAGE) == 
            CliConstants.MDCONFIG_LAST_MESSAGE);
        long stamp = timestamp.longValue();

        if ((schemaBuffer != null) && 
          (stamp != schemaTimestamp)) {
            logger.severe("already uploading a schema...");
            return BigInteger.valueOf(-1);
        } else {
            if (isFirstSchemaPiece) {
                System.err.println("New schema");
                schemaBuffer = new StringBuffer();
                schemaTimestamp = stamp;
            }
        }

        schemaBuffer.append(schema);
        if (!isLastSchemaPiece) {
           logger.info("Received new piece of schema message");
            return BigInteger.valueOf(0);
        } else {
            logger.info("Received last piece of schema");
        }

        String newSchema = schemaBuffer.toString();
        schemaBuffer = null;
        schemaTimestamp = -1;

        logger.info("updateSchema validationOnly = " + 
            isValidationOnly);
        //
        // Validate the schema
        //
        ByteArrayInputStream stream = null;
        try {
            stream = new ByteArrayInputStream(newSchema.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException ue) {
            logger.log(Level.SEVERE,
              "Unsupported encoding type : UTF-8", ue);
            //
            // Internationalize here
            //
            throw new MgmtException("Internal error, can't validate schema.");
        }


        RootNamespace namespace = null;
        try {
            namespace = RootNamespace.getNewInstance();
            XMLConfigHandler.CLI_CONTEXT = true;
            namespace.readConfig(stream, false);
            XMLConfigHandler.CLI_CONTEXT = false;
            namespace.validateSchema();
        } catch (EMDConfigException e) {
            logger.info("failed to validate schema " + e);
            MgmtException newe = new MgmtException(e.getMessage());
            throw newe;
        }

        logger.info("updateSchema, validation successful ");

        if (isValidationOnly) {
            return BigInteger.valueOf(CliConstants.MGMT_OK);
        }

        try {
            evt.sendSynchronousEvent("Successful schema validation [cell " +
                localCellid + "]");
        } catch (MgmtException e) {
            logger.severe("failed to send synchronous event " +
                e);   
        }
        logger.info("updateSchema, ... will create new config. ");

        //
        // Create new config file
        //
        File configFile = null;
        while (configFile == null) {
            configFile = new File(RootNamespace.userConfig.getAbsolutePath() + 
              "."+stamp);
            if (configFile.exists()) {
                stamp++;
                configFile = null;
            }
        }
        Writer output = null;
        try {
            output = new BufferedWriter(
                         new OutputStreamWriter(
                             new FileOutputStream(configFile),
                             "UTF-8"));
            namespace.export(output, false);
        } catch (IOException e) {
            logger.severe("failed to export the new schema " + e);
            return BigInteger.valueOf(CliConstants.MGMT_CMM_CONFIG_UPDATE_FAILED);
        } finally {
            if (output != null) {
                try {
                    output.close(); 
                } catch (IOException ignored) {
                }
            }
        }

        logger.info("updateSchema, new config file created " +
            ", stamp = " + stamp);

        //
        // Propagate the new config file around the ring
        //
        try {
            CMM.getAPI().storeConfig(CMMApi.UPDATE_METADATA_FILE,
              stamp, "0000000000000000");
        } catch (Exception e) {
            logger.severe("Failed to propagate the configuration ["+
              e.getMessage()+"]");
            return BigInteger.valueOf(CliConstants.MGMT_CMM_CONFIG_UPDATE_FAILED);
        }

        try {
            evt.sendSynchronousEvent("successful config/update [cell " +
                localCellid + "]");
        } catch (MgmtException e) {
            logger.severe("failed to send synchronous event " +
                e);   
        }
        logger.info("updateSchema, CMM config successful ");


        //
        // Ask HADB to perform the config update
        //
        try {
            MetadataClient.getInstance().updateSchema();
        } catch (Exception e) {
                 logger.severe("Failed to load the schema ["+
                   e.getMessage()+"]");
            return BigInteger.valueOf(CliConstants.MGMT_HADB_LOAD_SCHEMA_FAILED);
        }

        try {
            evt.sendSynchronousEvent("successfully loaded new schema [cell " +
                localCellid + "]");
        } catch (MgmtException e) {
            logger.severe("failed to send synchronous event " +
                e);   
        }
        logger.info("updateSchema, hadb loaded schema successfully");
        String msg = getLocalString("cli.alert.newSchema");       
        Utils.notifyChangeCli(msg);
        return BigInteger.valueOf(CliConstants.MGMT_OK);
    }

    public BigInteger retrieveSchema(EventSender evt, Byte template)
        throws MgmtException {

        boolean isTemplate = (template.byteValue() == 1) ? true : false;
        StringBuffer buffer = null;

        logger.info("retrieve schema, template = " + isTemplate);

        if (isTemplate) {
            buffer = new StringBuffer();
            FileInputStream stream = null;
            try {
                byte [] tmp = new byte[1024];
                int nbRead;

                stream = new FileInputStream(RootNamespace.templateFile);
                do {
                    nbRead = stream.read(tmp);
                    if (nbRead > 0) {
                        for (int i = 0; i < nbRead; i++) {
                            buffer.append((char) tmp[i]);
                        }
                    }
                } while (nbRead > 0);
            } catch (IOException e) {
                logger.severe("IOException while reading config " +
                    e);
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        } else {
            Writer out = null;
            StringWriter swo = new StringWriter();
            try {
                RootNamespace namespace = RootNamespace.getInstance();
                out = new BufferedWriter(swo);
                namespace.export(out, true);
                out.flush();
            } catch (EMDConfigException e) {
                logger.severe("Failed to read the config ["+
                  e.getMessage()+"]");
            } catch (IOException e) {
                 logger.severe("Failed to read the config ["+
                   e.getMessage()+"]");
            } finally {
                try {
                    out.close();
                } catch (IOException ignre) {
                    logger.severe("failed to close writer stream " +
                        " for schema");
                    return null;
                }
            }
            buffer = swo.getBuffer();
        }
        
        if(Utils.sendBuffer(buffer,evt) == -1) {
            return BigInteger.valueOf(CliConstants.MGMT_CANT_RETRIEVE_SCHEMA);
        }
        return BigInteger.valueOf(CliConstants.MGMT_OK);

    }
    /**
     * Query the state of the switches - returns true if both 
     * switches are online. Useful to check this before issuing configuration commands
     */
    public BigInteger getSwitchesState(BigInteger dummy) throws MgmtException {
        int activeSwitchId = Integer.parseInt(Switch.getActiveSwitchId());

        if (activeSwitchId!=1 || (!Switch.isBackupSwitchAlive()) ) {
            return BigInteger.valueOf(1);
        }

        return BigInteger.valueOf(0);
            
    }


    // The prototype in mgmt/mof/hcadm.mof requires a dummy argument
    // and a dummy return type, even if the function doesnt return or takes
    // any parameter.  This is an issue with MOF compiler which seem to 
    // mandate the input and return arguments. So one would see a lot of 
    // places where "dummy" arguments/returns used.

   public void updateSwitch(Map changedProps)
        throws MgmtException {

        boolean updateSwitch=false;
        boolean updateSp=false;
        Iterator it = changedProps.keySet().iterator();
        //
        // This loop is currently a no-op; we call "updateSwitchUnconditionally" below..
        // Why? we should enable this and re-run tests.
        //
        while (it.hasNext()) {
            String key = (String) it.next();
            String value = (String) changedProps.get(key);
            for(int i=0;i<cellProps.length && updateSwitch==false;i++) {
                if(key.equals(cellProps[i]) && (!value.equals(config.getProperty(key)))) {
                    updateSwitch=true;
                }
                    
            }
        }
        // if(updateSwtich)
	updateSwitchUnconditionally();
    }


    /**
     * Perform and update of the all the switich properties that are currently
     * stored in MultiCellLib and ClusterProperties
     */
    public void updateSwitchUnconditionally()
        throws MgmtException {
	
	logger.logp(Level.INFO, this.getClass().getName(), "updateSwitch", "TIME: start");
	
        //
        // If any properties get added here, update the static cellProps strucre at the top 
        // of this file
        //
        ClusterProperties props = ClusterProperties.getInstance();
	MultiCellLib cellLib = MultiCellLib.getInstance();
        String adminIp = cellLib.getInstance().getAdminVIP();
        String dataIp = cellLib.getInstance().getDataVIP();
        String spIp = cellLib.getSPVIP();
        String subnet = cellLib.getSubnet();
        String gateway = cellLib.getGateway();

        String ntp = props.getProperty(ConfigPropertyNames.PROP_NTP_SERVER);
        String smtp = props.getProperty(ConfigPropertyNames.PROP_SMTP_SERVER);
        String smtp_port = props.getProperty(ConfigPropertyNames.PROP_SMTP_PORT);
        String extlogger = props.getProperty(ConfigPropertyNames.PROP_EXT_LOGGER);
        String authClients = props.getProperty(ConfigPropertyNames.PROP_AUTH_CLI);

        String dns = props.getProperty(ConfigPropertyNames.PROP_DNS);
        String domain_name = props.getProperty(ConfigPropertyNames.PROP_DOMAIN_NAME);
        String dns_search = props.getProperty(ConfigPropertyNames.PROP_DNS_SEARCH);
        String primary_dns_server = props.getProperty(ConfigPropertyNames.PROP_PRIMARY_DNS_SERVER);
        String secondary_dns_server = props.getProperty(ConfigPropertyNames.PROP_SECONDARY_DNS_SERVER);

        Switch.updateAll(dataIp,
                         adminIp,
                         spIp,
                         smtp,
                         smtp_port,
                         ntp,
                         subnet,
                         gateway,
                         extlogger,
                         authClients,
                         dns,
                         domain_name,
                         dns_search,
                         primary_dns_server,
                         secondary_dns_server);
	logger.logp(Level.INFO, this.getClass().getName(), "updateSwitch", "TIME: end");
    }


    public String getDate() throws MgmtException {

        BufferedReader in = null;
        float timeOffset = Time.MAX_COMPLIANT_TIME_OFFSET;
        String masterNodeDate;
        String externalNtpServer = null;
        boolean isHCDateCompliant = false;
        String ntpCmd;
        int retcode;
        String dateStr;
        String line;
        Pattern p;
        Matcher m;
 
        TimeManagedService.Proxy TimeProxy;
        Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE,
                                         "TimeKeeper");
        if (! (obj instanceof TimeManagedService.Proxy)) {
            logger.severe("TimeManagedService.Proxy not available");
            throw new MgmtException("Unable to retrieve date from cell.");
        }
        TimeProxy = (TimeManagedService.Proxy) obj;
 
        // Since AdminServer runs only on the master node 
        // getting date is as simple as running `date` command
        masterNodeDate = (new Date()).toString();       

	// Don't show time compliance information for now.  Removed for 1.1 release
        //
        // Is Master Node Time Compliant? 
        // isHCDateCompliant = TimeProxy.isMasterNodeCompliant() ? true : false;
        //if(isHCDateCompliant) {
        //   dateStr = masterNodeDate + " [ " +  getLocalString("info.date.compliance_date_str") + " ]";
        //} else {
        //   dateStr = masterNodeDate + " [ " +  getLocalString("info.date.nocompliance_date_str") + " ]";
        //} 
        dateStr =  masterNodeDate;     
	return dateStr;
    } 
    

    // ==========================================================================
    // PRIVATE methods
    // ==========================================================================
    


    /**
     * Perform and update of the all the switich properties that are currently
     * stored in MultiCellLib
     */
    public BigInteger updateServiceProcessor(BigInteger dummy) throws MgmtException {
	
	logger.logp(Level.INFO, this.getClass().getName(), "updateServiceProcessor()", "TIME: start");
	MultiCellLib cellLib = MultiCellLib.getInstance();
        String spIp = cellLib.getSPVIP();
        String subnet = cellLib.getSubnet();
        String gateway = cellLib.getGateway();
        ServiceProcessor.updateAll(spIp, subnet, gateway);
	logger.logp(Level.INFO, this.getClass().getName(), "updateServiceProcessor()", "TIME: end");
        return BigInteger.valueOf(0);
    }

    protected String getLocalString(String l) {
        try {
            return BundleAccess.getInstance().getBundle().getString(l);
        } catch (MissingResourceException e) {

            String missingString=getLocalString("common.missingString");
            return missingString;
        }
    }

    //
    // Needs to return from cellcfg before we start the 'reboot'
    // when we execute cellcfg on a remote cell (multicell)
    //
    protected void updatePropertiesAndRebootCell(Map props,
      boolean updateSwitch, boolean updateSP) {
        CellCfgAsync executor = 
          new CellCfgAsync(props, updateSwitch, updateSP);
        executor.start();
    }

    protected  void rebootCell(EventSender evt,
                               boolean switches, 
                               boolean sp) throws MgmtException {
        ClusterManagement.getInstance().rebootCell(null, true, true);
    }
    

    /**
     * Creates the explorer defaults file which is required to run the explorer
     * tool.  If successful, the explorer script is executed to collect log
     * information and send it back to Sun Service via HTTPS.
     * @param evt required for interactive commands
     * @param expProps master cell explorer config params
     * @return BigInteger exit value for logdump -- success/failure
     * @throws MgmtException when unable to save configuration settings
     */
    public BigInteger scrapeLogs(EventSender evt, HCExpProps expProps) 
                                                throws MgmtException { 
        String strCellId =  getCellId().toString(localCellid);
        int cellId = getCellId().intValue();
        // create explorer defaults file based on the configuration info set
        // in the cluster properties file on the master cell
        if (!ExplorerDefaultsWriter.createDefaultsFile(expProps)) {
            logger.log(Level.SEVERE, "Unable to create explorer defaults" +
                    " file for CELL-" + strCellId);
            return BigInteger.valueOf(CliConstants.FAILURE);  
        }
        String cmd = "/opt/SUNWexplo/bin/explorer -P -D -d " +
                        EXPLORER_FILE + " -t /var/adm -w st5800";

        logger.log(Level.INFO, "CELL-" + strCellId + 
                                ":Invoking explorer script with " + cmd);
        try {  
            return execExplorer(evt, cmd, strCellId);
        } catch (Exception e) {
            logger.log(Level.SEVERE, 
                    "CELL-" + strCellId + ":Error running scripts: ", e);
            return BigInteger.valueOf(CliConstants.FAILURE);        
        } finally {
            try {
                Exec.exec ("/usr/bin/rm " + EXPLORER_FILE);
            } catch (IOException ioe) {
                logger.log (Level.WARNING,
                            "Unable to remove: " + EXPLORER_FILE);
            }
        }

    }
}

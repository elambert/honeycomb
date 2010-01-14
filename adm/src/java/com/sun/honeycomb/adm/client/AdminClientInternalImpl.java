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

import com.sun.honeycomb.cm.node_mgr.*;
import com.sun.honeycomb.adm.cli.*;
import com.sun.honeycomb.adm.*;
import com.sun.honeycomb.cm.*;
import com.sun.honeycomb.datadoctor.DataDocConfig;
import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.StringTokenizer;
import java.util.Date;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.util.Switch;
import com.sun.honeycomb.emd.config.RootNamespace;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import com.sun.honeycomb.emd.config.EMDConfigException;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import com.sun.honeycomb.emd.MetadataClient;
import java.util.Map;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.util.ServiceProcessor;
import java.text.MessageFormat;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.AdminResourcesConstants;
import com.sun.honeycomb.adm.cli.config.CliConfigProperties;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.admin.mgmt.client.Fetcher;
import java.math.BigInteger;
import com.sun.honeycomb.admin.mgmt.client.HCDDCycles;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.admin.mgmt.client.HCSilo;
import com.sun.honeycomb.admin.mgmt.client.HCProfiler;
import com.sun.honeycomb.admin.mgmt.client.HCHadb;
import com.sun.honeycomb.admin.mgmt.client.HCLoglevels;
import  com.sun.honeycomb.priv.mgmt.client.HCTestConfigUpdate;
import  com.sun.honeycomb.priv.mgmt.client.HCNVProperties;
import  com.sun.honeycomb.priv.mgmt.client.HCNameValueProp;
import  com.sun.honeycomb.priv.mgmt.client.HCNameValuePropArray;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.adm.cli.PermissionException;


/**
 * A straight-forward implementation of the Client API that uses the Honeycomb
 * CMM IPC mechanism to communication with the hive.
 */
public class AdminClientInternalImpl 
    implements AdminClientInternal {
    private SiloInfo siloInfo = null;

    private static final long LOGIN_TIMEOUT=1000*60*1; // one minute timeout
    private static final Logger logger =
        Logger.getLogger(AdminClientInternalImpl.class.getName());

    private AdminClient api = null;

    public AdminClientInternalImpl(AdminClient api) 
        throws ConnectException, PermissionException, MgmtException {
        super();
        siloInfo = SiloInfo.getInstance();
        this.api = api;
    }
    //
    // log levels
    //
    public void setLogLevel(byte cellId,
      int nodeId, String jvmName, int level)         
        throws MgmtException,ConnectException,PermissionException {
        if(!api.loggedIn()) 
            throw new PermissionException();
        HCLoglevels logLevels = getLoglevels(cellId);
        logLevels.setLevel(BigInteger.valueOf(nodeId),BigInteger.valueOf(level),jvmName);
    }
    public String[] getLogLevels(byte cellId) 
        throws MgmtException,ConnectException {
        HCLoglevels logLevels = getLoglevels(cellId);
        String[] retArray = new String[logLevels.getLogLevels().size()];
        Iterator it = logLevels.getLogLevels().iterator();
        int i=0;
        while(it.hasNext()){
            retArray[i]=(String)it.next();
            i++;
        }
        return retArray;
    }




    public void clearHADBFailure(byte cellId) 
        throws MgmtException,ConnectException,PermissionException {
        if(!api.loggedIn()) 
            throw new PermissionException();


        HCHadb hadb =
          Fetcher.fetchHCHadb(siloInfo.getServerUrl(cellId));
        Object[] params = new Object[1]; 
        params[0] = Long.toString(api.getLoggedInSessionId());
        api.extLog(ExtLevel.EXT_INFO, AdminResourcesConstants.
                MSG_KEY_SET_CLEAR_HADB_FAILURE, params, "clearHADBFailure");
        hadb.clearHadbFailure(BigInteger.valueOf(0));


    }



    /*
     * Profiler commands
     */
    
    public void profilerStart(String module, byte cellId, int nodeid, int howlong) 
        throws MgmtException,ConnectException,PermissionException {
        if(!api.loggedIn()) 
            throw new PermissionException();

        HCProfiler profiler = getProfiler(cellId);
        profiler.start(module,BigInteger.valueOf(nodeid),BigInteger.valueOf(howlong));
    }
    public void profilerStop(byte cellId) 
        throws MgmtException,ConnectException,PermissionException {
        if(!api.loggedIn()) 
            throw new PermissionException();

        HCProfiler profiler = getProfiler(cellId);
        
        profiler.stop(        BigInteger.valueOf(0));
    }
    public String profilerTarResult(byte cellId) 
        throws MgmtException,ConnectException,PermissionException {
        if(!api.loggedIn()) 
            throw new PermissionException();

        HCProfiler profiler = getProfiler(cellId);
        return(profiler.tarResult(BigInteger.valueOf(0)));
        
    }

    public String listAvailableModules(byte cellId) throws MgmtException ,ConnectException
    {
        HCProfiler profiler = getProfiler(cellId);
        return profiler.getModules();
    }

    /**
     * ddcfg commands
     */


    public void restoreDdDefaults(byte cellId) 
        throws MgmtException,ConnectException,PermissionException {
        if(!api.loggedIn()) 
            throw new PermissionException();

        HCDDCycles cycles = getDDCycles(cellId);
        Object[] params = new Object[1];    // no params
        api.extLog(ExtLevel.EXT_INFO, AdminResourcesConstants.
                MSG_KEY_DATA_DOC_SET_DEFAULTS, params, "restoreDdDefaults");

        cycles.restoreDdDefaults(BigInteger.valueOf(0));

    }
    public void setAllDdProps(byte cellId, long cycleGoal) 
        throws MgmtException,ConnectException,PermissionException  {
        if(!api.loggedIn()) 
            throw new PermissionException();

        HCDDCycles cycles = getDDCycles(cellId);
        cycles.setRemoveDupFragsCycle(BigInteger.valueOf(cycleGoal));
        cycles.setRemoveTempFragsCycle(BigInteger.valueOf(cycleGoal));
        cycles.setPopulateSysCacheCycle(BigInteger.valueOf(cycleGoal));          
        cycles.setPopulateExtCacheCycle(BigInteger.valueOf(cycleGoal));
        cycles.setRecoverLostFragsCycle(BigInteger.valueOf(cycleGoal));
        cycles.setSloshFragsCycle(BigInteger.valueOf(cycleGoal));
        cycles.setScanFragsCycle(BigInteger.valueOf(cycleGoal));

        // Log before issue command
        Object [] params = new Object[2];
        params[0] = Long.toString(api.getLoggedInSessionId());
        params[1] = Long.toString(cycleGoal);
        String msgKey = AdminResourcesConstants.MSG_KEY_DATA_DOC_SET;
        if (cycleGoal == DataDocConfig.CG_DONT_RUN) {
            msgKey = AdminResourcesConstants.MSG_KEY_DATA_DOC_SET_OFF;
        } else if (cycleGoal == DataDocConfig.CG_FULL_SPEED) {
            msgKey = AdminResourcesConstants.MSG_KEY_DATA_DOC_SET_FULLSPEED;
        }
        api.extLog(ExtLevel.EXT_INFO, msgKey, params, "setAllDdProps");      
        cycles.push();
    }

    public void setDDCycle(byte cellId,int cycleType, long cycleGoal) 
        throws MgmtException,ConnectException,PermissionException 
    {
        if(!api.loggedIn()) 
            throw new PermissionException();

        String msgKey = AdminResourcesConstants.MSG_KEY_DATA_DOC_SET;
        String methodName = "setDDCycle";
        Object [] params = new Object[2];
        params[0] = Long.toString(api.getLoggedInSessionId());
        params[1] = Long.toString(cycleGoal);
   
        HCDDCycles cycles = getDDCycles(cellId);
        if(cycleType==AdminClientInternal.REMOVE_DUP_FRAGS_CYCLE) {
            cycles.setRemoveDupFragsCycle(BigInteger.valueOf(cycleGoal));
            api.extLog(ExtLevel.EXT_INFO, AdminResourcesConstants.
                    MSG_KEY_DATA_DOC_SET_DUP_FRAGS, params, methodName);   
        }

        if(cycleType==AdminClientInternal.REMOVE_TEMP_FRAGS_CYCLE) {
            cycles.setRemoveTempFragsCycle(BigInteger.valueOf(cycleGoal));
            api.extLog(ExtLevel.EXT_INFO, AdminResourcesConstants.
                    MSG_KEY_DATA_DOC_SET_TEMP_FRAGS, params, methodName);   
        }

        if(cycleType==AdminClientInternal.POPULATE_SYS_CACHE_CYCLE) {
            cycles.setPopulateSysCacheCycle(BigInteger.valueOf(cycleGoal));
            api.extLog(ExtLevel.EXT_INFO, AdminResourcesConstants.
                    MSG_KEY_DATA_DOC_SET_POP_SYS_CACHE, params, methodName);
        }

        if(cycleType==AdminClientInternal.POPULATE_EXT_CACHE_CYCLE)  {
            cycles.setPopulateExtCacheCycle(BigInteger.valueOf(cycleGoal));
            api.extLog(ExtLevel.EXT_INFO, AdminResourcesConstants.
                    MSG_KEY_DATA_DOC_SET_POP_EXT_CACHE, params, methodName);   
        }

        if(cycleType==AdminClientInternal.RECOVER_LOST_FRAGS_CYCLE) {
            cycles.setRecoverLostFragsCycle(BigInteger.valueOf(cycleGoal));
            api.extLog(ExtLevel.EXT_INFO, AdminResourcesConstants.
                    MSG_KEY_DATA_DOC_SET_RECOVER_LOST_FRAGS, params, 
                    methodName);   
        }

        if(cycleType==AdminClientInternal.SLOSH_FRAGS_CYCLE) {
            cycles.setSloshFragsCycle(BigInteger.valueOf(cycleGoal));
            api.extLog(ExtLevel.EXT_INFO, AdminResourcesConstants.
                    MSG_KEY_DATA_DOC_SET_SLOSH_FRAGS, params, 
                    methodName);   
        }

        if(cycleType==AdminClientInternal.SCAN_FRAGS_CYCLE) {
            cycles.setScanFragsCycle(BigInteger.valueOf(cycleGoal));
            api.extLog(ExtLevel.EXT_INFO, AdminResourcesConstants.
                    MSG_KEY_DATA_DOC_SET_SCAN_FRAGS, params, 
                    methodName);   
        }

        cycles.push();
    }
    public int getDDCycle(byte cellId,int cycleType) throws MgmtException,ConnectException{
        HCDDCycles cycles = getDDCycles(cellId);
        if(cycleType==AdminClientInternal.REMOVE_DUP_FRAGS_CYCLE) 
            return cycles.getRemoveDupFragsCycle().intValue();

        else if(cycleType==AdminClientInternal.REMOVE_TEMP_FRAGS_CYCLE) 
            return cycles.getRemoveTempFragsCycle().intValue();

        else if(cycleType==AdminClientInternal.POPULATE_SYS_CACHE_CYCLE) 
            return cycles.getPopulateSysCacheCycle().intValue();

        else if(cycleType==AdminClientInternal.POPULATE_EXT_CACHE_CYCLE) 
            return cycles.getPopulateExtCacheCycle().intValue();

        else if(cycleType==AdminClientInternal.RECOVER_LOST_FRAGS_CYCLE) 
            return cycles.getRecoverLostFragsCycle().intValue();

        else if(cycleType==AdminClientInternal.SLOSH_FRAGS_CYCLE) 
            return cycles.getSloshFragsCycle().intValue();

        else if(cycleType==AdminClientInternal.SCAN_FRAGS_CYCLE) 
            return cycles.getScanFragsCycle().intValue();
        else return -1;
        
    }

    public HCDDCycles getDDCycles(byte cellId) throws MgmtException,ConnectException{
        HCDDCycles cycles= Fetcher.fetchHCDDCycles(siloInfo.getServerUrl(cellId));
        return cycles;
    }


    private HCProfiler getProfiler(byte cellId) throws MgmtException,ConnectException{
        HCProfiler cycles= Fetcher.fetchHCProfiler(siloInfo.getServerUrl(cellId));
        return cycles;
    }

    private HCLoglevels getLoglevels(byte cellId) throws MgmtException,ConnectException{
        HCLoglevels cycles= Fetcher.fetchHCLoglevels(siloInfo.getServerUrl(cellId));
        return cycles;
    }

    public int startNewExecutor(byte cellId,
      long latency, boolean createInterface, 
      boolean nodeFailure, int rateFailure)
        throws MgmtException, ConnectException, PermissionException {
        if(!api.loggedIn()) 
            throw new PermissionException();
        String url = siloInfo.getServerUrl(cellId);
        HCTestConfigUpdate cfg = 
          com.sun.honeycomb.priv.mgmt.client.Fetcher.fetchHCTestConfigUpdate(
              siloInfo.getServerUrl(cellId));
        byte failure = (nodeFailure) ? (byte) 1 : (byte) 0;
        byte create = (createInterface) ? (byte) 1 : (byte) 0;

        BigInteger res = cfg.startExecutor(new Long(latency),
          new Byte(create), new Byte(failure), BigInteger.valueOf(rateFailure));
        return res.intValue();
    }

    public String stopExistingExecutor(byte cellId,
      int executorId) 
        throws MgmtException, ConnectException,PermissionException {
        if(!api.loggedIn()) 
            throw new PermissionException();
        HCTestConfigUpdate cfg = 
          com.sun.honeycomb.priv.mgmt.client.Fetcher.fetchHCTestConfigUpdate(
              siloInfo.getServerUrl(cellId));
        return cfg.stopExecutor(BigInteger.valueOf(executorId));
    }

    public int setProperties(byte cellId, Map map)
        throws MgmtException, ConnectException,PermissionException {
        if(!api.loggedIn()) 
            throw new PermissionException();
        HCNVProperties hcProps = 
          com.sun.honeycomb.priv.mgmt.client.Fetcher.fetchHCNVProperties(
              siloInfo.getServerUrl(cellId));

        HCNameValuePropArray nvProps = new HCNameValuePropArray();
        List<HCNameValueProp> list = nvProps.getProperties();

        Iterator it = map.keySet().iterator();
        while (it.hasNext()) {
            HCNameValueProp curProp = new HCNameValueProp();
            String name = (String) it.next();
            curProp.setName(name);
            String value = (String) map.get(name);
            curProp.setValue(value);
            list.add(curProp);
        }
        BigInteger res = hcProps.setProperties(nvProps);
        return res.intValue();
    }
}

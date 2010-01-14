
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


import java.util.Locale;
import java.util.ArrayList;
import java.util.Map;

import java.io.InputStream;
import java.io.PrintStream;

import java.math.BigInteger;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.adm.cli.ConnectException;
import com.sun.honeycomb.admin.mgmt.client.HCDDCycles;
import com.sun.honeycomb.admin.mgmt.client.HCProfiler;
import com.sun.honeycomb.admin.mgmt.client.HCLoglevels;
import com.sun.honeycomb.adm.cli.PermissionException;
/**
 * Interface for internal testing functions that commands can use.
 */
public interface AdminClientInternal {

    public static final int REMOVE_DUP_FRAGS_CYCLE=0;
    public static final int REMOVE_TEMP_FRAGS_CYCLE=1;
    public static final int POPULATE_SYS_CACHE_CYCLE=2;
    public static final int POPULATE_EXT_CACHE_CYCLE=3;
    public static final int RECOVER_LOST_FRAGS_CYCLE=4;
    public static final int SLOSH_FRAGS_CYCLE=5;
    public static final int SCAN_FRAGS_CYCLE=6;


    //
    // log levels
    //
    public void setLogLevel(byte cellId,
      int nodeId, String jvmName, int level)         
        throws MgmtException,ConnectException,PermissionException;

    public String[] getLogLevels(byte cellId) 
        throws MgmtException,ConnectException;

    //
    // hadb
    //

    public void clearHADBFailure(byte cellId)
        throws MgmtException,ConnectException,PermissionException;
    //
    // profiler
    //
    public void profilerStart(String module, byte cellId, int nodeid, int howlong) 
        throws MgmtException,ConnectException,PermissionException;
    public void profilerStop(byte cellId) 
        throws MgmtException,ConnectException,PermissionException;
    public String profilerTarResult(byte cellId) 
        throws MgmtException,ConnectException,PermissionException;
    public String listAvailableModules(byte cellId)
        throws MgmtException,ConnectException;


    //
    // ddcfg
    //
    public void restoreDdDefaults(byte cellId) 
        throws MgmtException,ConnectException,PermissionException;
    public void setAllDdProps(byte cellId, long cycleGoal) 
        throws MgmtException,ConnectException,PermissionException;

    public void setDDCycle(byte cellId,int cycleType, long cycleGoal)
        throws MgmtException,ConnectException,PermissionException;
    public int getDDCycle(byte cellId,int cycleType) 
        throws MgmtException,ConnectException;
    public HCDDCycles getDDCycles(byte cellId)
        throws MgmtException,ConnectException;

    // priv module
    public int startNewExecutor(byte cellid,
      long latency, boolean createInterface, boolean nodeFailure,
      int rateFailure)
        throws MgmtException, ConnectException,PermissionException;
    public String stopExistingExecutor(byte cellid,
      int executorId) 
        throws MgmtException, ConnectException,PermissionException;

    public int setProperties(byte cellId, Map map)
               throws MgmtException, ConnectException,PermissionException;


}

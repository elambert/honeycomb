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

import org.w3c.dom.Document;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.math.BigInteger;
import java.util.logging.Logger;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.Properties;

import com.sun.honeycomb.mgmt.common.MgmtException;

public class HCDDCyclesAdapter
    implements HCDDCyclesAdapterInterface {
    private static final String DEFAULT_CONF_FILE = 
        "/config/config_defaults.properties";

    private static transient final Logger logger = 
        Logger.getLogger(HCDDCyclesAdapter.class.getName());

    public void loadHCDDCycles()
        throws InstantiationException {
    }

    public BigInteger getRemoveDupFragsCycle() throws MgmtException {
        return BigInteger.valueOf(ClusterProperties.getInstance().
                                  getPropertyAsInt("honeycomb.datadoctor.remove_dup_frags_cycle"));
    }
    public void setRemoveDupFragsCycle(BigInteger value) throws MgmtException {
        try {
            ClusterProperties.getInstance().put(
                "honeycomb.datadoctor.remove_dup_frags_cycle",
                  value.toString());
        } catch (ServerConfigException e) {
            logger.severe("failed to set dd cycle " + e);
            //
            // Internationalize here
            //
            throw new MgmtException("Failed to set remove_dup_frags_cycle to " + value);

                       
        }
    }
    public BigInteger getRemoveTempFragsCycle() throws MgmtException {
        return BigInteger.valueOf(ClusterProperties.getInstance().
                                  getPropertyAsInt("honeycomb.datadoctor.remove_temp_frags_cycle"));
    }
    public void setRemoveTempFragsCycle(BigInteger value) throws MgmtException {
        try {
            ClusterProperties.getInstance().put(
                "honeycomb.datadoctor.remove_temp_frags_cycle",
                  value.toString());
        } catch (ServerConfigException e) {
            logger.severe("failed to set dd cycle " + e);
            //
            // Internationalize here
            //
            throw new MgmtException("Failed to set remove_temp_frags_cycle to " + value);                       
        }
    }
    public BigInteger getPopulateSysCacheCycle() throws MgmtException {
        return BigInteger.valueOf(ClusterProperties.getInstance().
                                  getPropertyAsInt("honeycomb.datadoctor.populate_sys_cache_cycle"));
    }

    public void setPopulateSysCacheCycle(BigInteger value) throws MgmtException {
        try {
            ClusterProperties.getInstance().put(
                "honeycomb.datadoctor.populate_sys_cache_cycle",
                  value.toString());
        } catch (ServerConfigException e) {
            logger.severe("failed to set dd cycle " + e);
            //
            // Internationalize here
            //
            throw new MgmtException("Failed to set populate_sys_cache_cycle to " + value);

                       
        }
    }


    public BigInteger getPopulateExtCacheCycle() throws MgmtException {
        return BigInteger.valueOf(ClusterProperties.getInstance().
                                  getPropertyAsInt("honeycomb.datadoctor.populate_ext_cache_cycle"));
    }
    public void setPopulateExtCacheCycle(BigInteger value) throws MgmtException {
        try {
            ClusterProperties.getInstance().put(
                "honeycomb.datadoctor.populate_ext_cache_cycle",
                  value.toString());
        } catch (ServerConfigException e) {
            logger.severe("failed to set dd cycle " + e);
            //
            // Internationalize here
            //
            throw new MgmtException("Failed to set populate_ext_cache_cycle to " + value);

                       
        }
    }



    public BigInteger getRecoverLostFragsCycle() throws MgmtException {
        return BigInteger.valueOf(ClusterProperties.getInstance().
                                  getPropertyAsInt("honeycomb.datadoctor.recover_lost_frags_cycle"));
    }
    public void setRecoverLostFragsCycle(BigInteger value) throws MgmtException {
        try {
            ClusterProperties.getInstance().put(
                "honeycomb.datadoctor.recover_lost_frags_cycle",
                  value.toString());
        } catch (ServerConfigException e) {
            logger.severe("failed to set dd cycle " + e);
            //
            // Internationalize here
            //
            throw new MgmtException("Failed to set recover_lost_frags_cycle to " + value);

                       
        }
    }


    public BigInteger getSloshFragsCycle() throws MgmtException {
        return BigInteger.valueOf(ClusterProperties.getInstance().
                                  getPropertyAsInt("honeycomb.datadoctor.slosh_frags_cycle"));
    }
    public void setSloshFragsCycle(BigInteger value) throws MgmtException {
        try {
            ClusterProperties.getInstance().put(
                "honeycomb.datadoctor.slosh_frags_cycle", value.toString());
        } catch (ServerConfigException e) {
            logger.severe("failed to set dd cycle " + e);
            //
            // Internationalize here
            //
            throw new MgmtException("Failed to set slosh_frags_cycle to " + value);

                       
        }
    }





    public BigInteger getScanFragsCycle() throws MgmtException {
        return BigInteger.valueOf(ClusterProperties.getInstance().
                                  getPropertyAsInt("honeycomb.datadoctor.scan_frags_cycle"));
    }
    public void setScanFragsCycle(BigInteger value) throws MgmtException {
        try {
            ClusterProperties.getInstance().put(
                "honeycomb.datadoctor.scan_frags_cycle", value.toString());
        } catch (ServerConfigException e) {
            logger.severe("failed to set dd cycle " + e);
            //
            // Internationalize here
            //
            throw new MgmtException("Failed to set scan_frags_cycle to " + value);

                       
        }
    }

    public BigInteger restoreDdDefaults(BigInteger dummy) throws MgmtException {


        String[] ddPropNames = {
            "honeycomb.datadoctor.populate_ext_cache_cycle",
            "honeycomb.datadoctor.populate_sys_cache_cycle",
            "honeycomb.datadoctor.recover_lost_frags_cycle",
            "honeycomb.datadoctor.remove_dup_frags_cycle",
            "honeycomb.datadoctor.remove_temp_frags_cycle",
            "honeycomb.datadoctor.scan_frags_cycle",
            "honeycomb.datadoctor.slosh_frags_cycle",

        };
        
        HashMap pending = new HashMap();

        // load the original properties file from disk
        Properties defaultProps = new Properties();
        File defaultsFile = new File (DEFAULT_CONF_FILE);
        try { 
            defaultProps.load(new FileInputStream(defaultsFile));
        } catch (IOException ioe) {
            logger.severe("Cannot read config file "+
                          DEFAULT_CONF_FILE+": "+ioe);
            //
            // Internationalize here
            //
            throw new MgmtException("Cannot read config file "+
                                        DEFAULT_CONF_FILE+": "+ioe);

        }

        for (int i=0; i < ddPropNames.length; i++) {
            String name = ddPropNames[i];
            String defValue = defaultProps.getProperty(name);

            if (defValue == null) {
                logger.severe("internal error: property "+
                              name+" not found in default config file");
                //
                // Internationalize here
                //
                throw new MgmtException("Internal error: property "+
                                           name+" not found in default config file.");

            }
            pending.put(name, defValue);
        }
        try {
            ClusterProperties.getInstance().putAll(pending);
        } catch (ServerConfigException e) {
            logger.severe("failed to set dd cycle defaults " + e);
            //
            // Internationalize here
            //
            throw new MgmtException("Failed to set dd defaults.");
        }
        return BigInteger.valueOf(0);
    }
}

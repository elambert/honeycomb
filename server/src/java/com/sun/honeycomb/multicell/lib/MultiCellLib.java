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



package com.sun.honeycomb.multicell.lib;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Iterator;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Observer;
import java.util.Observable;
import java.io.OutputStream;
import java.io.File;
import java.io.ByteArrayOutputStream;

import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.multicell.lib.Rule.Interval;
import com.sun.honeycomb.cm.cluster_membership.messages.api.ConfigChangeNotif;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.multicell.MultiCellIntf;
import com.sun.honeycomb.common.UID;


public final class MultiCellLib
    extends MultiCellLibBase implements Observer
{
    private boolean hasRegisteredCmm;
    static private MultiCellLib multicellLib = null;

    //
    // Get instance of MultiCellLib
    //
    static public synchronized MultiCellLib getInstance() {
        if (multicellLib == null) {
            multicellLib = new MultiCellLib();
        }
        return multicellLib;
    }

    //
    // Implementation of the Observer interface.
    //
    public synchronized void update(Observable o, Object arg) {
        if (! (arg instanceof ConfigChangeNotif)) {
            logWarning("received unexpected notifications, ignore...");
            return;
        }
        ConfigChangeNotif notif = (ConfigChangeNotif) arg;
        if ((notif.getFileUpdated() != CMMApi.UPDATE_SILO_FILE) ||
            (notif.getCause() != ConfigChangeNotif.CONFIG_UPDATED)) {
            return;
        }
        refreshConfiguration("update");
    }


    /**
     * Register for notifications about properties maintained in the 
     * silo_info.xml config file.
     * @param l the listener
     */
    public void addPropertyListener(PropertyChangeListener l) {
        synchronized (listeners) {
            if (!hasRegisteredCmm) {
                try {
                    ServiceManager.register (ServiceManager.CONFIG_EVENT,
                      multicellLib);
                    hasRegisteredCmm = true;
                } catch (Exception e) {
                    logSevere("unable to register for CMM notifications");
                }
            }
            if (!listeners.contains(l)) {
                listeners.add(l);
            }
        }
    }

    /**
     * Remove listener
     * @param l the listener
     */
    public void removePropertyListener(PropertyChangeListener l) {
        synchronized (listeners) {
            if (listeners.contains (l)) {
                listeners.remove(l);
            }
        }
    }

    //
    // API for Protocol server to retrun latest MC XMl config to clients.
    //
    public String getVersion()
        throws MultiCellLibException {

        MultiCellIntf.Proxy proxy = MultiCellIntf.Proxy.getProxy();
        if (proxy == null) {
            logSevere("can't retrieve Multicell proxy");
            throw new MultiCellLibException("can't retrieve Multicell proxy");
        }
        String version = proxy.getMajorVersion() + "." + 
          proxy.getMinorVersion();
        return version;
    }


    public byte [] getXMLConfig()
        throws IOException, MultiCellLibException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        MultiCellIntf.Proxy proxy = MultiCellIntf.Proxy.getProxy();
        if (proxy == null) {
            logSevere("can't retrieve Multicell proxy");
            throw new MultiCellLibException("can't retrieve Multicell proxy");
        }

        XMLWriter.generateXMLClient(proxy.getCells(),
                                    proxy.getMajorVersion(),
                                    proxy.getMinorVersion(),
                                    out);
        return out.toByteArray();
    }

    public String getConfigFileName() {
        return SILO_INFO_FILE;
    }

    //
    // Call default implementation in base class.
    //
    public short getNextSiloLocation(UID uid) {
        return getNextSiloLocationImpl(uid);
    }

    public byte getOriginCellid(byte ruleNumber, short siloLocation)
        throws MultiCellLibException {
        return getOriginCellidImpl(ruleNumber, siloLocation);
    }

    public byte getRuleNumber(byte originCellid, short siloLocation)
        throws MultiCellLibException {
        return getRuleNumberImpl(originCellid, siloLocation);
    }


    //
    // Private
    //
    private MultiCellLib() {
        super();;
        hasRegisteredCmm = false;
    }


    protected void updateConfig(String type, long newVersionMajor) {

        logInfo("updateConfig \"" + type + "\":" + " newVersionMajor = " +
        newVersionMajor);

        long newTimestamp = System.currentTimeMillis();
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(getConfigFileName() +
                                       "." + newTimestamp);
            XMLWriter.generateXML(xmlParser.getHiveConfig(),
                                  newVersionMajor,
                                  out);

            CMM.getAPI().storeConfig(CMMApi.UPDATE_SILO_FILE,
              newTimestamp, "0000000000000000");

        } catch (ServerConfigException se) {
            logSevere("failed to generate config update for " +
                      CMMApi.UPDATE_SILO_FILE + " file", se);
        } catch  (CMMException cmme) {
            logSevere("failed to generate config update for " +
                      CMMApi.UPDATE_SILO_FILE + " file", cmme);
        } catch (IOException ioe) {
            logSevere("failed to generate config update for " +
                       CMMApi.UPDATE_SILO_FILE + " file", ioe);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }
        logInfo("successfully updated the silo_info config");
    }
}

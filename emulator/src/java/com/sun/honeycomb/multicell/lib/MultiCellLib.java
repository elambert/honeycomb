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

import com.sun.honeycomb.cm.NodeMgr;
import java.net.InetAddress;
import java.net.UnknownHostException;

import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.UID;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.multicell.CellInfo;
import com.sun.honeycomb.multicell.MultiCell;
import com.sun.honeycomb.protocol.server.ProtocolHandler;
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagData;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class MultiCellLib extends MultiCellLibBase
{

    static private MultiCellLib multicellLib = null;
    static private final String BOGUS_IP_ADDRESS = "0.0.0.0";
    static private final String DEFAULT_SUBNET = "255.255.255.0"; 
    static private final int EMULATOR_MULTICELL_MAJOR_VERSION = 1;	
    static private final int EMULATOR_MULTICELL_MINOR_VERSION = 0;	
    static private String emulatorName;
    static private String defaultEmulatorPort;

    static {
        // We need the default Emulator port
        ClusterProperties cp = ClusterProperties.getInstance();
        defaultEmulatorPort =
            cp.getProperty(ProtocolConstants.API_SERVER_PORT_PROPERTY,
                           Integer.toString(ProtocolConstants.DEFAULT_PORT));
        // We need the name of this box
        try {
            InetAddress me = InetAddress.getLocalHost();
            emulatorName = me.getHostName();
        } catch (UnknownHostException e){
            throw new Error("Can't retrieve localhost " + e);
        }
    }

    //
    // Get instance of MultiCellLib
    //
    static public synchronized MultiCellLib getInstance() {
        if (multicellLib == null) {
            multicellLib = new MultiCellLib();
        }
        return multicellLib;
    }
    
    
    /**
     * Register for notifications about properties maintained in the 
     * silo_info.xml config file.
     * @param l the listener
     */
    public void addPropertyListener(PropertyChangeListener l) {
        synchronized (listeners) {
            try {
                // It's unclear what if anything the emulator 
                // should do here.
            } catch (Exception e) {
                logSevere("unable to register for CMM notifications");
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
    // Get instance of MultiCellLib
    //
    static public synchronized MultiCellLib getInstance(boolean register) {
        return getInstance();
    }

    //
    // API for Protocol server to retrun latest MC XML config to clients.
    //
    public String getVersion()
        throws MultiCellLibException {

        if (!isMultiCellConfigured()) {
            return EMULATOR_MULTICELL_MAJOR_VERSION+"."+
                EMULATOR_MULTICELL_MINOR_VERSION;
        }

        String version = getMajorVersion() + "." +
          MultiCell.getInstance().getMinorVersion();
        return version;
    }

    public  byte [] getXMLConfig()
        throws IOException, MultiCellLibException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!isMultiCellConfigured()) {
            ArrayList result = new ArrayList();
            CellInfo defaultCell = createDummyCell();
            result.add(defaultCell);
            XMLWriter.generateXMLClient(result, 
                                        EMULATOR_MULTICELL_MAJOR_VERSION,
                                        EMULATOR_MULTICELL_MINOR_VERSION,
                                        out);
        } else {
            XMLWriter.generateXMLClient(MultiCell.getInstance().getCells(
                MultiCell.DEEP_COPY, true),getMajorVersion(),
                MultiCell.getInstance().getMinorVersion(), out);
        }
        return out.toByteArray();
    }
  
    public CellInfo createDummyCell() {
        ArrayList defaultList =  new ArrayList();
        Rule defaultRule = new Rule((byte) 1);

        defaultList.add(defaultRule);

        // Get the client's original address for this server, and
        // send it back to the client in the multicell config
        // This makes the traceproxy work, and also fixes bug 6571818
        String host = ProtocolHandler.getSessionRequestedIP();
        if (host == null) {
            throw new RuntimeException("createDummyCell called from a context"+
                                       " without a getSessionRequestedIP");
        }
        // Assume the Default Port, though this may get overridden if the session
        // requested host name had a different port number.
        String port = defaultEmulatorPort;
        int colon = host.indexOf(":");
        if (colon != -1){
            port = host.substring(colon+1);
            host = host.substring(0, colon);
        }

        String emulatorAddress = host + ":" + port;
        ServiceTagData tagData = new ServiceTagData();
        tagData.setInstanceURN("DUMMY");
        CellInfo dummyCell = 
            new CellInfo((byte) 0, BOGUS_IP_ADDRESS,
                         emulatorAddress, BOGUS_IP_ADDRESS, 
                         emulatorName,
                         DEFAULT_SUBNET, BOGUS_IP_ADDRESS, 
                         defaultList,
                         tagData);

        return dummyCell;
    }

    public String getConfigFileName() {
        return NodeMgr.getInstance().getEmulatorRoot() +
          "/" + SILO_INFO_FILE;
    }

    public short getNextSiloLocation(UID uid) {
        if (!isMultiCellConfigured()) {
            return (short) 0;
        } else {
            return getNextSiloLocationImpl(uid);
        }
    }

    public byte getOriginCellid(byte ruleNumber, short siloLocation)
        throws MultiCellLibException {
        if (!isMultiCellConfigured()) {
            return (byte) 0;
        } else {
            return getOriginCellidImpl(ruleNumber, siloLocation);
        }
    }

    public byte getRuleNumber(byte originCellid, short siloLocation)
        throws MultiCellLibException {
        if (!isMultiCellConfigured()) {
            return (byte) 1;
        } else {
            return getRuleNumberImpl(originCellid, siloLocation);
        }
    }

    protected void updateConfig(String type, long newVersionMajor) {

        logInfo("updateConfig \"" + type + "\"" +
          ", version = " + newVersionMajor);
        long newTimestamp = System.currentTimeMillis();
        FileOutputStream out = null;
        try {
            String link = getConfigFileName() + "." + newTimestamp;
            out = new FileOutputStream(link);
            XMLWriter.generateXML(xmlParser.getHiveConfig(),
              newVersionMajor,
              out);
            activate(link);
        } catch (IOException ioe) {
            logSevere("failed to generate config update for " +
              getConfigFileName() + " file", ioe);
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

    private int exec(String cmd) {

        int rc = -1;
        Runtime runtime = Runtime.getRuntime();
        try {
            Process process = runtime.exec(cmd);
            rc = process.waitFor();
        } catch(Exception e) {
            logSevere("failed to exec cmd " + cmd);
            return -1;
        }
        return rc;
    }


    private void activate(String link) {
        try {
            StringBuffer cmd = new StringBuffer();
            cmd.append ("/usr/bin/ln -sf ");
            cmd.append(link);
            cmd.append(" ");
            cmd.append(getConfigFileName());

            int exitCode = exec(cmd.toString());
            if (exitCode != 0) {
                logSevere("Link failure: ["+cmd+"] ["+exitCode+"]");
            }
        } catch (Exception e) {
            logSevere("Unable to symlink config !");
        }
    }

    private boolean isMultiCellConfigured() {
        if (cellid == (byte) 0) {
            return false;
        } else {
            return true;
        }
    }

    private MultiCellLib() {
        super();
    }

}

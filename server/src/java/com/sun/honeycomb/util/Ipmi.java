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



package com.sun.honeycomb.util;

import java.io.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.StringTokenizer;

import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.common.InternalException;

/**
 * This class encapsulates a few util functions pattered on popen/exec*
 *
 * @author Peter Buckingham
 * @version $Version$ $Date$
 */
public class Ipmi {

    // Singleton
    public static String IPMITOOL = "/usr/sfw/bin/ipmitool ";
    private static final Logger logger = Logger.getLogger(Ipmi.class.getName());

    public static String PASSWORD = "/opt/honeycomb/share/ipmi-pass ";
    public static final int IPMI_POWER_ON = 0;
    public static final int IPMI_POWER_OFF = 1;
    public static final int IPMI_POWER_CYCLE = 2;
    public static final int IPMI_POWER_RESET = 3;
    public static final int IPMI_POWER_STATUS = 4;
    private static final String[] power = { "on", "off", "cycle", "reset", "status" };

    public static final int IPMI_BOOT_PXE = 0;
    public static final int IPMI_BOOT_DISK = 1;
    public static final int IPMI_BOOT_SAFE = 2;
    public static final int IPMI_BOOT_DIAG = 3;
    public static final int IPMI_BOOT_CDROM = 4;
    public static final int IPMI_BOOT_BIOS = 5;
    public static final String[] boot = { "pxe", "disk", "safe", "diag",
                                          "cdrom", "bios" };

    public static final int IPMI_LOCATOR_LED = 0;
    public static final int IPMI_NODE_FAULT_LED = 1;
    public static final int IPMI_HDD1_LED = 2;
    public static final int IPMI_HDD2_LED = 3;
    public static final int IPMI_HDD3_LED = 4;
    public static final int IPMI_HDD4_LED = 5;
    private static final String[] ledId = { "0x00 ", "0x01 ", "0x02 ",
                                            "0x03 ", "0x04 ", "0x05 " };

    public static final int IPMI_DISK_BAD = 1;
    public static final int IPMI_DISK_GOOD = 0;

    public static final int IPMI_LED_ON = IPMI_DISK_BAD;
    public static final int IPMI_LED_OFF = IPMI_DISK_GOOD;
    private static final String[] ledStatus = { "0x00", "0x01" };
    public static final int IPMI_NODE_BASE = 100;
    //This is for string "hcbxxx", to get offset of nodeId xxx
    public static final int IPMI_NODEID_OFF = 3;
    

    public static BufferedReader ipmi(String raw) throws IOException {
                return Exec.execRead(IPMITOOL + raw, logger);
    }

    private static String ipmitool (String host) 
        throws IOException {
        String cmd = IPMITOOL;
        try {
            if (!host.equalsIgnoreCase("local") &&
                !isMasterNode(host)) {
                 cmd += "-I lan -H " + host + "-sp -U Admin -f " + PASSWORD;
            }
        } catch (ManagedServiceException mse) {
            throw new IOException("Operation failed to get proxy/master");
        }

        return cmd;
    }

    public static String nodeName(int nodeId) {
        
        if ( nodeId < IPMI_NODE_BASE) {
            nodeId = nodeId + IPMI_NODE_BASE;
        } 
        return "hcb" + nodeId;
    }

    public static String fwVersion() {
        String line;
        String ret = null;
        BufferedReader out = null;

        try {

            out = ipmi("mc info");

            while ((line = out.readLine()) != null) {
                if (line.startsWith("Firmware Revision")) {
                    ret = line.substring(28);
                    break;
                }
            }
        } catch (IOException e) {}
        finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {}
        }

        return ret;
    }

    public static boolean power(String node, int opt)
        throws IOException {
        String cmd = ipmitool(node) +  "chassis power " + power[opt];

        Exec.exec(cmd);

        return true;
    }

    private static BufferedReader powerResults(String node, int opt)
        throws IOException {
        BufferedReader out = null;
        String cmd = ipmitool(node) +  "chassis power " + power[opt];

        out = Exec.execRead(cmd);

        return out;
    }

    public static boolean powerOn(String node) throws IOException {

        
        return power(node, IPMI_POWER_ON);
    }

    public static boolean powerOn(int nodeId) throws IOException {

        return powerOn(nodeName(nodeId));
    }

    public static boolean powerOff() throws IOException {

        return power("local", IPMI_POWER_OFF);
    }

    public static boolean powerOff(String node) throws IOException {

        boolean status = power(node, IPMI_POWER_OFF);
	return status;
    }

    public static boolean powerOff(int nodeId) throws IOException {
        boolean status = powerOff(nodeName(nodeId));
	return status;
    }

    public static boolean powerCycle() throws IOException {

        return power("local", IPMI_POWER_CYCLE);
    }

    public static boolean powerCycle(String node) throws IOException {

        return power(node, IPMI_POWER_CYCLE);
    }

    public static boolean powerCycle(int nodeId) throws IOException {

        return powerCycle(nodeName(nodeId));
    }

    public static boolean powerReset() throws IOException {

        return power("local", IPMI_POWER_RESET);
    }

    public static boolean powerReset(String node) throws IOException {

        return power(node, IPMI_POWER_RESET);
    }

    public static boolean powerReset(int nodeId) throws IOException {

        return powerReset(nodeName(nodeId));
    }
    

    /**
     * Determine whether the power status of the specified node is on
     *
     * @param String node the node id to check
     * @return boolean true if power status of the node is on, false otherwise
     */
    public static boolean powerStatus(String node) throws IOException {
        BufferedReader out = null;
        String line;

        out=powerResults(node, IPMI_POWER_STATUS);

        while ((line = out.readLine()) != null) {
	    // Looks like the messages we use to match on
	    // can change.  Right now output says:
	    //
	    // Chassis Power is on
	    //
	    // But we should probably should not rely on this not changing.
	    // Parse the string and look for the word on or off.  Ignore
	    // the other words.
	    StringTokenizer st = new StringTokenizer(line.toLowerCase());
	     while (st.hasMoreTokens()) {
		 String value = st.nextToken();
		 if (value.equals("on"))
		     return true;
		 if (value.equals("off"))
		     return false;
	     }
        }
	throw new InternalException("Parsing of power status failed.");
    }

    public static boolean powerStatus(int nodeId) throws IOException {

        return powerStatus(nodeName(nodeId));
    }

    public static void bootDevice(String node, int opt, boolean clearCMOS)
        throws IOException {
        String cmd = ipmitool(node) +  " chassis bootdev " + boot[opt];

        if (clearCMOS) {
            cmd += " clear-cmos=yes";
        }

        Exec.exec(cmd);
    }

    // FIX ME, The exception handling for Led should be fixed
    public static String ledStr(String node, int id, int opt) 
        throws IOException {

        return ipmitool(node) + "raw 0x30 0x08 " + ledId[id] + ledStatus[opt];
    }

    public static String ledStr(int id, int opt) 
        throws IOException {

        return ipmitool("local") + "raw 0x30 0x08 " + ledId[id] + ledStatus[opt];
    }

    public static void led(String node, int id, int opt) {
        String cmd = null;
        String line;

        try {
            cmd = ledStr(node, id, opt);
            BufferedReader output = Exec.execRead(cmd, logger);
            while ((line = output.readLine()) != null) {
                logger.info("output: " + line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void nodeLocator(String node, int opt) {
        led(node, IPMI_LOCATOR_LED, opt);
    }

    public static void nodeFault(String node, int opt) {
        led(node, IPMI_NODE_FAULT_LED, opt);
    }

    /**
     * diskLed() - Turns the disk lights on or off
     *
     * node - node identifier ie hcb101
     * disk - disk identifier ie DISK-116:0
     * opt - on =  off = 0
     */
    public static void diskLed(String node, String disk, int opt) {
        int hddId = Character.getNumericValue(disk.charAt(disk.length() - 1));

        /* Because the IDs for the HDD begin at 0x02 we can
           just add 2 to the disk Id to get the HDD ID.
        */
        led(node, hddId + 2, opt);
    }

    public static String diskLedStr(String node, String disk, int opt) {
        int hddId = Character.getNumericValue(disk.charAt(disk.length() - 1));

        /* Because the IDs for the HDD begin at 0x02 we can
           just add 2 to the disk Id to get the HDD ID.
        */
        String str = null;
        try { 
            if (node != null) {
                str = ledStr(node, hddId + 2, opt);
            } else {
                str = ledStr(hddId + 2, opt);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return str;
    }

    public static void diskLed(DiskId disk, boolean on) {

        diskLed("hcb" + disk.nodeId(),
                "DISK-" + disk.nodeId() + ":" + disk.diskIndex(),
                on ? IPMI_LED_ON : IPMI_LED_OFF);
    }

    public static String diskLedStr(DiskId disk, boolean on) {

        return diskLedStr("hcb" + disk.nodeId(),
                "DISK-" + disk.nodeId() + ":" + disk.diskIndex(),
                on ? IPMI_LED_ON : IPMI_LED_OFF);
    }

    public static String localDiskLedStr(DiskId disk, boolean on) {

        return diskLedStr(null,
                "DISK-" + disk.nodeId() + ":" + disk.diskIndex(),
                on ? IPMI_LED_ON : IPMI_LED_OFF);
    }

    private static NodeMgrService.Proxy getProxy() throws ManagedServiceException {
        Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);

        if (!(obj instanceof NodeMgrService.Proxy)) {
            logger.severe("Can't get NodeMgr proxy!");
            throw new ManagedServiceException("Failed to get Proxy"); 
        }

        return (NodeMgrService.Proxy) obj;
    }

    private static boolean isMasterNode(String node)
        throws ManagedServiceException {
        Node masterNode=null;
        NodeMgrService.Proxy proxy=null;
        try {
            proxy = getProxy();
        } catch (ManagedServiceException mse) {
            throw new ManagedServiceException("Operation failed to get proxy"); 
        }

        masterNode= proxy.getMasterNode();
        if (masterNode != null) {
           return (masterNode.nodeId() ==  
                Integer.parseInt(node.substring(IPMI_NODEID_OFF)));
        } else {
            throw new ManagedServiceException("Failed to get Master Node"); 
        }
    }

}

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

import com.sun.honeycomb.config.ClusterProperties;

import java.util.StringTokenizer;
import java.util.Observer;
import java.util.Observable;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.UnknownHostException;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.sun.honeycomb.common.InternalException;

import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.jvm_agent.CMAException;

/*
 * This class manages the node power service
 */
public class AsfPowerController implements PowerController {

    public static final int RESET = 0x10;
    public static final int ON = 0x11;
    public static final int OFF = 0x12;
    public static final int CYCLE = 0x13;

    private Map nodeAddrs = null;

    private static final Logger logger =
        Logger.getLogger(AsfPowerController.class.getName());

    private static final int ASF_PORT = 623;

    protected AsfPowerController() {
        getNodeAddrs();
    }
    
    public boolean powerOff (int nodeId) {
        logger.info("powering off node " + nodeId);
        try {
            return sendASF(nodeId, OFF);
        } catch(IOException e) {
            logger.log(Level.SEVERE, "Couldn't send ASF packet: ", e);
        }
        return false;
    }

    public boolean powerOn (int nodeId) {
        logger.info("Powering ON node " + nodeId);
        try {
            return sendASF(nodeId, ON);
        } catch(IOException e) {
            logger.log(Level.SEVERE, "Couldn't send ASF packet: ", e);
        }
        return false;
    }

    public boolean powerCycle (int nodeId) {
        logger.info("Powering cycling node " + nodeId);
        try {
            return sendASF(nodeId, CYCLE);
        } catch(IOException e) {
            logger.log(Level.SEVERE, "Couldn't send ASF packet: ", e);
        }
        return false;
    }

    public boolean reset (int nodeId) {
        logger.info("RESET " + nodeId);
        try {
            return sendASF(nodeId, RESET);
        } catch(IOException e) {
            logger.log(Level.SEVERE, "Couldn't send ASF packet: ", e);
        }
        return false;
    }

    public void start() {
    }

    public void stop() {
    }

    private boolean sendASF(int nodeId, int what) throws IOException {
        ByteBuffer packet = getPacket(what);
        InetAddress addr = getAddr(nodeId);
        logger.info("Sending " + toStr(packet) + " to " + addr);
        DatagramSocket s = new DatagramSocket();
        s.send(new DatagramPacket(packet.array(), packet.remaining(),
                                  addr, ASF_PORT));
        s.close();

        // Maybe wait for a reply?
        return true;
    }

    /** Lookup addresses for all nodes */
    private void getNodeAddrs() {
        nodeAddrs = new HashMap();
        NodeMgrService.Proxy proxy = 
            ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        Node[] nodes = proxy.getNodes();
        if (nodes == null || nodes.length == 0) {
            // If we couldn't get the nodes, it's serious
            logger.severe("Null node list from NodemgrMailbox!");
            throw new InternalException("Couldn't get node list");
        }

        for (int i = 0; i < nodes.length; i++) {
            InetAddress addr;
            String nodeAddress = nodes[i].getAddress();
            try {
                addr = InetAddress.getByName(nodeAddress);
            } catch(UnknownHostException e) {
                throw new InternalException("Couldn't lookup address: " + 
                                            nodeAddress);
            }
            nodeAddrs.put(new Integer(nodes[i].nodeId()), addr);
        }
    }

    private InetAddress getAddr(int nodeId) {
        return (InetAddress) nodeAddrs.get(new Integer(nodeId));
    }

    private ByteBuffer getPacket(int what) {
        /**
         * Constructs an ASF packet for power control. This is the format:<PRE>
         * uint8_t version = 6 (ASF)
         * uint8_t reserved1 = 0
         * uint8_t sequence = 0xff->No ACK
         * uint8_t msg_class = 7 [6 is supposed to be ASF!]
         * uint32_t iana = htonl(4542)
         * uint8_t msg_type = 0x10->RESET, 0x11->ON, 0x12->OFF, 0x13->CYCLE
         * uint8_t msg_tag = 0xff->No ACK
         * uint8_t reserved2 = 0
         * uint8_t data_len = len
         * uint8_t buf[len] = any char data</PRE>
         *
         * Documentation is on the <a
         * href="http://www.dmtf.org/standards/asf">Alert Standard
         * Format</a> page.
         *
         * @param what power on, off, or cycle
         * @return buffer with the packet
         */

        ByteBuffer buf = ByteBuffer.allocate(20);
        buf.clear();
        buf.put((byte)6);
        buf.put((byte)0);
        buf.put((byte)-1);      // 0xff
        buf.put((byte)7);
        buf.putInt(4542);
        buf.put((byte)what);
        buf.put((byte)-1);      // 0xff
        buf.put((byte)0);
        buf.put((byte)0);

        buf.flip();

        return buf;
    }

    private String toStr(ByteBuffer buf) {
        byte[] b = buf.array();
        String s = "";
        for (int i = 0; i < buf.remaining(); i++) {
            String d = Integer.toHexString(b[i]);
            int l = d.length();
            if (l < 2) d = "0" + d;
            if (l > 2) d = d.substring(l-2, l);
            s += d;
        }
        return s;
    }
}

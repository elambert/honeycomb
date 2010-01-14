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



package com.sun.honeycomb.spreader;

import com.sun.honeycomb.common.ProtocolConstants;

/**
 * This class represents a datagram to be sent to a ZNYX switch.
 * It's a stub because we're really using an external program
 * zrule2 for this purpose.
 *
 * @author Shamim Mohamed <shamim@sun.com>
 * @version $Id: ZNetlink2Message.java 10855 2007-05-19 02:54:08Z bberndt $
 */
public class ZNetlink2Message {

    public static final String[] PACKET_TYPES = { "http"};
    public static final int CPUPORT = 24;
    public static final int NULLPORT = -1;
    public static final int ACCEPT = 42; // arbitrary

    /**
     * @param action - action to perform: can be "add" or "delete"
     * @param type - type of packet the rule applies to
     * @param hashVal - hash value of interest
     * @param switchPort - if redirecting, switch port to send to
     *
     * "delete" means that any mapping for hash value hashVal is
     * removed. The value of switchPort is not used. (This better
     * be followed with an "add"!)
     *
     * "add" means a rule is set up for packets with the hash
     * value hashVal to be sent to switchPort. The normal case
     * mapping is (nodeFrom, nodeFrom).
     *
     * The class knows about the disposition of packets: ARP sent
     * to CPU, TCP and UDP sent to the port defined by the hash
     */
    public ZNetlink2Message(String action, String type,
			    int hashVal, int switchPort) {
    }

    /**
     * @param action - action to perform: can be "add" or "delete"
     * @param IPPort - TCP/UDP port the packet is addressed to
     * @param hashVal - hash value of interest
     * @param switchPort - if redirecting, switch port to send to
     *
     * "delete" means that any mapping for hash value hashVal is
     * removed. The value of switchPort is not used. (This better
     * be followed with an "add"!)
     *
     * "add" means a rule is set up for packets with the hash
     * value hashVal to be sent to switchPort. The normal case
     * mapping is (nodeFrom, nodeFrom).
     *
     * The class knows about the disposition of packets: ARP sent
     * to CPU, TCP and UDP sent to the port defined by the hash
     */
    public ZNetlink2Message(String action, int IPPort,
			    int hashVal, int switchPort) {
    }

    /**
     * This constructor is used when packets are received. Currently
     * unimplemented because no one uses it.
     */
    public ZNetlink2Message(byte[] packet) {
    }

    /**
     * Return size of the packet in bytes (octets)
     */
    public int getSize() {
	return 0;
    }

    /**
     * Actual content of message
     */
    public byte[] getBytes() {
	return null;
    }

}

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

import java.util.logging.Logger;

import java.io.IOException;

import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

/**
 * This is a UDP multicast socket that is used to communicate with
 * the switch
 *
 * @version $Id: CommSocket.java 10855 2007-05-19 02:54:08Z bberndt $
 */
class CommSocket {

    private static final Logger logger =
        Logger.getLogger(CommSocket.class.getName());

    private static final String ZTMD_MCAST_ADDR = "239.0.0.1";
    private static final int ZTMD_MCAST_PORT = 2345;

    private String myAddr;

    private MulticastSocket s;
    private InetAddress group;

    public CommSocket(String addr) {
	myAddr = addr;

	try {
	    group = InetAddress.getByName(ZTMD_MCAST_ADDR);
	    s = new MulticastSocket(ZTMD_MCAST_PORT);
	    s.joinGroup(group);
	}
	catch (UnknownHostException e) {
	    logger.severe("Lookup of IP address failed");
	}
	catch (Exception e) {
	    logger.severe("Couldn't create multicast socket");
	}
    }

    /**
     * Send the ZNYX message on the multicast socket
     */
    public void SendMessage(ZNetlink2Message msg) {

	DatagramPacket p = new DatagramPacket(msg.getBytes(), msg.getSize(),
					      group, ZTMD_MCAST_PORT);
	try {
	    s.send(p);
	}
	catch (IOException e) {
	    logger.severe("Couldn't send mulitcast packet to switch");
	}
    }


    /**
     * Receive packet -- currently not used by anyone.
     */
    public ZNetlink2Message Receive() {
	byte[] buf = new byte[1000];
	DatagramPacket recv = new DatagramPacket(buf, buf.length);
	try {
	    s.receive(recv);
	}
	catch (IOException e) {
	    logger.severe("Couldn't recv mulitcast packet from switch");
	}
	return new ZNetlink2Message(buf);
    }

    protected void finalize() throws Throwable {
	// Leave the group
	s.leaveGroup(group);
    }
}

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



package sniffer;

import java.nio.channels.ServerSocketChannel;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.util.ArrayList;
import java.nio.channels.SocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

import com.sun.honeycomb.cm.cluster_membership.CMMException;

public class Proxy 
    implements Runnable {

    public static final int NORMAL_TYPE = 0;
    public static final int TEST_TYPE = 1;
    
    private int portNbr;
    private InetSocketAddress nextCMMAddr;
    private ServerSocketChannel server;
    private ArrayList connections;
    private int tunnelType = NORMAL_TYPE;
    
    Proxy(int nPortNbr,
    		  InetSocketAddress nNextCMMAddr, int tunnelType) {
    		portNbr = nPortNbr;
    		nextCMMAddr = nNextCMMAddr;
    		server = null;
    		connections = new ArrayList();
    		this.tunnelType = tunnelType;
    }
    
    Proxy(int nPortNbr,
	  InetSocketAddress nNextCMMAddr) {
	portNbr = nPortNbr;
	nextCMMAddr = nNextCMMAddr;
	server = null;
	connections = new ArrayList();
    }

    public void run() {
	System.out.println("Binding port ["+
			   portNbr+"] tunneling to: " + nextCMMAddr.getHostName() + ":" + nextCMMAddr.getPort());

	try {
	    server = ServerSocketChannel.open();
	    server.socket().bind(new InetSocketAddress(portNbr));
            server.configureBlocking(false);

	    while (true) {
		try {
		    eventLoop();
		} catch (IOException e) {
		    System.err.println("Got an exception in the event loop ["+
				       e.getMessage()+"]");
		    e.printStackTrace();
		}
	    }
	} catch (IOException e) {
	    System.err.println("Failed to start the proxy ["+
			       e.getMessage()+"]");
	    e.printStackTrace();
	}
    }

    private void eventLoop() 
	throws IOException {

	Selector selector = Selector.open();
	
	try {
	    server.register(selector, SelectionKey.OP_ACCEPT);

	    for (int i=0; i<connections.size(); i++) {
		Tunnel tunnel = (Tunnel)connections.get(i);
		tunnel.register(selector);
	    }

	    while (true) {
		selector.select();
		Iterator keys = selector.selectedKeys().iterator();
		while (keys.hasNext()) {
		    SelectionKey key = (SelectionKey)keys.next();
		    if (key.isValid()) {
			if (key.isAcceptable()) {
                            try {
                            	Tunnel tunnel = null;
                            	
                            	if (tunnelType == TEST_TYPE)
                            		tunnel = new TestTunnel(server, nextCMMAddr);
                            	else
                                	tunnel = new Tunnel(server, nextCMMAddr);
                            	
                                connections.add(tunnel);
                                System.out.println(connections.size()+" active connections");
                                tunnel.register(selector);
                            } catch (ClosedChannelException e) {
                            }
			}
			if (key.isReadable()) {
			    Tunnel tunnel = (Tunnel)key.attachment();

			    try {
				tunnel.dispatch();
			    } catch (ClosedChannelException e) {
				connections.remove(tunnel);
                                System.out.println(connections.size()+" active connections");
			    }
			}
		    }
		}
		selector.selectedKeys().clear();
	    }
	} finally {
	    selector.close();
	}
    }
}

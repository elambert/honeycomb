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

import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import com.sun.honeycomb.cm.cluster_membership.CMMException;

import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.messages.FrameType;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Election;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Discovery;

public class Tunnel {

    private static final int LEFT_CHANNEL = 1;
    private static final int RIGHT_CHANNEL = 2;

    static protected final int[] packetFormat = { 4, 8, 14, 25 };

    protected SocketChannel left;
    protected SocketChannel right;
    private Selector selector;

    public Tunnel(ServerSocketChannel server,
		  SocketAddress nextCMMAddr) 
	throws IOException {
	    
	boolean connected = false;

	try {
	    right = SocketChannel.open(nextCMMAddr);
	    connected = true;
	} finally {
	    left = server.accept();
	    if (!connected) {
		System.out.println("Next CMM in the ring is not available. Reject connection");
		left.close();
		left = null;
		right = null;
                throw new ClosedChannelException();
	    }
	}

        int linger = CMM.HEARTBEAT_TIMEOUT / 1000; // in seconds
        left.socket().setSoLinger(true, linger);

        left.configureBlocking(false);
        right.configureBlocking(false);

	selector = Selector.open();
	left.register(selector, SelectionKey.OP_READ, new Integer(LEFT_CHANNEL));
	right.register(selector, SelectionKey.OP_READ, new Integer(RIGHT_CHANNEL));
    }

    public void close()
	throws IOException {
	if (left != null) {
	    left.close();
	    left = null;
	}
	if (right != null) {
	    right.close();
	    right = null;
	}
    }

    private void checkConnection()
	throws IOException {
	if ( (left==null) || (!left.isConnected())
	     || (right==null) || (!right.isConnected()) ) {
	    close();
	    throw new ClosedChannelException();
	}
    }

    public void register(Selector sel) 
	throws IOException {
	checkConnection();
	left.register(sel, SelectionKey.OP_READ, this);
	right.register(sel, SelectionKey.OP_READ, this);
    }

    public void dispatch()
	throws IOException {
	
	checkConnection();
	
	int nbKeys = 0;
	nbKeys = selector.selectNow();
	if (nbKeys == 0) {
	    System.out.println("WARNING ! No I/O operation was ready");
	    return;
	}

	Iterator ite = selector.selectedKeys().iterator();
	try {
	    while (ite.hasNext()) {
		SelectionKey key = (SelectionKey)ite.next();
		if ((key.isValid()) && (key.isReadable())) {
		    int direction = ((Integer)key.attachment()).intValue();
		    switch (direction) {
		    case LEFT_CHANNEL:
			dispatch(true);
			break;
			
		    case RIGHT_CHANNEL:
			dispatch(false);
			break;

		    default:
			System.out.println("WARNING ! Bad selection key ["+
					   direction+"]");
		    }
		}
	    }
	} finally {
	    selector.selectedKeys().clear();
	}
    }

    protected void dispatch(boolean fromLeft) 
	throws IOException {
	SocketChannel input = null;
	SocketChannel output = null;
	PacketSBuffer packet = new PacketSBuffer(packetFormat);

	if (fromLeft) {
	    input = left;
	    output = right;
	    packet.append("->");
	} else {
	    input = right;
	    output = left;
	    packet.append("<-");
	}

	// Get the packet

        try {
            Message msg = Message.receive(input);
            msg.send(output);

            // Skip heartbeat frames
            if (msg.getType().getValue() == FrameType.HEARTBEAT.getValue()) {
                return;
            }

            packet.append(msg.getSource());
            
            int dest = msg.getDestination();
            if (dest == -2) {
                packet.append("PEER");
            } else if (dest == -1) {
                packet.append("BROAD");
            } else {
                packet.append(dest);
            }

            packet.append(msg.getType().getLabel());

            if (msg.getType().getValue() == FrameType.ELECTION.getValue()) {
                packet.append(((Election)msg).toString());
            } else if (msg.getType().getValue() == FrameType.DISCOVERY.getValue()) {
                packet.append(((Discovery)msg).toString());
            }
            
            System.out.println(packet);
        } catch (CMMException e) {
            close();
            throw new ClosedChannelException();
        }
    }
}

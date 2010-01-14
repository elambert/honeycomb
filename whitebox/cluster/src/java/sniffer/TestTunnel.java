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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

import com.sun.honeycomb.cm.cluster_membership.CMMException;
import com.sun.honeycomb.cm.cluster_membership.messages.FrameType;
import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Discovery;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Election;

public class TestTunnel extends Tunnel {
	
	private static final Logger LOG = Logger.getLogger(TestTunnel.class.getName());
	
	public TestTunnel(ServerSocketChannel server, SocketAddress nextCMMAddr)
			throws IOException {
		super(server, nextCMMAddr);
	}
	
	private String parseAndReturnNodeID(SocketAddress socketaddress){
		try {
			String address = new String("" + socketaddress);
			address.replaceAll(":.*","");
			address = address.substring(address.lastIndexOf(".")+1,address.lastIndexOf(':'));
			return address;
		} catch (Throwable t){
			return "UNKNOWN";
		}
	}
			
	protected void dispatch(boolean fromLeft) throws IOException {
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
			
			packet.append(msg.getSource());
			int dest = msg.getDestination();
	        if (dest == -2) {
	            packet.append("PEER");
	        } else if (dest == -1) {
	            packet.append("BROAD");
	        } else {
	            packet.append(dest);
	        }
	        
	        
	        String fromNode = "SRC" + parseAndReturnNodeID(input.socket().getRemoteSocketAddress());
	        String toNode = "DST" + parseAndReturnNodeID(output.socket().getRemoteSocketAddress());
	        
	        packet.append(msg.getType().getLabel());
	        
	        if (msg.getType().getValue() == FrameType.ELECTION.getValue()) {
                packet.append(((Election)msg).toString());
            } else if (msg.getType().getValue() == FrameType.DISCOVERY.getValue()) {
                packet.append(((Discovery)msg).toString());
            }
			
			Rule current_rule = BehaviourMonitor.getInstance().getNextRule();
			
			if (current_rule.action.equals(BehaviourMonitor.NOTHING_ACTION)) {
				msg.send(output);
				return;
			}
			
			if (current_rule.packet_type.equalsIgnoreCase("ALL") ||
					msg.getType().getLabel().matches(current_rule.packet_type)) {
				
				if (current_rule.action.equals(BehaviourMonitor.DROP_ACTION)) {
					// Drop Packet
					// Match current fromNode or toNode with the arguments to the Drop action
					// arguments can be a regular expression such as 105|106 which would apply this drop 
					// action to the matching packets from/to 105 or 106.
					if (fromNode.matches(current_rule.args) || toNode.matches(current_rule.args)) {
						LOG.info("Dropping message: " + packet + " from: " + fromNode + " to: " + toNode);
					}
					else
						msg.send(output);
				} else if (current_rule.action.equals(BehaviourMonitor.DELAY_ACTION)) {
					// Delay Packet
					LOG.info("DELAY_ACTION not really delay: " + packet);
					msg.send(output);
				} else {
					// Something wrong validation should of picked this up!
					LOG.severe("Unknown action type:" + current_rule.action);
				}	
				
			} else {
				msg.send(output);
			}	
			
		} catch (CMMException e) {
			close();
			throw new ClosedChannelException();
		}
	}
}

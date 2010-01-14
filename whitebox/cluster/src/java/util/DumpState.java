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



package util;

import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMApi.Node;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeChange;
import java.util.Iterator;
import java.io.IOException;

public class DumpState {

    private static void usage(String msg) {
	if (msg != null) {
	    System.out.println("ERROR: "+msg);
	}
	System.out.println("");
	
	System.out.println("Usage : dump_state.sh <nodeid> [-]\n"+
			   "\tnodeid is the node to query\n"+
			   "\t- specifies that the process will keep running and print the received notifications\n");
	System.exit(1);
    }

    public static void main(String args[]) {
	if ((args.length < 1) || (args.length > 2)) {
	    usage("Bad number of arguments ["+
		  args.length+"]");
	}

	boolean listenToNotifs = false;
	if (args.length == 2) {
	    if (!args[1].equals("-")) {
		usage("Bad parameter ["+args[1]+"]");
	    }
	    listenToNotifs = true;
	}

	int nodeid = -1;
	try {
	    nodeid = Integer.parseInt(args[0]);
	} catch (NumberFormatException e) {
	    usage(e.getMessage());
	}

	CMMApi api = CMM.getAPI("localhost", CMM.API_PORT+2*(nodeid-101));

	try {

	    System.out.println("Connected to node ["+
			       api.nodeId()+"]");

	    Node[] nodes = api.getNodes();
	    for (int i = 0; i < nodes.length; i++) {
		System.out.println(printNode(nodes[i]).toString());
	    }

	    if (listenToNotifs) {
		System.out.println("\nListening to CMM notifications. Press Ctrl-C to exit\n");
		
		notificationLoop(api);
	    }

	} catch (CMMException e) {
	    System.out.println("Fatal error: "+
			       e.getMessage());
	    System.exit(1);
	} catch (IOException e) {
	    System.out.println("Fatal error: "+
			       e.getMessage());
	    System.exit(1);
	}
    }

    private static void pad(StringBuffer sb,
			    int offset) {
	int toBeAdded = offset-sb.length();
	for (int i=0; i<toBeAdded; i++) {
	    sb.append(" ");
	}
    }

    private static StringBuffer printNode(Node node) {
        StringBuffer sb = new StringBuffer("node " + node.nodeId());
        sb.append(" (").append (node.getAddress()).append (") [").append (node.getActiveDiskCount())
            .append(" disks");

	pad(sb, 30);
        if (node.isAlive()) {
            sb.append(" up "); 
        } else {
            sb.append(" down ");
        }

	pad(sb, 40);
        if (node.isMaster()) {
            sb.append(" master ");
        }
        if (node.isViceMaster()) {
            sb.append(" vicemaster ");
        }

	pad(sb, 55);
        if (node.isEligible()) {
            sb.append(" eligible ");
        }
        sb.append("] ");

        return sb;
    }

    private static void notificationLoop(CMMApi api) 
	throws CMMException, IOException {
	
	// Register to notifications
	SocketChannel sc = api.register();

	// Wait for notifs
	Selector select = Selector.open();
	sc.configureBlocking(false);
	sc.register(select, SelectionKey.OP_READ);

	while (true) {
	    select.select();
	    Iterator keys = select.selectedKeys().iterator();
	    SelectionKey key;
	    while (keys.hasNext()) {
		key = (SelectionKey)keys.next();
		if (key.isReadable()) {
		    StringBuffer sb = new StringBuffer();
		    Message msg = api.getNotification((SocketChannel)key.channel());
                    if (msg instanceof NodeChange) {
                        NodeChange change = (NodeChange) msg;
                        change.exportString(sb);
                        System.out.println(sb.toString());
                    }
		}
	    }
	    select.selectedKeys().clear();
	}
    }
}

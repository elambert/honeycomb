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

import com.sun.honeycomb.cm.cluster_membership.CMM;
import java.net.InetSocketAddress;
import com.sun.honeycomb.cm.cluster_membership.NodeTable;

public class Sniffer {

    private static void usage(String msg) {
	System.out.println("Error: "+msg+"\n\n"+
			   "Usage : cmm_sniffer.sh <nodeid> [-distmode]\n"+
			   "where :\n"+
			   "\t<nodeid> is the node to hijack (one specified in cmm.test.sniffed\n" + 
			   "\t-distmode indicates that the sniffer is being run on a distributed mode (not single machine mode)\n");
	
	System.exit(1);
    }

    public static void main(String[] args) {
	if (args.length > 3 || args.length < 1) {
	    usage("Bad number of arguments ["+args.length+"]");
	}

	int nodeid = -1;

	try {
	    nodeid = Integer.parseInt(args[0]);
	} catch (NumberFormatException e) {
	    usage("Bad argument format ["+args[0]+"] -["+
		  e.getMessage()+"]");
	}

        NodeTable.init(nodeid, null);

	int portNbr = CMM.RING_PORT+2*(nodeid-101);
	
	if (args.length > 1) {
		if (args[1].equalsIgnoreCase("-distmode"))
			new Proxy(portNbr, new InetSocketAddress("10.123.45." + nodeid, portNbr+CMM.SNIFF_OFFSET),Proxy.TEST_TYPE).run();
		else
			usage("Bad arguments!");
	}
	else
		new Proxy(portNbr, new InetSocketAddress("127.0.0.1", portNbr+CMM.SNIFF_OFFSET)).run();
			
	

	System.out.println("The sniffer is exiting");
    }
}

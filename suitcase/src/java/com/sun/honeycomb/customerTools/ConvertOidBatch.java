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



/*
 * Utility to translate a batch of external 1.0 OIDs to external 1.1 
 * OIDs.  By default, cellId is set to 1.
 *
 * Reads a list of OIDs in via standard in, and outputs a mapping of
 * the original OIDs to the converted OIDs via standard out.
 *
 * WARNING: To be able to determine the original cell id (for external
 * conversion), one has to consult silo_info.xml file from the
 * server. This is TBD. Currently, cell id is set to 1 unless a
 * different value is passed on the command line.
 *
 * You can invoke it like this (on a client machine):
 * > cd /opt/test/bin && cat oids | java -classpath
 *    ../lib/honeycomb-common.jar:../lib/honeycomb-server.jar:../lib/jug.jar
 *     -Djava.library.path=../lib -Dhoneycomb.config.dir=../etc
 *     com.sun.honeycomb.hctest.util.ConvertOidBatch [cell_id]
 *
 */

package com.sun.honeycomb.suitcase;

import com.sun.honeycomb.suitcase.ConvertOid.ConverterNewObjectIdentifier;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.logging.Logger;
import java.util.logging.Level;

public class ConvertOidBatch {

    /**********************************************************************/
    private static void printUsage() {
        String msg = "Usage: cat <oidfile> | ConvertOidBatch [cell_id] [options]\n" + 
	    "ConvertOidBatch reads 1.0 OIDs from stdin (one OID per line of " +
	    "input) and outputs a mapping of 1.0 OIDs to 1.1 OIDs to stdout.\n" +
	    "Default cell_id is 1\n" +
	    "Possible options are\n" +
	    "   map : output a mapping of 1.0->1.1 OIDs (default)\n" +
	    "   list : output a list of 1.1 OIDs only\n" +
	    "   help : print this usage message\n";
	
        System.out.println(msg);
    }

    /**********************************************************************/
    public static void main(String args[]) {
        if (args == null || args.length > 2) {
            printUsage();
            System.exit(1);
        }
	// Prevents stderr and other logging junk from being outputted
        Logger.getLogger("").setLevel(Level.SEVERE);

	try {
	    BufferedReader in = 
		new BufferedReader(new InputStreamReader(System.in));
	    
	    String line = null;
	    ConverterNewObjectIdentifier oid = 
		new ConverterNewObjectIdentifier();
	    boolean map = true;

	    if (args.length > 0) {
		if (args[0].equalsIgnoreCase("help") || 
		    args[0].equals("-h") ||
		    args[0].equals("--help")) {
		    printUsage();
		    System.exit(1);
		}

		for (int i = 0; i < args.length; i++) {
		    if (args[i].equalsIgnoreCase("list")) {
			map = false;
		    } else if (args[i].equalsIgnoreCase("map")) {
			map = true;
		    } else {
			byte cellId = 1;
			try {
			    cellId = Byte.parseByte(args[i]);
			} catch (NumberFormatException e) {
			    System.err.println
				("Cell ID argument invalid: " + args[i] +
				 " -- " + e.toString());
			    printUsage();
			    System.exit(-1);
			}
			oid.setCellId(cellId);
		    }
		}
	    }
	    
	    while ((line = in.readLine()) != null) {
		oid.convert(line);
		if (map) {
		    System.out.println(line + " " + oid.toExternalHexString());
		} else {
		    System.out.println(oid.toExternalHexString());
		}
	    }

	    in.close();
	} catch (Throwable t) {
	    System.err.println("An unexpected error occurred.");
	    t.printStackTrace();
	}

    }

}

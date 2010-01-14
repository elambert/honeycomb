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
 * Utility to translate between hex (external) and ascii (internal)
 * OID formats as well as to convert from 1.0 version of oid - either
 * as hex or ascii - to 1.1 format. By default, cellId for external
 * format is set to 1. You can do the following:
 * 1. External 1.1 oid -> internal 1.1, internal/external 1.0.1
 * 2. Internal 1.1 [cell_id] -> external 1.1, internal/external 1.0.1
 * 3. external 1.0.1 [cell_id] -> internal 1.0.1, internal/external 1.1
 * 4. internal 1.0.1 [cell_id] -> external 1.0.1, internal/external 1.1
 *
 * For internal 1.1 format, assume no sloshing (rule id is set to
 * 1). For (1) and (2), 1.0.1 oid's will have cell id 1, even if 1.1
 * oid has a different cell id. This is so that 1.0.1 clients always
 * see consistent values for oid.
 *
 * WARNING: To be able to determine the original cell id (for external
 * conversion), one has to consult silo_info.xml file from the
 * server. This is TBD. Currently, cell id is set to 1 unless a
 * different value is passed on the command line.
 *
 * You can invoke it like this (on a client machine):
 * > cd /opt/test/bin && java -classpath
 *    ../lib/honeycomb-common.jar:../lib/honeycomb-server.jar:../lib/jug.jar
 *     -Djava.library.path=../lib -Dhoneycomb.config.dir=../etc
 *     com.sun.honeycomb.hctest.util.ConvertOid <oid> [cell_id]
 *
 * Or use script /opt/test/bin/ConvertOid on the client machines.
 */

package com.sun.honeycomb.suitcase;

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ByteArrays;
import java.net.URLClassLoader;
import java.net.URL;

import java.util.logging.Logger;
import java.util.logging.Level;

public class ConvertOid {

    /**********************************************************************/
    private static void printUsage() {
        String msg = "Usage: ConvertOid <oid> [cell_id]\n" +
	    "NOTE: This is an internal tool.  Use ConvertOidBatch instead.\n" +
	    "ConvertOid takes a single OID in any format and outputs " +
	    "it in all formats: 1.0 and 1.1, external and internal.\n" +
	    "Default cell_id is 1\n";
        System.out.println(msg);
    }

    /**********************************************************************/
    public static void main(String args[]) {
        if (args == null || args.length < 1 || args.length > 3) {
            printUsage();
            System.exit(1);
        }
        Logger.getLogger("").setLevel(Level.SEVERE);

	if (args[0].equalsIgnoreCase("help") ||
	    args[0].equals("-h") ||
	    args[0].equals("--help")) {
	    printUsage();
	    System.exit(1);
	}
	
	ConverterNewObjectIdentifier oid = new ConverterNewObjectIdentifier();
	try {
	    System.out.println("  Converting OID " + args[0]);
	    oid.convert(args[0]);
	}
	catch (Exception e) {
	    System.err.println("OID argument invalid: " + args[0] +
			       " -- " + e.toString());
	    printUsage();
	    System.exit(-1);
	}

        if (args.length == 2) {
            byte cellId = 1;
            try {
                cellId = Byte.parseByte(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Cell ID argument invalid: " + args[1] +
                                   " -- " + e.toString());
                printUsage();
                System.exit(-1);
            }
            oid.setCellId(cellId);
        }
	else {
	    System.out.println("  Attention: Using cell ID 1. Run without arguments for usage.");
	}
        System.out.println("    External: " + oid.toExternalHexString());
        System.out.println("    Internal: " + oid.toString());
        System.out.println("    External (v1.0): "
                           + oid.toLegacyExternalHexString());
        System.out.println("    Internal (v1.0): "
                           + oid.toLegacyString());
    }

    /**********************************************************************/
    public static class ConverterNewObjectIdentifier
        extends NewObjectIdentifier {
        private byte cellId = 1;
        public void convert(String oid) {
            if (oid.matches("(\\S{36})(\\.(\\d+)){7}")) {
                // 1.1 string
                initFromString(oid);
            } else if (oid.matches("(\\S{36})(\\.(\\d+)){5}")) {
                // 1.0 string
                legacyAdaptor.initializeFields(oid);
            } else {
                byte[] bytes = ByteArrays.toByteArray(oid);
                if (bytes.length == OID_LENGTH) {
                    // 1.1 hex string
                    initFromHexString(oid);
                    if (representation == REPRESENTATION_INT) {
                        throw new IllegalArgumentException("OID " + oid 
							   + " is not in "
                                                           + "external format");
                    }
                    ruleId = 1; // assume no sloshing
                } else if (bytes.length == legacyAdaptor.LENGTH) {
                    // 1.0 hex string
                    legacyAdaptor.initFromHexString(oid);
                } else {
                    throw new IllegalArgumentException("OID format of " + oid
                                                       + " not understood");
                }
            }
        }
        public String toExternalHexString() {
            representation = REPRESENTATION_EXT;
            ruleId = cellId;
            return super.toHexString();
        }
        public String toString() {
            representation = REPRESENTATION_INT;
            ruleId = 1; // assume no sloshing
            return super.toString();
        }
        public String toLegacyString() {
            representation = REPRESENTATION_INT;
            return uid.toString() + "." + ruleId + ".1."
                + objectType + "." + chunkNumber + "." + layoutMapId;
        }
        public String toLegacyExternalHexString() {
            representation = REPRESENTATION_EXT;
            return super.toLegacyHexString();
        }
        public void setCellId(byte cellId) {
            this.cellId = cellId;
        }
    }
}

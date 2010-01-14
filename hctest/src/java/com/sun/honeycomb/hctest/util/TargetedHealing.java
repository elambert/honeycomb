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



package com.sun.honeycomb.hctest.util;

import com.sun.honeycomb.hctest.cli.DataDoctorState;
import com.sun.honeycomb.hctest.cli.CLIState;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class TargetedHealing {

    DataDoctorState dd = null;

    public TargetedHealing() {
        dd = DataDoctorState.getInstance();
    }

    public static void usage() {
        System.err.println("Usage: TargetedHealing <cluster> <oid> <fragId> <chunkId>");
        System.exit(-1);
    }

    public static void main(String[] args) {
        
        if (args.length != 4) {
            usage();
        }

        String cluster = args[0];
        String oid = args[1];
        int fragId = Integer.parseInt(args[2]);
        int chunkId = Integer.parseInt(args[3]);

        // do this before instantiating TargetedHealing
        TestRunner.setProperty(HoneycombTestConstants.PROPERTY_SP_IP_ADDR, cluster+"-cheat");
        TestRunner.setProperty(HoneycombTestConstants.PROPERTY_ADMIN_VIP_ADDR, cluster+"-admin");

        System.out.println("Forcing healing on cluster " + cluster + 
                           " on frag " + fragId + " chunk " + chunkId + " of OID " + oid);
        
        TargetedHealing doHeal = new TargetedHealing();
        boolean verbose = false;
        boolean verify = false;

        try {
            doHeal.dd.dataDoctorTargetedHeal(oid, fragId, chunkId,
                                         CLIState.LOST_FRAGS, verbose, verify);
        } catch (HoneycombTestException he) {
            Log.ERROR(he.getMessage());
        }
        
        System.out.println("Done");
    }

}

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



package com.sun.honeycomb.oa;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.platform.diskinit.Disk;

public class OAClientDelete {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("usage: java OAClientDelete <root path> <oid>");
            System.exit(1);
        }

        OAClient client = OAClient.getTestInstance();
        ObjectReliability reliability = client.getReliability();

        System.out.println(reliability);

        // Prepare the test layout
        Disk[] testLayout = new Disk[reliability.getTotalFragCount()];
        for (int i=0; i<reliability.getTotalFragCount(); i++) {
            Disk d = new Disk(new String(args[0] + "/" + i));
            testLayout[i] = d;
        }
        client.setTestLayout(testLayout);

        //
        // DELETE PHASE
        //

        NewObjectIdentifier oid = new NewObjectIdentifier(args[1]);
        try {
            client.delete(oid, true, 0);
        } catch (ArchiveException e) {
            System.out.println("FAILED: Error in delete: " + e);
            System.exit(1);
        }
        System.out.println("PASSED: Successfully deleted [" + oid + "]");
    }
}

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



package com.sun.honeycomb.cm;

import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import java.io.*;

public class CMMClient {
    
    private static CMMClient app;

    private static void usage () {
        System.out.println (
            "CMMClient host");
    }

    public static void main (String[] args) {
        if (args == null || args.length == 0 || args.length > 1) {
            usage();
            System.exit (1);
        }

        String host = args[0];

        if (host == null) {
           usage();
           System.exit (2);
        }

        try {
            app = new CMMClient (host);
            app.work();
        } catch (Exception e) {
            System.out.println ("fatal error: " + e.getMessage());
            System.exit (100);
        }
    }

    private CMMApi api = null;
    private volatile boolean stop = false;
    private BufferedReader in = null;
    private String host = null;
    
    private CMMClient (String host) {
        this.host = host;
    }

    private void work () {
        api = CMM.getAPI (host);
        in = new BufferedReader (new InputStreamReader (System.in));
        String line = null;
        while (!stop) {
            try {
                line = in.readLine();
            } catch (IOException ioe ) {
                System.err.println ("Error reading from console: " 
                    + ioe.getMessage());
                continue;
            }

            if (line.equals ("show")) {
                CMMApi.Node[] nodes = null;
               
                try {
                    nodes = api.getNodes();
                } catch (CMMException cme) {
                    System.err.println ("cmm error:" + cme.getMessage());
                    continue;
                }

                for (int i = 0; i < nodes.length; i++) {
                    System.out.print (nodes[i].nodeId());
                    if (nodes[i].isAlive()) {
                        System.out.print (" [" + nodes[i].getActiveDiskCount()+"]");
                        System.out.print (" alive");
                    }
                    if (nodes[i].isMaster())
                        System.out.print (" master");
                    if (nodes[i].isViceMaster())
                        System.out.print (" vicemaster");
                    if (nodes[i].isEligible())
                        System.out.print (" elig");
                    System.out.println();
                        
               }
            }
            else if (line.equals ("quit") || line.equals("exit")) {
                stop = true;
                continue;
            }
            else {
                System.out.println ("unknown token: " + line);
                continue;
            }
        }
    }
}

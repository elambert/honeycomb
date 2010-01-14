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




package com.sun.honeycomb.datadoctor;
import com.sun.honeycomb.layout.LayoutProxy;
import com.sun.honeycomb.layout.LayoutProxy;
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.protocol.server.ProtocolService;
                                                                                                                                       
class DirectoryCheck {
                                                                                                                                       
    static private void usage() {
        
        System.out.println("usage: [operation] diskId mapId1 [ mapId2 mapId3 ... ] ");
        System.out.println("example: 101:3 721 2345 9993");
        System.out.println("\nRemember that the stepNum 1234 corresponds to the dir /12/34");
        System.out.println("and that hcb103:/data/2 is diskId 103:2");
        System.out.println("So to run it on hcb103:/data/2/12/34 use args \"103:2 1234\" ");
        System.out.println("Operation options: frag, scan");
    }
                                                                                                                                       
    static public void main(String[] args) {



        if (args.length < 1) {
            usage();
            return;
        }

        int a = 1;

        if(args[0].equals("frag")) {           
            DiskMask diskMask = LayoutProxy.getCurrentDiskMask();
            DiskId diskId = new DiskId( args[a++] );
            RecoverLostFrags recover = new RecoverLostFrags();
            recover.init("ManualRecover", diskId);
            recover.newDiskMask(diskMask);                                                                                                                                       
            while (a < args.length) {                                                                                                                                       
                int mapId = Integer.parseInt(args[a++]);
                System.out.println("manually stepping disk "+diskId+
                                   " step "+mapId);
                recover.step(mapId);
            }
        } else if (args[0].equals("scan")){

            DiskMask diskMask = LayoutProxy.getCurrentDiskMask();
            DiskId diskId = new DiskId( args[a++] );
            ScanFrags scan = new ScanFrags();
            scan.init("ManualScan", diskId);
            scan.newDiskMask(diskMask);                                                                                                                                       
            while (a < args.length) {                                                                                                                                       
                int mapId = Integer.parseInt(args[a++]);
                System.out.println("manually stepping disk "+diskId+
                                   " step "+mapId);
                scan.step(mapId);
            }
        } else {
            System.err.println("Don't know this arg:" +args[0]);
        }

        System.exit(0);
    }
}



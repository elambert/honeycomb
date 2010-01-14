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


 
package com.sun.honeycomb.hctest.cli;

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
 
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

public class Cellcfg {

    public class Cell {
        public int cellid;
        public String adminvip;
        public String datavip;
        public String spIP;
        public String subnet;
        public String gateway;

        Cell(int cellid, String adminvip, String datavip, String spIP,
             String subnet, String gateway) {
            this.cellid = cellid;
            this.adminvip = adminvip;
            this.datavip = datavip;
            this.spIP = spIP;
            this.subnet = subnet;
            this.gateway = gateway;
        }
        public String toString() {
            return "cellid " + cellid + 
                   " admin " + adminvip + 
                   " data " + datavip +
                   " sp " + spIP +
                   " subnet " + subnet +
                   " gateway " + gateway;
        }
    }

    private String adminVIP = null;

    public ArrayList cells = new ArrayList();

    private BufferedReader runCommand(String command)
        throws Throwable {
        Log.INFO("CLI::runCommand(" + command + ")");
        String[] args = new String[] {
            "/usr/bin/ssh",
            "-o",
            "StrictHostKeyChecking=no",
            "admin@" + adminVIP,
            command
        };

        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec(args);
        InputStream stdout = process.getInputStream();
        return new BufferedReader(new InputStreamReader(stdout));
    }

    public Cellcfg(String adminvip) {
        adminVIP = adminvip;
    }

    public void getAllCells() throws Throwable {
        Hiveadm hive = new Hiveadm(adminVIP);
        for (int i=0; i<hive.cells.size(); i++) {
            Hiveadm.Cell c = (Hiveadm.Cell) hive.cells.get(i);

            BufferedReader reader = runCommand("hiveadm");
            String outputStr = HCUtil.readLines(reader);
            reader.close();
            String lines[] = outputStr.split("\n");

            //
            // > ssh admin@dev303-admin cellcfg -c 3
            // Admin IP Address = 10.7.224.61  [have this from hiveadm]
            // Data IP Address = 10.7.224.62  [have this from hiveadm]
            // Service Node IP Address = 10.7.224.60
            // Subnet = 255.255.252.0
            // Gateway = 10.7.227.254
            //

            String fields[] = lines[2].split(" ");
            String sp = fields[5];
            fields = lines[3].split(" ");
            String sub = fields[2];
            fields = lines[4].split(" ");
            String gate = fields[2];

            cells.add(new Cell(c.cellid, c.adminvip, c.datavip, sp, 
                               sub, gate));
        }
    }

    public Cell getCell(int cellid) throws Throwable {
        if (cells.size() > 0) {
            for (int i=0; i<cells.size(); i++) {
                Cell c = (Cell) cells.get(i);
                if (c.cellid == cellid)
                    return c;
            }
        }

        BufferedReader reader = runCommand("cellcfg -c " + cellid);
        String outputStr = HCUtil.readLines(reader);
        reader.close();
        String lines[] = outputStr.split("\n");

        //
        // > ssh admin@dev303-admin cellcfg -c 3
        // Admin IP Address = 10.7.224.61  
        // Data IP Address = 10.7.224.62  
        // Service Node IP Address = 10.7.224.60
        // Subnet = 255.255.252.0
        // Gateway = 10.7.227.254
        //
        if (lines.length != 5) {
            throw new HoneycombTestException("got " + lines.length + 
                                             " lines, expected 5");
        }

        String fields[] = lines[0].split(" ");
        String admin = fields[4];
        fields = lines[1].split(" ");
        String data = fields[4];
        fields = lines[2].split(" ");
        String sp = fields[5];
        fields = lines[3].split(" ");
        String sub = fields[2];
        fields = lines[4].split(" ");
        String gate = fields[2];

        return new Cell(cellid, admin, data, sp, sub, gate);
    }

    /**
     *  List id's of cells.
     */
    public String listIDs() {
        StringBuffer sb = new StringBuffer();
        for (int i=0; i<cells.size(); i++) {
            if (i > 0)
                sb.append(", ");
            Cell c = (Cell) cells.get(i);
            sb.append(c.cellid);
        }
        return sb.toString();
    }   

    // java -cp "../build/hctest/dist/lib/honeycomb-hctest.jar:../build/hctest/dist/lib/honeycomb-test.jar" com.sun.honeycomb.hctest.cli.Cellcfg dev303-admin
    public static void main(String args[]) throws Throwable {
        if (args.length != 1) {
            System.err.println("Usage: ... adminvip");
            System.exit(1);
        }
        Cellcfg c = new Cellcfg(args[0]);
        System.out.println("== Cell ids: " + c.listIDs());
        for (int i=0; i<c.cells.size(); i++)
            System.out.println("\t" + c.cells.get(i));
    }   
}

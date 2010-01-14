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
import java.util.ArrayList;

public class Hiveadm {

    public class Cell {
        public int cellid;
        public String adminvip;
        public String datavip;

        Cell(int cellid, String adminvip, String datavip) {
            this.cellid = cellid;
            this.adminvip = adminvip;
            this.datavip = datavip;
        }
        public String toString() {
            return "cellid " + cellid + 
                   " admin " + adminvip + 
                   " data " + datavip;
        }
    }

    public ArrayList cells = new ArrayList();

    public BufferedReader runCommand(String adminIp, String command)
        throws Throwable {
        Log.INFO("CLI::runCommand(" + command + ")");
        String [] args = new String [] {
            "/usr/bin/ssh",
            "-o",
            "StrictHostKeyChecking=no",
            "admin@" + adminIp,
            command
        };

        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec(args);
        InputStream stdout = process.getInputStream();
        return new BufferedReader(new InputStreamReader(stdout));
    }

    public Hiveadm(String adminvip) throws Throwable {

        BufferedReader reader = runCommand(adminvip, "hiveadm");
        String outputStr = HCUtil.readLines(reader);
        reader.close();
        String lines[] = outputStr.split("\n");

        //
        // > ssh admin@dev303-admin hiveadm
        // There is/are 4 cell(s) in the hive:
        // - Cell 3: adminVIP = 10.7.224.61, dataVIP = 10.7.224.62
        // - Cell 12: adminVIP = 10.7.225.21, dataVIP = 10.7.225.22
        //
        for (int i=1; i<lines.length; i++) {

            String fields[] = lines[i].split(" ");

            String s = fields[2].substring(0, fields[2].indexOf(':'));
            int cellid = Integer.parseInt(s);
            String admin = fields[5].substring(0, fields[5].indexOf(','));
            String data = fields[8];

            cells.add(new Cell(cellid, admin, data));
        }
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

    // java -cp "../build/hctest/dist/lib/honeycomb-hctest.jar:../build/hctest/dist/lib/honeycomb-test.jar" com.sun.honeycomb.hctest.cli.Hiveadm dev303-admin
    public static void main(String args[]) throws Throwable {
        if (args.length != 1) {
            System.err.println("Usage: ... adminvip");
            System.exit(1);
        }
        Hiveadm h = new Hiveadm(args[0]);
        System.out.println("== Cell ids: " + h.listIDs());
        for (int i=0; i<h.cells.size(); i++)
            System.out.println("\t" + h.cells.get(i));
    }   
}

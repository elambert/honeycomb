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

import java.util.LinkedList;
import java.util.Iterator;
import java.net.Socket;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.EOFException;

import com.sun.honeycomb.test.util.*;

/**
    Get current alerts from adminVIP
*/

public class AlertMonitor {

    private static final int ALERT_PORT = 2807;

    private String vip;

    public AlertMonitor(String adminVIP) {
        vip = adminVIP;
    }

    /**
     *  Get all alerts as a LinkedList of NameValue pairs.
     */
    public LinkedList getAlerts() throws HoneycombTestException {
        return getAlerts(null);
    }

    /**
     *  Get alerts with names containing a given pattern,
     *  as a LinkedList of NameValue pairs.
     */
    public LinkedList getAlerts(String pattern) throws HoneycombTestException {

        LinkedList result = new LinkedList();

        Socket client = null;
        DataInputStream input = null;

        try {
            try {
                client = new Socket(vip, ALERT_PORT);
                input = new DataInputStream(client.getInputStream());
            } catch (Throwable t) {
                throw new HoneycombTestException(vip + ":" + ALERT_PORT +
                                                 " - " + t, t);
            }
            while (true) {
                try {
                    String name = input.readUTF();
                    String value = input.readUTF();
                    if (pattern != null  &&  name.indexOf(pattern) == -1) {
                        continue;
                    }
                    result.add(new NameValue(name, value));
                } catch (EOFException e) {
                    break;
                } catch (Throwable t) {
                    throw new HoneycombTestException("reading", t);
                }
            }
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {}
            }
            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {}
            }
        }

        return result;
    }

    /** main is for test */
    private static void usage() {
        System.err.println("usage: ... <adminVIP> [filter-string]");
        System.exit(1);
    }
    public static void main(String args[]) {
        if (args.length == 0  ||  args[0].equals("-h"))
            usage();
        if (args.length > 2)
            usage();
        String filter = null;
        if (args.length == 2)
            filter = args[1];
        try {
            AlertMonitor am = new AlertMonitor(args[0]);
            LinkedList ll = am.getAlerts(filter);
            Iterator it = ll.iterator();
            while (it.hasNext()) {
                NameValue nv = (NameValue) it.next();
                System.out.println(nv.name + " " + nv.value);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }
}

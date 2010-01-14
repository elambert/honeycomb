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

import com.sun.honeycomb.client.*;

import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.test.*;

import java.util.*;
import java.io.*;
import java.nio.channels.*;

public class PortTest {
    public static void usage() {
        System.out.println("PortTest datavip num_oa_connections [tag]");
        System.exit(1);
    }

    public static void main(String args[]) throws Throwable {

        if (args.length <= 2) {
            usage();
        }

        String datavip = args[0];

        int howmany = Integer.parseInt(args[1]); // how many oa objects

        String tag = "";
        if (args.length == 3) {
            tag = args[2];
        }

        long sleeptime = 5*60*1000; // msecs

        NameValueObjectArchive nvoa[] = new NameValueObjectArchive[howmany];
        
        for (int i = 0; i < howmany; i++) {
            System.out.println(tag + " creating entry [" + i + "] for vip " + datavip);
            try {
                nvoa[i] = new NameValueObjectArchive(datavip);
                System.out.println(tag + " creating entry [" + i + "] for vip " + datavip + " SUCCEEDED");
            } catch (Throwable t) {
                System.out.println(tag + " creating entry [" + i + "] for vip " + datavip + " FAILED");
                System.out.println(tag + " failure due to " + t);
                nvoa[i] = null;
            } 
        }

        ReadableByteChannel data = null;
        for (int i = 0; i < howmany; i++) {
            if (nvoa[i] == null) {
                System.out.println(tag + " skipping store...nvoa is null");
                continue;
            }

            System.out.println(tag + " doing a store using entry [" + i + "] for vip " + datavip);
            SystemRecord sr = null;
            try {
                sr = nvoa[i].storeObject(data);
            } catch (Throwable t) {
                sr = null;
                System.out.println(tag + " store [" + i + "] for vip " + datavip + " FAILED");
                System.out.println(tag + " failure due to " + t);
            }
            if (sr != null) {
                System.out.println(tag + " store [" + i + "] for vip " + datavip + " returned " + sr.getObjectIdentifier() + " and SUCCEEDED");
            }
        }

        System.out.println(tag + " sleeping " +  sleeptime + "...use netstat -p to check ports");
        Thread.sleep(sleeptime);
    }
}

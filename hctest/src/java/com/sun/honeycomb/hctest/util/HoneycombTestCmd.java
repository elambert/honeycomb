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

import com.sun.honeycomb.hctest.CmdResult;
import com.sun.honeycomb.test.util.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.logging.Logger;

import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.NameValueSchema;
import com.sun.honeycomb.client.QueryResultSet;
import com.sun.honeycomb.client.ObjectIdentifier;

/**
	NOTE: This is a somewhat re-purposed class, now intended
	for extension for cmd-line progs.
*/

public class HoneycombTestCmd {
    private static final Logger LOG =
			Logger.getLogger(HoneycombTestCmd.class.getName());

    // Sleep the given number of milliseconds
    public void sleep(long msecs) {
        try {
            if (msecs > 0) {
                LOG.info("Sleeping for " + msecs + " msecs");
            }
            Thread.sleep(msecs);
        } catch (InterruptedException e) {
            LOG.info("Sleep " + msecs + " Interrupted: " + e.getMessage());
        }
    }

    // for status to terminal
    public static int dot_range = 70;
    private int dots = 0;
    public void dot(String s) {
        System.err.print(s);
        dots++;
        if (dots == dot_range) {
            System.err.println();
            dots = 0;
        }
    }
    public void closeDots() {
        if (dots > 0) {
            System.err.println();
            dots = 0;
        }
        System.err.flush();
    }
    public void finishDotRange(String s) {
        while (dots < dot_range) {
            System.err.print(s);
            dots++;
        }
        System.err.println();
        System.err.flush();
    }

}

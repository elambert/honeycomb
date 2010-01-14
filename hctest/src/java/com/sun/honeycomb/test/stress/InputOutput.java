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



package com.sun.honeycomb.test.stress;

import java.io.PrintStream;
import java.util.StringTokenizer;

public class InputOutput
{
    public long t0;
    public long t1;
    public String oid;
    public String uid;
    public long sizeBytes;
    public String op;
    public boolean ok;
    public String exception;

    public InputOutput() {
        this.t0 = 0;
        this.t1 = 0;
        this.oid = null;
        this.uid = null;
        this.sizeBytes = 0;
        this.op = null;
        this.ok = false;
    }

    public String toString()
    {
        return "" + t0 + " " + t1 + " " + oid + " " + sizeBytes + " " + op + " " + (ok ? OK : ERR);
    }

    public static final String STORE = "STR";
    public static final String RETRIEVE = "RTV";
    public static final String QUERY = "QRY";
    public static final String DELETE = "DEL";

    public static final String OK = "OK";
    public static final String ERR = "ERR";
    
    public static void printLine(long t0, long t1, String oid, String uid, 
	    long sizeBytes, String op, boolean ok) {
	printLine(t0,t1,oid,uid,sizeBytes,op,ok,false,null);
    }

    public static void printLine(long t0, long t1, String oid, String uid, 
	    long sizeBytes, String op, boolean ok, boolean toErr, Throwable t)
    {
	StringBuffer excDesc = new StringBuffer();
	if (t != null) {
	    excDesc.append(" " + t.getClass().getCanonicalName());
	    Throwable cause = t.getCause();
	    if (cause != null) {
		excDesc.append("::" + cause.getClass().getCanonicalName());
	    }
	}
       System.out.println(t0 + " " + t1 + " " + oid + " " + uid + " " 
	       + sizeBytes + " " + op + " " + (ok ? OK : ERR) + excDesc);
       if (toErr) {
	   System.err.println(t0 + " " + t1 + " " + oid + " " + uid + " " 
		   + sizeBytes + " " + op + " " + (ok ? OK : ERR) + excDesc);
       }
    } 

    public static InputOutput readLine(String s) throws Throwable
    {
        InputOutput line = new InputOutput();
        StringTokenizer st = new StringTokenizer(s);
        line.t0 = Long.parseLong(st.nextToken());
        line.t1 = Long.parseLong(st.nextToken());
        line.oid = st.nextToken();
        line.uid = st.nextToken();
        line.sizeBytes = Long.parseLong(st.nextToken());
        line.op = st.nextToken();
        line.ok = (st.nextToken().equals("OK"));
        if (st.hasMoreTokens()) {
            line.exception = st.nextToken();
        }
        return line;
    }
}

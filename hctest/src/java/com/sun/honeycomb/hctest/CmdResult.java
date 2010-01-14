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


package com.sun.honeycomb.hctest;

import com.sun.honeycomb.hctest.util.AuditResult;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;

import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.BandwidthStatistic;

import com.sun.honeycomb.client.ResultSet;
import com.sun.honeycomb.client.NameValueSchema;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.client.PreparedStatement;

import java.io.Serializable;
import java.lang.StringBuffer;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 *  Values that might be returned by a given command.
 *
 *  This class is used to return the results of API, CLI,
 *  and whitebox (e.g. failure injection) commands from
 *  lower-level routines to the caller.
 */

public class CmdResult implements Serializable {

    public boolean pass = false;

    public String dataVIP = null;

    public String mdoid = null;
    public String dataoid = null;
    public String mdsha1 = null;
    public String datasha1 = null;
    public String filename = null;
    public long filesize = -1;
    public long fileseed = -1;

    public SystemRecord sr = null;

    public String logTag = null;
    public HashMap mdMap = null;
    public List list = null;
    public String string = null;

    public ResultSet rs = null;
    public PreparedStatement  query = null; // for result count hack
    public String field = null; // for result count hack
    public int maxResults = -1; // for result count hack
    public NameValueSchema nvs = null;

    public long time = -1;
    public long total_time = -1;

    public int retries = 0;
    public int count = -1; // for whatever
    public int node = -1;
    public int error = 0;       // up to caller to use as fit
    
	// used internally to deletion history.
    public boolean deleted = false;
    
    public long query_result_count = 0;

    public ArrayList thrown = null;

	public AuditResult auditResult;
    public void addException(Throwable t) {
        if (thrown == null)
            thrown = new ArrayList();
        thrown.add(t);
    }

    public void logExceptions(String prefix) {
	logExceptions(prefix, false);
    }

    public String getExceptions() {
        if (thrown == null)
            return "none";
        StringBuffer sb = new StringBuffer();
        for (int i=0; i<thrown.size(); i++) {
            Throwable t = (Throwable) thrown.get(i);
            sb.append(t.toString());
        }
        return sb.toString();
    }

    public void logExceptions(String prefix, boolean stackTrace) {
        if (thrown == null)
            return;
        for (int i=0; i<thrown.size(); i++) {
            Throwable t = (Throwable) thrown.get(i);
            Log.ERROR(prefix + ": exception [" + i + "] " + t.toString());
	    if (stackTrace) {
		Log.ERROR(Log.stackTrace(t));
	    }
        }
    }

    /**
     *  Return true if an exception doesn't match expected type.
     */
    public boolean checkExceptions(Class c, String msg) {
        if (thrown == null)
            return false;
        boolean ret = false;
        for (int i=0; i<thrown.size(); i++) {
            Throwable t = (Throwable) thrown.get(i);
            if (!c.isInstance(t)) {
                ret = true;
                Log.ERROR(msg + ": unexpected exception: " + t.toString());
            }
        }
        return ret;
    }

    /**
     * There are some things that shouldn't appear in an error msg.
     * Check for those here.  Return true if everything seems ok.
     * XXX Should we always do this check, maybe from checkExceptions
     * above?
     */
    public boolean checkExceptionStringContent() {
        String excpn = null;
        String badRegexps[]  = {
            HoneycombTestConstants.INTERNAL_OID_REGEXP,
            // XXX not sure if the delete regexp could legitimately show up...
            HoneycombTestConstants.DELETE_REGEXP,
            HoneycombTestConstants.CTX_REGEXP, 
            HoneycombTestConstants.REFCNT_REGEXP
        };

        for (int i = 0; i < badRegexps.length; i++) {
            if ((excpn = findExceptionWithString(badRegexps[i])) != null) {
                Log.ERROR("Found undesirable string match " + badRegexps[i] +
                    " in the returned error msg: " + excpn);
                return (false);
            }
        }

        return (true);
    }

    /**
     *  Return the exception string if regexp given appears in exception.
     */
    public String findExceptionWithString(String regexp) {
        if (thrown == null)
            return null;
        for (int i=0; i<thrown.size(); i++) {
            String exmsg = ((Throwable)thrown.get(i)).getMessage();
            Pattern p = Pattern.compile(regexp,
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m = p.matcher(exmsg);
            if (m.matches()) {
                return (exmsg);
            }
        }

        return (null);
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        if (pass)
            sb.append("PASS\n");
        else
            sb.append("FAIL\n");
        if (filesize == -1) {
            sb.append("time: ").append(time).append(" ms\n");
        } else {
            sb.append("size: ").append(filesize).append('\n');
            long r = (filesize * 1000) / time;
            if (r > 2000) {
                sb.append("rate: ");
                sb.append(BandwidthStatistic.toMBPerSec(filesize, time));
                sb.append('\n');
            } else
                sb.append("rate: ").append(r).append(" bytes/s\n");
        }
        if (mdoid != null)
            sb.append("oid:  ").append(mdoid).append('\n');
        if (datasha1 != null)
            sb.append("sha:  ").append(datasha1).append('\n');
        if (filename != null)
            sb.append("file:  ").append(filename).append('\n');

        return sb.toString();
    }
}

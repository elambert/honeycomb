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



package com.sun.honeycomb.test.util;

import com.sun.honeycomb.test.util.HttpClient;
import com.sun.honeycomb.test.util.Log;

import java.io.*;

public class QB {

    // default web server location, and URLs for post and query
    private String webSrv = "http://hc-web.sfbay.sun.com/cgi-bin/qb/";
    private String postURL;
    private String queryURL;
    private String runResultsURL;
    
    /* singleton is accessed via static method getInstance() 
     */
    private static QB q;
    synchronized public static QB getInstance() {
        if (q == null) {
            q = new QB();
        }
        return q;
    }
    
    private boolean noDB = false;
    private HttpClient toQB = null;

    private QB(boolean noDB) {
	this.noDB = noDB; // are we running with or without QB database?
        
        String s = System.getProperty("qburl");
        if (s != null) webSrv = s; // use special QB web server (for testing)
        postURL = webSrv + "qb_post.cgi";
        queryURL = webSrv + "qb_query.cgi";
        runResultsURL = queryURL + "?target=results&result.run=";
    }

    private QB() {
        this(false); // default = yes, run with QB DB
    }

    // if noDB is set, do not post test data to QB database,
    // and instead preserve output files (XXX: not done now)
    public void turnOff() {
        Log.DEBUG("QB = off, running without database support");
        noDB = true; 
    }
    public void turnOn() { noDB = false; }
    public boolean isOff() { return noDB; }

    public String getRunResultsURL() { return runResultsURL; }
    
    public File dataFile(String target) throws Throwable {
	File dataFile = File.createTempFile(target, "qb");
	// if no-db flag set, don't delete and don't post to QB
	if (!noDB) dataFile.deleteOnExit(); 
	return dataFile;
    }

    public int post(String target, File infile) throws Throwable {
	
	int qbId = 0;
	if (noDB) return qbId; // do not post, ID cannot be obtained

	toQB = new HttpClient(postURL, target, infile);
	String response = toQB.postHttp(); 
	// response will contain: QB POST: some-word ID = numeric-id OK

	// numeric-id is the item ID in QB database; parse it out
	int starts = response.indexOf("ID = ");
	int ends = response.indexOf(" OK");
	if ((starts != -1) && (ends != -1)) {
	    String idString = response.substring(starts+5, ends);
	    try { qbId = Integer.valueOf(idString).intValue(); } 
	    catch(Exception e) { Log.ERROR("QB RESPONSE: " + response); }
	} else {
	    // unexpected response format, post must have failed, no ID	
	    Log.ERROR("QB RESPONSE: " + response);
	}

	return qbId; // 0 if failed to post or parse response
    }

    /** Convert milliseconds into SQL-compliant string YYYY-MM-DD HH:MM:SS
     */
    public static String sqlDateTime(long millis) {
        return 
            new java.sql.Date(millis).toString() + 
            " " + new java.sql.Time(millis).toString();
    }

}

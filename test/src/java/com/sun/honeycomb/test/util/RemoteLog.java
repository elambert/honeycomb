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

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RemoteLog extends LogLocation {

    public String host; // user@host: path is located there

    public RemoteLog(String host, 
                     String pathName, String reName, boolean partial) {
        super(pathName, reName, partial);
        this.host = host;
        checkpoint(); // set current line count for partial log fetching
    }

    public String toString() {
        return host + ":" + path;
    }

    
    public void checkpoint() {
        try {
            numLines = shell.countLinesCmd(host, path);

            partial = true;
        } catch (Exception e) {
            Log.ERROR("Failed to checkpoint line count in log: " 
        	       + host + ":" + path + " " + e);
            // something went wrong, will fetch the whole log
            partial =LogArchive.FULL;
        }
    }
    
    
    public void setTimeCheckpoint(long time)  {
	try {
	    if (time == CHECK_POINT_TIME_NOW) {
		ExitStatus ex  = shell.sshCmdStructuredOutput(host, "date");
		//Tue Dec 19 02:02:51 GMT 2006
		DateFormat df = new SimpleDateFormat("EEE MMM d HH:mm:ss zzz yyyy");
		Date dt = df.parse(ex.getOutputString());
		timeCheckPoint = dt.getTime();
	    } else {
		timeCheckPoint  = time;
	    }
	} catch (Exception e) {
	    Log.ERROR("Failed to checkpoint line count in log: " + host + ":"
		       + path + " " + e);
	}
    }
    
    
    public void fetch(String dest) {
        try {
            if (partial) {
                shell.tailCmd(host, "+" + archiveFrom() + " " + path 
                              + " > " + dest);
            } else {
                shell.scpCmd(host + ":" + path, dest);
            }
        } catch (Exception e) {;}
    }
    
    
    public boolean grepLog(String msg)  {
	String fileToGrep = this.path;
	String fileHost = host;
	
	// If I need to cut the file, bring it local
	if (partial) {
	    try {
		File tmpFile = File.createTempFile("grep", "tmp");
		fetch(tmpFile.getAbsolutePath());
		fileToGrep = tmpFile.getAbsolutePath();
		fileHost = "";
	    } catch (IOException ioe) {
		Log.WARN("Encountered  an IOException while tailing file " 
			 + this.path );
		Log.WARN(ioe.getMessage());
		return false;
	    }
	} 
	try {
	    return fileContainsText(fileToGrep, fileHost, msg);
	} catch (Throwable t) {
	    Log.WARN("Encoutered the following exception while greping for " 
		    + msg);
	    Log.WARN(t.getMessage());
	    return false; 
	}
    }
}

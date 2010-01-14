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


public class LocalLog extends LogLocation {


    public LocalLog(String pathName, String reName, boolean partial) {
        super(pathName, reName, partial);
        checkpoint(); // set current line count for partial log fetching
    }

    public void checkpoint() {
        try {
            numLines = shell.countLinesCmd(path);
        } catch (Exception e) {
            // something went wrong, will fetch the whole log
            Log.ERROR("Failed to checkpoint line count in log: " + path 
        	      + " " + e);
            partial = false; 
        }
    }
    
    
    public void setTimeCheckpoint(long time) {
	if (time == CHECK_POINT_TIME_NOW) {
	    timeCheckPoint =System.currentTimeMillis();
	} else {
	    timeCheckPoint = time;
	}
    }

    
    public void fetch(String dest) {
        try {
            if (partial) {
        	    shell.tailCmd("+" + archiveFrom() + " " + path + 
                              " > " + dest);
            } else {
                shell.cp(path, dest);
            }
        } catch (Exception e) {;}
    }
    
    
    public boolean grepLog(String msg) { 
	String fileToGrep = this.path;
	
	//cut the file if needed
	if (partial) {
	    try {
		File tmpFile = File.createTempFile("grep", "tmp");
		fetch(tmpFile.getAbsolutePath());
		fileToGrep = tmpFile.getAbsolutePath();
	    } catch (IOException ioe) {
		Log.WARN("Encountered  an IOException while tailing file " 
			 + this.path );
		Log.WARN(ioe.getMessage());
		return false;
	    }
	} 
	
	try {
	    return fileContainsText(fileToGrep, "", msg);
	} catch (Throwable t) {
	    Log.WARN("Encoutered the following exception while greping for " 
		    + msg);
	    Log.WARN(t.getMessage());
	    return false; 
	}
	
    }
    
}

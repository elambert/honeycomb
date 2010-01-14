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
import java.io.FilenameFilter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;



public class LocalLogDir extends LogLocation {
    
    FilenameFilter filter = null;
    
    public LocalLogDir(String pathName, String reName) {
        super(pathName, reName, LogArchive.FULL);
        isFile = false; // this is a dir, not a file
    }

    public void checkpoint() {
        // no-op since logs are in a directory
    }

    
    public void fetch(String dest) {
	try {
            shell.cpr(path, dest);
        } catch (Exception e) {;}
    }
    
    public void setFileFilter(FilenameFilter filter) {
	this.filter = filter;
    }
    
    public void setTimeCheckpoint(long time) {
	if (time == CHECK_POINT_TIME_NOW) {
	    timeCheckPoint =System.currentTimeMillis();
	} else {
	    timeCheckPoint = time;
	}
    }
    
    public boolean grepLog(String msg)  {
	boolean foundIt = false;
	try {
	    Set modifiedFiles = getModifiedFiles(this.path, this.timeCheckPoint);
	    Iterator iter = modifiedFiles.iterator();
	    while (iter.hasNext()) {
		File curFile = (File)iter.next();
		LocalLog curLog = new LocalLog(curFile.getAbsolutePath(),
		    		 "",false);
		curLog.setTimeCheckpoint(this.timeCheckPoint);
		foundIt = curLog.grepLog(msg);
		if (foundIt) {
		    break;
		}
	    }
	} catch (Throwable t) {
	    Log.WARN("Encountered the following throwable while greping log:");
	    Log.WARN(t.getMessage());
	}
	return foundIt;
    }

    private Set getModifiedFiles(String dirPath, long time) {
	File dir = new File(dirPath);
	Set fileSet = new HashSet();
	File [] entries = dir.listFiles(filter);
	if (entries != null) {
	    for (int i = 0; i < entries.length; i++) {
		File curFile = entries[i];
		if (curFile.lastModified() >= time) {
		    fileSet.add(curFile);
		}
	    }
	}
	return fileSet;
    }
    
    
 }

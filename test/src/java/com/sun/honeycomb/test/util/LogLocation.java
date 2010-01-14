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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public abstract class LogLocation {
    public String path; // where the log comes from
    public String saveAs; // can archive under diff name
    public static final int CHECK_POINT_TIME_NOW = -1;
    
    public boolean isFile; // file or dir?
    
    // are we interested in the entire log
    // or only its new portion (useful for /var/log/messages)
    protected boolean partial;

    // skip this many lines before archiving the rest of the log
    protected int numLines;
    private static final int contextLines = 100;
    protected long timeCheckPoint = -1;

    protected RunCommand shell;
    
    public LogLocation(String pathName, String reName, boolean partial) {
        this.path = pathName; // where the log is originally stored
        this.saveAs = reName; // archive under this name
        this.partial = partial;
        this.isFile = true;   // default = file
        shell = new RunCommand(); // shell command wrapper
    }

    // see how many lines are already in log file
    // we will skip them when archiving
    protected abstract void checkpoint();

    // start partial archiving from this line number in the log
    protected int archiveFrom() {
        int startHere = numLines - contextLines;
        return (startHere > 0) ? startHere : 0;
    }
    
    /**
     * Get entries from the log. If the partial variable has been set 
     * to false, this method will retrieve the entire file, otherwise
     * this method will fetch all enteries that proceed the checkpoint. 
     * 
     * @param dest The path to file where the fetched log will be 
     * placed.
     */
    protected abstract void fetch(String dest); // get the log!
    
    /**
     * Establish a timeCheckPoint. This method establishes a time based
     * checkpoint which the grepLog method will use when examining the log
     * for entries. Any entry before this point in time will be ignored by 
     * grepLog. This time checkpoint is ignored by the fetch method
     * 
     * @param time
     */
    protected abstract void setTimeCheckpoint(long time);

    
    public String toString() {
        return path;
    }
    
    
    /**
     * Grep the log for the existence of a string. If the partial flag has been
     * set, then only entries which appear after that check point are
     * considered hits. If both a line number and time check point have been
     * established, then the method will only return true if an entry appears
     * after the line number checkpoint and the time stamp is after the
     * time check point.
     * 
     * @param msg The text to be searched for
     * @return true if the text is found after the checkpoint, else returns
     * false.
     */
    public abstract boolean grepLog(String msg); 
    
    
    
    protected boolean fileContainsText(String file, String host, String msg) 
    throws HoneycombTestException, IOException, ParseException {
	ExitStatus es = null; 
	if (this.timeCheckPoint < 0) {
	    if (host.equals("")) {
		es = shell.exec("/usr/bin/egrep -s '" + msg +"' " + file);
	    } else {
		es = shell.sshCmdStructuredOutput(host, "/usr/bin/egrep -s '"
			+ msg + "' " + file);
	    }
	    return (es.getReturnCode() == 0);
	} else {	
	    Date startDate = new Date(this.timeCheckPoint);
	    Calendar cal = new GregorianCalendar();
	    cal.setTimeInMillis(System.currentTimeMillis());
	    int year = cal.get(Calendar.YEAR);		
	    if (host.equals("")) {		
		es = shell.exec("/usr/bin/egrep '" + msg +"' " + file);		
	    } else {	
		es = shell.sshCmdStructuredOutput(host, "/usr/bin/egrep '" 
			+ msg +"' " + file);		
	    }
	    if (es.getReturnCode() != 0) {
		return false;
	    }	
	    StringReader sr = new StringReader(es.getOutputString(false));
	    BufferedReader br = new BufferedReader(sr);
	    String line = br.readLine(); 
	    while (line != null) {
		String dateText = line.substring(0, line.indexOf(":") + 6) 
				+ " " + year;	
		DateFormat df = new SimpleDateFormat("MMM d HH:mm:ss yyyy");
		Date dt = null;
		dt = df.parse(dateText);
		if (dt.after(startDate) || dt.equals(startDate)) {
		    return true;
		}
		line = br.readLine();
	    }
	    return false;
	}
    }
    
    
    
}
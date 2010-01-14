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



/************* LOG ARCHIVING FACILITIES *************/

package com.sun.honeycomb.test.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.io.File;
import java.io.IOException;

public class LogArchive {

    /* use in calls to addLogSource for partial flag */
    public static final boolean PARTIAL = true;
    public static final boolean FULL = false;

    /* singleton is accessed via static method getInstance()
     */

    private static LogArchive la;
    synchronized public static LogArchive getInstance() {
        if (la == null) {
            la = new LogArchive();
        }
        return la;
    }

    private LogArchive() {
        sources = new ArrayList();
        destinations = new ArrayList();
        tmpLogs = new ArrayList();
        dest = null;
        shell = new RunCommand();
    }
    
    private ArrayList sources; // where to get logs from
    private ArrayList destinations; // where to get logs from
    private ArrayList tmpLogs; // which temp files we created
    private String dest; // log destination directory (inside archive)
    private String archive; // host:/path/to/archive

    private RunCommand shell; // shell command wrapper
    

    /* Where shall we move the logs at the end of the run?
     * Caller sets the log directory name, we supply host and root path
     */
    public void setLogDir(String dir) {
        dest = dir;
    }

    /* What other logs shall we collect and archive?
     * Call multiple times to specify many additional logs.
     * 
     * saveAs parameter allows to rename the log when archiving
     *
     * If partial==true, we will archive only newer log portion (from now on)
     * If partial==false, we will archive the entire log file
     */

    public void addLogSource(String host, String path, String saveAs) {
        addLogSource(host, path, saveAs, FULL); // default = full log (remote)
    }

    public void addLogSource(String path, String saveAs) {
        addLogSource(path, saveAs, FULL); // default = full log (local)
    }

    public void addLogSource(String host, String path, String saveAs, 
                             boolean partial) {
        if (host == null || path == null || saveAs == null) {
            Log.ERROR("Incomplete specification of log source, will not add: " 
                      + "src: " + host + ":" + path + " dst: " + saveAs);
        } else {
            sources.add(new RemoteLog(host, path, saveAs, partial));
        }
    }

    public void addLogSource(String path, String saveAs, boolean partial) {
        if (path == null || saveAs == null) {
            Log.ERROR("Incomplete specification of log source, will not add: " 
                      + "src: " + path + " dst: " + saveAs);
        } else {
            sources.add(new LocalLog(path, saveAs, partial));
        }
    }

    public void addLogSourceDir(String path, String saveAs) {
        if (path == null || saveAs == null) {
            Log.ERROR("Incomplete specification of log source, will not add: " 
                      + "src: " + path + " dst: " + saveAs);
        } else {
            sources.add(new LocalLogDir(path, saveAs));
        }
    }
    
    public void addLogDestination(LogDestination log) {
    		destinations.add(log);
    }

    /* Discover where syslog writes on a given host
     * (differs between Linux and Solaris)
     */
    public String syslog(String host) {
        String syslogFile = null;
        try {
            String os = shell.uname(host);
            syslogFile = shell.syslog_file; // OS-dependent location
        } catch(Exception e) {
            Log.ERROR("Failed to identify OS on host: " + host + " " + e);
            syslogFile = null; // we don't know
        }
        return syslogFile;
    }

    /* Call at the end of the run to actually move logs to archive
     */
    public void archiveLogs() {

        if (tmpLogs != null) { // tell the user about tmp files left around
            Log.STDOUT("Copy of test log(s) in temp dir: " + tmpLogs);
        }
        if (sources == null) { // probably an error 
            Log.WARN("No log files? This may be a bug in Log/LogArchive");
            return;
        } 
        if (dest == null) { 
            // nothing to do - not an error! happens when run without QB
            return;
        }
        
        // temporary local path to store logs in
        // before we move them to the remote archive
        File tmpDir;
        try {
            tmpDir = FileUtil.createTempDir("/" + dest);
        } catch (HoneycombTestException e) {
            Log.ERROR("Failed to create temp directory: " + dest + " Will not archive logs!!!");
            return;
        }

        // first place all logs into a local temp dir
        Iterator i = sources.iterator();
        while (i.hasNext()) {
            LogLocation log = (LogLocation)i.next();
            String saveAsFile = tmpDir.getAbsolutePath() + "/" + log.saveAs;
            log.fetch(saveAsFile);
            // make world-readable
            try {
                if (log.isFile)
                    shell.chmodCmd(saveAsFile, "644");
                else // dir
                    shell.chmodCmd(saveAsFile, "755");
            } catch (Exception e) {;}
        }
        
        // secondly tell LogDestinations where to get logs from!
        i = destinations.iterator();
        while ( i.hasNext() ) {
            LogDestination log = (LogDestination)i.next();
            log.store(tmpDir);
        }
     
        // clean up tmp dir (note: files created with createTempFile stay)
        try {
            shell.rm(tmpDir.getAbsolutePath());
        } catch (Exception e) {
            Log.STDOUT("Failed to clean up temporary log dir: "
                     + tmpDir.getAbsolutePath());
        }
    }

    // used by Log
    public File createTempFile() throws HoneycombTestException {
        File tmpLog = FileUtil.createTempFile("test.", ".log");
        // temp log files are not deleted on exit, but left around
        addLogSource(tmpLog.getAbsolutePath(), "test.log");
        tmpLogs.add(tmpLog);
        return tmpLog;
    }
    
}

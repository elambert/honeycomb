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



package com.sun.honeycomb.util;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.logging.*;

import com.sun.honeycomb.common.InternalException;

// These are used in the iunit test framework
import com.sun.honeycomb.common.SafeMessageDigest;


/**
 * This class encapsulates a few util functions pattered on popen/exec*
 *
 * @author Shamim Mohamed
 * @version $Version$ $Date: 2007-05-18 19:54:08 -0700 (Fri, 18 May 2007) $
 */
public class Exec {
	
    private static final Logger logger =
        Logger.getLogger(Exec.class.getName());

    // Solaris Background Runtime environment 
    private static SolarisRuntime sBgr;

    // Singleton
    private static Exec theExec = null;

    public static final int TIME_EXPIRED = 0xdeadbeef;

    // These are for simulation
    private static PrintStream simFile = null;
    private static File inFileDir = null;

    /**
     * In simulator mode, nothing is exec'ed, but instead it is logged
     * to a file represented by the stream argument.
     *
     * @param fd stream to write
     */
    public static void simulatorMode(PrintStream wfd, String inFileDirectory) {
        simFile = wfd;
        inFileDir = new File(inFileDirectory);
        if (!inFileDir.exists() || !inFileDir.isDirectory())
            throw new InternalException("Not a directory: \"" +
                                        inFileDirectory + "\"");
    }

    public static SolarisRuntime execBg(String cmd, Logger l)
        throws IOException {
        return execBg(cmd, null, l);
    }
    
    public static SolarisRuntime execBg(String cmd, String[] env)
        throws IOException {
        return execBg(cmd, env, null);
    }

    public static SolarisRuntime execBg(String cmd)
        throws IOException {
        return execBg(cmd, null, null);
    }

    public static SolarisRuntime execBg(String cmd, String[] env, Logger l)
        throws IOException {

        if (l != null && l.isLoggable(Level.INFO))
            l.info(cmd);

        if (simFile != null) {
            writeSimRecord("exec", cmd, env);
            simFile.println("");
            l.info("bad sim mode " + cmd);
            return null;
        }

        sBgr = new SolarisRuntime();
      
        try { 
	        sBgr.exec(cmd, env);
	        sBgr.closeDescriptors();
	        return sBgr;
        } finally { 
	        if (!sBgr.processExists()) {
	            sBgr.cleanUp();
	            return null;
	        }
        }
    }
    
    /**
     * Run an external command
     *
     * @param cmd the command to run
     * @throws IOException on any error
     */
    public static int exec(String cmd) throws IOException {
        return exec(cmd, null, null);
    }

    /**
     * Run an external command, and log any output
     *
     * @param cmd the command to run
     * @param env the environment for the process
     * @throws IOException on any error
     */
    public static int exec(String cmd, String[] env)
            throws IOException {
        return exec(cmd, env, null);
    }

    /**
     * Run an external command, and log any output
     *
     * @param cmd the command to run
     * @param l the logger to use for logging
     * @throws IOException on any error
     */
    public static int exec(String cmd, Logger l) throws IOException {
        return exec(cmd, null, l);
    }
    
    /**
     * Run an external command, and log any output
     *
     * @param cmd the command to run
     * @param l the logger to use for logging
     * @param env the environment for the process
     * @throws IOException on any error
     */
    public static int exec(String cmd, String[] env, Logger l)
            throws IOException {
    	
        if (l != null && l.isLoggable(Level.INFO))
            l.info(cmd);

        if (simFile != null) {
            writeSimRecord("exec", cmd, env);
            simFile.println("");
            l.info("bad sim mode " + cmd);
            return 0;
        }

        SolarisRuntime sr = new SolarisRuntime();
        BufferedReader stdout = null;
        BufferedReader stderr = null;
        
        try {
            sr.exec(cmd, env);
            
	        if (l != null) {
	            // Log stdout or stderr
	            stdout =
	                new BufferedReader(
	                    new InputStreamReader(sr.getInputStream()));
	            stderr =
	                new BufferedReader(
	                    new InputStreamReader(sr.getErrorStream()));
	            String line;
	            while ((line = stderr.readLine()) != null)
	                l.warning(line);
	            if (l.isLoggable(Level.FINE))
	                while ((line = stdout.readLine()) != null)
	                    l.fine(line);
	        }
            
	        int rc;
	        try {
	            rc = sr.waitFor();
	        } catch (Throwable iex) {
	            throw (IOException)
	                new IOException(cmd + " interrupted").initCause(iex);
	        }
	
	        logger.fine("Running: " + cmd + " returned: " + rc);
	        sr.cleanUp();
	        return rc;
        } finally {
           	try{
           		if (stdout != null)
           			stdout.close();
        	} catch (IOException e) {}
        	try{
        		if (stderr != null)
        			stderr.close();
        	} catch (IOException e) {}        	
        }
    }

    public static int exec(String[] cmd, String[] env, Logger l)
	    throws IOException {
	
		if (l != null && l.isLoggable(Level.INFO))
		    l.info(cmd[0]);
		
		if (simFile != null) {
		    writeSimRecord("exec", cmd[0], env);
		    simFile.println("");
		    l.info("bad sim mode " + cmd);
		    return 0;
		}
		
		SolarisRuntime sr = new SolarisRuntime();
		
		sr.exec(cmd, env);
		BufferedReader stdout = null;
	    BufferedReader stderr = null;
	    
	    try {
			if (l != null) {
			    // Log stdout or stderr
			    stdout =
			        new BufferedReader(
			            new InputStreamReader(sr.getInputStream()));
			    stderr =
			        new BufferedReader(
			            new InputStreamReader(sr.getErrorStream()));
			    String line;
			    while ((line = stderr.readLine()) != null)
			        l.warning(line);
			    if (l.isLoggable(Level.FINE))
			        while ((line = stdout.readLine()) != null)
			            l.fine(line);
			}
            
			int rc;
			try {
			    rc = sr.waitFor();
			} catch (Throwable iex) {
			    throw (IOException)
			        new IOException(cmd + " interrupted").initCause(iex);
			}
		
			logger.fine("Running: " + cmd[0] + " returned: " + rc);
			sr.cleanUp();
			return rc;
	    } finally {
	    	try{
	       		if (stdout != null)
	       			stdout.close();
	    	} catch (IOException e) {}
	    	try{
	    		if (stderr != null)
	    			stderr.close();
	    	} catch (IOException e) {}  
	    }
    }
    
    /**
     * Run an external command, and read its output
     *
     * @param cmd the command to run
     * @return a filehandle to read from
     * @throws IOException on any error
     */
    public static BufferedReader execRead(String cmd)
        throws IOException {
        return execRead(cmd, null, null);
    }
    
    public static BufferedReader execRead(String cmd[])
	    throws IOException {
	    return execRead(cmd, null, null);
	}

    /**
     * Run an external command, and read its output
     *
     * @param cmd the command to run
     * @param env the environment for the process
     * @return a filehandle to read from
     * @throws IOException on any error
     */
    public static BufferedReader execRead(String cmd, String[] env)
        throws IOException {
        return execRead(cmd, null, null);
    }

    /**
     * Run an external command, and read its output
     *
     * @param cmd the command to run
     * @param l the logger to use for logging
     * @return a filehandle to read from
     * @throws IOException on any error
     */
    public static BufferedReader execRead(String cmd, Logger l)
        throws IOException {
        return execRead(cmd, null, l);
    }

    /**
     * Run an external command and read its output; log its stderr
     *
     * @param cmd the command to run
     * @param env the environment for the process
     * @param l the logger to use for logging
     * @return a filehandle to read from
     * @throws IOException on any error
     */
    public static BufferedReader execRead(String cmd, String[] env, Logger l)
        throws IOException {
    	
    	logger.fine("Running: " + cmd);
    	
        if (l != null && l.isLoggable(Level.INFO))
            l.info(cmd);

        if (env != null)
            Arrays.sort(env);

        if (simFile != null) {
            writeSimRecord("execRead", cmd, env);
            BufferedReader br = getSimInput(cmd, env);
            simFile.println("");
            return br;
        }

        SolarisRuntime sr = new SolarisRuntime();
   
        try { 
            sr.exec(cmd,env);
            BufferedReader br = new BufferedReader(new InputStreamReader(sr.getInputStream()));
            return br;
        } finally {  
            sr.cleanUp();
        }
    }
    
    public static InputStream execReadWithInputStream(String cmd, String[] env, Logger l)
        throws IOException {
       
        logger.fine("Running: " + cmd);
        
        if (l != null && l.isLoggable(Level.INFO))
            l.info(cmd);
    
        if (env != null)
            Arrays.sort(env);
    
        SolarisRuntime sr = new SolarisRuntime();
        sr.exec(cmd,env);
        return getExec().getExecInputStream(sr);
    }
    
    private ExecInputStream getExecInputStream(SolarisRuntime sr) { 
        return new ExecInputStream(sr,sr.getInputStream()); 
    }
    
    public static BufferedReader execRead(String cmd[], String[] env, Logger l)
	    throws IOException {
    	
    	logger.fine("Running: " + cmd[0]);
    	
	    if (l != null && l.isLoggable(Level.INFO))
	        l.info(cmd[0]);
	
	    if (env != null)
	        Arrays.sort(env);
	
	    if (simFile != null) {
	        writeSimRecord("execRead", cmd[0], env);
	        BufferedReader br = getSimInput(cmd[0], env);
	        simFile.println("");
	        return br;
	    }
	
	    SolarisRuntime sr = new SolarisRuntime();
	   
        try { 
            sr.exec(cmd,env);
            BufferedReader br = new BufferedReader(new InputStreamReader(sr.getInputStream()));
            return br;
        } finally { 
            sr.cleanUp();
        }
	}

    /**
     * Run an external command and write to its stdin
     *
     * @param cmd the command to run
     * @return a filehandle to write to
     * @throws IOException on any error
     */
    public static PrintStream execWrite(String cmd)
        throws IOException {
        return execWrite(cmd, null);
    }

    /**
     * Run an external command and write to its stdin; log its output: 
     * <tt>stdout</tt> is logged <tt>fine</tt>, and <tt>stderr</tt> is
     * <tt>warning</tt>.
     *
     * @param cmd the command to run
     * @param l the logger to use for logging
     * @return a filehandle to write to
     * @throws IOException on any error
     */
    public static PrintStream execWrite(String cmd, Logger l)
        throws IOException {
    	
    	logger.fine("Running: " + cmd);
    	
        if (l != null && l.isLoggable(Level.INFO))
            l.info(cmd);

        if (simFile != null) {
            writeSimRecord("execWrite", cmd, null);
            return getExec().getNonClosingStream(simFile);
        }

        SolarisRuntime sr = new SolarisRuntime();
       
        sr.exec(cmd);
        PrintStream w = new PrintStream(sr.getOutputStream());
        if (w == null)
            throw new IOException("Couldn't get ostream for \"" + cmd + "\"");
        return getExec().getWaitingStream(sr, cmd, l, w);
    }
    
    /**
     * Run an external command with a timeout check
     *
     * @param cmd the command to run
     * @param timeout the maximum time (in ms) the command is allowed
     * @throws IOException on any error
     */
    public static int exec(String cmd, long timeout)
            throws IOException {

        // A timeout value of 0 (or negative) means vanilla exec, no timeout
        if (timeout <= 0)
            return exec(cmd);

        return exec(cmd, timeout, null);
    }
    /**
     * Run an external command with a timeout check, and log its output: 
     * <tt>stdout</tt> is logged <tt>fine</tt>, and <tt>stderr</tt> is
     * <tt>warning</tt>.
     *
     * @param cmd the command to run
     * @param timeout the maximum time (in ms) the command is allowed
     * @param l the logger to use for logging
     * @throws IOException on any error
     */
    public static int exec(String cmd, long timeout, Logger l)
        throws IOException {

        if (timeout <= 0)
            return exec(cmd, l);

        if (l != null && l.isLoggable(Level.INFO))
            l.info(cmd);

        if (simFile != null) {
            writeSimRecord("exec[t=" + timeout + "]", cmd, null);
            simFile.println("");
            return 0;
        }

        SolarisRuntime sr = new SolarisRuntime();
        try { 
	        sr.exec(cmd);
	
	        ProcessMonitor monitor = getExec().getMonitor(sr, timeout, cmd, l);
	        new Thread(monitor).start();
	
	        if (l != null) {
	        	BufferedReader stdout = null;
	        	BufferedReader stderr = null;
	            try {
	                // Log stdout or stderr
	                stdout =
	                    new BufferedReader(
	                       new InputStreamReader(sr.getInputStream()));
	                stderr =
	                    new BufferedReader(
	                       new InputStreamReader(sr.getErrorStream()));
	
	                String line;
	                while ((line = stderr.readLine()) != null)
	                    l.warning(line);
	                if (l.isLoggable(Level.INFO))
	                    while ((line = stdout.readLine()) != null)
	                        l.info(line);
	            }
	            catch (Exception e) {
	                // This probably means the proc was terminated after
	                // the timeout expired
	                if (l.isLoggable(Level.INFO))
	                    l.info("Process \"" + cmd + "\": read: " + e);
	            } finally {
	            	try{
	               		if (stdout != null)
	               			stdout.close();
	            	} catch (IOException e) {}
	            	try{
	            		if (stderr != null)
	            			stderr.close();
	            	} catch (IOException e) {}  
	            }
	        }
	
	        int rc = -1;
	        try {
	            rc = sr.waitFor();
	        }
	        catch (Throwable iex) {
	            throw (IOException)
	                new IOException(cmd + " interrupted").initCause(iex);
	        }
	
	        if (!monitor.done())
	            // The process had to be killed
	            return TIME_EXPIRED;
	        
	        logger.fine("Running: " + cmd + " returned: " + rc);
	        return rc;
        } finally { 
	        sr.cleanUp();
        }
    }
    
    public static int exec(String[] cmd, long timeout) throws IOException {
    	return exec(cmd,timeout,null);
    }

    public static int exec(String[] cmd, long timeout, Logger l)
	    throws IOException {
	
	    if (timeout <= 0)
	        return exec(cmd,timeout,l);
	
	    if (l != null && l.isLoggable(Level.INFO))
	        l.info(cmd[0]);
	
	    if (simFile != null) {
	        writeSimRecord("exec[t=" + timeout + "]", cmd[0], null);
	        simFile.println("");
	        return 0;
	    }
	
	    SolarisRuntime sr = new SolarisRuntime();
        try { 
		    sr.exec(cmd,null);
		
		    ProcessMonitor monitor = getExec().getMonitor(sr, timeout, cmd[0], l);
		    new Thread(monitor).start();
		
		    if (l != null) {
		    	BufferedReader stdout = null;
		    	BufferedReader stderr = null;
		        try {
		            // Log stdout or stderr
		            stdout =
		                new BufferedReader(
		                   new InputStreamReader(sr.getInputStream()));
		            stderr =
		                new BufferedReader(
		                   new InputStreamReader(sr.getErrorStream()));
		
		            String line;
		            while ((line = stderr.readLine()) != null)
		                l.warning(line);
		            if (l.isLoggable(Level.INFO))
		                while ((line = stdout.readLine()) != null)
		                    l.fine(line);
		        }
		        catch (Exception e) {
		            // This probably means the proc was terminated after
		            // the timeout expired
		            if (l.isLoggable(Level.INFO))
		                l.info("Process \"" + cmd + "\": read: " + e);
		        } finally {
		        	try{
		           		if (stdout != null)
		           			stdout.close();
		        	} catch (IOException e) {}
		        	try{
		        		if (stderr != null)
		        			stderr.close();
		        	} catch (IOException e) {}  
	            }
		    }
		
		    int rc = -1;
		    try {
		        rc = sr.waitFor();
		    }
		    catch (Throwable iex) {
		        throw (IOException)
		            new IOException(cmd + " interrupted").initCause(iex);
		    }
		
		    if (!monitor.done())
		        // The process had to be killed
		        return TIME_EXPIRED;
		    
		    logger.fine("Running: " + cmd + " returned: " + rc);
		    return rc;
        } finally { 
            sr.cleanUp();
        }
	}


    private static Exec getExec() {
        if (theExec == null)
            theExec = new Exec();

        return theExec;
    }

    private PrintStream getNonClosingStream(PrintStream w) {
        return new WaitingStream(null, null, null, w, false);
    }

    private PrintStream getWaitingStream(SolarisRuntime sr, String cmd, Logger l, 
                                    PrintStream w) {
        return new WaitingStream(sr, cmd, l, w, true);
    }

    private ProcessMonitor getMonitor(SolarisRuntime sr, long timeout, String cmd,
                                      Logger l) {
        return new ProcessMonitor(sr, timeout, cmd, l);
    }

    /**
     * This is a helper class for use with {@link execWrite}: it
     * extends {@link PrintStream} so that after it's closed, we can wait
     * for the process to check its exit status and log its output if
     * required. It passes on all PrintStream methods to the PrintStream it was
     * created with, except for close.
     */
    private class WaitingStream extends PrintStream {
        String cmd;
        Logger l;
        SolarisRuntime sr;
        boolean closeSuper;
        PrintStream w;

        WaitingStream(SolarisRuntime sr, String cmd, Logger l, 
                      PrintStream w, boolean closeSuper) {
            super(w);
            this.w = w;
            this.sr = sr;
            this.cmd = cmd;
            this.l = l;
            this.closeSuper = closeSuper;
           
            if (this.w == null)
                throw new RuntimeException("Null output stream");
        }

        public void close() {
            if (closeSuper) {
                w.close();
        	}
            else
                if (w != null)
                    w.println("[EOF]");

            if (sr != null)
                try {
                    waitForProc();
                }
                catch (IOException e) {
                    if (l != null)
                        l.log(Level.SEVERE, "waiting for external proc: ", e);
                }
        
           sr.cleanUp();
        }
        
        
        private void waitForProc() throws IOException {
        	BufferedReader stdout = null;
        	BufferedReader stderr = null;
        	try {
	            if (l != null) {
	                stdout =
	                    new BufferedReader(
	                        new InputStreamReader(sr.getInputStream()));
	                stderr =
	                    new BufferedReader(
	                        new InputStreamReader(sr.getErrorStream()));
	                String line;
	                while ((line = stderr.readLine()) != null)
	                    l.warning(line);
	                while ((line = stdout.readLine()) != null)
	                    l.fine(line);
	            }
        	} finally {
        		try{
               		if (stdout != null)
               			stdout.close();
            	} catch (IOException e) {}
            	try{
            		if (stderr != null)
            			stderr.close();
            	} catch (IOException e) {}  
        	}

            int rc = 0;
            try {
                rc = sr.waitFor();
            } catch (Throwable iex) {
                throw (IOException)
                    new IOException(cmd + " interrupted").initCause(iex);
            }
            if (rc != 0) {
                throw new IOException(cmd + " exit status " + rc);
            }
        }
    }

    private class ExecInputStream extends InputStream {
        private SolarisRuntime sr = null;
        private InputStream is = null;
        
        public ExecInputStream(SolarisRuntime sr,InputStream is) {
            this.sr = sr;
            this.is = is;
        }

        public void close() throws IOException {
            sr.cleanUp();
            is.close();
        }

        public int read() throws IOException {
            return is.read();
        }
   }

    /**
     * This is a helper class used by the execs with timeout: a
     * thread to wait for the external process for a given time, and
     * kills it if it's still running.
     */
    private class ProcessMonitor implements Runnable {

        private static final long POLL_INTERVAL = 1000;

        private SolarisRuntime sr = null;
        private Logger logger = null;
        private long timeout = 0;
        private boolean done = false;
        private boolean terminated = false;
        private String cmd = null;

        ProcessMonitor(SolarisRuntime sr, long timeout, String cmd, Logger l) {
            this.sr = sr;
            this.timeout = timeout;
            this.cmd = cmd;
            this.logger = l;
        }

        synchronized boolean done() {
            done = true;
            notifyAll();
            return !terminated;
        }

        public void run() {
            synchronized(this) {
                long startTime = System.currentTimeMillis();
                long endTime = startTime + timeout;
                while (!done && System.currentTimeMillis() < endTime) {
                    try {
                        wait(POLL_INTERVAL);
                    } catch (InterruptedException ignore) {}
                }

                if (!done) {
                    if (logger != null)
                        logger.severe("Command \"" + cmd +
                                      "\" taking too long; destroying it");
                    terminated = true;
                    sr.destroy();
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // Utilities for the simulator

    private static String lQuote(String s) {
        if (s == null)
            return "(null)";
        return "[" + s.length() + "]" + s;
    }

    private static String nQuote(String s) {
        if (s == null)
            return "(null)";
        return s;
    }

    private static String image(String s) {
        if (s == null)
            return "(null)";
        return "\"" + s + "\"";
    }

    private static void writeSimRecord(String ctx, String cmd, String[] env)
            throws IOException {
        simFile.print(nQuote(ctx) + "(" + image(cmd));
        if (env != null) {
            simFile.println(",");
            for (int i = 0; i < env.length; i++)
                simFile.print("    " + lQuote(env[i]));
        }
        simFile.println(")");
    }

    private static BufferedReader getSimInput(String cmd, String[] env)
            throws IOException {

        String s = cmd;
        if (env != null)
            for (int i = 0; i < env.length; i++)
                s += "\n    \"" + env[i] + "\"";

        String fH = hash(s);
        String fName = inFileDir + "/" + fH;

        simFile.print("Input: \"" + fName + "\" [" + s + "]");

        File inp = new File(inFileDir, fH);
        if (inp.exists()) {
            BufferedReader r = new BufferedReader(new FileReader(inp));
            if (r != null) {
                simFile.println("");
                return r;
            }
        }

        simFile.println(" missing, substituting /dev/null");
        simFile.println("@@@ run \"" + cmd + " >" + fH + 
                        "; mv " + fH + " " + inFileDir + "\"");
        return new BufferedReader(new FileReader("/dev/null"));
    }
    
    private static String hash(String w) {
        try {
            

            byte[] digest = SafeMessageDigest.digest(w.getBytes("UTF8"),"MD5");

            StringBuffer hex = new StringBuffer();
            for (int i = 0; i < digest.length; i++) {
                String h = Integer.toHexString(0xff & digest[i]);
                if (h.length() <= 1)
                    h = "0" + h;
                hex.append(h);
            }

            return hex.toString();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

    }
}

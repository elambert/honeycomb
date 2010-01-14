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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;


/**
 * This class is an interface for our own Runtime for solaris
 * it avoids some problems with Java's implementation of 
 * getRuntime().exec. Java implementation of Runtime.exec() 
 * is done with fork down in C land and this results in the 
 * allocation of the same virtual memory as the parent code 
 * which in our case is a 200MB JVM or more in some instances.
 * Using vfork() avoids this problem as vfork() does not duplicate
 * the parent's address space. (READ vfork() man page for more info)
 *  
 * There maybe a fix in the future for this introduce into the 
 * JDK in order to use vfork() if they do that then we can remove
 * this code and start using a correct implmenetation.
 * 
 * Links to articles describing this issue:
 * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5049299
 * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4227230
 * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4693581
 * 
 * depends on the exec.c (libjava-exec.so)
 * 
 * WARNING: only use this class directly if you know what you 
 *          are doing... you SHOULD be using the Exec utility found
 *          in com.sun.honeycomb.util.Exec you have to be sure to 
 *          correctly call the cleanUp method once you're done with 
 *          this object otherwise you will leak filedescriptors until 
 *          the GC comes around and cleans things up.
 *
 * @author Rodney Gomes
 */

public class SolarisRuntime {
    
    static {
        System.loadLibrary("java-exec");
    }
   
    private static Logger logger = Logger.getLogger(SolarisRuntime.class.getSimpleName());
    protected int pid = 0;
    private String cmd = null;

    private FileDescriptor fdin;
    private FileDescriptor fdout;
    private FileDescriptor fderr; 
    
    private FileOutputStream is = null;
    private FileInputStream os = null;
    private FileInputStream eos = null;
  
    public SolarisRuntime(){
    	fdin = new FileDescriptor();
    	fdout = new FileDescriptor();
    	fderr = new FileDescriptor(); 
    } 
    
    private native void exec(String[] cmd, String[] env, FileDescriptor fdin, FileDescriptor fdout, FileDescriptor fderr) throws IOException;
    private native int waitpid(int testOnly);    
    public native void destroy();


    public int waitFor() {
        return waitpid(0);
    }
    
    public boolean processExists() {
        return (waitpid(1) < 0)? false : true;
    }
    
    public void exec(String[] cmd, String[] env) throws IOException {
    	exec(cmd,env,fdin,fdout,fderr);    	
    }
    
    /**
     * exec with string command and string array environment. 
     * 
     * CAUTION: be sure not to put quoutes into the cmd string if you would
     *          like to use quotes then use the exec(String[] cmd, String[] env)
     *          
     * @param cmd
     * @param env
     */
    public void exec(String cmd, String[] env) throws IOException {
        this.cmd = new String(cmd);
    	if (cmd.indexOf("\"") != -1 || cmd.indexOf('\'') != -1)
    		throw new IOException("Illegal argument if you want to use \" "+
                                  "or \' make sure to use the other exec method."
                                  );
    	
    	String[] args = cmd.split("\\s++");
    	exec(args, env);	
    }
        
    public void exec(String cmd) throws IOException{
    	exec(cmd,null);
    }
    
    /*
     * Only create a new instance if it does not already exist. 
     */
    public synchronized InputStream getInputStream(){
        if (os == null)
            os = new FileInputStream(fdout);
    	return os;
    }
    
    /*
     * Only create a new instance if it does not already exist.
     */
    public synchronized InputStream getErrorStream(){
        if (eos == null)
            eos = new FileInputStream(fderr);
    	return eos;
    }
    
    /*
     * Only create a new instance if it does not already exist.
     */
    public synchronized OutputStream getOutputStream(){
     	if (is == null)
     		is = new FileOutputStream(fdin);
     	return is;
    }
   
    private boolean cleaned = false;
    public void cleanUp(){
    	if (os == null)
		closeDescriptor(fdout);
    	
    	if (eos == null)
    		closeDescriptor(fderr);
    	
    	if (is == null)
    		closeDescriptor(fdin);
        
    	waitFor();
        cleaned = true;
    }
    
    public void closeDescriptors(){
    	if (os == null)
		closeDescriptor(fdout);
    	
    	if (eos == null)
    		closeDescriptor(fderr);
    	
    	if (is == null)
    		closeDescriptor(fdin);
    }

    private void closeDescriptor (FileDescriptor fd){
    	try {
			new FileOutputStream(fd).close();
		} catch (IOException e) {}
    }
    
    String getCommand() { 
        return new String(cmd);
    }
    
    protected void finalize() throws Throwable {
        super.finalize();
        if (!cleaned) {
            /*
             * Catch possible leaks in the code where we forget to close
             * the input stream and therefore create zombie processes as 
             * well as leak filedescriptors.
             */
            logger.severe("Exec called without closing InputStream for command: '" + 
                           getCommand() + "'");
            this.cleanUp();
        }
    }
 
}

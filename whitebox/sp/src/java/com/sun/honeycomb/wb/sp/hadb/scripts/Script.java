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



package com.sun.honeycomb.wb.sp.hadb.scripts;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;


public interface Script {
    
    public static final int RETURN_CODE_PASSED = 0;
    public static final int RETURN_CODE_FAILED = 1;
    public static final int RETURN_CODE_INPROGRESS = -1;
    
    /**
     * @param p a properties object which
     * contains the setup parameters used by
     * this script. 
     */
    public void setUp(LinkedList l ) throws IllegalArgumentException;
    
    /**
     * Execute the script. 
     * 
     * @param caller Client calling execute on the 
     * script
     */
    public void executeScript(Object caller) throws Throwable;
    
    /**
     * Get a description of this script.
     * 
     * @return A description of the script be executed.
     */
    public String getDescription ();
    
    /**
     * Get the name of this script.
     * 
     * @return the script name
     */
    public String getName ();
    
    /**
     * 
     * @return the exit code for the script. Scripts
     * should abide by the following convention. 
     * -Success, return 0
     * -Error, return >0
     * -In progress <0
     */
    public int getResult ();

    /**
     * 
     * @return the OutputStream to which 
     * Standard out messages are sent. 
     * Null, if not applicable 
     */
    public OutputStream getOutputStream();
    
    /**
     * 
     * @return the OutputStream to which 
     * Standard error messages are sent. 
     * Null, if not applicable 
     */
    public OutputStream getErrorStream();
    
    /**
     * 
     * @param is InputStream where script
     * can use to read standard input.
     */
    public void setInputStream(InputStream is);

    /**
     * Stop the script if it is running.
     * If script is not running, this call
     * should be ignored. Will return false if
     * the script failed.
     *
     */
    public boolean stop () throws Throwable;
}

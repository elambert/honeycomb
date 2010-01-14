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



package com.sun.honeycomb.hctest.suitcase;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.io.BufferedReader;
import java.io.Reader;
import java.io.IOException;

import com.sun.honeycomb.test.util.Log;

public class OutputReader extends Thread
{
    InputStream _stream = null;
    boolean _silent;
    boolean _useLog = false;;
    String lastLine=null;

    public OutputReader( InputStream inputStream,boolean silent,boolean useLog ) {
        super();
        _silent=silent;
        _stream=inputStream;
        _useLog=useLog;
        this.start();
    }
 
    public OutputReader( InputStream inputStream,boolean silent ) {
        this(inputStream,silent,false);    
    }

    public String getLastLine() {
        return lastLine;
    }
    public OutputReader( InputStream inputStream )
    {
        super();
        _silent=false;
        _stream = inputStream;
        this.start();
    }
    public synchronized void run()
    {
        Reader streamReader=new  InputStreamReader(_stream);

        
        try {            
            BufferedReader reader = new BufferedReader( streamReader );
            String nextLine;
            if(!_silent) {
                while ((nextLine = reader.readLine()) != null) {
                    lastLine=nextLine;

                    if (_useLog) { 
                        Log.INFO("output: "+nextLine);
                    } else {
                        System.out.println("output: "+nextLine);
                    }
                }
            } else {

                while ((nextLine = reader.readLine()) != null) {
                    lastLine=nextLine;
                }
            }
            reader.close();
            streamReader.close();
        }catch (IOException e) {                    
            System.out.println("IO Exception streaming output from Process");
        }

    }
} 



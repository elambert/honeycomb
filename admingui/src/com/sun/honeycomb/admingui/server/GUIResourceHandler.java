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



package com.sun.honeycomb.admingui.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.DataInputStream;
//import java.io.FileInputStream;
//import java.io.FileWriter;
import java.io.IOException;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.OutputStream;

        
/**
 * this class is used to serve GUI static resources: html, jnlp and jar files.
 * it is only used when the GUI is started.
 * @see MainHandler.java
 */
public class GUIResourceHandler
    extends org.mortbay.http.handler.ResourceHandler {

    // this tag will be replaced with an actual address
    static final String HOSTNAME_TAG = "\\$\\{host\\}"; // regexp for ${host}
    static final String PORT_TAG     = "\\$\\{port\\}"; // regexp for ${port}

    static final String JNLP_FILE_EXT = "jnlp";

    String resBase = null; // the local path where files are served from
            
    /**
     * the admin address in the jnlp file cannot be hardcoded
     * since it's configurable.
     * We need to intercept reqests for the jnlp file, extract the host from
     * the header and put that info in the jnlp file before the jnlp file is
     * served to the client
     */
    public GUIResourceHandler() {
        super();
        //this.resBase = resBase;
    }
    
    public void handle(java.lang.String pathInContext,
                   java.lang.String pathParams,
                   org.mortbay.http.HttpRequest request,
                   org.mortbay.http.HttpResponse response)
            throws org.mortbay.http.HttpException,
                   java.io.IOException {


        System.out.println("request from " + request.getRemoteAddr() +
           " -> " + pathInContext);
 
        // check if request for a jnlp file
        if (pathInContext.endsWith(JNLP_FILE_EXT)) {
            //String filename = resBase + pathInContext;
            String filename = pathInContext.substring(1);
            byte[] result;
            try {
                result = replaceTags(request.getHost(), // value for HOSTNAME_TAG
                            String.valueOf(request.getPort()), // PORT_TAG
                            filename);
            } catch (IOException ioe) {
                System.out.println("Exception handling jnlp file: " + ioe + 
                    " " + ioe.getLocalizedMessage());
                ioe.printStackTrace();
                throw(ioe);
            }
            // send jnlp file back
            request.setHandled(true);
            response.setContentType("application/x-java-jnlp-file");
            response.setContentLength(result.length);
            OutputStream out = response.getOutputStream();
            out.write(result);
            out.flush();
 
        } else
            super.handle(pathInContext, pathParams, request, response);
    }

    protected static byte[] replaceTags(String hostname, String port,
        String filename) throws IOException {

        String hostName;
        System.out.println("replacing tags in " + filename + ".template: " +
            HOSTNAME_TAG + "->" + hostname + " " +
            PORT_TAG + "->" + port);

//      FileWriter fw = new FileWriter(filename, false);
        BufferedReader br = new BufferedReader(new InputStreamReader(
//            new FileInputStream(filename + ".template")));
              ClassLoader.getSystemResourceAsStream(filename + ".template")));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos);
                
        String line;
        while ((line = br.readLine()) != null) {
            // System.out.println("read:  " + line); System.out.flush();
            line = line.replaceFirst(HOSTNAME_TAG, hostname);
            line = line.replaceFirst(PORT_TAG, port);
            // System.out.println("write: " + line); System.out.flush();
            writer.write(line + "\n");
        }
        writer.flush();

        return baos.toByteArray();
    }

   /** testing */
    public static void main(String args[]) throws IOException {
        byte[] res = replaceTags("rumble.east", "8090", "admgui.jnlp");
    }
}
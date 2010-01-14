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


package com.sun.honeycomb.adm.cli.config;
import java.util.Properties;
import java.io.*;

public class CliConfigProperties extends Properties {

    private static CliConfigProperties _instance = null;

    /**
     * The getter for this singleton class
     */
    public static synchronized CliConfigProperties getInstance() {
        if (_instance == null) {
            _instance = new CliConfigProperties();
        }

        return(_instance);
    }

    CliConfigProperties() {

        String confpath = "/opt/honeycomb/share";

        
        File confdir = new File (confpath);
        if(!confdir.exists()) {
            confpath = "../lib/";       
            confdir = new File (confpath);

        }

        String filename=confdir.getAbsolutePath() + "/cli_config.properties";
        File defaults_file = new File (filename);

        
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(defaults_file);
            load (fis);
        } catch (IOException ioe) {
            //
            // Fixme - how do we deal with this?
            // 
            System.err.println("Failed to load properties file: " + filename);

        } finally {
            try {
                fis.close();
            } catch (Exception e) {
                // ignore
            }
        }
        if(isEmpty()) {
            System.err.println("Severe: Got empty property file: " + filename); 

        }
    }


}

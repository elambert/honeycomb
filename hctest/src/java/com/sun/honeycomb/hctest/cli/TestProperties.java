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



package com.sun.honeycomb.hctest.cli;

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.Properties;

/**
 *
 * @author jk142663
 */
public class TestProperties {
    
    private static final String PROPERTY_FILE_LOCATION = "/opt/test/etc/";
    protected static final String CLI_PROPERTY_FILE = "admin_cli.properties";
    protected static final String ADMIN_RESOURCE_PROPERTY_FILE = "CliResources_en.properties.in";
    protected static final String CLI_AUDIT_MESSAGE_PROPERTY_FILE = "AdminResources_en.properties.in";
    
    private String propFile;
    
    /** Creates a new instance of TestProperties */
    public TestProperties() {
        propFile = PROPERTY_FILE_LOCATION + CLI_PROPERTY_FILE;
    }
    
    public TestProperties(String fileName) {
        propFile = PROPERTY_FILE_LOCATION + fileName;
    }
    
    public String getPropertyFile() {
        return propFile;
    }
    
    public String getProperty(String propName) {       
        try {
            // Check for existence
            File file = new File(propFile);
            if (!(file.exists())) {
                throw new HoneycombTestException("Unable to get " +
                     "properties file " + propFile);
            }
        } catch (Exception e) {
            Log.ERROR("Error accessing properties file: " + propFile + 
                    ": " + e.toString());
        }
        
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(propFile));
        } catch (IOException e) {
            Log.ERROR("IO Error accessing:" + e.toString());
        }
  
        String propValue = properties.getProperty(propName);
        if (propValue != null) 
            propValue = propValue.trim();
        
        return propValue;  
    }
    
}

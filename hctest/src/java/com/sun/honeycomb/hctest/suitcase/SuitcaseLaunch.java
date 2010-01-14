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

import com.sun.honeycomb.hctest.util.*;

import com.sun.honeycomb.hctest.rmi.clntsrv.clnt.ClntSrvClnt;
import com.sun.honeycomb.hctest.rmi.spsrv.clnt.SPSrvClnt;
import java.util.logging.Logger;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import java.io.*;
import java.util.*;
import java.lang.Runtime;
import java.lang.reflect.Method;


public class SuitcaseLaunch {

    String harvestLocation=new String("/opt/test/bin/harvest.sh");
    String importLocation=new String("/opt/test/bin/import.sh");
    String dbscriptLocation=new String("/opt/test/bin/dbscript.sh");
    String analyzeLocation=new String("/opt/test/bin/analyze.sh");
    String installLocation=new String("/opt/test/bin/install_suitcase.sh");

    private boolean locateScript(String scriptPath){
        File f = new File(scriptPath);
        if (!f.exists()) {
            System.err.println("Cannot locate: " + scriptPath + ". Validation not run");
            return false;
        }
        return true;
    }

    private boolean locateScripts() {
        if (!locateScript(harvestLocation)) 
            return false;
        if (!locateScript(importLocation)) 
            return false;
        if (!locateScript(dbscriptLocation)) 
            return false;
        if (!locateScript(analyzeLocation)) 
            return false;
        if (!locateScript(installLocation)) 
            return false;
        return true;
    }

    public SuitcaseLaunch() {
/*
        String noValidate = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_NO_FRAGMENT_VALIDATE);
        if(noValidate != null) {
            Log.INFO("Skipping fragment validation.");
            return;
        } 

        Run run = null; 
        run = Run.getInstance();
        
        String clusterName;
        
        clusterName = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER);
        if(null == clusterName) {
            System.err.println("Can't run validation; cluster not set.");
            return;
        }

        if(!locateScripts()) {
            return;
        }

        String exclude= TestRunner.getProperty(HoneycombTestConstants.PROPERTY_EXCLUDE);

        System.out.println("Got run id: " + run.getId());

        try {
            String excludeString = new String();
            if (null != exclude) {
                excludeString += "-X " + exclude;
            }
            System.err.println("Launching: " + analyzeLocation+" -t /mnt/test " + excludeString + " -i " +  run.getId() +  " " + clusterName );


            Runtime rt = Runtime.getRuntime();
            Process proc = rt.exec(analyzeLocation+" -e /mnt/test " + excludeString + " -i " +  run.getId() +  " " + clusterName );

            OutputReader errReader= new OutputReader( proc.getErrorStream());
            OutputReader stdReader = new OutputReader( proc.getInputStream());
            System.out.println("All done...");
            int exitVal = proc.waitFor();
            System.out.println("Exit value for fragment analyzer: " + exitVal);
            //
            // Cleanup errReader and stdReader
            //

        } catch (Throwable t) {
            System.err.println("Failed to execute analyze.sh." + t.toString());
        }
*/
    }
};

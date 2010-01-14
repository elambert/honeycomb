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



package com.sun.honeycomb.hctest.cases;

import java.io.BufferedReader;
import java.util.Vector;

import com.sun.honeycomb.hctest.cli.CLI;
import com.sun.honeycomb.hctest.cli.CLIState;
import com.sun.honeycomb.hctest.cli.DataDoctorState;
import com.sun.honeycomb.hctest.util.ClusterNode;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class DestructiveSysCacheTest extends SimpleSysCacheTest {

    private DataDoctorState state;
    private TestCase self = null;
    
    public void setUp() throws Throwable {
        self = createTestCase("DestructiveSysCacheTest");
        self.addTag(Tag.NOEMULATOR);

        if (self.excludeCase())
               return;
        
        super.init();

        state = DataDoctorState.getInstance();
        String cluster = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER);
        cli = new CLI(cluster + "-admin");
    }

    public void tearDown() throws Throwable {
        super.tearDown();
    }
    
    public void testSysCache() throws HoneycombTestException {
        
        if (self.excludeCase()) 
            return;
        
        Vector results = new Vector();
        
        // Store known data.
        Log.INFO("Storing some known data.");
        results = storeData();

        // Check state in syscache.
        checkSysCacheState(results);

        // Destroy system caches on nodes.
        destroyCaches();
        
        // Set populate_sys_cache_cycle to 1
        state.setValue(CLIState.SYSTEM_CACHE,1);
        waitForTaskCompletion("PopulateSysCache", null);
        // set populate_sys_cache_cycle back to default
        state.setDefault(CLIState.SYSTEM_CACHE);
      
        checkSysCacheState(results);
        
        // Delete the data I just stored.
        deleteData(results);
       
        // Destroy system caches on nodes.
        destroyCaches();
        
        // Set populate_sys_cache_cycle to 1
        state.setValue(CLIState.SYSTEM_CACHE,1);
        waitForTaskCompletion("PopulateSysCache", null);
        // set populate_sys_cache_cycle back to default
        state.setDefault(CLIState.SYSTEM_CACHE);
        
        // Check state in syscache.
        checkSysCacheState(results);
        
        self.postResult(true);
    }
   
    /* 
     * TODO: merge this waitForTaskCompletion code with BasicSloshing code, it's exactly the
     *       same just needs some smart engineering to make things clean... 
     */
    
    /**
     *  Function used to stat a given task for completion.
     * 
     *  Usage:
     *  
     *  First time you pass lastStatus pass as null and then the return value will be the
     *  value to use for the next iteration of completion.
     */
    public String waitForTaskCompletion(String taskname, String lastStatus) {
        String currentStatus = null; 
        boolean recoveryComplete = false;
        
        while (!recoveryComplete) {
            Log.INFO("Waiting for " + taskname + " to finish.");
            try {
                Thread.sleep(60000);
            } catch (InterruptedException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            try {
                currentStatus = readSysstat(taskname); 
                if (currentStatus != null) {
                    if ((lastStatus != null) && (lastStatus.equals(currentStatus)))
                            continue;
                    recoveryComplete = true;
                }
            } catch (Throwable e) {
              Log.ERROR("CLI access error:" + Log.stackTrace(e));
            }
        }
        
        Log.INFO(currentStatus);
        return currentStatus;
    }
   
    private static CLI cli = null;
    protected String readSysstat(String startsWith) throws Throwable {
        String line = null;
        BufferedReader br = cli.runCommand("sysstat -r");
        while ((line = br.readLine()) != null) {
            if (line.startsWith(startsWith)) 
                return line;
        }
        return null;
    }
    
    private void destroyCaches() throws HoneycombTestException {
        Log.INFO("Destroy system caches on all nodes");
        for (int node = 1; node <= numNodes; node++) {
            ClusterNode clusterNode = cm.getNode(node);
            clusterNode.removeFile("/data/?/MD_cache/system/*");
            clusterNode.killJVM("IO-SERVERS");
        }
    }
}

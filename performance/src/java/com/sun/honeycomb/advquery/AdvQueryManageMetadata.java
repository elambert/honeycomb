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



import java.net.InetAddress;
import java.util.Random;

import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.test.util.HoneycombTestException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AdvQueryManageMetadata {
    
    private static int objCount = 0;
    private static int objId;
    
    private static String hostname;
    private static String namespace = "";
      
    // Metadata values per instance
    private String uid = null;
                
    private long currentObjCount = 0;
        
    /*Metadata added similar to Blockstore MD */
    public static String MD_CLIENT_ATTR = "client";
    private static String clientAttr = MD_CLIENT_ATTR;
    private static String client = "";
    
    public static String MD_INSERT_TIME_ATTR = "insert_timestamp";
    private static String insertTimeAttr = MD_INSERT_TIME_ATTR;
    private long insertTime = 0;
        
    public static String MD_START_TIME_ATTR = "test_start_timestamp";
    private static String startTimeAttr = MD_START_TIME_ATTR;
    private static long startTime = 0;
    
    public static String MD_ITERATION_ATTR = "iteration";
    private static String iterationAttr = MD_ITERATION_ATTR;
    private Long iteration = 0L;
    
    public static String MD_TEST_ID_ATTR = "test_id";
    private static String testidAttr = MD_TEST_ID_ATTR;
    private static String testid = "";
       

    private AdvQueryMDPatternElement[] mdPatterns = null;
    private String[] mdPatternValues = null;
    public static String MD_TEST_TYPE_ATTR = "system.test.type_string";
    private String testType = "";
    private static boolean initialized = false;
      
    
    static {
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println("error: unable to determine local hostname.");
            System.err.println("setting hostname attribute to 'Default Host'");
            hostname = "Default Host";
        }
        startTime = new Long(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat("ddMMyy");
        Date date = new Date(startTime);
        String dateString = dateFormat.format(date);                
        testid = "AdvQuery-" + hostname + "-" + dateString +
                "." + startTime;
        client = hostname;
    }
    private AdvQueryManageMetadata() {
        
    }
    
    public static String getHostname() {
        return hostname;
    }
    
    /**
     *  Initialize the base AdvQuery metadata attribute names. These are used
     *  for each AdvQuery test.
     */
    public static boolean init(String ns) {
        if (! initialized) {
            namespace = ns;
            // Set the namespace if any
            if (namespace.length() > 0) {
                clientAttr = namespace + "." + clientAttr;
                testidAttr = namespace + "." + testidAttr;
                startTimeAttr = namespace + "." + startTimeAttr;
                iterationAttr = namespace + "." + iterationAttr;
                insertTimeAttr = namespace + "." + insertTimeAttr;           
            }              
        }
        initialized = true;
        return initialized;
    }

    /**
     *  Create a new instance of AdvQueryManageMetadata with the standard
     *  peformance metadata and the user-defined random metadata patterns.
     */
    public static AdvQueryManageMetadata createInstance(
            AdvQueryRandomUtil prng,
            AdvQueryRandomUtil dataPRNG,
            AdvQueryMDPatternElement[] mdPatterns,
            long startTime,
            long storeCount) throws HoneycombTestException {
                
        AdvQueryManageMetadata manageMD = new AdvQueryManageMetadata();
        manageMD.uid = null;

        synchronized (AdvQueryManageMetadata.class) {
            objId = objCount++;
        }
        
        /* Create standard AdvQuery test metadata */
        manageMD.insertTime = System.currentTimeMillis();
        manageMD.iteration = new Long(storeCount).longValue();
                       
        manageMD.uid = "AdvQuery-" + hostname + "." + objId + "-" +
                startTime + "." + storeCount;
                       
        if (mdPatterns != null) {
            manageMD.mdPatterns = 
                    new AdvQueryMDPatternElement[mdPatterns.length];
            manageMD.mdPatternValues = new String[mdPatterns.length];
            for (int i = 0; i < mdPatterns.length; i++) {
                manageMD.mdPatterns[i] = mdPatterns[i];
                manageMD.mdPatternValues[i] = 
                        manageMD.mdPatterns[i].getNextGeneratedPattern(prng);
             }
        }
        
        /* One additional metadata field to fit Josh's infrastructure*/
        manageMD.testType = manageMD.uid;
        
        return manageMD;
    }
            
    /**
     *  Add metadata.
     */
     public static AdvQueryManageMetadata addMetadata(NameValueRecord metadata,
            AdvQueryRandomUtil prng,
            AdvQueryRandomUtil dataPRNG,
            AdvQueryMDPatternElement[] mdPatterns,
            long startTime,
            long storeCount) throws HoneycombTestException {
        
        AdvQueryManageMetadata manageMD = createInstance(
            prng,
            dataPRNG,
            mdPatterns,
            startTime,
            storeCount);
        
        /* Add standard AdvQuery Metadata */
        metadata.put(clientAttr,manageMD.client);
        metadata.put(startTimeAttr, manageMD.startTime);
        metadata.put(insertTimeAttr,manageMD.insertTime);
        metadata.put(iterationAttr, manageMD.iteration);
        metadata.put(testidAttr, manageMD.testid);
                 
        if (manageMD.mdPatterns != null) {
            for (int i = 0; i < manageMD.mdPatterns.length; i++) {
                 metadata.put(manageMD.mdPatterns[i].getMetadataFieldName(),
                        manageMD.mdPatternValues[i]);
            }
        }
        
        /* One additional metadata field  - check if need this */
        metadata.put(MD_TEST_TYPE_ATTR, manageMD.testType);
        
        return manageMD;
    }

     /**
      * Returns the uid.
      */
     public String getUID() {
         return this.uid;
     }
     
     /**
      * Returns the current generated metadata values 
      */
     public String[] getGeneratedValues() {
         return this.mdPatternValues;
     }
     
     /**
      * Returns the test id.
      */
     public static String getTestid() {
         return testid;
     }
     
     /**
      * Returns test start time
      */
     public static long getStartTime() {
         return startTime;
     }
     
    /**
     * Converts the user specified metadata fields to string suitable
     * for output to the log.
     */
    public static String toString(NameValueRecord metadata,
            AdvQueryMDPatternElement[] mdPatterns) {
        
        
        StringBuffer userMetadata = new StringBuffer();
        
        synchronized (AdvQueryManageMetadata.class) {
            if (mdPatterns != null) {
                for (int i = 0; i < mdPatterns.length; i++) {
                    String name = mdPatterns[i].getMetadataFieldName();
                    String data = metadata.getAsString(name);
                    userMetadata.append("/MDField:" + name);
                    userMetadata.append(":" + data + ":");
                }
            }
        }
        
        return userMetadata.toString();
    }
        
}

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



package com.sun.honeycomb.layout;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.sun.honeycomb.disks.DiskId;

public class LayoutSimulation {
    private static final Logger log = 
    	                     Logger.getLogger(LayoutSimulation.class.getSimpleName());

    private static int MAX_NODE_COUNT = 16;
    
    private static int MAX_NODE = 116;
    private static int MIN_NODE = 101;
    
    // if disk reaches this % full, print warning in the log.
    private static final float UFS_PERF_WATERMARK = 95.0f; 

    public static final String PROP_NUM_NODES = "nodes";
    private static int _nodes = 16; // Defaults to 16 node setup
    
    public static final String PROP_DISK_RESERVED_CAP  = "disk.reserved.capacity";
    private int _capacity = 85; // % usable capacity for stores defaults to 85
    
    public static final int MAX_COLUMN_CHANGES = 2;
    

    private static final float fulldisk = 100.0f;

    private int numFailed = 0;
    private int numPassed = 0;
    
    // in random selection of disk failure cases this is the total 
    // number of random combinations generated.
    public int NUM_RANDOM_COMBINATIONS = 50;
    
    private int numberOfWorkers = 5;
    LayoutWorker[] threads = new LayoutWorker[numberOfWorkers];
    
    // Here you define the mapGenerators you want to test...
    private MapGenInterface[] mapGenerators = {new ArnoudMapGenerator()};
    
    public LayoutSimulation() { 
    	log.setUseParentHandlers(false);
    	log.addHandler(new ConsoleHandler());
    	log.getHandlers()[0].setFormatter(new MyFormatter());
    }
    
    public class MyFormatter extends Formatter {
		public String format(LogRecord record) {
			return record.getLevel().getName() + ": " + record.getMessage() + "\n";
		}
    }
   
    public boolean testSuite() {

        String prop = System.getProperty(PROP_NUM_NODES);
        if (prop != null)
            _nodes = new Integer(prop).intValue();

        if ( (_nodes != 8) && (_nodes !=16) ) 
            throw new RuntimeException("Number of nodes set by property '" + 
                                       PROP_NUM_NODES + 
                                       "' must be either 8 or 16, not '"
                                       + _nodes + "'.");

        prop = System.getProperty(PROP_DISK_RESERVED_CAP);
        if (prop != null)
            _capacity = new Integer(prop).intValue();

        if ((_capacity < 1) || (_capacity > 100))
            throw new RuntimeException("Capacity property must be set to a value between 1 and 100");

        boolean result = true;

        for (int i = 0; i < mapGenerators.length; i++) {
        	currentGen = mapGenerators[i];
            log.info("*** Test Information ***\n"+ 
            		 "\n\tMapGenerator:           " + currentGen.getClass().getSimpleName() + 
            		 "\n\tCluster Size:           " + _nodes +
            		 "\n\tReserved Capacity (%):  " + _capacity + 
            		 "\n\tUFS Perf Watermark (%): " + UFS_PERF_WATERMARK + "\n");
           
            log.info("*** Cluster state before any failures ***");
            printInitialState();

            log.info("*** All single disk case faults ***");
            result &= capacityTest(1, 
            		               DISK_FAILURE, 
            		               MODE_ALL,
            		               TestInfo.VERIFY_HYSTERESIS_PROPERTY | 
            		               TestInfo.VERIFY_COLUMN_CHANGE_PROPERTY);

            log.info("*** Random 3 disk case faults ***");
            result &= capacityTest(3, 
            		               DISK_FAILURE, 
            		               MODE_RANDOM,
            		               TestInfo.VERIFY_HYSTERESIS_PROPERTY);

            log.info("*** Random 5 disk case faults ***");
            result &= capacityTest(5,
            		               DISK_FAILURE, 
            		               MODE_RANDOM,
            		               TestInfo.VERIFY_HYSTERESIS_PROPERTY);
            
            log.info("*** All single node faults ***");
            result &= capacityTest(1, 
            		               NODE_FAILURE, 
            		               MODE_ALL,
            		               TestInfo.VERIFY_HYSTERESIS_PROPERTY | 
            		               TestInfo.VERIFY_COLUMN_CHANGE_PROPERTY);

            if (_nodes == 16) {
                log.info("*** Random 8-disk disk faults ***");
                result &= capacityTest(8, 
                		               DISK_FAILURE, 
                		               MODE_RANDOM,
                		               TestInfo.VERIFY_HYSTERESIS_PROPERTY);

                log.info("*** Random 9-disk disk faults ***");
                result &= capacityTest(9, 
                		               DISK_FAILURE, 
                		               MODE_RANDOM,
                		               TestInfo.VERIFY_HYSTERESIS_PROPERTY);
                
                log.info("*** Random 2 node failure ***");
                result &= capacityTest(2, 
                		               NODE_FAILURE, 
                		               MODE_RANDOM,
                		               TestInfo.VERIFY_HYSTERESIS_PROPERTY | 
                		               TestInfo.VERIFY_COLUMN_CHANGE_PROPERTY);
            }
        }

        return result;
    }
    
    private void printInitialState() { 
        Map initial = new TreeMap();
        initializeShares(initial);
        TestInfo ti = new TestInfo(_nodes, 0);
        runSimulation(ti, initial);
    }

    /*
     * Failure combination generating code...
     */
    public static final int MODE_ALL = 0;
    public static final int MODE_RANDOM = 1;
    
    public static final int DISK_FAILURE = 0;
    public static final int NODE_FAILURE = 1;

    private Random random = new Random(System.currentTimeMillis());

    private List calcCombination(int num, 
    		                     int faulttype, 
    		                     int faultmode, 
    		                     int options) {
        if (faulttype == DISK_FAILURE) {
            if (faultmode == MODE_ALL)
                return getCombinations(num, options);
            else if (faultmode == MODE_RANDOM)
                return getRandomCombinations(num,
                		                     NUM_RANDOM_COMBINATIONS, 
                		                     options);      
        }
        
        if (faulttype == NODE_FAILURE) {
            // simulate failure of each of the nodes
            return getRandomNodeFailure(num, _nodes, options);
        }
        
        return null;
    }
    
    private boolean capacityTest(int numFailures, 
    		                     int failureType, 
    		                     int failureMode,
    		                     int options) {
    	
        List combinations = calcCombination(numFailures, 
        		                            failureType,
        		                            failureMode,
        		                            options);
    	
        Map initial = new TreeMap();
        initializeShares(initial);

        numFailed = 0;
        numPassed = 0;
        
        for(int i = 0; i < threads.length; i++){
            threads[i] = new LayoutWorker();
            threads[i].start();
        }
                
        log.info("Combinations to test: " + combinations.size());
            
        int counter = 0;
        while(counter < combinations.size()) {
                
            for(int threadCounter = 0; threadCounter < threads.length; threadCounter++){
                if (counter >= combinations.size())
                    break;

                if (threads[threadCounter].isWaitingForWork()){
                    threads[threadCounter].startNewRun((TestInfo)combinations.get(counter),initial);
                    counter++;
                }
            }
                
            try {
                Thread.sleep(1000);
            } catch(Throwable t){
                // Ignore .. this is just for a small pause so the main thread doesn't consume much cpu...
            }
        }
            
        log.info("Waiting on last workers...");
            
        for(int threadCounter = 0; threadCounter < threads.length; threadCounter++){
            threads[threadCounter].stopWorker();
            try{
                threads[threadCounter].join();
            } catch (Throwable t) {
                log.severe("Issue stopping worker... " + t.getMessage());
            }
        }
            
        log.info("Failed: " + numFailed + " Passed: " + numPassed);

        log.info("Capacity test Done.");
        return (numFailed==0);
    }

    private List getRandomCombinations(int count, int howmany, int options) {
        
        if (count > 15) {
            throw new RuntimeException("failure count " + count);
        }
        
        List combinations = new LinkedList();
        
        while (combinations.size() < howmany) {

            TestInfo testInfo = new TestInfo(_nodes, options);
            
            while (testInfo.failedDisks.size() < count) { 
                int rand_node = (Math.abs(random.nextInt()))%_nodes;
                int rand_disk = (Math.abs(random.nextInt()))%TestInfo.DISK_COUNT;
                
                DiskId diskId = new DiskId(rand_node + LayoutConfig.BASE_NODE_ID, rand_disk);
                
                if (!testInfo.failedDisks.contains(diskId))
                    testInfo.addFailed(diskId);
            }           
            
            combinations.add(testInfo);     
        }
        
        return combinations;
    }
    
    private List getRandomNodeFailure(int count, int howmany, int options) {
        List combinations = new LinkedList();
        int failureCases = 0;
        
        while (combinations.size() < howmany) {

            TestInfo testInfo = new TestInfo(_nodes, options);    
            
            failureCases = 0;
            
            int rand_node = -1;
            int prev = -1;
            
            while (failureCases < count) { 

                while (rand_node == prev)
                    rand_node = (Math.abs(random.nextInt()))%_nodes;
                    
                DiskId diskId = null;
                
                diskId = new DiskId(rand_node + LayoutConfig.BASE_NODE_ID, 0);
                testInfo.addFailed(diskId);
                diskId = new DiskId(rand_node + LayoutConfig.BASE_NODE_ID, 1);
                testInfo.addFailed(diskId);
                diskId = new DiskId(rand_node + LayoutConfig.BASE_NODE_ID, 2);
                testInfo.addFailed(diskId);
                diskId = new DiskId(rand_node + LayoutConfig.BASE_NODE_ID, 3);
                testInfo.addFailed(diskId);
                
                prev = rand_node;
                failureCases++ ;
            }           
            
            combinations.add(testInfo);     
        }
        
        return combinations;
    }
        
    private List getCombinations(int count, int options) {

        if (count > 15) {
            throw new RuntimeException("failure count " + count);
        }
        
        List combinations = new LinkedList();
        List previous = null;
        if (count != 1) {
            previous = getCombinations(count - 1, options);
        }
        
        for (int node = 0; node < _nodes; node++) {
            for (int disk = 0; disk < TestInfo.DISK_COUNT; disk++) {
                DiskId diskId = new DiskId(node + LayoutConfig.BASE_NODE_ID, disk);
                if (count == 1) {
                    TestInfo testInfo = new TestInfo(_nodes,options);
                    testInfo.addFailed(diskId);
                    combinations.add(testInfo);
                } else {
                    if (previous == null) {
                        throw new RuntimeException("this should not happen");
                    }
                    boolean duplicate = true;
                    for(int i = 0; i < previous.size(); i++) {
                        TestInfo ti = (TestInfo) previous.get(i);
                        if (duplicate) {
                            if (ti.getLast().equals(diskId)) {
                                duplicate = false;
                            }
                            continue; 
                        }
                        TestInfo found = ti.partialClone();
                        found.addFailed(diskId);
                        combinations.add(found);
                    }
                }
            }
        }
        return combinations;
    }
    
    private boolean runSimulation(TestInfo ti, Map initial) {
        Stats stats = simulateTest(ti);
        
        generateStats(stats, initial, ti);
        
        log.info(stats.getStatsString());
        
        if (stats.hasErrors()) 
        	log.severe("Errors Summary: " + stats.getErrorSummary() + "\n");
        
        if (stats.hasWarnings())
        	log.warning("Warnings Summary: " + stats.getWarningSummary() + "\n");
        	
        return !stats.hasErrors();
    }
        
    private void generateStats(Stats stats, Map initial, TestInfo ti) {
        int maxInitialShare = maxShare(initial);
        Map shares = stats.getTreeMap(); 

        String msg = "*** Failing: " + ti + "***\n";
      
        /* 
         * Generate headers for Stats table
         */
        for (int i = 0; i < TestInfo.DISK_COUNT; i++)
        	msg += "DiskId\t%Full\tFragsMV\t";
        
        msg+="\n";
        
        for (int node = MIN_NODE; node <= MAX_NODE; node++) {
        	for (int disk = 0; disk < TestInfo.DISK_COUNT; disk++) {
        		DiskId diskId = new DiskId(node,disk);
        		shares.get(diskId);
        	
        		Integer share = (Integer) shares.get(diskId);
                Integer transfer = (Integer) initial.get(diskId);

                int transferCount = share.intValue() - transfer.intValue();
                double allocation = share.floatValue() / ((float) maxInitialShare) * _capacity;
            
                if (allocation > fulldisk) {
                	msg += " " + diskId.toStringShort() + "\t " 
                	           + (int)allocation + " E\t " 
                	           + transferCount + "\t "; 
                	
                	stats.addError("Disk " + diskId.toStringShort() + 
                			       " overflowed disk capacity at: " + 
                			       (int)allocation);
                } else if (allocation > UFS_PERF_WATERMARK) {
                	msg += " " + diskId.toStringShort() + "\t " 
                	           + (int)allocation + " W\t " 
                	           + transferCount + "\t "; 
                	
                	stats.addWarning("Disk " + diskId.toStringShort() +
                			         " over UFS performance watermark at: " 
                			         + (int)allocation);
                } else
                	msg += " " + diskId.toStringShort() 
                	           + "\t " + (int)allocation 
                	           + "\t " + transferCount + "\t ";
        	}
        	msg+="\n";
        }
      
        /*
         * TODO: more stats can be generated if we can figure out what is 
         *       important
         */
        float optimal_share = (LayoutClient.NUM_MAP_IDS * TestInfo.FRAG_COUNT) / 
                              ((_nodes * TestInfo.DISK_COUNT) - ti.getFailedDisks().size());
        float maxShare = stats.getMaxShare();
        float minShare = stats.getMinShare();
      
        msg+="\n*** Additional Stats ***\n";
        msg+="Max share offset:                " + 
             ((maxShare-minShare)/maxShare)*100 + "%\n";
        msg+="Share offset from optimal case: -" + 
             ((optimal_share - minShare)/optimal_share) * 100 + "% +" +
             ((maxShare - optimal_share)/optimal_share) * 100 + "%\n";

      	msg+="Single couple count:             " + stats.getSingleCouplecount() + "\n";
        msg+="Double couple count:             " + stats.getDoubleCouplecount() + "\n";
       
        msg+="\n*** Legend ***\n";
        msg+="W - Warning this disk is nearing the UFS watermark.\n";
        msg+="E - Error this disk is over the available disk space.\n";

        stats.setStatsString(msg);
    }

    /**********************************************************************
     * Return a list of all layouts based on a diskmask.
     **/
    private List getLayouts(List failedDisks) {
        
        TestLayoutClient tlc = new TestLayoutClient();
        LinkedList layouts = new LinkedList();
        DiskMask mask = new DiskMask();

        mask.setAvailable(0, _nodes*TestInfo.DISK_COUNT, true); // set em all online
        mask.setEnabled(0, _nodes*TestInfo.DISK_COUNT, true); // set em all online

        // turn off the ones that are offline
        for (int i = 0; i < failedDisks.size(); i++) {
            DiskId disk = (DiskId) failedDisks.get(i);
            mask.setOffline(disk);
        }
        
        // compute layouts
        for (int i = 0; i < tlc.NUM_MAP_IDS; i++) {
            layouts.add(tlc.getLayout(i, mask));
        }
        
        return layouts;
    }

    private void initializeShares(Map shares) {

        for (int node = 0; node < _nodes; node++) {
            for (int disk = 0; disk < TestInfo.DISK_COUNT; disk++) {
                DiskId diskId = new DiskId(node + LayoutConfig.BASE_NODE_ID, disk);
                shares.put(diskId, new Integer(0));
            }
        }
        
        LinkedList failedDisks = new LinkedList();
        
        for (int node = _nodes; node < MAX_NODE_COUNT; node++){
            for (int disk = 0; disk < TestInfo.DISK_COUNT; disk++) {
                DiskId diskId = new DiskId(node + LayoutConfig.BASE_NODE_ID, disk);
                shares.put(diskId, new Integer(-1));
                failedDisks.add(diskId);
            }
        }
        
        List layouts = getLayouts(failedDisks);
        
        for (int i = 0; i < layouts.size(); i++) {
            Layout layout = (Layout) layouts.get(i);
            
            for (int frag = 0; frag < TestInfo.FRAG_COUNT; frag++) {
                DiskId diskId = layout.getDiskId(frag);
                Integer share = (Integer) shares.get(diskId);
                shares.put(diskId, new Integer(share.intValue() + 1));
            }
        }
        
        
    }

    private int maxShare(Map shares) {
        int max = 0;
        for (Iterator it = shares.values().iterator(); it.hasNext(); ) {
            Integer share = (Integer) it.next();
            // Skip disabled disks...
            if (share.intValue() != -1) {
                if (share.intValue() > max) { // failed disk
                    max = share.intValue();
                }
            }
        }
        return max;
    }
    
    private int minShare(Map shares) {
        int min = Integer.MAX_VALUE;
        for (Iterator it = shares.values().iterator(); it.hasNext(); ) {
            Integer share = (Integer) it.next();
            // Skip disabled disks...
            if (share.intValue() != -1) {
                if (share.intValue() < min) { // failed disk
                    min = share.intValue();
                }
            }
        }
        return min;
    }
    
    private Stats simulateTest(TestInfo testInfo) {
        Stats result = new Stats();
    	
        TreeMap shares = new TreeMap();
        initializeShares(shares);
        LinkedList healed = new LinkedList();

        List beforeFailure = getLayouts(healed);
        healed.addAll(testInfo.getFailedDisks());
        
        List afterFailure = getLayouts(healed);
        
        int max = 0;
        int min = Integer.MAX_VALUE;
        // Accumulate shares
        int countSingleCouples = 0;
        int countDoubleCouples = 0;

        /*
         * flipped failed disk scenario used to validate hysteresis property.
         */
        TestInfo flippedTestInfo = testInfo.flip();
        List flippedLayouts = getLayouts(flippedTestInfo.getFailedDisks());
        
        for (int i = 0; i < LayoutClient.NUM_MAP_IDS; i++) {
            Layout before = (Layout) beforeFailure.get(i);
            Layout after = (Layout) afterFailure.get(i);
    
            /*
        	 * Hystersis check for current layout.
        	 */
            if (testInfo.optionChosen(TestInfo.VERIFY_HYSTERESIS_PROPERTY)) { 
            	Layout flipped = (Layout) flippedLayouts.get(i);
            	if (!flipped.equals(after)) 
            		result.addError("Hysteresis property not validated for mapID: " + i 
            				        + " when failing  " + testInfo + " and " + flippedTestInfo);
            }
            
            /*
             * Couple counting code here.
             */
            int cnt = 0;
            for (int node = MIN_NODE; node < MAX_NODE; node++) {
            	if (after.containsNodeCount(node) > 1)  {
            		countSingleCouples++;
            		cnt++;
            	}
            }
            
            if (cnt > 2) 
            	countDoubleCouples++;
           
            int diffCount = 0; 
            for (int frag = 0; frag < TestInfo.FRAG_COUNT; frag++) {
               
            	DiskId transferTo = after.getDiskId(frag);
                DiskId transferFrom = before.getDiskId(frag);
       
                if (!transferTo.equals(transferFrom)) {
            
                	Integer share = (Integer) shares.get(transferTo);
                    shares.put(transferTo, new Integer(share.intValue() + 1));
                
                    share = (Integer) shares.get(transferFrom);
                    shares.put(transferFrom, new Integer(share.intValue() - 1));
                   
                    if (testInfo.optionChosen(TestInfo.VERIFY_COLUMN_CHANGE_PROPERTY)) { 
	                    /*
	                     * Verify that a DiskMask change never affects the current 
	                     * layout by more than two columns. 
	                     */
	                    if (!before.getDiskId(frag).equals(after.getDiskId(frag))) {
	                        diffCount++;
	                        if (diffCount > MAX_COLUMN_CHANGES) {
	                            result.addError("More than 2 column transitions for MapId " + 
	                            		        i + " " + testInfo);
	                        }
	                    }
                    }
                }
              
                if (((Integer)shares.get(transferTo)).intValue() > max)
                    max = ((Integer)shares.get(transferTo)).intValue();
                
              	if (((Integer)shares.get(transferTo)).intValue() < min)
               		min = ((Integer)shares.get(transferTo)).intValue();
                
            }
        }
        
        // health check
        // Verify all of the disks that did fail are now empty.
        for(Iterator it = testInfo.getFailedDisks().iterator(); it.hasNext(); ) {
            DiskId diskId = (DiskId) it.next();
            Integer share = (Integer) shares.get(diskId);
            if (share.intValue() != 0) {
                throw new RuntimeException("Share of " + diskId + " " + share.intValue());
            }
        }
        
        // also verify that any disks that didn't fail are not having data shifted
        // out of them
      
        // Return stats collected during this simulation
        result.setTreeMap(shares);
        result.setMaxShare(max);
        result.setMinShare(min);
        result.setSingleCouplecount(countSingleCouples);
        result.setDoubleCouplecount(countDoubleCouples);
        
        return result;
    }

    /**********************************************************************
     * Modify behaviour of LayoutClient to suit this test.
     **/
    public MapGenInterface currentGen = null;
    private class TestLayoutClient extends LayoutClient {

        public TestLayoutClient() {
            super();
            
            if (currentGen != null)
                mapGen = currentGen;
        }

        public Layout getLayout(int mapId, DiskMask mask) {
            return super.getLayout(mapId, mask);
        }

        public void printLayoutMap(int mapId) {
            System.out.println("MapId: " + mapId);
            for (int row = 0; row < MAP_ROWS; row++) {
                for (int col = 0; col < FRAGS; col++) {
                    int[] entry = mapGen.getMapEntry(mapId, row, col);
                    int nodeId = LayoutConfig.BASE_NODE_ID + entry[ENTRY_NODE];
                    int diskIndex = entry[ENTRY_DISK];
                    System.out.print(nodeId + ":" + diskIndex + " ");
                }
                System.out.println("");
            }
            System.out.println("");
        }
    }

    /**********************************************************************
     * A container to hold info about a single instance of test.
     **/
    private static class TestInfo {

        static final int FRAG_COUNT = LayoutConfig.FRAGS_PER_OBJ;
        static final int DISK_COUNT = LayoutConfig.DISKS_PER_NODE;

        private int NODE_COUNT = -1;

        private LinkedList failedDisks;
        
        public static final int VERIFY_COLUMN_CHANGE_PROPERTY = 1;
        public static final int VERIFY_HYSTERESIS_PROPERTY    = 2;
        
        private int _options = 0;
        
        TestInfo(int numNodes, int options) {
            NODE_COUNT=numNodes;
            this.failedDisks = new LinkedList();
            _options = options;
        }

        public boolean optionChosen(int option) { 
        	return ((_options & option) == option);
        }
        /* 
         * Flip the disk failure sequence. Used mainly for the validation of the
         * hysteresis property.
         */
        public TestInfo flip() { 
            TestInfo ti = new TestInfo(NODE_COUNT, _options);

            Iterator iter = failedDisks.iterator();

            while (iter.hasNext()) {
                ti.addFailed((DiskId)iter.next());
            }

            return ti;
        }

        public void addFailed(DiskId diskId) {
            ((LinkedList) failedDisks).addFirst(diskId);
        }

        public boolean contains(DiskId diskId) {
            return failedDisks.contains(diskId);
        }

        public List getFailedDisks() {
            return failedDisks;
        }

        public DiskId getLast() {
            return (DiskId) ((LinkedList) failedDisks).getLast();
        }

        public String toString() {
            String msg = "failed disks: ";
            if  (failedDisks.size() == 0) {
                msg += "(none)";
            }
            for (int i = 0; i < failedDisks.size(); i++) {
                msg += ((DiskId) failedDisks.get(i)).toStringShort() + " ";
            }
            return msg;
        }

        public TestInfo partialClone() {
            TestInfo ti = new TestInfo(_nodes,_options);
            ti.failedDisks.addAll(this.failedDisks);
            return ti;
        }
    }
    
    /**
     * Parallelize the testing work for verifying capacities after failures.
     *
     */
    public class LayoutWorker extends Thread {
        
        TestInfo test = null;
        Map initial = null;
        Exception exception = null;
        
        boolean timeToRun = true;
        
        public void run() { 
            while (timeToRun){
                
                if (test != null) {
                    try {
                        if (runSimulation(test,initial))
                            numPassed++;
                        else
                            numFailed++;
                        
                    } catch (Exception e) {
                        log.severe("Exception " + e);
                        numFailed++;
                    }
                }
                
                test = null;
                waitForNotification();
            }
        }
        
        public void stopWorker() {
            timeToRun = false;
            synchronized (this) {
                notify();
            }
        }

        private boolean waitingForWork = false;
        
        public boolean isWaitingForWork(){
            return waitingForWork;
        }
        
        public void waitForNotification(){
            try {
                waitingForWork = true;
                synchronized (this) {
                    wait(1000);
                }
            } catch (InterruptedException e) {
                // Ignore.. 
            }
        }
        
        public void startNewRun(TestInfo test, Map initial) {
            this.waitingForWork = false;
            this.initial = initial;
            this.test = test;
            synchronized (this) {
                notify();
            }
        }

        public Exception getException() {
            return exception;
        }

        public void setException(Exception exception) {
            this.exception = exception;
        }

        public void setInitial(Map initial) {
            this.initial = initial;
        }
    }
    
    private class Stats { 
    	private TreeMap treeMap = null;
    	private int singleCouplecount = 0;
    	private int doubleCouplecount = 0;
 
    	private int maxShare = 0;
    	private int minShare = 0;
    	
    	private ArrayList errors = new ArrayList();
    	private ArrayList warns = new ArrayList();
    	
    	private String stats = null;
    	
		public int getDoubleCouplecount() { return doubleCouplecount; }
		public void setDoubleCouplecount(int doubleCouplecount) { this.doubleCouplecount = doubleCouplecount; }
		
		public int getMaxShare() { return maxShare; }
		public void setMaxShare(int maxShare) { this.maxShare = maxShare; }
		
		public int getMinShare() { return minShare; }
		public void setMinShare(int minShare) { this.minShare = minShare; }
		
		public int getSingleCouplecount() { return singleCouplecount; }
		public void setSingleCouplecount(int singleCouplecount) { this.singleCouplecount = singleCouplecount; }
		
		public TreeMap getTreeMap() { return treeMap; }
		public void setTreeMap(TreeMap treeMap) { this.treeMap = treeMap; }
		
		public void addError(String errorMessage) {  errors.add(errorMessage); }
		
		public String getErrorSummary() { 
			StringBuffer result = new StringBuffer();
		
			for (int i = 0; i < errors.size(); i++) { 
				result.append("\n" + errors.get(i));
			}
			
			return result.toString();
		}

		public String getWarningSummary() { 
			StringBuffer result = new StringBuffer();
		
			for (int i = 0; i < warns.size(); i++) { 
				result.append("\n" + warns.get(i));
			}
			
			return result.toString();
		}
	
		public void setStatsString(String stats) { this.stats = stats; } 
		public String getStatsString() { return stats; } 
		public void addWarning(String warningMessage) {  warns.add(warningMessage); }
		
		public boolean hasErrors() { return (errors.size()!=0); }
		public boolean hasWarnings() { return (warns.size()!=0); }
    }
}

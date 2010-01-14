package com.sun.honeycomb.layout;
import java.util.Random;
import com.sun.honeycomb.disks.DiskId;



/* This java class simulates the disk usages in Honeycomb */
/*  Under trunk/unit_tests/src/java/com/sun/honeycomb/layout */
/* ./run.sh CapacitySimulator <algorithm> <numObj> <smallestSize> <largestSize> */
/*   <numFailedDisks> <numFailedNodes><retryLimit><iterations>*/
/*  It will print out -- how many times each mapId get selected, max, min and diff in % */
/*                    -- how many obj fragments stored on each disk, max, min and diff in % */   
/*                    -- how much data stored on each disk, max, min, diff in %, mean and standard deviation */  

/*  It can also simulate the case of disk and node failure */
/*  It can also handle multi-trunk case */
/*  It will retry another MapId if one disk is 80% full,retryLimit=0 means no retry, which is current design.*/

public class CapacitySimulator implements Runnable {


    private static final int NUM_MAP_IDS = 10000;  /* # of different MapID*/
    private static final int NUM_NODES = 16;       /* # of nodes*/
    private static final int DISKS_PER_NODE = 4;   /* # of disks per node*/ 
    private static final int FRAG = 7;             /* # of total fragments*/
    private static final int REDUNDANCY = 2;       /* # of redundancy fragments*/
    private static final int BASE_NODE_ID = 101;   /*101 is node 0*/ 
    private static final double pReset = 0;         /* prob. of resetting MapId */
    private static final double CAPACITY_LIMIT = 0.8; 
    private static final double CAPACITY_KB = 449128448;	
    private static final long USED_CAP_DISK0 = 0;  /* overhead on disk 0 for an empty cluster: 15071232;*/
    private static final long USED_CAP_DISK1 = 0;  /* overhead on disk 1-3 for an empty cluster: 4567040;*/
    private static final long trunkSize = 1048576;   /*1 GB*/

    private static int algorithm;            /*1: RS: Random Selection */
                                             /*2: RSSS: Random Starting Sequential Selection*/
                                             /*3: FSSS: Fixed Starting Sequential Selection*/
    private static long numObj;              /* # of objects we will store*/
    private static long smallestSize;        /* smallest obj size in kilo bytes*/
    private static long largestSize;         /* largest obj size in kilo bytes*/
    private static int iterations;          /* iterations the simulator runs*/
    private static int numFailedDisks;
    private static int numFailedNodes;
    private static int retryLimit;           /*=0, if there is no retry*/
 
    private static int[] nextMapId = new int[NUM_NODES];
       
    public static void main(String[] args) {

        if (args.length !=8){
            System.err.println("Error: insufficient arguments!");
            System.err.println ("Usage: CapacitySimulator <algorithm> <numObj> <smallestSize><largestSize> <numFailedDisks> <numFailedNodes><retryLimit><iterations>"); 
            System.exit(1);
        }

        int algorithm = Integer.parseInt(args[0]);
        long numObj = Long.parseLong(args[1]);
        long smallestSize = Long.parseLong(args[2]);
        long largestSize = Long.parseLong(args[3]);
        int numFailedDisks = Integer.parseInt(args[4]);
        int numFailedNodes = Integer.parseInt(args[5]);
        int retryLimit = Integer.parseInt(args[6]);
        int iterations = Integer.parseInt(args[7]); 
        CapacitySimulator capacitySimulator = new CapacitySimulator(algorithm,numObj,smallestSize,
                                                  largestSize,numFailedDisks, numFailedNodes,retryLimit,iterations);
        capacitySimulator.run();
    }

    public CapacitySimulator(int algorithm,long numObj,long smallestSize,
                            long largestSize,int numFailedDisks, int numFailedNodes,int retryLimit,int iterations){

        this.algorithm = algorithm;
        this.numObj = numObj;
        this.smallestSize = smallestSize;
        this.largestSize = largestSize;
        this.numFailedDisks = numFailedDisks;
        this.numFailedNodes = numFailedNodes;
        this.retryLimit = retryLimit;
        this.iterations = iterations;
    }

    public void run() {


        float meanMaxMap = 0;
        float meanMinMap = 0;
        float meanMaxByte = 0;
        float meanMinByte = 0;
        long meanMeanByte = 0;
        double meanSdByte = 0;
        long meanObj = 0;
        int meanDiskFull = 0;
 
        DiskMask mask = new DiskMask();

        LayoutClient lc = new LayoutClient();
        int onlineDisks = (NUM_NODES-numFailedNodes)*DISKS_PER_NODE-numFailedDisks;

        for (int k = 0; k < iterations; k++) {

            int[] mapIdCount = new int[NUM_MAP_IDS];
            int[][] objCount = new int[NUM_NODES][DISKS_PER_NODE];
            long[][] kiloByteCount =  new long[NUM_NODES][DISKS_PER_NODE];
            long meanKiloByteCount = 0;
            double sdKiloByteCount = 0;
            long totalObj = 0;
            int numDiskFull = 0; 
 
            float maxMapIdCount = 0;
            float minMapIdCount = Integer.MAX_VALUE;

            float maxObjCount = 0;
            float minObjCount = Integer.MAX_VALUE;

            float maxKiloByteCount = 0;
            float minKiloByteCount = Integer.MAX_VALUE;

            System.out.println("------------------------------------------------------------------");
            System.out.println("Iteration: " + (k + 1));

            mask.setAvailable(0, NUM_NODES*DISKS_PER_NODE, true); /* set them all online */
            mask.setEnabled(0, NUM_NODES*DISKS_PER_NODE, true);    /* set them all online */

            for (int i = 0; i < numFailedDisks; i++) {
                DiskId failedDisk = new DiskId((int)(BASE_NODE_ID + NUM_NODES*Math.random()), 
                                               (int) (DISKS_PER_NODE*Math.random()));
                mask.setOffline(failedDisk);
                System.out.println("Failed Disk: " + failedDisk.nodeId() + ":" +  failedDisk.diskIndex());  
            }

            for (int i = 0; i < numFailedNodes; i++) {
                int failedNode = (int) (BASE_NODE_ID + NUM_NODES*Math.random()); 
                System.out.println("Failed Node: " + failedNode); 
                for (int j = 0; j < DISKS_PER_NODE; j++) {
                    DiskId failedDisk = new DiskId(failedNode,j);
                    mask.setOffline(failedDisk);
                }
            }

            for (int i = 0; i < NUM_NODES; i++) {
                kiloByteCount[i][0] = USED_CAP_DISK0; 
                for (int j = 1; j < DISKS_PER_NODE; j++)
                    kiloByteCount[i][j] = USED_CAP_DISK1;
            }

            for (int i = 0; i < NUM_NODES; i++) {
                switch (algorithm) {

                    case 1: 
                       nextMapId[i] = (int) (Math.random()*NUM_MAP_IDS); 
                       break;
                    case 2: 
                       nextMapId[i] = (int) (Math.random()*NUM_MAP_IDS); 
                       break;
                    case 3: 
                       nextMapId[i] = (NUM_MAP_IDS/NUM_NODES)*i; 
                       break;
                    default: 
                       System.err.println("Error: Invalid Algorithm!"); 
                       System.exit(1);

                }
            }

            for (long i = 0; i < numObj; i++) {

                int nodes = (int) Math.random()* NUM_NODES; /*Node will be randomly selected*/
                long objSize = (long) (Math.random()*(largestSize-smallestSize)+smallestSize);
                long fragSize;
                boolean canBeStored = false;
                int numRetry = 0;
                int initialMapId;
                int currentMapId; 
      
                initialMapId = generateMapId(nodes);

                /* First need to check if there is enough capacity */ 

                while (numRetry <= retryLimit && !canBeStored) {
   
                    currentMapId = initialMapId;
                    boolean enoughCapacity = true; 

     	            for (long trunk = 0; trunk <= ((objSize-0.01)/trunkSize); trunk++) {

                        if ((trunk ==  (long) ((objSize-0.01)/trunkSize)) && ((objSize % trunkSize)!=0)) 
                            fragSize = (long) ((objSize % trunkSize)/(FRAG-REDUNDANCY)+1);
                        else 
                            fragSize = (long) (trunkSize/(FRAG-REDUNDANCY)+1);

                        Layout layout = lc.getLayout(currentMapId,mask);

                        for (int fragid = 0; fragid < FRAG; fragid++) {  

	                    DiskId diskid = (DiskId) layout.get(fragid);
                            int nodeid = diskid.nodeId() - BASE_NODE_ID;
                            int diskindex = diskid.diskIndex();
                            if(kiloByteCount[nodeid][diskindex] > (CAPACITY_LIMIT*CAPACITY_KB)) {
                                enoughCapacity = false; 
              
                                System.out.println("While storing " + i + " th object using mapid: " + currentMapId);
                                System.out.println("Find disk " + (BASE_NODE_ID+nodeid) + ":" + diskindex + " has used "
                                       + (float) kiloByteCount[nodeid][diskindex]/1024/1024
                                       + " GB; " + (float) (kiloByteCount[nodeid][diskindex]*100/CAPACITY_KB) 
                                       + " % of its capacity reached");
             
                                if (retryLimit > 0) {
             
                                   System.out.println("Want to do " + (numRetry+1)+" th retry. Search for another MapId!");
             
                                   initialMapId = generateMapId(nodes); /*(currentMapId + 1)%NUM_MAP_IDS; */
                                }
                                break; 
                            }
                        }   

                        if (!enoughCapacity) {
                            numRetry++;
                            break;
                        }
                        currentMapId = (currentMapId + 1)%NUM_MAP_IDS;
                    }
                    if (enoughCapacity) canBeStored = true;
                      
                }       
  
                /* Then store obj if there is enough capacity*/


                if (canBeStored) {

                    currentMapId = initialMapId; 

     	            for (long trunk = 0; trunk <= ((objSize-0.01)/trunkSize); trunk++) {

                        if ((trunk ==  (long) ((objSize-0.01)/trunkSize)) && ((objSize % trunkSize)!=0)) 
                            fragSize = (long) ((objSize % trunkSize)/(FRAG-REDUNDANCY)+1);
                        else fragSize = (long) (trunkSize/(FRAG-REDUNDANCY)+1);
 
                        Layout layout = lc.getLayout(currentMapId,mask);
                        mapIdCount[currentMapId]++;

                        for (int fragid = 0; fragid < FRAG; fragid++) {  

	                    DiskId diskid = (DiskId) layout.get(fragid);
                            int nodeid = diskid.nodeId() - BASE_NODE_ID;
                            int diskindex = diskid.diskIndex();
                            objCount[nodeid][diskindex]++;
                            kiloByteCount[nodeid][diskindex] = kiloByteCount[nodeid][diskindex] + fragSize;
                        }
                        currentMapId = (currentMapId + 1)%NUM_MAP_IDS;
                    }
                    totalObj = i;   
		}
                else {
                    if (retryLimit>0) System.out.println("Have tried  " + (numRetry-1) + " times");

                    System.out.println("Cannot find enough capacity. Stop test!");
                    totalObj = i-1;
                    break;
                }
            }

            for (int i = 0; i < NUM_MAP_IDS; i++) {

                /*System.out.println("MapID: " + i + " occurred " + mapIdCount[i]); */

                if (maxMapIdCount < mapIdCount[i]) maxMapIdCount = mapIdCount[i];
                if (minMapIdCount > mapIdCount[i]) minMapIdCount = mapIdCount[i]; 
            }
  
            System.out.println("Disk Usage Summary:");

            for (int i = 0; i < NUM_NODES; i++) {
                for (int j = 0; j < DISKS_PER_NODE; j++) {
                    if (kiloByteCount[i][j] > (CAPACITY_LIMIT*CAPACITY_KB)) numDiskFull++;
                    System.out.println("node: " + (BASE_NODE_ID+i) + " disk: " + j + " stores "+ objCount[i][j] 
                                        + " objects, used " + (float) kiloByteCount[i][j]/1024/1024 + " GB capacity");
		    
                    meanKiloByteCount = meanKiloByteCount + kiloByteCount[i][j];
	            sdKiloByteCount = sdKiloByteCount + kiloByteCount[i][j]*kiloByteCount[i][j];

                    if (maxObjCount < objCount[i][j])
                        maxObjCount = objCount[i][j];
                    if (minObjCount > objCount[i][j] && objCount[i][j] > 0 )
                        minObjCount = objCount[i][j];
                    if (maxKiloByteCount < kiloByteCount[i][j])
                        maxKiloByteCount = kiloByteCount[i][j];
                    if (minKiloByteCount > kiloByteCount[i][j] && kiloByteCount[i][j] > 0)
 			minKiloByteCount = kiloByteCount[i][j];
                }

            }
           
            System.out.println("");
            System.out.println("There is (are) " + numDiskFull + " disk(s) exceeding " + "CAPACITY_LIMIT");
            System.out.println("MaxMapIdCount: " + (int) maxMapIdCount);
            System.out.println("MinMapIdCount: " + (int) minMapIdCount);
            System.out.println("---- Diff in %: " + ((maxMapIdCount - minMapIdCount)/maxMapIdCount )*100); 
            /* 
            System.out.println("MaxObjFrag: " + (int) maxObjCount + "; MinObjFrag: " + (int) minObjCount
                               + "; Diff in %: " + ((maxObjCount - minObjCount)/maxObjCount)*100);
            */ 
            System.out.println("Most Full Disk: " + maxKiloByteCount/1024/1024 + " GB");
            System.out.println("Least Full Disk: " + minKiloByteCount/1024/1024 + " GB"); 
            System.out.println("----Diff in %: " + ((maxKiloByteCount - minKiloByteCount)/maxKiloByteCount )*100); 
	    System.out.println("Mean (Used capacity of online disks) = " + (float) meanKiloByteCount/onlineDisks/1024/1024 
                                + " GB");
            System.out.println("Standard Deviation = " 
				+ Math.sqrt(sdKiloByteCount/onlineDisks
                                - (meanKiloByteCount/onlineDisks)*(meanKiloByteCount/onlineDisks))/1024/1024 + " GB");
            System.out.println("Total data stored = " + (float) meanKiloByteCount/1024/1024 + " GB; " 
                                + (float) (meanKiloByteCount/CAPACITY_KB/NUM_NODES/DISKS_PER_NODE*100) + " % of whole capacity"); 
            System.out.println("Total objects stored: " + totalObj);

            meanMaxMap =  meanMaxMap + maxMapIdCount;
            meanMinMap =  meanMinMap + minMapIdCount;
            meanMaxByte =  meanMaxByte + maxKiloByteCount;
            meanMinByte =  meanMinByte + minKiloByteCount;
            meanMeanByte =  meanMeanByte + meanKiloByteCount;
            meanSdByte =  meanSdByte + sdKiloByteCount;
            meanObj = meanObj + totalObj;
            meanDiskFull = meanDiskFull + numDiskFull;
        }
       
        System.out.println("");
        System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
        System.out.println("");
        System.out.println("SIMULATION SUMMARY:");
        System.out.println("Parameters:");
        switch (algorithm) {
            case 1: 
               System.out.println("MapID Selection Algorithm: 1-Random Selection "); 
               break;
            case 2: 
               System.out.println("MapID Selection Algorithm: 2-Random Starting Sequential Selection"); 
               break;
            case 3: 
               System.out.println("MapID Selection Algorithm: 3-Fixed Starting Sequential Selection"); 
               break;
        }

        System.out.println("Total iterations: " + iterations);
        System.out.println("Number of objs to store in each iteration = " + numObj);
        System.out.println("Min Obj Size = "
                            + smallestSize +" kB; Max Obj Size = "
                            + largestSize +" kB");
        System.out.println("Number of failed disks: " + numFailedDisks + "; Number of failed nodes = " + numFailedNodes);
        System.out.println("Retry times: " + retryLimit); 

        System.out.println("");
        System.out.println("Statistics (average over " + iterations + " iterations):");
        System.out.println("MaxMapIdCount: " +(int)meanMaxMap/iterations);
        System.out.println("MinMapIdCount: " + (int) meanMinMap/iterations);
        System.out.println("---- Diff in %: " + ((meanMaxMap - meanMinMap)/meanMaxMap )*100);

        System.out.println("There is (are) " + (float) meanDiskFull/iterations 
                            + " disk(s) exceeding " + "CAPACITY_LIMIT");
        System.out.println("Most Full Disk (online): " + meanMaxByte/iterations/1024/1024+ " GB");
        System.out.println("Least Full Disk (online): "
                            + meanMinByte/iterations/1024/1024 + " GB");
        System.out.println("----Diff in %: " + (( meanMaxByte - meanMinByte )/meanMaxByte )*100);
        System.out.println("Mean  (Used capacity of online disks) = " + (float)  meanMeanByte/iterations/onlineDisks/1024/1024 + " GB");
        System.out.println("Standard Deviation (online disks) = "
                            + Math.sqrt(meanSdByte/iterations/onlineDisks
                            - (meanMeanByte/iterations/onlineDisks)*(meanMeanByte/iterations/onlineDisks))/1024/1024 + " GB");
        System.out.println("Total data stored = " + (float) meanMeanByte/iterations/1024/1024 + " GB; "
                                + (float) (meanMeanByte/CAPACITY_KB/NUM_NODES/DISKS_PER_NODE/iterations*100) + " % of whole capacity");
        System.out.println("Total objects stored = " +  meanObj/iterations);

    }


    private static int generateMapId(int node) {
        if(Math.random() < pReset) {
            switch (algorithm) {
                case 1: 
                   nextMapId[node] = (int) (Math.random()*NUM_MAP_IDS);
                   break;
                case 2: 
                   nextMapId[node] = (int) (Math.random()*NUM_MAP_IDS); 
                   break;
                case 3: 
                   nextMapId[node] = (NUM_MAP_IDS/NUM_NODES)*node; 
                   break;
            }
        }
        else {
            switch (algorithm) {
                case 1: 
                   nextMapId[node] = (int) (Math.random()*NUM_MAP_IDS); 
                   break;
                case 2: 
                   nextMapId[node] = (nextMapId[node]+1)%NUM_MAP_IDS; 
                   break;
                case 3: 
                   nextMapId[node] = (nextMapId[node]+1)%NUM_MAP_IDS; 
                   break;                 
            }
        }
        return (nextMapId[node]);
    }
}


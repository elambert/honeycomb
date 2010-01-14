package com.sun.honeycomb.layout;
import java.util.Random;
import com.sun.honeycomb.disks.DiskId;



/* This java class simulates the disk usages in Honeycomb */
/*  Under trunk/unit_tests/src/java/com/sun/honeycomb/layout */
/* ./run.sh DiskUsageSimulator <algorithm> <numObj> <smallestSize> <largestSize> */
/*  It will print out -- how many times each mapId get selected, max, min and diff in % */
/*                    -- how many obj fragments stored on each disk, max, min and diff in % */   
/*                    -- how much data stored on each disk, max, min, diff in %, mean and standard deviation */  
/* It can also handle multi-trunk case */

public class DiskUsageSimulator implements Runnable {


    private static final int NUM_MAP_IDS = 10000;  /* # of different MapID*/
    private static final int NUM_NODES = 16;       /* # of nodes*/
    private static final int DISKS_PER_NODE = 4;   /* # of disks per node*/ 
    private static final int FRAG = 7;             /* # of total fragments*/
    private static final int REDUNDANCY = 2;       /* # of redundancy fragments*/
    private static final int BASE_NODE_ID = 101;   /*101 is node 0*/ 
    private static final double pReset = 0;         /* prob. of resetting MapId */
    private static final long trunkSize = 1048576;   /*1 GB*/

    private static int algorithm;            /*1: RS: Random Selection */
                                             /*2: RSSS: Random Starting Sequential Selection*/
                                             /*3: FSSS: Fixed Starting Sequential Selection*/
    private static long numObj;              /* # of objects we will store*/
    private static long smallestSize;        /* smallest obj size in kilo bytes*/
    private static long largestSize;         /* largest obj size in kilo bytes*/

    private static int[] nextMapId = new int[NUM_NODES];
       
    public static void main(String[] args) {

        if (args.length != 4) {
            System.err.println("Error: insufficient arguments!");
            System.err.println ("Usage: DiskUsageSimulator <algorithm> <numObj> <smallestSize><largestSize>");
            System.exit(1);
        }

        int algorithm = Integer.parseInt(args[0]);
        long numObj = Long.parseLong(args[1]);
        long smallestSize = Long.parseLong(args[2]);
        long largestSize = Long.parseLong(args[3]);
               
        DiskUsageSimulator diskUsageSimulator = new DiskUsageSimulator(algorithm,numObj,smallestSize,largestSize);
        diskUsageSimulator.run();
    }

    public DiskUsageSimulator(int algorithm,long numObj,long smallestSize,long largestSize) {

        this.algorithm = algorithm;
        this.numObj = numObj;
        this.smallestSize = smallestSize;
        this.largestSize = largestSize;
    }

    public void run() {


        int[] mapIdCount = new int[NUM_MAP_IDS];
        int[][] objCount = new int[NUM_NODES][DISKS_PER_NODE];
        long[][] kiloByteCount =  new long[NUM_NODES][DISKS_PER_NODE];
        long meanKiloByteCount = 0;
        double sdKiloByteCount = 0;

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
                   System.exit(1); ;
            }
        }


        try{

            DiskMask mask = new DiskMask();

            mask.setAvailable(0, NUM_NODES*DISKS_PER_NODE, true); /* set them all online */
            mask.setEnabled(0, NUM_NODES*DISKS_PER_NODE, true);    /* set them all online */

            LayoutClient lc = new LayoutClient();

            for (long i = 0; i < numObj; i++) {

                int nodes = (int) Math.random()* NUM_NODES; /*Node will be randomly selected*/
                int currentMapId = generateMapId(nodes);
                long objSize = (long) (Math.random()*(largestSize-smallestSize)+smallestSize);
                long fragSize;
 
     	        for (long trunk = 0; trunk <= ((objSize-0.01)/trunkSize); trunk++) {
                    if ((trunk ==  (long) ((objSize-0.01)/trunkSize)) && ((objSize % trunkSize)!=0)) 
                        fragSize = (long) ((objSize % trunkSize)/(FRAG-REDUNDANCY)+1);
                    else 
                        fragSize = (long) (trunkSize/(FRAG-REDUNDANCY)+1);
		    mapIdCount[currentMapId]++;
                    Layout layout = lc.getLayout(currentMapId,mask);
                    for (int fragid = 0; fragid < FRAG; fragid++) {  

	                DiskId diskid = (DiskId) layout.get(fragid);
                        int nodeid = diskid.nodeId() - BASE_NODE_ID;
                        int diskindex = diskid.diskIndex();
                        objCount[nodeid][diskindex]++;
                        kiloByteCount[nodeid][diskindex] = kiloByteCount[nodeid][diskindex] + fragSize;
                    }
                    currentMapId = (currentMapId + 1)%NUM_MAP_IDS;
                }  
            }

            float maxMapIdCount = 0;
            float minMapIdCount = Integer.MAX_VALUE;

            float maxObjCount = 0;
            float minObjCount = Integer.MAX_VALUE;

            float maxKiloByteCount = 0;
            float minKiloByteCount = Integer.MAX_VALUE;

            switch (algorithm) {
                case 1: 
                   System.out.println("Simulate Algorithm 1: Random Selection "); 
                   break;
                case 2: 
                   System.out.println("Simulate Algorithm 2: Random Starting Sequential Selection"); 
                   break;
                case 3: 
                   System.out.println("Simulate Algorithm 3: Fixed Starting Sequential Selection"); 
                   break;
            }

            System.out.println("Total number of object stored = " + numObj + "; Max Obj Size = " 
                                + largestSize +" kB;" + "Min Obj Size = " 
                                + smallestSize +" kB; MapId Reset Probability =" + pReset);
            System.out.println("------------------------------------------------------------------");
            System.out.println("Disk Usage Summary:");

            for (int i = 0; i < NUM_MAP_IDS; i++) {

                /*System.out.println("MapID: " + i + " occurred " + mapIdCount[i]); */

                if (maxMapIdCount < mapIdCount[i]) 
                    maxMapIdCount = mapIdCount[i];
                if (minMapIdCount > mapIdCount[i]) 
                    minMapIdCount = mapIdCount[i]; 
            }

            for (int i = 0; i < NUM_NODES; i++) {
                for (int j = 0; j < DISKS_PER_NODE; j++) {

 		    System.out.println("node: " + (101+i) + " disk: " + j + " stores "+ objCount[i][j] 
                                        + " objects, " + (float) kiloByteCount[i][j]/1024/1024 + " GB data" );
	            
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
            
            
            meanKiloByteCount = meanKiloByteCount/NUM_NODES/DISKS_PER_NODE;
            sdKiloByteCount = Math.sqrt(sdKiloByteCount/NUM_NODES/DISKS_PER_NODE-meanKiloByteCount*meanKiloByteCount);

            System.out.println("------------------------------------------------------------------");
            System.out.println("MaxMapIdCount: " + (int) maxMapIdCount + "; MinMapIdCount: " + (int) minMapIdCount);
            System.out.println("Diff in %: " + ((maxMapIdCount - minMapIdCount)/maxMapIdCount )*100);

            System.out.println("------------------------------------------------------------------");
            System.out.println("MaxObjCount: " + (int) maxObjCount + "; MinObjCount: " + (int) minObjCount);
            System.out.println("Diff in %: " + ((maxObjCount - minObjCount)/maxObjCount)*100);

            System.out.println("------------------------------------------------------------------");
            System.out.println("MaxGigaByteCount: " + maxKiloByteCount/1024/1024 + " GB; MinGigaByteCount: " 
				+ minKiloByteCount/1024/1024 + " GB");
            System.out.println("Diff in %: " + ((maxKiloByteCount - minKiloByteCount)/maxKiloByteCount )*100 
				+ "; mean =" + (float) meanKiloByteCount/1024/1024 + " GB; sd =" 
				+ sdKiloByteCount/1024/1024 + " GB");

        }

        catch (Throwable throwable) {
            System.err.println("An unexpected error occurred.");
            throwable.printStackTrace();
        } 
    }


    private static int generateMapId(int node) {
        if (Math.random() < pReset) {
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


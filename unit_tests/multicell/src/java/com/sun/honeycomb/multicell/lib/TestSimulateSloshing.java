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



package com.sun.honeycomb.multicell.lib;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import java.util.Random;

//import com.sun.honeycomb.multicell.lib.Rule;


public class TestSimulateSloshing
{

    static private final int HC_DATA_OBJECT           = 1;
    static private final int HC_MD_OBJECT             = 2;

    static private final int NB_NODES                 = 16;

    static private final int DISTRIBUTION_NB_OBJECTS  = 1; 
    static private final int DISTRIBUTION_SIZE        = 2; 

    static private final String ARG_PARSE_SEPARATOR   = " ";
    static private final String ARG_MD_SIZES          = "--md_sizes";
    static private final String ARG_DATA_SIZES        = "--data_sizes";
    static private final String ARG_NB_DATA_OBJECTS   = "--nb_objects";
    static private final String ARG_RATIO_DATA_MD     = "--ratio_md";
    static private final String ARG_DEBUG_FLAG        = "--debug";
    static private final String ARG_DISPLAY           = "--display";

   

    private int []  DSIZES;
    private int []  MDSIZES;
    private long    MAX_NB_DATA_OBJECTS;
    private long    RATIO_MD;
    private boolean DEBUG_FLAG;
    private int     DISPLAY_TYPE;

    private long nbDataObjects;
    private long nbMdObjects;

    private ThreadGroup     nodeLoaderGrp;
    private HCNodeLoader [] nodeLoaders;
    

    public static void main(String [] argv) {
        TestSimulateSloshing test = new TestSimulateSloshing(argv);
    }

    public TestSimulateSloshing(String [] argv) {
        nbDataObjects       = 0;
        nbMdObjects         = 0;
        MAX_NB_DATA_OBJECTS = -1;
        RATIO_MD            = -1;
        MDSIZES             = null;
        DSIZES              = null;
        DISPLAY_TYPE        = -1;
        DEBUG_FLAG          = false;
        parseArguments(argv);
        displayHeader();
        startTest();
        waitForResult();
        displayDistribution(mergeResults());
    }


    //
    // Parsing
    //
    private void usage(String msg) {
        System.err.println("\nusage: java -cp <> TestSimulateSloshing " +
          ARG_DATA_SIZES + "=<data_size1, data_size2, ...>" + 
          ARG_PARSE_SEPARATOR +
          ARG_MD_SIZES + "=<md_size1, md_size2, ...>" + 
          ARG_PARSE_SEPARATOR +
          ARG_NB_DATA_OBJECTS + "=<max_number_of_data_objects>" + 
          ARG_PARSE_SEPARATOR +
          ARG_RATIO_DATA_MD + "=<ratio_md/data>" +
          ARG_PARSE_SEPARATOR +
          ARG_DISPLAY + "=\"size\"|\"nb_objects\"\n");
        System.err.println("error: " + msg);
        System.exit(1);
    }

    private void parseArguments(String [] args) {
        String name = null;
        String value = null;
        for (int i = 0; i < args.length; i++) {
            int indexEqual = args[i].indexOf("=");
            if (indexEqual == -1) {
                usage("Need to specify argument of the form name=value");
            }
            try {
                name = args[i].substring(0, indexEqual).trim();
                value = 
                  args[i].substring(indexEqual + 1, args[i].length()).trim();
            } catch (IndexOutOfBoundsException iob) {
                usage("Can't parse argument...");
            }
                
            try {
                if (name.equals(ARG_MD_SIZES)) {
                    MDSIZES = parseValueInts(value);
                } else if (name.equals(ARG_DATA_SIZES)) {
                    DSIZES = parseValueInts(value);
                } else if (name.equals(ARG_NB_DATA_OBJECTS)) {
                    MAX_NB_DATA_OBJECTS = Long.parseLong(value);
                } else if (name.equals(ARG_RATIO_DATA_MD)) {
                    RATIO_MD = Long.parseLong(value);
                } else if (name.equals(ARG_DEBUG_FLAG)) {
                    DEBUG_FLAG = (value.equals("true")) ? true : false;
                } else if (name.equals(ARG_DISPLAY)) {
                    if (value.equals("size")) {
                        DISPLAY_TYPE = DISTRIBUTION_SIZE;
                    } else if (value.equals("nb_objects")) {
                        DISPLAY_TYPE = DISTRIBUTION_NB_OBJECTS;
                    } else {
                        usage("Invalid display type : " + value);
                    }
                } else {
                    usage("Invalid argument :" + name);
                }
            } catch (NumberFormatException nfe) {
                System.err.println(name + ": can't decode values " + value);
                usage("Can't decode values " + value + " for name " + name);
            }
        }
        if ((MDSIZES == null) ||
          (DSIZES == null) ||
          (MAX_NB_DATA_OBJECTS == -1) ||
          (RATIO_MD == -1) ||
          (DISPLAY_TYPE == -1)) {
            usage("Missing arguments, all of them need to be specified...");
        }
    }


    private int [] parseValueInts(String val)
        throws NumberFormatException {

        String [] values = val.split(",");
        int [] res = new int[values.length];

        for (int i = 0; i < values.length; i++) {
            res[i] = Integer.parseInt(values[i].trim());
        }
        return res;
    }

    private String displayValueLongs(int [] val) {
        StringBuffer str = new StringBuffer();
        for (int i = 0; i < val.length; i++) {
            str.append(Integer.toString(val[i]));
            if (i < (val.length - 1)) {
                str.append(", ");
            }
        }
        return str.toString();
    }

    private void displayHeader() {
        System.out.println("#");
        System.out.println("# Test : " +
          getArgStringValue(ARG_NB_DATA_OBJECTS) + " = " + 
          MAX_NB_DATA_OBJECTS + 
          ARG_PARSE_SEPARATOR +
          getArgStringValue(ARG_RATIO_DATA_MD) + " = " + 
          RATIO_MD +
          ARG_PARSE_SEPARATOR +
          getArgStringValue(ARG_DATA_SIZES) + " = " + 
          displayValueLongs(DSIZES) + 
          ARG_PARSE_SEPARATOR +
          getArgStringValue(ARG_MD_SIZES) + " = " + 
          displayValueLongs(MDSIZES) +
          ARG_PARSE_SEPARATOR + 
          getArgStringValue(ARG_DISPLAY) + " = " + 
          ((DISPLAY_TYPE == DISTRIBUTION_NB_OBJECTS) ? "nb_objects" : "size"));
        System.out.println("#");
        System.out.flush();
    }

    private String getArgStringValue(String arg) {
        return arg.substring(2, arg.length());
    }


    //
    // Run test
    //
    private void startTest() {
        nodeLoaderGrp = new ThreadGroup("Node loader group");
        nodeLoaders = new HCNodeLoader[NB_NODES];
        for (int i = 1; i <= NB_NODES; i++) {
            nodeLoaders[i - 1] = new HCNodeLoader(i);
            Thread th = new Thread(nodeLoaderGrp, nodeLoaders[i - 1],
              ("th-node-" + i));
            th.start();
        }
    }

    private void waitForResult() {
        Thread [] activeThrs = new Thread[NB_NODES];
        int nbActive = nodeLoaderGrp.enumerate(activeThrs);
        for (int i = 0; i < nbActive; i++) {
            try {
                activeThrs[i].join();
            } catch (InterruptedException ignored) {
            }
        }
        debug("All threads completed, stored " +  nbDataObjects +
          " data objects and " +  nbMdObjects + ", ratio = "  +
          nbMdObjects / nbDataObjects);
    }



    //
    // Analyze results
    //

    private HashMap mergeResults() {
        HashMap res = new HashMap();
        for (int i = 0; i < NB_NODES; i++) {
            Map map = nodeLoaders[i].allObjects;
            Iterator it = map.keySet().iterator();
            while (it.hasNext()) {
                Short key =  (Short) it.next();
                List totalList = (List) res.get(key);
                if (totalList == null) {
                    totalList = new ArrayList();
                    res.put(key, totalList);
                }
                List list = (List) map.get(key);
                totalList.addAll(list);
            }
        }
        return res;
    }

    private void displayDistribution(HashMap totalMap) {

        Iterator it = totalMap.keySet().iterator();
        double average = 0;
        double deviation = 0;

        while (it.hasNext()) {
            Short key = (Short) it.next();
            List list = (List) totalMap.get(key);
                debug("siloLocation = " + key +
                  ", nb entries = " + list.size());
                double tmp = getDistributionPerSilolocation(list);
                tmp = tmp / totalMap.size();
                average += tmp;
        }

        it = totalMap.keySet().iterator();
        while (it.hasNext()) {
            Short key = (Short) it.next();
            List list = (List) totalMap.get(key);
            double tmp = getDistributionPerSilolocation(list);
            double tmp2  = tmp - average;
            tmp2 *= tmp2;
            debug("cur val = " + tmp + ", avg = "
              + average + "-> tmp init = " + tmp2);
            deviation += (tmp2 / average);
            debug("cumual deviation  = " + deviation);
        }
        deviation = Math.sqrt(deviation);
        System.out.println("# average\tstd deviation\t%deviation");
        System.out.println("  " + average + "\t " + deviation + "\t" +
          (deviation/average) * 100);
        System.out.flush();
    }
    
    private double getDistributionPerSilolocation(List list) {
        long res = 0;
        switch (DISPLAY_TYPE) {
        case DISTRIBUTION_NB_OBJECTS:
            res = list.size();            
            break;
        case DISTRIBUTION_SIZE:
            for (int i = 0; i < list.size(); i++) {
                HCObject obj = (HCObject) list.get(i);
                res += obj.size;
            }
            break;
        default:
            System.err.println("unexpected distribution type");
            System.exit(1);
            break;
        }
        return res;
    }

    //
    // Utils
    //
    private void debug(String trace) {
        if (DEBUG_FLAG) {
            System.out.println("#..debug: " + trace);
            System.out.flush();
        }
    }

    //
    // One such object to simulate each cluster node.
    //
    private class HCNodeLoader implements Runnable {

        int     node;
        List    dataObjects;
        Map     allObjects;

        private Rule.Interval interval;
        private Random genRandomSize;
        private Random genDataObject;

        HCNodeLoader(int node) {
            this.dataObjects = dataObjects;
            this.allObjects = allObjects;
            this.node = node;
            this.genDataObject = new Random(System.currentTimeMillis() % node);
            this.genRandomSize = new Random(System.currentTimeMillis() / node);
            this.dataObjects = new ArrayList();
            this.allObjects = new HashMap();

            // Use class Interval directly instead of MultiCellLib because
            // MultiCellLib oblige to pull more code from server. use
            // hardcoded value for begining and end interval since those
            // values are private in this class-- and should be.
            this.interval = new Rule.Interval((short) 0, (short) 32767, 0L);
        }
        

        public void run() {
            debug("Start thread on node " + node);        
            loadClusters();
            debug("Thread on node " + node + " has finished");
        }

        private void loadClusters() {
            long maxDataObjects = (MAX_NB_DATA_OBJECTS / NB_NODES) / 
              (RATIO_MD + 1);
            long maxMdObjects = maxDataObjects * RATIO_MD;
            while (maxDataObjects-- > 0) {
                loadData();
            }
            
            while (maxMdObjects-- > 0) {
                loadMD();
            }
        }

        private void loadData() {
            short siloLocation = interval.getNextSiloLocation();
            int sizeOffset = genRandomSize.nextInt(DSIZES.length);

            HCObject data = new HCObject(HC_DATA_OBJECT, siloLocation, 
                DSIZES[sizeOffset]);
            List list = (List)  allObjects.get(new Short(siloLocation));
            if (list == null) {
                list = new ArrayList();
                allObjects.put(new Short(siloLocation), list);
            }
            list.add(data);
            dataObjects.add(data);
            synchronized(TestSimulateSloshing.class) {
                nbDataObjects++;
            }
        }

        private void loadMD() {
            int sizeOffset = genRandomSize.nextInt(MDSIZES.length);
            int slOffset = genDataObject.nextInt(dataObjects.size());
            HCObject data = (HCObject) dataObjects.get(slOffset);
            short siloLocation = data.siloLocation;

            HCObject md = new HCObject(HC_MD_OBJECT, siloLocation, 
                MDSIZES[sizeOffset]);
            List list = (List)  allObjects.get(new Short(siloLocation));
            list.add(md);
            data.nbMds++;
            synchronized(TestSimulateSloshing.class) {
                nbMdObjects++;
            }
        }
    }


    private class HCObject {
        short siloLocation;
        long  size;
        int   type;
        int   nbMds;

        HCObject(int type, short siloLocation, long size) {
            this.type = type;
            this.siloLocation = siloLocation;
            this.size = size;
            nbMds = 0;
        }
    }
}
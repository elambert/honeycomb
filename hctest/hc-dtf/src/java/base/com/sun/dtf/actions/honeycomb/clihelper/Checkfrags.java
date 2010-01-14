package com.sun.dtf.actions.honeycomb.clihelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.file.Returnfile;
import com.sun.dtf.actions.flowcontrol.Parallel;
import com.sun.dtf.cluster.Cluster;
import com.sun.dtf.cluster.NodeInterface;
import com.sun.dtf.cluster.node.CollectFragments;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

/**
 * @dtf.tag checkfrags
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Utility cluster side tag that can be used to validate that all 
 *               objects currently residing on the cluster have all of the 
 *               fragments available. 
 *               
 *               All objects that have less than 7 fragments will be reported in
 *               the logs as a warning and the objects that seem to have data 
 *               loss (ie less than 5 fragments) will be reported as an error 
 *               in the logs.
 *               
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *      <checkfrags/>
 *  </component>
 *  
 */
public class Checkfrags extends Action { 

    private static final String DEFAULT_FRAGLIST_LOCATION = "checkfrags";
    
    private static final int DRIVES = 4;
    private static final int NORM_FRAG_COUNT = 7;
    private static final int DATA_LOSS_COUNT = 5;
   
    /**
     * @dtf.attr uri
     * @dtf.attr.desc the uri attribute is used to identify a location on the 
     *                DTFX where the listings of all of the fragments should 
     *                be saved. NOTE: currently this isn't really used an there
     *                is no real reason to save these files on the DTFX since 
     *                they are accessible on the DTFA that is controlling the 
     *                cluster.
     */
    private String uri = null;
    
    public void execute() throws DTFException {
        Cluster cluster = Cluster.getInstance();
        int nodeCount = cluster.getNumNodes();
       
        int objectsAnalyzed = 0;
        int fragsAnalyzed = 0;
        int lessThan7Frags = 0;
        int lessThan5Frags = 0;
        int dupFrags = 0;
        
        NodeInterface nodes[] = new NodeInterface[nodeCount];
        
        for(int i = 1; i <= nodeCount; i++) {
            nodes[i-1] = cluster.getNode(i);
        }
        
        File fragdir = new File(DEFAULT_FRAGLIST_LOCATION + 
                                File.separatorChar + 
                                System.currentTimeMillis()); 
        fragdir.mkdirs();
    
        // use existing parallel tag to execute all of the necessary 
        // CollectFragments actions
        Parallel parallel = new Parallel();
        getRemoteLogger().info("Gathering fraglist from cluster.");
        CollectFragments[] frags = new CollectFragments[DRIVES];
        long start = System.currentTimeMillis(); 
        for (int i = 0; i < DRIVES; i++) { 
            frags[i] = new CollectFragments();
            frags[i].setWhere(fragdir.getAbsolutePath() + "/node-");
            frags[i].setDrive(""+i);
            parallel.addAction(frags[i]);
        }
        parallel.execute();
        long stop = System.currentTimeMillis();
        getRemoteLogger().info("Time to gather " + (stop-start) + "ms.");

        getRemoteLogger().info("Analyzing oid information gathered.");
        try { 
            BufferedReader[] readers = new BufferedReader[nodeCount*DRIVES];
            int readerCnt = 0;
            for (int d = 0; d < 4; d++) { 
                for(int i = 0; i < nodeCount; i++) { 
                    NodeInterface node = nodes[i];
                    FileInputStream fis = new FileInputStream(fragdir.getAbsoluteFile() + 
                                                              "/node-"+ node.getId() + 
                                                              "." + d + ".gz");
                    // gziped on the node side so I have to read back the results
                    // making use of the GZIPInputStream
                    GZIPInputStream zis = new GZIPInputStream(fis);
                    InputStreamReader isr = new InputStreamReader(zis);
                    readers[readerCnt++] = new BufferedReader(isr);
                }
            }
        
            String[] heads = new String[readers.length];
            boolean done = false;
                        
            while (!done) { 
                int cnt = 0;
                for(int i = 0; i < heads.length; i++) {
                    BufferedReader reader = readers[i];
                                    
                    if (reader != null && heads[cnt] == null) { 
                        heads[cnt] = reader.readLine();
                        // anything with _ in it is a fragment file.
                        while (heads[cnt] != null && ( 
                               heads[cnt].trim().length() == 0)) {
                            heads[cnt] = reader.readLine();
                        }
                    }
                    cnt++;
                }
                         
                // find the smallest oid since things are ordered
                int refIndex = 0;
                for(int i = 0; i < heads.length; i++) { 
                    if (heads[i] != null) { 
                        if (heads[refIndex] == null) { 
                            refIndex = i;
                        } else if (heads[i].compareTo(heads[refIndex]) < 0) {
                            refIndex = i;
                        } 
                    }
                }
                            
                String ref = heads[refIndex];
                heads[refIndex] = null;
                            
                if (ref == null) { 
                    done = true;
                    break;
                }
                
                objectsAnalyzed++;
                            
                String refOID = fixOID(ref);
                String fragID = getID(ref);
                fragsAnalyzed++;
                          
                int oidCount = 1;
                for(int i = 0; i < heads.length; i++) { 
                    if (heads[i] != null) { 
                        String oid = fixOID(heads[i]);
                        if (oid.equals(refOID)) { 
                            String ofragID = getID(heads[i]);
                            if (ofragID.equals(fragID)) { 
                                getRemoteLogger().warn("Duplicate of " + heads[i]);
                                dupFrags++;
                            } else {
                                fragsAnalyzed++;
                                oidCount++;
                            }
                            heads[i] = null;
                        }
                    }
                }

                if (oidCount < DATA_LOSS_COUNT) { 
                    getRemoteLogger().error("Data Loss: [" + refOID + 
                                            "] has " + oidCount);
                    lessThan5Frags++;
                } else if (oidCount < NORM_FRAG_COUNT) { 
                    getRemoteLogger().warn("Less than 7 frags [" + refOID + 
                                           "] has " + oidCount);
                    lessThan7Frags++;
                }
            }
        } catch (IOException e) {
            throw new DTFException("Unable to read buffer.",e);
        } finally { 
            stop = System.currentTimeMillis();
            getRemoteLogger().info(objectsAnalyzed + " objects.");
            getRemoteLogger().info(fragsAnalyzed + " frags.");
            
            long duration = (stop - start)/1000;
            if (duration != 0)
                getRemoteLogger().info((fragsAnalyzed/duration) + " frags/s");
            
            getRemoteLogger().info(dupFrags + " duplicate fragments.");
            getRemoteLogger().info(lessThan7Frags + " with less than 7 frags.");
            getRemoteLogger().info(lessThan5Frags + " with less than 5 frags.");
        }
       
        if (getURI() != null) {
            for (int d = 0; d < 4; d++) { 
                for(int i = 0; i < nodeCount; i++) { 
                    NodeInterface node = nodes[i];
                    File oidfile = new File(fragdir.getAbsoluteFile() + 
                                      "/node-"+ node.getId() + 
                                      "." + d + ".gz");
                    Returnfile.genReturnFile(getURI() + "/" + oidfile.getName(),
                                             0,
                                             oidfile.getAbsolutePath(),
                                             false);
                }
            }
        }
    }
    
    private String fixOID(String oid) { 
        return oid.substring(oid.lastIndexOf("/")+1,oid.indexOf('_'));
    }

    private String getID(String oid) { 
        return oid.substring(oid.indexOf('_')+1,oid.length());
    }
    
    public String getURI() throws ParseException { return replaceProperties(uri); }
    public void setURI(String uri) { this.uri = uri; }
}

package com.sun.dtf.actions.honeycomb.cluster;

import java.io.File;

import com.sun.dtf.actions.file.Returnfile;
import com.sun.dtf.cluster.NodeAction;
import com.sun.dtf.cluster.NodeInterface;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

/**
 * @dtf.tag perfmonitor
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This tag is used for starting/stopping and collection the logs
 *               from the server side performance monitoring. The logs themselves
 *               are very similar to client side logs that are generated from 
 *               the API events and recorded by the DTF framework. 
 *               
 *               These results can be used to show where some server side 
 *               resource bottlenecks reside. They can also be used to correlate
 *               certain spikes in CPU/MEM/Disk usage with different events that
 *               happen to the cluster, such as load, losing a node or losing a 
 *               disk.
 *               
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *     <perfmonitor command="start" sample="1"/>
 * </component>
 * 
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *     <perfmonitor command="stop" />
 * </component>
 * 
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *     <perfmonitor command="collectlogs" uri="storage://OUTPUT/server-${iter}.logs"/>
 * </component>
 *
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *     <perfmonitor command="start" sample="1" node="8"/>
 * </component>
 */
public class Perfmonitor extends NodeAction {
    
    private static final String PERFLOG_CTX = "hc.perflog.ctx";

    public static final String PERFMONITOR_START    = "start";
    public static final String PERFMONITOR_STOP     = "stop";
    public static final String PERFMONITOR_LOGS     = "collectlogs";
   
    /**
     * @dtf.attr command 
     * @dtf.attr.desc The command can only be 1 of the 3 following:
     *                start - start the perfomance monitoring
     *                stop  - stop any currently running performance monitoring.
     *                collectlogs - collect the logs of any previously started
     *                              performance monitoring to the the location
     *                              specified by the uri attribute.
     */
    private String command = null;

    /**
     * @dtf.attr uri
     * @dtf.attr.desc the uri specifies the location to save the logs when 
     *                using the perfmonitor tag with the command option equal to
     *                collectlogs. 
     */
    private String uri = null;
   
    /**
     * @dtf.attr sample
     * @dtf.attr.desc specifies the sample rate in seconds of the perfomance 
     *                monitoring task.
     */
    private String sample = null;
   
    // lock used to protected while writing out logs by multiple nodes to the 
    // same log location. This way it serializes them nicely.
    private static Object LOG_LOCK = new Object();
    public void execute(NodeInterface node) throws DTFException {
        if (getCommand().equals(PERFMONITOR_START)) { 
            node.stopPerformanceMonitor();
            node.startPerformanceMonitor(getSample());
        } else if (getCommand().equals(PERFMONITOR_STOP)) { 
            node.stopPerformanceMonitor();
        } else if (getCommand().equals(PERFMONITOR_LOGS)) { 
            if (getUri() == null) 
                throw new ParseException("URI must be set inorder to collect logs to.");
          
            synchronized (LOG_LOCK) { 
                String perflog =  (String) getContext(PERFLOG_CTX);
               
                if (perflog == null) { 
                    String dir = "hc-resource-logs" + File.separatorChar + 
                                 System.currentTimeMillis();
                    
                    new File(dir).mkdirs();
                    perflog = dir + File.separatorChar + "hc.perf.log";

                    getLogger().info("Collecting logs to [" + perflog + "]");
                    
                    registerContext(PERFLOG_CTX, perflog); 
                    Returnfile.genReturnFile(getUri(), 0, perflog, false);
                }

                node.collectPerfLog(perflog, true);
            }
        }
    }
   
    public void setCommand(String command) { this.command = command; } 
    public String getCommand() throws ParseException { return replaceProperties(command); } 

    public void setSample(String sample) { this.sample = sample; } 
    public int getSample() throws ParseException { return toInt("sample", sample); } 

    public String getUri() throws ParseException { return replaceProperties(uri); } 
    public void setUri(String uri) { this.uri = uri; } 
}

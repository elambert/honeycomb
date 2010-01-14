package com.sun.dtf.actions.honeycomb.cli;

import com.sun.dtf.cluster.cli.CLI;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.recorder.Event;

/**
 * @dtf.tag df 
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc <p>
 *               This task uses the honeycomb CLI command to get the currently 
 *               used, free and available space. This taga makes use of events
 *               to register available results and therefore should be wrapped
 *               with a recorder tag in order to make use of the available 
 *               results.
 *               </p>
 *               <p>
 *               Also note that this tag should only be called within a DTFA 
 *               that is controlling a cluster and not from a client because
 *               it will throw an exception in the later case.
 *               </p>
 * 
 * @dtf.event df
 * @dtf.event.attr total.kbytes
 * @dtf.event.attr.desc total kilobytes of space on the cluster.

 * @dtf.event df
 * @dtf.event.attr available.kbytes
 * @dtf.event.attr.desc availble kilobytes of free space on the cluster.

 * @dtf.event df
 * @dtf.event.attr used.kbytes
 * @dtf.event.attr.desc used kilobytes of space on the cluster.

 * @dtf.event df
 * @dtf.event.attr usage.percentage
 * @dtf.event.attr.desc percentage of the cluster that is used.
 * 
 * @dtf.tag.example 
 * <script>
 *     <record type="object" uri="property://df">
 *         <cli>
 *              <df/>
 *         </cli>
 *     </record>
 *     
 *     <local>
 *         <echo>Available space: ${df.available.kbytes}</echo>
 *     </local>
 * </script>
 */
public class Df extends CLICommand {
    
    public final static String TOTAL_KBYTES = "total.kbytes";
    public final static String AVAIL_KBYTES = "available.kbytes";
    public final static String USED_KBYTES  = "used.kbytes";
    public final static String USAGE_PERC   = "usage.percentage";
    
    /*
     * Example output from df
     * All sizes expressed in 1K blocks
     * Total: 14372110336; Avail: 13694068736; Used: 678041600; Usage: 4.7%
     */
    private final static int EXPECTED_LINES   = 2;
    private final static int EXPECTED_PARTS   = 8;
    
    private final static int TOTAL_INDEX      = 1;
    private final static int AVAIL_INDEX      = 3;
    private final static int USED_INDEX       = 5;
    private final static int USAGE_INDEX      = 7;
    
    public void execute() throws DTFException {
        CLI cli = getCLI();
        String[] lines = cli.df();
        
       
        if (lines.length != EXPECTED_LINES) { 
            throw new DTFException("df output should contain " + 
                                   EXPECTED_LINES + " lines, but contains " + 
                                   lines.length + ", output is: " + lines);
        }
       
        String[] parts = lines[1].split(" "); 

        if (parts.length != EXPECTED_PARTS) { 
            throw new DTFException("df output should contain " + 
                                   EXPECTED_PARTS + " parts, but contains " + 
                                   parts.length + ", output is: " + lines);
        }
       
        String total = parts[TOTAL_INDEX];
        String avail = parts[AVAIL_INDEX];
        String used  = parts[USED_INDEX];
        String usage = parts[USAGE_INDEX];
        
        Event event = new Event("df");
        event.addAttribute(TOTAL_KBYTES, total);
        event.addAttribute(AVAIL_KBYTES, avail);
        event.addAttribute(USED_KBYTES, used);
        event.addAttribute(USAGE_PERC, usage);
        
        getRecorder().record(event);
    }
}

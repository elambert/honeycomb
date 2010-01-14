package com.sun.dtf.actions.honeycomb.clihelper;

import com.sun.dtf.actions.honeycomb.cli.CLICommand;
import com.sun.dtf.cluster.cli.CLI;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.util.ThreadUtil;

/**
 * @dtf.tag waitforhadb
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This tag will wait for hadb to HAFaulterTolerant forever, if it
 *               never reaches that state than it will just keep waiting.
 *               
 * @dtf.tag.example 
 * <component id="CLUSTER">
 *     <waitforhadb/>
 * </component>
 */
public class Waitforhadb extends CLICommand {

    public static final String HADB_OK = "Query Engine Status: HAFaultTolerant";
    private static int HADB_STATUS_LINE = 3;
    private static long WAIT_INTERVAL = 60000; // 1 minute
    
    public void execute() throws DTFException {
        getRemoteLogger().info("Waiting for HADB to be HAFaultTolerant.");
        CLI cli = getCLI();
        String[] lines = cli.sysstat(null);

        String hadbline = lines[HADB_STATUS_LINE];
        
        while (hadbline.indexOf(HADB_OK) == -1) {
            getLogger().info("Waiting for hadb, current state [" + 
                             hadbline + "]");
            getRemoteLogger().info("Waiting for hadb, current state [" + 
                                   hadbline + "]");
            ThreadUtil.pause(WAIT_INTERVAL);
            lines = cli.sysstat(null);
            
            if (lines.length <= HADB_STATUS_LINE) { 
                for(int i = 0; i < lines.length;i++) 
                    getLogger().info("shorter sysstat that expected: " + lines[i]);
                continue;
            }
            hadbline = lines[HADB_STATUS_LINE];
        }
        
        getLogger().info("Hadb read for action, " + hadbline);
    }
    
}

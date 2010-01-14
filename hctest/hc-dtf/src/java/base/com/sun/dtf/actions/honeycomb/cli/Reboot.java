package com.sun.dtf.actions.honeycomb.cli;

import com.sun.dtf.cluster.Cluster;
import com.sun.dtf.cluster.HCCluster;
import com.sun.dtf.cluster.cli.CLI;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.util.ThreadUtil;

/**
 * @dtf.tag reboot
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc reboot tag is used to reboot can be used to reboot the cluster
 *               from the honeycomb cli.
 *               
 * @dtf.tag.example 
 * <cli>
 *      <reboot/>
 * </cli>
 * 
 */
public class Reboot extends CLICommand {
    public void execute() throws DTFException {
        getRemoteLogger().info("Rebooting honeycomb.");
        getLogger().info("Rebooting honeycomb.");
        
        CLI cli = getCLI();
        cli.reboot(new String[] {"-F"});
        
        //XXX: hack wait 60 seconds just so that the cluster doesn't have any 
        //     jvms running...
        if (Cluster.getInstance() instanceof HCCluster) { 
            getLogger().warn("Waiting for 60s because the CLI reboot doens't kill off all jvms instantly.");
            ThreadUtil.pause(60000);
        }
    }
}

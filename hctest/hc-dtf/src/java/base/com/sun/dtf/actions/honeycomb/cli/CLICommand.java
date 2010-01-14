package com.sun.dtf.actions.honeycomb.cli;

import com.sun.dtf.actions.Action;
import com.sun.dtf.cluster.Cluster;
import com.sun.dtf.cluster.cli.CLI;
import com.sun.dtf.config.Config;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.util.HCProperties;

public abstract class CLICommand extends Action {
    
    private String cellID = null;
    
    protected CLI getCLI() throws DTFException {
        Config config = Action.getConfig();
        Cluster cluster = Cluster.getInstance();
        
        String cell0 = cluster.getCellId(0);
        String adminvip = cluster.getAdminVIP(cell0);
        
        String user = config.getProperty(HCProperties.HC_ADMIN_USER,
                                         HCProperties.HC_ADMIN_USER_DEF);
        String pass = config.getProperty(HCProperties.HC_ADMIN_PASS);

        return CLI.getInstance(adminvip, user, pass);
    }

    public String getCellID() throws ParseException { return replaceProperties(cellID); }
    public void setCellID(String cellID) { this.cellID = cellID; }
    
}

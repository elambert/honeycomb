package com.sun.dtf.cluster.cli;

import java.io.InputStream;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exceptions.CLIException;

public class EmulatorCLI extends CLI {

    public static final String EXEC_CHANNEL = "exec";
    
    private String _adminvip = null;
    private String _user = null;
    private String _pass = null;
   
    public EmulatorCLI(String adminvip, String user, String pass) throws CLIException { 
        _adminvip = adminvip;
        _user = user;
        _pass = pass;
    }

    public String[] df() throws CLIException {
        Action.getLogger().info("df not implemented for emulator.");
        return null;
    }
    
    public void reboot(String[] options) throws CLIException { 
        Action.getLogger().info("Reboot not implemented for emulator.");
    }
    
    public String[] sysstat(String[] options) throws CLIException {
        return new String[]{"","","","Query Engine Status: HAFaultTolerant","",""};
    }
    
    public String[] mdconfig(String[] options,
                             InputStream is) 
           throws CLIException {
        return new String[]{};
    }
    
    public String[] setprops(String name, String value) throws CLIException {
        return null;
    }
}

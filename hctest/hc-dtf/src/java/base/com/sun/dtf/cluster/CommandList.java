package com.sun.dtf.cluster;

import java.util.ArrayList;

import com.sun.dtf.cluster.ssh.SSHCommand;
import com.sun.dtf.exceptions.RemoteCmdException;

public class CommandList {

    private ArrayList _commands = null;
    
    public CommandList() { _commands = new ArrayList(); }
    
    public void addCommand(SSHCommand cmd) { 
        _commands.add(cmd);
    }
    
    public void disconnect() throws RemoteCmdException { 
        for(int i = 0; i < _commands.size(); i++)
            ((SSHCommand)_commands.get(i)).disconnect();
    }
}

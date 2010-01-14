package com.sun.dtf.cluster.ssh;

import java.io.IOException;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exceptions.RemoteCmdException;

import com.sshtools.j2ssh.connection.ChannelState;
import com.sshtools.j2ssh.session.SessionChannelClient;
import com.sshtools.j2ssh.util.InvalidStateException;

public class SSHCommand {
  
    private String _cmd = null;
    private SessionChannelClient _session = null;
    
    private boolean _clean = false;
    
    public SSHCommand(String cmd, 
                      SessionChannelClient session) throws RemoteCmdException {
        _cmd = cmd;
        _session = session;
    }
    
    public void disconnect() throws RemoteCmdException { 
        Action.getLogger().debug("Ending command [" + _cmd + "]");
        
        /*
         * XXX: part of the work around for the MessageStoreEOFException
         */
        if (_session != null) { 
            try {
                // by using a timeout we release the hold on some internal locks
                // by this call that lock out all other read/writes to the 
                // channel input/output streams.
                while (!_session.getState().waitForState(ChannelState.CHANNEL_CLOSED,1000));
                _session.close();
            } catch (IOException e) {
                throw new RemoteCmdException("Error closing session for command [" 
                                              + _cmd + "]",e);
            } catch (InvalidStateException e) {
                throw new RemoteCmdException("Error closing session for command [" 
                                              + _cmd + "]",e);
            } catch (InterruptedException e) {
                throw new RemoteCmdException("Error closing session for command [" 
                                              + _cmd + "]",e);
            }
        }
        _clean = true;
    }
    
    public int getExitStatus() {

        /*
         * XXX: part of the work around for the MessageStoreEOFException
         */
        if (_session == null) 
            return -1;
        
        Integer exitCode = _session.getExitCode();
        if (exitCode == null)
            return -1;
        else 
            return exitCode.intValue(); 
    }
    
    protected void finalize() throws Throwable {
        super.finalize();
        if (!_clean)  {
            Action.getLogger().warn("*** Object not cleaned up correctly: " + this);
            disconnect();
        }
    }
}

package com.sun.dtf.cluster;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.sun.dtf.cluster.ssh.SSHCommand;
import com.sun.dtf.cluster.ssh.SSHManager;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exceptions.NodeCommException;
import com.sun.dtf.exceptions.SSHException;

public abstract class RemoteNode {
    
    public static final String EXEC_CHANNEL = "exec";
    public static final String CAT_CMD = "cat";
    
    private SSHManager _sshManager = null;
    
    public RemoteNode(String host, String user, String pass, int port)
            throws NodeCommException {
        _sshManager = new SSHManager(host,user,pass,port);
    }
    
    protected SSHCommand execute(String cmd, 
                                 InputStream in,
                                 OutputStream out,
                                 OutputStream err) throws SSHException { 
        return _sshManager.execute(cmd,in,out,err);
    }
    
    /**
     * 
     * @param remotefile
     * @param out
     * @throws DTFException
     */
    public void scpFrom(String remotefile, OutputStream out) throws DTFException {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        
        SSHCommand cmd = _sshManager.execute(CAT_CMD + " " + remotefile,null,out,err);
        cmd.disconnect();
        
        if (cmd.getExitStatus() != 0) 
           throw new NodeCommException("Failure transfering file, return code: "
                                       + cmd.getExitStatus() + ", cause: " + err);
    }
    
    /**
     * 
     * @param in
     * @param remotefile
     * @throws DTFException
     */
    public void scpTo(InputStream in, String remotefile) throws DTFException {
        SSHCommand cmd = _sshManager.execute(CAT_CMD + " > " + remotefile,in,null,null);
        cmd.disconnect();
            
        if (cmd.getExitStatus() != 0) 
            throw new NodeCommException("Failure transfering file, return code: "
                                        + cmd.getExitStatus());
    }
    
    protected void copy(InputStream in, OutputStream out) 
              throws DTFException, IOException {
        BufferedInputStream bis = new BufferedInputStream(in);
        BufferedOutputStream bos = new BufferedOutputStream(out);
        int res;
        while ((res = bis.read()) != -1) {
            bos.write(res);
        }
        bis.close();
        bos.close();
    }
}

package com.sun.dtf.cluster.cli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.sun.dtf.cluster.ssh.SSHCommand;
import com.sun.dtf.cluster.ssh.SSHManager;

import com.sun.dtf.exceptions.RemoteCmdException;
import com.sun.dtf.exceptions.CLIException;
import com.sun.dtf.exceptions.SSHException;
import com.sun.dtf.util.ThreadUtil;

public class HCCLI extends CLI {

    // honeycomb default config file that comes from platform land
    private static final String HC_PRIVATE_KEY="lib/hc/config/id_dsa";
    public static final String EXEC_CHANNEL = "exec";
    
    private SSHManager _sshManager = null;
   
    public HCCLI(String adminvip, String user, String pass) throws CLIException { 
        _sshManager = new SSHManager(adminvip,user,pass,22);
        _sshManager.setPublickey(new File(HC_PRIVATE_KEY));
    }
    
    public String[] sysstat(String[] options) throws CLIException { 
        try { 
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            
            String command = putTogether("sysstat", options);
            SSHCommand cmd = _sshManager.execute(command, null, out, err);
            cmd.disconnect();
            
            if (cmd.getExitStatus() != 0) { 
                throw new CLIException("Error running sysstat: " + err);
            }
            return out.toString().split("\n");
        } catch (RemoteCmdException e) {
            throw new CLIException("Error running sysstat.", e);
        } catch (SSHException e) {
            throw new CLIException("Error running sysstat.", e);
        } 
    }
    
    public String[] df() throws CLIException { 
        try { 
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            
            String command = putTogether("df", new String[]{});
            SSHCommand cmd = _sshManager.execute(command, null, out, err);
            cmd.disconnect();
            
            if (cmd.getExitStatus() != 0) { 
                throw new CLIException("Error running sysstat: " + err);
            }
            return out.toString().split("\n");
        } catch (RemoteCmdException e) {
            throw new CLIException("Error running sysstat.", e);
        } catch (SSHException e) {
            throw new CLIException("Error running sysstat.", e);
        } 
    }
    
    public void reboot(String[] options) throws CLIException { 
        try { 
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
           
            String command = putTogether("reboot", options);
            SSHCommand cmd = _sshManager.execute(command, null, out, err);

            cmd.disconnect();
            if (cmd.getExitStatus() != 0) { 
                throw new CLIException("Error rebooting honeycomb: " + err);
                                       
            }
        } catch (RemoteCmdException e) {
            throw new CLIException("Error rebooting honeycomb.", e);
        } catch (SSHException e) {
            throw new CLIException("Error rebooting honeycomb.", e);
        } 
    }
    
    private String putTogether(String cmd, String[] options) { 
        StringBuffer result = new StringBuffer(cmd);
        
        if (options != null) { 
            for(int i = 0; i < options.length; i++) { 
                result.append(" " + options[i]);
            }
        }
        
        return result.toString();
    }
    
    public String[] mdconfig(String[] options, InputStream is) throws CLIException {
        try { 
            String command = putTogether("mdconfig", options);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
         
            SSHCommand cmd = _sshManager.execute(command, is, out, err);
          
            cmd.disconnect();
            
            if (cmd.getExitStatus() != 0) { 
                throw new CLIException("Error running mdconfig: " + 
                                       err);
            }
                   
            /*
             * XXX: 
             * yes we probably have a bug here as well we're throwing the 
             * exception back as a message and not actually failing th command
             * so that the ssh session would return a return code != 0.
             */
            String outstr = out.toString();
            if (outstr.indexOf("Exception") != -1) 
                throw new CLIException("Error running mdconfig: " + 
                                       out + "|" + err);
                    
            if (outstr.indexOf("session is read-only.") != -1) 
                throw new CLIException("Error running mdconfig: " + 
                                       out + "|" + err);
        
            // catches "Failed to upload" and "Failed to validate"
            if (outstr.indexOf("Failed") != -1) 
                throw new CLIException("Error running mdconfig: " + 
                                       out + "|" + err);

            return outstr.split("\n");
        } catch (RemoteCmdException e) {
            throw new CLIException("Error running mdconfig.", e);
        } catch (SSHException e) {
            throw new CLIException("Error running mdconfig.", e);
        } 
    }
    
    public String[] setprops(String name, String value) throws CLIException {
        try { 
            String command = putTogether("setprops", new String[]{"-F"});

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
          
            String prop = name + " = " + value + "\n\n";
            
            /*
             * XXX:
             * Total hack the CLI setprops command needed to have a delay 
             * between feeding the property = value and the y\n at the end so 
             * I delayed it by 10s in order to acheive this result.
             * 
             * TODO: Will be looking into making a better solution.
             */
            ByteArrayInputStream in = new ByteArrayInputStream(prop.getBytes()) {
                long start = -1;
                long wait = 10000;
                
                byte[] response = "y\n".getBytes();
                
                public synchronized int read() {
                    throw new RuntimeException("This method should not be called.");
                }
                
                public int read(byte[] b) throws IOException {
                    if (pos < count)  { 
                        return super.read(b);
                    } else { 
                         if (start == -1) 
                            start = System.currentTimeMillis();
                        
                        if (System.currentTimeMillis() - start < wait) {
                            ThreadUtil.pause(1000);
                            return 0;
                        } else {  
                            System.arraycopy(response, 0, b, 0, response.length);
                            return 2;
                        } 
                    }
                }
                
                public synchronized int read(byte[] b, int off, int len) {
                    if (pos < count)  { 
                        return super.read(b,off,len);
                    } else { 
                         if (start == -1) 
                            start = System.currentTimeMillis();
                        
                        if (System.currentTimeMillis() - start < wait) {
                            ThreadUtil.pause(1000);
                            return 0;
                        } else {  
                            System.arraycopy(response, 0, b, 0, response.length);
                            return 2;
                        } 
                    }
                }
            };
            
            SSHCommand cmd = _sshManager.execute(command, in, out, err);
            
            cmd.disconnect();
            
            if (cmd.getExitStatus() != 0) { 
                throw new CLIException("Error running setprops: " + err);
            }
            
            if (err.toString().indexOf("Properties have not been committed") != -1)
                throw new CLIException("Failed to setprops");
                   
            return out.toString().split("\n");
        } catch (RemoteCmdException e) {
            throw new CLIException("Error running mdconfig.", e);
        } catch (SSHException e) {
            throw new CLIException("Error running mdconfig.", e);
        } 
    }
}

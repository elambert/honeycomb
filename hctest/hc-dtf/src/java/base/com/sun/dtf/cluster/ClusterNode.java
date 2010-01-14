package com.sun.dtf.cluster;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.sun.dtf.actions.Action;
import com.sun.dtf.cluster.ssh.SSHCommand;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exceptions.NodeCommException;
import com.sun.dtf.util.ThreadUtil;

public class ClusterNode extends RemoteNode implements NodeInterface {

    public static final String SHA1_CMD         = "digest -a sha1";
    public static final String PERF_MONITOR_CMD = HCCluster.HC_NODE_SCRIPTDIR + 
                                                  File.separatorChar +
                                                  "hc-resource-stat";
    
    public static final String PKGINFO_CMD      = "pkginfo";
    public static final String MKDIR_CMD        = "mkdir";
    public static final String RM_CMD           = "rm";
    public static final String CHMOD_CMD        = "chmod";
    public static final String TAR_CMD          = "tar";
    public static final String LS_CMD           = "ls";
    public static final String PKILL_CMD        = "pkill";
    public static final String REBOOT_CMD       = "reboot";
    public static final String STARTHC_CMD      = 
                                    "/opt/honeycomb/etc/init.d/honeycomb start";
    
    public static final String PERF_MONITOR_OUT = "hc-resource-stat.out";
    public static final String PERF_MONITOR_ERR = "hc-resource-stat.err";
   
    public static final String CLASSPATH        = "/var/adm/test/lib/hc-dtfhook.jar";
    public static final String SNAPSHOT_CMD     =  "java -cp " + CLASSPATH + 
                                      "  com.sun.dtf.cluster.snapshot.Snapshot";

    public static final String FRAGLIST_CMD     =  "java -cp " + CLASSPATH + 
                                      "  com.sun.dtf.cluster.fragments.FragList";
    
    private int _nodeID = -1;
    
    public ClusterNode(String host, String user, String pass, int port)
            throws NodeCommException {
        super(host, user, pass, port);
        _nodeID = (port - HCCluster.SSH_ADMIN_BASE_PORT) + 100;
    }

    public String getId() { return ""+_nodeID; }

    /* (non-Javadoc)
     * @see com.sun.dtf.cluster.NodeInterface#hashFile(java.lang.String)
     */
    public String hashFile(String remotefile) throws DTFException { 
        Action.getLogger().info("Sha1 calculation on file  [" + remotefile + 
                                "] on node " + _nodeID);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SSHCommand cmd = executeCommand(SHA1_CMD + " " + remotefile,
                                        null,out,null);
        cmd.disconnect();
        return out.toString();
    }
   
    /* (non-Javadoc)
     * @see com.sun.dtf.cluster.NodeInterface#startPerformanceMonitor(int)
     */
    public void startPerformanceMonitor(int sampleRate) throws DTFException { 
        if (Action.getLogger().isDebugEnabled())
            Action.getLogger().debug("Starting perfmonitor on node [" + 
                                     _nodeID + "]");
       
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        
        // TODO: have to bg in a nicer way!
        SSHCommand cmd = executeCommandBG(PERF_MONITOR_CMD + " -d " + sampleRate + 
                                       " > " + HCCluster.HC_NODE_LOGDIR + 
                                       File.separatorChar + PERF_MONITOR_OUT + 
                                       " 2> " + HCCluster.HC_NODE_LOGDIR + 
                                       File.separatorChar +  PERF_MONITOR_ERR,
                                       null,out,err);
        
        cmd.disconnect();
        
        Action.getLogger().info("Node: " + _nodeID + "> " +out);
       
        if (cmd.getExitStatus() != 0) { 
            throw new DTFException("Error stopping performance monitoring " + 
                                   "on node " + _nodeID + ", cause: " +
                                   err + " with return code: " + 
                                   cmd.getExitStatus());
        }
    }
 
    /* (non-Javadoc)
     * @see com.sun.dtf.cluster.NodeInterface#stopPerformanceMonitor()
     */
    public void stopPerformanceMonitor() throws DTFException { 
        SSHCommand cmd = null;
       
        if (Action.getLogger().isDebugEnabled())
            Action.getLogger().debug("Shutdown perfmonitor on node [" + 
                                     _nodeID + "]");
        boolean stopped = false;
        while (!stopped) { 
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            
            cmd = executeCommand(PERF_MONITOR_CMD + " --stop",null,out,err);
            cmd.disconnect();
            
            Action.getLogger().info("Node: " + _nodeID + "> " +out);
         
            // we don't need to check the return code because failure to stop 
            // is acceptable since we'll just retry again below.
          
            // no reason in checking immediately when the default timeout for 
            // the hc-resource-stat script is 5s.  
            ThreadUtil.pause(5000);

            out = new ByteArrayOutputStream();
            err = new ByteArrayOutputStream();
            
            // wait for completed
            cmd = executeCommand("ps -ef | grep -v grep | grep hc-resource-stat",null,out,err);
            cmd.disconnect();

            Action.getLogger().info("Node: " + _nodeID + "> " +out);
            
            if (cmd.getExitStatus() != 0) 
                stopped = true;
            else { 
                Action.getLogger().info("Retry shutdown of perfmonitor on node [" + 
                                        _nodeID + "]");
            }
                
        }
    }
    
    /* (non-Javadoc)
     * @see com.sun.dtf.cluster.NodeInterface#collectPerfLog(java.lang.String, boolean)
     */
    public void collectPerfLog(String whereTo, boolean append) 
           throws DTFException { 
        Action.getLogger().debug("Collecting perf log from node " + _nodeID);
       
        String logname = HCCluster.HC_NODE_LOGDIR + 
                         File.separatorChar + 
                         PERF_MONITOR_OUT;
        
        try { 
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            
            FileOutputStream fos = new FileOutputStream(whereTo, append);
            SSHCommand command = executeCommand(CAT_CMD + " " + logname,null,fos,err);
            
            command.disconnect();
            
            if (command.getExitStatus() != 0)  { 
                Action.getLogger().error(err);
                throw new DTFException("Error collecting logs from node " + 
                                       _nodeID + ", cause " + err + " got return code " + 
                                       command.getExitStatus());
            } 
        } catch (FileNotFoundException e) { 
            throw new DTFException("Error collecting logs from " + _nodeID, e);
        }
    }
    
    protected void error(InputStream in) throws NodeCommException {
        try {
            byte[] tmp = new byte[16 * 1024];
            int i = in.read(tmp, 0, tmp.length);
            while (i != -1) {
                Action.getLogger().error(new String(tmp,0,tmp.length));
                i = in.read(tmp, 0, tmp.length);
            }
        } catch (IOException e) {
            throw new NodeCommException("Failed to communicate to node 101", e);
        }
    }
    
    public SSHCommand executeCommand(String cmd,
                                     InputStream in,
                                     OutputStream out,
                                     OutputStream err) throws DTFException {
        Action.getLogger().debug("Executing [" + cmd + "] on node " + _nodeID);
        return super.execute(cmd,in,out,err);
    }

    public SSHCommand executeCommandBG(String cmd,
                                       InputStream in,
                                       OutputStream out,
                                       OutputStream err) throws DTFException {
        Action.getLogger().debug("Executing [" + cmd + "] on node " + _nodeID + 
                                 " in background.");
        return super.execute("nohup " + cmd + " &",in,out,err);
    }
    
    public void executeCommandAndWait(String command,
                                      String operation) 
           throws DTFException { 
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        
        SSHCommand cmd = execute(command,null,out,err);
       
        String output = out.toString();
      
        cmd.disconnect();
       
        if (cmd.getExitStatus() != 0) { 
            throw new DTFException("Error trying to " + operation + ", on node "
                                   + _nodeID + " got return code: " + 
                                   cmd.getExitStatus() + ", cause: " + output);
        }
    }
    
    public void mkdir(String dir) throws DTFException { 
        executeCommandAndWait(MKDIR_CMD + " " + dir, "mkdir " + dir);
    }
    
    public boolean dirExists(String dir) throws DTFException { 
        SSHCommand cmd = executeCommand(LS_CMD + " " + dir,null,null,null);
        cmd.disconnect();
        
        if (cmd.getExitStatus() == 0) 
            return true;
        else 
            return false;
    }

    // TODO: whenever I want a performance boast I just need to workaround the
    //       fact that the adminvip nating is making the streaming of these 
    //       results quite slow. Right now being able to check on 1 Million 
    //       objects in under 5 minutes is good enough.
    public void collectFrags(String drive, String whereTo) throws DTFException {
        try { 
            String command = "for i in {0..9}; do ls /data/" + drive + 
                "/${i}?/??; done | grep _ | sort | gzip"; 
            FileOutputStream out = new FileOutputStream(whereTo);
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            SSHCommand cmd = executeCommand(command, null, out, err);
            cmd.disconnect();
            out.close();
        } catch (IOException e) { 
           throw new DTFException("Error collecting fragmetns.",e);
        }
    }
    
    public void rmdir(String dir) throws DTFException { 
        executeCommandAndWait(RM_CMD + " -fr " + dir, "rm " + dir);
    }
    
    public void chmod(String perm, String file) throws DTFException { 
        executeCommandAndWait(CHMOD_CMD + " " + perm + " " + file, 
                             "chmod " + file);
    }
    
    public void scpFrom(String remotefile, OutputStream out)
            throws DTFException {
        Action.getLogger().debug("Copying file  [" + remotefile + 
                                 "] from node " + _nodeID);
        super.scpFrom(remotefile, out);
    }

    public void scpTo(InputStream in, String remotefile) throws DTFException {
        Action.getLogger().debug("Copying file  [" + remotefile + 
                                 "] to node " + _nodeID);
        super.scpTo(in, remotefile);
    }
   
    public boolean packageInstalled(String packageName) throws DTFException { 
        try { 
            executeCommandAndWait(PKGINFO_CMD + " " + packageName,"pkginfo");
            return true;
        } catch (DTFException e) { 
            return false;
        }
    }

    public void pkillHoneycomb() throws DTFException { 
        executeCommandAndWait(PKILL_CMD + " -9 java ", "pkill honeycomb");
    }
   
    public void rebootOS() throws DTFException { 
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        
        SSHCommand cmd = executeCommand(REBOOT_CMD,null,out,err);
        cmd.disconnect();
      
        // reboot on command line returns -1 because it forcefully disconnects 
        // the ssh server
        if (cmd.getExitStatus() != -1) { 
            throw new DTFException("Error trying to reboot node "
                                   + _nodeID + " got return code: " + 
                                   cmd.getExitStatus() + ", cause: " + err);
        }
    }
    
    public void startHoneycomb() throws DTFException {
        executeCommandAndWait(STARTHC_CMD, "start honeycomb");
    }
    
    public void setDevMode() throws DTFException {
        executeCommandAndWait("touch /config/nohoneycomb /config/noreboot", 
                              "set devmode");
    }

    public void unSetDevMode() throws DTFException {
        executeCommandAndWait("rm -f /config/nohoneycomb /config/noreboot", 
                              "unset devmode");
    }
    
    public void snapshot(String type, 
                         String name,
                         String mode,
                         String disk) throws DTFException { 
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        
        SSHCommand cmd = executeCommand(SNAPSHOT_CMD + " " + 
                                     mode + " " + 
                                     name +  " " + 
                                     type + " " + 
                                     (disk == null ? "-1" : disk),
                                     null,out,err);
        cmd.disconnect();
        
        Action.getLogger().info("Node " + _nodeID + ": " + out);
        if (cmd.getExitStatus() != 0) { 
            Action.getLogger().info("error: " + err);
            throw new DTFException("Snapshot command failed [" + err + "]");
        }
    }
    
    public void snapshotPreCondition(String type,
                                     String name,
                                     String mode,
                                     String disk) 
           throws DTFException { 
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        
        SSHCommand cmd = executeCommand(SNAPSHOT_CMD + " " +
                                     mode + " " + 
                                     name +  " " + 
                                     " precheck " + 
                                     (disk == null ? "-1" : disk),
                                     null,out,err);
        
        cmd.disconnect();
        
        Action.getLogger().info("Node " + _nodeID + ": " + out);
        if (cmd.getExitStatus() != 0) { 
            Action.getLogger().info("error: " + err);
            throw new DTFException("Snapshot command failed [" + err + "]");
        }
    }
}

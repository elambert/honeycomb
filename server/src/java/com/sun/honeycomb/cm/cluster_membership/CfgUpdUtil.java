/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



package com.sun.honeycomb.cm.cluster_membership;

import java.io.FilenameFilter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.posix.StatFS;
import com.sun.honeycomb.config.ConfigChecksumException;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Connect;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.ConnectResponse;


public class CfgUpdUtil {

    protected static final Logger logger =
        Logger.getLogger(CfgUpdUtil.class.getName());
    
    private static final String CFGNAME_SEP = "."; // <name>.<timestamp>
    private static final String WIPE_SEP = "wiped."; // <name>.wiped.<timestamp>
    
    private static CfgUpdUtil cfgUtil = null;
    
    public static synchronized CfgUpdUtil getInstance() {

        if (cfgUtil == null) {
            cfgUtil = CMM.getCfgUpdUtilImpl();
        }
        return cfgUtil;
    }
    
    /*******************
     * Config Update API
     ********************/
    
    public long getVersion(CMMApi.ConfigFile cfg) {
        
        if (cfg == null) {
            return 0;
        }        
        String token = null;
        try {
            String fpath = cfg.name();
            if (fpath == null) {
                return(0);
            }
            File origConfig = new File(fpath);
            if (!origConfig.exists()) {
                fine("The [" + origConfig.getAbsolutePath()
                     +"] file couldn't be found"
                     );
                return(0);
            }
            String filename = origConfig.getCanonicalFile().getName();
            String[] tokens = filename.split("\\.");
            if (tokens.length != 3) {
                severe("Configuration filename has a bad format ["+
                       filename+"]");
                return(0);
            }
            token = tokens[2];
            
        } catch (IOException e) {
            severe("Failed to get the configuration version number for " 
                   + cfg + " ["+ e.getMessage() + "]"
                   );
            return(0);
        }
        
        long result = 0;
        try {
            if (token != null) {
                result = Long.parseLong(token);
            }
        } catch (NumberFormatException e) {
            logger.severe("Bad configuration version ["+token+"]");
            return(0);
        }
        return result;
    }    
    
    public long getWipedVersion(CMMApi.ConfigFile cfg) {
        
        if (cfg == null) {
            return 0;
        }
        String fpath = cfg.name();
        if (fpath == null) {
            return 0;
        }
        File cfgFile = new File(fpath);
        String prefix = cfgFile.getName() + CFGNAME_SEP + WIPE_SEP;            
        File[] list = cfgFile.getParentFile().listFiles
                    (new ConfigFileFilter(prefix));
        
        if (list == null) {
            return 0;
        }
        long result = 0;
        for (int i = 0; i < list.length; i++) {
            String str = list[i].getName().substring(prefix.length() + 1);
            try {
                if (str != null) {
                    long local = Long.parseLong(str);
                    if (local > result) {
                        result = local;
                    }
                }
            } catch (NumberFormatException e) {
                severe("failed to get version from " + list[i].getName());
            }
        }
        return result;
    }
    
    public File getFile(CMMApi.ConfigFile fileToUpdate, long version) {
        
        String filename = (fileToUpdate != null)? fileToUpdate.name():null;
        if (filename == null) {
            return null;
        }
        
        File f = new File(filename + CFGNAME_SEP + version);
        if (!f.exists()) {
            return null;
        }
        return f;
    }        
    
    /***************************************************
     * Config Update implementation (package restricted)
     ***************************************************/
    
    protected List fetchConfigFiles(ConnectResponse msg) {
        
        Node node = msg.getNodeSource();
        Connect.ConfigVersion[] remote = msg.getConfigVersions();
        Connect.ConfigVersion[] local = msg.getConnectMsg().getConfigVersions();

        if (local.length != remote.length) {
            warning("Number of config files mismatch with node " + node);
        }
        
        ArrayList updatedFiles = new ArrayList();
        for (int i = 0; i < local.length && i < remote.length; i++) {
            if (local[i] == null) {
                if (remote[i] == null) {
                    continue;
                }
            } else if (remote[i] != null) {
                if (remote[i].version <= local[i].version) {
                    continue;
                }
            } else {
                continue;
            }
            
            CMMApi.ConfigFile cfg = CMMApi.ConfigFile.get(i);
            info("Fetching config file " + cfg + " from " + node);
            
            if (remote[i].wiped) {
                if (!wipeConfig(cfg, remote[i].version)) {
                    severe("Failed to wipe local config for "+ cfg);
                    continue;
                }
            } else if (fetchConfigFile(node, cfg, remote[i].version)) {
                if (activate(cfg, remote[i].version) ) {
                    info("The local configuration " + cfg +
                         " has been updated with version "+ remote[i]
                         );
                } else {
                    severe("Failed to activate the new config " +
                           remote[i] + " for config file " + cfg
                           );
                    continue;
                }
            } else {
                severe("Failed to fetch config file version " +
                       remote[i] + " for config " + cfg
                       );
                continue;
            }
            updatedFiles.add(cfg);
        }
        return updatedFiles;
    }
    
    protected boolean fetchConfigFile(Node copyFrom, 
                                      CMMApi.ConfigFile fileToUpdate, 
                                      long version) 
    {
        String configFile = (fileToUpdate != null)? fileToUpdate.name():null;
        if (configFile == null) {
            warning("Tried to fetch a config update with " 
                    + "a bad fileToUpdate ["+ fileToUpdate + "]"
                    );
            return(false);
        }
        configFile +=  CFGNAME_SEP + version;
        
        if (copyFrom == null) {
            warning("Tried to perform a config update with " 
                    + "a null node source "
                    );
            return(false);
        }

        StringBuffer cmd = new StringBuffer();
        cmd.append( "/usr/bin/rcp -p ");
        cmd.append(copyFrom.getHost());
        cmd.append(":");
        cmd.append(configFile);
        cmd.append(" ");
        cmd.append(configFile);

        fine(cmd.toString());

        try {
            int exitCode = Exec.exec(cmd.toString(), logger);
            if (exitCode != 0) {
                severe("unable to fetch config ["+
                       configFile+"], code=" + exitCode);
                return false;
            }
        } catch (Exception e) {
            severe("unable to fetch config ["+configFile+"] ! " 
                   + e.toString(), e);
            return false;
        }
        
        // Checksum the file
        boolean success = false;
        if (fileToUpdate == CMMApi.UPDATE_DEFAULT_FILE) {
            try {
                File openConfigFile = new File(configFile);       
                ClusterProperties.parseConfigFile(openConfigFile, true);
                success = true;
            } catch (IOException e) {
                warning("IO error loading config update:" + e.toString());
            } catch (ConfigChecksumException e) {
                warning("Checksum exception loading config update: " + e);
            } catch (Exception e) {
                severe("Unknown error loading config update!", e);
            }
        } else {
            success = true;
        }

        info("Copied and checksummed configuration update version: " + version);
        return success;
    }
    
    protected boolean writeConfigFile(CMMApi.ConfigFile fileToUpdate,
                                      long version,
                                      byte[] buffer)
    {
        String filename = (fileToUpdate != null)? fileToUpdate.name():null;
        if (filename == null || buffer == null) {
            warning("Tried to store a config update with " 
                    + "a bad fileToUpdate or buffer ["+ fileToUpdate + "]"
                    );
            return(false);
        }
        
        boolean result = false;
        FileOutputStream fos = null;
        try {
            File f = new File(filename + CFGNAME_SEP + version);
            f.createNewFile();
            fos = new FileOutputStream(f);
            fos.write(buffer);
            result = true;
            
        } catch (IOException e) {
            severe("Failed to create the wipe timestamp file ["+e.getMessage()+"]");
            
        } finally {
            if (fos != null) {
                try { fos.close(); } catch(IOException ignore) {}
            }
        }
        
        // 
        // Checksum - we should checksum the file if this is the cluster
        // config properties file. This code is disabled for now.
        
        return result;
    }
    
    /**
     * activate the config - create the symlink
     * This method must be MT SAFE
     * WARNING - symlink() is not atomic
     */
    protected synchronized boolean activate(CMMApi.ConfigFile fileToUpdate, long version) 
    {
        boolean result;
        String link = (fileToUpdate != null)? fileToUpdate.name():null;
        if (link == null) {
            warning(" Tried to perform a config activate with " 
                    + "a bad fileToUpdate ["+ fileToUpdate + "]"
                    );
            return(false);
        }
        
        String configFile = link + CFGNAME_SEP + version;
        int errno = StatFS.createSymLink(configFile, link);
        if (errno != 0) {
            severe("failed to create symlink for " + fileToUpdate
                   + " version " + version + " errno = " + errno
                   );
            result = false;
        } else {
            result = true;
        }

        info("Activated (post commit) config update version: " +version);
        return result;
    }

    protected boolean wipeConfig(CMMApi.ConfigFile fileToUpdate, long version) {

        boolean result = true;
        String filename = (fileToUpdate != null)? fileToUpdate.name():null;
        if (filename == null) {
            warning("Tried to perform a wipe config with " 
                    + "a bad fileToUpdate ["+ fileToUpdate + "]"
                    );
            return(false);
        }
        
        File config = new File(filename);
        File[] files = config.getParentFile().listFiles(
            new ConfigFileFilter(config.getName()));

        for (int i=0; i<files.length; i++) {
            if (!files[i].delete()) {
                result = false;
                warning("Failed to delete ["+files[i].getAbsolutePath()+"]");
            } else {
                info("Deleted ["+files[i].getAbsolutePath()+"]");
            }
        }
        
        try {
            new File(filename + CFGNAME_SEP + WIPE_SEP + version).createNewFile();            
        } catch (IOException e) {
            severe("Failed to create the wipe timestamp file ["+e.getMessage()+"]");
            result = false;
        }
        return result;
    }
  
    protected void purgeConfig(CMMApi.ConfigFile fileToPurge) {
        
        String fpath = (fileToPurge != null) ?fileToPurge.name():null;
        if (fpath == null) {
            warning("bad config file " + fileToPurge);
            return;
        }
        
        File cfgFile = new File(fpath);
        String prefix = cfgFile.getName() + CFGNAME_SEP;            
        File[] list = cfgFile.getParentFile().listFiles
            (new ConfigFileFilter(prefix));
        
        if (list == null) {
            return ;
        }
        if (list.length <= CMM.MAX_CONFIG_HISTORY) {
            return;
        }
        
        long[] versions = new long[list.length];
        for (int i = 0; i < list.length; i++) {
            String str = list[i].getName().substring(prefix.length());
            try {
                versions[i] = 0;
                if (str == null) {
                    continue;
                }
                if (str.startsWith(WIPE_SEP)) {
                    continue;
                }
                versions[i] = Long.parseLong(str);

            } catch (NumberFormatException e) {
                severe("failed to get version from " + list[i].getName());
            }
        }
        
        Arrays.sort(versions);
        long curvers = getVersion(fileToPurge);
        
        int count = versions.length - CMM.MAX_CONFIG_HISTORY;
        for (int i = 0; i < count; i++) {
            
            if (curvers == versions[i]) {
                severe("trying to delete current version " + prefix 
                       + " version " + curvers);

            } else if (versions[i] != 0) {
                
                File f = new File(fpath + CFGNAME_SEP + versions[i]);
                info("deleting " + f.getAbsolutePath());
                if (!f.delete()) {
                    warning("failed to delete " + f.getAbsolutePath());
                }
            }
        }
    }
    
    protected void recoverConfig(CMMApi.ConfigFile cfg) {
        if (getVersion(cfg) != 0) {
            return;
        }
        String fpath = (cfg != null) ?cfg.name():null;
        if (fpath == null) {
            warning("bad config file " + cfg);
            return;
        }
        
        File cfgFile = new File(fpath);
        String prefix = cfgFile.getName() + CFGNAME_SEP;            
        File[] list = cfgFile.getParentFile().listFiles
            (new ConfigFileFilter(prefix));
        
        if (list == null) {
            return ;
        }
        
        long lastVersion = 0;
        for (int i = 0; i < list.length; i++) {
            String str = list[i].getName().substring(prefix.length());
            try {
                if (str == null) {
                    continue;
                }
                if (str.startsWith(WIPE_SEP)) {
                    continue;
                }
                long version = Long.parseLong(str);
                if (version > lastVersion) {
                    lastVersion = version;
                }                
            } catch (NumberFormatException e) {
                severe("failed to get version from " + list[i].getName());
            }
        }
        if (lastVersion != 0) {
            warning("recovering config " + cfg + " to version " + lastVersion);
            activate(cfg, lastVersion);
        }
    }
    
    protected void purgeAllConfigs() {
        
        for (int i = 0; i < CMMApi.CFGFILES.size(); i++) {
            CMMApi.ConfigFile cfg = (CMMApi.ConfigFile) CMMApi.CFGFILES.get(i);
            purgeConfig(cfg);
        }
    }
            
    protected long createFile(CMMApi.ConfigFile fileToUpdate, Map newProps)
        throws ServerConfigException 
    {
        if (fileToUpdate == CMMApi.UPDATE_DEFAULT_FILE) {
            return createConfigFile(newProps);
        } else {
            throw new ServerConfigException("unsupported API for this file");
        }
    }

    /******************
     * private methods
     ******************/

    public static String generateComments(long timestamp) { 
        StringBuffer comments = new StringBuffer();
        comments.append("[0000000000000000 ");
        comments.append(timestamp + " ]");
        return comments.toString();

    }
    
    private long createConfigFile(Map newProps) throws ServerConfigException {

        Properties curProps = new Properties();
        ClusterProperties.load(curProps);
        long timestamp = System.currentTimeMillis();
        curProps.setProperty("honeycomb.config.version", String.valueOf(timestamp));
        curProps.putAll(newProps);

        FileOutputStream fos = null;
        try {
            do {
                String newConfig;
                newConfig = ClusterProperties.CLUSTER_CONF_FILE+"."+timestamp;
                File newConfigFile = new File(newConfig);
                if (newConfigFile.exists()) {
                    timestamp += 1;
                } else {
                    fos = new FileOutputStream(newConfigFile, false);
                }
            } while (fos == null);
            curProps.store(fos, generateComments(timestamp));
        } catch (IOException ioe) {
            throw new ServerConfigException("Failed to create the " + 
                                            "configuration file", ioe);
        } finally {
            try {
                fos.close();
            } catch(IOException ignore) {
            }
        }
        return timestamp;
    }

    private static class ConfigFileFilter implements FilenameFilter {

        private String prefix = null;

        public ConfigFileFilter(String prefix) {
            this.prefix = prefix;
        }
        
        public boolean accept(File dir, String name) {
            return(name.startsWith(prefix));
        }
    }
    
    /**
     * log fine
     */
    private static void fine(String msg) {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, CMMApi.LOG_PREFIX + msg);
        }
    }
    
    /**
     * log info
     */
    private static void info(String msg) {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, CMMApi.LOG_PREFIX + msg);
        }
    }
    
    /**
     * log warning
     */
    private static void warning(String msg) {
        if (logger.isLoggable(Level.WARNING)) {
            logger.log(Level.WARNING, CMMApi.LOG_PREFIX + msg);
        }
    }
    
    /**
     * log severe
     */
    private static void severe(String msg) {
        if (logger.isLoggable(Level.SEVERE)) {
            logger.log(Level.SEVERE, CMMApi.LOG_PREFIX + msg);
        }
    }
    
    private static void severe(String msg, Exception e) {
        if (logger.isLoggable(Level.SEVERE)) {
            logger.log(Level.SEVERE, CMMApi.LOG_PREFIX + msg, e);
        }
    }
    
}

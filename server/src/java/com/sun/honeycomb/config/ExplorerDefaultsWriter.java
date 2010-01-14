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

 

package com.sun.honeycomb.config;

import com.sun.honeycomb.admin.mgmt.server.HCExpProps;
import com.sun.honeycomb.common.ConfigPropertyNames;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * This class is responsible for creating the explorer tool's defaults file. 
 * The logdump CLI command simply executes the explorer script from the master
 * node so the defaults file is required in order to to collect log info.  
 * Normally, the defaults file would be created by running the following 
 * once to configure the explorer tool:
 *                  /opt/SUNWexplo/bin/explorer -g
 * However, the defaults file must be generated EACH time the logdump command
 * is run to handle the following possibilities:
 * <UL>
 *   <LI> master node has changed and the explorer script is being kicked off
 *        from a different node which doesn't have a defaults file
 *   <LI> node has been replaced and doesn't have a defaults file
 *   <LI> defaults file information has changed and needs to be easily updated
 * </UL>
 * The default file's parameters are located in the cluster config properties
 * file on the master cell.  Most of the values are static and are not updated 
 * during logdump configuration.  The values that are set dynamically:
 * <UL>
 *   <LI> https proxy server
 *   <LI> https proxy port
 *   <LI> geographic location - THIS IS REQUIRED AND MUST BE SET
 *   <LI> contact name
 *   <LI> contact phone
 *   <LI> contact email
 * </UL>
 * The only required settable parameter is the geographic location.  Once the
 * cluster config properties file on the master cell is updated after 
 * configuration, this class is used to read those properties and create the 
 * explorer defaults file under /opt/honeycomb/share on the master node 
 * for each cell.
 */
public class ExplorerDefaultsWriter {
    
    // These are the explorer default file keys 
    public static final String KEY_DEF_VER = "EXP_DEF_VERSION";
    public static final String KEY_PATH = "EXP_PATH";   
    public static final String KEY_LIB = "EXP_LIB";   
    public static final String KEY_HTTPS_TRANSPORT = "EXP_TRANSPORT";   
    public static final String KEY_HTTPS_TRIES = "EXP_HTTPS_TRIES";    
    public static final String KEY_HTTPS_RETRY_INT = "EXP_HTTPS_RETRY_INTERVAL";
    public static final String KEY_CONTACT_NAME = "EXP_USER_NAME";
    public static final String KEY_CONTACT_PHONE = "EXP_PHONE";   
    public static final String KEY_CONTACT_EMAIL = "EXP_USER_EMAIL";
    public static final String KEY_HTTPS_PROXY = "EXP_HTTPS_PROXY";
    public static final String KEY_GEO = "EXP_GEO";
    
    private static final String EXPLORER_PATH = "/opt/honeycomb/share";
    
    private static transient final Logger logger = 
        Logger.getLogger(ExplorerDefaultsWriter.class.getName());
    
    private static HCExpProps expProps = null;

    /**
     * Static method to create the explorer defaults file under 
     * /opt/honeycomb/share/explorer.  The explorer configuration properties 
     * from the master cell are passed into this class in order to create 
     * the correct explorer defaults file for each cell.
     * @param eProps explorer defaults from master cell
     * @return boolean indicates whether or not the explorer defaults file was
     *         successfully created, otherwise false.
     */
    public static boolean createDefaultsFile(HCExpProps eProps) {
        File file = null;
        expProps = eProps;
        try {
            // creates the file "explorer"
            file = new File(EXPLORER_PATH + "/explorer");
            if (file.exists()) {
                file.delete();
            }
            boolean success = file.createNewFile();
            if (!success) {
                logger.log(Level.SEVERE, 
            "Failed to create explorer defaults file in " + EXPLORER_PATH);
                return false;
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, 
                "IO Error while creating " + EXPLORER_PATH + "/explorer: " + e);
            return false;
        }  
        writeToFile(file);
        return true;
    }
    /**
     * Static method to create the explorer defaults file for TEST PURPOSES
     * ONLY within the specified path since on the nodes, the file is created
     * under /etc/opt/SUNWexplo/default for the emulator.
     * @param eProps explorer defaults from master cell
     * @param path the full path where the test defaults file should be created
     * @return boolean indicates whether or not the explorer defaults file was
     *         successfully created, otherwise false.
     */
    public static boolean createDefaultsTestOnly(String path, 
                                                        HCExpProps eProps) {
        File file = null;
        expProps = eProps;
        try {
            // creates the file "explorer" under path
            file = new File(path + "/explorer");
            if (file.exists()) {
                file.delete();
            }
            boolean success = file.createNewFile();
            if (!success) {
                logger.log(Level.SEVERE, 
                        "Failed to create explorer defaults file under " + 
                        path);
                return false;
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, 
                        "IO Error while creating " + path + "/explorer: " + e);
            return false;
        } catch (Exception ex) {
            logger.log(Level.SEVERE, 
                    "Exception while creating " + path + "/explorer: " + ex);
            return false;
        }
        writeToFile(file);
        return true;
    }
    private static Properties loadData() {
        ClusterProperties config = ClusterProperties.getInstance();
        Properties values = new Properties();
        
        // STATIC VALUES -- retrieve from local cluster config props
        values.setProperty(KEY_HTTPS_TRANSPORT, config.getProperty(
                        ConfigPropertyNames.PROP_LOGDUMP_EXP_TRANSPORT));
        values.setProperty(KEY_HTTPS_TRIES, config.getProperty(
                        ConfigPropertyNames.PROP_LOGDUMP_EXP_HTTPS_TRIES));
        values.setProperty(KEY_PATH, config.getProperty(
                        ConfigPropertyNames.PROP_LOGDUMP_EXP_PATH));
        values.setProperty(KEY_LIB, config.getProperty(
                        ConfigPropertyNames.PROP_LOGDUMP_EXP_LIB));
        values.setProperty(KEY_DEF_VER, config.getProperty(
                        ConfigPropertyNames.PROP_LOGDUMP_EXP_VERSION));
        values.setProperty(KEY_HTTPS_RETRY_INT, config.getProperty(
                        ConfigPropertyNames.PROP_LOGDUMP_EXP_HTTPS_INTERVAL));
        
        // DYNAMIC VALUES -- retrieve from master cell's config file since
        // user updates/sets these values before kicking off the explorer script
        values.setProperty(KEY_GEO, expProps.getGeoLocale());
        values.setProperty(KEY_CONTACT_NAME, expProps.getContactName());
        values.setProperty(KEY_CONTACT_PHONE, 
                                        expProps.getContactPhone().toString());
        values.setProperty(KEY_CONTACT_EMAIL, expProps.getContactEmail());
        String server = expProps.getProxyServer();
        String port = expProps.getProxyPort().toString();
        if (server.length() != 0) {
            values.setProperty(KEY_HTTPS_PROXY, server + ":" + port);
        }
        return values;
    }
    private static String[] parseProperties(Properties props) {
        int cnt = 0;
        String[] valPairs = new String[props.entrySet().size()];
        for (Iterator iter = props.entrySet().iterator(); iter.hasNext(); ) {          
            Map.Entry entry = (Map.Entry)iter.next();
            String key = (String)entry.getKey();
            String value = (String)entry.getValue();
            valPairs[cnt] = key + "=\"" + value + "\"\n";
            cnt++;
        }
        return valPairs;       
    }
    private static void writeToFile(File file) {
        BufferedWriter output = null;
        try {
            output = new BufferedWriter(new FileWriter(file));
            output.write("#!/bin/ksh -p\n");
            output.write("# Explorer defaults file\n");
            output.write("# This file is Korn shell source and is read by the explorer program\n\n");
            String[] values = parseProperties(loadData());
            for (int idx = 0; idx < values.length; idx++) {
                output.write(values[idx]);
                output.newLine();
            }
            logger.log(Level.INFO, "Explorer defaults file has been written");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "IO Error while writing to: " +
                                                                file.getPath());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Exception writing to file: " + 
                                                                e.getMessage());
        } finally {
            try {
                if (output != null) {
                    output.flush();
                    output.close();
                }
            } catch (IOException ignore) {
                logger.log(Level.SEVERE, "IOException in finally block: " + 
                                                        ignore.getMessage());
            } catch (Exception eIgnore) {
                logger.log(Level.SEVERE, "Exception in finally block: " + 
                                                        eIgnore.getMessage());
            }
        }
    }
}

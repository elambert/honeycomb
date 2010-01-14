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



package com.sun.honeycomb.common;

import com.sun.honeycomb.config.ClusterProperties;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.File;
import java.util.logging.Logger;

/**
 * The SoftwareVersion class defines the version of the
 * Honeycomb software.
 */

public class SoftwareVersion {

    /**
     * Version of this software
     */
    public static final String name = "CHARTER";

    /**
     * UID for serialization
     */
    public static final long serializeUID = 0x1; 

    private static Logger log = 
	Logger.getLogger(SoftwareVersion.class.getName());

    private SoftwareVersion() {
    }

    static final String HC_VERSION_FILE   =  "/opt/honeycomb/version";
    static final String HC_EMULATOR_VERSION_FILE   =  "/config/version";
    static final String HC_VERSION_CONFIG = "honeycomb.software.version";
    static final String HC_VERSION_CHECK = 
        "honeycomb.disable.version.checking";
    static final String UPGRADE_FLAG = "/tmp/inupgrade";

    /* Gets the software version from the config */
    public static String getConfigVersion() {
        ClusterProperties properties = ClusterProperties.getInstance();
        String version = properties.getProperty(HC_VERSION_CONFIG);
        log.info("getConfigVersion sees version="+version);
        return version;
    }

    private static String getRunningVersionFileName() {
        // Check if we run in the emulator
        String emulatorRoot = System.getProperty("emulator.root");
        if (emulatorRoot != null)
            return emulatorRoot+HC_EMULATOR_VERSION_FILE;
        return HC_VERSION_FILE;
    }


    /* Gets the software version from the ramdisk version file */
    /* TODO:  Deal with error path  */
    public static String getRunningVersion() {
        File version_file = new File(getRunningVersionFileName());
        if (! version_file.exists()) {
            log.info (version_file + " does not exist.");
            return null;
        }

        BufferedReader in = null;
        String line = null;
        try {
            in = new BufferedReader (new FileReader(version_file));
            line = in.readLine();
            
            // the 1st line of the version file looks like:
            // Honeycomb release [1.1-10]
            // The version string is 1.1-10

            int start = line.indexOf ("[") + 1;
            int end   = line.indexOf ("]");
            String version_string = line.substring (start, end);
            log.info("getRunningVersion sees version_string="+
                     version_string);
            return version_string;

        } catch (Exception e) {
	    log.severe("Error reading " + HC_VERSION_FILE+" = "+
                   line);
	    return null;
        }
        finally {
            if (in != null) {
                try { in.close(); } catch (Exception e) { ; }
            }
        }
    }

    /* Check if the config and ramdisk versions match */
    public static boolean checkVersionMatch() {
	/* Check if version checking is enabled */
	if (doVersionChecking()) {
	    String config = getConfigVersion();
	    String ramdisk = getRunningVersion();
	    
	    if ((config == null) || (ramdisk == null)) {
		return false;
	    }
	    
	    if (config.equals(ramdisk)) {
		log.info ("Config and ramdisk versions match");
		return true;
	    }
	    else {
		log.warning ("Version mismatch.  Config version " + config +
			     " does not match ramdisk version " + ramdisk);
		return false;
	    }
	}
	else {
	    /* if version checking is not enabled, just return true */
	    log.info ("Version checking disabled");
	    return true;
	}
    }

    /* Check if version checking is enabled */
    public static boolean doVersionChecking() {
	ClusterProperties properties = ClusterProperties.getInstance();
	String check = properties.getProperty(HC_VERSION_CHECK);

	/* First check if the upgrade flag is present.  This should
	   only be set in the special case at the end of an upgrade
	   so changing the config version before rebooting to the new
	   ramdisk will not cause the node to power down.  Version
	   checking is disabled if this flag is present. The flag will
	   be reset on reboot/in the honeycomb init script.
	*/

	File flag = new File (UPGRADE_FLAG);
	if (flag.exists()) {
	    log.info("In upgrade mode, version checking disabled");
	    return false;
	}
	
	/* Since the property is honeycomb.disable.version.checking,
	   if the property = true, that means version checking should
	   be disabled (ie, do not do version checking, return false)
	   if the property = false, that means version checking should
	   be enabled (ie, do version checking, return true)
	*/

	if (check.equals("false")) {
	    return true;
	}
	else {
	    return false;
	}
    }
}

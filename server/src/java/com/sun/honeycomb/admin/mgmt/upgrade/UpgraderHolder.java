package com.sun.honeycomb.admin.mgmt.upgrade;

import java.net.URL;
import java.io.*;
import com.sun.honeycomb.config.ClusterProperties;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.honeycomb.mgmt.common.MgmtException;

/**
 * Singleton class to hold the loaded Upgrader class. Utilized since
 * HCUpgradeAdapter is recreated with every method invocation, and can
 * not be used to hold state. Also ensures only one Upgrader is loaded
 * at once.
 */
public class UpgraderHolder {

    private static UpgraderHolder instance = null;
    private UpgraderInterface upgrader = null;

    private static final String JAR_FILE_PROP = "honeycomb.upgrade.jar.downloadfile";
    private static final String UPGRADE_CLASS_PROP = "honeycomb.upgrade.classname";

    private static final Logger logger = 
        Logger.getLogger(UpgraderHolder.class.getName());

    public static UpgraderHolder getInstance() throws MgmtException {
	if (instance == null) {
	    instance = new UpgraderHolder();
	}
	return (instance);
    }

    /* 
     * Method to reset the Upgrader when finished with it
     */
    public void reset() {
	instance = null;
    }

    /* 
     * Method to access the held Upgrader class
     * @return UpgraderInterface the loaded upgrader class
     */
    public UpgraderInterface getUpgrader() {
	return upgrader;
    }

    /* 
     * Private constructor
     */
    private UpgraderHolder() throws MgmtException {
	loadUpgrader();
    }

    /*
     * Private method by which the new Upgrader class is loaded from
     * the new upgrade jar, located at an expected location.
     */
    private void loadUpgrader() throws MgmtException {	
	try {
	    ClusterProperties confs = ClusterProperties.getInstance();
	    String jarfile = confs.getProperty(JAR_FILE_PROP);
	    String upgradeclass = confs.getProperty(UPGRADE_CLASS_PROP);
	    URL[] urls = {new File(jarfile).toURL()};
	    UpgraderClassLoader cl = new UpgraderClassLoader (urls);
	    Class<? extends UpgraderInterface> classOf =
		cl.loadClass(upgradeclass).
		asSubclass(UpgraderInterface.class);
	    upgrader = classOf.newInstance();
	} catch (Exception e) {
	    logger.log(Level.SEVERE, "Failed to load upgrade class: ", e);
	    throw new MgmtException("Failed to load upgrade class: " + e.getMessage());
	}
    }
}

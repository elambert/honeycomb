package com.sun.honeycomb.admin.mgmt.upgrade;

import java.net.URL;
import java.net.URLClassLoader;
import java.lang.ClassNotFoundException;

/**
 * Wrapper subclass of URLClassLoader to make the URLClassLoader's
 * findClass method public for use by upgrade to find and load the
 * dynamic upgrade jar.
 */
public class UpgraderClassLoader extends URLClassLoader {
    /*
     * Constructor
     * @param urls - array of URL objects for the loader to search
     */
    public UpgraderClassLoader (URL[] urls) {
	super(urls);
    }

    /*
     * Wrapper method for URLClassLoader's findClass method
     * @param name - String representing the name of the class to be loaded
     * @return Class the loaded class
     */
    public Class findClass(String name) throws ClassNotFoundException {
	return super.findClass(name);
    }

}

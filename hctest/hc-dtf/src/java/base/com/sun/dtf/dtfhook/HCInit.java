package com.sun.dtf.dtfhook;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.impl.NoOpLog;

import com.sun.dtf.DTFNode;
import com.sun.dtf.actions.Action;
import com.sun.dtf.cluster.Cluster;
import com.sun.dtf.comm.CommClient;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.init.InitClass;
import com.sun.dtf.util.HCProperties;

public class HCInit implements InitClass {

    static { 
        // disable sshtools logging... 
        System.getProperties().setProperty(Log.class.getName(),NoOpLog.class.getName()); 
    }
    
    public HCInit() { }

    public void init() throws DTFException { 
        String dir = getClientDir();
        String version = getClientVersion(dir);
        
        /* Hackish setup of the classpath, but it WORKS! */ 
        initClasspath(dir, version);
        
        if (DTFNode.getType().equals("dtfa")) { 
            String type = Action.getConfig().getProperty(HCProperties.HC_CLUSTER_TYPE);
            
            if (type != null) { 
                Cluster.init();
            } else { 
                /* setup honeycomb specific client attributes for recording on tests side */
                CommClient.addAgentAttribute(HCProperties.HC_CLIENT_BUILD, dir);
                CommClient.addAgentAttribute(HCProperties.HC_CLIENT_VERSION, version);
                CommClient.addAgentAttribute(HCProperties.HC_CLIENT_TYPE, "java");
            }
        } 
    }
    
    private String getClientVersion(String dir) throws DTFException { 
        if (dir.indexOf("1.0") != -1) {
            return "1.0";
        } else if (dir.indexOf("1.1") != -1)  {
            return "1.1";
        } else { 
            if (dir.endsWith("cur"))
                return "cur";
            else 
                throw new DTFException("Unkown build directory format [" + dir + "]");
        }
    }
    
    private String getClientDir() { 
        String dir = System.getProperty(HCProperties.HC_CLIENT_BUILD);
        if (dir == null) { 
            dir = "lib/cur";
            Action.getLogger().info("Defaulting to [" + dir + "] client build.");
        }
        return dir;
    }
    
    public void initClasspath(String dir, String version) throws DTFException {
        try {
            char sep = java.io.File.separatorChar;
            Class[] parameters = new Class[] { URL.class };
            
            URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Class sysclass = URLClassLoader.class;
           
            Method method = sysclass.getDeclaredMethod("addURL", parameters);
            method.setAccessible(true);
           
            java.io.File file = null;
            
            // specific dtf action jar built for this version
            String cp = System.getProperty("java.class.path");
            
            file = new java.io.File("lib" + sep + "hc" + sep + "hc-dtf-" + version + ".jar");
            cp = cp + java.io.File.pathSeparatorChar + file.getAbsolutePath();
            
            if (Action.getLogger().isDebugEnabled())
                Action.getLogger().debug("Import [" + file.getAbsolutePath() + "].");
            
            method.invoke(sysloader, new Object[] { file.toURL() });
            
            file = new java.io.File(dir + sep + "lib" + sep + "honeycomb-client.jar");
            cp = cp + java.io.File.pathSeparatorChar + file.getAbsolutePath();
          
            if (Action.getLogger().isDebugEnabled())
                Action.getLogger().debug("Import [" + file.getAbsolutePath() + "].");

            method.invoke(sysloader, new Object[] { file.toURL() });
            System.setProperty("java.class.path",cp);
        } catch (IllegalArgumentException e) {
            throw new DTFException("Error, could not add URL to system classloader",e);
        } catch (MalformedURLException e) {
            throw new DTFException("Error, could not add URL to system classloader",e);
        } catch (IllegalAccessException e) {
            throw new DTFException("Error, could not add URL to system classloader",e);
        } catch (InvocationTargetException e) {
            throw new DTFException("Error, could not add URL to system classloader",e);
        } catch (SecurityException e) {
            throw new DTFException("Error, could not add URL to system classloader",e);
        } catch (NoSuchMethodException e) {
            throw new DTFException("Error, could not add URL to system classloader",e);
        }
    }
}

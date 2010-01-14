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



package com.sun.honeycomb.admin.mgmt.server;
import org.w3c.dom.Document;
import java.math.BigInteger;
import java.util.List;
import java.util.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.honeycomb.config.ClusterProperties;
import java.net.URLConnection;
import java.net.URL;
import java.net.MalformedURLException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import com.sun.honeycomb.admin.mgmt.Reassure;
import com.sun.honeycomb.admin.mgmt.MessageSender;
import com.sun.honeycomb.admin.mgmt.Utils;
import com.sun.honeycomb.admin.mgmt.upgrade.UpgraderInterface;
import com.sun.honeycomb.admin.mgmt.upgrade.UpgraderHolder;
import com.sun.honeycomb.common.CliConstants;

public class HCUpgradeAdapter
    implements  HCUpgradeAdapterInterface {

    public void loadHCUpgrade()
        throws InstantiationException {
    }

    private static final String SUID = "/opt/honeycomb/sbin/setuid";
    private static final String NEWMNT_PROP = "honeycomb.upgrade.image.dir";
    private static final String DVD_DOWNLOAD_PROP = "honeycomb.upgrade.dvd.downloadfile";
    private static final String JAR_FILE_PROP = "honeycomb.upgrade.jar.downloadfile";
    private static final String JAR_NAME = "honeycomb.upgrade.jarname";

    // Legacy buffer size from old upgrade code. Used by downloadJar, 
    // not sure why it's this size.
    private static final int BUFSIZE = 524288;
    private static transient final Logger logger = 
        Logger.getLogger(HCUpgradeAdapter.class.getName());

    /*
    * This is the list of accessors to the object
    */
    public BigInteger getDummy() throws MgmtException {
        return BigInteger.valueOf(0);
    }

    /*
     * This is the list of custom actions
     */
    public BigInteger mountIso(String spdvd) throws MgmtException {

        String cmd=null;
	logger.log(Level.INFO, "UPGRADE: Mounting upgrade image");
	ClusterProperties confs = ClusterProperties.getInstance();
	String newmnt = confs.getProperty(NEWMNT_PROP);

        if(spdvd.equals("true")) {
            cmd = SUID + " /opt/honeycomb/sbin/mntiso.sh spdvd " +
                newmnt;
        } else {
	    String downloadfile = confs.getProperty(DVD_DOWNLOAD_PROP);

            cmd = SUID + " /opt/honeycomb/sbin/mntiso.sh " +
                downloadfile  + " " + newmnt;
        }
	    
        // TODO: remove the image we downloaded in fail case
	int exitval = runCommand(cmd);
	if (exitval != 0) {
	    return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	} else {
	    return BigInteger.valueOf(CliConstants.UPGRADE_SUCCESS);
	}
    }

    public BigInteger uMountIso(String spdvd) throws MgmtException {
        String cmd=null;
	logger.log(Level.INFO, "UPGRADE: Unmounting upgrade image");
	ClusterProperties confs = ClusterProperties.getInstance();
	String newmnt = confs.getProperty(NEWMNT_PROP);

        if(spdvd.equals("true")) {
            cmd = SUID + " /opt/honeycomb/sbin/umntiso.sh spdvd " +
                newmnt;
        } else {
	    String downloadfile = confs.getProperty(DVD_DOWNLOAD_PROP);

            cmd = SUID + " /opt/honeycomb/sbin/umntiso.sh " +
                downloadfile  + " " + newmnt;
        }
	int exitval = runCommand(cmd);
	if (exitval != 0) {
	    return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	} else {
	    return BigInteger.valueOf(CliConstants.UPGRADE_SUCCESS);
	}
    }

    public BigInteger statusCheck(EventSender evt, BigInteger dummy) throws MgmtException {
	logger.log(Level.INFO, "UPGRADE: statusCheck invoked");
	UpgraderHolder uh = null;
	try {
	    uh = UpgraderHolder.getInstance();
	    // Call reset since this is the first place we're calling getInstance, to make 
	    // sure we get a fresh one.
	    uh.reset();
	    uh = UpgraderHolder.getInstance();
	} catch (MgmtException e) {
	    logger.log(Level.WARNING, "UPGRADE: Could not get upgrader singleton", e);
	    throw new MgmtException("Could not get upgrader singleton");
	}
	UpgraderInterface upgrader = uh.getUpgrader();
	return BigInteger.valueOf(upgrader.statusCheck(new MessageSender(evt)));
    }
    
    public BigInteger initializeUpgrader(BigInteger dummy) throws MgmtException {
	logger.log(Level.INFO, "UPGRADE: initializeUpgrader invoked");
	UpgraderHolder uh = null;
	try {
	    uh = UpgraderHolder.getInstance();
	} catch (MgmtException e) {
	    logger.log(Level.WARNING, "UPGRADE: Could not get upgrader singleton", e);
	    throw new MgmtException("Could not get upgrader singleton");
	}
	UpgraderInterface upgrader = uh.getUpgrader();
	return BigInteger.valueOf(upgrader.initialize());
    }
	    
    public String getNextQuestion(BigInteger dummy) throws MgmtException {
	logger.log(Level.INFO, "UPGRADE: getNextQuestion invoked");
	UpgraderHolder uh = null;
	try {
	    uh = UpgraderHolder.getInstance();
	} catch (MgmtException e) {
	    logger.log(Level.WARNING, "UPGRADE: Could not get upgrader singleton", e);
	    throw new MgmtException("Could not get upgrader singleton");
	}
	UpgraderInterface upgrader = uh.getUpgrader();
	return upgrader.getNextQuestion();
    }

    public String getConfirmQuestion(BigInteger dummy) throws MgmtException {
	logger.log(Level.INFO, "UPGRADE: getConfirmQuestion invoked");
	UpgraderHolder uh = null;
	try {
	    uh = UpgraderHolder.getInstance();
	} catch (MgmtException e) {
	    logger.log(Level.WARNING, "UPGRADE: Could not get upgrader singleton", e);
	    throw new MgmtException("Could not get upgrader singleton");
	}

	UpgraderInterface upgrader = uh.getUpgrader();
	return upgrader.getConfirmQuestion();
    }

    public BigInteger invokeNextMethod(EventSender evt, BigInteger answer) 
	throws MgmtException {
	logger.log(Level.INFO, "UPGRADE: invokeNextMethod invoked");
	UpgraderHolder uh = null;
	try {
	    uh = UpgraderHolder.getInstance();
	} catch (MgmtException e) {
	    logger.log(Level.WARNING, "UPGRADE: Could not get upgrader singleton", e);
	    throw new MgmtException("Could not get upgrader singleton");
	}
	UpgraderInterface upgrader = uh.getUpgrader();
	String method = null;
	Method m;
	boolean response = false;

	if (answer.intValue() == 0) {
	    response = true;
	} else {
	    response = false;
	}
	try {
	    Class paramTypes[] = { com.sun.honeycomb.admin.mgmt.MessageSender.class };
	    Object args[] = { new MessageSender(evt) };
	    Integer retval;
	    if ((method = upgrader.getNextMethod(response)) != null) {		
		m = upgrader.getClass().getMethod(method, paramTypes);
		retval = (Integer) m.invoke(upgrader, args);
		return BigInteger.valueOf(retval);
	    }
	} catch (NoSuchMethodException nsm) {
	    // How to handle this situation?
	    logger.log(Level.WARNING, "UPGRADE: No " + method + " method has been found.", nsm);
	    return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	} catch (IllegalAccessException iae) {
	    logger.log(Level.WARNING, "UPGRADE: Illegal access for method " + method, iae);
	    return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	} catch (InvocationTargetException ite) {
	    logger.log(Level.WARNING, "UPGRADE: Got exception for method " + method, ite);
	    return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	}
	return BigInteger.valueOf(CliConstants.UPGRADE_SUCCESS);
    }

    public BigInteger setForceOptions(EventSender evt, BigInteger dummy) 
	throws MgmtException {
	// call when force is enabled in the CLI instead of quest
	logger.log(Level.INFO, "UPGRADE: setForceOptions invoked");
	UpgraderHolder uh = null;
	try {
	    uh = UpgraderHolder.getInstance();
	} catch (MgmtException e) {
	    logger.log(Level.WARNING, "UPGRADE: Could not get upgrader singleton", e);
	    throw new MgmtException("Could not get upgrader singleton");
	}
	UpgraderInterface upgrader = uh.getUpgrader();
	return BigInteger.valueOf(upgrader.setForceOptions(new MessageSender(evt)));
    }

    public BigInteger downloadJar(EventSender evt, String src) {
	/* Download upgrade jar from same directory as image, into jarfile, where
	   the jar will also be copied if not doing an http upgrade. */
	URL url = null;
	URL jarURL = null;
	ClusterProperties confs = ClusterProperties.getInstance();
	String jarfile = confs.getProperty(JAR_FILE_PROP);
	File dstFile = new File(jarfile);
	URLConnection conn = null;
	DataInputStream in = null;
	FileOutputStream fileOut = null;
	byte[] buf = new byte[BUFSIZE];
	long contentLen = -1;
	int read = -1;
	long totalRead = 0;
	logger.log(Level.INFO, "UPGRADE: downloadJar invoked");

	try {
	    try {
		url = new URL(src);
	    } catch (MalformedURLException mfu) {
		evt.sendAsynchronousEvent(mfu.getMessage());
		logger.log(Level.WARNING, "UPGRADE: malformed URL " + src, mfu);
		return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	    }
	    
	    try {
		String jarname = confs.getProperty(JAR_NAME);
		jarURL = new URL(url, jarname);
	    } catch (MalformedURLException mfu) {
		evt.sendAsynchronousEvent(mfu.getMessage());
		logger.log(Level.WARNING, "UPGRADE: malformed URL " + jarURL, mfu);
		return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	    }
	    
	    try {
		conn = jarURL.openConnection();
		conn.connect();
	    } catch (IOException ioe) {
		evt.sendAsynchronousEvent("Cannot open connection to url \'" 
					  + jarURL.toString() + "\': " + ioe.getMessage());
		logger.log(Level.WARNING, "UPGRADE: cannot open connection to url " 
			   + jarURL.toString(), ioe);
		return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	    }
	    
	    try {
		in = new DataInputStream(conn.getInputStream());
	    } catch (IOException ioe) {
		evt.sendAsynchronousEvent("Cannot open stream to url \'" 
					  + jarURL.toString() + "\': " + ioe.getMessage());
		logger.log (Level.WARNING, "UPGRADE: cannot open stream to URL " 
			    + jarURL.toString(), ioe);
		return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	    }
	    
	    contentLen = conn.getContentLength();
	    
	    if (contentLen == -1) {
		logger.log(Level.INFO, "UPGRADE: Initiating jar download, length unknown");
	    } else {
		logger.log(Level.INFO, "UPGRADE: Initiating image download, length " + contentLen
			   + "bytes.");
	    }
	    
	    try {
		if (dstFile.exists()) {
		    dstFile.delete();
		}
		dstFile.createNewFile();
	    } catch (IOException ioe) {
		logger.log (Level.WARNING, "UPGRADE: Failed to create local jar file: " +
			    jarfile, ioe);
		evt.sendAsynchronousEvent("Failed to create local jar file: " + ioe.getMessage());
		in.close();
		return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	    }
	    
	    try {
		fileOut = new FileOutputStream(dstFile);
	    } catch (IOException ioe) {
		logger.log (Level.WARNING, "UPGRADE: Could not open local jar file: ", ioe);
		evt.sendAsynchronousEvent("Could not open local jar file: " + ioe.getMessage());
		in.close();
		return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	    }
	    
	    Reassure reassureThread = new Reassure(evt);
	    reassureThread.start();
	    
	    try {
		while ((read = in.read(buf, 0, buf.length)) != -1) {
		    try {
			fileOut.write(buf, 0, read);
		    } catch (IOException ioe) {
			logger.log(Level.WARNING, "UPGRADE: Upgrade write error getting jar: ", ioe);
			reassureThread.setMessage("Write error: " + ioe.getMessage());
			fileOut.close();
			in.close();
			reassureThread.safeStop();
			return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
		    }
		    totalRead += read;
		}
	    } catch (IOException ioe) {
		reassureThread.setMessage("Read error: " + ioe.getMessage());
		logger.log(Level.WARNING, "UPGRADE:  Upgrade read error getting jar: ", ioe);
		fileOut.close();
		in.close();
		return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	    } finally {
		reassureThread.safeStop();
	    }
	    
	    try {
		fileOut.close();
		in.close();
	    } catch (IOException ioe) {
		evt.sendAsynchronousEvent("Close error: " + ioe.getMessage());
		logger.log(Level.SEVERE, "UPGRADE: Close error in upgrade: ", ioe);
		fileOut.close();
		in.close();
		return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	    }
	    
	    logger.log(Level.INFO, "UPGRADE: Downloaded " + totalRead 
		       + " bytes successfully for upgrade jar.");
	} catch (IOException ioe) {
	    logger.log(Level.SEVERE, "UPGRADE: Upgrade IO error: ", ioe);
	    return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	} catch (MgmtException e) {
	    logger.log(Level.SEVERE, "UPGRADE: Error sending async events: ", e);
	    return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	}
	return BigInteger.valueOf(CliConstants.UPGRADE_SUCCESS);
    }

    public BigInteger copyJar(BigInteger dummy) {
	// copy jar from mounted iso to predetermined location
	FileChannel source, dest;
	ClusterProperties confs = ClusterProperties.getInstance();
	String jarfile = confs.getProperty(JAR_FILE_PROP);
	String tempjar = confs.getProperty(NEWMNT_PROP) + "/.upgrade/" + confs.getProperty(JAR_NAME);

	logger.log(Level.INFO, "UPGRADE: copyJar invoked");

	try {
	    source = new FileInputStream(tempjar).getChannel();
	} catch (FileNotFoundException fe) {
	    logger.log(Level.WARNING, "UPGRADE: Source file not found: " + tempjar, fe);
	    return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	}
	try {
	    dest = new FileOutputStream(jarfile).getChannel();
	} catch (FileNotFoundException fe) {
	    logger.log(Level.WARNING, "UPGRADE: Destination file not found: " + jarfile, fe);
	    return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	}
	try {
	    source.transferTo(0, source.size(), dest);
	} catch (IOException ie) {
	    logger.log(Level.WARNING, "UPGRADE: Unable to copy jar file: ", ie);
	    return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	}
	try {
	    source.close();
	    dest.close();	
	} catch (IOException ie) {
	    logger.log(Level.WARNING, "UPGRADE: Unable to close file channels ", ie);
	    return BigInteger.valueOf(CliConstants.UPGRADE_ERROR_CONTINUE);
	}
	return BigInteger.valueOf(CliConstants.UPGRADE_SUCCESS);
    }

    public BigInteger httpFetch(EventSender evt, String srcURL) throws MgmtException {
	int retval;
	logger.log(Level.INFO, "UPGRADE: httpFetch invoked");
	UpgraderHolder uh = null;
	try {
	    uh = UpgraderHolder.getInstance();
	} catch (MgmtException e) {
	    logger.log(Level.WARNING, "UPGRADE: Could not get upgrader singleton", e);
	    throw new MgmtException("Could not get upgrader singleton");
	}

	UpgraderInterface upgrader = uh.getUpgrader();

	Reassure rt = new Reassure(evt);
	rt.start();

	try {
	    retval = upgrader.httpFetch(new MessageSender(rt), srcURL);
	    return BigInteger.valueOf(retval);
	} catch (MgmtException me) {
	    logger.log(Level.WARNING, "UPGRADE: MgmtException ", me);
	    return BigInteger.valueOf(CliConstants.UPGRADE_FAILURE);
	} finally {
	    rt.safeStop();
	}
    }

    public BigInteger startUpgrade(EventSender evt, String spdvd, Byte cellid) 
	throws MgmtException {
	logger.log(Level.INFO, "UPGRADE: start invoked");
	UpgraderHolder uh = null;
	try {
	    uh = UpgraderHolder.getInstance();
	} catch (MgmtException e) {
	    logger.log(Level.WARNING, "UPGRADE: Could not get upgrader singleton", e);
	    throw new MgmtException("Could not get upgrader singleton");
	}

	UpgraderInterface upgrader = uh.getUpgrader();

	Reassure rt = new Reassure(evt);
	rt.start();
	int retval = upgrader.startUpgrade(new MessageSender(rt),
				      spdvd, cellid.byteValue());
	rt.safeStop();
	return BigInteger.valueOf(retval);
    }

    public BigInteger finishUpgrade(EventSender evt, BigInteger type, 
				    BigInteger success, Byte cellid) 
	throws MgmtException {
	logger.log(Level.INFO, "UPGRADE: finish invoked");
	UpgraderHolder uh = null;
	try {
	    uh = UpgraderHolder.getInstance();
	} catch (MgmtException e) {
	    logger.log(Level.WARNING, "UPGRADE: Could not get upgrader singleton", e);
	    throw new MgmtException("Could not get upgrader singleton");
	}

	UpgraderInterface upgrader = uh.getUpgrader();
	boolean response = false;

	if (success.intValue() == 0) {
	    response = true;
	} else {
	    response = false;
	}

	return BigInteger.valueOf(upgrader.finishUpgrade
				  (new MessageSender(evt), type.intValue(),
				   response, cellid.byteValue()));
    }

    private int runCommand(String cmd) {
        logger.log(Level.INFO, "UPGRADE: Upgrade running command: " + cmd);
        int retVal=-3;
        try {
            retVal=Runtime.getRuntime().exec(cmd).waitFor();
	    logger.log(Level.INFO, "UPGRADE: Command " + cmd + " got return value " +
		       retVal);


        } catch (IOException ioe) {
	    logger.log(Level.SEVERE, "UPGRADE: Error running upgrade command " + cmd, ioe);
            return -1;
        } catch (InterruptedException ie) {
	    logger.log(Level.SEVERE, "UPGRADE: Error running upgrade command " + cmd, ie);
            return -1;
        }
        return retVal;
    }
}

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



package com.sun.honeycomb.diskmonitor;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InterruptedIOException;

import java.util.*;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.ResourceBundle;
import java.text.MessageFormat;

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.disks.DiskInitialize;
import com.sun.honeycomb.disks.DiskLabel;
import com.sun.honeycomb.diskmonitor.DiskMonitor;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.common.StringUtil;
import com.sun.honeycomb.util.BundleAccess;
import com.sun.honeycomb.util.ExtLevel;

import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.sysdep.Commands;

/**
 * KernelListener: background thread that waits for kernel
 * warnings/errors.  This whole class is a big hack, only slightly
 * better than scraping log files looking for errors. Here the
 * relevant errors come to us so we can just block on read instead of
 * polling in a loop.
 *
 * @author Shamim Mohamed
 */

class KernelListener implements Runnable {

    private static final Logger logger =
        Logger.getLogger(KernelListener.class.getName());

    private String logfileName = null;
    private BufferedReader logpipe = null;
    private String driverName = null;
    private Disk[] disks = null;
    private String[] devnames = null;
    private String[] mountpoints = null;
    private DiskMonitor parent = null;
    private String[] hotStrings = null;
    private boolean terminate = false;
    private Map altDevToDiskDev = new HashMap();

    private Thread theThread = null;

    // patterns for matching disk attach/detach messages

    private static final Pattern attachDetachDevPattern =
        Pattern.compile(".* sata: .* WARNING: (/.*):$");

    private static final Pattern attachDetachPortPattern =
        Pattern.compile(".* SATA device (?:attached|detached|detected) at port (\\d+)$");

    private static final Pattern diskPathPattern =
        Pattern.compile("ok: .*::(.*)$");


    KernelListener(DiskMonitor parent, String logfileName,
                   String driverName, Disk[] disks) {
        this.logfileName = logfileName;
        this.parent = parent;
        this.driverName = driverName;
        this.disks = disks;

        if (disks == null) {
            logger.warning("No disks to watch over");
            return;
        }

        devnames = new String[disks.length];
        mountpoints = new String[disks.length];
        for (int i = 0; i < devnames.length; i++) {
            Disk disk = disks[i];

            // Munge the device special file paths to be of the form
            // used in kernel error messages
            devnames[i] = getDevName(disk);
            mountpoints[i] = getMountPoint(disk);

            // XXX TODO: In some cases, first there's a warning with
            // just the driver/controller name, without the disk:
            //     WARNING: /pci@0,0/pci108e,5348@8:
            // followed by kernel.notice messages detailing the error
            // but using "port 0" etc., not the device names we so
            // painstakingly extracted. D'oh!
        }

        if (logger.isLoggable(Level.INFO)) {
            String msg = "Watching for errors on " + driverName + ":";
            for (int i = 0; i < devnames.length; i++)
                msg += " " + StringUtil.image(devnames[i]);
            logger.info(msg);
        }
    }

    synchronized void addDisk(Disk disk) {
	String devName =  getDevName(disk);
        devnames[disk.diskIndex()] = devName;
        mountpoints[disk.diskIndex()] = getMountPoint(disk);

	// drive insert/delete messages are of the form
	// "/devices/pci@0,0/pci108e,5348@7/disk@1"; 
	// populate a map with those entries
	logger.info("addDisk: devName " + devName);
	String altDev = devName.replaceAll(",\\d$", "");

	altDevToDiskDev.put(altDev, disk.getDevice());

        if (logger.isLoggable(Level.INFO)) {
            String msg = "Now watching for errors on " +
                driverName + " devices";
            for (int i = 0; i < devnames.length; i++)
                msg += " " + StringUtil.image(devnames[i]);
            logger.info(msg);
        }
    }

    private synchronized String getDiskPath(String altDev) {
	return (String)altDevToDiskDev.get(altDev);
    }

    public void start() {
        theThread = new Thread(this);
        theThread.start();

        // We want the other thread to start running and block on the
        // FIFO read so when syslogd re-reads its config file and
        // tries to open the FIFO there's a reader on the other
        // end
        try {
            Thread.sleep(500);  // XXX TODO: Need a better way
        }
        catch (InterruptedException ignored) { }

        // Send HUP to syslogd
        try {
            Exec.exec(Commands.getCommands().pkill()+ "-HUP syslogd$", logger);
        } catch (IOException e) {
            logger.warning("Couldn't HUP syslogd: kernel monitoring may fail");
        }
    }

    public void run() {
        // Open the FIFO. This will block until there's a writer
        try {
            logpipe = new BufferedReader(new FileReader(logfileName));
        }
        catch (IOException e) { }

        if (logpipe == null)
            logger.severe("Couldn't open " + StringUtil.image(logfileName) + 
                " -- can't monitor kernel warnings and errors!");

        logger.info("Monitoring.");

        while (!terminate) {
            try {
                String line = logpipe.readLine();
                inspectError(line);
                inspectInsertDelete(line);
            }
            catch (InterruptedIOException ign) {}
            catch (Exception e) {
                logger.log(Level.WARNING, "reading from " + logfileName, e);
            }
        }

        logger.info("Terminating.");

        theThread = null;

        try {
            logpipe.close();
        }
        catch (Exception e) {
            logger.log(Level.WARNING, logfileName, e);
        }
    }

    void stop() {
        terminate = true;
        if (theThread != null)
            theThread.interrupt();
    }

    // gotta be a better way
    private static final int NO_SEQ = 0;
    private static final int START_SEQ = 1;

    // are we currently in an attach/detach log message sequence?
    private int seqType = NO_SEQ;
    String devName;
    String portName;

    private boolean includes(String msg, String text) {
        return (msg.indexOf(text) >= 0);
    }

    /**
     * helper routine for running script which handles attach/detach requests
     *
     * Returns a string of the form:
     *   "ok: <accesspt>"
     * or
     *   "error: <error_text>"
     *
     * <accesspt> is of the form "sataX/X::dsk/cXtXdX", where X is a digit.
     * The dsk part after "::" can be turned into a path to the disk by
     * prepending "/dev/r" and appending "p0"
     */
    private String runHandleAttachDetachCmd(String devName, String portName) {
        BufferedReader reader = null;

        try {
            // XXX should be a static final with a prefix.
            // Look elsewhere to see how this is done.
            String cmd = "/opt/honeycomb/bin/handle_attach_detach.pl " +
                devName + " " + portName;

            reader = Exec.execRead(cmd, logger);
            String line = reader.readLine();
            if (line == null)
                return null;

            return line;
        
        }
        catch (IOException e) {
            logger.log(Level.WARNING,
                "can't run handle_attach_detach", e);
            return null;
        }
        finally {
            if (reader != null)
                try {
                    reader.close();
                } catch (IOException e) {}
        }
    }

    private void handlePush(String devName, String portName) {
        logger.info("handlePush " + devName + " " + portName);
        String d = getDiskPath(devName + "/disk@" + portName);
        logger.info("handlePush:  parent.processInsertion(" + d + ")");
        parent.processInsertion(d);
    }

    private void handlePull(String devName, String portName) {
        logger.info("handlePull " + devName + " " + portName);
        String d = getDiskPath(devName + "/disk@" + portName);
        logger.info("handlePull:  parent.processDeletion(" + d + ")");
        parent.processDeletion(d);
    }

    /**
     * Inspect the kernel log file to see if there was a disk insertion
     * or deletion.  Call the relevant disk insertion or deletion routine
     * if there was such an event.
     * 
     * @param msg the kernel log message to examine
     */
    private void inspectInsertDelete(String msg) {

        if (msg == null)
            return;

        Matcher startSeqMatcher = attachDetachDevPattern.matcher(msg);
        Matcher secondLineMatcher = attachDetachPortPattern.matcher(msg);

        if (startSeqMatcher.find()) {

            // start of sequeunce
            seqType = START_SEQ;
            
            assert(startSeqMatcher.groupCount() == 1);
            devName = startSeqMatcher.group(1);

	    logger.info("start of insert/delete sequence; dev " + devName);

        } else if (secondLineMatcher.find()) {

            if (seqType == NO_SEQ) {
                logger.warning("unexpected SATA device attach/detach: " + msg);
                ResourceBundle rs = BundleAccess.getInstance().getBundle();
                String str = rs.getString("warn.disk.monitor.listener.sataattachdetach");
                Object [] args = {new String(msg)};
                logger.log(ExtLevel.EXT_WARNING, MessageFormat.format(str, args));
                return;
            }

            assert(secondLineMatcher.groupCount() == 1);
            portName = secondLineMatcher.group(1);
	    
	    // make sure port is numeric
	    try {
		Integer.parseInt(portName);
	    } catch (NumberFormatException e) {
		logger.warning("non-numeric portName attach/detach: "
			       + portName);
		seqType = NO_SEQ;
		return;
	    }

	    logger.info("end of insert/delete sequence; dev " + devName + 
			" port " + portName);

            if (includes(msg, "SATA device detached"))
                handlePull(devName, portName);
            else
                handlePush(devName, portName);
            
            seqType = NO_SEQ;
        } else {
            // non-matching line ends sequence
            seqType = NO_SEQ;
        }
    }

    /**
     * Inspect the kernel log file to see if it's a disk problem. See <a
     * href="http://hc-web.sfbay/svn/docs/Anza/DetailedDesigns/Disk_Failure_Detection.html">the
     * Disk Failure Detection design</a> for more on the kinds of
     * errors we look for
     *
     * @param msg the kernel log message to examine
     * @param return whether any action was taken
     */
    private synchronized boolean inspectError(String msg) {
        if (msg == null)
            return false;

        if (devnames == null) {
            // Cannot happen!
            logger.severe("No device names? Can't inspect error " +
                          StringUtil.image(msg));
            return false;
        }

        // Check for hot strings
        for (int i = 0; i < disks.length; i++) {
            try {
                if (msg.indexOf(devnames[i]) >= 0 || 
                        msg.indexOf(mountpoints[i]) >= 0)
                    return handleError(disks[i], msg);
            }
            catch (Exception ign) {}
        }

        if (logger.isLoggable(Level.INFO))
            logger.info("Ignoring kernel log " + StringUtil.image(msg));

        return false;
    }

    private boolean handleError(Disk disk, String msg) {
        // There's some indication that a disk problem results in a
        // cluster of errors, so one actual error can result in the
        // disk being disabled. Maybe we need to filter the errors by
        // time... remember the time of the previous error on this
        // disk; if it's now more than n seconds later, this one
        // counts as a new error; otherwise it's part of the error
        // swarm.

        logger.severe("Detected kernel error on disk " + disk.getId() + " " +
                      StringUtil.image(msg));
        ResourceBundle rs = BundleAccess.getInstance().getBundle();
        String str = rs.getString("err.disk.monitor.listener.kernerror");
        Object [] args = {new String(disk.getId().toString()),
                          new String(StringUtil.image(msg))};
        logger.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));

        return parent.reportError(disk);
    }

    private String getDevName(Disk disk) {
        if (disk == null)
            return null;

        String dev = disk.getAltDevice();
        if (dev == null)
            dev = disk.getDevice();

        // Here's what the device names look like:

        // HON hcb104 ~ $ ls -l /dev/dsk/c?t?d0s4
        // lrwxrwxrwx   1 root     root          47 Apr  6 19:29 /dev/dsk/c0t0d0s4 -> ../../devices/pci@0,0/pci108e,5348@7/disk@0,0:e
        // lrwxrwxrwx   1 root     root          47 Apr  6 19:29 /dev/dsk/c0t1d0s4 -> ../../devices/pci@0,0/pci108e,5348@7/disk@1,0:e
        // lrwxrwxrwx   1 root     root          47 Apr  6 19:29 /dev/dsk/c1t0d0s4 -> ../../devices/pci@0,0/pci108e,5348@8/disk@0,0:e
        // lrwxrwxrwx   1 root     root          47 Apr  6 19:29 /dev/dsk/c1t1d0s4 -> ../../devices/pci@0,0/pci108e,5348@8/disk@1,0:e
        //
        // Or,
        //     /devices/pci@0,0/pci108e,5348@7/disk@0,0:e
        //     /devices/pci@0,0/pci108e,5348@7/disk@1,0:e
        //     /devices/pci@0,0/pci108e,5348@8/disk@0,0:e
        //     /devices/pci@0,0/pci108e,5348@8/disk@1,0:e

        // In Solaris logs, the leading "/devices" is stripped out,
        // and everything beyond the colon
        dev = dev.replaceAll("^/devices", "");
        dev = dev.replaceAll(":.*$", "");

        if (dev.length() == 0)
            return null;
        return dev;
    }

    private String getMountPoint(Disk disk) {
        try {
            if (disk.getPath().length() == 0)
                return null;
            return disk.getPath();
        }
        catch (Exception ign) {}
        return null;
    }
}

// 456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 

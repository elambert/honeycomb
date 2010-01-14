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



package com.sun.honeycomb.oa.upgrade;

import com.sun.honeycomb.oa.Common;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.datadoctor.TaskFragUtils;
import com.sun.honeycomb.datadoctor.NotFragmentFileException;
import com.sun.honeycomb.util.posix.StatFS;
import com.sun.honeycomb.oa.upgrade.UpgradeableList.FileInfo;
import com.sun.honeycomb.oa.upgrade.UpgraderException.TmpUpgraderException;
import 
    com.sun.honeycomb.oa.upgrade.UpgraderFragmentFile.UpgraderTmpFragmentFile;
import 
    com.sun.honeycomb.oa.upgrade.UpgraderFragmentFile.UpgraderRepoFragmentFile;

import java.io.File;
import java.io.FilenameFilter;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Random;

public class DiskUpgrader extends Thread {
    private static final Logger log
        = Logger.getLogger(DiskUpgrader.class.getName());
    private final Disk disk;
    private final ByteBufferPool readBufferPool;
    private final ByteBufferPool writeBufferPool;
    private final ExecutorService executor;

    private static final int WORKER_COUNT = 2;
    private static final long WORKER_WORKTIME = 50; // milliseconds
    public static final String WORK_DIRECTORY = "tmp-upgrade";

    private static final int PRINT_INTERVAL = 60; // seconds

    private final File workDirectory;
    private long start;
    private final Object countMonitor = new Object();
    private int upgradeCount;
    private Semaphore semaphore;

    private UpgraderException exception;
    private final Profile profile = new Profile();

    public static final Type DATA = new Type("data");
    public static final Type REPO = new Type("repo");
    public static final Type TEMP = new Type("temp");

    /**********************************************************************/
    public DiskUpgrader(Disk disk) {
        super(disk.getPath());
        this.disk = disk;
        int readBufferSize
            = UpgraderFragmentFile.UpgraderFragmentFooter.oldSize();
        int writeBufferSize
            = UpgraderFragmentFile.UpgraderFragmentFooter.newSize();
        readBufferPool = new ByteBufferPool(WORKER_COUNT, readBufferSize);
        writeBufferPool = new ByteBufferPool(WORKER_COUNT, writeBufferSize);
        executor = Executors.newFixedThreadPool(WORKER_COUNT);
        workDirectory = new File(disk.getPath() + File.separator
                                 + WORK_DIRECTORY);
    }

    /**********************************************************************/
    private void populateList(UpgradeableList list, String dirPath) {
        List listedFiles = new LinkedList();
        FragmentNameFilter fragmentNameFilter = new FragmentNameFilter();
	String[] files = new File(dirPath).list(fragmentNameFilter);
	if ((files != null) && (files.length != 0)) {
            for (int f = 0; f < files.length; f++) {
                listedFiles.add(new FileInfo(files[f], dirPath));
	    }
            Collections.sort(listedFiles);
	    try {
		list.add(listedFiles);
		listedFiles.clear();
	    } catch (UpgraderException e) {
		log.log(Level.SEVERE,"Could not upgrade files for " + dirPath, e);
	    }
	}
    }			     

    /**********************************************************************/
    public void run() {
        log.info("Upgrading " + disk.getPath());

        // create a work directory
        if (!workDirectory.isDirectory() && !workDirectory.mkdir()) {
            String msg = "Failed to create " + workDirectory.getName();
            log.severe(msg);
            throw new IllegalStateException(msg);
        }

        start = System.currentTimeMillis();
        semaphore = new Semaphore(Integer.MAX_VALUE);
        semaphore.drainPermits();

	// First upgrade fragments in tmp-upgrade directory.
        UpgradeableList repoUpgradeableList = new UpgradeableList(disk, REPO);
	populateList(repoUpgradeableList, workDirectory.getPath());

	// Then upgrade fragments in data directories
        UpgradeableList upgradeableList = new UpgradeableList(disk, DATA);
	int startLayout = new Random().nextInt(LayoutClient.NUM_MAP_IDS);
	log.info(disk.getPath() + ": Starting layout " + startLayout);
	int layout;

        for (int i = 0; i < LayoutClient.NUM_MAP_IDS; i++) {
	    layout = (i + startLayout) % LayoutClient.NUM_MAP_IDS;
            String dirPath
                = Common.makeDir(new Integer(layout).toString(), disk.getPath());
	    populateList(upgradeableList, dirPath);
        }

	// Now upgrade fragments in tmp-close directory.
        UpgradeableList tmpUpgradeableList = new UpgradeableList(disk, TEMP);
	populateList(tmpUpgradeableList, Common.makeTmpDirName(disk));

        log.info("Files to upgrade for " + disk.getPath() + ": " 
		 + (upgradeableList.count() + tmpUpgradeableList.count() 
		    + repoUpgradeableList.count()));

	execute(repoUpgradeableList);
	execute(upgradeableList);
	execute(tmpUpgradeableList);
        waitForCompletion(upgradeableList.count() 
			  + tmpUpgradeableList.count()
			  + repoUpgradeableList.count());

        float elapsedTimeSec = (System.currentTimeMillis() - start) / 1000F;
        log.info("Files upgraded for path " + disk.getPath() + ": " + upgradeCount
                 + " in " + elapsedTimeSec + " seconds");
        writeVersionFile();

	// Clean up backing disk files
	repoUpgradeableList.remove();
	upgradeableList.remove();
	tmpUpgradeableList.remove();
    }

    /**********************************************************************/
    private void execute(UpgradeableList upgradeableList) {
        // schedule threads to upgrade files
        int submitted = 0;
        int printTimer = PRINT_INTERVAL;
        for (Iterator it = upgradeableList.iterator(); it.hasNext(); ) {
            FileInfo fi = (FileInfo) it.next();
            // Don't let the queue get too big, and print status periodically.
            while ((submitted - semaphore.availablePermits()) > 1000) {
                if (printTimer <= 0) {
                    printStatus(0);
                    printTimer = PRINT_INTERVAL;
                } else {
                    printTimer--;
                }
                this.sleep(1);
            }

	    executor.execute(newWorker(fi.getFile(), upgradeableList.getType()));
            submitted++;
        }
    }


    /**********************************************************************/
    private void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException ignored) {
        }
    }

    /**********************************************************************/
    private void waitForCompletion(int fileCount) {
        int printTimer = PRINT_INTERVAL;
        long maxAllowedTime = Math.max(fileCount * WORKER_WORKTIME, 
				       30 * 60 * 1000);

        while (semaphore.availablePermits() < fileCount) {
            if ((System.currentTimeMillis() - start) > maxAllowedTime) {
                log.severe("Taking too long to upgrade (" + maxAllowedTime
                           + " millisec) for " + disk.getPath());
                terminate();
            } else if (printTimer <= 0) {
                printStatus(fileCount);
                printTimer = PRINT_INTERVAL;
            } else {
                printTimer--;
            }
            this.sleep(1);
        }
        terminate();
    }

    /**********************************************************************/
    private void terminate() {
        log.info(disk.getPath() + ": Terminating, please wait...");
        executor.shutdown(); // Disable new tasks from being submitted
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow(); // Cancel currently executing tasks
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.severe(disk.getPath() + ": Executor did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow(); // re-cancel tasks
        }
    }

    /**********************************************************************/
    public void checkException() throws UpgraderException {
        if (exception != null) {
            throw exception;
        }
    }

    /**********************************************************************/
    private void printStatus(int totalFiles) {
        float elapsedTimeSec = (System.currentTimeMillis() - start) / 1000F;
        int count = upgradeCount;
        int done = semaphore.availablePermits();
        int failed = done - count;
        failed = (failed > 0) ? failed : 0;
        log.info("Upgraded " + count + " and failed " + failed
                 + ((totalFiles != 0) ? (" of total " + totalFiles) : "")
                 + " files for path " + disk.getPath() + " in " + elapsedTimeSec
                 + " seconds");
        if (semaphore.availablePermits() > 0) {
            log.info(profile.getString(semaphore.availablePermits(),
                                       disk.getPath()));
        }
    }

    /**********************************************************************/
    protected void writeVersionFile() {
        Upgrader.updateVersionFile(disk);
    }

    /**********************************************************************/
    private void incrementUpgradeCount() {
        synchronized(countMonitor) {
            upgradeCount++;
        }
    }

    /**********************************************************************/
    public int getUpgradeCount() {
        return upgradeCount;
    }

    /**********************************************************************/
    public Disk getDisk() {
        return disk;
    }

    /**********************************************************************/
    protected Worker newWorker(String filename, Type type) {
	if (type == DATA) {
	    return new Worker(filename);
	} 
	if (type == REPO) {
	    return new RepoWorker(filename);
	}
	if (type == TEMP) {
	    return new TmpWorker(filename);
	}
	throw new IllegalStateException("Unknown type " + type.name);
    }

    /**********************************************************************/
    class Worker implements Runnable {
        private final String filename;

        public Worker(String filename) {
            this.filename = filename;
        }

        public void run() {
            int fragmentId;
            NewObjectIdentifier oid = null;
            long start = System.currentTimeMillis();
            try {
                try {
                    fragmentId = TaskFragUtils.extractFragId(filename);
                    oid = UpgraderNewObjectIdentifier.fromFilename(filename);
                } catch (NotFragmentFileException nfe) {
                    log.severe(filename + ": " + nfe.getMessage());
                    return;
                } catch (UpgraderException ue) {
                    log.severe(filename + ": " + ue.getMessage());
                    return;
                }

                ByteBufferRecord rbr = readBufferPool.checkoutBuffer();
                ByteBufferRecord wbr = writeBufferPool.checkoutBuffer();

                UpgraderFragmentFile uff = null;
                try {
                    uff = newFragmentFile(oid, fragmentId, disk,
                                          rbr.getBuffer(), wbr.getBuffer(),
                                          profile);
                    uff.upgrade();
                    incrementUpgradeCount();
		} catch (TmpUpgraderException ignored) {
		    log.info("Ignored " + ignored.getMessage());
                } catch (UpgraderException ue) {
                    log.severe("Upgrading of " + uff + " failed: "
                               + ue.getMessage());
                } finally {
                    readBufferPool.checkinBuffer(rbr);
                    writeBufferPool.checkinBuffer(wbr);
                }
            } catch (RuntimeException re) {
                log.log(Level.SEVERE, re.getMessage() + " caused by "
                           + ((oid != null) ? oid : "null"), re);
                throw re;
            } finally {
                semaphore.release();
                profile.put(Profile.Run, (System.currentTimeMillis() - start));
            }
        }

        protected UpgraderFragmentFile newFragmentFile(NewObjectIdentifier oid,
                                                       int fragmentId, Disk disk,
                                                       ByteBuffer readBuffer,
                                                       ByteBuffer writeBuffer,
                                                       Profile profile) {
            return new UpgraderFragmentFile(oid, fragmentId, disk, readBuffer,
                                            writeBuffer,
                                            workDirectory.getAbsolutePath(),
                                            profile);
        }
    }

    /**********************************************************************/
    /* Worker for temp frags in tmp-close */
    class TmpWorker extends Worker {
	public TmpWorker(String filename) {
	    super(filename);
        }

	protected UpgraderFragmentFile 
	    newFragmentFile(NewObjectIdentifier oid,
			    int fragmentId, 
			    Disk disk,
			    ByteBuffer readBuffer,
			    ByteBuffer writeBuffer,
			    Profile profile) {
            return new UpgraderTmpFragmentFile(oid, fragmentId, disk, 
					       readBuffer,
					       writeBuffer,
					       workDirectory.getAbsolutePath(),
					       profile);
        }
    }

    /**********************************************************************/
    /* Worker for recovering upgradable fragments in tmp-upgrade */
    class RepoWorker extends Worker {
	public RepoWorker(String filename) {
	    super(filename);
        }

	protected UpgraderFragmentFile 
	    newFragmentFile(NewObjectIdentifier oid,
			    int fragmentId, 
			    Disk disk,
			    ByteBuffer readBuffer,
			    ByteBuffer writeBuffer,
			    Profile profile) {
            return new UpgraderRepoFragmentFile(oid, fragmentId, disk, 
					       readBuffer,
					       writeBuffer,
					       workDirectory.getAbsolutePath(),
					       profile);
        }
    }

    /**********************************************************************/
    public static class Profile {
        public static final String DaalRead = new String("daalRead ");
        public static final String DaalWrite = new String("daalWrite ");
        public static final String DaalRWOpen = new String("daalRWOpen ");
        public static final String DaalClose = new String("daalClose ");
        public static final String Run = new String("total ");

        private Map elapsed = Collections.synchronizedMap(new HashMap());

        public void put(String operation, long interval) {
            Long value = (Long) elapsed.get(operation);
            if (value == null) {
                elapsed.put(operation, new Long(interval));
            } else {
                elapsed.put(operation, new Long(interval + value.longValue()));
            }
        }

        public long get(String operation) {
            Long value = (Long) elapsed.get(operation);
            return (value != null) ? value.longValue() : -1;
        }

        public String getString(int count, String path) {
            return "Avg. millisec. per operation (" + path + "): "
                + ((get(DaalRWOpen) != -1) ? (DaalRWOpen + (get(DaalRWOpen) / count) + ", ") : "")
                + ((get(DaalRead) != -1) ? (DaalRead + (get(DaalRead) / count) + ", ") : "")
                + ((get(DaalWrite) != -1) ? (DaalWrite + (get(DaalWrite) / count) + ", ") : "")
                + ((get(DaalClose) != -1) ? (DaalClose + (get(DaalClose) / count) + ", ") : "")
                + ((get(Run) != -1) ? (Run + (get(Run) / count)) : "");
        }
    }

    /**********************************************************************/
    public static class FragmentNameFilter implements FilenameFilter {
        private static final Pattern pattern
            = Pattern.compile("(\\S{36})(\\.(\\d+)){5}_(\\d+)");
        private final Matcher matcher = pattern.matcher("foo");
        public synchronized boolean accept(File dir, String name) {
            try {
                return matcher.reset(name).matches();
            } catch (RuntimeException re) {
                // to shed light on a StringIndexOutOfBoundsException
                log.severe(re.getMessage() + " caused by " + name);
                throw re;
            }
        }
    }

    /**********************************************************************/
    public static class ByteBufferPool {
        private List bufferPool;

        public ByteBufferPool(int poolSize, int bufferSize) {
            bufferPool = new LinkedList();
            for (int i = 0; i < poolSize; i++) {
                bufferPool.add(new ByteBufferRecord(bufferSize));
            }
        }

        public synchronized ByteBufferRecord checkoutBuffer() {
            for (Iterator it = bufferPool.iterator(); it.hasNext(); ) {
                ByteBufferRecord bbr = (ByteBufferRecord) it.next();
                if (bbr.available()) {
                    return bbr.reuse();
                }
            }
            throw new IllegalStateException("No buffers available");
        }

        public synchronized void checkinBuffer(ByteBufferRecord bbr) {
            bbr.setAvailable();
        }
    }

    /**********************************************************************/
    public static class ByteBufferRecord {
        final ByteBuffer buffer;
        private boolean available = true;
        ByteBufferRecord(int size) {
            buffer = ByteBuffer.allocateDirect(size);
        }
        boolean available() {
            return available;
        }
        ByteBufferRecord reuse() {
            assert(available);
            buffer.clear();
            available = false;
            return this;
        }
        ByteBuffer getBuffer() {
            return buffer;
        }
        void setAvailable() {
            assert(!available);
            available = true;
        }
    }

    /**********************************************************************/
    public static class Type {
	public final String name;
	public Type (String type) {
	    name = type;
	}
    }
}

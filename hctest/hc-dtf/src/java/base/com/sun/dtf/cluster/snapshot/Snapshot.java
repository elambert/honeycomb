package com.sun.dtf.cluster.snapshot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import com.sun.dtf.exceptions.SnapshotException;

public class Snapshot {

    private static int THREADS = 32;
    
    private static String MOUNT_IDS[] = { "/data/0",
                                          "/data/1",
                                          "/data/2",
                                          "/data/3" };
    
    private static String DISK_IDS[]  = { "/dev/dsk/c0t0d0s4",
                                          "/dev/dsk/c0t1d0s4",
                                          "/dev/dsk/c1t0d0s4",
                                          "/dev/dsk/c1t1d0s4" };
    
    private static String MOUNT_CMD = "/usr/sbin/mount -F ufs -o noxattr,noatime,syncdir";
    
    private static String SNAPSHOT_DIR = ".snapshots";
    
    private static boolean hcIsUp() throws SnapshotException { 
        try {
            if (Exec.executeCmdWithOutput("ps -ef").indexOf("NODE-SERVERS") != -1)
                return true;
            else 
                return false;
        } catch (IOException e) {
            throw new SnapshotException("Error checking if hc is running",e);
        } catch (InterruptedException e) {
            throw new SnapshotException("Error checking if hc is running",e);
        }
    }
    
    private static boolean isMounted(String deviceID) throws SnapshotException {
        try {
            return (Exec.executeCmd("grep " + deviceID + " /etc/mnttab",false,false) == 0);
        } catch (IOException e) {
            throw new SnapshotException("Error checking if mounted.",e);
        } catch (InterruptedException e) {
            throw new SnapshotException("Error checking if mounted.",e);
        }
    }
    
    private static class DiskMounter extends Thread { 

        private String _path = null;
        private String _device = null;
        
        private SnapshotException _exception = null;
        
        public DiskMounter(String path, String device) {
            _path = path;
            _device = device;
        }
        
        public void run() {
            try {
                if (!isMounted(_device)) {
                    int rc;
                    try {
                        rc = Exec.executeCmd(MOUNT_CMD + " " + _device + 
                                             " " + _path, false, true);
                    } catch (IOException e) {
                        throw new SnapshotException("Error mounting device.", e);
                    } catch (InterruptedException e) {
                        throw new SnapshotException("Error mounting device.", e);
                    }
               
                    if (rc != 0)
                        SimpleLogger.info("Unable to mount " + _device);
                    //if (rc != 0)
                    //    throw new SnapshotException("Unable to mount device " + _device);
                }
            } catch (SnapshotException e) {
                _exception = e;
            }
        }
        
        public void checkForException() throws SnapshotException { 
            if (_exception != null)
                throw _exception;
        }
    }
    
    private static void mountDisks(int[] disks) throws SnapshotException { 
        checkAndCreateMountPoints(disks);
        ArrayList mounters = new ArrayList();
        
        for (int i = 0; i < disks.length; i++) { 
            /*
             * Thread it makes it 4x faster... definitely worth it.
             */
            int index = disks[i];
            Thread diskmounter = new DiskMounter(MOUNT_IDS[index],DISK_IDS[i]);
            mounters.add(diskmounter);
            diskmounter.start();
        }
        
        waitFor(mounters);
        
        // check for any exceptions
        for(int i = 0; i < mounters.size(); i++) 
            ((DiskMounter)mounters.get(i)).checkForException();
    }
    
    private static void checkAndCreateMountPoints(int[] disks) { 
        for (int m = 0; m < disks.length; m++) { 
            File dir = new File(MOUNT_IDS[disks[m]]);
            if (!dir.exists())
                dir.mkdirs();
        }
    }

    private static void checkAndCreateSnapshotDirectory(int[] disks) { 
        for (int m = 0; m < disks.length; m++) { 
            File dir = new File(MOUNT_IDS[disks[m]] + 
                                File.separatorChar + 
                                SNAPSHOT_DIR);
            if (!dir.exists())
                dir.mkdirs();
        }
    }
    
    private static boolean alreadyExists(String name, int[] diskIds) { 
        String[] snapshots = getSnapshotList(diskIds[0]);
        
        for(int i = 0; i < snapshots.length; i++)
            if (snapshots[i].equals(name)) 
                return true;
        
        return false;
    }
    
    private static String[] getSnapshotList(int disk) { 
        ArrayList results = new ArrayList();
        
        if (disk == -1)
            disk = 0;
      
        // check only the first disk
        File dir = new File(MOUNT_IDS[disk] + File.separatorChar + SNAPSHOT_DIR);
        
        if (dir.exists()) { 
            File[] sub = dir.listFiles();
            for (int i = 0; i < sub.length; i++) {
                if (sub[i].isDirectory()) { 
                    results.add(sub[i].getName());
                }
            }
        }
        
        String[] result = new String[results.size()];
        
        for (int i = 0; i < results.size(); i++)
            result[i] = (String)results.get(i);
       
        return result;
    }
    
    private static void checkPreConditions(int[] diskIds) throws SnapshotException { 
        if (hcIsUp())
            throw new SnapshotException("Honeycomb is still up on this node.");
        else 
            SimpleLogger.info("Honeycomb is not up and running.");
        
        mountDisks(diskIds);
        checkAndCreateSnapshotDirectory(diskIds);
    }
    
    private static void saveConfig(String name, 
                                   int[] diskIds)
                   throws SnapshotException {
        SimpleLogger.info("Save config to disks " + printDisks(diskIds));

        long start = System.currentTimeMillis();
        ArrayList tasks = new ArrayList();
        for (int i = 0; i < diskIds.length; i++) {
            File config = new File("/config");
            TaskGenerator generator = new TaskGenerator(config);
            File base = new File(MOUNT_IDS[diskIds[i]]);
            File snapDir = new File(base, SNAPSHOT_DIR + File.separatorChar
                                    + name + File.separatorChar + ".config");

            tasks.addAll(Task.getTasks("copy", config, snapDir, generator, THREADS));
        }

        startTasks(tasks);
        waitFor(tasks);
        long stop = System.currentTimeMillis();
        
        SimpleLogger.info("Time to save config " + ((stop-start)/1000) + "s");
    }
    
    private static void restoreConfig(String name,
                                      int[] diskIds) throws SnapshotException { 
        for (int i = 0; i < diskIds.length; i++) {
            SimpleLogger.info("Restoring config from disk " + diskIds[i]);
            
            long start = System.currentTimeMillis();
            
            ArrayList tasks = new ArrayList();
            File config = new File("/config");
            File base = new File(MOUNT_IDS[diskIds[i]]);
            File snapDir = new File(base, SNAPSHOT_DIR + File.separatorChar + 
                                    name + File.separatorChar + ".config");
            
            TaskGenerator generator = new TaskGenerator(snapDir);
            tasks.addAll(Task.getTasks("copy", snapDir, config, generator, THREADS));

            startTasks(tasks);
            waitFor(tasks);
            long stop = System.currentTimeMillis();
            SimpleLogger.info("Time to restore config " + ((stop-start)/1000) + "s");
            
            // only restore from one disk
            return;
        }
    }
    
    private static String printDisks(int[] diskIds) { 
        StringBuffer result = new StringBuffer();
       
        for (int i = 0; i < diskIds.length; i++) { 
            result.append(diskIds[i] + ",");
        }
        
        return result.substring(0,result.length()-1);
    }
    
    public static void preCheck(String name,
                                String type,
                                int[] diskIds) throws SnapshotException { 
        
    }
    
    public static void saveSnapshot(String name,
                                    String type,
                                    int[] diskIds) throws SnapshotException { 
        checkPreConditions(diskIds);
        
        if (alreadyExists(name, diskIds))
            throw new SnapshotException("Snapshot with name [" + name + 
                                        "] already exists.");
      
        if (type.equalsIgnoreCase("precheck")) {
            SimpleLogger.info("Precheck of saving snapshot [" + name + 
                              "] by " + type + ", on disks " + 
                              printDisks(diskIds) + " passed.");
            return;
        }
        
        saveConfig(name, diskIds);
        
        SimpleLogger.info("Save snapshot [" + name + "] by " + type + 
                          ", on disks " + printDisks(diskIds));

        ArrayList tasks = new ArrayList();
        for (int i = 0; i < diskIds.length; i++) { 
            File base = new File(MOUNT_IDS[diskIds[i]]);
            TaskGenerator generator = new TaskGenerator(base);
            File snapDir = new File(base, 
                                    SNAPSHOT_DIR + File.separatorChar + name);
                
            tasks.addAll(Task.getTasks(type, base, snapDir, generator, THREADS));
        }
       
        startTasks(tasks);
        waitFor(tasks);
    } 
    
    public static void restoreSnapshot(String name,
                                       String type,
                                       int[] diskIds) throws SnapshotException { 
        checkPreConditions(diskIds);

        if (!alreadyExists(name, diskIds))
            throw new SnapshotException("Snapshot with name [" + name + 
                                        "] does not exist.");
        
        if (type.equalsIgnoreCase("precheck")) {
            SimpleLogger.info("Precheck of restoring snapshot [" + name + 
                              "] by " + type + ", on disks " + 
                              printDisks(diskIds) + " passed.");
            return;
        }
        
        restoreConfig(name, diskIds);

        // before res
        deleteData(diskIds);
        
        SimpleLogger.info("Restoring snapshot [" + name + "] by " + type + 
                          ", on disks " + printDisks(diskIds));
        
        ArrayList tasks = new ArrayList();
        for (int i = 0; i < diskIds.length; i++) { 
            File base = new File(MOUNT_IDS[diskIds[i]]);
            File snapDir = new File(base, 
                                    SNAPSHOT_DIR + File.separatorChar + name);
            TaskGenerator generator = new TaskGenerator(snapDir);
            tasks.addAll(Task.getTasks(type, snapDir, base, generator, THREADS));
        }
       
        startTasks(tasks);
        waitFor(tasks);
       
        // clean up any leftovers (mainly config)
        deleteSnapshot(name, type, diskIds);

        /*
         * cleanup
         */
        for (int i = 0; i < diskIds.length; i++) { 
            File base = new File(MOUNT_IDS[diskIds[i]]);
            File snapDir = new File(base, 
                                    SNAPSHOT_DIR + File.separatorChar + name);
            snapDir.delete();
        }
    }
    
    public static void deleteSnapshot(final String name,
                                      String type,
                                      final int[] diskIds) 
                  throws SnapshotException { 
        mountDisks(diskIds);
        
        if (type.equalsIgnoreCase("precheck")) {
            SimpleLogger.info("Precheck of deleting snapshot [" + name + 
                              "], on disks " + printDisks(diskIds) + " passed.");
            return;
        }
        
        SimpleLogger.info("Deleting snapshot [" + name + "], on disks " + 
                          printDisks(diskIds));
      
        ArrayList tasks = new ArrayList();
        for (int i = 0; i < diskIds.length; i++) { 
            File base = new File(MOUNT_IDS[diskIds[i]]);
            File snapDir = new File(base, 
                                    SNAPSHOT_DIR + File.separatorChar + name);
            TaskGenerator generator = new TaskGenerator(snapDir);
            tasks.addAll(Task.getTasks("delete", snapDir, null, generator, THREADS));
        }

        startTasks(tasks);
        waitFor(tasks);

        /* 
         * This is necessary because the previous deletes don't always get all 
         * of the empty directories cleaned up.
         */
        ArrayList delTasks = new ArrayList();
        
        SimpleLogger.info("Cleaning up empty directories...");
        for (int i = 0; i < diskIds.length; i++) { 
            final int x = i;
            Thread t = new Thread() { 
                public void run() {
                    File base = new File(MOUNT_IDS[diskIds[x]]);
                    File snapDir = new File(base, 
                                            SNAPSHOT_DIR + File.separatorChar + name);
                    deleteDirectory(snapDir,true);
                }
            };
            delTasks.add(t);
            t.start();
        }
       
        waitFor(delTasks);
    }
    
    private static boolean deleteDirectory(File path, boolean handleHidden) {
        if (path.exists()) {
            File[] files = path.listFiles();
            for (int i = 0; i < files.length; i++) {
               
                if (files[i].isHidden() && !handleHidden)
                    continue;
                
                if (files[i].isDirectory()) {
                    deleteDirectory(files[i], handleHidden);
                } else {
                    files[i].delete();
                }
            }
        }
        return (path.delete());
    }
    
    private static void waitFor(ArrayList tasks) { 
        Iterator itasks = tasks.iterator();
        
        while (itasks.hasNext()) { 
            Thread thread = (Thread)itasks.next();
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    private static void startTasks(ArrayList tasks) { 
        Iterator itasks = tasks.iterator();
        
        while (itasks.hasNext()) { 
            Thread thread = (Thread)itasks.next();
                thread.start();
        }
    }
    
    public static void deleteData(final int[] diskIds) throws SnapshotException { 
        checkPreConditions(diskIds);
        SimpleLogger.info("Deleting data on disks [" + printDisks(diskIds) + "]");

        ArrayList tasks = new ArrayList();
        for (int i = 0; i < diskIds.length; i++) { 
            File base = new File(MOUNT_IDS[diskIds[i]]);
            TaskGenerator generator = new TaskGenerator(base);
            tasks.addAll(Task.getTasks("delete", base, null, generator, THREADS));
        }
       
        startTasks(tasks);
        waitFor(tasks);
        /* 
         * This is necessary because the previous deletes don't always get all 
         * of the empty directories cleaned up.
         */
        ArrayList delTasks = new ArrayList();
        SimpleLogger.info("Cleaning up empty directories...");
        for (int i = 0; i < diskIds.length; i++) { 
            final int x = i;
            Thread t = new Thread() { 
                public void run() {
                    File base = new File(MOUNT_IDS[diskIds[x]]);
                    deleteDirectory(base,false);
                }
            };
            delTasks.add(t);
            t.start();
        }
        
        waitFor(delTasks);
    }
    
    public static void listSnapshot(int diskId) { 
        String[] snapshots = getSnapshotList(diskId);
       
        SimpleLogger.info("Snapshots: ");
       
        if (snapshots.length == 0) 
            SimpleLogger.info("NONE");
        
        for (int i = 0; i < snapshots.length; i++) { 
            SimpleLogger.info("* " + snapshots[i]);
        }
    }

    
    private static void printUsage() { 
        SimpleLogger.info("Snapshot Tool");
        SimpleLogger.info("-------------");
        SimpleLogger.info(""); 
        SimpleLogger.info("java com.sun.dtf.cluster.Snapshot [mode] [name] [type] [disk]");
        SimpleLogger.info("     mode  - save, restore, delete, list, deletedata");
        SimpleLogger.info("     name  - name of the snapshot being handled");
        SimpleLogger.info("     type  - type of snapshot: move, copy, precheck");
        SimpleLogger.info("     disk  - disk to snapshot (by default all available");
    }
    
    public static void main(String[] args) {
        if (args.length >= 1) { 
            String command = args[0];
            String name = null;
            String type = null;
            
            int disk = -1;
            int[] diskIds = null;
            
            if (args.length >= 2) { 
                name = args[1];
            } 
            
            if (args.length >= 3) { 
                type = args[2];
            }
            
            if (args.length >= 4) { 
                try { 
                    disk = new Integer(args[3]).intValue(); 
                } catch(NumberFormatException e) { 
                    SimpleLogger.error("disk must be a positive integer betwen 0 and 3.");
                    throw e;
                }
            }
            
            if (disk == -1) 
                diskIds = new int[] { 0,1,2,3 };
            else 
                diskIds = new int[] { disk };
          
            long start = System.currentTimeMillis();
            try {
                if (command.equalsIgnoreCase("save")) { 
                    saveSnapshot(name, type, diskIds);
                } else if (command.equalsIgnoreCase("restore")) { 
                    restoreSnapshot(name, type, diskIds);
                } else if (command.equalsIgnoreCase("delete")) { 
                    deleteSnapshot(name, type, diskIds);
                } else if (command.equalsIgnoreCase("list")) { 
                    listSnapshot(disk);
                } else if (command.equalsIgnoreCase("deletedata")) { 
                    deleteData(diskIds); 
                } else if (command.equalsIgnoreCase("check")) { 
                    checkPreConditions(diskIds);
                }
            } catch (SnapshotException e) { 
                SimpleLogger.error("Snapshot error.", e);
                System.exit(-1);
            } finally { 
                long stop = System.currentTimeMillis();
                SimpleLogger.info("Operation took " + ((stop-start)/1000) + "s");
            }
        } else {
            printUsage();
            System.exit(-1);
        }
    }
}

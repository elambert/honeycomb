package com.sun.honeycomb.testcmd.cmd;

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



/* adapted from Sacha's original */

import com.sun.honeycomb.client.TestNVOA;

import com.sun.honeycomb.testcmd.common.HoneycombTestCmd;
import com.sun.honeycomb.testcmd.common.HoneycombTestException;
import com.sun.honeycomb.testcmd.common.NameValue;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.common.ArchiveException;

import java.io.ByteArrayInputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.Channels;
import java.io.*;
import java.util.*;

public class BulkStore extends HoneycombTestCmd {

    private final String mount_point = "/tmp/hc";
    private final String view_dir1_base = 
	"/study_group_sample/study_test/group_test/";
    private final String view_dir2_base = 
	"/study_group_processing/study_test/group_test/param_test/";
    private String last_dir = "test_id";
    private final String last_dir_md = "sample_id";
    private String view_dir1, view_dir2;

    private static final String definedAttributeNames[] = {
	"study_id",
	"sample_group",
	"processing_param" };
    private static final String definedAttributeValues[] = {
	"study_test",
	"group_test",
        "param_test" };

    private static final String attributeToDefine = "scan_number";

    private static String clusterAddress = null;

    private static int total_files = 30000;
    private static int file_number = 0;
    private static boolean running = true;

    private static final int NB_MAX_THREADS = 32;
    private static int n_threads;

    private int interval, dotsPerInterval;

    private NameValueObjectArchive objectArchive;

    private LinkedList ll = new LinkedList();

    private BulkStore(String host, int n_files, String last_dir) throws ArchiveException, IOException{

        if (n_files > 0)
            total_files = n_files;
        if (last_dir != null) {
            if (last_dir.endsWith("/"))
                last_dir = last_dir.substring(0, last_dir.length()-1);
            this.last_dir = last_dir;
        }
        view_dir1 = view_dir1_base + this.last_dir + "/";
        view_dir2 = view_dir2_base + this.last_dir + "/";

        dot_range = 80;

        if (total_files > dot_range) {
            dotsPerInterval = 1;
            interval = total_files / dot_range;
        } else {
            dotsPerInterval = dot_range / total_files;
            interval = 1;
        }
        if (total_files < NB_MAX_THREADS)
            n_threads = total_files;
        else
            n_threads = NB_MAX_THREADS;

        //System.out.println("dotsPerInterval " + dotsPerInterval + " interval " + interval + " n_threads " + n_threads);
        clusterAddress = host;

	objectArchive = new TestNVOA(clusterAddress);
    }

    private int totalDots = 0;
    private void my_store(boolean limit, NameValueRecord md, int id) {
        String value = null;
        boolean notify = false;
        synchronized (this) {
            if (limit  &&  !running)
                return;
            value = Integer.toString(file_number++);
            if (file_number == total_files) {
                running = false;
            }
            if (limit  &&  file_number % interval == 0)
                notify = true;
        }
        md.put(attributeToDefine, value);
        ByteArrayInputStream inputStream = new ByteArrayInputStream
			(new String("I am entry <"+value+">\n").getBytes());
 
        while (true) {
            ReadableByteChannel channel = Channels.newChannel(inputStream);
            try {
	        SystemRecord sr = objectArchive.storeObject(channel, md);
                ll.add(new NameValue(value, sr.getObjectIdentifier().toString()));
                break;
            } catch (Exception e) {
                sleep(5000);
                try {
                    inputStream.reset();
                } catch (Exception ee) {
                    inputStream = new ByteArrayInputStream
                              (new String("I am entry <"+value+">").getBytes());
                }
            }
	    try {
	        inputStream.close();
            } catch (Exception e) {}
        }
        if (notify  &&  totalDots < dot_range) {
            for (int i=0; i<dotsPerInterval; i++) {
                dot(".");
                totalDots++;
            }
        }
    }
    private class StoreThread extends Thread {
	private int modulo;

        private NameValueRecord md = createRecord();

	private StoreThread(int nModulo) {
	    modulo = nModulo;

            md.put(last_dir_md, last_dir);

	    for (int i=0; i<definedAttributeNames.length; i++) {
	        md.put(definedAttributeNames[i], definedAttributeValues[i]);
	    }
	}

	public void run() {
	    while (file_number < total_files) {
                my_store(true, md, modulo);
            }
            saydone();
        }
    }
    private synchronized void saydone() {
        notifyAll();
    }

    private void showMissing(String dirname) {
        int missing = 0;
        System.out.println("missing:");
        ListIterator it = ll.listIterator(0);
        while (it.hasNext()) {
            NameValue nv = (NameValue) it.next();
            File f = new File(dirname + nv.name);
            if (!f.exists()) {
                missing++;
                System.out.println(nv.name + "\t" + nv.value);
            }
        }
        System.out.println("total " + missing);
    }

    private void start() {

        try {
            if (!shell.isMounted(mount_point)) {
                System.out.print("Mounting " + mount_point);
                shell.nfsMount(clusterAddress + ":/", mount_point);
                System.out.println(" .. done");
            } else {
                System.out.println(mount_point + " is mounted");
            }
            File f = new File(mount_point + view_dir1);
            if (f.exists()) {
                System.err.println("\n\nProgam already run, dir populated [" +
					mount_point + view_dir1 + 
					"] - exiting");
                System.exit(1);
            }
        } catch (Exception e) {
            System.out.println("\n");
            e.printStackTrace();
            System.exit(1);
        }
 
	StoreThread[] threads = new StoreThread[n_threads];
        System.out.println("Threads: " + n_threads);
	System.out.println("Starting.. expect " + dot_range + " dots.");

	long t1 = System.currentTimeMillis();

	for (int i=0; i<n_threads; i++) {
	    threads[i] = new StoreThread(i);
	    threads[i].start();
	}
	
	synchronized (this) {
	    for (int i=0; i<n_threads; i++) {
		try {
		    wait();
		} catch (InterruptedException ignored) {
		}
	    }
	}
        t1 = System.currentTimeMillis() - t1;
        long t_rec = t1 / total_files;

        finishDotRange(".");

        String total_time = null;
        if (t1 >= 60000)
            total_time = "" + (t1 / 60000) + " minutes";
        else
            total_time = "" + (t1 / 1000) + " seconds";

	System.out.println("All data have been stored ["+
			       total_files+" files at " + t_rec +
				" ms/file, total time " + total_time + "]");

        System.out.println("Sleeping 10 sec for system to settle..");
        try { Thread.sleep(10000); } catch (Exception e) {}

        System.out.println("Timing 'ls' (may take a while)");
        try {
            long t = timeLs(mount_point + view_dir1, total_files);
            System.out.println("total ls time of view1: " + t + " msec, " +
				(t / total_files) + " msec/file");
        } catch (Exception e) {
            System.out.println("ls of view1 (" + mount_point + view_dir1 +
				"): " + e);
            showMissing(mount_point + view_dir1);
            System.out.println("sleeping 10 sec for 2nd try..");
            try { Thread.sleep(10000); } catch (Exception ee) {}
        }
        try {
            long t = timeLs(mount_point + view_dir1, total_files);
            System.out.println("total 2nd ls time of view1: " + t + " msec, " +
				(t / total_files) + " msec/file");
        } catch (Exception e) {
            System.out.println("2nd ls of view1 (" + mount_point + view_dir1 +
				"): " + e);
            showMissing(mount_point + view_dir1);
        }
        try {
            long t = timeLs(mount_point + view_dir2, total_files);
            System.out.println("total ls time of view2: " + t + " msec, " +
				(t / total_files) + " msec/file");
        } catch (Exception e) {
            System.out.println("ls of view2 (" + mount_point + view_dir2 +
				"): " + e);
            showMissing(mount_point + view_dir2);
        }
        System.out.println("Adding another file..");
        try {
            NameValueRecord md = createRecord();
            md.put(last_dir_md, this.last_dir);
	    for (int i=0; i<definedAttributeNames.length; i++) {
	        md.put(definedAttributeNames[i], definedAttributeValues[i]);
	    }
            my_store(false, md, -1);
        } catch (Exception e) {
            System.out.println("storing another file: " + e);
        }
        System.out.println("Sleeping 10 sec for system to settle..");
        try { Thread.sleep(10000); } catch (Exception e) {}
        try {
            long t = timeLs(mount_point + view_dir1, total_files+1);
            System.out.println("total 2nd ls time of view2 w/ 1 added file: " +
				t + " msec, " + 
				(t / total_files) + " msec/file");
        } catch (Exception e) {
            System.out.println("ls of view2 plus 1 file (" + 
				mount_point + view_dir2 + "): " + e);
            showMissing(mount_point + view_dir2);
        }
        System.out.println("view1 is " + mount_point + view_dir1);
        System.out.println("view2 is " + mount_point + view_dir2);
        System.out.println("total files written: " + file_number);
    }
    
    public static void main(String[] args) throws ArchiveException, IOException {
        String usage = "Usage: BulkStore <data-vip> [-n n_files] [-d last_dir]";
        if (args.length < 1) {
            System.err.println(usage);
            System.exit(1);
        }
        String host = args[0];
        int n_files = 0;
        String last_dir = null;
        if (args.length > 1) {
            for (int i=1; i<args.length; i+=2) {
                if (args[i].equals("-n")) {
                    n_files = Integer.parseInt(args[i+1]);
                    if (n_files < 1) {
                        System.err.println(usage + "\n\tn_files must be > 1");
                        System.exit(1);
                    }
                } else if (args[i].equals("-d")) {
                    last_dir = args[i+1];
                } else {
                    System.err.println(usage);
                    System.exit(1);
                }
            }
        }
        
	BulkStore instance = new BulkStore(host, n_files, last_dir);
	instance.start();
    }
}

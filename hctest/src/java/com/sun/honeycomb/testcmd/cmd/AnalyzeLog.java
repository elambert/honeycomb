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



import com.sun.honeycomb.testcmd.common.HoneycombTestCmd;
import com.sun.honeycomb.testcmd.common.HoneycombTestException;
import com.sun.honeycomb.testcmd.common.RunCommand;
import com.sun.honeycomb.testcmd.common.NameValue;

import java.util.*;
import java.io.*;
import java.util.regex.*;

public class AnalyzeLog extends HoneycombTestCmd {

    boolean dots = true;
    static String in_filename = null;
    static Date start = new Date();

    private void usage() {
        System.err.println("Usage: java AnalyzeLog <log_file>");
        System.exit(1);
    }

    public static void main(String args[]) {
        new AnalyzeLog(args);
    }
        
    public AnalyzeLog(String args[]) {

        verbose = false;

        if (args.length != 1) {
            usage();
        }
	LineReader lr = null;
        try {
            lr = new LineReader(args[0]);
	} catch (Exception e) {
	    System.err.println(e.getMessage());
	    System.exit(1);
	}
/*
        if (out_filename != null) {
            try {
                fo = new FileWriter(out_filename, true);
                flog("# " + new Date() + "\n");
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
*/
        // Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown(), 
							// "Shutdown"));
        try {
            lr = new LineReader(args[0]);
        } catch (Exception e) {
            System.err.println("AnalyzeLog: opening/reading " + 
						in_filename + ": " + e);
            usage();
        }
        StringBuffer sb = new StringBuffer();
        String line;

        int lines, infos, warnings, severes, kernels, others;
        lines = infos = warnings = severes = kernels = others = 0;

        int no_master, no_heartbeat, default_schema, frag_null;
        no_master = no_heartbeat = default_schema = frag_null = 0;
        int frag_deleted, frag_no_good, frag_init_fail;
        frag_deleted = frag_no_good = frag_init_fail = 0;
        int uncaught = 0;
        int ntps, logs, mounts, sshs, dhcps, scripts, thread_wait;
        ntps = logs = mounts = sshs = dhcps = scripts = thread_wait = 0;

        ObjectCounter warn_hosts = new ObjectCounter();
        ObjectCounter warn_what = new ObjectCounter();
        ObjectCounter severe_hosts = new ObjectCounter();
        ObjectCounter severe_what = new ObjectCounter();
        ObjectCounter kernel_hosts = new ObjectCounter();

        ArrayList extras = new ArrayList();

        Pattern fnull = Pattern.compile(".*fragment ... is null.*");
        Pattern fbad = Pattern.compile(".*fragment ... is no good.*");

        while ((line = lr.getline()) != null) {

            lines++;
            if (lines % 10000 == 0)
                dot(".");

            String fields[] = line.split(" +");
            String host = fields[3];

            String what = null;
            boolean info = false;
            boolean kern = false;
            boolean warn = false;
            boolean severe = false;
            boolean ntp = false;
            boolean log = false;
            boolean ssh = false;
            boolean mount = false;
            boolean dhcp = false;
            boolean script = false;

            String type = fields[4];

            if (type.equals("java:")) {
                what = fields[7];
                if (fields[6].equals("INFO"))
                    info = true;
                else if (fields[6].equals("WARNING"))
                    warn = true;
                else if (fields[6].equals("SEVERE"))
                    severe = true;
                else if (fields.length > 9  &&  fields[9].equals("WARNING:"))
                    warn = true;
            } else if (type.equals("logger:")) {
                if (fields.length > 9) {
                    type = fields[9];
                    if (type.equals("kernel:")) {
                         kern = true;
                    } else if (type.equals("java:")) {
                         what = fields[12];
                         if (fields[11].equals("INFO")) {
                              if (fields[14].equals("WARNING:"))
                                  warn = true;
                              else
                                  info = true;
                         } else if (fields[11].equals("WARNING")) {
                             warn = true;
                         } else if (fields[11].equals("SEVERE")) {
                             severe = true;
                         }
                    } else {
                         what = fields[9];
                         if (type.startsWith("ntpd")) {
                             ntp = true;
                         } else if (type.equals("rc-scripts:")) {
                             script = true;
                         } else if (type.startsWith("syslog")) {
                             log = true;
                         }
//else
//System.out.println("XXX " + type);
                    }
                    if (fields.length > 10  &&  fields[10].equals("WARNING:"))
                        warn = true;
                } else {
                    what = fields[5];
                }
            } else if (type.equals("kernel:")) {
                kern = true;
            } else if (type.equals("rpc.mountd:")) {
                mount = true;
            } else if (type.equals("rc-scripts:")) {
                script = true;
            } else if (type.equals("dhcpd:")) {
                dhcp = true;
            } else if (type.startsWith("ntpd")) {
                ntp = true;
            } else if (type.startsWith("syslog")) {
                log = true;
            } else if (type.startsWith("ssh")) {
                ssh = true;
            } else {
                 what = fields[4];
                 if (fields[5].equals("WARNING:"))
                     warn = true;
            }

            if (info) {
                 infos++;
                 if (line.indexOf("thread pools have already been created. Waiting for free threads") != -1)
                     thread_wait++;
            } else if (warn) {
                 warnings++;
                 warn_hosts.count(host);
                 warn_what.count(what);

                 if (line.indexOf(" NO MASTER FOUND ") != -1)
                     no_master++;
                 else if (line.indexOf("failed to receive heartbeat") != -1)
                     no_heartbeat++;
                 else if (line.indexOf("Failed to get the live schema, backing to the default one") != -1)
                     default_schema++;
                 else if (line.indexOf(" has been deleted: com.sun.honeycomb.oa.DeletedFragmentException: -1 has been deleted") != -1)
                     frag_deleted++;
                 else if (line.indexOf("Failed to init frag") != -1)
                     frag_init_fail++;
                 else if (fnull.matcher(line).matches())
                     frag_null++;
                 else if (fbad.matcher(line).matches())
                     frag_no_good++;
            } else if (severe) {
                 severes++;
                 severe_hosts.count(host);
                 severe_what.count(what);
                 if (line.indexOf(" Uncaught exception") != -1)
                     uncaught++;
            } else if (kern) {
                 kernels++;
                 kernel_hosts.count(host);
            } else if (mount) {
                 mounts++;
            } else if (ntp) {
                 ntps++;
            } else if (script) {
                 scripts++;
            } else if (log) {
                 logs++;
            } else if (dhcp) {
                 dhcps++;
            } else if (ssh) {
                 sshs++;
            } else {
                others++;
                if (line.indexOf("WARNING") != -1  ||  
                    line.indexOf("SEVERE") != -1  ||
                    line.indexOf("kernel") != -1)
                    extras.add(line);
            }
        }
        lr.close();
        closeDots();
        System.out.println("======================= lines");
        System.out.println("info   " + infos);
        System.out.println("warn   " + warnings);
        System.out.println("severe " + severes);
        System.out.println("kernel " + kernels);
        System.out.println("others " + others);
        System.out.println("TOTAL  " + lines);
        System.out.println("======================= info");
        System.out.println("thread_wait " + thread_wait);
        System.out.println("======================= warning");
        System.out.println("no_master      " + no_master);
        System.out.println("no_heartbeat   " + no_heartbeat);
        System.out.println("default_schema " + default_schema);
        System.out.println("frag_null      " + frag_null);
        System.out.println("frag_deleted   " + frag_deleted);
        System.out.println("frag_no_good   " + frag_no_good);
        System.out.println("frag_init_fail " + frag_init_fail);
        System.out.println("================ hosts");
        System.out.print(warn_hosts.sort().toString());
        System.out.println("================ what");
        System.out.print(warn_what.sort().toString());
        System.out.println("======================= severe");
        System.out.println("uncaught_exception " + uncaught);
        System.out.println("================ hosts");
        System.out.print(severe_hosts.sort().toString());
        System.out.println("================ what");
        System.out.print(severe_what.sort().toString());
        System.out.println("======================= kernel");
        System.out.println("================ hosts");
        System.out.print(kernel_hosts.sort().toString());
        System.out.println("======================= daemons/misc");
        System.out.println("rpc.mountd " + mounts);
        System.out.println("ntpd       " + ntps);
        System.out.println("rc-scripts " + scripts);
        System.out.println("dhcpd      " + dhcps);
        System.out.println("syslog     " + logs);
        System.out.println("ssh        " + sshs);
        if (extras.size() > 0) {
            System.out.println("======================= unexpected format:");
            for (int i=0; i<extras.size(); i++)
                System.out.println((String) extras.get(i));
        }
    }
/*
    private static class Shutdown implements Runnable {
        public void run() {
            Date now = new Date();
            if (in_filename.equals("-")) {
                // let piping program print 1st
                try { Thread.sleep(1000); } catch (Exception e) {}
            }
            System.err.println();
            flout("AnalyzeLog START:    " + start + "\n");
            flout("AnalyzeLog FINISH:   " + now + "\n");
            flout("AnalyzeLog Records:  " + total + "\n");
            flout("AnalyzeLog ok files: " + ok_files + "\n");
            flout("AnalyzeLog md retrieve failures: " + 
						md_retrieve_failures + "\n");
            if (checkData)
                flout("AnalyzeLog retrieve failures: " + 
						retrieve_failures + "\n");
            if (checkUnique)
                flout("AnalyzeLog uniqueness-check failures: " + 
						unique_failures + "\n");
            flout("AnalyzeLog Total corruption errors: " + 
						corruption_errors + "\n");
            reportExceptions("AnalyzeLog");

            long deltat = now.getTime() - start.getTime();
            deltat /= 1000;
            if (total > 0)
                flout("AnalyzeLog API thruput, ms/record:   " + 
						(md_time / total) + "\n");
            if (checkData) {
                flout("AnalyzeLog Total bytes retrieved:    " + 
							restore_bytes + "\n");
                if (restore_time > 0)
                    flout("AnalyzeLog Data rate:                " + 
						(restore_bytes * 1000 / 
						restore_time) + " bytes/sec\n");
            }
            if (checkUnique  && total > 0) {
                flout("AnalyzeLog API unique, ms/record:     " + 
						(md_qtime / total) + "\n");
            }
            deltat = now.getTime() - start.getTime();
            if (deltat > 0)
                flout("AnalyzeLog Total thruput, ms/record: " + 
						(deltat / total) + "\n");
            try {
                new File(lastfile).delete();
            } catch (Exception e) {}
        }
    }
*/
}

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



package com.sun.honeycomb.hctest.util;

import com.sun.honeycomb.test.util.*;

import java.util.*;
import java.io.*;
import java.util.regex.*;

public class MeasureLog extends HoneycombTestCmd {

    String in_filename = null;
    FileOutputStream metrics_out = null;
    Date start = new Date();
    Hashtable diskStart = new Hashtable();
    HashMap diskEnd = new HashMap();
    Hashtable loadStart = new Hashtable();
    HashMap loadEnd = new HashMap();


    private void usage() {
        System.err.println("Usage: java MeasureLog [-s start_log_time] " +
                                                  "[-e end_log_time] " +
                                                  "[-p] " +
                                                  "[-m] " +
                                                  "[-v] <log_file>");
        System.err.println(" *_log_time like this: May 24 19:09:33");
        System.err.println("         -m:  metrics-only mode of output");
        System.err.println("         -v:  show unclassified msgs");
        System.err.println("         -p:  show per-size bandwidth stats");
        System.exit(1);
    }

    public static void main(String args[]) {
        new MeasureLog(args);
    }

    private void addBandwidth(BandwidthStatistic bws, String line, 
                                                      String[] fields) {

        //  12       13           14    15   16     17    18
        // MEAS <store|retrieve> <tag> size <size> time <time>
        long bytes = -1;
        long time = -1;
        try {
            bytes = Long.parseLong(fields[16]);
            time = Long.parseLong(fields[18]);
        } catch (Exception e) {
            System.err.println("parsing bandwidth <<" + line + ">>: " + e);
            return;
        }
        bws.add(time, bytes);
    }

    private void addTiming(Statistic st, String line, String[] fields) {

        //  12       13            14    15   16     
        // MEAS <query|delete|..> <tag> time <time>
        long time = -1;
        try {
            time = Long.parseLong(fields[16]);
        } catch (Exception e) {
            System.err.println("parsing time <<" + line + ">>: " + e);
            return;
        }
        st.addValue(time);
    }

    private void addDiskUsage(String host, String line) {
        String s = (String) diskStart.get(host);
        if (s == null)
            diskStart.put(host, line);
        else
            diskEnd.put(host, line);
    }

    /*
    XXX not sure if the load avgs we are getting are worth
    the effort of tracking max and min (or at all) - they 
    remain pretty steady and happen to scale down by about 2 
    for each successive number: 0.61 0.34 0.14
    */
    private void addLoad(String host, String line) {
       String s = (String) loadStart.get(host);
       if (s == null)
           loadStart.put(host, line);
       else
           loadEnd.put(host, line);
    }

    private void printStates(String tag, Hashtable start, HashMap end) {
        Set s = start.entrySet();
        if (s == null  ||  s.size() == 0) {
            System.out.println("\n==== " + tag + ": no data");
            return;
        }
        System.out.println("\n===== " + tag + " start/end:");
        Iterator it = s.iterator();
        while (it.hasNext()) {
            Map.Entry e = (Map.Entry) it.next();
            String key = e.getKey().toString();
            System.out.println(key + ": ");
            String val = e.getValue().toString();
            System.out.println("  " + val);
            Object v2 = end.get(key);
            if (v2 != null)
                System.out.println("  " + v2.toString());
        }
    }

    private void printBW(String name, BandwidthStatistic api, 
                                      BandwidthStatistic internal) {
        System.out.println("==== " + name);
        if (api.getRejected() + internal.getRejected() > 0) {
            System.out.println("rejected\t" + api.getRejected() + "\t" +
                                              internal.getRejected());
            System.out.println("rejected_size\t" + api.getRejectedSize() + 
                                        "\t" + internal.getRejectedSize());
        }
        Iterator it = api.getPerSizeStats();
        Iterator it2 = internal.getPerSizeStats();
        int len = 0;
        while (it.hasNext()) {
            if (!it2.hasNext()) {
                System.out.println(name + 
                                 ": internal count != external - skipping");
                break;
            }
            BandwidthStatistic st = (BandwidthStatistic) it.next();
            BandwidthStatistic st2 = (BandwidthStatistic) it2.next();
            if (!st.name.equals(st2.name)) {
                System.out.println(name +
                                 ": internal size != external (" +
                                   st2.name + " " + st.name + ") - skipping");
                break;
            }
            // first number is longest
            if (len == 0) {
                len = st.name.length();

                for (int i=0; i<len; i++)
                    System.out.print(" ");
                System.out.println("\tapi_N\tapi_BW     \tint_N\tint_BW");
                System.out.print("total");
                for (int i="total".length(); i<len; i++)
                    System.out.print(" ");
                System.out.println("\t" + api.getN() + "\t" +
                                          api.averageBW_MB() + "\t" +
                                          internal.getN() + "\t" +
                                          internal.averageBW_MB());
            }
            long l = Long.parseLong(st.name);
            System.out.print(st.name);
            // pad field to longest
            for (int i=st.name.length(); i<len; i++)
                System.out.print(" ");
            System.out.println("\t" + st.getN() + "\t" +
                        st.averageBW_MB() + "\t" +
                        st2.getN() + "\t" +
                        st2.averageBW_MB());
        }
    }

    public MeasureLog(String args[]) {

        /////////////////////////////////////////////////
        //  args

        if (args.length == 0  ||  args.length > 10)
            usage();

        //
        //  log is last
        //
        String in_filename = args[args.length-1];

        //
        //  options
        //
        String start_time = null;
        String end_time = null;
        boolean show_extras = false;
        boolean per_size = false;
        boolean metrics_mode = false;

        for (int i=0; i<args.length-1; i++) {
            if (args[i].equals("-v")) {
                show_extras = true;
            } else if (args[i].equals("-m")) {
                metrics_mode = true;
                per_size = true;
            } else if (args[i].equals("-p")) {
                per_size = true;
            } else if (args[i].equals("-s")) {
                if (start_time != null)
                    usage();
                if (args.length < i+4)
                    usage();
                start_time = args[i+1] + " " + args[i+2] + " " + args[i+3];
                i += 3;
            } else if (args[i].equals("-e")) {
                if (end_time != null)
                    usage();
                if (args.length < i+4)
                    usage();
                end_time = args[i+1] + " " + args[i+2] + " " + args[i+3];
                i += 3;
            } else {
                usage();
            }
        }

        //
        //  open input file
        //
	LineReader lr = null;
        try {
            lr = new LineReader(in_filename);
        } catch (Exception e) {
            System.err.println("MeasureLog: opening/reading " + 
						in_filename + ": " + e);
            usage();
        }
/*
        //
        //  open metrics file if req'd
        //
        if (metrics_file != null) {
            try {
                metrics_out = new FileOutputStream(metrics_file);
            } catch (Exception e) {
                 System.err.println("MeasureLog: opening " + metrics_file +
                                    ": " + e);
                 usage();
            }
        }
*/
        // MEAS vars
        ObjectCounter meas_unexpected = new ObjectCounter();
        BandwidthStatistic meas_store = 
                            new BandwidthStatistic("store", per_size);
        BandwidthStatistic meas_store_b = 
                            new BandwidthStatistic("store_both", per_size);
        BandwidthStatistic meas_store_ch = 
                            new BandwidthStatistic("store_ch", per_size);
        BandwidthStatistic meas_store_ch_b = 
                            new BandwidthStatistic("store_ch_both", per_size);
        BandwidthStatistic meas_store_int = 
                            new BandwidthStatistic("store_int", per_size);
        BandwidthStatistic meas_store_b_int = 
                            new BandwidthStatistic("store_both_int", per_size);
        BandwidthStatistic meas_retrieve = 
                            new BandwidthStatistic("retrieve", per_size);
        BandwidthStatistic meas_retrieve_ch = 
                            new BandwidthStatistic("retrieve_ch", per_size);
        BandwidthStatistic meas_retrieve_int = 
                            new BandwidthStatistic("retrieve_int", per_size);
        BandwidthStatistic meas_retrieve_md_int = 
                            new BandwidthStatistic("retrieve_md_int", per_size);
        BandwidthStatistic meas_rretrieve = 
                            new BandwidthStatistic("rretrieve", per_size);
        BandwidthStatistic meas_rretrieve_int = 
                            new BandwidthStatistic("rretrieve_int", per_size);
        Statistic meas_addmd = new Statistic("addmd");
        Statistic meas_addmd_int = new Statistic("addmd_int");
        Statistic meas_getmd = new Statistic("getmd");
        Statistic meas_getmd_int = new Statistic("getmd_int");
        Statistic meas_query = new Statistic("query");
        Statistic meas_query_int = new Statistic("query_int");
        Statistic meas_seluniq = new Statistic("seluniq");
        Statistic meas_seluniq_int = new Statistic("seluniq_int");
        Statistic meas_delete = new Statistic("delete");
        Statistic meas_delete_int = new Statistic("delete_int");
        Statistic meas_getschema = new Statistic("getschema");
        Statistic meas_getschema_int = new Statistic("getschema_int");
        Statistic meas_store_md_int = new Statistic("store_md_int");
        
        // general vars
        StringBuffer sb = new StringBuffer();
        String line;

        int lines, infos, warnings, severes, ext_severes, kernels, others;
        lines = infos = warnings = severes = ext_severes = kernels = others = 0;

        int meas_lines = 0;
        int sys_meas_lines = 0;
        int cheat_lines = 0;
        int ext_warnings = 0;

        int no_master, no_heartbeat, default_schema, frag_null;
        no_master = no_heartbeat = default_schema = frag_null = 0;
        int frag_deleted, frag_no_good, frag_init_fail;
        frag_deleted = frag_no_good = frag_init_fail = 0;
        int uncaught = 0;
        int heartbeat_missed = 0;
        int hw_addr = 0;
        int screen_device = 0;
        int ntps, logs, mounts, sshs, dhcps, scripts, thread_wait;
        ntps = logs = mounts = sshs = dhcps = scripts = thread_wait = 0;

        ObjectCounter all_hosts = new ObjectCounter();
        ObjectCounter warn_hosts = new ObjectCounter();
        ObjectCounter warn_what = new ObjectCounter();
        ObjectCounter info_what = new ObjectCounter();
        ObjectCounter severe_hosts = new ObjectCounter();
        ObjectCounter severe_what = new ObjectCounter();
        ObjectCounter store_hosts = new ObjectCounter();
        ObjectCounter retrieve_hosts = new ObjectCounter();

        ArrayList extras = new ArrayList();

        Pattern fnull = Pattern.compile(".*fragment ... is null.*");
        Pattern fbad = Pattern.compile(".*fragment ... is no good.*");

        // Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown(), 
							// "Shutdown"));

        //
        //  organise date-skipping
        //
        boolean skipping = false;
        if (start_time != null)
            skipping = true;
 
        boolean trim = false;
        if (end_time != null)
            trim = true;

        //
        //  track incidentals of date skipping 
        //
        int skipped_lines = 0;
        boolean truncated = false;

        while ((line = lr.getline()) != null) {

            lines++;
            if (lines % 10000 == 0)
                dot(".");

            //
            //  date skipping: beginning-of-line user boundary conditions
            //
            if (skipping) {
                if (line.startsWith(start_time)) {
                    skipping = false;
                } else {
                    skipped_lines++;
                    continue;
                }
            } else if (trim) {
                if (line.startsWith(end_time)) {
                    truncated = true;
                    break;
                }
            }

            //
            //  whew, not skipped! parse the line
            //
            String fields[] = line.split(" +");
            if (fields.length < 5) {
                //  this can happen if you 'echo wow > /var/adm/messages'
                System.err.println("short line skipped: [" + line + "]");
                continue;
            }
            String host = fields[3];

            //
            //  handle perf measurements 1st since this prog
            //  is being resurrected for the purpose
            //
            if (fields.length > 13  &&  fields[12].equals("MEAS")) {
                meas_lines++;
                //
                //  we trust these to be 'correct' format:
                //      12    13    14    
                //     MEAS <type> <tag> ...
                //
                String m_type = fields[13];
                boolean internal = fields[14].equals("__");
                // compare in descending probability order
                if (m_type.equals("store")) {
                    if (internal) {
                        addBandwidth(meas_store_int, line, fields);
                        store_hosts.count(host);
                    } else {
                        addBandwidth(meas_store, line, fields);
                    }
                } else if (m_type.equals("store_b")) {
                    if (internal) {
                        addBandwidth(meas_store_b_int, line, fields);
                        store_hosts.count(host);
                    } else {
                        addBandwidth(meas_store_b, line, fields);
                    }
                } else if (m_type.equals("store_md")) {
                    addTiming(meas_store_md_int, line, fields);
                } else if (m_type.equals("store_ch")) {
                    addBandwidth(meas_store_ch, line, fields);
                } else if (m_type.equals("store_ch_b")) {
                    addBandwidth(meas_store_ch_b, line, fields);
                } else if (m_type.equals("retrieve")) {
                    if (internal) {
                        addBandwidth(meas_retrieve_int, line, fields);
                        retrieve_hosts.count(host);
                    } else {
                        addBandwidth(meas_retrieve, line, fields);
                    }
                } else if (m_type.equals("retrieve_md")) {
                        addBandwidth(meas_retrieve_md_int, line, fields);
                } else if (m_type.equals("retrieve_ch")) {
                    addBandwidth(meas_retrieve_ch, line, fields);
                } else if (m_type.equals("addmd")) {
                    if (internal)
                        addTiming(meas_addmd_int, line, fields);
                    else
                        addTiming(meas_addmd, line, fields);
                } else if (m_type.equals("getmd")) {
                    if (internal)
                        addTiming(meas_getmd_int, line, fields);
                    else
                        addTiming(meas_getmd, line, fields);
                } else if (m_type.equals("query")) {
                    if (internal)
                        addTiming(meas_query_int, line, fields);
                    else
                        addTiming(meas_query, line, fields);
                } else if (m_type.equals("seluniq")) {
                    if (internal)
                        addTiming(meas_seluniq_int, line, fields);
                    else
                        addTiming(meas_seluniq, line, fields);
                } else if (m_type.equals("delete")) {
                    if (internal)
                        addTiming(meas_delete_int, line, fields);
                    else
                        addTiming(meas_delete, line, fields);
                } else if (m_type.equals("getschema")) {
                    if (internal)
                        addTiming(meas_getschema_int, line, fields);
                    else
                        addTiming(meas_getschema, line, fields);
                } else if (m_type.equals("rretrieve")) {
                    if (internal)
                        addBandwidth(meas_rretrieve_int, line, fields);
                    else
                        addBandwidth(meas_rretrieve, line, fields);
                } else {
                    meas_unexpected.count(m_type);
                }
                continue;
            }

            //
            //  basic periodically-logged cluster stats
            //
            if (fields.length > 12) {
                if (fields[12].equals("diskUsage:")) {
                    sys_meas_lines++;
                    String usage = line.substring(0, 15);
                    for (int ii=13; ii<fields.length; ii++) {
                        if (fields[ii].startsWith("/data/"))
                            usage += " " + fields[ii].substring(6, 
                                                         fields[ii].length());
                        else
                            usage += " " + fields[ii];
                    }
                    addDiskUsage(host, usage);
                    continue;
                }
                if (fields[12].equals("Load")  &&  fields[13].equals("avg:")) {
                    sys_meas_lines++;
                    String load = line.substring(0, 15);
                    for (int ii=14; ii<fields.length; ii++)
                        load += " " + fields[ii];
                    addLoad(host, load);
                    continue;
                }
            }
            

            //
            //  back to the original log analyzer, still
            //  somewhat relevant
            //
            all_hosts.count(host);

            // skip cheat node
            if (!host.startsWith("hcb")) {
                cheat_lines++;
                continue;
            }

            String type = fields[4];

            String what = null;
            boolean info = false;
            boolean kern = false;
            boolean warn = false;
            boolean ext_warn = false;
            boolean severe = false;
            boolean ext_severe = false;
            boolean ntp = false;
            boolean log = false;
            boolean ssh = false;
            boolean mount = false;
            boolean dhcp = false;
            boolean script = false;

            if (type.equals("java:")) {
                what = fields[10];
                String level = fields[9];
                // compare in order of frequency descending
                if (level.equals("INFO"))
                    info = true;
                else if (level.equals("WARNING"))
                    warn = true;
                else if (level.equals("EXT_WARNING"))
                    ext_warn = true;
                else if (level.equals("SEVERE"))
                    severe = true;
                else if (level.equals("EXT_SEVERE"))
                    ext_severe = true;
                else if (fields.length > 12  &&  fields[12].equals("WARNING:"))
                    warn = true;  // from linux days ..?
            } else if (type.equals("logger:")) {

                // XXX offsetting linux numbers by 3 consistent
                // w/ changes above but these may not apply to solaris

                if (fields.length > 12) {
                    type = fields[9];
                    if (type.equals("java:")) {
                         what = fields[15];
                         if (fields[14].equals("INFO")) {
                              if (fields[17].equals("WARNING:"))
                                  warn = true;
                              else
                                  info = true;
                         } else if (fields[14].equals("WARNING")) {
                             warn = true;
                         } else if (fields[14].equals("SEVERE")) {
                             severe = true;
                         } else if (fields[14].equals("EXT_SEVERE")) {
                             ext_severe = true;
                         }
                    } else {
                         what = fields[12];
                         if (type.startsWith("xntpd")) {
                             ntp = true;
                         } else if (type.equals("rc-scripts:")) {
                             script = true;
                         } else if (type.startsWith("syslog")) {
                             log = true;
                         }
//else
//System.out.println("XXX " + type);
                    }
                    if (fields.length > 13  &&  fields[13].equals("WARNING:"))
                        warn = true;
                } else {
                    what = fields[8];
                }
            } else if (type.equals("genunix:")  ||  type.equals("bge:")  ||
                     type.equals("ip:")  ||  type.equals("consconfig_dacf:")) {
                //
                // XXX could count genunix:/bge:/ip: separately,
                // but maybe enough that 'what'="kern.warning]" for all
                // (added for solaris) Actually what is the level here,
                // so we should break down kern.info/notice/warning etc
                // except these msgs aren't formatted with a regular
                // 'what' field, so hacking it for now.
                //
                what = fields[7];
                String level = fields[8];
                if (level.equals("WARNING:")) {
                    warn = true;
                    if (line.indexOf("trying to be our address") != -1)
                        hw_addr++;
                    else if (line.indexOf("screen device") != -1)
                        screen_device++;
                } else if (level.equals("SEVERE:"))
                    severe = true;
                else if (what.equals("kern.info]"))
                    info = true;
                else if (what.equals("kern.notice]"))
                    info = true;  // easy way out
                else
                    extras.add(line);
            } else if (type.equals("rpc.mountd:")) {
                // does this happen in solaris?
                mount = true;
            } else if (type.equals("rc-scripts:")) {
                // does this happen in solaris?
                script = true;
            } else if (type.equals("dhcpd:")) {
                // does this happen in solaris?
                dhcp = true;
            } else if (type.startsWith("ntpd")) {
                // does this happen in solaris?
                ntp = true;
            } else if (type.startsWith("syslog")) {
                // does this happen in solaris?
                log = true;
            } else if (type.startsWith("ssh")) {
                // does this happen in solaris?
                ssh = true;
            } else {
                // does this happen in solaris?
                 what = fields[4];
                 if (fields[5].equals("WARNING:"))
                     warn = true;
            }

            if (info) {
                 infos++;
                 info_what.count(what);
                 //if (line.indexOf("thread pools have already been created. Waiting for free threads") != -1) {
                     // does this happen in solaris?
                     //thread_wait++;
                 //}
            } else if (warn) {
                 warnings++;
                 warn_hosts.count(host);
                 warn_what.count(what);

                 // does this happen in solaris?
                 if (line.indexOf(" NO MASTER FOUND ") != -1)
                     no_master++;
/*
                 else if (line.indexOf("failed to receive heartbeat") != -1)
                     no_heartbeat++;
                 else if (line.indexOf("Failed to get the live schema, backing to the default one") != -1)
                     default_schema++;
                 else if (line.indexOf(" has been deleted: com.sun.honeycomb.oa.DeletedFragmentException: -1 has been deleted") != -1)
                     frag_deleted++;
*/
                 else if (line.indexOf("Failed to init frag") != -1)
                     frag_init_fail++;
                 else if (fnull.matcher(line).matches())
                     frag_null++;
                 else if (fbad.matcher(line).matches())
                     frag_no_good++;
            } else if (ext_warn) {
                 ext_warnings++;
                 warn_hosts.count(host);
                 warn_what.count(what);
                 if (line.indexOf("Heartbeat missed") != -1)
                     heartbeat_missed++;
            } else if (severe) {
                 severes++;
                 severe_hosts.count(host);
                 severe_what.count(what);
                 if (line.indexOf("uncaughtException") != -1)
                     uncaught++;
            } else if (ext_severe) {
                 ext_severes++;
                 severe_hosts.count(host);
                 severe_what.count(what);
                 if (line.indexOf("uncaughtException") != -1)
                     uncaught++;
            //} else if (kern) {
                 //kernels++;
                 //kernel_hosts.count(host);
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
                    line.indexOf("SEVERE") != -1) {
                    extras.add(line);
                }
            }
        }
        lr.close();
        closeDots();
        if (!metrics_mode) {
            System.out.println("======================= lines");
            if (infos > 0)
                System.out.println("info        " + infos);
            if (warnings > 0)
                System.out.println("warn        " + warnings);
            if (ext_warnings > 0)
                System.out.println("ext_warn    " + ext_warnings);
            if (severes > 0)
                System.out.println("severe      " + severes);
            if (ext_severes > 0)
                System.out.println("ext_severe  " + ext_severes);
            //System.out.println("kernel    " + kernels);
            if (meas_lines > 0)
                System.out.println("metrics     " + meas_lines);
            if (sys_meas_lines > 0)
                System.out.println("sys_metrics " + sys_meas_lines);
            if (cheat_lines > 0)
                System.out.println("non-node    " + cheat_lines);
            if (others > 0)
                System.out.println("others      " + others);
            System.out.println(    "TOTAL       " + lines);
            if (extras.size() > 0)
                System.out.println("unparsed    " + extras.size() + 
                         "    (unparsed warnings/severes - use -v to show)");
            System.out.println("======================= hosts");
            System.out.print(all_hosts.sort().toString());
            System.out.println("======================= info");
            if (thread_wait > 0) 
                System.out.println("thread_wait " + thread_wait);
            System.out.println("================ what");
            System.out.print(info_what.sort().toString());
            if (warnings > 0) {
                System.out.println("======================= warning");
                if (hw_addr > 0)
                    System.out.println("ip_conflict        " + hw_addr);
                if (no_master > 0)
                    System.out.println("no_master          " + no_master);
                if (no_heartbeat > 0)
                    System.out.println("no_heartbeat       " + no_heartbeat);
                if (heartbeat_missed > 0)
                    System.out.println("heartbeat_missed   " + heartbeat_missed);
                if (default_schema > 0)
                    System.out.println("default_schema     " + default_schema);
                if (frag_null > 0)
                    System.out.println("frag_null          " + frag_null);
                if (frag_deleted > 0)
                    System.out.println("frag_deleted       " + frag_deleted);
                if (frag_no_good > 0)
                    System.out.println("frag_no_good       " + frag_no_good);
                if (frag_init_fail > 0)
                    System.out.println("frag_init_fail     " + frag_init_fail);
                if (screen_device > 0)
                    System.out.println("screen_device      " + screen_device);
                System.out.println("================ hosts");
                System.out.print(warn_hosts.sort().toString());
                System.out.println("================ what");
                System.out.print(warn_what.sort().toString());
            }
            if (severes + ext_severes > 0) {
                System.out.println("======================= severe, ext_severe");
                if (uncaught > 0)
                    System.out.println("uncaught_exception " + uncaught);
                System.out.println("================ hosts");
                System.out.print(severe_hosts.sort().toString());
                System.out.println("================ what");
                System.out.print(severe_what.sort().toString());
            }
            //System.out.println("======================= kernel");
            //System.out.println("================ hosts");
            //System.out.print(kernel_hosts.sort().toString());
            if (mounts + ntps + scripts + dhcps + logs + sshs > 0) {
                System.out.println("======================= daemons/misc");
                if (mounts > 0)
                    System.out.println("rpc.mountd " + mounts);
                if (ntps > 0)
                    System.out.println("ntpd       " + ntps);
                if (scripts > 0)
                    System.out.println("rc-scripts " + scripts);
                if (dhcps > 0)
                    System.out.println("dhcpd      " + dhcps);
                if (logs > 0)
                    System.out.println("syslog     " + logs);
                if (sshs > 0)
                    System.out.println("ssh        " + sshs);
            }
            if (show_extras  &&  extras.size() > 0) {
                System.out.println("======================= unexpected format:");
                for (int i=0; i<extras.size(); i++)
                    System.out.println((String) extras.get(i));
            }
            System.out.println("======================= ops/host");
            System.out.println("================ store");
            System.out.print(store_hosts.sort().toString());
            System.out.println("================ retrieve");
            System.out.print(retrieve_hosts.sort().toString());

            //
            //  MEAS
            //
            System.out.println("======================= metrics");
            if (meas_store.getN() > 0)
                System.out.println(meas_store.toString());
            if (meas_store_b.getN() > 0)
                System.out.println(meas_store_b.toString());
            if (meas_store_ch.getN() > 0)
                System.out.println(meas_store_ch.toString());
            if (meas_store_ch_b.getN() > 0)
                System.out.println(meas_store_ch_b.toString());
            if (meas_store_int.getN() > 0)
                System.out.println(meas_store_int.toString());
            if (meas_store_b_int.getN() > 0)
                System.out.println(meas_store_b_int.toString());
            if (meas_store_md_int.getN() > 0)
                System.out.println(meas_store_md_int.toString());
            if (meas_retrieve.getN() > 0)
                System.out.println(meas_retrieve.toString());
            if (meas_retrieve_ch.getN() > 0)
                System.out.println(meas_retrieve_ch.toString());
            if (meas_retrieve_int.getN() > 0)
                System.out.println(meas_retrieve_int.toString());
            if (meas_retrieve_md_int.getN() > 0)
                System.out.println(meas_retrieve_md_int.toString());
            if (meas_rretrieve.getN() > 0)
                System.out.println(meas_rretrieve.toString());
            if (meas_rretrieve_int.getN() > 0)
                System.out.println(meas_rretrieve_int.toString());
            if (meas_addmd.getN() > 0)
                System.out.println(meas_addmd.toString());
            if (meas_addmd_int.getN() > 0)
                System.out.println(meas_addmd_int.toString());
            if (meas_getmd.getN() > 0)
                System.out.println(meas_getmd.toString());
            if (meas_getmd_int.getN() > 0)
                System.out.println(meas_getmd_int.toString());
            if (meas_query.getN() > 0)
                System.out.println(meas_query.toString());
            if (meas_query_int.getN() > 0)
                System.out.println(meas_query_int.toString());
            if (meas_seluniq.getN() > 0)
                System.out.println(meas_seluniq.toString());
            if (meas_seluniq_int.getN() > 0)
                System.out.println(meas_seluniq_int.toString());
            if (meas_delete.getN() > 0)
                System.out.println(meas_delete.toString());
            if (meas_delete_int.getN() > 0)
                System.out.println(meas_delete_int.toString());
            if (meas_getschema.getN() > 0)
                System.out.println(meas_getschema.toString());
            if (meas_getschema_int.getN() > 0)
                System.out.println(meas_getschema_int.toString());

            printStates("diskUsage", diskStart, diskEnd);
            printStates("loadAverage", loadStart, loadEnd);

            //
            //  time boundaries
            //
            if (start_time != null  ||  end_time != null)
                System.out.println("======================= boundaries");
            if (start_time != null)
                System.out.println("START LOG AT: " + start_time +
                                 " skipped " + skipped_lines + " lines");
            if (end_time != null)
                System.out.println("END LOG AT: " + end_time + 
                                  " truncated_read=" + truncated);
        } else {
            //
            //  metrics mode for building tables
            //  from repeated runs  XXX simple case for now..
            //
            if (meas_store.getTotalCases() == 0  
                ||  meas_store_b_int.getTotalCases() == 0
                ||  meas_store.getTotalCases() != 
                    meas_store_b_int.getTotalCases()) {
                System.out.println("needs more programming");
                System.exit(1);
            }
            printBW("store",  meas_store, meas_store_b_int);

            if (meas_retrieve.getTotalCases() == 0  
                ||  meas_retrieve_int.getTotalCases() == 0
                ||  meas_retrieve.getTotalCases() != 
                    meas_retrieve_int.getTotalCases()) {
                System.out.println("needs more programming2 " + 
                              meas_retrieve.getTotalCases() + "  " +
                              meas_retrieve_int.getTotalCases());
                System.exit(1);
            }
            printBW("retrieve", meas_retrieve, meas_retrieve_int);
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
            flout("MeasureLog START:    " + start + "\n");
            flout("MeasureLog FINISH:   " + now + "\n");
            flout("MeasureLog Records:  " + total + "\n");
            flout("MeasureLog ok files: " + ok_files + "\n");
            flout("MeasureLog md retrieve failures: " + 
						md_retrieve_failures + "\n");
            if (checkData)
                flout("MeasureLog retrieve failures: " + 
						retrieve_failures + "\n");
            if (checkUnique)
                flout("MeasureLog uniqueness-check failures: " + 
						unique_failures + "\n");
            flout("MeasureLog Total corruption errors: " + 
						corruption_errors + "\n");
            reportExceptions("MeasureLog");

            long deltat = now.getTime() - start.getTime();
            deltat /= 1000;
            if (total > 0)
                flout("MeasureLog API thruput, ms/record:   " + 
						(md_time / total) + "\n");
            if (checkData) {
                flout("MeasureLog Total bytes retrieved:    " + 
							restore_bytes + "\n");
                if (restore_time > 0)
                    flout("MeasureLog Data rate:                " + 
						(restore_bytes * 1000 / 
						restore_time) + " bytes/sec\n");
            }
            if (checkUnique  && total > 0) {
                flout("MeasureLog API unique, ms/record:     " + 
						(md_qtime / total) + "\n");
            }
            deltat = now.getTime() - start.getTime();
            if (deltat > 0)
                flout("MeasureLog Total thruput, ms/record: " + 
						(deltat / total) + "\n");
            try {
                new File(lastfile).delete();
            } catch (Exception e) {}
        }
    }
*/
}

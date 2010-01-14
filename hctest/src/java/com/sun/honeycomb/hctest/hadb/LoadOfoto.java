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



package com.sun.honeycomb.hctest.hadb;

import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Random;

import java.io.File;
import java.io.PrintStream;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;

import com.sun.honeycomb.common.NewObjectIdentifier;

public class LoadOfoto
{
    private static final int nbLevels = 6;

    private String dbHost = null;
    private int nbThreads = 1;
    private int startDir = 0;
    private int dirsPerThread = 2;
    private String tag = "deflttag";
    boolean print = false;
    boolean all = false;
    boolean get = false;
    boolean dd = false;
    DataDoctor ddoc = null;
    boolean verbose = false;

    private int puts = 0;
    private long maxput = 0;
    private long minput = 9999999;
    private long puttime = 0;
    private long t0 = 0;
    private boolean checkPut = false;
    private int puterrors = 0;
    private Getter getter = null;
    private int gets = 0;
    private long maxget = 0;
    private long minget = 9999999;
    private long gettime = 0;
    private int geterrors = 0;
    private LinkedList paths = new LinkedList();

    String[] schema = {
        "ofoto.dir1", "ofoto.dir2", "ofoto.dir3", 
        "ofoto.dir4", "ofoto.dir5", "ofoto.dir6"
    };

    private void parseArguments(String[] args) {

        String newout = null;

        char curOption = 0;

        // parse arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                curOption = args[i].substring(1).charAt(0);
                if (curOption == 'v') {
                    verbose = true;
                } else if (curOption == 'a') {
                    all = true;
                } else if (curOption == 'g') {
                    get = true;
                } else if (curOption == 'p') {
                    print = true;
                } else if (curOption == 'C') {
                    checkPut = true;
                } else if (curOption == 'd') {
                    dd = true;
                }
                continue;
            }
            switch(curOption) {
            case 'c':
                dbHost =  args[i];
                break;
            case 't':
                nbThreads = Integer.parseInt(args[i]);
                break;
            case 'T':
                tag = args[i];
                break;
            case 's':
                startDir = Integer.parseInt(args[i]);
                break;
            case 'n':
                dirsPerThread = Integer.parseInt(args[i]);
                break;
            case 'o':
                newout = args[i];
                break;

            default:
                usage();
                System.exit(-1);
                break;
            } 
        }
        if (dbHost == null) {
            usage();
            System.exit(-1);
        }
        if (newout != null) {
            try {
                System.setOut(new PrintStream(new File(newout)));
            } catch (Exception e) {
                System.err.println(newout + ": " + e);
                System.exit(1);
            }
        }
        println("Start ofoto test dbhost = " + dbHost +
                           " with nbThreads = " + nbThreads +
                           ", startDir = " + startDir +
                           ", dirsPerThread = " + dirsPerThread +
                           ", tag = " + tag +
                           ", all = " + all +
                           ", get = " + get +
                           ", verbose = " + verbose);
    }
    private static void usage() {
        println("Usage: LoadOfoto  [-agd] [-o outfile]" +
                           " -c dbhost" +
                           " -T tag" +
                           " -t nbThreads" +
                           " -s startDir" +
                           " -n dirsPerThread");
        println("\t-a:\tall: put(s) + 1 get thread");
        println("\t-d:\tdd: put(s) + 1 datadoctor thread");
        println("\t-g:\tget only (untested)");
        println("\tdefault:\tput only");
    }


    static public void main(String[] args) throws Exception {
        LoadOfoto demo = new LoadOfoto(args);
    }

    private static void println(String s) {
        System.out.println(s);
    }
    private static void println(String[] ss) {
        StringBuffer sb = new StringBuffer();
        for (int i=0; i<ss.length; i++)
            sb.append("/").append(ss[i]);
        println(ss.toString());
    }

    void quit(int code) {
        long t2 = System.currentTimeMillis();
        int errors = puterrors;
        if (puts > 0) {
            System.out.println("puts: " + puts);
            println("avg put: " + (puttime/puts) + " ms");
            println("min put: " + minput + " ms");
            println("max put: " + maxput + " ms");
            if (t0 != 0)
                System.out.println("total puts/sec = " + 
                               (((float)puts*1000)/(float)(t2-t0)));
            if (puterrors > 0)
                println("put errors: " + puterrors);
            if (getter != null) {
                endGetter();
                getter.sum();
                errors += getter.errors;
                if (getter.errors > 0)
                    println("get errors: " + getter.errors);
            }
            if (ddoc != null) {
                ddDone = true;
                ddoc.sum();
            }
        }
        if (gets > 0) {
            println("gets: " + gets);
            println("avg get: " + (gettime/gets) + " ms");
            println("min get: " + minget + " ms");
            println("max get: " + maxget + " ms");
            if (t0 != 0)
                System.out.println("total gets/sec = " + 
                               (((float)gets*1000)/(float)(t2-t0)));
            errors += geterrors;
        }
        if (errors > 0)
            println("ERROR");

        System.exit(code);
    }

    String[] nextPath() {
        synchronized(paths) {
            if (paths.size() == 0) {
                try {
                    paths.wait();
                } catch (Exception ignore) {}
            }
            if (paths.size() == 0)
                return null;
            return (String[]) paths.removeFirst();
        }
    }
    void putPath(String[] path) {
        synchronized(paths) {
            paths.add(path);
            paths.notifyAll();
        }
    }
    void endGetter() {
        synchronized(paths) {
            paths.notifyAll();
        }
    }

    private ArrayList oids = new ArrayList();
    void addOid(String oid) {
        synchronized(oids) {
            if (oids.size() > 512)
                return;
            oids.add(oid);
            oids.notifyAll();
        }
    }
    private boolean ddDone = false;
    private Random rand1 = new Random();
    String getOid() {
        if (ddDone)
            return null;
        synchronized(oids) {
            if (oids.size() == 0) {
                try {
                    oids.wait();
                } catch (Exception ignore) {}
            }
            if (oids.size() == 0)
                return null;
        
            int choice = rand1.nextInt(oids.size());
            return (String) oids.get(choice);
        }
    }

    public LoadOfoto(String [] args) throws Exception {

        parseArguments(args);

        runTest();
    }

    public void runTest() throws Exception {
        Putter[] runners = new Putter[nbThreads];
        Getter2[] getters = new Getter2[nbThreads];

        int cur = startDir;
        int batchNb = 0;
        t0 = System.currentTimeMillis();
        println("starting threads");
        if (get) {
            for (int i = 0; i < nbThreads; i++) {
                getters[i] = new Getter2(cur, cur + dirsPerThread-1);
                getters[i].start();
                cur += dirsPerThread;
            }
        } else {
            for (int i = 0; i < nbThreads; i++) {
                runners[i] = new Putter(cur, cur + dirsPerThread-1);
                runners[i].start();
                cur += dirsPerThread;
            }
            if (all) {
                getter = new Getter();
                getter.start();
            }
            if (dd) {
                ddoc = new DataDoctor();
                ddoc.start();
            }
        }

        println("threads started");
        if (get) {
            for (int i = 0; i < nbThreads; i++) {
                try { 
                    getters[i].join();
                    gets += getters[i].gets;
                    gettime += getters[i].getTime;
                    geterrors += getters[i].errors;
                    if (getters[i].maxget > maxget)
                        maxget = getters[i].maxget;
                    if (getters[i].minget < minget)
                        minget = getters[i].minget;
                } catch (InterruptedException e) {
                    println("interrupted [" + i + "]: " + e);
                }
            }
        } else {
            for (int i = 0; i < nbThreads; i++) {
                try { 
                    runners[i].join();
                    // println((new Date()).toString() + " join " + i);
                    puts += runners[i].puts;
                    puterrors += runners[i].errors;
                    puttime += runners[i].putTime;
                    if (runners[i].maxput > maxput)
                        maxput = runners[i].maxput;
                    if (runners[i].minput < minput)
                        minput = runners[i].minput;
                } catch (InterruptedException e) {
                    println("interrupted [" + i + "]: " + e);
                }
            }
            if (all) {
                getter.done();
                endGetter();
                getter.join();
            }
        }
        quit(0);
    }

    Connection connect() {   
        String SqlUrl = "jdbc:sun:hadb:system+superduper@" + dbHost + ":"
                                        + 15005;
        try {
            Class.forName("com.sun.hadb.jdbc.Driver");
            Connection conn = DriverManager.getConnection(SqlUrl);
            conn.setAutoCommit(true);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            return conn;
        } catch (Exception e) {
            System.err.println("connect: " + e);
            System.exit(1);
        }
        return null;
    }

    private class Putter extends Thread {
        
        private int startIdx;
        private int endIdx;
        int puts = 0;
        int gets = 0;
        long maxput = 0;
        long minput = 999999;
        int errors = 0;
        long putTime = 0;
        Connection conn = null;
        Statement stmt = null;

        public Putter(int start, int end) throws Exception {
            startIdx = start;
            endIdx = end;
            conn = connect();
            stmt = conn.createStatement();
        }
        
        public void run() {

            // println("Starting, start = " + startIdx + ", end = " + endIdx);
            PathIterator it = new PathIterator(nbLevels, startIdx, endIdx);
            String[] curPath = null;
            int count = 1;
            while ((curPath = it.getNextPath()) != null) {
                if (print) {
                    println(curPath);
                    continue;
                }
                put(curPath, tag + "_ofoto5k");
                put(curPath, tag + "_ofoto15k");
                put(curPath, tag + "_ofoto50k");
                put(curPath, tag + "_ofoto900k");
                count++;
                if (count % 5 == 0)
                    putPath(curPath);
            }
        }

        void put(String[] dirs, String fname) {

            // make an oid 
            String oid = generateOID();
            addOid(oid);

            long t1 = System.currentTimeMillis();

            // insert a self-referential oid record
            insertAttr(oid, "system.object_id", oid);

            // insert metadata
            for (int i=0; i<nbLevels; i++)
               insertAttr(oid, schema[i], dirs[i]);
            insertAttr(oid, "ofoto.fname", fname);

            t1 = System.currentTimeMillis() - t1;

            puts++;
            putTime += t1;
            if (t1 > maxput)
                maxput = t1;
            if (t1 < minput)
                minput = t1;

        }
        void insertAttr(String oid, String name, String value) {
            try {
                stmt.execute("INSERT INTO stringattribute (objectid, attrname, attrvalue) VALUES ('" + oid + "', '" + name + "', '" + value + "')");
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        Random rand = new Random();
        String generateOID() {
            int layout = rand.nextInt(10000);
            int chunkNumber = 1;
            byte type = (byte)0x01;
            return (new NewObjectIdentifier(layout,type,chunkNumber, null)).toString();
        }
    }

    private class Getter extends Thread {
        
        int gets = 0;
        long maxget = 0;
        long minget = 999999;
        long getTime = 0;
        boolean done = false;
        int errors = 0;
        Connection conn = null;
        Statement stmt = null;

        public Getter() throws Exception {
            conn = connect();
            stmt = conn.createStatement();
        }
        public void done() {
            done = true;
        }
        public void sum() {
            println("gets: " + gets);
            if (gets > 0) {
                println("avg get: " + (getTime / gets) + " ms");
                println("min get: " + minget + " ms");
                println("max get: " + maxget + " ms");
                println("ungot: " + paths.size());
            }
        }

        public void run() {
            boolean retry = false;
            int nbRetry = 0;

            println("getter is running");

            while (!done) {
                String[] curPath = nextPath();
                if (curPath == null)
                    break;

                // retrieve one file of each group of 4
                get(curPath, tag + "_ofoto15k");
                get(curPath, tag + "_ofoto50k");
            }
        }
        void get(String[] dirs, String fname) {

            String query = "select LOGsystem_object_id.objectid, LOGsystem_object_id.attrvalue, LOGsystem_object_ctime.attrvalue, LOGsystem_object_size.attrvalue from stringattribute as LOGofoto_dir2, longattribute as LOGsystem_object_ctime, stringattribute as LOGofoto_dir3, stringattribute as LOGofoto_dir1, stringattribute as LOGofoto_fname, longattribute as LOGsystem_object_size, stringattribute as LOGofoto_dir5, stringattribute as LOGofoto_dir6, stringattribute as LOGsystem_object_id, stringattribute as LOGofoto_dir4 where LOGofoto_dir2.objectid=LOGsystem_object_id.objectid " +
                " AND LOGsystem_object_ctime.objectid=LOGsystem_object_id.objectid" +
                " AND LOGofoto_dir3.objectid=LOGsystem_object_id.objectid" +
                " AND LOGofoto_dir1.objectid=LOGsystem_object_id.objectid" + 
                " AND LOGofoto_fname.objectid=LOGsystem_object_id.objectid" +
                " AND LOGsystem_object_size.objectid=LOGsystem_object_id.objectid" +
                " AND LOGofoto_dir5.objectid=LOGsystem_object_id.objectid" +
                " AND LOGofoto_dir6.objectid=LOGsystem_object_id.objectid" +
                " AND LOGofoto_dir4.objectid=LOGsystem_object_id.objectid" +
                " AND LOGsystem_object_ctime.attrname='system.object_ctime'" +
                " AND LOGsystem_object_size.attrname='system.object_size'" +
                " AND LOGsystem_object_id.attrname='system.object_id'" +
                " AND LOGofoto_fname.attrname='ofoto.fname'" +
                " AND LOGofoto_fname.attrvalue = '" + fname + "'" +
                " AND LOGofoto_dir1.attrname='ofoto.dir1'" +
                " AND LOGofoto_dir1.attrvalue = '" + dirs[0] + "'" +
                " AND LOGofoto_dir2.attrname='ofoto.dir2'" +
                " AND LOGofoto_dir2.attrvalue = '" + dirs[1] + "'" +
                " AND LOGofoto_dir3.attrname='ofoto.dir3'" +
                " AND LOGofoto_dir3.attrvalue = '" + dirs[2] + "'" +
                " AND LOGofoto_dir4.attrname='ofoto.dir4'" +
                " AND LOGofoto_dir4.attrvalue = '" + dirs[3] + "'" +
                " AND LOGofoto_dir5.attrname='ofoto.dir5'" +
                " AND LOGofoto_dir5.attrvalue = '" + dirs[4] + "'" +
                " AND LOGofoto_dir6.attrname='ofoto.dir6'" +
                " AND LOGofoto_dir6.attrvalue = '" + dirs[5] + "'" +
                " ORDER BY LOGsystem_object_id.objectid";

            try {
                long t1 = System.currentTimeMillis();
                ResultSet rs = stmt.executeQuery(query);
                t1 = System.currentTimeMillis() - t1;
                gets++;
                getTime += t1;
                if (t1 > maxget)
                    maxget = t1;
                if (t1 < minget)
                    minget = t1;
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private class Getter2 extends Thread {
        
        private int startIdx;
        private int endIdx;
        int gets = 0;
        long maxget = 0;
        long minget = 999999;
        int errors = 0;
        long getTime = 0;
        Connection conn = null;
        Statement stmt = null;

        public Getter2(int start, int end) throws Exception {
            startIdx = start;
            endIdx = end;
            conn = connect();
            stmt = conn.createStatement();
        }
        
        public void run() {

            // println("Starting, start = " + startIdx + ", end = " + endIdx);
            PathIterator it = new PathIterator(nbLevels, startIdx, endIdx);
            String[] curPath = null;
            int count = 1;
            while ((curPath = it.getNextPath()) != null) {
                if (print) {
                    println(curPath);
                    continue;
                }
                get(curPath, tag + "_ofoto5k");
                get(curPath, tag + "_ofoto15k");
                get(curPath, tag + "_ofoto50k");
                get(curPath, tag + "_ofoto900k");
                count++;
            }
        }

        void get(String[] dirs, String fname) {

            String query = "select LOGsystem_object_id.objectid, LOGsystem_object_id.attrvalue, LOGsystem_object_ctime.attrvalue, LOGsystem_object_size.attrvalue from stringattribute as LOGofoto_dir2, longattribute as LOGsystem_object_ctime, stringattribute as LOGofoto_dir3, stringattribute as LOGofoto_dir1, stringattribute as LOGofoto_fname, longattribute as LOGsystem_object_size, stringattribute as LOGofoto_dir5, stringattribute as LOGofoto_dir6, stringattribute as LOGsystem_object_id, stringattribute as LOGofoto_dir4 where LOGofoto_dir2.objectid=LOGsystem_object_id.objectid " +
                " AND LOGsystem_object_ctime.objectid=LOGsystem_object_id.objectid" +
                " AND LOGofoto_dir3.objectid=LOGsystem_object_id.objectid" +
                " AND LOGofoto_dir1.objectid=LOGsystem_object_id.objectid" + 
                " AND LOGofoto_fname.objectid=LOGsystem_object_id.objectid" +
                " AND LOGsystem_object_size.objectid=LOGsystem_object_id.objectid" +
                " AND LOGofoto_dir5.objectid=LOGsystem_object_id.objectid" +
                " AND LOGofoto_dir6.objectid=LOGsystem_object_id.objectid" +
                " AND LOGofoto_dir4.objectid=LOGsystem_object_id.objectid" +
                " AND LOGsystem_object_ctime.attrname='system.object_ctime'" +
                " AND LOGsystem_object_size.attrname='system.object_size'" +
                " AND LOGsystem_object_id.attrname='system.object_id'" +
                " AND LOGofoto_fname.attrname='ofoto.fname'" +
                " AND LOGofoto_fname.attrvalue = '" + fname + "'" +
                " AND LOGofoto_dir1.attrname='ofoto.dir1'" +
                " AND LOGofoto_dir1.attrvalue = '" + dirs[0] + "'" +
                " AND LOGofoto_dir2.attrname='ofoto.dir2'" +
                " AND LOGofoto_dir2.attrvalue = '" + dirs[1] + "'" +
                " AND LOGofoto_dir3.attrname='ofoto.dir3'" +
                " AND LOGofoto_dir3.attrvalue = '" + dirs[2] + "'" +
                " AND LOGofoto_dir4.attrname='ofoto.dir4'" +
                " AND LOGofoto_dir4.attrvalue = '" + dirs[3] + "'" +
                " AND LOGofoto_dir5.attrname='ofoto.dir5'" +
                " AND LOGofoto_dir5.attrvalue = '" + dirs[4] + "'" +
                " AND LOGofoto_dir6.attrname='ofoto.dir6'" +
                " AND LOGofoto_dir6.attrvalue = '" + dirs[5] + "'" +
                " ORDER BY LOGsystem_object_id.objectid";

            try {
                long t1 = System.currentTimeMillis();
                ResultSet rs = stmt.executeQuery(query);
                t1 = System.currentTimeMillis() - t1;
                gets++;
                getTime += t1;
                if (t1 > maxget)
                    maxget = t1;
                if (t1 < minget)
                    minget = t1;
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private class DataDoctor extends Thread {

        int queries = 0;
        long qTime = 0;
        long maxq = 0;
        long minq = 999999;
        Connection conn = null;
        Statement stmt = null;

        DataDoctor() {
            conn = connect();
            try {
                stmt = conn.createStatement();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        public void run() {
            println("datadoctor is running");
            String oid;
            while ((oid = getOid()) != null)
                query(oid);
        }
        public void sum() {
            println("dd queries: " + queries);
            if (queries > 0) {
                println("avg q: " + (qTime / queries) + " ms");
                println("min q: " + minq + " ms");
                println("max q: " + maxq + " ms");
            }
        }
        void query(String oid) {
            String query = "select LOGsystem_object_id.objectid" +
                   " from stringattribute as LOGsystem_object_id" +
                   " where LOGsystem_object_id.attrname='system.object_id'" +
                   " AND (LOGsystem_object_id.attrvalue = '" + oid +
                   "')  ORDER BY LOGsystem_object_id.objectid";
            try {
                long t1 = System.currentTimeMillis();
                ResultSet rs = stmt.executeQuery(query);
                t1 = System.currentTimeMillis() - t1;
                queries++;
                qTime += t1;
                if (t1 > maxq)
                    maxq = t1;
                if (t1 < minq)
                    minq = t1;
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    //
    // Iterator to build the ofoto Path
    //
    private class PathIterator {
        private int DIR_LEVELS;
        private int fields[];
        private String arr[];
        private int startIndex;
        private int endIndex;
        private boolean init;

        public PathIterator(int nb, int start, int end) {
            DIR_LEVELS = nb;
            startIndex = start;
            endIndex = end;
            fields = new int[DIR_LEVELS];
            arr = new String[DIR_LEVELS];

            reinit();
        }

        public void reinit() {
            for (int i = 0; i < DIR_LEVELS; i++) {
                fields[i] = startIndex;
            }
            init = true;
        }

        public String[] getNextPath() {
            if (!init) {
                return null;
            }

            for (int i=0; i<DIR_LEVELS; i++) {
                String s = Integer.toHexString(fields[i]);
                if (s.length() == 1)
                    s = "0" + s;
                arr[i] = s;
            }
            for (int i = (DIR_LEVELS - 1); i >= 0; i--) {
                if (fields[i] >= endIndex) {
                    fields[i] = startIndex;
                    if (i == 0) {
                        init = false;
                    }
                } else {
                    fields[i]++;
                    break;
                }
            }
            return arr;
        }
    }
}

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



package com.sun.honeycomb.test.util;

import java.io.File;
import java.util.ArrayList;

/**
 *  Recursive lister of files in a directory tree, keeping minimal
 *  info in memory. XXX Permissions are only considered a problem
 *  if the top dir can't be listed. There is a mechanism for checking
 *  if there were inaccessible directories after the fact, and one
 *  for requesting logging/listing of such dirs. Could have a mode
 *  for throwing if any seen.
 */
public class FileLister {

    private File root = null;
    private ArrayList dirs = new ArrayList();
    private File[] curList = null;
    private int index = 0;
    private StringBuffer sb = null;
    private boolean sawInaccessible = false;

    public FileLister(String rootdir) throws HoneycombTestException {
        root = new File(rootdir);
        try {
            if (!root.isDirectory()) {
                throw new HoneycombTestException("not a directory: " + rootdir);
            }
        } catch (Exception e) {
            throw new HoneycombTestException(rootdir, e);
        }
        curList = root.listFiles();
        if (curList == null)
            throw new HoneycombTestException("can't list top dir: " + rootdir);
    }

    /**
     *  Reset to start at beginning again. Loses inaccessible dir info.
     */
    public void reset() {
        curList = root.listFiles();
        dirs.clear();
        index = 0;
        sb = null;
        sawInaccessible = false;
    }

    /**
     *  Start internal logging of inaccessible directories.
     */
    public void logInaccessible() {
        sb = new StringBuffer();
    }

    /**
     *  List all inaccessible directories seen since last 
     *  call to logInaccessible().
     */
    public String listInaccessible() {
        if (sb == null)
            return null;
        return sb.toString();
    }

    /**
     *  Report whether any inaccessible dirs were seen.
     */
    public boolean sawInaccessible() {
        return sawInaccessible;
    }

    /**
     *  Get next file, return null if done.
     */
    public File nextFile() {
        //
        //  iterate over directory listings, pushing each
        //  directory onto the dirs stack, and popping a new
        //  dir from the stack when each listing is exhausted,
        //  until a file is found.
        //
        while (true) {
            if (index >= curList.length) {

                if (dirs.size() == 0)
                    return null;

                //
                //  get a new dir list
                //
                curList = null;
                while (dirs.size() > 0) {
                    //
                    //  pop off last dir
                    //
                    File nextDir = (File) dirs.remove(dirs.size()-1);
                    curList = nextDir.listFiles();
                    if (curList != null  &&  curList.length > 0)
                        break;
                    if (curList == null) {
                        sawInaccessible = true;
                        if (sb != null)
                            sb.append(nextDir.toString()).append("\n");
                    }
                }
                //
                //  check if nothing more to check
                //
                if (curList == null  ||  curList.length == 0)
                    return null;

                index = 0;
            }

            //
            //  examine current list
            //
            while (index < curList.length) {
                File f = curList[index];
                index++;
                if (f.isDirectory()) {
                    dirs.add(f);
                } else {
                    return f;
                }
            }
        }
    }

    /**
     *  Count all files (not directories) in tree, 
     *  leaving state reset to start.
     */
    public int countFiles() {
        reset();
        int count = 0;
        while (nextFile() != null)
            count++;
        reset();
        return count;
    }

    /** main is for test */
    public static void main(String[] argv) {
        if (argv.length != 1) {
            System.err.println("usage: xxx <dir>");
            System.exit(1);
        }
        try {
            if (System.getSecurityManager() == null) {
                //System.out.println("security mgr is null");
                if (System.getProperty("java.security.policy") != null) {
                    System.out.println("setting SecurityManager");
                    System.setSecurityManager(new SecurityManager());
                }
            }
            FileLister fl = new FileLister(argv[0]);
            fl.logInaccessible();
            File f = null;
            while ((f = fl.nextFile()) != null)
                System.out.println(f.getPath());
            String s = fl.listInaccessible();
            if (s.length() > 0)
                System.out.println("\n\n=== Inaccessible:\n" + s);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}

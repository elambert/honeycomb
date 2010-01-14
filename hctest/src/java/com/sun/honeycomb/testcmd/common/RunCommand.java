package com.sun.honeycomb.testcmd.common;

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



import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class RunCommand {

    // 'built-in' cmds
    public static final String MV                = "/bin/mv";
    public static final String CP                = "/bin/cp -f";
    public static final String RM                = "/bin/rm -rf";
    public static final String MKDIR             = "/bin/mkdir -p";
    public static final String LOGGER            = "/usr/bin/logger -p info";
    public static final String HOST              = "/usr/bin/host";
    public static final String CMP               = "/usr/bin/cmp";
    public static final String GZIP              = "/bin/gzip";
    public static final String SHA1SUM           = "/usr/bin/sha1sum -b";
    public static final String UMOUNT            = "/bin/umount";
    public static final String CAT               = "cat";
    public static final String LS                = "ls";
    public static final String DF                = "df";
    public static final String UNAME             = "uname"; 
						 // uname needs to be in path!
						 // so that the os-dependent
						 // paths can be chosen..
    public static final String LINUX_NFS_MOUNT   = "/bin/mount -t nfs -o rsize=32768,intr,retrans=10,timeo=600";
    public static final String SOLARIS_NFS_MOUNT = "/sbin/mount -o timeo=600";

    private boolean debuggingEnabled             = false;

    public static String uname = null;  // OS name
    public static String mount_cmd = null;
    public static boolean df_mount_first = true;

    Runtime rt;

    public RunCommand() {
        rt = Runtime.getRuntime();
    }

    // Execute a command, capturing the return value, stdout, & stdin
    public ExitStatus exec(String s) throws HoneycombTestException {
        ExitStatus es = new ExitStatus();
        Process process = null;

        try {
            if (debuggingEnabled) {
                System.out.println("RunCommand: " + s);
            }
            process = rt.exec(s);
            es.setCmdExecuted(s);
        } catch(IOException ioe) {
            throw new HoneycombTestException("Failed to execute: " + s,
                                             ioe);
        }

        try {
            process.waitFor();
        } catch(InterruptedException ie) {
            throw new HoneycombTestException("Failed to execute/wait: " + s,
                                             ie);
        }

        try {
            InputStreamReader isr = new 
				InputStreamReader(process.getInputStream());
            es.setOutStrings(parseCommandOutput(isr));
            isr.close();

            isr = new InputStreamReader(process.getErrorStream());
            es.setErrStrings(parseCommandOutput(isr));
            isr.close();
        } catch(IOException ioe) {
            throw new HoneycombTestException("Failed to get stdout/err: " + s,
                                             ioe);
        }

        es.setReturnCode(process.exitValue());

        if (debuggingEnabled) {
            System.out.println("RunCommand: Exit status: " + es.toString());
        }

        return es;
    }

    // Parse the output of a process exection in exec()
    protected List parseCommandOutput(Reader reader)
        			throws HoneycombTestException {
        BufferedReader bufferedreader = new BufferedReader(reader);
        ArrayList arraylist = new ArrayList();

        try {
            do {
                String s;
                if ((s = bufferedreader.readLine()) == null) {
                    break;
                }

                if (!s.equals("")) {
                    arraylist.add(s);
                }
            } while (true);
        } catch (IOException ioe) {
            throw new HoneycombTestException("Failed to read stream", ioe);
        }

        return arraylist;
    }

    // Call cp
    public void cp(String orig, String copy) throws HoneycombTestException {
        ExitStatus es = null;
        String cmd = CP + " " + orig + " " + copy;
        es = exec(cmd);
        if (es.getReturnCode() != 0) {
            throw new HoneycombTestException("Failed to cp: " + es.toString());
        }
    }

    // Call rm -rf
    public void rm(String path) throws HoneycombTestException {
        ExitStatus es = null;
        String cmd = RM + " " + path;
        es = exec(cmd);
        if (es.getReturnCode() != 0) {
            throw new HoneycombTestException("Failed to run: " + cmd);
        }
    }

    // Call mkdir -p
    public void mkdir(String path) throws HoneycombTestException {
        ExitStatus es = null;
        String cmd = MKDIR + " " + path;
        es = exec(cmd);
        if (es.getReturnCode() != 0) {
            throw new HoneycombTestException("Failed to run: " + cmd);
        }
    }

    // get OS name
    public String uname() throws HoneycombTestException {
        synchronized (this) {
            if (uname == null) {
                ExitStatus es = exec(UNAME);
                if (es.getReturnCode() != 0) {
                    throw new HoneycombTestException(
			"uname: can't get system type: " + es.toString());
                }
                List l = es.getOutStrings();
                uname = (String) l.get(0);
                if (uname.equals("Linux")) {
                    mount_cmd = LINUX_NFS_MOUNT;
                    df_mount_first = false;
                } else if (uname.equals("SunOS")) {
                    mount_cmd = SOLARIS_NFS_MOUNT;
                    df_mount_first = true;
                }
            }
        }
        return uname;
    }

    // Call mount
    public void nfsMount(String serverpath, String mountpoint)
        throws HoneycombTestException {

        // get mount cmd for OS
        synchronized (this) {
            uname();
            if (mount_cmd == null) {
                throw new HoneycombTestException(
			"nfsMount: no mount cmd defined for: " + uname);
            }
        }
        String cmd = mount_cmd + " " + serverpath + " " + mountpoint;
        ExitStatus es = exec(cmd);
        if (es.getReturnCode() != 0) {
            throw new HoneycombTestException("Failed to mount " + serverpath +
                                             " on " + mountpoint + ": " +
						es.toString());
        }
    }

    // Call unmount
    public void nfsUnmount(String mountpoint)
        throws HoneycombTestException {
        ExitStatus es = null;
        String cmd = UMOUNT + " " + mountpoint;
        es = exec(cmd);
        if (es.getReturnCode() != 0) {
            throw new HoneycombTestException("Failed to unmount " + mountpoint,
                                             es);
        }
    }

    // Log a message to syslog
    public void syslog(String s) {
        ExitStatus es = null;
        String cmd = LOGGER + " " + s;
        try {
            exec(cmd);
        } catch (HoneycombTestException ignore) {
        }
    }

    public String host(String s) throws HoneycombTestException {
        String cmd = HOST + " " + s;
        ExitStatus es = exec(cmd);
        if (es.getReturnCode() != 0) {
            List l = es.getErrStrings();
            throw new HoneycombTestException(
				"Failed to get host [" + cmd +"]: " +
				(String)l.get(0));
        }
        List l = es.getOutStrings();
        if (l == null) {
            throw new HoneycombTestException("Nothing returned by [" + cmd +"]");
        }
        int count = l.size();
        if (count < 1) {
            throw new HoneycombTestException("Nothing returned by [" + cmd +"]");
        }
        return((String)l.get(count-1));
    }

    public String cmp(String f1, String f2) throws HoneycombTestException {
        String cmd = CMP + " " + f1 + " " + f2;
        ExitStatus es = exec(cmd);
        if (es.getReturnCode() == 0)
            return null;
        return es.toString();
    }

    public String gzip(String fname) throws HoneycombTestException {
        String cmd = GZIP + " " + fname;
        ExitStatus es = exec(cmd);
        if (es.getReturnCode() == 0)
            return null;
        return es.toString();
    }

    public String sha1sum(String fname) throws HoneycombTestException {
        String cmd = SHA1SUM + " " + fname;
        ExitStatus es = null;
        try {
            es = exec(cmd);
        } catch (HoneycombTestException e) {
            File f = new File(fname);
            if (!f.exists())
                throw new HoneycombTestException("sha1sum(" + fname + 
						"): file does not exist", e);
            throw new HoneycombTestException("Failed to get sha1 [" + 
				cmd +"]", e);
        } catch (Throwable t) {
            throw new HoneycombTestException("Failed to get sha1 [" + cmd +"]", t);
        }
        if (es == null) {
            throw new HoneycombTestException("Failed to get sha1 [" + 
				cmd +"]: no error code");
        }
        if (es.getReturnCode() != 0) {
            List l = es.getErrStrings();
            throw new HoneycombTestException(
                                "Failed to get sha1 [" + cmd +"]: " + l);
        }
        List l = es.getOutStrings();
        if (l == null) {
            throw new HoneycombTestException(
					"Nothing returned by [" + cmd +"]");
        }
        int count = l.size();
        if (count < 1) {
            throw new HoneycombTestException(
					"Nothing returned by [" + cmd +"]");
        }
        // trim off " *filename"
        String s = (String)l.get(count-1);
        count = s.indexOf(" ");
        return s.substring(0, count);
    }

    public void catnull(String fname) throws HoneycombTestException {
        String cmd = CAT + " " + fname + " > /dev/null";
        ExitStatus es = null;
        try {
            es = exec(cmd);
        } catch (HoneycombTestException e) {
            File f = new File(fname);
            if (!f.exists())
                throw new HoneycombTestException("timecat(" + fname + 
						"): file does not exist", e);
            throw new HoneycombTestException("Failed to timecat [" + 
				cmd +"]", e);
        } catch (Throwable t) {
            throw new HoneycombTestException("Failed to timecat [" + cmd +"]", t);
        }
        if (es == null) {
            throw new HoneycombTestException("Failed to timecat [" + 
				cmd +"]: no error code");
        }
        if (es.getReturnCode() != 0) {
            List l = es.getErrStrings();
            throw new HoneycombTestException(
                                "Failed to timecat [" + cmd +"]: " + l);
        }
    }

    public boolean isMounted(String dirname) throws HoneycombTestException {
        uname();
        String cmd = DF;
        ExitStatus es = exec(cmd);
        if (es.getReturnCode() != 0) {
            List l = es.getErrStrings();
            throw new HoneycombTestException(
                                "[" + cmd +"]: " + l);
        }
        List l = es.getOutStrings();
        for (int i=0; i<l.size(); i++) {
            String s = (String) l.get(i);
            String[] ss = s.split(" ");
            String mntdir = null;
            if (df_mount_first)
                mntdir = ss[0];
            else
                mntdir = ss[ss.length-1];
            if (mntdir.equals(dirname))
                return true;
        }
        return false;
    }

    public List ls(String fname) throws HoneycombTestException {
        if (fname == null) {
            throw new HoneycombTestException("ls: fname is null");
        }
        String cmd = LS + " " + fname;
        ExitStatus es = null;
        try {
            es = exec(cmd);
        } catch (HoneycombTestException e) {
            File f = new File(fname);
            if (!f.exists())
                throw new HoneycombTestException("ls(" + fname + 
						"): file does not exist", e);
            throw new HoneycombTestException("[" + cmd +"]", e);
        } catch (Throwable t) {
            throw new HoneycombTestException("[" + cmd +"]", t);
        }
        if (es == null) {
            throw new HoneycombTestException("[" + cmd +"]: no error code");
        }
        if (es.getReturnCode() != 0) {
            List l = es.getErrStrings();
            throw new HoneycombTestException(
                                "[" + cmd +"]: " + l);
        }
        return es.getOutStrings();
    }

    // Enable/Disable debugging for this test case
    public void setDebug(boolean b) {
        debuggingEnabled = b;
    }

    // Query for Enable/Disable debugging status for this test case
    public boolean getDebug() {
        return debuggingEnabled;
    }

    // main is for test
    public static void main(String[] arg) {
        RunCommand rc = new RunCommand();
        try {
            System.out.println(rc.ls(arg[0]));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}

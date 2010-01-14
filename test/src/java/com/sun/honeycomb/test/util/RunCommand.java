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

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Hashtable;

//
// FIXME: Should shelling out be depricated? 
// Removing all unused shell commands.
// note: check java "file" commands...?
// joe russack 2/15/05
// 
// Note: shelling-out needed for various harness functions
// like nfs testing
//
// FIXME: [dmehra 03/09/05]
// multiple command wrappers (eg scpCmd) call exec()
// and proceed to check return code and throw exceptions
// in a copy-and-paste fashion. Need a smarter exec?

/**
 *  Class for running shell commands, with many convenience methods.
 */

public class RunCommand {

    static Hashtable commandTable;


    public static final String LINUX_NFS_MOUNT   = "mount -t nfs -o rsize=32768,intr,retrans=10,timeo=600";
    public static final String SOLARIS_NFS_MOUNT = "mount -o timeo=600";
    public static final String IGNORE_COMMAND_VALIDATION = "com.sun.honeycomb.test.util.RunCommand.noValidateCmd";
    private boolean debuggingEnabled             = false;

    public static String uname = null;  // OS name
    
    /* These command lines and file paths are OS-dependent
     */
    public static String mount_cmd = null;
    public static boolean df_mount_first = true;
    public static String tail_cmd = null; 
    public static String syslog_file = null;

    Runtime rt;

    public RunCommand() {
        //
        // Singleton initialization - set up the command set 
        // and test them all for validity on first invocation.
        //
        if (null == commandTable) {
            // 'built-in' cmds
            commandTable = new Hashtable();
            // uname needs to be in path!
            // so that the os-dependent
            // paths can be chosen..
            commandTable.put("UNAME","uname");
            commandTable.put("MV","mv");
            commandTable.put("CP","cp -f"); 
            commandTable.put("CPR","cp -rf");
            commandTable.put("RM","rm -rf");
            commandTable.put("MKDIR","mkdir -p");
            // this syslog priority seems to work on both Linux and Solaris
            commandTable.put("LOGGER","logger -p daemon.notice");
            //
            // Apparently not used.
            //
            //            commandTable.put("HOST","host");
            commandTable.put("CMP","cmp");
            commandTable.put("GZIP","gzip");
            commandTable.put("SHA1SUM","sha1sum -b");
            commandTable.put("UMOUNT","umount");
            commandTable.put("CAT","cat");
            commandTable.put("LS","ls");
            commandTable.put("DF","df");
            commandTable.put("PING","ping -c 1 -t 10");
            commandTable.put("LINES", "wc -l");
            commandTable.put("CHMOD", "chmod");
            commandTable.put("UPTIME", "uptime");

            // for passwordless ssh/scp with our standard keys
            // sshkeyloc gets set by runtest script: java -Dsshkeyloc=...
            //
            String sshKeyPath = System.getProperty("sshkeyloc");
            if (null != sshKeyPath) {
                File vFile = new File(sshKeyPath);
                if (vFile.exists() == false) {
                    Log.ERROR("FATAL: test.util.RunCommand: ssh key location has no file: " + sshKeyPath);
                    System.exit(1);
                }
                // Use quiet mode to avoid errors about host keys changing, etc.
                // This is important because we parse the output and any extra
                // output breaks this parsing.
                commandTable.put("SSH","ssh -q -o StrictHostKeyChecking=no -i " + sshKeyPath);
                commandTable.put("SCP","scp -o StrictHostKeyChecking=no -i " + sshKeyPath);
            } else {
                commandTable.put("SSH","ssh");
                commandTable.put("SCP","scp");
            }

            //
            //  reboot is only used on the cluster so far: currently linux
            //
            commandTable.put("REBOOT","reboot -n");       // no sync 1st
            commandTable.put("REBOOT_FAST","reboot -f");  // no nothing 1st

            //
            // FIXME - Joe Russack 2/15/05
            // This seems hackey to me, at best. 
            //
            commandTable.put("LINUX_NFS_MOUNT", "mount -t nfs -o rsize=32768,intr,retrans=10,timeo=600");
            commandTable.put("SOLARIS_NFS_MOUNT", "mount -o timeo=600");

            if(!validateCommandTable()) {
                System.out.println ("test.util.RunCommand: cannot validate command table; fatal; exiting.");
                System.exit(1);
            }
        }

        rt = Runtime.getRuntime();
    }

    private boolean validateCommandTable() {
        for(Iterator i = commandTable.values().iterator();i.hasNext();) {
            String value = (String)i.next();
            String command = (value.split(" "))[0];
            if(!hasCommand(command)) {
                System.out.println("test.util.RunCommand: cannot locate command: " + command );
                if (System.getProperty(IGNORE_COMMAND_VALIDATION) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Execute a command, capturing the return value, stdout, & stdin
     * 
     *  Shell redirection is supported: include " > file.out" in cmd string
     */
    public ExitStatus exec(String s) throws HoneycombTestException {
        return exec (s, new ArrayList());
    }

    /** Execute given command as arg to "sh -c" to enable redirection
     */
    public ExitStatus exec(String s, List returnList) 
        throws HoneycombTestException {

        ExitStatus es = new ExitStatus();
        OutputParser stdout = null;
        OutputParser stderr = null;
        Process process = null;
        
        /* This is support for (optional) output redirection via shell.
         * String "/bin/sh -c "+cmd will not work, but token array does.
         * Execing the shell appears to carry no performance penalty.
         */ 
        String[] cmdTokens = {"/bin/sh", "-c", s};

        try {
            if (debuggingEnabled) {
                System.out.println("RunCommand: " + s);
            }
            //  start the process, using shell cmd tokens
            process = rt.exec(cmdTokens);
        } catch (Exception e) {
            throw new HoneycombTestException("Failed to execute: " + s, e);
        }

        es.setCmdExecuted(s);
        
        //  create reader threads for stderr and stdout
        //  Note: the process can block otherwise, if this 
        //  output fills the internally-allocated buffer.
        //
        stdout = new OutputParser(process.getInputStream(), "stdout");
        stderr = new OutputParser(process.getErrorStream(), "stderr");
        stdout.start();
        stderr.start();

        try {
            process.waitFor();
            List l = stdout.getList();
            returnList.addAll(l);
            returnList = stdout.getList();
            if (l.size() > 0) 
                es.setOutStrings(l);
            l = stderr.getList();
            if (l.size() > 0)
                es.setErrStrings(l);
        } catch(InterruptedException ie) {
            throw new HoneycombTestException("Failed to wait/get result: " + s,
                                             ie);
        }

        es.setReturnCode(process.exitValue());

        if (debuggingEnabled) {
            System.out.println("RunCommand: Exit status: " + es.toString());
        }
        return es;
    }

    /** Given command to run, exec it and return stdout/stderr to caller;
     * throw our exception if something went wrong
     */
    public String execWithOutput(String cmd) throws HoneycombTestException {
        ExitStatus es = exec(cmd);
        if (es == null) {
            throw new HoneycombTestException("[" + cmd +"]: no exit status");
        }
        if (es.getReturnCode() != 0) {
            List l = es.getErrStrings();
            throw new HoneycombTestException("cmd failed: [" + 
                                         cmd +"] exit code=" + 
                                         es.getReturnCode() +", stderr=" + l);
        }
        return es.getOutputString(false); // normal-formatted stderr or stdout
    }

    /**
     *  Parse the output of a process execution in exec().
     */
    protected class OutputParser extends Thread {

        private InputStream is;
        private InputStreamReader isr;
        private BufferedReader br;
        private List l = new ArrayList();
        private String tag;

        protected OutputParser(InputStream is, String tag) 
                                                throws HoneycombTestException {
            this.tag = tag;
            this.is = is;
            try {
                isr = new InputStreamReader(is);
                br = new BufferedReader(isr);
            } catch (Exception e) {
                throw new HoneycombTestException(e);
            }
        }

        /**
         *  Get list of output lines. Needs to join the Thread
         *  because Process.waitFor() can return before the
         *  thread has started.
         */
        public List getList() throws HoneycombTestException {
            try {
                this.join();
            } catch (Exception e) {
                throw new HoneycombTestException("unexpected join exception", e);
            }
            return l;
        }

        /**
         *  Get output lines. 
         */
        public void run() {
            try {
                String line = null;
                while ( (line = br.readLine()) != null) {
                    l.add(line);
                }
            } catch (Exception e) {
                // throw new HoneycombTestException(e);
            } finally {
                //
                //  close everything just in case.. cost in time is 0-1 ms.
                //
                try {
                    br.close();
                } catch (Exception e) {
                    System.err.println("br: " + e);
                }
                try {
                    isr.close();
                } catch (Exception e) {
                    System.err.println("isr: " + e);
                }
                try {
                    is.close();
                } catch (Exception e) {
                    System.err.println("is: " + e);
                }
            }
        }
    }

    /**
    *   Call cp.
    */
    //
    // FIXME: This can be replaced with two file streams,
    // pros: architecture independent
    // cons: doesn't use any OS level cp optimization, runs in java 2/15/05 
    // Joe Russack
    // con: java version wouldn't show us perf/errors of shell cmd
    //      which is desired for nfs testing
    //
    public void cp(String orig, String copy) throws HoneycombTestException {
        ExitStatus es = null;
        String baseCommand = (String)commandTable.get("CP");
        String cmd = baseCommand + " " + orig + " " + copy;        
        es = exec(cmd);
        if (es.getReturnCode() != 0) {
            throw new HoneycombTestException("Failed to cp: " + es.toString());
        }
    }

    /** Recursive copy.
     */
    public void cpr(String orig, String copy) throws HoneycombTestException {
        ExitStatus es = null;
        String baseCommand = (String)commandTable.get("CPR");
        String cmd = baseCommand + " " + orig + " " + copy;        
        es = exec(cmd);
        if (es.getReturnCode() != 0) {
            throw new HoneycombTestException("Failed to cp: " + es.toString());
        }
    }

    /**
     *  Call rm -rf.
     */
    //
    // FIXME: Replace with file.delete() Joe Russack 2/15/05
    // 
    public void rm(String path) throws HoneycombTestException {
        ExitStatus es = null;
        String cmd = (String)commandTable.get("RM") + " " + path;
        es = exec(cmd);
        if (es.getReturnCode() != 0) {
            throw new HoneycombTestException("Failed to run: " + cmd);
        }
    }

    /**
     *  Call mkdir -p.
     */
    public void mkdir(String path) throws HoneycombTestException {
        ExitStatus es = null;
        String cmd = (String)commandTable.get("MKDIR") + " " + path;
        es = exec(cmd);
        if (es.getReturnCode() != 0) {
            throw new HoneycombTestException("Failed to run: " + cmd);
        }
    }

    /**
     *  get OS name of local or specified remote machine.
     */
    public String uname() throws HoneycombTestException {
        return uname(null); // local host
    }

    public String uname(String host) throws HoneycombTestException {
        synchronized (this) {
            if (uname != null) return uname; // singleton-like

            if (host == null) {  // get OS type from java properties
                java.util.Properties p = System.getProperties();
                java.util.Enumeration keys = p.keys();
                while( keys.hasMoreElements() ) {
                    String element = (String)keys.nextElement();
                    if(element.equals("os.name")) {
                        uname=(String)System.getProperty(element.toString());
                    }
                }
            } 
            else {  // ssh to the host and run uname there
                uname = sshCmd(host, "uname").trim();
            }

            if (uname.equals("Linux")) {
                mount_cmd = LINUX_NFS_MOUNT;
                df_mount_first = false;
                tail_cmd = "tail -n";
                syslog_file = "/var/log/messages";
            } 
            else if (uname.equals("SunOS")) {
                mount_cmd = SOLARIS_NFS_MOUNT;
                df_mount_first = true;
                tail_cmd = "tail ";
                syslog_file = "/var/adm/messages";
            } 
            else {
                throw new HoneycombTestException("Invalid OS: " + uname);
            }
        }
        return uname;
    }
    
    /**
     *  Call host-specific mount for nfs.
     */
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

    /**
     *  Call unmount.
     */
    public void unmount(String mountpoint) throws HoneycombTestException {
        ExitStatus es = null;
        String cmd = (String)commandTable.get("UMOUNT") + " " + mountpoint;
        es = exec(cmd);
        if (es.getReturnCode() != 0) {
            throw new HoneycombTestException("Failed to unmount " + mountpoint,
                                             es);
        }
    }

    /**
     *  Log a message to syslog.
     */
    public void syslog(String host, String s) 
        throws HoneycombTestException {
        String cmd = (String)commandTable.get("LOGGER") + " ";
            if (host == null) {
                cmd = cmd + "\"" + s + "\""; // double quotes to escape
                exec(cmd); // local syslog
            } else { 
                // double set of double quotes because of ssh
                cmd = cmd + "\"" + "\\\"" + s + "\\\"" + "\""; 
                sshCmd(host, cmd); // remote syslog
            }
    }

    public void syslog(String s) throws HoneycombTestException {
        syslog(null, s);
    }

    //
    // FIXME: Joe Russack 2/15/05
    // Not invoked anyplace in the code, and host
    // isn't in the path. Either update or remove.
    //


    /**
     *  Call host.
     */
    /*
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
            throw new HoneycombTestException("Nothing (null) returned by [" + 
                                                                    cmd +"]");
        }
        int count = l.size();
        if (count < 1) {
            throw new HoneycombTestException("Nothing returned by [" + cmd +"]");
        }
        return((String)l.get(count-1));
    }
    */
    /**
     *  Call cmp.
     */
    //
    // Replace with two iostreams. Architecure independent.
    // FIXME: Joe russack, 2/15/05
    // 
    //
    public String cmp(String f1, String f2) throws HoneycombTestException {
        String cmd = (String)commandTable.get("CMP")  + " " + f1 + " " + f2;
        ExitStatus es = exec(cmd);
        if (es.getReturnCode() == 0)
            return null;
        return es.toString();
    }

    /**
     *  Call gzip.
     */
    public String gzip(String fname) throws HoneycombTestException {
        String cmd = (String)commandTable.get("GZIP")  + " " + fname;
        ExitStatus es = exec(cmd);
        if (es.getReturnCode() == 0)
            return null;
        return es.toString();
    }

    /**
     *  Call sha1sum.
     */
    // 
    // FIXME: Joe russack 2/15/05 - call internal library or not?
    // perhaps we want to call if the binary is unavailable,
    // in any case. Open issue.
    //
    public String sha1sum(String fname) throws HoneycombTestException {
        String cmd = (String)commandTable.get("SHA1SUM")  + " " + fname;
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
    
    /**
     *  Read file to /dev/null for i/o timing.
     */
    // 'cat <file> > /dev/null' doesn't work
    public void catnull(String fname) throws HoneycombTestException {
        String cmd = "dd bs=4096 if=" + fname + " of=/dev/null";
        ExitStatus es = null;
        try {
            es = exec(cmd);
        } catch (HoneycombTestException e) {
            File f = new File(fname);
            if (!f.exists())
                throw new HoneycombTestException("catnull(" + fname + 
                                        "): file does not exist", e);
            throw new HoneycombTestException("Failed to catnull [" + 
                                        cmd +"]", e);
        } catch (Throwable t) {
            throw new HoneycombTestException("Failed to catnull [" + cmd +"]", t);
        }
        if (es == null) {
            throw new HoneycombTestException("Failed to catnull [" + 
                                        cmd +"]: no error code");
        }
        if (es.getReturnCode() != 0) {
            List l = es.getErrStrings();
            throw new HoneycombTestException(
                                "Failed to catnull [" + cmd +"]: " + l);
        }
    }

    /**
     *  See if directory is mounted.
     */
    public boolean isMounted(String dirname) throws HoneycombTestException {
        uname();
        String cmd = (String)commandTable.get("DF");
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
    
    /**
     *  Call ls.
     */
    public List ls(String fname) throws HoneycombTestException {
        if (fname == null) {
            throw new HoneycombTestException("ls: fname is null");
        }
        String cmd = (String)commandTable.get("LS")  + " " + fname;
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

    /**
     *  Returns true if pingable or throws HoneycombTestException.
     */
    public boolean ping(String ip) throws HoneycombTestException {
        if (ip == null) {
            throw new HoneycombTestException("ping: ip is null");
        }
        String cmd = (String)commandTable.get("PING")  + " " + ip;
        ExitStatus es = null;
        es = exec(cmd);
        if (es == null) {
            throw new HoneycombTestException("[" + cmd +"]: no error code");
        }
        if (es.getReturnCode() != 0) {
            List l = es.getErrStrings();
            throw new HoneycombTestException(
                                "[" + cmd +"] returned: " + es.getReturnCode() +
                                " error: " + l);
        }
        return true;
    }

    /**
     *  Call reboot.
     */
    public void reboot(boolean fast) throws HoneycombTestException {
        String cmd;
        if (fast)
            cmd = (String)commandTable.get("REBOOT_FAST") ;
        else
            cmd = (String)commandTable.get("REBOOT") ;
        ExitStatus es = exec(cmd);
        // shouldn't matter from here on :-)
        if (es == null) {
            throw new HoneycombTestException("[" + cmd +"]: no error code");
        }
        if (es.getReturnCode() != 0) {
            List l = es.getErrStrings();
            throw new HoneycombTestException("[" + cmd +"]: " + l);
        }
    }
    
    /**
     *  ssh a command to a remote host (or login@host).
     */
    public String sshCmd(String login_host, String remoteCmd) 
        throws HoneycombTestException {
        String cmd = (String)commandTable.get("SSH")  + " " + login_host + " " + remoteCmd;
        return execWithOutput(cmd);
    }

    public ExitStatus sshCmdStructuredOutput(String login_host, String remoteCmd)
        throws HoneycombTestException {
        String cmd = (String)commandTable.get("SSH")  + " " + login_host + " " + remoteCmd;
        return (exec(cmd));
    }

    /** scp a regular file (not a directory)
     */
    public String scpCmd(String src, String dst) 
        throws HoneycombTestException {
        boolean isDir = false;
        return scpCmd(src, dst, isDir);
    }

    /**
     *  scp from source to destination (path or host:path or login@host:path)
     *  if isDir == true, then perform "scp -r", otherwise plain scp a file
     */
    public String scpCmd(String src, String dst, boolean isDir) 
        throws HoneycombTestException {
        String addArg = isDir ? " -r" : ""; // recursive or not
        String cmd = (String)commandTable.get("SCP") + 
            addArg + " " + src + " " + dst;
        return execWithOutput(cmd);
    }

    /** Tail specified number of lines from given file
     * args contains both tail params, and filename
     * Redirect output to another file (also specified in args)
     *
     * tailCmd("-n 100 foo.txt > bar.txt"); // local src, local dst
     * tailCmd("root@otherbox", "-n 100 foo.txt > bar.txt"); // remote src, local dst
     */
    public void tailCmd(String args) 
        throws HoneycombTestException {
        tailCmd(null, args); // local
    }

    public void tailCmd(String host, String args) 
        throws HoneycombTestException {
        String cmd = tail_cmd + args; // no space!
        if (host == null) {
            exec(cmd);
        } else { 
            sshCmd(host, cmd);
        }
    }

    /** Count lines in given file, return the number as a string
     */
    public int countLinesCmd(String filename) 
        throws HoneycombTestException {
        return countLinesCmd(null, filename); // local file
    }

    // if host is specified, run remote version via ssh
    public int countLinesCmd(String host, String filename) 
        throws HoneycombTestException {
        String out;
        String cmd = (String)commandTable.get("LINES") + " " + filename;
        if (host == null) { // local 
            out = execWithOutput(cmd).trim();
        } else { // remote, ssh
            out = sshCmd(host, cmd).trim();
        }

        String num;
        try {
            num = out.substring(0, out.indexOf(' '));
        } catch (Exception e) {
            throw new HoneycombTestException("[" + cmd +
                                             "]: wrong output format: " + out);
        }
        Integer i = new Integer(num);
        return i.intValue();
    }

    /** Chmod. */
    public String chmodCmd(String filename, String perms) 
        throws HoneycombTestException {
        return execWithOutput((String)commandTable.get("CHMOD") 
                              + " " + perms + " " + filename);
    }

    /** Get uptime. */
    public String uptime() throws HoneycombTestException {
        return execWithOutput((String)commandTable.get("UPTIME"));
    }

    /** Enable/Disable debugging for this test case */
    public void setDebug(boolean b) {
        debuggingEnabled = b;
    }

    /** Query for Enable/Disable debugging status for this test case */
    public boolean getDebug() {
        return debuggingEnabled;
    }

    public static boolean hasCommand(String commandName) {
        boolean found=false;
        RunCommand rc = new RunCommand();

        List stringList = new ArrayList();
        try {
            rc.exec ("env",stringList);

        } catch (Exception e) {
            System.out.println ("Failed to run 'env' command - can't get path information! Fatal..");
            e.printStackTrace();
            System.exit(1);
        }
                    
                
        String curEnv=null;
        Iterator i=stringList.iterator();        
        while (i.hasNext()) {
            curEnv=(String)i.next();
            String splitString[]=curEnv.split("=");
            if( splitString[0].equals("PATH")) {
                String pathElement[]=splitString[1].split(":");
                for(int j=0;j<pathElement.length;j++) {
                    boolean exists = (new File(pathElement[j]+"/"+commandName)).exists();
                    if (exists) {

                        found=true;
                    }
                }
            }
        }

        
        return found; // do this really
    }

    //
    // local testing
    //
    public static void main(String[] arg) {
        System.out.println("-------------------------------------");
        System.out.print("Starting runCommand with args: ");
        for(int i=0;i<arg.length;i++) {
            System.out.print (arg[i]+" ");
        }
        System.out.println();
        
        if (hasCommand("ls")) {
            System.out.println("ls found.");
        }
        if (hasCommand("asd")) {
            System.out.println("asd found - this shouldn't be.");
        } else {
            System.out.println("asd not found.");
        }
        
        RunCommand rc = new RunCommand();

        try {
            System.out.println("Got uname: " + rc.uname());
            System.out.println(rc.ls("."));
            System.out.println ("Run successful.");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

}

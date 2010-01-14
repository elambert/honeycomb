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



package com.sun.honeycomb.util.sysdep;

/**
 * This interface encapsulates system-dependent command names and arguments
 * used for starting/stopping system services, searching for processes, etc.
 *
 * @author Shamim Mohamed
 * @version $Revision: 1.3 $ $Date: 2005-01-31 17:05:51 -0800 (Mon, 31 Jan 2005) $
 */

public abstract class Commands {
    public abstract String svcStart(String svc);
    public abstract String svcStop(String svc);
    public abstract String svcRestart(String svc);

    public abstract String exportfs();
    public abstract String nfsd();
    public abstract String mountd();
    public abstract String mount();
    public abstract String mountNFS();
    public abstract String umount();
    public abstract String ifconfig();
    public abstract String reboot();
    public abstract String poweroff();
    public abstract String hostname();

    public abstract String fgrep();
    public abstract String pgrep();
    public abstract String pkill();
    public abstract String fkill();

    public abstract String nfsSvcName();
    public abstract String nfsProcessName();
    public abstract String syslogSvcName();
    public abstract String syslogDir();

    // Factory
    private static Commands commands = null;
    public static synchronized Commands getCommands() {
        if (commands == null) {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.equals("sunos") || os.equals("solaris"))
                commands = new com.sun.honeycomb.util.sysdep.solaris.Commands();
            else if (os.equalsIgnoreCase("linux"))
                commands = new com.sun.honeycomb.util.sysdep.linux.Commands();
            else
                commands = null;
        }

        return commands;
    }
}


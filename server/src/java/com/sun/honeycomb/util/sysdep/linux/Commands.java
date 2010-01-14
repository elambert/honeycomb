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



package com.sun.honeycomb.util.sysdep.linux;

/**
 * Thisclass implements system-dependent command names and arguments
 * used for starting/stopping system services, searching for processes, etc.
 *
 * @author Shamim Mohamed
 * @version $Revision: 1.3 $ $Date: 2005-01-31 17:05:51 -0800 (Mon, 31 Jan 2005) $
 */
public class Commands extends com.sun.honeycomb.util.sysdep.Commands {

    public String exportfs() {
        return "/usr/sbin/exportfs -a ";
    }
    public String nfsd() {
        return "/usr/sbin/rpc.nfsd -p 1111 16 ";
    }
    public String mountd() {
        return "/usr/sbin/rpc.mountd -p 1110 ";
    }
    public String mount() {
        return "/bin/mount ";
    }
    public String mountNFS() {
        return "/bin/mount -t nfs ";
    }
    public String umount() {
        return "/bin/umount ";
    }
    public String ifconfig() {
        return "/sbin/ifconfig ";
    }
    public String reboot() {
        return "/sbin/reboot ";
    }
    public String poweroff() {
        return "/sbin/poweroff ";
    }
    public String hostname() {
        return "/bin/hostname ";
    }
    public String fgrep() {
        return "/usr/bin/pgrep -f ";
    }
    public String pgrep() {
        return "/usr/bin/pgrep ";
    }
    public String pkill() {
        return "/usr/bin/pkill ";
    }
    public String fkill() {
        return "/usr/bin/pkill -f ";
    }

    public String svcStart(String svc) {
        return "/etc/init.d/" + svc + " start";
    }
    public String svcStop(String svc) {
        return "/etc/init.d/" + svc + " stop";
    }
    public String svcRestart(String svc) {
        return "/etc/init.d/" + svc + " restart";
    }

    public String syslogSvcName() {
        return "sysklogd";
    }
    public String syslogDir() {
        return "/var/log";
    }

    public String nfsSvcName() {
        return "nfs";
    }
    public String nfsProcessName() {
        return "nfsd";
    }

}

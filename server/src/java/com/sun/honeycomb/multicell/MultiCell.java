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



package com.sun.honeycomb.multicell;

import java.util.List;
import java.io.IOException;

import com.sun.honeycomb.util.Switch;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.util.Exec;

public class MultiCell extends MultiCellBase implements MultiCellIntf
{
    static private final String ROUTE = "/usr/sbin/route";

    //
    // Returned by 'route' when the entry is already configured (add)
    // or when the entry does not exist (delete). This is not documented
    // in the man page and not necesseraly intuive-- ESRCH. We should
    // not hit that case, but just to harden the code...
    //
    // This code should be written platform independent.
    //
    static private final int EXIT_EXIST = 17; /* EXIST */ 
    static private final int EXIT_ABSENT = 3; /* ESRCH */

    static private MultiCell multicell = null;

    static public MultiCell getInstance() {
        if (multicell == null) {
            throw new MultiCellError("multicell has been started yet");
        }
        return multicell;
    }


    public MultiCell() throws MultiCellException {
	super();
        multicell = this;
        powerOfTwo = new PowerOfTwo();
    }

    public void shutdown () {
        keepRunning = false;
        
        if (thr != null) {
            thr.interrupt();
        }
        boolean stopped = false;

        while (!stopped) {
            try {
                if (thr != null) {
                    thr.join();
                }
                stopped = true;
            } catch (InterruptedException ignored) {
                
            }
        }
        logger.logDefault("Multicell now STOPPED");
    }

    public ManagedService.ProxyObject getProxy () {
        return new Proxy(this);
    }

    public void syncRun() {
        logger.logDefault("Multicell now READY");
    }

    protected void configureInterface(String adminVIP, boolean add) 
        throws MultiCellException  {

        int ignoredExitCode = 0;
        int exitCode = -1;
        StringBuffer cmd = new StringBuffer();
        cmd.append(ROUTE);
        if (add) {
            ignoredExitCode =  EXIT_EXIST;
            cmd.append(" add ");
        } else {
            ignoredExitCode =  EXIT_ABSENT;
            cmd.append(" delete ");
        }
        cmd.append(adminVIP);
        cmd.append(" ");
        cmd.append(Switch.SWITCH_FAILOVER_IP);
        cmd.append(" -host"); 

        try {
            exitCode = Exec.exec(cmd.toString(), logger.getLogger());
        } catch (IOException ioe) {
            throw new MultiCellException("Failed to add the route for the " +
              " cell adminVIP " + adminVIP, ioe);
        }
        if (exitCode == ignoredExitCode) {
            logger.logWarning(cmd.toString() + " exited with " + 
              exitCode + ", ignore...");
        } else if (exitCode != 0) {
            throw new MultiCellException("Failed to run the cmd : " +
              cmd.toString() + ", exitCode = " + exitCode);
        }
    }

    protected void executeInRunLoop() {
            ServiceManager.publish(this);
    }
}




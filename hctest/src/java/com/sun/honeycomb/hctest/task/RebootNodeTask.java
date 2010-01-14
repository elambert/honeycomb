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



package com.sun.honeycomb.hctest.task;

import com.sun.honeycomb.hctest.CmdResult;

//import com.sun.honeycomb.hctest.rmi.spsrv.common.SPSrvConstants;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

/**
 *  Task for handling periodic reboots. Call interrupt() freely
 *  on this one.
 */
public class RebootNodeTask extends SequenceTask {

    int node;
    long interval;

    public RebootNodeTask(int node, long interval) {
        super();
        this.node = node;
        this.interval = interval;
    }

    public void run() {
        while (!stop) {
            try {
                //Log.INFO("rebooting..");
                CmdResult cr = testBed.rebootNode(node);
                if (cr.pass)
                    Log.INFO("rebooted node " + (cr.node+1));
                else
                    Log.INFO("reboot failed");
            } catch (Exception e) {
                Log.INFO("reboot: " + e);
                thrown.add(e);
            }
            sleep(interval);
        }
    }
}

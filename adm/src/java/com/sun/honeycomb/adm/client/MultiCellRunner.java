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


package com.sun.honeycomb.adm.client;

import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.common.CliConstants;

public class MultiCellRunner {

    private MultiCellOp [] ops    = null;
    private Thread      [] ths    = null;

    public MultiCellRunner(MultiCellOp [] ops) {
        this.ops   = ops;
        ths = new Thread[ops.length];
        for (int i = 0; i < ops.length; i++) {
            ths[i] = new Thread(ops[i]);
        }        
    }
    

    public void start() {
        for (int i = 0; i < ops.length; i++) {
            ths[i].start();
        }
    }

    public void setCookie(Object obj) {
        for (int i = 0; i < ops.length; i++) {
            ops[i].setCookie(obj);
        }
    }

    public int waitForPartialResult() {

        //System.err.println("main thread enters waitForPartialResult()");
        for (int i = 0; i < ops.length; i++) {
            int res = ops[i].waitForPartialResult();
            if (res != CliConstants.MGMT_OK) {
                return res;
            }
        }
        //System.err.println("main thread exits waitForPartialResult()");
        return CliConstants.MGMT_OK;
    }

    public int waitForResult() throws MgmtException {
        
        int res = CliConstants.MGMT_OK;

        for (int i = 0; i <  ops.length; i++) {
            boolean done = false;
            while (!done) {
                try {
                    ths[i].join();
                    done = true;
                } catch (InterruptedException ignored) {
                    done = true;
                }
            }
            if (ops[i].getMgmtException() != null) {
                throw ops[i].getMgmtException();
            }
            if ((ops[i].getResult() != CliConstants.MGMT_OK) &&
              (res ==  CliConstants.MGMT_OK)) {
                res = ops[i].getResult(); 
            }
        }
        //System.err.println("main thread exits waitForResult(), res = " +
        //    res);
        return res;
    }
}

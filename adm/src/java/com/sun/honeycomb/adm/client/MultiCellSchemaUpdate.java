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

import java.math.BigInteger;

import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.admin.mgmt.client.HCCell;
import com.sun.honeycomb.common.CliConstants;

public class MultiCellSchemaUpdate extends MultiCellOpBase
{
    private byte mask;

    public MultiCellSchemaUpdate(HCCell cell) {
        super(cell);
        mask = 0;
    }

    public void run() {
        do {
            SchemaUpdateCookie schemaCookie = waitForCookie();
            mask = schemaCookie.mask;
            commitSchema(schemaCookie);
        } while ((mask & CliConstants.MDCONFIG_LAST_MESSAGE) != 
            CliConstants.MDCONFIG_LAST_MESSAGE);
        //System.err.println("thread " + cell.getCellId() + " exits run");
    }

    public int waitForPartialResult() {
        while (result == -1) {
            //System.err.println("main thread waits on thread " +
            //cell.getCellId());
            try {
                synchronized(this) {
                    wait();
                }
            } catch(InterruptedException ignored) {
            }
        }
        return result;
    }

    public void setCookie(Object cookie) {
        super.setCookie(cookie);
        //System.err.println("main thread notifies (setCookie) thread " +
        //  cell.getCellId());
        synchronized (this) {
            result = -1;
            notify();
        }
    }

    private SchemaUpdateCookie waitForCookie() {
        do {
            if (cookie == null) {
                //System.err.println("thread " + cell.getCellId() + 
                //  " waiting for cookie");
                synchronized(this) {
                    try {
                        wait();
                    } catch (InterruptedException ignore) {
                    }
                }
            }
        } while (cookie == null);
        //System.err.println("thread " + cell.getCellId() + 
        //  " received cookie");
        return (SchemaUpdateCookie) cookie;
    }


    private void commitSchema(SchemaUpdateCookie schemaCookie) {

        //System.err.println("thread " + cell.getCellId() + 
        //  " enters commitSchema");

        StatusCallback callback = new StatusCallback();
        BigInteger wsRet =  BigInteger.valueOf(CliConstants.MGMT_OK);
        try {
            wsRet = cell.updateSchema(callback, 
                                      schemaCookie.schemaPiece, 
                                      schemaCookie.timestamp,
                                      schemaCookie.mask,
                                      (byte) 1);
            result = wsRet.intValue();
        } catch (MgmtException e) {
            mgmtException=e;
        }


        //System.err.println("thread " + cell.getCellId() + 
        //  " exits commitSchema, notify main thread");
        synchronized (this) {
            cookie = null;
            notify();
        }
    }

    static public class SchemaUpdateCookie {

        long    timestamp;
        String  schemaPiece;
        int     nbPiece;
        byte    mask;

        public SchemaUpdateCookie(String schemaPiece,
          int nbPiece, long timestamp, byte mask) {
            this.schemaPiece = schemaPiece;
            this.nbPiece = nbPiece;
            this.timestamp = timestamp;
            this.mask = mask;
        }
    }
}

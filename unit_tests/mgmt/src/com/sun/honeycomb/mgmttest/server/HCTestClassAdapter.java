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



package com.sun.honeycomb.mgmttest.server;

import java.math.BigInteger;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.common.MgmtException;

public class HCTestClassAdapter
    implements HCTestClassAdapterInterface {

    private int id;

    public HCTestClassAdapter() {
        id = -1;
    }

    public void loadHCTestClass(BigInteger _id)
        throws InstantiationException {
        id = _id.intValue();
    }

    public BigInteger getId() {
        return(BigInteger.valueOf(id));
    }

    public BigInteger testSimpleMethod() {
        System.out.println("testSimpleMethod executed on obj "+id);
        return(BigInteger.valueOf(id));
    }

    public BigInteger testMethod(EventSender eventSender,
                                 String msg) {
        try {
            eventSender.sendAsynchronousEvent("async");
            String reply = eventSender.sendSynchronousEvent("sync");
            System.out.println("Synchronous reply: ["+reply+"]");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
            }
            reply = eventSender.sendSynchronousEvent("sync2");
            System.out.println("Synchronous reply: ["+reply+"]");
        } catch (MgmtException e) {
            e.printStackTrace();
        }
        System.out.println("testMethod executed on obj "+id+". Msg is ["+msg+"]");
        return(BigInteger.valueOf(id));
    }

    public BigInteger testXMLMethod(org.w3c.dom.Document msg) {
        System.out.println("testXMLMethod executed on obj "+id+". Root is ["+msg.getDocumentElement().getTagName()+"]");
        return(BigInteger.valueOf(id));
    }

}

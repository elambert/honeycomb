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



package com.sun.honeycomb.cm.ipc;

import java.io.ByteArrayInputStream;
import com.sun.honeycomb.cm.jvm_agent.Service;
import com.sun.honeycomb.cm.jvm_agent.Test;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import com.sun.honeycomb.cm.jvm_agent.CMSAP;
import java.io.IOException;
import com.sun.honeycomb.cm.jvm_agent.CMAException;
import com.sun.honeycomb.cm.ManagedService;

public class MailboxReader
    extends ByteArrayInputStream {

    private static final int MAILBOX_SIZE = 1024;

    public MailboxReader(String tag)
        throws IOException, CMAException {
        super(new byte[MAILBOX_SIZE]);
        CMSAP sap = new CMSAP(Test.SERVER_ADDR,
                              Test.START_PORT,
                              0, 0, 10000);
        Service.ProxyHeader hdr = new Service.ProxyHeader(Test.class, sap);
        
        ByteArrayOutputStream output = null;
        ObjectOutputStream objectOutput = null;
        byte[] mailbox = null;

        try {
            output = new ByteArrayOutputStream();
            objectOutput = new ObjectOutputStream(output);
            objectOutput.writeObject(hdr);
            objectOutput.writeObject(new ManagedService.ProxyObject());
            mailbox = output.toByteArray();
        } finally {
            try {
                objectOutput.close();
                output.close();
            } catch (IOException ignored) {}
        }

        if (mailbox.length > MAILBOX_SIZE) {
            throw new IOException("Mailbox is too small ["+
                                  MAILBOX_SIZE+"<"+
                                  mailbox.length+"]");
        }

        System.arraycopy(mailbox, 0,
                         buf, 0,
                         mailbox.length);
    }

    public void reset() {
        pos = 0;
    }

    public boolean isUpToDate() {
        return(true);
    }
}

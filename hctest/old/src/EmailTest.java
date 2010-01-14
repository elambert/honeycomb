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



package com.sun.honeycomb;

import com.sun.honeycomb.common.mail.*;
import java.net.UnknownHostException;
import java.io.IOException;

public class EmailTest {
    public static void main (String[] args) {
        EmailAddress to = null;
        EmailAddress from = null;
        SMTPClient   smtp = null;

        try {
            to   = new EmailAddress ("rjw");
            from = new EmailAddress ("noreply@129.153.4.233");

            MailMessage msg = new MailMessage (from, to);
            msg.setSubject ("Honeycomb System Alert");
            msg.setBody ("This is the message body\r\nLine 2\r\nLine 3\r\nrjw");
            msg.setPriority (MailMessage.PRIORITY_URGENT);

            System.out.println ("sending...");
            smtp = new SMTPClient ("129.153.4.233", 25);
            smtp.send (msg);
            System.out.println ("ok");
       }
        catch (MailException me) {
            System.out.println ("failed: " + me.getMessage());
            me.printStackTrace();
        }
        finally {
            if (smtp != null) {
                smtp.close();
            }
        }
    } 
}

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


package com.sun.honeycomb.common.mail;

import java.util.Enumeration;
import java.util.ArrayList;

/**
 * Email message data structure
 */
public class MailMessage {
    public static final int RECIP_TO  = 1;
    public static final int RECIP_CC  = 2;
    public static final int RECIP_BCC = 3;

    public static final int PRIORITY_URGENT = 1;
    public static final int PRIORITY_HIGH   = 2;
    public static final int PRIORITY_NORMAL = 3;
    public static final int PRIORITY_LOW    = 4;
    public static final int PRIORITY_BULK   = 5;

    private EmailAddress _from;
    private ArrayList    _to;
    private ArrayList    _cc;
    private ArrayList    _bcc;
    private String       _subject;
    private String       _body;
    private int          _priority;

    public MailMessage (EmailAddress from, EmailAddress to) {
        assert from != null;
        _to   = new ArrayList (1);
        _cc   = new ArrayList (1);
        _bcc  = new ArrayList (1);
        _from = from; 
        _subject = "";
        _body  = "";
        _priority = PRIORITY_NORMAL;
        addRecipient (RECIP_TO, to);
    }

    public void addRecipient (int type, EmailAddress recipient) {
        assert recipient != null;
        assert type > 0;
        assert type < 4;

        switch (type) {
            case RECIP_TO:
                _to.add (recipient);
                break;
            case RECIP_CC:
                _cc.add (recipient);
                break;
            case RECIP_BCC:
                _bcc.add (recipient);
                break;
            default:
                assert false;
        }
    }

    public EmailAddress getSender () {
        return _from;
    }
    
    public String getBody() {
        return _body;
    }

    public EmailAddress[] getTo() {
        return (EmailAddress[]) _to.toArray (new EmailAddress[_to.size()]);
    }

    public EmailAddress[] getCc() {
        return (EmailAddress[]) _cc.toArray (new EmailAddress[_cc.size()]);
    }
    public EmailAddress[] getBcc() {
        return (EmailAddress[]) _bcc.toArray (new EmailAddress[_bcc.size()]);
    }
    
    public String getSubject() {
        return _subject;
    }

    public int getPriority() {
        return _priority;
    }

    public void setBody (String body) {
        _body = body;
    }

    public void setSubject (String subject) {
        assert subject != null;
        _subject = subject;
    }

    public void setPriority (int p) {
        assert p <= PRIORITY_BULK;
        assert p >= PRIORITY_URGENT;
        _priority = p;
    }
}

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

import java.util.Properties;
import java.util.logging.Logger;

import java.net.Socket;
import java.net.UnknownHostException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Simple mail exchanger client that speaks SMTP. Allows the sending of
 * multiple messages, but invoking {@link send}. YOu should be nice and 
 * call {@link close} when you have finished with the object (this is also
 * invoked via finalize. This class is not thread safe: do not acess it
 * from multiple threads.
 */
public class SMTPClient {
    private static transient final Logger logger = 
        Logger.getLogger(SMTPClient.class.getName());

    private final static String EOL = "\r\n";

    private final static String SMTP_REPLY_CONNECT = "220";
    private final static String SMTP_REPLY_OK      = "250";
    private final static String SMTP_REPLY_DATA    = "354";
    private final static String SMTP_REPLY_CLOSE   = "221";

    private final static String SMTP_CMD_HELO     = "HELO ";
    private final static String SMTP_CMD_MAILFROM = "MAIL From: ";
    private final static String SMTP_CMD_RCPT     = "RCPT To: ";
    private final static String SMTP_CMD_DATA     = "DATA";
    private final static String SMTP_CMD_END_DATA = ".";
    private final static String SMTP_CMD_CLOSE    = "QUIT";

    private Properties     _cellConfig;
    private Socket         _socket;
    private BufferedReader _in;
    private BufferedWriter _out;

    public SMTPClient (String hostname, int port) throws MailException {
        assert hostname != null;
        assert port > 0;
        assert port < 65536;

        try {
            InetSocketAddress sockAddr = new InetSocketAddress(hostname,port);
            //
            // 10 second timeout
            //
            _socket = new Socket();
            _socket.connect(sockAddr,10000);
            _out = new BufferedWriter (
                    new OutputStreamWriter (
                        _socket.getOutputStream()));
            _in  = new BufferedReader (
                    new InputStreamReader (
                        _socket.getInputStream()));
        }
        catch (IOException ioe) {
            throw new MailException (
                "unable to connect to: " + hostname + ":" + port + ": "
                + ioe.getMessage(), ioe);
        }

        assert _socket != null;
        assert _in     != null;
        assert _out    != null;
    }

    /**
     * Sends the actual mail.
     */
    public void send (MailMessage msg) throws MailException {
        try {
            checkResponse (SMTP_REPLY_CONNECT);

            _out.write (SMTP_CMD_HELO);
            _out.write (msg.getSender().getHost().toString());
            _out.write (EOL);
            checkResponse(SMTP_REPLY_OK);

            _out.write (SMTP_CMD_MAILFROM);
            _out.write (msg.getSender().toString());
            _out.write (EOL);
            checkResponse (SMTP_REPLY_OK);

            EmailAddress[] toAddrs  = msg.getTo();
            EmailAddress[] ccAddrs  = msg.getCc();
            EmailAddress[] bccAddrs = msg.getBcc();

            writeRecipients (SMTP_CMD_RCPT, toAddrs, true);
            writeRecipients (SMTP_CMD_RCPT, ccAddrs, true);
            writeRecipients (SMTP_CMD_RCPT, bccAddrs, true);

            _out.write (SMTP_CMD_DATA);
            _out.write (EOL);
            checkResponse (SMTP_REPLY_DATA);

            _out.write ("From: ");
            _out.write (msg.getSender().toString());
            _out.write (EOL);

            writeRecipients ("To: ", toAddrs, false);
            writeRecipients ("Cc: ", ccAddrs, false);
            _out.write ("X-Priority: ");
            _out.write (String.valueOf (msg.getPriority()));
            _out.write (EOL);
            _out.write ("Subject: ");
            _out.write (msg.getSubject());
            _out.write (EOL);
            _out.write (EOL);
            _out.write (msg.getBody());
            _out.write (EOL);
            _out.write (SMTP_CMD_END_DATA);
            _out.write (EOL);
            checkResponse (SMTP_REPLY_OK);

            _out.write (SMTP_CMD_CLOSE);
            _out.write (EOL);
            checkResponse (SMTP_REPLY_CLOSE);
        }
        catch (IOException ioe) {
            throw new MailException (
                "unable to send mail: " + ioe.getMessage(), ioe);
        }
    }

    /** 
     * Cleans up our buffers and connections 
     */
    public void close () {
        if (_out != null) {
            try { 
                _out.close(); 
            } 
            catch (Exception e) { ; }
        }
        if (_in != null) {
            try { 
                _in.close(); 
            } catch (Exception e) { ; }
        }
        if (_socket != null) {
            try { 
                _socket.close(); 
            } 
            catch (Exception e) { ; }
        }
    }

    public void finalize() {
        close();
    }

    private void checkResponse (String code) 
        throws IOException, MailException {
        _out.flush();

        String response = _in.readLine(); // block until we get a response
        logger.fine ("smtp response: " + response);
        if (! response.startsWith (code)) {
            logger.warning ("bad response: " + response);
            throw new MailException ("bad response from SMTP server: "
                + response);
        }
    }

    private void writeRecipients (String header, 
                                  EmailAddress[] addrs, 
                                  boolean checkResponse) 
        throws IOException, MailException {
        for (int i = 0; i < addrs.length; i++) {
            _out.write (header);
            _out.write (addrs[i].toString());
            _out.write (EOL);
            if (checkResponse) {
                checkResponse (SMTP_REPLY_OK);
            }
        }
    }
}

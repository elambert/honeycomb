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



package com.sun.honeycomb.util;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

/**
 * Telnet class,a standard TELNET client,implementing 
 * the "user" host side of a TELNET connection.
 */
class TelnetClient implements Runnable {
    /* *** Telnet Options *** */
    private class OPT
    {
        private static final int BINARY =0;
        /** Telnet OPTION We ask for the remote to do so. */
        private static final int ECHO =1;
        private static final int RCP =2;
        /** Suppress Go Ahead.  We ask for the remote to do so. */
        private static final int SGA =3;
        private static final int NAMS =4,
                                 STATUS =5,
                                 TM =6,
                                 RCTE =7,
                                 NAOL =8,
                                 NAOP =9,
                                 NAOCRD =10,
                                 NAOHTS =11,
                                 NAOHTD =12,
                                 NAOFFD =13,
                                 NAOVTS =14,
                                 NAOVTD =15,
                                 NAOLFD =16,
                                 XASCII =17,
                                 LOGOUT =18,
                                 BM =19,
                                 DET =20,
                                 SUPDUP =21,
                                 SUPDUPOUTPUT =22,
                                 SNDLOC =23;
        /** Negotiate term type. */
        private static final int TTYPE =24;
        private static final int EOR =25,
                                 TUID =26,
                                 OUTMRK =27,
                                 TTYLOC =28,
                                 REGIME3270 =29,
                                 X3PAD =30;
        /** Negotiate about window size. */
        private static final int NAWS =31;
        private static final int TSPEED =32,
                                 LFLOW =33,
                                 LINEMODE =34,
                                 XDISPLOC =35,
                                 OLDENV =36,
                                 AUTHENTICATION =37,
                                 ENCRYPT =38,
                                 NEWENV = 39;
                                 //EXOPL =255;

        private static final int FIRST =BINARY,
                                 LAST =NEWENV;
    }

    /* *** Telnet Commands *** 
     A command consists of a 0xFF byte
     followed by one of the following codes. */

    private class CMD
    {
        private static final int EOF =236;
        private static final int SUSP =237;
        private static final int ABORT =238;
        private static final int EOR =239;

        /** End of subnegotiation */
        private static final int SE =240;

        /** No Operation */
        private static final int NOP =241;

        /** Data Mark (Data portion of Synch). */
        private static final int DM =242;
        /* private static final int SYNC =242; */
        /** Break */
        private static final int BREAK =243;
        /** Interrupt Process */
        private static final int IP =244;
        /** Abort Output */
        private static final int AO =245;
        /** Are You There */
        private static final int AYT =246;
        /** Erase Character */
        private static final int EC =247;
        /** Erase Line */ 
        private static final int EL =248;
        /** Go Ahead */
        private static final int GA =249;
        /** Perform subnegotiation of the option. */
        private static final int SB =250;

        /** Indicate desire to begin performing
         an option or confirm that it is
         now being performed. */
        private static final int WILL =251;

        /** Refuse to perform or continue
         performing an option. */
        private static final int WONT =252;

        /** Ask the remote system to start
         performing an option,or confirm
         we now expect it to perform
         the option. */
        private static final int DO =253;

        /** Ask the remote system to stop
         performing an option,or confirm
         we no longer expect it to perform
         the option. */
        private static final int DONT =254;

        /** Interpret next by as a command;
         IAC IAC is a 255 data byte. */
        private static final int IAC =255;

        private static final int FIRST =EOF,
                                 LAST  =IAC;
    }

    /** Current local and remote option status. */
    private boolean[] locopts = new boolean[OPT.LAST -OPT.FIRST +1],
                      remopts = new boolean[OPT.LAST -OPT.FIRST +1];

    /** The local options that we have implemented. */
    private static final boolean[] implocopts =  
    {   /*  1 */ true,false,false,true,false,
        /*  2 */ false,false,false,false,false,
        /*  3 */ false,false,true,false,false,
        /*  4 */ false,false,false,false,false,
        /*  5 */ false,false,false,
        /*  6 */ false,true,false,
        /*  7 */ false,false,false,
        /*  8 */ false,false,true,true,false,
        /*  9 */ false,false,false,false,
        /* 10 */ false,false,false };

    /** The remote options that we have implemented. */
    private static final boolean[] impremopts =  
    {   /*  1 */ true,true,false,true,false,
        /*  2 */ false,false,false,false,false,
        /*  3 */ false,false,false,false,false,
        /*  4 */ false,false,false,false,false,
        /*  5 */ false,false,false,
        /*  6 */ false,false,false,
        /*  7 */ false,false,false,
        /*  8 */ false,false,false,false,false,
        /*  9 */ false,false,false,false,
        /* 10 */ false,false,false };

    /** Socket */
    private Socket telnetSocket;
    /** Socket Output Stream */
    private OutputStream out;
    /** Socket Input Stream */
    private InputStream in;

    /** An Telnet Session? */
    private boolean isTelnet = false;

    private int telnetPort = 23;
    private StringBuffer resultBuffer = new StringBuffer();
    private boolean end = true;
    private static final int MAX_RETRIES=5;
    private static final Logger log = 
        Logger.getLogger(TelnetClient.class.getName());

    /**
     * Method to connect to a telnet server and start the telnet message
     * queue.
     *
     * @param server the network name of the server to connect to
     * @return boolean true if the connection succeeded
     * @throws UnknownHostException
     */
    public synchronized boolean connect(String server)
        throws UnknownHostException {

        boolean done = false;
        if (telnetSocket == null) {
            for (int i = 0; i < locopts.length; i++) {
                locopts[i] = remopts[i] = false;
            }

            // Try a few times if the connection fails
            int numRetries = 0;
            while ((!done) && (numRetries <= MAX_RETRIES)) {
                try {
                    InetSocketAddress addr =
                        new InetSocketAddress(InetAddress.getByName(server), telnetPort);

                    // Connect to the host
                    telnetSocket = new Socket();
                    telnetSocket.connect(addr);
                
                    // Open input and output streams
                    in = new BufferedInputStream(telnetSocket.getInputStream());
                    out = telnetSocket.getOutputStream();

                    // Start the thread to manage the telnet command queue
                    Thread thread = new Thread(this);
                    thread.start();
                    done = true;
                } catch (UnknownHostException e) {
                    throw e;
                } catch (Exception e) {
                    disconnect();
                    numRetries++;
                    continue;
                }
            }
        }
        return done;
    }

    /**
     * Method to disconnect from the telnet server.
     */
    public synchronized void disconnect() {
        end = false;
        while (in != null || out != null) {
            try {
                InputStream i;
                OutputStream o;
                if (in != null) {
                    i = in;
                    in = null;
                    i.close();
                }
                if (out != null) {
                    o = out;
                    out = null;
                    o.close();
                }
            } catch (IOException e) {
                log.severe(e.toString());
            }
        }

        if (telnetSocket != null) {
            try {
                telnetSocket.close();
            } catch (IOException e) {
                log.severe(e.toString());
            } finally {
                telnetSocket = null;
            }
        }
    }

    /**
     * Method to reset the result buffer which holds the telnet server's
     * response to the commands.
     */
    synchronized public void resetBuffer() {
        resultBuffer = new StringBuffer();
    }

    /**
     * Method to find out if the result buffer ends with the provided
     * sub-string. This is used to find out if the telnet server has sent the
     * expected response.
     *
     * @param suffix the sub-string to check for
     * @return boolean true if the buffer ends with the specified sub-string
     */
    synchronized public boolean endsWith(String suffix) {
        return resultBuffer.toString().endsWith(suffix);
    }

    /**
     * Method to get the string form of the result buffer.
     *
     * @return String the string form of the result buffer
     */
    synchronized public String getResultBuffer() {
        return resultBuffer.toString();
    }

    /**
     * Method to send a string to the connected telnet server.
     *
     * @param b the array of bytes to send
     * @throws IOException
     */
    public void send(byte[] b) throws IOException {
        if (out != null) {
            out.write(b);
            out.flush();
        }
    }

    /**
     * Method to send a character to the connected telnet server.
     *
     * @param ch the character to send
     * @throws IOException
     */
    private void send(char ch) throws IOException {
        if (out != null) {
            out.write((byte) ch);
            out.flush();
        }
    }

    /**
     * Method to send a character to the connected telnet server.
     *
     * @param str the string to send
     * @throws IOException
     */
    private void send(String str) throws IOException {
        if (out != null) {
            out.write(str.getBytes());
            out.flush();
        }
    }

    private void flush() throws IOException {
        if (out != null) {
            out.flush();
        }
    }

    /** Process Telnet Data */
    synchronized private void processData(char c) throws IOException {
        resultBuffer.append(c);
    }

    private boolean isCmd(int cmd) {
        return (cmd <= CMD.LAST && cmd >= CMD.FIRST);
    }

    private boolean isOpt(int opt) {
        return (opt <= OPT.LAST && opt >= OPT.FIRST);
    }

    private boolean isImpLocOpt(int opt) {
        return (isOpt(opt) && implocopts[opt - OPT.FIRST]);
    }

    private boolean isImpRemOpt(int opt) {
        return (isOpt(opt) && impremopts[opt - OPT.FIRST]);
    }

    /**
     * Method to manage the telnet command queue. This will read the telnet
     * input stream and call the appropriate handlers.
     */
    public void run() {
        while (end) {
            try {
                int b = in.read();
                if (b < 0) {
                    break;
                }
                if (b == CMD.IAC) {
                    processCmd();
                } else {
                    processData((char) b);
                }
            } catch (Exception e) {
                end = false;
            }
        }
        disconnect();
    }

    /**
     * Method to process the telnet commands.
     */
    private void processCmd()
        throws IOException {

        int cmd = in.read();
        switch (cmd) {
        case CMD.IAC :
            processData((char) cmd);
            break;
        case CMD.DONT :
        case CMD.DO :
        case CMD.WONT :
        case CMD.WILL :
            int opt = in.read();
            processOpt(cmd, opt);
            break;
        default :
            return;
        }

        if (!isTelnet) {
            isTelnet = true;
            sendOpt(CMD.WILL, OPT.TSPEED, false);
            sendOpt(CMD.WILL, OPT.TTYPE, false);
            sendOpt(CMD.WILL, OPT.NAWS, false);
            sendOpt(CMD.WILL, OPT.NAOHTD, false);
            sendOpt(CMD.DO, OPT.ECHO, false);
            sendOpt(CMD.DO, OPT.SGA, false);
        }
    }

    /**
     * Method to take action on a process comment received from the server.
     */
    synchronized private void processOpt(int cmd, int opt)
        throws IOException {
        // If this is a local option we don't understand 
        // or have not implemented, refuse any 'DO' request.
        if (cmd == CMD.DO && !isImpLocOpt(opt)) {
            sendOpt(CMD.WONT, opt, true);
        }
        // If this is a server option we don't understand 
        // or have not implemented,refuse any 'DO' request.
        else if (cmd == CMD.WILL && !isImpRemOpt(opt)) {
            sendOpt(CMD.DONT, opt, true);
        }
        // If this is a DONT request,(possibly) 
        // send a reply and turn off the option.
        else if (cmd == CMD.DONT) {
            sendOpt(CMD.WONT, opt, false);
        }
        // If this is a WONT request, (possibly) 
        // send a reply and turn off the option.
        else if (cmd == CMD.WONT) {
            sendOpt(CMD.DONT, opt, false);
        } else if (cmd == CMD.WILL) {
            sendOpt(CMD.DO, opt, false);
        }
    }

    /**
     * Method to send an option command to the remote server.
     */
    synchronized private void sendOpt(int cmd, int opt, boolean force)
        throws IOException {
        // Send this command if we are being forced to,
        // OR if it is a change of state of the server options,
        // OR if it is a change in the state of our local options.
        if (force || (isImpRemOpt(opt)
                && (cmd == CMD.DONT && remopts[opt - OPT.FIRST])
                || (cmd == CMD.DO && !remopts[opt - OPT.FIRST]))
            || (isImpLocOpt(opt)
                && (cmd == CMD.WONT && locopts[opt - OPT.FIRST])
                || (cmd == CMD.WILL && !locopts[opt - OPT.FIRST]))) {
            byte[] reply = new byte[3];
            reply[0] = (byte) CMD.IAC;
            reply[1] = (byte) cmd;
            reply[2] = (byte) opt;
            send(reply);
        }
        
        // Change our options state. We really shouldn't be turning options on 
        // until we get a reply, but this isn't a problem yet for the options
        // that are currently implemented.
        if (cmd == CMD.WILL) {
            if (isImpLocOpt(opt)) {
                locopts[opt - OPT.FIRST] = true;
            }
        } else if (cmd == CMD.WONT) {
            if (isImpLocOpt(opt)) {
                locopts[opt - OPT.FIRST] = false;
            }
        } else if (cmd == CMD.DO) {
            if (isImpRemOpt(opt)) {
                remopts[opt - OPT.FIRST] = true;
            }
        } else if (cmd == CMD.DONT) {
            if (isImpRemOpt(opt)) {
                remopts[opt - OPT.FIRST] = false;
            }
        }
    }
}

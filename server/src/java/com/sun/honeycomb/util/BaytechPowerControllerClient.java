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

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.logging.Logger;
import java.util.StringTokenizer;
import java.util.NoSuchElementException;

/**
 * Concrete implementation of the Baytech remote power controller. This class
 * implements the generic PowerControllerClient interface. The Baytech power
 * controller has 8 ports which can be in the [on|off] state. Access to the
 * power controller is through a telnet connection and a command menu. As this
 * class attempts to programatically control the ports, it needs to parse the
 * command menus and send the correct options. It uses a TelnetClient to
 * communicate with the remote power controller.
 */
public class BaytechPowerControllerClient implements PowerControllerClient {
    private String powerControllerName;
    private static final int MINPORTNUMBER = 1;
    private static final int MAXPORTNUMBER = 8;
    private static final String LOGIN_STRING = "Enter Selection>";
    private static final String PROMPT_STRING = "RPC-3>";
    private static final String YES_NO_STRING = "(Y/N)>";
    private static final String ERROR_STRING = "Input Error";
    private static final char CARRIAGE_RETURN = '\r';
    private static final char NULL_CHAR = '\000';
    private static final Logger log = 
        Logger.getLogger(BaytechPowerControllerClient.class.getName());

    public BaytechPowerControllerClient(String powerControllerName) {
        this.powerControllerName = powerControllerName;
    }

    /**
     * Method to send a command to the remote power controller and finds out
     * if the result was expected.
     *
     * @param t the telnet client instance to use for communication
     * @param cmd the command string to send
     * @param expectedResult the result to expect
     * @return boolean true if the command was sent and response was expected
     */
    private boolean sendCommand(TelnetClient t,
                                String cmd,
                                String expectedResult) {
        // Reset the result buffer
        t.resetBuffer();

        // Send the command
        boolean retval = false;
        try {
            byte[] cmdArray = new byte[cmd.length() + 2];
            System.arraycopy(cmd.getBytes(), 0, cmdArray, 0, cmd.length());
            cmdArray[cmd.length()] = CARRIAGE_RETURN;
            cmdArray[cmd.length()+1] = NULL_CHAR;
            t.send(cmdArray);
        } catch (IOException e) {
            retval = false;
        }

        if (expectedResult != null) {
            retval = checkResult(t, expectedResult);
        } else {
            retval = true;
        }

        return retval;
    }

    /**
     * Method to check if the expected result has come from the remote
     * power controller.
     *
     * @param t the telnet client instance to use for communication
     * @param expectedResult the result to expect
     * @return boolean true if the result matched the expected pattern
     */
    private boolean checkResult(TelnetClient t, String expectedResult) {
        while (!t.endsWith(expectedResult)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                continue;
            }
        }

        // Check to see if there is an error in the buffer. This can
        // happen if there was a malformed expression.
        boolean retval = true;
        if (t.getResultBuffer().indexOf(ERROR_STRING) != -1) {
            retval = false;
        }

        return retval;
    }

    private TelnetClient getConnectedClient() {
        TelnetClient t = new TelnetClient();
        try {
            if (!t.connect(powerControllerName)) {
                return null;
            }
        } catch (UnknownHostException e) {
            return null;
        }

        if (!checkResult(t, LOGIN_STRING)) {
            return null;
        }

        return t;
    }

    private boolean setPortState(String state, int portNumber)
        throws IllegalArgumentException {
        if ((portNumber < MINPORTNUMBER) || (portNumber > MAXPORTNUMBER)) {
            throw new IllegalArgumentException("Port must be between " +
                                               MINPORTNUMBER + " and " +
                                               MAXPORTNUMBER);
        }

        TelnetClient t = getConnectedClient();
        if (t == null) {
            return false;
        }

        if (!sendCommand(t, "1", PROMPT_STRING)) {
            return false;
        }

        if (!sendCommand(t, (state + " " + portNumber), YES_NO_STRING)) {
            return false;
        }

        if (!sendCommand(t, "Y", PROMPT_STRING)) {
            return false;
        }

        if (!sendCommand(t, "logout", null)) {
            return false;
        }

        return true;
    }

    /**
     * Method to get the buffer that shows the state of all the power
     * outlets.
     *
     * @return String the buffer containing the outlet status
     */
    private String getOutletStatusBuffer() {
        TelnetClient t = getConnectedClient();
        if (t == null) {
            throw new RuntimeException("Unable to connect");
        }

        if (!sendCommand(t, "1", PROMPT_STRING)) {
            throw new RuntimeException("Failed to send command");
        }

        //Get the result buffer from the Telnet client
        String buffer = t.getResultBuffer();

        sendCommand(t, "logout", null);

        return buffer;
    }

    /**
     * Method to power on a specified port of the remote power controller.
     *
     * @param portNumber the port number to power on
     * @return boolean true if the port was successfully powered on
     * @throws IllegalArgumentException if the port number is not valid
     */
    public boolean powerOn(int portNumber)
        throws IllegalArgumentException {
        return setPortState("on", portNumber);
    }

    /**
     * Method to power off a specified port of the remote power controller.
     *
     * @param portNumber the port number to power off
     * @return boolean true if the port was successfully powered off
     * @throws IllegalArgumentException if the port number is not valid
     */
    public boolean powerOff(int portNumber)
        throws IllegalArgumentException {
        return setPortState("off", portNumber);
    }

    /**
     * Method to find out if a power port is on
     *
     * @param portNumber the port number to query
     * @return boolean true if the specified port is on
     * @throws IllegalArgumentException if the port number is not valid
     * @throws RuntimeException for all the runtime errors
     */
    public boolean isOn(int portNumber)
        throws IllegalArgumentException {
        if ((portNumber < MINPORTNUMBER) || (portNumber > MAXPORTNUMBER)) {
            throw new IllegalArgumentException("Port must be between " +
                                               MINPORTNUMBER + " and " +
                                               MAXPORTNUMBER);
        }

        /*
         * Get the result buffer from the remote power controller client and
         * check the state of the port in question. The result buffer should
         * have some thing like this which indicates the state of the port.
         *
         * Selection   Outlet    Outlet   Power
         *   Number     Name     Number   Status
         *     1       Outlet 1    1       Off
         *     2       Outlet 2    2       Off
         *     3       Outlet 3    3       Off
         *     4       Outlet 4    4       Off
         *     5       Outlet 5    5       Off
         *     6       Outlet 6    6       Off
         *     7       Outlet 7    7       Off
         *     8       Outlet 8    8       Off
         *
         */
        String buffer = getOutletStatusBuffer();

        // Get the index of "Outlet N" and then use the tokenizer to
        // parse the rest of the fields
        int index = buffer.indexOf("Outlet " + portNumber);
        StringTokenizer st = new StringTokenizer(buffer.substring(index));
        boolean retval = false;

        try {
            st.nextToken();
            st.nextToken();
            st.nextToken();
            String status = st.nextToken();

            if (status.equals("On")) {
                retval = true;
            } else if (status.equals("Off")) {
                retval = false;
            } else {
                log.severe("Parse error expected [On/Off] got [" + status + "]");
            }
        } catch (NoSuchElementException e) {
            throw new RuntimeException("Error in parsing result buffer");
        }

        return retval;
    }

    /**
     * Method to get the number of power ports on the remote power
     * controller.
     *
     * @return int the number of power ports on the controller
     */
    public int getNumPorts() {
        return MAXPORTNUMBER;
    }

    private static void usage() {
        System.out.println("usage: java BaytechPowerControllerClient " +
                           "<rpcName> <port #> <on|off|status>");
        System.exit(1);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            usage();
        }

        PowerControllerClient pc = new BaytechPowerControllerClient(args[0]);

        boolean retval = false;
        if (args[2].equals("on")) {
            retval = pc.powerOn(Integer.parseInt(args[1]));
        } else if (args[2].equals("off")) {
            retval = pc.powerOff(Integer.parseInt(args[1]));
        } else if (args[2].equals("status")) {
            retval = pc.isOn(Integer.parseInt(args[1]));
            if (retval) {
                System.out.println("Port " + args[1] + " is [ON]");
            } else {
                System.out.println("Port " + args[1] + " is [OFF]");
            }
            retval = true;
        } else {
            usage();
        }

        if (!retval) {
            System.out.println("Error executing command [" + args[2] +
                               "] on port [" + args[1] +
                               "] for power controller [" +  args[0] + "]");
        }
    }
}

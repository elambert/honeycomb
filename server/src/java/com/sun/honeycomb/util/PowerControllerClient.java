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

/**
 * The PowerControllerClient interface should be implemented by any concrete
 * class that needs to talk to a specific remote power controller. The
 * implementation details of the underlying protocol and method of
 * communication should be hidden in the implementation class. This interface
 * provides simple method to control the ports of a remote power controller.
 * The PowerControl abstraction ca be defined as follows:
 *
 * Each power controller has a fixed number of ports, say N. The ports can be
 * addressed from 1-N. Each power port can only be in any one of the two states
 * (on|off). A power port's state can be toggled by calling the powerOn() or
 * powerOff() methods. Once a port's state has been set, it will remain that
 * way till another toggle method is called on it. Setting the same state on
 * a port will not result in any action.
 */
public interface PowerControllerClient {
    /**
     * Method to power on a specified port of the remote power controller.
     *
     * @param portNumber the port number to power on
     * @return boolean true if the port was successfully powered on
     * @throws IllegalArgumentException if the port number is not valid
     */
    public abstract boolean powerOn(int portNumber)
        throws IllegalArgumentException;

    /**
     * Method to power off a specified port of the remote power controller.
     *
     * @param portNumber the port number to power off
     * @return boolean true if the port was successfully powered off
     * @throws IllegalArgumentException if the port number is not valid
     */
    public abstract boolean powerOff(int portNumber)
        throws IllegalArgumentException;

    /**
     * Method to find out if a power port is on
     *
     * @param portNumber the port number to query
     * @return boolean true if the specified port is on
     * @throws IllegalArgumentException if the port number is not valid
     */
    public abstract boolean isOn(int portNumber)
        throws IllegalArgumentException;

    /**
     * Method to get the number of power ports on the remote power
     * controller.
     *
     * @return int the number of power ports on the controller
     */
    public abstract int getNumPorts();
}

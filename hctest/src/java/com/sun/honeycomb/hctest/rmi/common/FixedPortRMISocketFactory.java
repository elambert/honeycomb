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


package com.sun.honeycomb.hctest.rmi.common;

import java.rmi.server.RMISocketFactory;
import java.net.Socket;
import java.net.ServerSocket;
import java.io.IOException;

/*
* User: Tim Goffings
* Date: Oct 3, 2002 - 3:51:34 PM
*/

public class FixedPortRMISocketFactory extends RMISocketFactory {

  private int srvPort;

  public FixedPortRMISocketFactory(int port) {
    srvPort = port;
  }

  /**
  * Creates a client socket connected to the specified host and port and writes out debugging info
  * @param host  the host name
  * @param port  the port number
  * @return a socket connected to the specified host and port.
  * @exception IOException if an I/O error occurs during socket creation
  */
  public Socket createSocket(String host, int port) throws IOException {
    if (host == null  ||  host.length() == 0)
        throw new IOException("createSocket: host is null/empty");
    //System.out.println("creating socket to host: " + host + " on port " + port);
    return new Socket(host, port);
  }

  /**
  * Create a server socket on the specified port (port 0 indicates
  * an anonymous port) and writes out some debugging info
  * @param port the port number
  * @return the server socket on the specified port
  * @exception IOException if an I/O error occurs during server socket
  * creation
  */
  public ServerSocket createServerSocket(int port) throws IOException {
   
    //System.out.println("creating ServerSocket on port " + port);
    return new ServerSocket(srvPort);
  }
} 

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



package com.sun.honeycomb.protocol.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Iterator;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Set;

import org.mortbay.http.SocketListener;
import org.mortbay.http.HttpConnection;
import org.mortbay.http.HttpListener;
import org.mortbay.util.InetAddrPort;
import org.mortbay.util.Code;

public class MultiPortSocketListener extends SocketListener
{
    private static final int NB_TOLERATED_UNSUCCESSFULL_RETURNS  = 10;

    private ServerSocketChannel[] listeners;
    private Selector dispatch;
    private int unsucessfullReturns;
    private String ip;

    private static transient final Logger logger = 
        Logger.getLogger(MultiPortSocketListener.class.getName());
    
    public MultiPortSocketListener(String ip) {
        this.ip = ip;
    }

    public void initialize(Set ports) {

        listeners = new ServerSocketChannel[ports.size()];

        Iterator it = ports.iterator();
        int i = 0;
        while (it.hasNext()) {
            InetSocketAddress addr = null;
            int port = ((Integer) it.next()).intValue();
            try {
                ServerSocketChannel listener = ServerSocketChannel.open();
                listener.configureBlocking(false);
                ServerSocket sock = listener.socket();
                sock.setReuseAddress(true);

                if (ip == null) {
                    addr = new InetSocketAddress(port);
                } else {
                    addr = new InetSocketAddress(ip, port);
                }
                sock.bind(addr);
                listeners[i++] = listener;

                logger.info("bind to address " + addr);

            } catch (IOException io) {
                throw (new RuntimeException("cannot create server channel " +
                                            io));
            }
        }
        buildDispatcher();
    }

    public ServerSocket[] getMultiPortSocketServer() {
        ServerSocket[] sockets = new ServerSocket[listeners.length];
        for (int i = 0; i < listeners.length; i++) {
            sockets[i] = listeners[i].socket();
        }
        return sockets;
    }
    
    private void buildDispatcher() {
        if (dispatch != null) {
            try {
                dispatch.close();
            } catch (IOException e) {
                logger.log(Level.WARNING,
                           "Error while closing the MultiPortSocketListener dispatcher ["+
                           e.getMessage()+"]",
                           e);
            }
            dispatch = null;
        }

        try {
            dispatch = Selector.open();

            for (int i=0; i<listeners.length; i++) {
                listeners[i].register(dispatch, SelectionKey.OP_ACCEPT, null);
            }
        } catch (IOException e) {
            throw (new RuntimeException("Cannot create selector ["
                                        +e.getMessage()+"]"));
        }


        unsucessfullReturns = 0;
    }
    
    //
    // Overload from ThreadedServer (jetty)
    //
    protected ServerSocket newServerSocket(InetAddrPort address,
                                           int acceptQueueSize)
        throws java.io.IOException {
        return listeners[0].socket();
    }
    
    //
    // Overload from ThreadedServer (jetty)
    //
    protected Socket acceptSocket(ServerSocket serverSocket, int timeout) {
        
        if (timeout < 0) {
            timeout = 0;
        }

        try {
            if (dispatch.select(timeout) > 0) {
                Iterator it = dispatch.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = (SelectionKey) it.next();
                    it.remove();
                    if (key.isValid() && key.isAcceptable()) {
                        
                        SocketChannel s = null;
                        ServerSocketChannel listener =
                            (ServerSocketChannel) key.channel();
                        try {
                            s = listener.accept();
                        } catch (IOException io) {
                            logger.log(Level.SEVERE,
                                       "accept socket failed",
                                       io);
                            return null;
                        }
                        Socket socket = s.socket();
                        try {                            
                            if (getMaxIdleTimeMs() >= 0)
                                socket.setSoTimeout(getMaxIdleTimeMs());
                            // Hack --  _lingerTimeSecs is defined private in 
                            // ThreadedServer.
                            //socket.setSoLinger(true, 30);
                            socket.setSoLinger(true, 0);
                            
                        } catch (Exception e) {
                            Code.ignore(e);
                        }
                        return socket;
                    }
                }
                unsucessfullReturns = 0;
            } else {
                unsucessfullReturns++;
                if (unsucessfullReturns == NB_TOLERATED_UNSUCCESSFULL_RETURNS) {
                    buildDispatcher();
                    logger.fine("MultiPortSocketListener has to rebuild a dispatcher");
                }
            }
            
        } catch (IOException io) {
            logger.log(Level.SEVERE,
                       "dispatch failed",
                       io);
        }
        return null;
    }

    /* Extend the Jetty class so that the socket underlying the NIO stream
     *  is closed. Otherwise Jetty just closes the input and output streams 
     * and the client has no way of knowing that no one is litening (the 
     * connection appears to hang).
     */
    static class HttpSocketConnection extends HttpConnection{

        public HttpSocketConnection (HttpListener listener,
                                     InetAddress remoteAddr,
                                     InputStream in,
                                     OutputStream out,
                                     Object connection){
            super(listener, remoteAddr, in, out, connection);
        }

        public void close()
            throws IOException{
            super.close();
            Object connection = getConnection();
            if (connection instanceof Socket) {
                Socket s = (Socket)connection;
                s.shutdownOutput();
                s.close();
            }
        }
    }

    /* Make Jetty use our specialized connection class which closes 
     * the socket, not just the streams, when the connection times out.
     */
    protected HttpConnection createConnection(Socket socket)
        throws IOException
    {
        return new HttpSocketConnection(this,
                                        socket.getInetAddress(),
                                        socket.getInputStream(),
                                        socket.getOutputStream(),
                                        socket);
    }
}

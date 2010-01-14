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



package com.sun.honeycomb.alertviewer;

import com.sun.honeycomb.cm.ManagedService;
import java.util.logging.Logger;
import java.net.ServerSocket;
import java.io.IOException;
import java.util.logging.Level;
import java.net.Socket;
import com.sun.honeycomb.alert.AlertApi;
import com.sun.honeycomb.alert.AlertApiImpl;
import java.io.DataOutputStream;
import com.sun.honeycomb.alert.AlertException;

public class Server 
    implements ManagedService {
    
    private static final int ALERT_PORT = 2807;
    
    private static final Logger LOG = Logger.getLogger(Server.class.getName());

    private boolean running;
    private Thread serverThread;

    public Server() {
        running = false;
        serverThread = null;
    }
    
    public void syncRun() {
    }

    public void run() {
        running = false;
        serverThread = Thread.currentThread();

        ServerSocket server = null;

        try {
            server = new ServerSocket(ALERT_PORT);
        } catch (IOException e) {
            LOG.log(Level.SEVERE,
                    "Failed to start the Alert Viewer server ["+
                    e.getMessage()+"]",
                    e);
            return;
        }

        running = true;

        LOG.info("The Alert Viewer server is running");

        while (running) {
            try {
                Socket client = server.accept();
                dealWithClient(client);
            } catch (IOException e) {
                LOG.warning("The Alert Viewer server got an exception ["+
                            e.getMessage()+"]");
            } catch (AlertException ae) {
                LOG.warning("The Alert Viewer server got an exception ["+
                            ae.getMessage()+"]");
            }
        }

        LOG.info("The Alert Viewer server is exiting");
    }

    public void shutdown() {
        running = false;
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    public ManagedService.ProxyObject getProxy() {
        return(new ManagedService.ProxyObject());
    }

    private void dealWithClient(Socket client)
        throws IOException, AlertException {

        LOG.info("A client connected to the Alert Viewer Server");
        DataOutputStream output = null;
        
        try {
            output = new DataOutputStream(client.getOutputStream());

            AlertApi alertApi = AlertApiImpl.getAlertApi();
            AlertApi.AlertViewProperty properties = alertApi.getViewProperty();

            AlertApi.AlertObject property = properties.getFirstAlertProperty();

            while (property != null) {
                try {
                    String name = property.getPropertyName();
                    String value = "-undef-";
                    if (property.getPropertyValue() != null) { 
                        value = property.getPropertyValue().toString();
                    }
                    output.writeUTF(name);
                    output.writeUTF(value);
                    
                    property = properties.getNextAlertProperty();

                } catch (AlertException e) {
                    property = null;
                }
            }
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {}
            }
            client.close();
        }
    }
}

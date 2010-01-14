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



package com.sun.honeycomb.ndmp;

import com.sun.honeycomb.config.ClusterProperties;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Iterator;
import java.util.HashMap;

import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;

/** 
 * Define a base class that the emulator can share.
 */
abstract class BaseNDMPService {

    String activity = DataServer.INACTIVE;
    long objectsProcessed = 0;
    long bytesProcessed = 0;
    int controlPort;
    private int outboundDataPort;
    private int inboundDataPort;
    static Logger logger = Logger.getLogger(NDMPService.class.getName());

    public BaseNDMPService(){
        controlPort = ClusterProperties.getInstance().getPropertyAsInt("honeycomb.ndmp.DataServerPort", 10000);
        inboundDataPort = ClusterProperties.getInstance().getPropertyAsInt("honeycomb.ndmp.InboundDataPort", 10001);
        outboundDataPort = ClusterProperties.getInstance().getPropertyAsInt("honeycomb.ndmp.OutboundDataPort", 10002);
        logger.info("Started NDMP Server on port " + controlPort);
    }

    public void shutdown() {}



    void setStatus(String activity){
        this.activity = activity;
    }



//     public boolean getProceedAfterError () {
//         return false;
//     }
//     public void setProceedAfterError (boolean proceed) {
//     }

//     public int getControlPort () {
//         return controlPort;
//     }
//     public void setControlPort (int port) {
//         controlPort = port;
//         // --> persist this value!!!
//     }


//     public int getInboundDataPort () {
//         return inboundDataPort;
//     }
//     public void setInboundDataPort (int port) {
//         // --> persist this value!!!
//     }

//     public int getOutboundDataPort () {
//         return outboundDataPort;
//     }
//     public void setOutboundDataPort (int port) {
//         // --> persist this value!!!
//     }



    public long getBackupObjectCount(){
        return objectsProcessed;
    }

    void setObjectsProcessed(long objectsProcessed){
        this.objectsProcessed = objectsProcessed;
    }

    public long getBackupByteCount(){
        return bytesProcessed;
    }

    void setBytesProcessed(long bytesProcessed){
        this.bytesProcessed = bytesProcessed;
    }
    
    abstract String getLocalizedString(String id);

    abstract void alert(String message);

    //abstract void backup (Date startTime, Date endTime, SocketChannel sc) throws IOException;

    private boolean configured = false;
    public SocketChannel openSocketChannel(InetSocketAddress target, int outboundDataPort) throws Exception{
        SocketChannel sc = null;
        int retry = 0;
        while (true){
            try{
                sc = SocketChannel.open();
                Socket s = sc.socket();
                // does not work
                s.setSoLinger(false, 0);
                s.bind(new InetSocketAddress(outboundDataPort));
                if (logger.isLoggable(Level.INFO))
                    logger.info("NDMP Connecting " + sc + " " + target);
                sc.connect(target);
                if (logger.isLoggable(Level.INFO))
                    logger.info("NDMP Connected " + sc + " " + target);
                return sc;
            }
            catch (java.net.BindException e){
                // There is a bug in reusing an NIO socket before the linger expires
                if (retry == 10){
                    logger.log(Level.SEVERE, "NDMP Data connection failed after " + retry + " retries", e);                  
                    throw e;
                }
                else{
                    Thread.sleep(10000);
                    retry++;
                }
            }

            // Sometimes the connecion fails
            catch (java.net.ConnectException se){
                if (retry == 10){
                    logger.log(Level.SEVERE, "NDMP Data connection failed after " + retry + " retries", se);                    
                    throw se;
                }
                else{
                    logger.info("NDMP Data connection failed, retrying " + se);                    
                    Thread.sleep(2000);
                    retry++;
                }
            }
        }
    }



    private class MyClassLoader extends java.net.URLClassLoader{
        ClassLoader staticClassLoader = BaseNDMPService.class.getClassLoader();
        MyClassLoader(java.net.URL[] urls){
            super(urls, null);
            //System.out.println("Class Loader Initialized with " + urls[0]);
        }
        public Class loadClass(String name) throws ClassNotFoundException{
            if (name.startsWith("com.sun.honeycomb.ndmp.BaseNDMP") ||
                name.startsWith("com.sun.honeycomb.ndmp.DataServer") ||
                name.startsWith("com.sun.honeycomb.ndmp.XDR") ||
                name.equals("com.sun.honeycomb.ndmp.NDMP") ||
                name.startsWith("com.sun.honeycomb.ndmp.NDMP$")){

                Class c = super.loadClass(name);
                return c;
            }
            else{
                return staticClassLoader.loadClass(name);
            }
        }

        protected Class findClass(String name) throws ClassNotFoundException{
            return super.findClass(name);
        }
    }

    // Reload jar each time
    Runnable makeDataServer(InputStream is, OutputStream os) throws Exception{
        String path = ClusterProperties.getInstance().getProperty("honeycomb.ndmp.ClassPath", null);
        Class c;
        if (path == null){
            c = Class.forName("com.sun.honeycomb.ndmp.DataServer");
        }
        else{
            java.net.URL[] urls = {new java.io.File(path).toURL()};
            ClassLoader cl = new MyClassLoader(urls);
            //cl = new java.net.URLClassLoader(urls);
            c = cl.loadClass("com.sun.honeycomb.ndmp.DataServer");
        }
        Class[] argClassess = {InputStream.class, OutputStream.class, int.class, NDMPService.class};
        Object[] args = {is, os, new Integer(inboundDataPort), BaseNDMPService.this};
        return (Runnable) c.getConstructor(argClassess).newInstance(args);
    }

    void runJob(InputStream is, OutputStream os){
        try{
            if (logger.isLoggable(Level.INFO))
                logger.info("NDMP accepted request, dispatching to DataServer");
            Runnable ds = makeDataServer(is, os);
            new Thread(ds).start();
        }
        catch (Exception e){
            logger.log(Level.SEVERE, "NDMP Data Server Initialization failed", e);
        }
    }


    public void run() {
        try {
            ServerSocket ss = new ServerSocket(controlPort);

            while (true){
                final Socket s = ss.accept();
                s.setSoLinger(false, 0);
                runJob(s.getInputStream(), s.getOutputStream());
            }
        }
        catch (Exception e){
            logger.log(Level.SEVERE, "NDMP Error listening on port " + controlPort,  e);
        }
    }


}

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



/*
 * Updater.java
 *
 * Created on May 13, 2005, 2:36 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package com.sun.honeycomb.alertviewer;

import javax.swing.tree.DefaultMutableTreeNode;
import java.net.Socket;
import java.io.DataInputStream;
import java.io.IOException;
import javax.swing.tree.DefaultTreeModel;
import java.util.NoSuchElementException;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

public class Updater
    extends MainFrame
    implements Runnable {

    private static final int ALERT_PORT = 2807;
    private static final int REFRESH_DELAY = 5000; // ms

    private LinkedList tasks;
    private Thread thread;
    private Timer timer;

    /** Creates a new instance of Updater */
    public Updater() {
        super();
        tasks = new LinkedList();
        timer = null;
        thread = new Thread(this);
        thread.start();
    }
    
    private void updateTree(String name,
                            String value) {

        String[] nodes = name.split("\\.");
        AlertTreeNode root = (AlertTreeNode)treeModel.getRoot();
        root.update(nodes, 1, value);
    }

    public void run() {

        String IP = null;
        AlertTreeNode.model = treeModel;

        while (true) {

            synchronized (tasks) {
                while (tasks.size() == 0) {
                    try {
                        tasks.wait();
                    } catch (InterruptedException e) {
                    }
                }
                IP = (String)tasks.removeFirst();
            }

            updateLabel.setText("from "+IP);
            
            Socket client = null;
            DataInputStream input = null;
            
            try {
                client = new Socket(IP, ALERT_PORT);
                input = new DataInputStream(client.getInputStream());
                
                boolean running = true;
                while (running) {
                    try {
                        String name = input.readUTF();
                        String value = input.readUTF();
                        updateTree(name, value);
                    } catch (IOException e) {
                        running = false;
                    }
                }

                ((AlertTreeNode)treeModel.getRoot()).sweep();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException e) {}
                }
                if (client != null) {
                    try {
                        client.close();
                    } catch (IOException e) {}
                }
            }

            updateLabel.setText("");
        }
    }

    protected void refresh(String IP) {
        synchronized (tasks) {
            tasks.add(IP);
            tasks.notify();
        }
    }

    private class TimerImpl
        extends TimerTask {
      
        private String IP;

        private TimerImpl(String nIP) {
            super();
            IP = nIP;
        }

        public void run() {
            refresh(IP);
        }
    }

    protected void startAutoRefresh(String IP) {
        timer = new Timer();
        timer.schedule(new TimerImpl(IP), 0, REFRESH_DELAY);
    }
    
    protected void stopAutoRefresh() {
        timer.cancel();
        timer = null;
    }
}

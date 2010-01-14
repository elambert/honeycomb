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



package com.sun.ws.management.server.handler;

import com.sun.ws.management.Management;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import com.sun.honeycomb.mgmt.server.HCMGMTEvent;
import org.w3c.dom.Document;
import com.sun.honeycomb.mgmt.common.MgmtException;
import java.math.BigInteger;
import com.sun.honeycomb.mgmt.common.Utils;
import javax.xml.bind.JAXBException;
import javax.xml.soap.SOAPException;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.server.Task;
import java.util.logging.Logger;

public class InteractiveContext
    implements Runnable, EventSender {

    private static final int WAIT_TIMEOUT = (60 * 1000); // 1 min
    private static final Logger LOG = Logger.getLogger(InteractiveContext.class.getName());

    private JAXBContext jaxbContext;
    private com.sun.honeycomb.mgmt.server.ObjectFactory evFactory;

    private int slot;
    private int incarnation;
    private Task target;
    private boolean busy;
    private HCMGMTEvent event;
    private MgmtException serverException;

    private Management curResponse;
    private boolean sendResponse;

    public InteractiveContext(int _slot) 
        throws MgmtException {
        try {
            jaxbContext = JAXBContext.newInstance("com.sun.honeycomb.mgmt.server");
            evFactory = new com.sun.honeycomb.mgmt.server.ObjectFactory();
        } catch (JAXBException e) {
            MgmtException newe = new MgmtException("Failed to build a InteractiveContext ["+
                                                   e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }

        slot = _slot;
        incarnation = 0;
        target = null;
        busy = false;
        event = null;

        curResponse = null;
        sendResponse = false;
        serverException = null;
    }

    public synchronized void init(Management response,
                                  Task _target) {
        curResponse = response;
        sendResponse = false;

        event = new HCMGMTEvent();
        event.setSequence(BigInteger.valueOf(0));
        BigInteger cookie = BigInteger.valueOf(slot);
        cookie = cookie.shiftLeft(32);
        cookie = cookie.add(BigInteger.valueOf(incarnation));
        event.setCookie(cookie);
        incarnation++;
        if (incarnation == Integer.MAX_VALUE)
            incarnation = 0;
        event.setMessage(null);
        target = _target;
        busy = true;
        serverException = null;

        LOG.info("InteractiveContext for slot " + slot + 
          " notified, incarnation = " + incarnation);

        notify();
    }

    public int getIncarnation() {
        return incarnation;
    }

    public void setBusy() {
        busy = true;
    }

    public boolean isBusy() {
        return(busy);
    }

    public boolean matches(BigInteger cookie) {
        return(cookie.equals(event.getCookie()));
    }

    public synchronized void incomingReply(Management response,
                                           HCMGMTEvent ev) {
        while (curResponse != null) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }

        curResponse = response;
        sendResponse = false;
        event.setMessage(ev.getMessage());
        event.setSequence(event.getSequence().add(BigInteger.valueOf(1)));
        notify();
    }

    public synchronized void waitForReadyResponse() throws MgmtException {
        MgmtException curMgmtException = null;
        while (!sendResponse) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
        // If one of the runner thread encounters an exception, the
        // main thread is signaled and throws the exception so it
        // is propagated back to the client.
        curMgmtException = serverException;
        if (curMgmtException != null) {
            busy = false;
            throw curMgmtException;
        }
    }

    public synchronized void sendDocument(String action,
                                          Document xml) 
    throws MgmtException {
        while (curResponse == null) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
        
        // We can send the document back
        try {
            curResponse.setAction(action);
            curResponse.getBody().addDocument(xml);
        } catch (JAXBException e) {
            MgmtException newe = new MgmtException("Failed to prepare the response ["+
                                                   e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (SOAPException e) {
            MgmtException newe = new MgmtException("Failed to prepare the response ["+
                                                   e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
        curResponse = null;
        sendResponse = true;
        notify();
    }
    
    //
    // This method m,ay be called in the context of a different thread
    // so we limit the time it waits for the incoming reply so it does not
    // wait for ever if somehow there is a bug.
    //
    public synchronized void sendAsynchronousEvent(String message) 
        throws MgmtException {

        
        while (curResponse == null) {
            long date = System.currentTimeMillis();
            try {
                wait(WAIT_TIMEOUT);
                if (System.currentTimeMillis() - date >= WAIT_TIMEOUT) {
                    LOG.warning("timeout expired, returns without " +
                      " sending event...");
                    return;
                }
            } catch (InterruptedException e) {
                LOG.warning("thread got interrupted, return...");
                return;
            }
        }

        // We can send the message
        try {
            Document xmlresp = Management.newDocument();
            event.setMessage(message);
            jaxbContext.createMarshaller().marshal(evFactory.createJAXBHCMGMTEvent(event), xmlresp);
            sendDocument(Utils.BASE_CUSTOM_ONGOING_RESPONSE_URI, xmlresp);
        } catch (JAXBException e) {
            MgmtException newe = new MgmtException("Failed to prepare the response ["+
                                                   e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
    }

    public synchronized String sendSynchronousEvent(String message) 
        throws MgmtException {

        sendAsynchronousEvent(message);
        
        // Wait for the reply
        while (curResponse == null) {
            try {
                wait();
            } catch (InterruptedException e) {
                LOG.warning("thread got interrupted, return...");
                return null;
            }
        }
        return(event.getMessage());
    }

    public void run() {
        LOG.info("InteractiveContext for slot " + slot + " has been started");
        boolean running = true;
        while (running) {
            synchronized (this) {
                while (target == null) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
            try {
                target.run();
                busy = false;
            } catch (MgmtException e) {
                synchronized (this) {
                    serverException = e;
                    sendResponse = true;
                    notify();
                }
            } finally {
                target = null;
            }
        }
    }
}

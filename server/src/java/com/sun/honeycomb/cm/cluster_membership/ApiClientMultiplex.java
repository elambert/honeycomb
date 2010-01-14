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



package com.sun.honeycomb.cm.cluster_membership;

import java.util.logging.Logger;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Disconnect;
import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import java.io.IOException;
import java.util.logging.Level;


public class ApiClientMultiplex {
    
    private static final int REPLY_TIMEOUT = (10 * 1000); // 10s
    private static final int REQUEST_TIMEOUT = (60 * 1000); // 1mn FIXME

    private static final int MAX_CONCURRENT_REQUESTS = 5;
    private static final Logger LOG = Logger.getLogger(ApiClientMultiplex.class.getName());

    private SocketChannel apiSocket;
    private boolean aThreadIsInSelect;
    private Selector selector;
    private RequestId[] requests;
    private int nextRequestId;

    public ApiClientMultiplex(SocketChannel _apiSocket) throws IOException {
        apiSocket = _apiSocket;

        aThreadIsInSelect = false;
        selector = Selector.open();
        apiSocket.register(selector, SelectionKey.OP_READ);
        nextRequestId = 0;
        requests = new RequestId[MAX_CONCURRENT_REQUESTS];
        for (int i=0; i<MAX_CONCURRENT_REQUESTS; i++) {
            requests[i] = new RequestId();
        }
    }

    private synchronized RequestId getFreeRequestId() {
        RequestId result = null;
        
        while (result == null) {
            /*
             * Debugging code
             */
            if(LOG.getLevel()==Level.FINE) {
                int counter = 0;
                for (int i=0; i<requests.length; i++) {
                    if (requests[i].isFree) counter++;
                }
                LOG.info("API socket multiplex "+
                         counter+"/"+
                         requests.length+
                         " requests are available"
                         );
            }


            for (int i=0; i<requests.length; i++) {
                if (requests[i].isFree) {
                    result = requests[i];
                    requests[i].isFree = false;
                    requests[i].id = nextRequestId;
                    nextRequestId += 1;
                    if (nextRequestId == Integer.MAX_VALUE-1) {
                        nextRequestId = 0;
                    }                    
                    break;
                }
            }

            if (result == null) {
                LOG.info(MAX_CONCURRENT_REQUESTS+
                         " CMM requests have already been issued.."+
                         "Blocking until one is available ..."
                         );
                try {
                    wait();
                } catch (InterruptedException e) {
                }
                LOG.info("Trying to get a free request slot");
            }
        }
        
        return(result);
    }


    private synchronized void freeRequestId(RequestId request) {
        if (request.isFree) {
            LOG.warning("Freeing a CMM request that was already free ???");
        } else {
            request.isFree = true;
        }
        request.reply = null;
        notify();
    }
    
    private synchronized void setReplyForId(Message reply) {
        
        int id = reply.getRequestId();

        for (int i = 0; i < requests.length; i++) {
            if (!requests[i].isFree && requests[i].id == id) {
                requests[i].reply = reply;
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Got API reply message: " + reply);
                }
                return;
            }
        }
        LOG.warning("Got expired API reply message: " + reply);
    }
            
                    

    public Message performRequest(Message input, Class expectedClass)
        throws CMMException 
    {
        return(performRequest(input, expectedClass, REQUEST_TIMEOUT));
    }

    public Message performRequest(Message input,
                                  Class expectedClass,
                                  long timeout)
        throws CMMException {


        if (apiSocket == null) {
            throw new CMMException("ApiClientMultiplex is closed");
        }

        Message output = null;
        RequestId req = null;

        // The next call can block ...
        req = getFreeRequestId();

        try {

            synchronized (this) {
                input.setRequestId(req.id);
                input.send(apiSocket);
            }

            // If we disconnect, no answer will come back ... return !

            if (input instanceof Disconnect) {
                return(null);
            }

            // Wait for the answer

            long startTime = System.currentTimeMillis();
            /*
             * This blocking while loop will keep iterating in any thread
             * until the message ID that corresponds to 
             * req.getId() comes through. Messages for other tasks
             * are cached in the array.
             */
            while ((req.reply == null) && 
                   ((System.currentTimeMillis()-startTime) < timeout)) {
                boolean iShouldSelect = false;

                synchronized (this) {
                    if (!aThreadIsInSelect) {
                        aThreadIsInSelect = true;
                        iShouldSelect = true;
                    }
                }

                if (iShouldSelect) {
                    // I am working for everyone
                    try {
                        selector.selectedKeys().clear();
                        //
                        // Remaining is timeout; be aware that we're using
                        // our timeout, not the timeout of this
                        // particular operation
                        //
                        if (selector.select(REPLY_TIMEOUT) > 0) {
                            Message reply = Message.receive(apiSocket);
                            // the magick happens here
                            setReplyForId(reply);
                        }
                    } catch (IOException ioe) {
                        // Make sure to reset the thread interrupted state
                        // as NIO does not do it.
                        while (Thread.interrupted());
                        throw new CMMException(ioe);
                        
                    } catch (ClosedSelectorException ce) {
                        // this should never happens.
                        LOG.severe("Error during CMM request");
                        throw new CMMException(ce);
                        
                    } finally {
                        synchronized (this) {
                            aThreadIsInSelect = false;
                        }

                        synchronized (RequestId.class) {
                            RequestId.class.notifyAll();
                        }
                    }
                    
                } else {
                    // Just wait for the working thread to complete
                    synchronized (RequestId.class) {
                        try {
                            RequestId.class.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }

                long usedTime = System.currentTimeMillis()-startTime;
                if (usedTime > 0.1*timeout) {
                    LOG.info("CMM request " + input.getType().getLabel() + 
                      " took a long time ["+usedTime+"]");
                }
            }
            
            if (req.reply == null) {
                // We timed out
                throw new CMMException(ApiImpl.protocol_error);
            }
            
            if ((expectedClass != null) && (!expectedClass.isInstance(req.reply))) {
                LOG.severe("Was expecting a "+
                           expectedClass.getName()+" message from CMM, got a "+
                           req.reply.getClass().getName()
                           );
                throw new CMMException(ApiImpl.protocol_error);
            }

            // Remember output because freeRequestId will reset it
            output = req.reply;
        } finally {
            freeRequestId(req);
        }

        return(output);
    }
    
    public void close() {
        try {
            // try to end cleanly the API session - can fail
            performRequest(new Disconnect(), null);
        } catch (CMMException e) {}

        if (apiSocket != null) {
            try { apiSocket.close(); } catch (IOException ioe) {}
            apiSocket = null;
        }

        closeSelector();
    }

    public void closeSelector() {
        if (selector != null) {
            try { selector.close(); } catch (IOException ioe) {}
            selector = null;
        }
    }
    
    private static class RequestId {
        public int id;
        public boolean isFree;
        public Message reply;

        public RequestId() {
            id = 0;
            isFree = true;
            reply = null;
        }

        public int getId() {
            return(id);
        }
    }
}

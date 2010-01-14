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



package com.sun.honeycomb.emd.remote;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.InputStream;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.emd.EMDCookie;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.server.ProcessingCenter;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.emd.common.EMDCommException;
import com.sun.honeycomb.emd.common.QueryMap;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.coding.ByteBufferCoder;
import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.emd.common.HexObjectIdentifier;
import com.sun.honeycomb.emd.common.MDHit;
import com.sun.honeycomb.emd.cache.ExtendedCacheEntry;

/**
 * The <code>ObjectBroker</code> class is responsible for serializating /
 * deserializing objects on top of a stream.
 *
 * It also implements the communication protocol between MD clients and servers
 */

public class ObjectBroker extends ObjectBrokerBasic {
    private static final Logger LOG = Logger.getLogger("ObjectBroker");


    /**********************************************************************
     *
     * Constructors
     *
     **********************************************************************/

    public ObjectBroker(InputStream inputs,
                        OutputStream outputs) {
        super(inputs,outputs);
    }
    
    public ObjectBroker(Socket socket) 
        throws IOException {
        super(socket);
    }
    
    public ObjectBroker() {
        super();
    }

    public ObjectBroker(InputStream inputs) {
        super(inputs);
    }

    public ObjectBroker(OutputStream outputs) {
        super(outputs);
    }


    public void sendObject(Object obj)
        throws EMDException {

        if (output == null) {
            throw new EMDException("The output stream has not been defined");
        }

        if (obj instanceof Disk) {
            try {
                output.writeByte(OBJECT_DISK);
                Disk.serialize((Disk)obj, output);
            } catch (IOException e) {
                EMDException newe = new EMDCommException("Couldn't send an object ["
                                                         +obj.getClass().getName()+"]");
                newe.initCause(e);
                throw newe;
            }
        } else {
            super.sendObject(obj);
        }

    }
        
    /*
     * Deserialization methods
     */        

    public Object getObject(byte type) 
        throws EMDException {

        if (input == null) {
            throw new EMDException("The input stream has not been defined");
        }

        Object result = null;

        try {
        
            switch (type) {
            case OBJECT_DISK:
                result = Disk.deserialize(input);
                break;
            default:
                result = super.getObject(type);
                break;
            }        
        } catch (IOException e) {
            EMDCommException newe = new EMDCommException
                ("Got an IOException while reading objects ["+e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
        return(result);
    }
    
    /**********************************************************************
     * 
     * Routines dealing with the remote execution of a method
     *
     **********************************************************************/

    /**
     * The <code>executeClient</code> call performs a remote invocation and
     * wait for a result.
     *
     * @param methodType a <code>byte</code> value
     * @param params an <code>Object[]</code> value
     * @param expectResult a <code>boolean</code> value
     * @return an <code>Object</code> value
     * @exception EMDException if an error occurs
     */

    private Object executeClient(byte methodType,
                                 Object[] params)
        throws EMDException {

        if (output == null) {
            throw new EMDException("The output stream has not been defined");
        }
        
        Object result = null;

        try {
            
            output.writeByte(methodType);
            for (int i=0; i<params.length; i++) {
                sendObject(params[i]);
            }
            flush();

            result = getObject();
            
        } catch (IOException e) {
            EMDException newe = new EMDException("Got an IOException ["+e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }

        return(result);
    }

    /**
     * The following call is used to remotly invoke a method but only half
     * of the protocol is played. 
     *
     * The caller then has to get the stream coming from the remote server
     * and process the returned objects itself.
     *
     * @param methodType a <code>byte</code> value
     * @param params an <code>Object[]</code> value
     */

    private void executeClientAsStream(byte methodType,
                                       Object[] params) 
        throws EMDException {

        if (output == null) {
            throw new EMDException("The output stream has not been defined");
        }

        try {
            
            output.writeByte(methodType);
            for (int i=0; i<params.length; i++) {
                sendObject(params[i]);
            }
            flush();

        } catch (IOException e) {
            EMDException newe = new EMDException("Got an IOException ["+e+"]");
            newe.initCause(e);
            throw newe;
        }
    }

    public Object waitForCompletion()
        throws EMDException {
        
        Object result = getObject();
        
        return(result);
    }
    
    /*
     * Client routines
     */        
    
    public void launchSetMetadata(String cacheId,
                                  NewObjectIdentifier oid,
                                  Object argument,
                                  ArrayList disks)
        throws EMDException {
        
        Object[] params = {cacheId, oid, argument, disks};
        
        executeClientAsStream(METHOD_SETMETADATA,
                              params);
    }

    public void removeMetadataClient(String cacheId,
                                     NewObjectIdentifier oid,
                                     ArrayList disks) 
        throws EMDException {
        Object[] params = {cacheId, oid, disks};
        executeClient(METHOD_REMOVEMETADATA,
                      params);
    }

    public void launchAddLegalHold(String cacheId,
                                   NewObjectIdentifier oid,
                                   String legalHold,
                                   ArrayList disks)
        throws EMDException {
        Object[] params = {cacheId, oid, legalHold, disks};

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Add legal hold (" + oid + ", [" + legalHold +
                     "] into cache " + cacheId );
        }

        executeClientAsStream(METHOD_ADDLEGALHOLD, params);
    }

    public void launchRemoveLegalHold(String cacheId,
                                      NewObjectIdentifier oid,
                                      String legalHold,
                                      ArrayList disks) 
        throws EMDException {
        Object[] params = {cacheId, oid, legalHold, disks};
        executeClient(METHOD_REMOVELEGALHOLD, params);
    }

    public void launchQueryClient(String cacheId,
                                  ArrayList disks,
                                  String query,
                                  ArrayList attributes,
                                  EMDCookie cookie,
                                  int maxResult,
                                  int timeout,
                                  boolean forceResults,
                                  Object[] boundParameters) 
        throws EMDException {
        Integer _maxResult = new Integer(maxResult);
        Integer _timeout = new Integer(timeout);
        Boolean _forceResults = new Boolean(forceResults);

        Object[] params = { cacheId, disks, query, attributes, cookie, _maxResult, _timeout, _forceResults, boundParameters };

        executeClientAsStream(METHOD_QUERY, params);
    }

    public void launchSelectUniqueClient(String cacheId,
                                         String query,
                                         String attribute,
                                         String lastAttribute,
                                         int maxResult,
                                         int timeout,
                                         boolean forceResults,
                                         Object[] boundParameters) 
        throws EMDException {
        Integer _maxResult = new Integer(maxResult);
        Integer _timeout = new Integer(timeout);
        Boolean _forceResults = new Boolean(forceResults);

        Object[] params = { cacheId, query, attribute, lastAttribute, _maxResult, _timeout, _forceResults, boundParameters };

        executeClientAsStream(METHOD_SELECTUNIQUE, params);
    }
    
    /**
     * The <code>serverDispatch</code> method has to be called by the
     * serving thread to dispatch the call and perform the operation
     */

    public void serverDispatch() 
        throws EMDException {
        byte methodType;

        if (input == null) {
            throw new EMDException("The input stream has not been defined");
        }

        try {
            methodType = input.readByte();

            switch (methodType) {
            case METHOD_SETMETADATA: {
                if (LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("The thread "+Thread.currentThread().getName()+" is servicing a setMetadata request");
                }
                String cacheId = (String)getObject();
                NewObjectIdentifier oid = (NewObjectIdentifier)getObject();
                Object argument = getObject();
                ArrayList disks = (ArrayList)getObject();
                
                ProcessingCenter.setMetadata(cacheId, oid, argument, disks);
                sendObject(null);
            } break;

            case METHOD_REMOVEMETADATA: {
                if (LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("The thread "+Thread.currentThread().getName()+" is servicing a removeMetadata request");
                }
                String cacheId = (String)getObject();
                NewObjectIdentifier oid = (NewObjectIdentifier)getObject();
                ArrayList disks = (ArrayList)getObject();
                ProcessingCenter.removeMetadata(cacheId, oid, disks);
                sendObject(null);
            } break;

            case METHOD_ADDLEGALHOLD: {
                if (LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("The thread " +
                             Thread.currentThread().getName() +
                             " is servicing a addLegalHold request");
                }
                String cacheId = (String)getObject();
                NewObjectIdentifier oid = (NewObjectIdentifier)getObject();
                String legalHold = (String)getObject();
                ArrayList disks = (ArrayList)getObject();

                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Adding (" + oid + ", " + legalHold + ")");
                }

                ProcessingCenter.addLegalHold(cacheId, oid,
                                              legalHold, disks);
                sendObject(null);
            } break;

            case METHOD_REMOVELEGALHOLD: {
                if (LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("The thread " +
                               Thread.currentThread().getName() +
                               " is servicing a removeLegalHold request");
                }
                String cacheId = (String)getObject();
                NewObjectIdentifier oid = (NewObjectIdentifier)getObject();
                String legalHold = (String)getObject();
                ArrayList disks = (ArrayList)getObject();

                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Removing (" + oid + ", " + legalHold + ")");
                }

                ProcessingCenter.removeLegalHold(cacheId, oid,
                                                 legalHold, disks);
                sendObject(null);
            } break;

            case METHOD_QUERY: {
                if (LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("The thread "+Thread.currentThread().getName()+" is servicing a query request");
                }
                String cacheId = (String)getObject();
                ArrayList disks = (ArrayList)getObject();
                String query = (String)getObject();
                ArrayList attributes = (ArrayList)getObject();
                EMDCookie cookie = (EMDCookie)getObject();
                int maxResults = ((Integer)getObject()).intValue();
                int timeout = ((Integer)getObject()).intValue();
                boolean forceResults = ((Boolean)getObject()).booleanValue();
                Object[] boundParameters = (Object[])getObject();
                ProcessingCenter.queryPlus(this,
                                           cacheId, disks, query, attributes,
                                           cookie, maxResults, timeout, 
                                           forceResults, boundParameters);
                // Send the end of Stream
                sendObject(new EndOfStreamImpl());
            } break;
            
            case METHOD_SELECTUNIQUE: {
                if (LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("The thread "+Thread.currentThread().getName()+" is servicing a selectUnique request");
                }
                String cacheId = (String)getObject();
                String query = (String)getObject();
                String attribute = (String)getObject();
                String cookie = (String)getObject();
                int maxResults = ((Integer)getObject()).intValue();
                int timeout = ((Integer)getObject()).intValue();
                boolean forceResults = ((Boolean)getObject()).booleanValue();
                int nParams =((Integer)getObject()).intValue();
                Object[] boundParameters = (Object[])getObject();

                ProcessingCenter.selectUnique(this,
                                              cacheId, query, attribute, 
                                              cookie, maxResults,
                                              timeout, forceResults,
                                              boundParameters);
                // Send the end of Stream
                sendObject(new EndOfStreamImpl());
            } break;

            }

            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("The thread "+Thread.currentThread().getName()+" finished servicing the request");
            }
            flush();

        } catch (IOException e) {
            LOG.log(Level.SEVERE,
                    "Got an IOException in the server ["+e+"]",
                    e);

            // Do not try to send an exception since the channel already had troubles
        } catch (EMDCommException e) {
            LOG.log(Level.SEVERE,
                    "Got a communication exception in the server ["+e+"]",
                    e);

            // Do not try to send an exception since the channel already had troubles
        } catch (EMDException e) {
            LOG.log(Level.SEVERE,
                    "Got an exception in the server ["+e+"]",
                    e);
            try {
                sendException(e);
            } catch (IOException ioe) {
                LOG.log(Level.SEVERE,
                        "Failed to send exception",
                        ioe);
            } catch (EMDException ioe) {
                LOG.log(Level.SEVERE,
                        "Failed to send exception",
                        ioe);
            }
        }
    }

}

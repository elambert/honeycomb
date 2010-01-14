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
import java.io.BufferedOutputStream;
import java.nio.ByteBuffer;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

import com.sun.honeycomb.emd.EMDCookie;
import com.sun.honeycomb.common.CanonicalEncoding;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ExternalObjectIdentifier;
import com.sun.honeycomb.emd.common.EMDCommException;
import com.sun.honeycomb.emd.common.QueryMap;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.coding.ByteBufferCoder;
import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.emd.common.HexObjectIdentifier;
import com.sun.honeycomb.emd.common.MDHit;
import com.sun.honeycomb.emd.common.MDHitByATime;
import com.sun.honeycomb.emd.cache.ExtendedCacheEntry;

/**
 * The <code>ObjectBrokerBasic</code> class is responsible for serializing /
 * deserializing objects on top of a stream.
 *
 * It also implements the communication protocol between MD clients and servers
 */

public class ObjectBrokerBasic 
    implements MDInputStream, MDOutputStream {
    private static final Logger LOG = Logger.getLogger("ObjectBrokerBasic");

    protected DataInputStream input;
    protected  DataOutputStream output;

    /**********************************************************************
     *
     * Constructors
     *
     **********************************************************************/

    public ObjectBrokerBasic(InputStream inputs,
                        OutputStream outputs) {
        input = new DataInputStream(inputs);
        output = new DataOutputStream(outputs);
    }
    
    public ObjectBrokerBasic(Socket socket) 
        throws IOException {
        setSocket(socket);
    }
    
    public ObjectBrokerBasic() {
    }

    public ObjectBrokerBasic(InputStream inputs) {
        input = new DataInputStream(inputs);
        output = null;
    }

    public ObjectBrokerBasic(OutputStream outputs) {
        input = null;
        output = new DataOutputStream(outputs);
    }

    public void setSocket(Socket socket) 
        throws IOException {
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }




    /**********************************************************************
     *
     * Definition of the EndOfStream inner class
     *
     **********************************************************************/

    public static class EndOfStreamImpl
        implements EndOfStream {
        public EndOfStreamImpl() {
        }
    }

    /**********************************************************************
     *
     * Global constants used to encode/decode methods and objects in the
     * streams
     *
     **********************************************************************/

    /*
     * Global value for exceptions
     */

    private static final byte EXCEPTION_DETECTED        = 0;

    /*
     * Object types
     */

    protected static final byte OBJECT_NULL               = 1;
    protected static final byte OBJECT_EOS                = 2; /* Notify the End Of Stream */
    protected static final byte OBJECT_DISK               = 3;
    protected static final byte OBJECT_OBJECTIDENTIFIER   = 4;
    protected static final byte OBJECT_HEXOBJECTIDENTIFIER = 5;
    protected static final byte OBJECT_STRING             = 6;
    protected static final byte OBJECT_INTEGER            = 7;
    protected static final byte OBJECT_BOOLEAN            = 8;
    protected static final byte OBJECT_LONG               = 9;
    protected static final byte OBJECT_DOUBLE             = 10;
    protected static final byte OBJECT_ARRAYLIST          = 11;
    protected static final byte OBJECT_SYSTEMMETADATA     = 12;
    protected static final byte OBJECT_MDHIT              = 13;
    protected static final byte OBJECT_QUERYMAP           = 14;
    protected static final byte OBJECT_EXTENDED_CACHE_ENTRY = 15;
    protected static final byte OBJECT_CODABLE            = 16;
    protected static final byte OBJECT_BYTEARRAY          = 17;
    protected static final byte OBJECT_DATE               = 18;
    protected static final byte OBJECT_TIME               = 19;
    protected static final byte OBJECT_TIMESTAMP          = 20;
    protected static final byte OBJECT_OBJARRAY           = 21;
    protected static final byte OBJECT_MDHITBYATIME       = 22;
    protected static final byte OBJECT_EXTERNALOBJECTIDENTIFIER = 23;
    protected static final byte OBJECT_EMDCOOKIE          = 24;

    
    /*
     * Method types
     */

    protected static final byte METHOD_SETMETADATA        = 127;
    protected static final byte METHOD_REMOVEMETADATA     = 126;
    protected static final byte METHOD_QUERY              = 125;
    protected static final byte METHOD_SELECTUNIQUE       = 124;
    protected static final byte METHOD_ADDLEGALHOLD       = 123;
    protected static final byte METHOD_REMOVELEGALHOLD    = 122;

    /**********************************************************************
     *
     * Routines dealing with object serialization / deserialization
     *
     **********************************************************************/

    /*
     * Serialization routines
     */

    public void sendException(Exception e) 
        throws IOException, EMDException {

        if (output == null) {
            throw new EMDException("The output stream has not been defined");
        }

        output.writeByte(EXCEPTION_DETECTED);
        if (e.getMessage() != null) {
            output.writeUTF(e.getMessage());
        } else {
            output.writeUTF("");
        }
    }

    public void sendObject(Object obj)
        throws EMDException {

        if (output == null) {
            throw new EMDException("The output stream has not been defined");
        }

        boolean sent = false;

        try {

            if ((!sent) && (obj == null)) {
                output.writeByte(OBJECT_NULL);
                sent = true;
            }

            if ((!sent) && (obj instanceof EndOfStream)) {
                output.writeByte(OBJECT_EOS);
                sent = true;
            }

            if ((!sent) && (obj instanceof NewObjectIdentifier)) {
                output.writeByte(OBJECT_OBJECTIDENTIFIER);
                ((NewObjectIdentifier)obj).serialize(output);
                sent = true;
            }

            if ((!sent) && (obj instanceof ExternalObjectIdentifier)) {
                output.writeByte(OBJECT_EXTERNALOBJECTIDENTIFIER);
                ((ExternalObjectIdentifier)obj).serialize(output);
                sent = true;
            }

            if ((!sent) && (obj instanceof HexObjectIdentifier)) {
                output.writeByte(OBJECT_HEXOBJECTIDENTIFIER);
                output.writeUTF(((HexObjectIdentifier)obj).toString());
                sent = true;
            }

            if ((!sent) && (obj instanceof QueryMap)) {
                output.writeByte(OBJECT_QUERYMAP);
                ((QueryMap)obj).serialize(output);
                sent = true;
            }

            if ((!sent) && (obj instanceof String)) {
                output.writeByte(OBJECT_STRING);
                output.writeUTF((String)obj);
                sent = true;
            }

            if ((!sent) && (obj instanceof Integer)) {
                output.writeByte(OBJECT_INTEGER);
                output.writeInt(((Integer)obj).intValue());
                sent = true;
            }

            if ((!sent) && (obj instanceof Boolean)) {
                output.writeByte(OBJECT_BOOLEAN);
                output.writeBoolean(((Boolean)obj).booleanValue());
                sent = true;
            }

            if ((!sent) && (obj instanceof Long)) {
                output.writeByte(OBJECT_LONG);
                output.writeLong(((Long)obj).longValue());
                sent = true;
            }

            if ((!sent) && (obj instanceof Double)) {
                output.writeByte(OBJECT_DOUBLE);
                output.writeDouble(((Double)obj).doubleValue());
                sent = true;
            }

            if ((!sent) && (obj instanceof ArrayList)) {
                output.writeByte(OBJECT_ARRAYLIST);
                ArrayList list = (ArrayList)obj;
                output.writeInt(list.size());
                for (int i=0; i<list.size(); i++) {
                    if (LOG.isLoggable(Level.FINER)) 
                        LOG.finer("Sending ArrayList entry "+i+" = '"+list.get(i)+"'");
                    sendObject(list.get(i));
                }
                sent = true;
            }

            if ((!sent) && (obj instanceof SystemMetadata)) {
                output.writeByte(OBJECT_SYSTEMMETADATA);
                ((SystemMetadata)obj).serialize(output);
                sent = true;
            }

            // ATTENTION: order is important here!!!
            if ((!sent) && (obj instanceof MDHitByATime)) {
                output.writeByte(OBJECT_MDHITBYATIME);
                MDHitByATime hit = (MDHitByATime)obj;
                sendObject(hit.getOid());
                sendObject(hit.getExtraInfo());
                output.writeLong(hit.getATime());
                sent = true;
            }

            if ((!sent) && (obj instanceof MDHit)) {
                output.writeByte(OBJECT_MDHIT);
                MDHit hit = (MDHit)obj;
                sendObject(hit.getOid());
                output.writeFloat(hit.getScore());
                sendObject(hit.getExtraInfo());
                sent = true;
            }
            
            if ((!sent) && (obj instanceof ExtendedCacheEntry)) {
                output.writeByte(OBJECT_EXTENDED_CACHE_ENTRY);
                ((ExtendedCacheEntry)obj).send(output);
                sent = true;
            }
            
            if ((!sent) && (obj instanceof Codable)) {
                output.writeByte(OBJECT_CODABLE);
                byte[] buffer = new byte[0x1000]; // 4k buffer
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
                new ByteBufferCoder(byteBuffer).encodeCodable((Codable)obj);
                output.writeInt(byteBuffer.position());
                output.write(buffer, 0, byteBuffer.position());
                sent = true;
            }
            
            if ((!sent) && (obj instanceof EMDCookie)) {
                output.writeByte(OBJECT_EMDCOOKIE);
                ((EMDCookie)obj).serialize(output); 
                sent = true;
            }
            
            if ((!sent) && obj instanceof Object[]) {
                Object[] boundParameters = (Object[])obj;
                int nParams = boundParameters.length;

                output.writeByte(OBJECT_OBJARRAY);
                output.writeInt(nParams);
                for (int i = 0; i < boundParameters.length;i++) {
                    if (LOG.isLoggable(Level.FINER)) 
                        LOG.finer("Sending bound Parameter "+i+" = '"+boundParameters[i]+"'");
                    sendObject(boundParameters[i]);
                }
                sent = true;
            }
            if ((!sent) && obj instanceof byte[]) {
                output.writeByte(OBJECT_BYTEARRAY);
                byte[] buffer = (byte[]) obj;
                int nBytes = buffer.length;
                output.writeInt(nBytes);
                output.write(buffer, 0, nBytes);
                sent = true;
            }
            
            if ((!sent) && obj instanceof Date) { 
                output.writeByte(OBJECT_DATE);
                String stringDate = CanonicalEncoding.encode((Date) obj);
                output.writeUTF(stringDate);
                sent = true;

            }
            
            if ((!sent) && obj instanceof Time) {
                output.writeByte(OBJECT_TIME);
                String stringTime = CanonicalEncoding.encode((Time) obj);
                output.writeUTF(stringTime);
                sent = true;
            }
            
            if ((!sent) && obj instanceof Timestamp) {
                output.writeByte(OBJECT_TIMESTAMP);
                String stringTimestamp = 
                        CanonicalEncoding.encode((Timestamp) obj);
                output.writeUTF(stringTimestamp);
                sent = true;
            }
        } catch (IOException e) {
            EMDException newe = null;
            if (obj != null) {
                newe = new EMDCommException("Couldn't send an object ["
                                            +obj.getClass().getName()+"]");
            } else {
                newe = new EMDCommException("Couldn't send an object [null]");
            }
            newe.initCause(e);
            throw newe;
        }

        if (!sent) {
            throw new EMDException("Tried to send an object of an unknown type ["
                                   +obj.getClass().getName()+"]");
        }
    }
        
    /*
     * Deserialization methods
     */        

    public Object getObject() 
        throws EMDException {

        Object result = null;

        if (input == null) {
            throw new EMDException("The input stream has not been defined");
        }

        try {
        
            byte type = input.readByte();

            result = getObject(type);
        } catch (IOException e) {
            EMDCommException newe = new EMDCommException
                ("Got an IOException while reading type ["+e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }

        return result;
    }



    public Object getObject(byte type) 
        throws EMDException {

        if (input == null) {
            throw new EMDException("The input stream has not been defined");
        }

        Object result = null;
        boolean found = false;

        try {
        
            switch (type) {
            case EXCEPTION_DETECTED:
                result = input.readUTF();
                throw new EMDException((String)result);

            case OBJECT_NULL:
                found = true;
                break;

            case OBJECT_EOS:
                result = new EndOfStreamImpl();
                found = true;
                break;
                                   
            case OBJECT_OBJECTIDENTIFIER:
                result = NewObjectIdentifier.deserialize(input);
                found = true;
                break;

            case OBJECT_EXTERNALOBJECTIDENTIFIER:
                result = ExternalObjectIdentifier.deserialize(input);
                found = true;
                break;

            case OBJECT_HEXOBJECTIDENTIFIER:
                result = new HexObjectIdentifier(input.readUTF());
                found = true;
                break;

            case OBJECT_STRING:
                result = new String(input.readUTF());
                found = true;
                break;

            case OBJECT_INTEGER:
                result = new Integer(input.readInt());
                found = true;
                break;
                
            case OBJECT_BOOLEAN:
                result = new Boolean(input.readBoolean());
                found = true;
                break;

            case OBJECT_LONG:
                result = new Long(input.readLong());
                found = true;
                break;

            case OBJECT_DOUBLE:
                result = new Double(input.readDouble());
                found = true;
                break;

            case OBJECT_ARRAYLIST:
                int nbElems = input.readInt();
                ArrayList res = new ArrayList();
                for (int i=0; i<nbElems; i++) {
                    res.add(getObject());
                }
                result = res;
                found = true;
                break;

            case OBJECT_SYSTEMMETADATA:
                result = SystemMetadata.deserialize(input);
                found = true;
                break;

            case OBJECT_MDHIT:
                String oid = (String)getObject();
                float score = input.readFloat();
                Object extraInfo = getObject();
                result = new MDHit(oid, score, extraInfo);
                found = true;
                break;

            case OBJECT_MDHITBYATIME:
                oid = (String)getObject();
                extraInfo = getObject();
                long atime = input.readLong(); 
                result = new MDHitByATime(oid, extraInfo, atime);
                found = true;
                break;

            case OBJECT_QUERYMAP:
                result = QueryMap.deserialize(input);
                found = true;
                break;
		
            case OBJECT_EXTENDED_CACHE_ENTRY:
                result = new ExtendedCacheEntry(input);
                found = true;
                break;

            case OBJECT_CODABLE:
                int size = input.readInt();
                byte[] bytes = new byte[size];
                input.readFully(bytes);
                ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
                result = new ByteBufferCoder(byteBuffer).decodeCodable();
                found = true;
                break;

            case OBJECT_EMDCOOKIE:
                result = EMDCookie.deserialize(input);
                found = true;
                break;
                
            case OBJECT_OBJARRAY:
                nbElems = input.readInt();
                Object[] boundParameters = new Object[nbElems];
                for (int i=0; i< nbElems; i++) {
                    boundParameters[i] = getObject();
                    if (LOG.isLoggable(Level.FINER)) 
                        LOG.finer("Receiving bound Parameter "+i+" = '"+boundParameters[i]+"'");
                }
                result = boundParameters;
                found = true;
                break;

            case OBJECT_BYTEARRAY:              
                size = input.readInt();
                byte[] binBytes = new byte[size];
                input.readFully(binBytes);
                result = binBytes;

                found = true;
                break;

            case OBJECT_DATE:
                String stringDate = new String(input.readUTF());
                result = CanonicalEncoding.decodeDate(stringDate);
                found = true;
                break;

            case OBJECT_TIME:
                String stringTime = new String(input.readUTF());
                result = CanonicalEncoding.decodeTime(stringTime);
                found = true;
                break;

            case OBJECT_TIMESTAMP:
                String stringTimestamp = new String(input.readUTF());
                result = CanonicalEncoding.decodeTimestamp(stringTimestamp);
                found = true;
                break;
              }

        if (!found) {
                throw new EMDException("Received an object of an unknown type");
            }
            
        } catch (IOException e) {
            EMDCommException newe = new EMDCommException
                ("Got an IOException while reading objects ["+e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
        
        return(result);
    }

    public void flush() {
        if (output == null) {
            return;
        }
        try {
            output.flush();
        } catch (IOException ioe) { 
            if (LOG.isLoggable(Level.FINER)) {
                LOG.finer("I/O error flush output stream " + ioe);
            }
        }
    }
    
    public void clearLastObject() {
        throw new RuntimeException("Method not implemented");
    }
    public Object getLastObject() {
        throw new RuntimeException("Method not implemented");
    }
}

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
 * This class implements the Cookie specific the EMD calls
 */

package com.sun.honeycomb.emd;

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ExternalObjectIdentifier;
import com.sun.honeycomb.common.Cookie;
import com.sun.honeycomb.common.CanonicalEncoding;

import com.sun.honeycomb.emd.remote.ObjectBrokerBasic;
import com.sun.honeycomb.emd.common.EMDException;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class EMDCookie
    implements Cookie {
    
    private NewObjectIdentifier lastOid;
    private long lastATime;
    private String lastAttribute;
    private String query;
    private String attribute;
    private int toBeSkipped;
    private Object[] boundParameters;
    private ArrayList attributes;

    private static final Logger LOG = Logger.getLogger(EMDCookie.class.getName());

    public EMDCookie(NewObjectIdentifier newLastOid,
                     String newQuery,
                     int newToBeSkipped,
                     Object[] newBoundParameters,
                     ArrayList newAttributes){
        this(newLastOid,newQuery,newToBeSkipped,newBoundParameters,newAttributes,-1);
    }
    
    public EMDCookie(NewObjectIdentifier newLastOid,
                     String newQuery,
                     int newToBeSkipped,
                     Object[] newBoundParameters,
                     ArrayList newAttributes,
                     long newLastAtime) {
        lastOid = newLastOid;
        lastATime = newLastAtime;
        lastAttribute = null;
        query = newQuery;
        attribute = null;
        toBeSkipped = newToBeSkipped;
        boundParameters = newBoundParameters;
        attributes = newAttributes;
        if (LOG.isLoggable(Level.FINER)) {
            LOG.finer("Create cookie: "+toString());
        }
    }

    public EMDCookie(String newLastAttribute,
                     String newQuery,
                     String newAttribute,
                     int newToBeSkipped,
                     Object[] newBoundParameters) {
        lastOid = null;
        attributes = null;
        lastAttribute = newLastAttribute;
        query = newQuery;
        attribute = newAttribute;
        toBeSkipped = newToBeSkipped;
        boundParameters = newBoundParameters;
        if (LOG.isLoggable(Level.FINER)) {
            LOG.finer("Create cookie: "+toString());
        }
    }

    public EMDCookie(byte[] bytes) 
        throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));

        if (input.readBoolean()) {
            lastOid = NewObjectIdentifier.deserialize(input);
        } else {
            lastOid = null;
        }

        if (input.readBoolean()) {
            query = input.readUTF();
        } else {
            query = null;
        }
        attribute = input.readUTF();
        toBeSkipped = input.readInt();

        //Read the boundParameters
        ObjectBrokerBasic broker = new ObjectBrokerBasic(input);
        Object o;
        boundParameters = null;
        attributes = null;
        try {
            lastAttribute = (String) broker.getObject();
            boundParameters = (Object[]) broker.getObject();
            attributes = (ArrayList) broker.getObject();
        } catch (EMDException e) {
            IOException newe = new IOException("Could not parse cookie: ["+e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
        if (LOG.isLoggable(Level.FINER)) {
            LOG.finer("Receive cookie: "+toString());
        }
    }
    
    public byte[] getBytes() 
        throws IOException {
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(byteArray);

        if (lastOid != null) {
            output.writeBoolean(true);
            lastOid.serialize(output);
        } else {
            output.writeBoolean(false);
        }
        if (query != null) {
            output.writeBoolean(true);
            output.writeUTF(query);
        } else {
            output.writeBoolean(false);
        }
        if (attribute != null) {
            output.writeUTF(attribute);
        } else {
            output.writeUTF("");
        }
        output.writeInt(toBeSkipped);

        // Write the boundParameters
        ObjectBrokerBasic broker = new ObjectBrokerBasic(output);
        try {
            broker.sendObject(lastAttribute);
            broker.sendObject(boundParameters);
            broker.sendObject(attributes);
        } catch (EMDException e) {
            IOException newe = new IOException(e.getMessage());
            newe.initCause(e);
            throw newe;
        }
        
        return(byteArray.toByteArray());
    }

    public NewObjectIdentifier getLastOid() {
        return(lastOid);
    }
    
    public String getLastAttribute() {
        return(lastAttribute);
    }

    public String getQuery() {
        return(query);
    }
    
    public String getAttribute() {
        return(attribute);
    }

    public int getToBeSkipped() {
        return(toBeSkipped);
    }

    public Object[] getBoundParameters() {
        return (boundParameters);
    }

    public ArrayList getAttributes() {
        return (attributes);
    }
    
    public long getLastATime() { 
        return lastATime;
    }
    
    public void serialize(DataOutput dout) throws IOException {
        lastOid.serialize(dout);
        dout.writeLong(lastATime);
    }

    public static EMDCookie deserialize(DataInput din) throws IOException {
       NewObjectIdentifier lastoid = NewObjectIdentifier.deserialize(din);
       long lastAtime= din.readLong();
       EMDCookie cookie = new EMDCookie(lastoid,null,0,null,null,lastAtime);
       return cookie;
    }
    
    public String toString() {
        return "<cookie oid="+lastOid+
            " query='"+query+
            "' attribute='"+
            lastAttribute+
            "' boundparameters=("+
            CanonicalEncoding.parametersToString(boundParameters)+
            ") attributes=("+
            CanonicalEncoding.literalsToString(attributes)+")>";
        }
}

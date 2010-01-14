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



package com.sun.honeycomb.emd.common;

import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.PrintStream;
import java.io.IOException;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.DecoderException;

import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.CanonicalEncoding;
import com.sun.honeycomb.emd.config.SessionEncoding;
import com.sun.honeycomb.common.Encoding;

public class QueryMap {
    
    private static Logger LOG = Logger.getLogger("QueryMap");

    private static final String FIELD_OID =
        SystemMetadata.FIELD_NAMESPACE + "." + SystemMetadata.FIELD_OBJECTID;

    private String[] names;
    private Object[] values;

    public QueryMap(String[] nNames,
                    Object[] nValues) {
        names = nNames;
        values = nValues;
    }

    public Object get(String key) {
        int index = 0;
	
        for (index=0; index<names.length; index++) {
            if (names[index].equals(key)) {
                break;
            }
        }

        if (index == names.length) {
            return(null);
        }

        return(values[index]);
    }
    
    //
    // Used to return results to client library
    // - need to use external form of OID
    //
    public void serialize(DataOutputStream output) 
        throws IOException {
        Encoding encoder = SessionEncoding.getEncoding();

        int length = names.length;
        for (int i=0; i<names.length; i++) {
            if (values[i] == null) {
                length--;
            }
        }

        output.writeInt(length);
        for (int i=0; i<names.length; i++) {
            if (values[i] != null) {
                try {
                    output.writeUTF(encoder.encodeName(names[i]));
                    output.writeUTF(encoder.encode(values[i]));
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("Serialize : name='"+names[i]+
                                 "' value='"+values[i].toString()+
                                 "' encName='"+encoder.encodeName(names[i])+
                                 "' encValue='"+encoder.encode(values[i])+"'");
                    }
                } catch (EncoderException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

     public static QueryMap deserialize(DataInputStream input) 
         throws IOException {
         Encoding encoder = SessionEncoding.getEncoding();

         int size = input.readInt();

         String[] names = new String[size];
         Object[] values = new String[size];

         for (int i=0; i<size; i++) {
             try {
                 names[i] = encoder.decodeName(input.readUTF());
                 values[i] = encoder.decode(input.readUTF());
             } catch (DecoderException e) {
                 throw new RuntimeException(e);
             }

         }

         return(new QueryMap(names, values));
     }

    private void toString(StringBuffer buffer) {
        for (int i=0; i<names.length; i++) {
            buffer.append("[");
            buffer.append(names[i]);
            buffer.append("-");
            buffer.append(CanonicalEncoding.encode(values[i]));
            buffer.append("]");
        }
    }

    private String convertOIDExternalForm(String value) {

        return NewObjectIdentifier.convertExternalHexString(value);
    }

    //
    // Used to return results to client library
    // - need to use external form of OID
    //
    public void toXML(PrintStream printer) throws EncoderException {
        Encoding encoder = SessionEncoding.getEncoding();
        for (int i=0; i<names.length; i++) {
            printer.print("    <" + NameValueXML.TAG_ATTRIBUTE);
            printer.print(" ");
            printer.print(NameValueXML.ATTRIBUTE_NAME);
            printer.print("=\"");
            printer.print(encoder.encodeName(names[i]));
            printer.print("\"");

            printer.print(" ");
            printer.print(NameValueXML.ATTRIBUTE_VALUE);
            printer.print("=\"");
            printer.print(encoder.encode(values[i]));
            printer.print("\"");
            printer.println("/>");
        }
    }

    /*
     * For debug purpose
     */
    public String[] getNames() {
        return(names);
    }
}

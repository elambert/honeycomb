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



package com.sun.honeycomb.common;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Class representing Schema Attribute that is fit for public consumption */
public class EMDSchemaAttribute {

    public static final int TYPE_BYTE   =1; // For internal use
    public static final int TYPE_LONG   =2;
    public static final int TYPE_DOUBLE =3;
    public static final int TYPE_STRING =4;
    public static final int TYPE_BLOB   =5;
    
    public EMDSchemaAttribute(String name, int type) {
        this.name = name;
        this.type = type;
    }
    
    public String getName()  {return name;}
    public int    getType()  {return type;}
    
    public void serialize(DataOutput dout) throws IOException {
        dout.writeUTF(name);
        dout.writeInt(type);
    }
    
    public static EMDSchemaAttribute deserialize(DataInput din) 
        throws IOException {
        String name = din.readUTF();
        int type = din.readInt();
	
        return new EMDSchemaAttribute(name, type);
    }

    private String getTypeString() {
        String result = null;
        
        switch (type) {
        case TYPE_BYTE:
            result = "byte";
            break;

        case TYPE_LONG:
            result = "long";
            break;

        case TYPE_DOUBLE:
            result = "double";
            break;

        case TYPE_STRING:
            result = "string";
            break;

        case TYPE_BLOB:
            result = "blob";
            break;
            
        }
        
        return(result);
    }

    public String toString() {
        String typeString = getTypeString();
        
        return(typeString+" "+name);
    }
    
    String name;
    int type;
}

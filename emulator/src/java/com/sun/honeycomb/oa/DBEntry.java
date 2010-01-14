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



package com.sun.honeycomb.oa;

import com.sun.honeycomb.common.SystemMetadata;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.sql.Blob;
import java.sql.SQLException;

public class DBEntry {
    
    public String path;
    public String link;
    public SystemMetadata smd;
    public byte[] mdField;

    public DBEntry(String nPath,
		   String nLink,
		   SystemMetadata nSmd,
		   byte[] nMdField) {
	path = nPath;
	link = nLink;
	smd = nSmd;
	mdField = nMdField;
    }

    public byte[] getBytes() 
	throws IOException {
	ByteArrayOutputStream bytes = null;
	DataOutputStream output = null;

	try {
	    bytes = new ByteArrayOutputStream();
	    output = new DataOutputStream(bytes);
	    output.writeUTF(path);
	    if (link != null) {
		output.writeBoolean(true);
		output.writeUTF(link);
	    } else {
		output.writeBoolean(false);
	    }
	    output.writeInt(mdField.length);
	    output.write(mdField);
	    smd.serialize(output);

	    return(bytes.toByteArray());
	} finally {
	    if (output != null) {
		output.close();
	    }
	    if (bytes != null) {
		bytes.close();
	    }
	}
    }

    public static DBEntry convertBytes(Blob input)
	throws IOException, SQLException {
	
	DataInputStream stream = null;

	try {
	    stream = new DataInputStream(input.getBinaryStream());
	    String path = stream.readUTF();
	    String link = null;
	    if (stream.readBoolean()) {
		link = stream.readUTF();
	    }
	    int length = stream.readInt();
	    byte[] mdField = new byte[length];
	    stream.read(mdField);
	    SystemMetadata smd = SystemMetadata.deserialize(stream);

	    return(new DBEntry(path, link, smd, mdField));
	} finally {
	    if (stream != null) {
		stream.close();
	    }
	}
    }
}
    
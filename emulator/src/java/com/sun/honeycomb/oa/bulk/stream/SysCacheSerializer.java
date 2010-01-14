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



package com.sun.honeycomb.oa.bulk.stream;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;

import com.sun.honeycomb.cm.NodeMgr;

import com.sun.honeycomb.oa.bulk.Session;
import com.sun.honeycomb.oa.bulk.RestoreState;
import com.sun.honeycomb.oa.bulk.CallbackObject;
import com.sun.honeycomb.oa.bulk.SerializationException;


/*
 * Override the server version. Serializing the SysCache is a no-op for the emulator.
 */
public class SysCacheSerializer extends ContentSerializer  {

    public SysCacheSerializer(Session session) { 
        super(session);
    }
    
    public long getContentLength() {
        return -1; 
    }

    private final static String SYSTEM_CACHE_ENTRIES = "SYSTEM_CACHE_ENTRIES";

    public CallbackObject serialize(StreamWriter writer) throws SerializationException {
        String dir = NodeMgr.getEmulatorRoot() + File.separatorChar + "var" + File.separatorChar + "data";
        final File root = new File (dir);
        File[] files = root.listFiles();
        writer.writeHeader(SYSTEM_CACHE_ENTRIES, files.length);
        writer.writeSeparator();
        for (int i = 0; i < files.length; i++)
            writer.writeLine(files[i].getName() + " " + files[i].lastModified());
        return new CallbackObject(null, CallbackObject.SYS_CACHE_CALLBACK);
    }

    public CallbackObject deserialize(StreamReader reader, Map headers) 
        throws SerializationException, IOException {
        RestoreState restoreState = new RestoreState();
        int objectCount = Integer.parseInt((String)headers.get(SYSTEM_CACHE_ENTRIES));
        reader.readSeparator();
        for (int i = 0; i < objectCount; i++){
            String line = reader.readLine();
            int space = line.indexOf(" ");
            String oid = line.substring(0, space);
            String ctimeString = line.substring(space+1);
            long ctime = Long.parseLong(ctimeString);
            restoreState.put(oid, ctime);
        }
        System.out.println("Restore state: " + restoreState);
        return new CallbackObject(restoreState, CallbackObject.SYS_CACHE_CALLBACK);
    }


    public void init(Object obj) throws SerializationException  {}

    public int getCacheCount(){return 1;}

}

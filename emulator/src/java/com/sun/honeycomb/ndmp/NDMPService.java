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



package com.sun.honeycomb.ndmp;

import com.sun.honeycomb.cm.NodeMgr;

import java.util.Comparator;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Date;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.logging.Level;

import com.sun.honeycomb.cm.EmulatedService;

public class NDMPService extends BaseNDMPService
    implements EmulatedService {

    public String getName() {
        return("NDMP Data Server");
    }
    // no-op for emulator
    public void configureSwitch(int port){}

    Iterator getOIDs(Date startTime, Date endTime){
        long start = (startTime == null) ? Long.MIN_VALUE : startTime.getTime();
        long end = (endTime == null) ? Long.MAX_VALUE : endTime.getTime();
        String root = NodeMgr.getEmulatorRoot() +
            File.separator+"var"+File.separator+"data";

        File f = new File(root);
        if (logger.isLoggable(Level.INFO))
            logger.info("Searching from " + new Date(start) + " to " + new Date(end) + " " + f);
        File[] unfiltered = f.listFiles();
        Comparator c = new Comparator(){
                public boolean equals(Object o1, Object o2){
                    return ((File)o1).getName().equals(((File)o2).getName());
                }
                public int compare (Object o1, Object o2){
                    if (((File)o1).lastModified()  < ((File)o2).lastModified())
                        return -1;
                    else if (((File)o1).lastModified()  < ((File)o2).lastModified())
                        return 0;
                    else
                        return 1;
                }
            };
        final TreeSet files = new TreeSet(c);
        int n = unfiltered.length;
        for (int i = 0; i < n; i++){
            if (unfiltered[i].lastModified() >= start && unfiltered[i].lastModified() <= end)
                files.add(unfiltered[i]);
        }
        return files.iterator();
    }

    void backup (Date startTime, Date endTime, SocketChannel sc) throws IOException{
        Iterator oids = getOIDs(startTime, endTime);

        sc.write(ByteBuffer.wrap(("<pre>").getBytes()));
        while (oids.hasNext()){
            File file = (File) oids.next();
            sc.write(ByteBuffer.wrap(("OID: " + file.getName() + "\n").getBytes()));
            sc.write(ByteBuffer.wrap(("Creation-date: " + new Date(file.lastModified()) + "\n").getBytes()));
            sc.write(ByteBuffer.wrap(("length: " + file.length() + "\n").getBytes()));
            sc.write(ByteBuffer.wrap(("\n").getBytes()));
            FileInputStream fis = new FileInputStream(file);
            FileChannel fc = fis.getChannel();
            fc.transferTo(0, fc.size(), sc);
            sc.write(ByteBuffer.wrap(("\n").getBytes()));
        }
    }

    public NDMPService(){
        try{
            if (logger.isLoggable(Level.INFO))
                logger.info("Starting NDMP Service on " + java.net.InetAddress.getLocalHost().getHostName());
        }
        catch (java.net.UnknownHostException e){
            logger.log(Level.SEVERE, "Could not determine local host", e);
        }
    }

    private static HashMap bundle = new HashMap() { 
            { 
                put("info.ndmp.inactive", "none");
                put("info.ndmp.backup", "Backup");
                put("info.ndmp.restore", "Restore");
                put("info.ndmp.accepted", "NDMP: Accepted request");
                put("info.ndmp.backupRequest", "NDMP: Backup requested from  {0,date} to {1,date}");
                put("info.ndmp.backupStarted", "NDMP: Backup started");
                put("info.ndmp.backupFinished", "NDMP: Backup finished. {0} objects backed up");
                put("info.ndmp.restoreRequest", "NDMP: Restore requested");
                put("info.ndmp.restoreStarted", "NDMP: Restore started");
                put("info.ndmp.restoreFinished", "NDMP: Restore finished. {0} objects restored");
                put("err.ndmp.error", "NDMP: Request failed with error: {0}");
                put("err.ndmp.parseFields", "NDMP: Unable to parse date from {0} environment variable: {1}. Expected date in form {2}");
                put("err.ndmp.missingField", "NDMP: Backup request missing required {0} environment variable");
            }
    };

    void alert(String message){
        System.err.println("**** Alert **** " + message);
    }

    String getLocalizedString(String key){
        String s = (String) bundle.get(key);
        if (s == null)
            return key;
        else
            return s;
    }

}

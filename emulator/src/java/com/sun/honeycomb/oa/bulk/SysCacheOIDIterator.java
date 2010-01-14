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



package com.sun.honeycomb.oa.bulk;

import java.io.File;
import java.io.FileFilter;
import java.util.logging.Logger;

import com.sun.honeycomb.cm.NodeMgr;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.NewObjectIdentifier;

/* The emulator version looks at the file properties instead of the 
 * system cache to generate a time-ordered iteration of OIDs.
 */
public class SysCacheOIDIterator implements OIDIterator {
    
    private Logger Log = Logger.getLogger(SysCacheOIDIterator.class.getName());
    private int index = 0;
    private File[] files;
    private long _end;
    
    /**
     * Simple implementation of querying the system cache in order to obtain 
     * a list of oids for consumption by the BackupSession 
     *  
     * @param query
     * @throws ArchiveException
     */
    public SysCacheOIDIterator(long start, long end) throws ArchiveException {
        this(start, end, false);
        _end = end;
    }

    public SysCacheOIDIterator(final long start, final long end, boolean force) throws ArchiveException {
        String dir = NodeMgr.getEmulatorRoot() + File.separatorChar + "var" + File.separatorChar + "data";
        final File root = new File (dir);
        FileFilter ff = new FileFilter(){
                public boolean accept(File pathname){
                    return pathname.lastModified() >= start && (pathname.lastModified() <= end || end == -1); 
                }
            };
        files = root.listFiles(ff);
    }

    public boolean hasNext() throws ArchiveException {
        return index < files.length;
    }

    public NewObjectIdentifier next() {
        String name = files[index++].getName();
        return NewObjectIdentifier.fromHexString(name);
    }

    public long getEndTime() {
        return _end;
    }
    public static void main (String argv[]) throws Exception{
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MM/dd/yyyy hh:mm:SSS");
        java.util.Date start = dateFormat.parse("02/05/2008 00:00:000");
        java.util.Date end = dateFormat.parse("02/06/2008 00:00:000");
        com.sun.honeycomb.oa.bulk.SysCacheOIDIterator oidIterator = new com.sun.honeycomb.oa.bulk.SysCacheOIDIterator(start.getTime(), end.getTime());
        int i = 0;
        while (oidIterator.hasNext()){
            i++;
            oidIterator.next();
        }
        System.out.println(i);
    }
}

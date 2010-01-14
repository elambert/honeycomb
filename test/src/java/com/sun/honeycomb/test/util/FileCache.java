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



package com.sun.honeycomb.test.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.io.File;

/**
 *  Hold files for reuse. Each file created here is remembered and
 *  deleted by the clear() method if it still exists, whether or not
 *  it is in the cache when clear() is called. All files allocated
 *  here are renamed and made unique by adding or overwriting some data.
 *
 *  XXX consider sorting cache by filesize
 */
public class FileCache {

    private ArrayList cache = new ArrayList();
    private HashSet all = new HashSet();

    public FileCache() {
        Runtime.getRuntime().addShutdownHook(new Thread(new FileCacheCleanup(),
                                                        "FileCacheCleanup"));
    }

    private class FileCacheCleanup implements Runnable {
        public void run() {
            clear();
        }
    }

    /**
     *  put file/FileHandle back in cache.
     *  XXX throw it away if cache is already big enough with
     *  file(s) around this size.
     */
    public void add(FileHandle fh) {
        synchronized(cache) {
            cache.add(fh);
        }
    }

    /**
     *  Get file from cache, or create new one.
     */
    public FileHandle get(long size, boolean binary) 
                                                throws HoneycombTestException {
        FileHandle fh = null;
        long min_diff = -1;
        int index = -1;
        synchronized(cache) {
            //
            //  find a file
            //
            for (int i=0; i<cache.size(); i++) {
                FileHandle fh2 = (FileHandle) cache.get(i);

                if (fh2.binary != binary)
                    continue;

                if (fh2.size > size)
                    continue;

                if (fh2.size == size) {
                    //
                    //  a winner
                    //
                    fh = fh2;
                    index = i;
                    break;
                }

                if (fh == null) {
                    //
                    //  1st candidate of lesser size
                    //
                    fh = fh2;
                    index = i;
                } else {
                    //
                    //  candidate of lesser size
                    //
                    long diff = size - fh2.size;
                    if (diff < min_diff) {
                        //
                        //  better candidate
                        //
                        fh = fh2;
                        index = i;
                    }
                }
            }
            if (fh != null) {
                //
                //  this file will be used -
                //  take it out of cache while in sync section
                //
                cache.remove(index);
            }
        }
        if (fh == null) {
            //
            //  create new file
            //
            fh = new FileHandle(size, binary);
        } else {
            //
            //  recycle existing file
            //
            all.remove(fh.f.getPath());
            fh.recycle(size);
        }

        //
        //  remember path for deletion in clear()
        //
        all.add(fh.f.getPath());
        return fh;
    }

    /**
     *  Clear cache, deleting all files ever handed out by get().
     */
    public void clear() {
        Iterator it = all.iterator();
        while (it.hasNext()) {
            String path = (String) it.next();
            File f = new File(path);
            try {
                f.delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        all.clear();
        cache.clear();
    }

    /** main is for test */
    public static void main(String args[]) {
        try {
            FileCache fc = new FileCache();
            FileHandle fh = fc.get(1024, true);
            System.out.println("got file " + fh.f);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }
}

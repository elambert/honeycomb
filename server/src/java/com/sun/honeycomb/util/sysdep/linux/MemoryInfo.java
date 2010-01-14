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


package com.sun.honeycomb.util.sysdep.linux;

import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.RuntimeException;

/** 
 * Provides a concrete implementation of MemoryInfo for i386 Linux. Memory 
 * utilization information for the underlying host running the virtual machine
 * containing this object. Utilizes the /proc filesystem to get this 
 * information.
 */
public class MemoryInfo extends com.sun.honeycomb.util.sysdep.MemoryInfo {

    private static final Logger logger = 
        Logger.getLogger (MemoryInfo.class.getName());

    /** where we get the memory information */
    private static final String PROC_MEMINFO = "/proc/meminfo";

    protected int _total;
    protected int _free;
    protected int _buffers;
    protected int _cached;
   
    public MemoryInfo() {
        BufferedReader file = null;
        
        try {
            String line = null;
            file = new BufferedReader (new FileReader (PROC_MEMINFO));

            while ((line = file.readLine()) != null) {
                if (line.startsWith ("MemTotal:")) {
                    _total = parseMemLine (line);
                }
                else if (line.startsWith ("MemFree:")) {
                    _free = parseMemLine (line);
                }
                else if (line.startsWith ("Buffers:")) {
                    _buffers = parseMemLine (line);
                }
                else if (line.startsWith ("Cached:")) {
                    _cached = parseMemLine (line);
                }
            }
        }
        catch (IOException ioe) {
            logger.severe ("unable to read memory statistics from /proc");
            throw new RuntimeException (
                "unable to read memory stats from /proc", ioe);
        }
        finally {
            if (file != null) {
                try {
                    file.close();
                }
                catch (Exception e) {
                    // nothing to do here
                }
            }
        }
    }

    public long getTotal() {
        return _total;
    }

    public long getFree() {
        return _free;
    }

    public long getBuffers() {
        return _buffers;
    }

    public long getCached() {
        return _cached;
    }

    /** Turn a String into an int */
    private static int toInt (String string) {
        return Integer.valueOf (string).intValue();
    }

    /** */
    private static int parseMemLine (String line) {
        String[] lineParts = line.split("\\s+", 3);
        if (lineParts.length == 3) {
            return toInt (lineParts[1]);
        }

        return -1;
    }
}

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



package com.sun.honeycomb.platform;

import com.sun.honeycomb.util.sysdep.SystemInfo;
import com.sun.honeycomb.util.sysdep.MemoryInfo;

import java.text.DecimalFormat;

/**
 * A simple test program that excercises the Platform creation
 * mechanism
 */
public class PlatformTest {

    public static void main (String[] argv) {

        SystemInfo sys = HAPlatform.getPlatform().getSystemInfo();
        MemoryInfo mem = HAPlatform.getPlatform().getMemoryInfo();

        if (mem == null) {
            System.out.println ("No memory info available!");
            return;
        }

        if (sys == null) {
            System.out.println ("No system info available!");
            return;
        }

        DecimalFormat d = new DecimalFormat ("#######0.0#");
        System.out.println ("Uptime: " 
                            + sys.getUpDays() + " days " 
                            + sys.getUpHours() + " hours "
                            + sys.getUpMinutes() + " minutes");

        System.out.println ("Load avg: "
                            + d.format (sys.get1MinLoad()) + " "
                            + d.format (sys.get5MinLoad()) + " " 
                            + d.format (sys.get15MinLoad()));

        System.out.println ("Cpu(s): " 
                            + d.format (sys.getUserLoad()) + " user "
                            + d.format (sys.getSystemLoad()) + " system " 
                            + d.format (sys.getNiceLoad()) + " nice " 
                            + d.format (sys.getIdleLoad()) + " idle " 
                            + d.format (sys.getIntr()) + " intr/s");

        System.out.println ("Memory (kB): " 
                            + mem.getTotal() + " total "
                            + mem.getFree() + " free " 
                            + mem.getBuffers() + " buffers " 
                            + mem.getCached() + " cached");
        System.out.println ();
    }
}

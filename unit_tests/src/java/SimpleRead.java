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



import java.util.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;

/**
    Do infinite opens/seeks/reads of given file.

    % javac SimpleRead.java
    % java SimpleRead <filename>
*/

public class SimpleRead extends Thread {

    public static final int SLICE_SIZE = 64*1024;

    private static void usage() {
        System.err.println("usage: ... <mode> <file-to-read>");
        System.err.println("\tmode = o(pen) s(eek) or r(ead)");
        System.exit(1);
    }

    public static void main(String[] args) {
        final int OPEN = 1;
        final int SEEK = 2;
        final int READ = 3;

        if (args.length != 2) {
            usage();
        }
        int mode = -1;
        if (args[0].equals("o")) {
            mode = OPEN;
        } else if (args[0].equals("s")) {
            mode = SEEK;
        } else if (args[0].equals("r")) {
            mode = READ;
        } else {
            System.err.println("unknown mode" + args[0]);
            usage();
        }
        ByteBuffer bbuf = ByteBuffer.allocateDirect(SLICE_SIZE);
        String fname = args[1];
        int opens = 0;
	try {
            System.err.println("start: " + new Date());
            // HC uses "rw" for reads
            RandomAccessFile file = new RandomAccessFile(fname, "rw");
            FileChannel ch = file.getChannel();
            switch (mode) {
                case OPEN:
                    while (true) {
                        file = new RandomAccessFile(fname, "rw");
                        ch = file.getChannel();
                        opens++;
                    }
                case SEEK:
                    Random r = new Random();
                    int length = (int)file.length();
                    while (true) {
                        long offset = r.nextInt(length);
                        file.seek(offset);
                    }
                case READ:
                    while (true) {
                        while(ch.read(bbuf) != -1) {
                            bbuf.clear();
                        }
                        file.seek(0);
                    }
                default:
                    System.err.println("prog err: case/default");
                    System.exit(1);
            }
	} catch (Throwable t) {
            System.err.println(new Date());
	    t.printStackTrace();
            if (mode == OPEN)
                System.out.println("opens: " + opens);
	    System.exit(1);
	}
    }
}

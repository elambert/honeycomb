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
    Open/write lots of files in given dir, then close.

    % javac SimpleClose.java
    % java SimpleClose <dirname>
*/

public class SimpleClose extends Thread {

    public static final int SLICE_SIZE = 64*1024;
    public static final int NFILES = 50000;
    public static final int NBUFS = 1;

    private static boolean quit = false;

    private static void usage() {
        System.err.println("usage: ... <dir-to-write>");
        System.exit(1);
    }

    private static File[] files = new File[NFILES];

    public static void main(String[] args) {

        if (args.length != 1) {
            usage();
        }

        String dirname = args[0];
	try {

            ByteBuffer bbuf = ByteBuffer.allocateDirect(SLICE_SIZE);
            for (int i=0; i<SLICE_SIZE; i++)
                bbuf.put((byte)i);
            bbuf.flip(); // prepare for read

            // get stdin
	    InputStreamReader isr = new InputStreamReader(System.in);
	    BufferedReader in = new BufferedReader(isr);

            Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown(),
                                                        "Shutdown"));

            RandomAccessFile rafs[] = new RandomAccessFile[NFILES];

            // open/write files
            System.out.println("opening " + NFILES + " files and writing " +
                               NBUFS + " 64k buffers to each");
            for (int i=0; i<files.length; i++) {

                if ((i+1) % 10 == 0)
                    System.out.print(".");
                if ((i+1) % 700 == 0)
                    System.out.println();

                if (quit)
                    System.exit(1);

                files[i] = new File(dirname + "/TESTCLOSE." + i);
                rafs[i] = new RandomAccessFile(files[i], "rw");
                FileChannel ch = rafs[i].getChannel();
                for (int j=0; j<NBUFS; j++) {
                    bbuf.rewind();
                    while (bbuf.hasRemaining()) {
                        if (ch.write(bbuf) < 1) {
                            System.err.println("wrote < 1");
                            System.exit(1);
                        }
                    }
                }
            }
            // wait for word to continue

            System.out.print("\n<enter> to begin closing..");
            in.readLine();

            // close files
            long t0 = System.currentTimeMillis();
            for (int i=0; i<files.length; i++) {
                rafs[i].close();
                rafs[i].getChannel().close();
            }
            System.out.println("closed all in " + 
                               (System.currentTimeMillis()-t0) + " ms");

            // cleanup in shutdown handler

	} catch (Throwable t) {
	    t.printStackTrace();
	    System.exit(1);
	}
    }
    private static class Shutdown implements Runnable {
        public void run() {
            quit = true;
            System.out.println("\ncleaning up..");
            try {
                for (int i=0; i<files.length; i++) {
                    if (files[i] == null)
                        break;
                    files[i].delete();
                }
	    } catch (Throwable t) {
	        t.printStackTrace();
	        System.exit(1);
	    }
        }
    }
}

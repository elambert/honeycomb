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



/**
    Timed parallel writes of 64 MB files using nio, no threads.

    % javac SimpleThreadedWrites.java
    % java SimpleThreadedWrites [-s] <fname1 fname2 ...>
*/

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;

public class SimpleThreadedWrites extends Thread {

    public static final int SLICE_SIZE = 64*1024;
    public static final int NB_SLICES = 1024;

    public static float mbytes_per_thread = (float)(NB_SLICES * SLICE_SIZE) / 
                                  (float)(1024 * 1024);

    private static ByteBuffer mainBuffer = null;
    private static int n_threads;
    private static boolean sync_slices = false;

    private static long[] times;
    private static long[] synctimes;

    public static void init() throws IOException {
	
	mainBuffer = ByteBuffer.allocateDirect(SLICE_SIZE);
        for (int i=0; i<SLICE_SIZE; i++)
	    mainBuffer.put((byte)i);
	mainBuffer.flip();
	
	times = new long[n_threads];
	synctimes = new long[n_threads];

	waitingThreads = 0;
    }

    private static int waitingThreads = 0;
    private static void synchronize() {
	synchronized (SimpleThreadedWrites.class) {
	    waitingThreads++;
	    if (waitingThreads == n_threads) {
		waitingThreads = 0;
		SimpleThreadedWrites.class.notifyAll();
	    } else {
		boolean blocked = true;
		while (blocked) {
		    try {
			SimpleThreadedWrites.class.wait();
			blocked = false;
		    } catch (InterruptedException ie) {
                        System.err.println("interrupted: " + ie);
		    }
		}
	    }
	}
    }

    private static void printStats(String msg, long[] t) {

	System.out.println(msg+":");

        long sum = 0;
	for (int i=0; i<t.length; i++) {
	    System.out.print(t[i]+" ");
	    sum += t[i];
	}
        System.out.println("msec");

        // msec avg is integer, avg bandwidth is precise
        long average = sum / t.length; 
	System.out.println("["+average+"] => [" +
			   (1000.0 * mbytes_per_thread * (float)t.length /
                            ((float)sum))+" MB/s]\n");
    }

    private static void usage() {
        System.err.println("Usage: java SimpleThreadedWrites [-s] <file1 file2 ..>");
        System.err.println("\t-s\tsync between slice writes");
        System.exit(1);
    }

    public static void main(String[] args) {

        if (args.length < 1)
            usage();
 
        int start_arg = 0;
        if (args[0].equals("-s")) {
            if (args.length < 2)
                usage();
            sync_slices = true;
            n_threads = args.length - 1;
            start_arg = 1;
        } else
            n_threads = args.length;

        System.out.println("MB/thread: " + mbytes_per_thread + "\n");
	try {
	    init();
	} catch (IOException e) {
	    e.printStackTrace();
	    System.exit(1);
	}

	SimpleThreadedWrites[] threads = new SimpleThreadedWrites[n_threads];
	for (int i=0; i<n_threads; i++)
	    threads[i] = new SimpleThreadedWrites(i, args[start_arg+i]);
	for (int i=0; i<n_threads; i++)
	    threads[i].start();

	for (int i=0; i<n_threads; i++) {
	    while (threads[i].isAlive()) {
		try {
		    threads[i].join();
		} catch (InterruptedException e) {
                    e.printStackTrace();
		}
	    }
	}

	printStats("Writes per thread", times);
	printStats("With sync", synctimes);
    }

    private int nbr;
    private String fname;
    private ByteBuffer buffer;

    public SimpleThreadedWrites(int i, String fname) {
	nbr = i;
        this.fname = fname;
	buffer = mainBuffer.duplicate();
    }

    public void run() {
	try {
            RandomAccessFile output = new RandomAccessFile(fname, "rw");
	    //FileOutputStream output = new FileOutputStream(fname);
	    synchronize();

	    long startTime;
	    times[nbr] = 0;
	    synctimes[nbr] = 0;

	    try {
		FileChannel channel = output.getChannel();
		for (int i=0; i<NB_SLICES; i++) {
		    buffer.rewind();
		    startTime = System.currentTimeMillis();
		    while (buffer.hasRemaining()) {
			channel.write(buffer);
		    }
		    times[nbr] += (System.currentTimeMillis()-startTime);
                    if (sync_slices)
		        synchronize();
		    synctimes[nbr] += (System.currentTimeMillis()-startTime);
		}
	    } finally {
		output.close();
                try {
                    (new File(fname)).delete();
                } catch (Exception e) {
                    e.printStackTrace();
                }
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	    System.exit(1);
	}
    }
}

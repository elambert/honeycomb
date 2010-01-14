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
    Timed write of a 64 MB file using nio, no threads.

    % javac SimpleWriteTest.java
    % java SimpleWriteTest <filename>
*/

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;

public class SimpleWriteTest {

    public static final int SLICE_SIZE = 64*1024;
    public static final int NB_SLICES = 1024;

    public static float mbytes_written = (float)(NB_SLICES * SLICE_SIZE) / 
                                  (float)(1024 * 1024);

    private static ByteBuffer buffer = null;
	
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: java SimpleWriteTest <write_fname>");
            System.exit(1);
        }
        String fname = args[0];
        RandomAccessFile output;
	try {
	    buffer = ByteBuffer.allocateDirect(SLICE_SIZE);
            for (int i=0; i<SLICE_SIZE; i++)
	        buffer.put((byte)i);
	    buffer.flip();

            long t1 = System.currentTimeMillis();
            output = new RandomAccessFile(fname, "rw");
	    //FileOutputStream output = new FileOutputStream(fname);

            System.out.println("open time: " + (System.currentTimeMillis()-t1) +
                               " ms");
            FileChannel channel = output.getChannel();

            t1 = System.currentTimeMillis();
            for (int i=0; i<NB_SLICES; i++) {
                buffer.rewind();
                while (buffer.hasRemaining())
                    channel.write(buffer);
            }
            t1 = System.currentTimeMillis() - t1;
            System.out.println("wrote " + mbytes_written + " MB");
            System.out.println("write time: " + t1 + " ms");
            System.out.println("bandwidth: " + ((mbytes_written * 1000.0) / 
                                               (float) t1) + " MB/s");
            t1 = System.currentTimeMillis();
            output.close();
            System.out.println("close time: " +
                               (System.currentTimeMillis()-t1) + " ms");
	} catch (Throwable t) {
	    t.printStackTrace();
	    System.exit(1);
	} finally {
            try {
                (new File(fname)).delete();
            } catch (Exception e) {
                e.printStackTrace();
	    }
        }
    }
}

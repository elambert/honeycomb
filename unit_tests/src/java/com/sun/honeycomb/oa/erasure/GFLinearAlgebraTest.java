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



package com.sun.honeycomb.oa.erasure;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;

public class GFLinearAlgebraTest {
    /** 
     * A utility to print a matrix to stdout
     * @param a The matrix to print
     * @param title A description of the matrix to print
     */
    /*
    public static void printMatrix(byte[][] a, String title) {
        short MASK = 0xff;
        System.out.println(title);
        for(int i=0;i<a.length;i++) {
            for(int j=0;j<a[i].length;j++) {
                System.out.print((a[i][j]&MASK)+"  ");
            }            
            System.out.println("");
        }
    }
    */

    /** 
     * Test code for the <CODE>GFLinearAlgebra</CODE> class
     */
    /*
    private static void test1() {
        int num_checks = 5;
        int num_data = 10;
        int fragSize = 65562;
        GFLinearAlgebra gfla 
            = new GFLinearAlgebra(num_checks, num_data, fragSize,
                                  GFLinearAlgebra.VANDERMONDE);
        
        // test many combinations of missing and replaced by frags
        Random random = new Random();
        long startTime = System.currentTimeMillis();
        for(int xxx = 0; xxx < 1000000; xxx++) {
            // At least 1 missing
            int n_missing = random.nextInt(num_checks) + 1;
            int missing_frag[] = new int[n_missing];
            int replaced_by[] = new int[n_missing];
            // fill in the missing and replaced by arrays
            BitSet dbits = new BitSet(num_data);
            BitSet cbits = new BitSet(num_checks);
            for(int i = 0; i < n_missing; i++) {
                int v1 = random.nextInt(num_data);
                int v2 = random.nextInt(num_checks);
                while(dbits.get(v1)) {
                    v1 = random.nextInt(num_data);
                }
                while(cbits.get(v2)) {
                    v2 = random.nextInt(num_checks);
                }
                dbits.set(v1);
                cbits.set(v2);
                missing_frag[i] = v1;
                replaced_by[i] = v2;
            }
            Arrays.sort(missing_frag);
            Arrays.sort(replaced_by);
            // Construct the forward matrix
            byte f[][] = new byte[num_data][num_data];
            for(int i=(num_data - replaced_by.length);i<num_data;i++) {
                f[i] = gfla.forwardMatrix
                    [replaced_by[i-num_data+replaced_by.length]];
            }
            int c =0;
            int c1 = 0;
            
            for(int i = 0; i < num_data;i++) {
                if((c == missing_frag.length) || (i < missing_frag[c])) {
                    f[c1++][i] = 1;
                }
                else {
                    c++;
                }
            }
            
            // Multiply the forward matrix by the inverse. Result
            // should be identity
            // printMatrix(output, xxx+" Result of matrix mul with its
            // inverse: missing = "+n_missing);
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Time to test 1000000 inverses = "+
                           (endTime - startTime));
    }
    */

    private static void testNative() {
        int numData = 3;
        int numParity = 2;
        int fragSize = 1024;
        byte[] bytes = new byte[fragSize];
        Arrays.fill(bytes, (byte)1);

        ByteBuffer[] dataFragments = new ByteBuffer[numData];
        for (int i=0; i<numData; i++) {
            dataFragments[i] = ByteBuffer.allocateDirect(fragSize);
            dataFragments[i].put(bytes);
            dataFragments[i].flip();
        }

        ByteBuffer[] parityFragments = new ByteBuffer[numParity];
        for (int i=0; i<numParity; i++) {
            parityFragments[i] = ByteBuffer.allocateDirect(fragSize);
        }

        GFLinearAlgebra gfla =
            new GFLinearAlgebra(numParity,
                                numData,
                                GFLinearAlgebra.VANDERMONDE);
        gfla.calculateParityFragments(dataFragments, 
                                      parityFragments,
                                      fragSize);
    }

    public static void testJava() {
        int numData = 5;
        int numParity = 3;
        int fragSize = 1024;
        byte[] bytes = new byte[fragSize];
        Arrays.fill(bytes, (byte)1);

        ByteBuffer dataFragments = ByteBuffer.allocate(numData*fragSize);

        for (int i=0; i<numData; i++) {
            dataFragments.put(bytes);
        }
        dataFragments.flip();

        GFLinearAlgebra gfla =
            new GFLinearAlgebra(numParity,
                                numData,
                                GFLinearAlgebra.VANDERMONDE);
        ByteBuffer parityFragments =
            gfla.generateParityFragsFast(dataFragments, fragSize);
        System.out.println("Parity size = " + parityFragments.remaining());
    }

    private static void usage() {
        System.out.println("java Main <input file> " +
                           "--mode [generate|calculate]");
        System.exit(1);
    }

    private static ByteBuffer[] allocateBuffers(int num, int size) {
        ByteBuffer[] buffers = new ByteBuffer[num];
        for (int i=0; i<num; i++) {
            buffers[i] = ByteBuffer.allocateDirect(size);
        }

        return buffers;
    }

    private static void clearBuffers(ByteBuffer[] buffers) {
        for (int i=0; i<buffers.length; i++) {
            buffers[i].clear();
        }
    }

    private static void flipBuffers(ByteBuffer[] buffers) {
        for (int i=0; i<buffers.length; i++) {
            buffers[i].flip();
        }
    }

    private static void pad(ByteBuffer[] buffers) {
        for (int i=0; i<buffers.length; i++) {
            while(buffers[i].hasRemaining()) {
                buffers[i].put((byte)0);
            }
        }
    }

    private static FileOutputStream[] openOutputStreams(int num,
                                                        String prefix) {
        FileOutputStream files[] = new FileOutputStream[num];
        try {
            for (int i=0; i<num; i++) {
                files[i] = new FileOutputStream(prefix + i);
            }
        } catch (Exception e) {
            System.out.println("Failed to open file" + e);
            System.exit(1);
        }

        return files;
    }

    private static void writeFiles(ByteBuffer[] buffers,
                                   FileOutputStream[] streams)
        throws Exception{
        for (int i=0; i<streams.length; i++) {
            streams[i].getChannel().write(buffers[i]);
        }
    }

    private static void closeFiles(FileOutputStream[] streams)
        throws Exception{
        for (int i=0; i<streams.length; i++) {
            streams[i].close();
        }
    }

    public static void testFileCalculate(String inputFileName)
        throws Exception {
        int numData = 5;
        int numParity = 3;
        int fragSize = 64*1024;

        GFLinearAlgebra gfla =
            new GFLinearAlgebra(numParity,
                                numData,
                                GFLinearAlgebra.VANDERMONDE);

        // Open the input file
        FileInputStream inFile = null;
        try {
            inFile = new FileInputStream(inputFileName);
        } catch (Exception e) {
            System.out.println("Failed to open input file " + inputFileName);
            System.exit(1);
        }

        /* DO NOT WRITE ANY OUTPUT FOR NOW
        // Open all the data and parity fragment files
        FileOutputStream dataFiles[] = openOutputStreams(numData,"data");
        FileOutputStream parityFiles[] = openOutputStreams(numParity,"parity");
        */

        // Allocate buffers for the data and parity fragments
        ByteBuffer[] dataFragments = allocateBuffers(numData, fragSize);
        ByteBuffer[] parityFragments = allocateBuffers(numParity, fragSize);

        // Read the input file and generate the data and parity fragments
        long bytesRead = 0;
        long totalBytesRead = 0;
        long before = System.currentTimeMillis();
        while (true) {
            clearBuffers(dataFragments);
            if ((bytesRead = inFile.getChannel().read(dataFragments)) == -1) {
                break;
            }
            if (bytesRead != numData*fragSize) {
                pad(dataFragments);
            }
            totalBytesRead += numData*fragSize;

            flipBuffers(dataFragments);

            clearBuffers(parityFragments);
            gfla.calculateParityFragments(dataFragments,
                                          parityFragments, 
                                          fragSize);

            /*
            writeFiles(dataFragments, dataFiles);
            writeFiles(parityFragments, parityFiles);
            */
        }
        long after = System.currentTimeMillis();

        /*
        closeFiles(dataFiles);
        closeFiles(parityFiles);
        */

        System.out.println("Throughput = " + totalBytesRead/(after-before) +
                           " KB/sec");
    }

    private static ByteBuffer[] slice(ByteBuffer buffer, int size) {
        ByteBuffer[] buffers = new ByteBuffer[buffer.remaining()/size];

        for (int i=0; i<buffers.length; i++) {
            buffers[i] = buffer.duplicate();
            buffers[i].position(i*size);
            buffers[i].limit((i+1)*size);
        }

        return buffers;
    }

    public static void testFileGenerate(String inputFileName)
        throws Exception {
        int numData = 5;
        int numParity = 3;
        int fragSize = 64*1024;

        GFLinearAlgebra gfla =
            new GFLinearAlgebra(numParity,
                                numData,
                                GFLinearAlgebra.VANDERMONDE);

        // Open the input file
        FileInputStream inFile = null;
        try {
            inFile = new FileInputStream(inputFileName);
        } catch (Exception e) {
            System.out.println("Failed to open input file " + inputFileName);
            System.exit(1);
        }

        // Open all the data and parity fragment files
        FileOutputStream dataFiles[] = openOutputStreams(numData,"data");
        FileOutputStream parityFiles[] = openOutputStreams(numParity,"parity");

        // Allocate buffers for the data and parity fragments
        ByteBuffer dataFragments[] = new ByteBuffer[1];
        dataFragments[0] = ByteBuffer.allocate(numData*fragSize);

        // Read the input file and generate the data and parity fragments
        long bytesRead = 0;
        long totalBytesRead = 0;
        long before = System.currentTimeMillis();
        while (true) {
            clearBuffers(dataFragments);
            if ((bytesRead = inFile.getChannel().read(dataFragments)) == -1) {
                break;
            }
            if (bytesRead != numData*fragSize) {
                pad(dataFragments);
            }
            totalBytesRead += numData*fragSize;

            flipBuffers(dataFragments);

            ByteBuffer parityFragment =
                gfla.generateParityFragsFast(dataFragments[0], fragSize);

            /*
            writeFiles(slice(dataFragments[0], fragSize), dataFiles);
            writeFiles(slice(parityFragment, fragSize), parityFiles);
            */
        }
        long after = System.currentTimeMillis();

        closeFiles(dataFiles);
        closeFiles(parityFiles);

        System.out.println("Throughput = " + totalBytesRead/(after-before) +
                           " MB/sec");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            usage();
        }

        //test1();
        //testNative();
        //testJava();

        if (args[2].equals("calculate")) {
            System.out.println("Using calculate...");
            testFileCalculate(args[0]);
        } else {
            System.out.println("Using generate...");
            testFileGenerate(args[0]);
        }
    }
}

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



import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

public class HCIOTest {
    private static void usage() {
        System.out.println("NAME");
        System.out.println("       HCIOTest - loop writing/reading/comparing data to/from files ");
        System.out.println("       mimicing Honeycomb's IO patterns");
        System.out.println();
        System.out.println("SYNOPSIS");
        System.out.println("       java HCIOTest Tempfile Permfile Size");
        System.err.println();
        System.out.println("DESCRIPTION");
        System.out.println("       HCIOTest takes three parameters:");
        System.out.println("         the complete path of the temporary file to be written");
        System.out.println("         the complete path of the permanent file");
        System.out.println("         the size of the file which must be a multiple of 4096");
        System.out.println();
        System.out.println("EXAMPLES");
        System.out.println("       java HCIOTest /data/tmp/tempfile0 /data/testfile0 102400");
        System.exit(1);
    }

    public static void main(String [] argv) {
        if (argv.length != 3) {
            usage();
        }

        String tempFile = argv[0];
        String testFile = argv[1];
        int fileSize = Integer.valueOf(argv[2]).intValue();
        int exitcode = 0;
        int numRead;
        int numThisBuf;
        int numWritten;

        if ((fileSize <= 0) || (fileSize%4096 != 0)) {
            System.out.println("Error: size is not a multiple of 4096!!!!!!");
            System.out.println();
            usage();
        }

        try {
            int bufSize = 4096;
            byte[] inBytes = new byte[bufSize];
            byte[] outBytes = new byte[bufSize];
            Random ioRandom = new Random(2006);
            ioRandom.nextBytes(outBytes);
            ByteBuffer inBuf = ByteBuffer.allocate(bufSize);
            ByteBuffer outBuf = ByteBuffer.wrap(outBytes);

            //
            // Loop forever
            //
            while (true) {
                //
                // Write the temporary file
                //
                FileOutputStream fos = new FileOutputStream(tempFile);
                FileChannel foc = fos.getChannel();
                numWritten = 0;
                while (numWritten < fileSize) {
                    outBuf.clear(); //sets limit to capacity & position to zero
                    while (outBuf.hasRemaining()) {
                        numWritten += foc.write(outBuf);
                    }
                }
    
                //
                // Move to permanent location
                //
                FileChannel srcChannel = new FileInputStream(tempFile).getChannel();
                FileChannel dstChannel = new FileOutputStream(testFile).getChannel();
                dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
                srcChannel.close();
                dstChannel.close();
                boolean success = (new File(tempFile)).delete();
                if (!success) {
                    System.out.println("Warning: unable to delete temporary file");
                }
    
                //
                // Read and compare
                //
                FileInputStream fis = new FileInputStream(testFile);
                FileChannel fic = fis.getChannel();
    
                for (numRead=0,numThisBuf=0; numRead<fileSize; numThisBuf = 0) {
                    inBuf.rewind();     // Set the buffer position to 0
                    numThisBuf = fic.read(inBuf);
                    while (numThisBuf < bufSize) {
                        numThisBuf += fic.read(inBuf);
                    }
                    numRead += bufSize;
                    inBuf.rewind();     // Set the buffer position to 0
                    inBuf.get(inBytes);
                    boolean same = Arrays.equals(inBytes, outBytes);
                    if (same = false) {
                       System.out.println("Data read does not equal data written at "
                                            + numRead + " bytes");
                    }
                }
            }

        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace(System.err);
            exitcode = 1;
            // break;
        } catch (SecurityException se) {
            se.printStackTrace(System.err);
            exitcode = 1;
            // break;
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            exitcode = 1;
        }

        System.exit(exitcode);

    }
}

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



package com.sun.honeycomb.test.util.fragexplorer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;





class FragExplorer {
    public static void main(String[] args) {
        int offset=0;
        FragExplorer explorer=new FragExplorer();
        if(args.length  < 1) {
            System.out.println("Gimmie a path, dude.");
            System.exit(1);
        }
        
        if (args[offset].equals("-h")) {
            offset++;
            System.out.println("hey, we have help!");
        }
        
        if (args[offset].equals("-v")) {
            offset++;
            explorer.verboseLogging = true;
        }
        
        for(int i=offset;i<args.length;i++) {
            File rootFile = new File(args[i]);
            explorer.print(rootFile);
        }
    }



    public static boolean verboseLogging = false;
    private File root;

    private void print(final File fileOrDirectory) {
        boolean isDirectory = fileOrDirectory.isDirectory();
        String path = null;

        try {
            path = fileOrDirectory.getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException("failed to get canonical path for directory " +
                                       fileOrDirectory);
        }

        if (verboseLogging) {
            System.out.println(((isDirectory) ? "" : "    ") +
                               "loading " +
                               path);
        }


        if (isDirectory) {
            loadDirectory(fileOrDirectory);
        } else {
            System.out.println(fileOrDirectory.toString() +
                               "," +
                               fileOrDirectory.length() +
                               "," +
                               fileOrDirectory.lastModified());
        }


        if (verboseLogging && isDirectory) {
            System.out.println("done with " + path);
        }
    }

    private void loadDirectory(final File directory) {
        File[] contents = directory.listFiles();
        if (contents != null) {
            for (int i = 0; i < contents.length; i++) {
                print(contents[i]);
            }
        }
    }


};


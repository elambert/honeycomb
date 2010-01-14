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



package com.sun.honeycomb.hctest.util;

import com.sun.honeycomb.test.util.*;

import java.io.File;
import java.io.FileReader;

/**
 *  Fetch and (minimally) parse records in a HC audit database.
 */
public class AuditParser {

    private String root;
    private FileLister fl;
    private char[] cbuf = new char[1024];

    public AuditParser(String root) throws HoneycombTestException {
        this.root = root;
        fl = new FileLister(root);
    }

    private String readFile(File f) throws HoneycombTestException {

        try {
            int length = (int)f.length();

            if (length > cbuf.length)
                cbuf = new char[length];

            FileReader fr = new FileReader(f);
            int l = fr.read(cbuf, 0, length);
            fr.close();
            if (l != length)
                throw new HoneycombTestException("unexpected read (" + l + 
                                             "/" + length + ") " + f.getPath());
            return new String(cbuf, 0, (int)length);
         } catch (Exception e) {
             throw new HoneycombTestException(e);
         }
    }

    /**
     *  Get contents of next non-addmd file, null if done.
     */
    synchronized public String next() throws HoneycombTestException {

        while (true) {
            File f = fl.nextFile();
            if (f == null)
                return null;

            String s = readFile(f);
            //
            //  leave 'addmd' oids to the 'root' oid checking
            //
            if (!s.startsWith(HoneycombTestConstants.AUDIT_ADD_MD))
                return f.getName() + "\t" + s;
        }
    }

    /**
     *  Count the files under the root dir - could take a while!
     */
    public int countFiles() throws HoneycombTestException {
        FileLister fl = new FileLister(root);
        int count = 0;
        while (fl.nextFile() != null)
            count++;
        return count;
    }

    /**
     *  Trim the passed-in number of threads if there are fewer files.
     */
    public int fileThreads(int nthreads) throws HoneycombTestException {
        if (nthreads == 0)
            return 0;
        FileLister fl = new FileLister(root);
        int count = 0;
        while (fl.nextFile() != null) {
            count++;
            if (count == nthreads) {
                // more than enough files for threads
                return nthreads;
            }
        }
        // fewer files than threads so cut back threads
        return count;
    }

    /**
     *  Get contents of oid file.
     */
    public String getOidRecord(String oid) throws HoneycombTestException {
        String filename = root + HCUtil.getHashPath(oid);
        return readFile(new File(filename));
    }

    /** main is for test */
    public static void main(String args[]) {
        try {
            FileLister fl = new FileLister(args[0]);
            File f;
            while ((f=fl.nextFile()) != null)
                System.out.println(f.toString());
/*
            AuditParser ap = new AuditParser(args[0]);
            String s;
            while ((s = ap.next()) != null)
                System.out.println(s);
*/
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}


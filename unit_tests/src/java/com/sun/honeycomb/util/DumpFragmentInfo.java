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



/*
 * Utility to print the system metadata info and footer info from a frag file.
 *
 * You can invoke it like this on a cluster node:
 *

java -classpath <PATH_TO_UNIT_TEST_JAR>/honeycomb-utests.jar:/opt/honeycomb/lib/honeycomb.jar:/opt/honeycomb/lib/honeycomb-server.jar:/opt/honeycomb/lib/honeycomb-common.jar:/opt/honeycomb/lib/jug.jar -Djava.library.path=/opt/honeycomb/lib/ com.sun.honeycomb.util.DumpFragmentInfo FullFragmentFilePath [FullFragmentFilePath2 ...]

 */

package com.sun.honeycomb.util;

import java.io.File;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.common.SystemMetadata;

public class DumpFragmentInfo {
    // The basis for this code is in oa/Crawl.java
    public static void main(String args[]) {
        File f = null;

        if (args.length < 1) {
            usage();
        }

        // iterate through all frag paths and try to dump their info
        for (int i = 0; i < args.length; i++) {
            try {
                f = new File(args[i]);
                FragExplorer.FragmentFileSubclass ffs
                    = new FragExplorer.FragmentFileSubclass(f);
                SystemMetadata sm = ffs.readSystemMetadata();

                System.out.println("--- System Metadata for " + f + " ---");
                System.out.println(sm);
                System.out.println("--- Footer for " + f + " ---");
                System.out.println((ffs.getFragmentFooter()).toString());
            } catch (com.sun.honeycomb.oa.DeletedFragmentException dfe) {
                System.out.println("\nOA says fragment " + f + " has been deleted");
            } catch (Throwable t) {
                t.printStackTrace();
                System.out.println("(skipping frag " + f + " due to an exception)");
            }

            System.out.println("\n");
        }
    }

    public static void usage() {
        System.out.println("Usage: DumpMetadata fragfile [fragfile2 ...]");
        System.exit(1);
    }
}

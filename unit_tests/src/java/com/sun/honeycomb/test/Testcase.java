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
 * A placeholder to accumulate functionality needed by all tests.
 */

package com.sun.honeycomb.test;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.File;
import java.util.logging.Logger;
import java.util.logging.LogManager;
import java.util.logging.FileHandler;
import java.util.logging.ConsoleHandler;
import java.util.logging.SimpleFormatter;

public class Testcase extends TestCase {
    private static Logger log;
    private static final String BASE_DIR
        = System.getProperty("oa.upgrade.basedir", ".");
    private static final String CONFIG_DIR = BASE_DIR + "/share";
    private static final String LOG_DIR = BASE_DIR + "/logs";
    private static final String LOG_FILE = LOG_DIR + "/test.log";
    static {
        log = Logger.getLogger(Testcase.class.getName());
        try {
            new File(LOG_DIR).mkdir();
            File file = new File(LOG_FILE);
            if (file.exists()) {
                file.renameTo(new File(LOG_FILE + "." + System.currentTimeMillis()));
            }
            FileHandler fh = new FileHandler(LOG_FILE);
            fh.setFormatter(new SimpleFormatter());
            Logger.getLogger("").addHandler(fh);
        } catch (IOException ioe) {
            log.severe(ioe.getMessage());
        }
    }


    /**********************************************************************/
    protected void setUp() throws Exception {
        super.setUp();
        System.setProperty("honeycomb.config.dir", CONFIG_DIR);
    }

    /**********************************************************************/
    public Testcase(String name) {
        super(name);
    }
}

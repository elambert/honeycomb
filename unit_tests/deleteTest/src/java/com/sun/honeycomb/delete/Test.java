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



package com.sun.honeycomb.delete;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.oa.Common;

import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.io.IOException;
import java.io.File;
import java.util.logging.Level;
import java.io.FileReader;
import bsh.Interpreter;

public class Test {

    private static Logger LOG;

    private static void setupLog() 
        throws IOException {
        Logger rootLogger = Logger.getLogger("");
        
        Handler[] hdlers = rootLogger.getHandlers();
        for (int i=0; i<hdlers.length; i++) {
            rootLogger.removeHandler(hdlers[i]);
        }

        File logDir = new File(Constants.getRootDir()+"/logs");
        if (!logDir.exists()) {
            logDir.mkdir();
        }
        String logFile = logDir.getAbsolutePath()+"/deleteFmk.log";

        FileHandler fileHandler = null;

        fileHandler = new FileHandler(logFile,
                                      true);

        fileHandler.setFormatter(new SimpleFormatter());
        rootLogger.addHandler(fileHandler);
        
        LOG = Logger.getLogger(Test.class.getName());
    }

    private static void mkdir(String diskRoot,
                              String relativePath) {
        File f = null;

        if (relativePath == null) {
            f = new File(diskRoot);
        } else {
            f = new File(diskRoot+"/"+relativePath);
        }
        if (!f.exists()) {
            f.mkdir();
        }
    }

    // used to reinitialize the disks 
    public static void setupDisks()
        throws IOException {
        String diskRoot = Constants.getDiskRoot();
        
        mkdir(diskRoot, null);

        for (int i=0; i<Constants.NB_DISKS; i++) {
            String localRoot = diskRoot+"/"+i;

            mkdir(localRoot, null);
            mkdir(localRoot, Common.closeDir);
        }

        LOG.info("Virtual disks have been setup");
    }

    private static void setupOA() {
	// These are defaults - tests may change them
        OAClient oac = OAClient.getInstance();
	Constants.MAX_CHUNK_SIZE = oac.blockSize;
        oac.setMaxChunkSize(Constants.MAX_CHUNK_SIZE);
        Constants.reliability = oac.getReliability();
        Constants.MAX_FRAG_ID = Constants.reliability.getTotalFragCount() - 1;
    }

    private static void usage() {
        System.out.println("Usage :\n"+
                           "run.sh [--verbose] <scenario file> [ <cluster IP> ]\n\n"+
                           "If the cluster IP is not specified, the test will run in local OA emulation mode\n"+
                           "NOTE: cluster mode is not yet supported\n");
        System.exit(1);
    }

    public static void main(String[] args) {
        boolean verbose = false;

        if ((args.length < 1) || (args.length > 3)) {
            usage();
        }

        String scen = null;
        String clusterIP = null;

        if (args[0].equals("--verbose")) {
            verbose = true;
            if (args.length > 1) {
                scen = args[1];
            } else {
                usage();
            }
            if (args.length > 2) {
                clusterIP = args[2];
            }
        } else {
            scen = args[0];
            if (args.length > 1) {
                clusterIP = args[1];
            }
         }

        File scenario = new File(scen);
        if (clusterIP != null) {
            System.out.println("Cluster mode not yet supported");
            usage();
        }

        FileReader input = null;
        OpCodeOutput output = null;
        OpCode currentOpCode = null;
        
        // This property can later be used by unit tests to localize where the
        // scenarii directory is located.
        String scriptDir = scenario.getParent();
        System.setProperty("deletefmk.script.dir", scriptDir);
        
        try {
            setupLog();
            OIDTagTable.init();
            LOG.info("----- STARTING SCENARIO " + scenario + " -----");
            LOG.info("The delete Fmk is starting");
            
            input = new FileReader(scenario);
            setupDisks();
            setupOA();
            OpCode.init(new DeleteFmkFactory(verbose), new OpCodeOutput());
            
            Interpreter interpreter = new Interpreter();
            interpreter.getNameSpace().importStatic(BshOpCode.class);

            System.out.println("Setup is done. Test is starting");

            interpreter.eval(input);
            
            LOG.info("Test passed.");
            System.out.println("\nTest passed.");
            LOG.info("The deleteFmk is exiting ...");
            
        } catch (Exception orige) {
            Throwable e = orige;
            if (orige instanceof bsh.TargetError) {
                e = ((bsh.TargetError)orige).getTarget();
            }
            e.printStackTrace();
            if (LOG != null) {
                LOG.log(Level.SEVERE,
                        "PANIC !!!",
                        e);
            }
            System.exit(1);
        } finally {
            OIDTagTable.destroy();
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {}
            }
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {}
            }
        }
        
        System.exit(0);
    }
}

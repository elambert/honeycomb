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



package com.sun.honeycomb.hctest.cli;

import com.sun.honeycomb.hctest.cli.HwStat;
import com.sun.honeycomb.hctest.util.HCUtil;

import com.sun.honeycomb.test.util.Log;

import java.util.StringTokenizer;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.BufferedReader;

/**
 ** A class to interface with the CLI.
 ** A precondition is that non-interactive ssh keys are
 ** already setup.  This is true for root on any of the
 ** client machines (clXX).
 **
 **/
public class CLI
{
    private String adminVIP;
    
    public CLI(String adminVIP)
    {
        this.adminVIP = adminVIP;
    }

    public BufferedReader runCommand(String command)
        throws Throwable
    {
        Log.INFO("CLI::runCommand(" + command + ")");
        String [] args = new String [] {
            "/usr/bin/ssh",
            "-o",
            "StrictHostKeyChecking=no",
	    "-o",
            "BatchMode=yes",
            "-o",
            "CheckHostIP=no",
            "admin@" + adminVIP,
            command
        };
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec(args);
        InputStream stdout = process.getInputStream();
        return new BufferedReader(new InputStreamReader(stdout));
    }


    /**
     * As above, but check for error status and log
     * stderr.  Note that we wait for the process to
     * exit, so if a log of output is produced, this can
     * cause a problem.  
     *
     * The HADB State Machine tests use this, but most do not.  Sometimes
     * hangs are observed as a result of using this interface.
     */
    public BufferedReader runCommandLogErrors(String command,
        boolean failOnError)
        throws Throwable
    {
        Log.INFO("CLI::runCommandLogErrors(" + command + ")");
        String [] args = new String [] {
            "/usr/bin/ssh",
            "-o",
            "StrictHostKeyChecking=no",
            "-o",
            "BatchMode=yes",
            "-o",
            "CheckHostIP=no",
            "admin@" + adminVIP,
            command
        };
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec(args);

        Log.INFO("Waiting for process " + command);
        // NOTE: this could be dangerous if process produces a lot
        // of output.  
        process.waitFor();

        // Check the return code.  This might help understand why some
        // things like reboot aren't behaving properly.
        int rc = process.exitValue();

        // We don't account for stderr in the caller. Print it for reference.
        InputStream stderr = process.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(stderr));
        try {
            String s = br.readLine();
            Log.INFO("Begin stderr for cmd " + command);
            while (s != null) {
                Log.ERROR("stderr: " + s);
                s = br.readLine();
            }
            Log.INFO("End stderr for cmd " + command);
        } catch (IOException ioe) {
            Log.WARN("Unable to retrieve error output from command." +
                " IOException during read" +
                ioe.getMessage());
        } finally {
            try {
                if (br != null)
                    br.close();
            } catch (Throwable ignore) {}
        }

        // Check for exit status, and exit if we were asked to
        if (rc != 0) {
            Log.ERROR("Error: got non-zero exit code " + rc + " for cmd " +
                command);
            if (failOnError) {
                throw new RuntimeException("command " + command + " failed");
            } else {
                Log.INFO("test will continue despite error");
            }
        } else {
            Log.INFO("Success: cmd " + command + " returned " + rc);
        }

        InputStream stdout = process.getInputStream();
        return new BufferedReader(new InputStreamReader(stdout));
    }

    // Pass the delimiter as parameter
    private String [] tokenizeIt(String string, String delimiter) 
                                                     throws Exception {
        StringTokenizer st = new StringTokenizer(string, delimiter);
        String [] tokenized;
        tokenized = new String[ st.countTokens()];
        int mctr = 0;
    
        while (st.hasMoreTokens()) {
            tokenized[mctr] = st.nextToken().trim();
            mctr++;
        }
         
        return tokenized ;
    } 

    
    public boolean verifyCommandStdout(String command, String [] lsExpOutput) {

        boolean isCorrectStdout = false;

        try {
            String line = null;
            BufferedReader output = runCommand(command);
            String outputStr = HCUtil.readLines(output);
            String [] lsActOutput = tokenizeIt(outputStr, "\n");

            if (lsActOutput.length != lsExpOutput.length) {
                Log.WARN("Unexpected output from command <" +
                        command + ">: " + outputStr);
                return false;
            }

            for (int lineNo=0; lineNo<lsActOutput.length; lineNo++) {
                line = lsActOutput[lineNo].trim();
                Log.DEBUG(command + " output : " + line);

                if (!line.trim().equals(lsExpOutput[lineNo].trim())) {
                    Log.WARN(command + " command: " + "# Actual output: <" +
                            line + ">, Expected output: <" + lsExpOutput[lineNo] + ">");
                    isCorrectStdout = false;
                }
                else {
                    Log.DEBUG("Correct output: " + line);
                    if ((lineNo == 0) || (isCorrectStdout))
                        isCorrectStdout = true;
                }
            }

            if (!isCorrectStdout)
                Log.ERROR("Unexpected output from <" + command + "> command");
            else
                Log.INFO("Output of <" + command + "> command is displayed properly");

        } catch (Throwable t) {
            Log.ERROR("IO Error accessing CLI:" + t.toString());
        }

        return isCorrectStdout;
    }
    
    public HwStat hwstat() 
        throws Throwable
    {
        return new HwStat(runCommand("hwstat"));
    }
    
    public HwStat hwstat(int cellId) 
        throws Throwable
    {
        return new HwStat(runCommand("hwstat" + 
                HoneycombCLISuite.CELL_ID_ARG + cellId));
    }

    public static void main(String [] args)
        throws Throwable
    {
        String adminVIP = args[0];
        String command = args[1];
        CLI cli = new CLI(adminVIP);
        System.out.print(HCUtil.readLines(cli.runCommand(command)));
    }
}

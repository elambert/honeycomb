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



package com.sun.honeycomb.emd.config;

import java.io.FileInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;

public class MergeConfig {

    ///Utility class -- should never get instantiated
    private MergeConfig() {}

    private static void usage(String msg) {
        if (msg != null) {
            System.err.println("Error: "+msg+"\n");
        }

        System.err.println("Arguments: filename\n"+
                           "\twhere :\n"+
                           "\tfilename is the name of the file containing the config to be added"+
                           "\n");
        System.exit(1);
    }

    public static void main(String[] arg) {
        if (arg.length != 1) {
            usage("Bad number of arguments ["+arg.length+"]");
        }
        File configFile = new File(arg[0]);
        if (!configFile.exists()) {
            usage("The file ["+
                  configFile.getAbsolutePath()+"] does not exist");
        }

        FileInputStream stream = null;
        Writer out = null;
        String emulatorRoot = System.getProperty("emulator.root");

        try {
            RootNamespace rootNamespace = RootNamespace.getInstance();
            stream = new FileInputStream(configFile);
            rootNamespace.readConfig(stream, false);
            rootNamespace.validateSchema();

            if (emulatorRoot == null) {
                out = new BufferedWriter(
                          new OutputStreamWriter(
                              new FileOutputStream(RootNamespace.userConfig),
                          "UTF-8"));
                rootNamespace.export(out, false);
            } else {
                out = new BufferedWriter(
                          new OutputStreamWriter(
                              new FileOutputStream(emulatorRoot+"/"+RootNamespace.emulatorUserConfig),
                          "UTF-8"));
                rootNamespace.export(out, true);
            }
        } catch (IOException e) {
            System.out.println("The merge failed \""+
                               e.getMessage()+"\"");
            //            e.printStackTrace();
        } catch (EMDConfigException e) {
            System.out.println("The merge failed \""+
                               e.getMessage()+"\"");
            //            e.printStackTrace();
        } finally {
            if (out != null) {
                try { out.close(); }  catch (IOException e) {}
                out = null;
            }
            if (stream != null) {
                try { stream.close(); } catch (IOException e) {}
                stream = null;
            }
        }
    }
}

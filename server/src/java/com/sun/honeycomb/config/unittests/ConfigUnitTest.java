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



package com.sun.honeycomb.config.unittests;

import com.sun.honeycomb.config.*;
import java.util.*;
import java.util.logging.*;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;

public class ConfigUnitTest {
    static {
        // If the property isn't set, use some crazy default
        if (System.getProperty (ClusterProperties.PROP_CONFIG_DIR) == null) {
            System.setProperty ("honeycomb.config.dir", "test_config");
        }
        Logger.getLogger (ClusterProperties.class.getName()).setLevel (Level.ALL);
    }

    protected static ClusterProperties props = ClusterProperties.getInstance();

    public static void main (String[] args) {


        ConfigUnitTest app = new ConfigUnitTest();

        if (args.length == 0) {
            app.showAll();
        }

        else if (args.length == 1) {
            if (args[0].equals ("show")) {
                app.showAll();
            } else {
                app.usage();
            }
        }

        else if (args.length == 2) {
            if (args[0].equals ("show")) {
                app.show (args[1]);
            }
            else {
                app.usage();
            }
        }

        else if (args.length == 3) {
            if (args[0].equals ("set")) {
                try {
                    app.set (args[1], args[2]);
                } catch (ServerConfigException e) {
                    System.out.println("Failed - got server config exception");
                    return;
                }
            } else {
                app.usage();
            }
        }

        else {
            app.usage();
        }

    }

    public void usage () {
        System.out.println ("ConfigUnitTest: display or set config properties");
        System.out.println ();
        System.out.println ("ConfigUnitTest show");
        System.out.println ("ConfigUnitTest set [name] [value]");
    }

    protected void showAll () {
        System.out.println ("Properties: ");
        for (Enumeration e = props.propertyNames(); e.hasMoreElements(); ) {
            String name = (String) e.nextElement();
            System.out.println (name + ": " + props.getProperty (name));
        }
    }

    protected void show (String name) {
        if (! props.isDefined (name)) {
            System.out.println (name + ": not defined");
            return;
        }
        System.out.println (name + ": " + props.getProperty (name));
    }

    protected void set (String name, String value) throws ServerConfigException{
        props.put(name, value);
    }
}

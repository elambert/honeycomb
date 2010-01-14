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



package com.sun.honeycomb.config;

import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;
import java.io.FileInputStream;
import util.Constants;

public class ClusterProperties
    extends Properties {

    private static ClusterProperties singleton = null;

    private static final String CONFIG_FILE = "/config/config_defaults.properties";
    private static final Logger LOG = Logger.getLogger(ClusterProperties.class.getName());

    public static synchronized ClusterProperties getInstance(boolean register) {
        if (singleton == null) {
            singleton = new ClusterProperties();
            try {
                singleton.init();
            } catch (IOException e) {
                LOG.log(Level.SEVERE,
                        "Failed to get the honeycomb simulator properties ["+
                        e.getMessage()+"]",
                        e);
                singleton = null;
            }
        }
        return(singleton);
    }

    private ClusterProperties() {
        super();
    }

    private void init()
        throws IOException {
        FileInputStream input = null;

        try {
            input = new FileInputStream(Constants.getRootDir()+CONFIG_FILE);
            load(input);
        } finally {
            if (input != null) {
                input.close();
            }
        }
    }

    public boolean isDefined(String prop) {
        return(getProperty(prop) != null);
    }

    public boolean isDefaulted(String prop) {
        return(true);
    }

    public long getVersion () {
        return 1;
    }

    public String getMD5Sum () {
        return(null);
    }
}

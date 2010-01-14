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



package config;

import java.util.prefs.Preferences;
import java.io.File;

public class Config {
    private static final String CLUSTER_IP      = "clusterIP";
    private static final String LAST_DIRECTORY  = "lastDirectory";

    private static Preferences prefs =
        Preferences.userRoot().node("honeycomb/mp3UploadApp");

    public static String getClusterIP() {
        return(prefs.get(CLUSTER_IP, null));
    }

    public static void setClusterIP(String clusterIP) {
        if (clusterIP == null) {
            return;
        }
        prefs.put(CLUSTER_IP, clusterIP);
    }

    public static File getLastDirectory() {
	String location = prefs.get(LAST_DIRECTORY, null);
	File file = null;
	if (location != null) {
	    file = new File(location);
	}
	return(file);
    }

    public static void setLastDirectory(File lastDirectory) {
	if (!lastDirectory.isDirectory()) {
	    lastDirectory = lastDirectory.getParentFile();
	}
	prefs.put(LAST_DIRECTORY, lastDirectory.getAbsolutePath());
    }

    public static boolean isValid() {
        return(getClusterIP() != null);
    }
}

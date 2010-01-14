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



package com.sun.honeycomb.admingui.present;


import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.UIManager;

public final class Main {

    private static final Logger LOGGER = 
        Logger.getLogger( Main.class.getName() );
    
    private Main() {
    }

    /**
     * Main routine of the GUI application.
     */
    public static void main(final String[] args) throws Exception {
        try {
            String clsName = UIManager.getSystemLookAndFeelClassName();
            if (!clsName.endsWith("WindowsLookAndFeel"))
                clsName = UIManager.getCrossPlatformLookAndFeelClassName();
                // Use Windows L&F if on Windows, otherwise use cross-platform
            UIManager.setLookAndFeel(clsName);
            LOGGER.info("L&F set to: " + clsName);
        } catch(Exception e) {
            LOGGER.log(Level.WARNING, "Error setting Java LAF", e);
        }

        // Log application version
        Package p = Main.class.getPackage();
        String version =
                Main.class.getPackage().getSpecificationVersion() + "." +
                Main.class.getPackage().getImplementationVersion();
        LOGGER.info("Application version " + version);
        
        // AntiAliasHack.install();
        com.sun.nws.mozart.ui.MainFrame.main(args);
    }
    
    /**
     * Logs out information about the runtime environment.
     */
    public static void logEnvironment() {
        Properties props = new Properties();
        props.putAll( System.getProperties() );
        // Clone properties as we were seeing 
        // ConcurrentModificationExceptions
        StringBuilder sb = new StringBuilder();
        for (Iterator i = props.entrySet().iterator(); i.hasNext();) {
            Map.Entry entry = (Map.Entry) i.next();
            sb.append( entry.getKey() + ": " 
            + entry.getValue().toString().trim() + "\n" );
        }
        // Log.logInfoMessage( sb.toString() );
    }
}

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
 * HoneyCombCatchAllExceptionHandler.java
 *
 * Created on December 8, 2006, 9:09 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.honeycomb.admingui.present;

import com.sun.honeycomb.admingui.XmlRpcParamDefs;
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.CommException;
import com.sun.honeycomb.admingui.client.TimeoutException;
import com.sun.honeycomb.admingui.client.PermException;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.exceptions.CatchAllExceptionHandler;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import java.util.logging.Level;
import javax.swing.SwingUtilities;

/**
 *
 * @author ronaldso
 */
public class HoneycombCatchAllExceptionHandler
    extends CatchAllExceptionHandler {

    private static final HoneycombCatchAllExceptionHandler INSTANCE =
        new HoneycombCatchAllExceptionHandler();

    /** Creates a new instance of HoneyCombCatchAllExceptionHandler */
    private HoneycombCatchAllExceptionHandler() {
    }

    /** to refresh controls and clear cache */
    private void refreshContentAndClearCache(final boolean clearCache)  {
        // Use invokeLater() because this handler could catch exceptions
        // from any thread.
        SwingUtilities.invokeLater(
            new Runnable() {
                public void run() {
                    if (clearCache) {
                        ObjectFactory.clearCache();
                    }
                    try {
                        ExplorerItem ei = MainFrame.getMainFrame().getCurrentItem();
                        if (ei != null) {
                            String status = ei.getPanelState();
                            if (status != null &&
                                    status.equals(ExplorerItem.SAVE_STATE) &&
                                                        !ei.isErrorOnPanel()) {
	                        MainFrame.getMainFrame().refreshContentPanel();
		            }
                        }
                    } catch (Exception ex) {
                         Log.logToStatusAreaAndExternal(Level.SEVERE,
                                GuiResources.getGuiString(
                                 "error.posterrorrefresh"), null);
                        // Swallow this exception as rethrowing it leads
                        // to an endless loop.
                    }
                }
            });
    }

    public static HoneycombCatchAllExceptionHandler getInstance() {
        return INSTANCE;
    }

    // @Override
    // This method is called if an exception (hopefully runtime only) is not
    // caught anywhere
    public void handle(Throwable t) {
        try {
            // Dig to the ultimate cause of the exception
            while (t.getCause() != null) {
                t = t.getCause();
            }
            if (t instanceof PermException) {
                Log.logAndDisplay(
                            Level.SEVERE,
                            GuiResources.getMsgString(
                                "error.lostadminpermission"), null);
                refreshContentAndClearCache(false);
                return;
            } else if (t instanceof TimeoutException) {
                Log.logAndDisplay(
                    Level.INFO,
                    GuiResources.getMsgString("error.timeout"), null);
                ObjectFactory.clearCache();
                MainFrame.getMainFrame().showLoginPanel();
                return;
            } else if (t instanceof CommException) {
                Log.logAndDisplay(
                            Level.SEVERE,
                            GuiResources.getMsgString("error.connection"), t);
                // if a communication exception then shutdown application
                System.exit(1);
            } else if (t instanceof ClientException || 
                                t instanceof HostException) {
                Log.logAndDisplay(Level.SEVERE, 
                        GuiResources.getMsgString("error.client"), t);
                refreshContentAndClearCache(true);
                return;
            } else if (t instanceof UIException) {
                Log.logAndDisplay(Level.SEVERE, t.getMessage(), t);
                refreshContentAndClearCache(true);
                return;
            } else if (t instanceof ServerException) {
                // do not pass the throwable since we do not want the stack
                // trace to be printed to the external log file
                Log.logAndDisplay(Level.SEVERE, 
                        GuiResources.getMsgString("error.server"), null);
                refreshContentAndClearCache(true);
                return;
            } 
            String exMsg = (t.getMessage() != null)
                            ? t.getMessage()
                            : GuiResources.getGuiString("common.noneIndicator");

            // Build message
            String shortMsg = GuiResources.getMsgString(
                            "catchAllExceptionHandler.msg",
                            new String[] {t.getClass().getName(), exMsg});
            
            Log.logAndDisplay(Level.SEVERE, shortMsg, t);
            
            if (t instanceof RuntimeException) {
                ObjectFactory.clearCache();
                // do not want to continually loop through the exception by
                // trying to display the same page again
                refreshContentAndClearCache(false);
            } else {
                refreshContentAndClearCache(true);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            // Note: this is a major problem if we catch anything here - best
            // to dump to the screen as logging might not even be working. I
            // got stumped by a MissingResourceException being thrown above.
        }
    }
}

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
 * ObjectFactory.java
 *
 * Created on January 18, 2006, 11:48 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package com.sun.honeycomb.admingui.present;

import com.sun.honeycomb.admingui.XmlRpcParamDefs;
import com.sun.honeycomb.admingui.client.AdminApi;
import com.sun.honeycomb.admingui.client.AdminApiXML;
import com.sun.honeycomb.admingui.client.Cell;
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.Node;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.present.exploreritems.ExplItemCells;
import com.sun.honeycomb.admingui.present.panels.PnlConfigMetadataSchema;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.BaseActionCollection;
import com.sun.nws.mozart.ui.BaseExplorerModel;
import com.sun.nws.mozart.ui.BaseMenuBar;
import com.sun.nws.mozart.ui.BaseObjectFactory;
import com.sun.nws.mozart.ui.BaseToolBar;
import com.sun.nws.mozart.ui.utility.AsyncProxy;
import com.sun.nws.mozart.ui.utility.CacheOperations;
import com.sun.nws.mozart.ui.utility.CacheProxy;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.InstallCertificates;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.nws.mozart.ui.utility.TimerProxy;
import com.sun.nws.mozart.ui.utility.UserMessage;
import com.sun.honeycomb.admingui.present.actions.ActionsCollection;
import com.sun.honeycomb.admingui.present.actions.ToolBar;
import com.sun.honeycomb.admingui.present.exploreritems.ExplorerModel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.contentpanels.PnlLogin;
import com.sun.nws.mozart.ui.utility.CapacityValue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.JTree;

/**
 * This class can be used to obtain instances of objects when the application
 * does not know how to instantiate the object and instead should only deal
 * with an interface.  This is useful in running the app with different 
 * protocols on the back end or for running in demo mode.
 *
 * @author dp127224
 */
public class ObjectFactory extends BaseObjectFactory {

    private static String GUI_BUNDLE_NAME = 
            ObjectFactory.class.getPackage().getName() + ".resources.l10n_gui";
    private static String MSG_BUNDLE_NAME = 
            ObjectFactory.class.getPackage().getName() + 
                                                    ".resources.l10n_messages";
    private static String SETTINGS_BUNDLE_NAME = 
            ObjectFactory.class.getPackage().getName() + ".resources.settings";
    // Must be slash delimited and start with slash 
    private static String LOGGING_PROPERTIES_NAME = 
            "/" + ObjectFactory.class.getPackage().getName().replace('.', '/') +
            "/resources/logging.properties";
    private static String IMAGES_DIR = 
            "/" + ObjectFactory.class.getPackage().getName().replace('.', '/') +
            "/images";
    public static final String IP_ADDRESS_PATTERN = 
                                "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}";

    // TODO: "en" locale needs to be determined at runtime
    
    // using StringBuffer since StrBufs. are synchronized
    private static final StringBuffer HELPSET_NAME;
    static { HELPSET_NAME = new StringBuffer("en/hc_hlp.hs"); }

    public static final int CAPACITY_UNIT = CapacityValue.UNIT_MB;
    
    private static AdminApi adminApi = null;
    
    // the host is a combination of the hostname and host port
    private static String host = null;
    // the hostId is the hostname
    private static String hostId = null;
    private static String hostIPAddress = null;
    private static int hostPort = 0;
    private static int lastVisitedCell = -1;
    
    // flag to indicate whether or not the user has the write lock
    private static boolean fullAccess = false;
    // flag to indicate whether or not the user successfully logged in
    private static boolean validLogin = false;
    // flag to indicate whether or not there was an error when trying to 
    // remotely retrieve the cell information via getCells method -- true if
    // there was an error, false otherwise.
    private static boolean errGetCells = false;
    // flag to indicate whether or not the cell data for the ExplItemCells
    // tree node should be refreshed or not
    private static boolean refreshExplItemCells = false;
    
    public static boolean isMultiCell() {
        try {
            Cell[] cells = adminApi.getCells();
            int numCells = cells.length;
            if (numCells > 1) {
                return true;
            }
            // need to check further to determine if single cell or
            // multicell setup since there is only one cell in hive
            if (cells[0].getID() != 0) {
                Node[] nodes = adminApi.getNodes(cells[0]);
                if (nodes.length == AdminApi.FULL_CELL_NODES) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static boolean isGetCellsError() {
        return errGetCells;
    }
    public static void setGetCellsError(boolean err) {
        errGetCells = err;
    }
    public static boolean isRefreshExplItemCells() {
        return refreshExplItemCells;
    }
    public static void setRefreshExplItemCells(boolean refresh) {
        refreshExplItemCells = refresh;
    }
    public static boolean switchesOK() {
        String msg = "";
        if (!areAllCellSwitchesOK()) {
            if (isMultiCell()) {
                msg = GuiResources.getGuiString("switches.hive.error");
            } else {
                msg = GuiResources.getGuiString("switches.cell.error");
            }
        }
        if (msg.length() > 0) {
            JOptionPane.showMessageDialog(
                            MainFrame.getMainFrame(),
                            msg,
                            GuiResources.getGuiString("app.name"),
                            JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }
    private static boolean areAllCellSwitchesOK() {
        boolean allOK = true;
        try {
            Cell[] cells = adminApi.getCells();
            for (int idx = 0; idx < cells.length; idx++) {
                Cell c = cells[idx];
                if (!c.isAlive() || 
                        !adminApi.areSwitchesOk(cells[idx].getID())) {
                    allOK = false;
                    break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return allOK;
    }

    public static AdminApi getHostConnection() {
        return adminApi;
    }
    
    public static String getGUIServerIP() { 
        return hostIPAddress; 
    }
    
    public static int getLastCellId() { return lastVisitedCell; }
    
    public static void setLastCellId(int cellId) {
        lastVisitedCell = cellId;
    }

    // hard-coding this to true since there is no "read-only" mode -- do not
    // want to disable buttons etc. which check this value
    public static boolean isAdmin() { return true; };
    public static void setAdmin(boolean priv, boolean changeTreeLabel)
        throws UIException {
        fullAccess = priv;
//        if (!fullAccess && changeTreeLabel) {
//            changeModeStringInTree();
//        }
    }

    // Do not use this since there is no "read-only" mode - do not delete though
//    private static void changeModeStringInTree()
//        throws UIException {
//        ExplorerModel explorerModel = (ExplorerModel) getExplorerModel();
//        ExplItemSilo silo = (ExplItemSilo) explorerModel.getRoot();
//
//        // update window title
//        String windowTitle = MainFrame.getMainFrame().getTitle();
//        MainFrame.getMainFrame().setTitle(windowTitle.concat(" ").concat(
//            GuiResources.getGuiString("explorer.silo.readonly")));
//
//        // update tree root node label
//        String newLabel =
//            getGUIServerIP() == null ?
//                getHostName() : getGUIServerIP();
//        if (silo != null) {
//            silo.setLabel("<html>".concat(newLabel).concat("&nbsp;<b>")
//                    .concat(GuiResources.getGuiString("explorer.silo.readonly"))
//                    .concat("</b></html>"));
//            explorerModel.updateRootLabel();
//        }
//    }

    protected Object doInitHostIdentifier(Object id) throws UIException {
        // id passed in is the hostname for this application
        String hostname = (String)id;
        if (hostname.indexOf(":") != -1) {
            String[] hostInfo = hostname.split(":");
            hostId = hostInfo[0];
            hostPort = Integer.valueOf(hostInfo[1]).intValue();
            try {
                InetAddress addr = InetAddress.getByName(hostId);
                hostIPAddress = addr.getHostAddress();
                if (hostIPAddress != null) {
                    hostname = hostIPAddress;
                } else {
                    hostname = hostId;
                }
            } catch (Exception ex) {
                hostname = hostId;

            } 
        }
        return hostname;
    }

    /*
     * Initializes the admin api connection.  
     *
     * @param theHost Host name or IP of the STK58xxx box this app is managing.
     * Can be use in forming a URL to contact the host.
     */
    protected Object doInitHostConnection(Object theHost) throws UIException {
        host = (String)theHost;
        
        if (hostIPAddress == null) {
            doInitHostIdentifier(theHost);
        }

        // Download server certificates to avoid SSL exceptions
        InstallCertificates.host(hostId, hostPort);
        
        // Set up a catch-all exception handler
        System.setProperty("sun.awt.exception.handler", 
                           HoneycombCatchAllExceptionHandler.class.getName());
        AsyncProxy.registerExceptionHandler(
            HoneycombCatchAllExceptionHandler.getInstance());

        // Set up window listener for main frame
        MainFrame.getMainFrame().addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                try {
                    if (validLogin) {
                        // ensure that the user is logged out if 
                        // the window closes
                        String question = GuiResources
                                            .getMsgString("info.user.loggedOn");
                        String okText = GuiResources.getGuiString("button.ok");
                        String cancelText = GuiResources
                                                .getGuiString("button.cancel");
                        int retVal = JOptionPane.showOptionDialog(
                                MainFrame.getMainFrame(),
                                question,
                                GuiResources.getGuiString("app.name"),
                                JOptionPane.OK_CANCEL_OPTION,
                                JOptionPane.QUESTION_MESSAGE,
                                null, // Icon
                                new String[] { okText, cancelText },
                                cancelText);
                        if (retVal == JOptionPane.OK_OPTION) {
                            // log user out and exit
                            String msg = null;
                            if (!ObjectFactory.doLogOut()) {
                                msg = GuiResources
                                         .getMsgString("error.problem.logout");
                                if (ObjectFactory
                                        .getHostConnection().reqFullAccess()) {
                                    if (ObjectFactory.doLogOut()) {
                                        msg = null;
                                    }
                                }
                            }
                            if (msg != null) {
                                Log.logAndDisplay(Level.SEVERE, msg, null);
                            }
                        } else {
                            // user hit "Cancel" -- don't exit application
                            return;
                        }
                    } // full access check
                    
                } catch (ClientException ce) {
                    Log.logAndDisplay(
                        Level.SEVERE,
                        ce.getLocalizedMessage(),
                        null);
                } catch (ServerException se) {
                    Log.logAndDisplay(
                        Level.SEVERE,
                        se.getLocalizedMessage(),
                        null);
                }
                
                System.exit(0);
            } // window closing method
        });
        
        if (runningMode == MODE_LIVE) {
            try {
                adminApi = new AdminApiXML(host);
                adminApi = (AdminApi) TimerProxy.newProxy(adminApi);
                adminApi = (AdminApi) CacheProxy.newProxy(adminApi);
            } catch (MalformedURLException e) {
                UserMessage msg = new UserMessage(UserMessage.ERROR,
                        GuiResources.getMsgString(
                            "error.problem.hostConnection", host),
                        GuiResources.getMsgString(
                            "error.cause.badURL", e.getLocalizedMessage()),
                        GuiResources.getMsgString(
                            "error.solution.contactAdmin"));
                throw new UIException(msg);
            }
        }        
       
        // Register an action with Mozart that will clear the data cache
        // every time the panel timer fires.
        MainFrame.getMainFrame().registerTimerAction(
            new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    ObjectFactory.clearCache();
                }
            }
        );
        
        return adminApi;
    }
    /**
     * Clear all data from the cache.
     */
    public static void clearCache() {
        ((CacheOperations) adminApi).clear();
    }
    
    /**
     * Clear cache data for a particular method.
     */
    public static void clearCacheMethod(String methodName) {
        ((CacheOperations) adminApi).clearMethod(methodName);
    }
    
    public static class ShutdownThread implements Runnable {
        
        boolean reboot = false;
        Cell cObj = null;
        boolean shutdownSwitches = true;
        boolean shutdownSP = true;
        
        public ShutdownThread(Cell c, boolean switches,
                                    boolean sp, boolean boot) {
            reboot = boot;
            shutdownSwitches = switches;
            shutdownSP = sp;
            cObj = c;
        }
        public void run() {
            try {
                if (reboot) {
                    adminApi.reboot(cObj, shutdownSwitches, shutdownSP);
                } else {
                    // cell specfic only for 1.1
                    adminApi.powerOff(cObj, shutdownSwitches, shutdownSP);
                }
            } catch (Exception e) {
                Log.logToStatusAreaAndExternal(Level.SEVERE, 
                                                "async.operation.error", e);
                throw new RuntimeException(e);
            }
        }
        public void runReturn() {
        }
    }
    
    public static void shutdownSystem(Cell cell, boolean switches, 
                                            boolean sp, boolean boot,
                                                        ContentPanel cp) {
        String msg = null;
        try {
            // check to see if user is logged in
            if (validLogin) { 
                if (boot) {
                    // if cell == null, then reboot is hive wide
                    if (cell != null) {
                        msg = GuiResources.getGuiString(
                                        "cell.operation.reboot.guiExit", 
                                            Integer.toString(cell.getID()));
                    } else {
                        msg = GuiResources.getGuiString(
                            "cell.operation.rebootAll.guiExit");
                    }
                } else {
                    // if cell == null, then shutdown is hive wide
                    if (cell != null) {
                        msg = GuiResources.getGuiString(
                                        "cell.operation.shutdown.guiExit", 
                                            Integer.toString(cell.getID()));
                    } else {
                        msg = GuiResources.getGuiString(
                            "cell.operation.shutdownAll.guiExit");
                    }
                } 
                Log.logAndDisplayInfoMessage(msg);
                AsyncProxy.run(
                            new ShutdownThread(cell, switches, sp, boot), cp);
                Thread.sleep(5000);
                System.exit(0);
            }
        } catch (Exception e) {
            HoneycombCatchAllExceptionHandler.getInstance().handle(e);
        }
    }
    
    protected String doGetApplicationString(int whichString) {
        switch (whichString) {
            case APPSTR_APP_NAME:
                return GuiResources.getGuiString("app.name");
                
            case APPSTR_GUI_RESOURCE_BUNDLE_NAME:
                return GUI_BUNDLE_NAME;
                
            case APPSTR_MSG_RESOURCE_BUNDLE_NAME:
                return MSG_BUNDLE_NAME;
                
            case APPSTR_IMAGES_DIR:
                return IMAGES_DIR;
                
            case APPSTR_SETTINGS_RESOURCE_BUNDLE_NAME:
                return SETTINGS_BUNDLE_NAME;
                
            case APPSTR_LOGGING_PROPERTIES_FILE_NAME:
                return LOGGING_PROPERTIES_NAME;

            case APPSTR_HELPSET_NAME:
                return HELPSET_NAME.toString();
                
            default:
        }
        return "";
    }

    protected String doGetHelpMapKey(ExplorerItem explItem) {
        String helpKey = getHelpKey(explItem);

        /**
         * DO NOT REMOVE THE FOLLOWING CODE.
         * Need to uncomment it once we can add the getPageKey method
         * back to ContentPanel.
         */
        /*
        try {
            helpKey = explItem.getContentPanel().getPageKey();
        } catch (UIException uie) {
            Log.logAndDisplayException(uie);
        }
        helpKey = helpKey == null ? "" : helpKey;
         */
        return helpKey;
    }
    
    protected String doGetHostId() {
        String id = null;
        if (!(hostIPAddress == null && hostId == null)) {
            if (hostIPAddress == null) {
                id = hostId;
            } else {
                id = hostIPAddress;
            }
        }
        return id;
    }

    protected boolean postLogin(ContentPanel loginPanel) throws UIException {
        String password = ((PnlLogin) loginPanel).getPassword();

        try {
            validLogin = 
                    getHostConnection().newSessionAndVerifyPasswd(password);
        } catch (ServerException se) {
            // CR6531511: cli doesn't allow non-master cell access
            String errMsg = se.getMessage();
            boolean ipFound = false;
            String[] s = errMsg.split(IP_ADDRESS_PATTERN);
            if (!errMsg.equals(s[0]))
                ipFound = true;
            if (ipFound) {
                JOptionPane.showMessageDialog(MainFrame.getMainFrame(),
                    GuiResources.getGuiString("login.nonMasterCell.error",
                        errMsg.substring(s[0].length(), errMsg.length() -
                                   ((s.length > 1) ? s[1].length() : 0))),
                    GuiResources.getGuiString("app.name"),
                    JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            } else {
                MainFrame.setLoginErrMsg("<html>".concat(
                    GuiResources.getMsgString("error.login.deviceError")).
                        concat("<br>").concat(GuiResources.getMsgString(
                            "error.login.checkLog")).concat("</html>"));
            }
        } catch (Exception e) {
            MainFrame.setLoginErrMsg("<html>".concat(
                    GuiResources.getMsgString("error.login.deviceError")).
                        concat("<br>").concat(GuiResources.getMsgString(
                            "error.login.checkLog")).concat("</html>"));
        }
                
        return validLogin;
    }

    protected boolean logOut() {
        // Clear cells and nodes map as well as version information
        clearCache();

        try {
            getHostConnection().logout();
        } catch (ClientException e) {
            Log.logAndDisplay(
                Level.SEVERE,
                "Client Exception: " + e.getLocalizedMessage(),
                null);
            return false;
        } catch (ServerException e) {
            Log.logAndDisplay(
                Level.SEVERE,
                "Server Exception: " + e.getLocalizedMessage(),
                null);
            return false;
        }
        // Need to reset this flag in case the user logs in, then logs out and 
        // closes the application....once the "fullAccess" method works, then 
        // this can be removed.
        validLogin = false;
        return true;
    }
        
    protected BaseExplorerModel instantiateExplorerModel() throws UIException {
        return new ExplorerModel();
    }

    protected BaseActionCollection instantiateActionCollection() 
    throws UIException {
        return new ActionsCollection();
    }

    protected BaseMenuBar instantiateMenuBar() throws UIException {
        return null;
    }

    protected BaseToolBar instantiateToolBar() throws UIException {
        return new ToolBar();
    }

    protected ContentPanel instantiateLoginPanel() throws UIException {
        return new PnlLogin();
    }
   
    protected JTree instantiateMenuTree() throws UIException { 
        return null;  
//        return new MenuTree( MainFrame.getMainFrame(),
//            ObjectFactory.class.getResourceAsStream( 
//            "/com/sun/honeycomb/admingui/present/resources/menu.xml"),
//            ResourceBundle.getBundle( 
//                "com.sun.honeycomb.admingui.present.resources.l10n_gui"));
    }

    /**
     * The following method is a temporary fix to avoid adding an interface in
     * mozart ContentPanel in order to retrieve the help map key for a panel.
     * This method can be removed once the tree in Taos is changed to XML
     * format, and we can add the getPageKey method call back to ContentPanel.
     */
    private String getHelpKey(ExplorerItem explItem) {
        String panelName = explItem.getPanelClass().getName();
        String [] nameHelper = panelName.split("\\.");

        if ("PnlSilo".equals(nameHelper[6])) {
            return HelpFileMapping.SYSTEMSTATUS;
        } else if ("PnlConfigCellIPs".equals(nameHelper[6])) {
            return HelpFileMapping.CONFIGURECELLIPS;
        } else if ("PnlConfigAdminPassword".equals(nameHelper[6])) {
            return HelpFileMapping.CHANGEADMINISTRATIONPASSWORD;
        } else if ("PnlConfigAdminAuthorize".equals(nameHelper[6])) {
            return HelpFileMapping.AUTHORIZEDATACLIENTS;
        } else if ("PnlConfigNetworkNtp".equals(nameHelper[6])) {
            return HelpFileMapping.SETNTPSERVERS;
        } else if ("PnlConfigDNS".equals(nameHelper[6])) {
            return HelpFileMapping.SETUPDNS;
        } else if ("PnlConfigMetadataSchema".equals(nameHelper[6])) {
            int pnlType = ((Integer)explItem.getPanelPrimerData()).intValue();
            return
                pnlType == PnlConfigMetadataSchema.TYPE_SETUP_PNL ?
                    HelpFileMapping.SETUPMETADATASCHEMA :
                    HelpFileMapping.VIEWMETADATASCHEMA;
        } else if ("PnlConfigDataViewViews".equals(nameHelper[6])) {
            return HelpFileMapping.VIEWFILESYSTEMVIEW;
        } else if ("PnlConfigDataSetupView".equals(nameHelper[6])) {
            return HelpFileMapping.SETUPFILESYSTEMVIEW;
        } else if ("PnlBrowseWebDAV".equals(nameHelper[6])) {
            return HelpFileMapping.BROWSEFILESYSTEMVIEW;
        } else if ("PnlConfigNotifEmail".equals(nameHelper[6])) {
            return HelpFileMapping.CONFIGUREEMAILALERTS;
        } else if ("PnlConfigNotifLogHost".equals(nameHelper[6])) {
            return HelpFileMapping.SETLOGGINGHOST;
        } else if ("PnlCellNodesNode".equals(nameHelper[6])) {
            return HelpFileMapping.NODESTATUS;
        } else if ("PnlCellDisks".equals(nameHelper[6])) {
            return HelpFileMapping.DISKSTATUS;
        } else if ("PnlCell".equals(nameHelper[6])) {
            return HelpFileMapping.CELLSTATUS;
        } else if ("PnlMonitorPerfStats".equals(nameHelper[6])) {
            return HelpFileMapping.PERFORMANCESTATISTICS;
        } else if ("PnlMonitorEnvStatus".equals(nameHelper[6])) {
            return HelpFileMapping.ENVIRONMENTALSTATUS;
//        } else if ("PnlSiloExpansion".equals(nameHelper[6])) {
//            return HelpFileMapping.EXPANDHIVE;
            
        } else {
            // Unknown or Not implemented at the time when this method is
            // written
            return "";
        }
    }
}

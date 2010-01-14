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




package com.sun.honeycomb.alert.cli;


import com.sun.honeycomb.alert.AlertApiImpl;
import com.sun.honeycomb.alert.AlertApi;
import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.alert.AlertException;
import com.sun.honeycomb.cm.ipc.Mboxd;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.jvm_agent.CMAException;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeChange;

import java.util.regex.Pattern;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.io.File;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Observer;
import java.util.Observable;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.Iterator;



//
// Simple unit test for Alert Framework.
//
// 1. Register for a few notification

public class AlertTestClient implements AlertTestClientService, Observer
{

    private static transient final Logger logger = 
        Logger.getLogger(AlertTestClient.class.getName());

    static private int MAX_NOTIFICATIONS = 7;
    static private int MAX_NB_ITERATIONS = 500;

    private File f;
    private FileOutputStream out ;
    private PrintWriter wr;
    private boolean keepRunning;
    private int registartionID;
    private HashMap knownProps;
    private AlertApi api;
    private Object[] notifs;
    private Object  cmmNotif;


    public AlertTestClient() {

        try {
            f = File.createTempFile("alert", null, new File("/tmp"));
            out = new FileOutputStream(f);
            wr = new PrintWriter(out);
        } catch (IOException io) {
            logger.severe("ALERT_TEST : cannot create tmp file");
        }

        knownProps = new HashMap();
        keepRunning = true;
        notifs = null;
        cmmNotif = null;

        registerForNotifications();
        registerForCMMNotifications();
    }


    public void shutdown () {
        keepRunning = false;
    }

    public ManagedService.ProxyObject getProxy () {
        return new Proxy();
    }

    public void syncRun() {
    }

    public void run() {

        try {
            api = AlertApiImpl.getAlertApi();
        } catch (AlertException ae) {
            logger.severe("AlertTestClient exits..." +ae);
            keepRunning = false;
        }

        int delay = Mboxd.PUBLISH_INTERVAL * 5;
        int iteration = 0;
        
        while (keepRunning) {
            
            displayProperties(iteration++);
            try {
                Thread.sleep (delay);
            } catch (InterruptedException ie) {
                break;
            }

            if (iteration == MAX_NB_ITERATIONS) {
                unregisterForNotifications();
                break;
            }
        }
        wr.close();
        try {
            out.close();
        } catch (IOException io) {
            logger.severe("ALERT_TEST : cannot close tmp file");
        }
    }
    


    private void unregisterForNotifications() {
        for (int i = 0; i < MAX_NOTIFICATIONS; i++) {
            try {
                api.unregister(this, notifs[i]);
                logger.info("ALERT_TEST : Unregistration successful");
            } catch (AlertException ae) {
                logger.severe("ALERT_TEST : Unregistration failed.." + ae);
            }
        }
    }

    private void registerForCMMNotifications() {
        AlertApi.AlertEvent ev =
            new AlertApi.AlertEvent(AlertApi.AlertEvent.EVT_CMM);
        try {
            cmmNotif = api.register("AlertTestClient", null, this, ev);
        } catch (AlertException ae) {
            logger.severe("ALERT_TEST : fail to register to CMM notifications");
        }
    }

    private void registerForNotifications() {

        notifs = new Object[MAX_NOTIFICATIONS];

        try {
            //
            // Change events.
            //
            AlertApi.AlertEvent ev1 =
                new AlertApi.AlertEvent(AlertApi.AlertEvent.EVT_CHANGE, null);
            
            notifs[0] = api.register("AlertTestClient",
                                     "root.*.PLATFORM-SERVERS.freeMemory",
                                        this, ev1);
            notifs[1] = api.register("AlertTestClient",
                                     "root.102.Layout.diskmap.currentMap",
                                        this, ev1);
            notifs[2] = api.register("AlertTestClient",
                                     "root.105.API-SERVERS.*",this, ev1);

            //
            // Threshold events.
            //
            Double threshold = new Double(1.0);
            AlertApi.AlertEvent ev =
                new AlertApi.AlertEvent(AlertApi.AlertEvent.EVT_THRESHOLD_MAX,
                                        threshold);

            for (int i = 0; i < 4; i++) {
                String rule = "root.*.DiskMonitor.disk" + i + ".percentUsed";
                notifs[i + 3] = api.register("AlertTestClient", rule, this, ev);
            }
            logger.info("ALERT_TEST : Registration sucessfull...");
        } catch (AlertException ae) {
            logger.severe("ALERT_TEST : Registration failed.." + ae);
        }
    }


    private void removeProperties(int nodeid) {

        Pattern p = Pattern.compile("root.\\.(\\d+).*");


        logger.info("ALERT_TEST : Remove properties for node" + nodeid);
        synchronized(AlertTestClient.class) {

            Iterator it = knownProps.values().iterator();
            while (it.hasNext()) {
                AlertApi.AlertObject obj = (AlertApi.AlertObject) it.next();
                Matcher m = p.matcher(obj.getPropertyName());
                if (m.matches()) {
                    int curNode = (new Integer(m.group(1))).intValue();
                    if (curNode == nodeid) {
                        it.remove();
                    }
                }
            }
        }
    }


    public void update(Observable obs, Object arg) {
        // CMM notification
        if (arg instanceof NodeChange) {
            String action = (((NodeChange) arg).getCause() == 
                NodeChange.MEMBER_JOINED) ? "join" : "leave";

            logger.info("ALERT_TEST : CMM Notification for node " + 
                        ((NodeChange) arg).nodeId() + " .." + action);

            removeProperties(((NodeChange) arg).nodeId());
            
        // Alert Framework notification    
        } else if (arg instanceof AlertApi.AlertObject) {
            synchronized(AlertTestClient.class) {
                AlertApi.AlertObject obj = (AlertApi.AlertObject) arg;
                wr.print("Properties: Notification for = " +
                         obj.getPropertyName());
                wr.println();
                wr.flush();
            }
        // error
        } else  {
            logger.severe("ALERT_TEST :Invalid notification");
        }
    }



    // XXX: Temp code
    private int compareObjects(Object val1, Object val2, int type) {
        switch (type) {
        case AlertType.INT:
            return ((Integer) val1).compareTo((Integer) val2);
        case AlertType.LONG:
            return ((Long) val1).compareTo((Long) val2);
        case AlertType.FLOAT:
            return ((Float) val1).compareTo((Float) val2);
        case AlertType.DOUBLE:
            return ((Double) val1).compareTo((Double) val2);
        case AlertType.STRING:
            return ((String) val1).compareTo((String) val2);
        case AlertType.BOOLEAN:
            return ((Boolean) val1).equals((Boolean) val2) ? 0 : -1;
        default:
            logger.severe("ALERT_TEST : Object type undefined...");
            return -1;
        }
    }

    private void displayProperties(int iteration) {

        String type;
        boolean valBool;
        int valInt;
        long valLong;
        float valFloat;
        double valDouble;
        String valString;

        AlertApi.AlertViewProperty view = api.getViewProperty();
        
        synchronized(AlertTestClient.class) {
            
            wr.print("Properties: ITERATION = " + iteration + "\n");
            wr.flush();

            try { 
                AlertApi.AlertObject obj = view.getFirstAlertProperty();

                while (obj != null) {


                    AlertApi.AlertObject prev = (AlertApi.AlertObject)
                        knownProps.get(obj.getPropertyName());

                    if (prev != null &&
                        compareObjects(prev.getPropertyValue(),
                                       obj.getPropertyValue(),
                                       obj.getPropertyType()) == 0) {
                        obj = view.getNextAlertProperty();
                        wr.flush();
                        continue;
                    } else if (prev != null) {
                        wr.print("- name (CHG) = " +
                                 obj.getPropertyName() + " ");
                        knownProps.remove(obj.getPropertyName());
                        knownProps.put(obj.getPropertyName(), obj);
                    } else {
                        knownProps.put(obj.getPropertyName(), obj);
                        wr.print("- name (NEW) = " + 
                                 obj.getPropertyName() + " ");
                    }

                    switch (obj.getPropertyType()) {
                        
                    case AlertType.INT:
                        type = "Integer";
                        valInt = obj.getPropertyValueInt();
                        wr.print("type = " + type + " val = " + valInt) ;  
                        wr.println();
                        break;
                        
                    case AlertType.LONG:
                        type = "Long";
                        valLong =  obj.getPropertyValueLong();
                        wr.print("type = " + type + " val = " + valLong) ;  
                        wr.println();
                        break;
                        
                    case AlertType.FLOAT:
                        type = "Float";
                        valFloat =  obj.getPropertyValueFloat();
                        wr.print("type = " + type + " val = " + valFloat);  
                        wr.println();
                        break;
                        
                    case AlertType.DOUBLE:
                        type = "Double";
                        valDouble =  obj.getPropertyValueDouble();
                        wr.print("type = " + type + " val = " + valDouble);  
                        wr.println();
                        break;
                        
                    case AlertType.STRING:
                        type = "String";
                        valString =  obj.getPropertyValueString();
                        wr.print("type = " + type + " val = " + valString);  
                        wr.println();
                        break;
                        
                    case AlertType.BOOLEAN:
                        type = "Boolean";
                        valBool =  obj.getPropertyValueBoolean();
                        wr.print("type = " + type + " val = " + valBool);  
                        wr.println();
                        break;
                        
                    default:
                        type = "unknown";
                        wr.print("type = " + type);  
                        wr.println();
                    }
                    obj = view.getNextAlertProperty();
                    wr.flush();
                }
            } catch (AlertException ae) {
                logger.severe("ALERT_TEST : AlertException" + ae);
            } catch  (Exception e) {
                logger.severe("ALERT_TEST : Unknow exception " + e);
            } finally {
                wr.flush();
            }
        }
    }
}

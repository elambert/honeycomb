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



package com.sun.honeycomb.alert;

import java.util.LinkedList;
import java.util.HashMap;
import java.util.Observer;
import java.util.logging.Logger;
import java.util.logging.Level;

public class AlertObserver
{

    private static transient final Logger logger = 
        Logger.getLogger(AlertObserver.class.getName());

    private String     tagName;
    private LinkedList notifications;
    private Observer   observer;
    private int        registeredCMM;

    public AlertObserver(Observer obs, String tag) {
        tagName = tag;
        observer =  obs;
        notifications = new LinkedList();
        registeredCMM = 0;
    }

    public Observer getObserver() {
        return observer;
    }

    public boolean isRegisteredCMM() {
        if (registeredCMM > 0) {
            return true;
        } else {
            return false;
        }
    }

    public String getName() {
        return tagName;
    }

    public int getNbNotifications() {
        return notifications.size();
    }

    // Returns AlertApi.AlertEvent for notif 'index'
    public AlertApi.AlertEvent getEvent(int index) {
        AlertNotification notif =
            (AlertNotification) notifications.get(index);
        return notif.getEvent();
    }

    public Object addCMMNotification() {
        AlertNotification notif = new AlertNotification();
        registeredCMM++;
        return notif;
    }


    public Object addNotification(String node,
                                  String svc,
                                  String prop,
                                  AlertApi.AlertEvent ev) {
        AlertNotification notif = new AlertNotification(node,
                                                    svc,
                                                    prop,
                                                    ev);
        notifications.add(notif);
        return notif;
    }

    public boolean delNotification(Object obj) {
        
        if (! (obj instanceof AlertNotification)) {
            return false;
        }
        AlertNotification notif =  (AlertNotification) obj;
        if (notif.getEvent().getType() == AlertApi.AlertEvent.EVT_CMM) {
            return delCMMNotification(notif);
        } else {
            return notifications.remove(notif);
        }
    }

    public boolean isLeafMatchingNotification(AlertTreeNodeLeaf leaf) {
        String[] pathEl = leaf.parsePath();

        for (int i = 0; i < notifications.size(); i++) {
            AlertNotification notif =
                (AlertNotification) notifications.get(i);
            if (notif.isMatch(pathEl[0], pathEl[1], pathEl[2])) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("leaf " + "root." + pathEl[0] + "." 
                                + pathEl[1] + "." + pathEl[2] + 
                                " matching notif " + notif.getRule());
                }
                return true;
            } 
        }
        return false;
    }

    public boolean isLeafMatchingNotification(AlertTreeNodeLeaf leaf,
                                              int index) {
        String[] pathEl = leaf.parsePath();
        AlertNotification notif =
            (AlertNotification) notifications.get(index);
        if (notif.isMatch(pathEl[0], pathEl[1], pathEl[2])) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("leaf " + "root." + pathEl[0] + "." 
                            + pathEl[1] + "." + pathEl[2] + 
                            " matching notif " + notif.getRule());
            }
            return true;
        }
        return false;
    }

    private boolean delCMMNotification(Object notif) {
        boolean res = notifications.remove(notif);
        if (res == true) {
            registeredCMM--;
        }
        return res; 
    }

    
    private class AlertNotification {
        String                   node;
        String                   service;
        String                   prop;
        AlertApi.AlertEvent      event;


        public AlertNotification() {
            node = null;
            service = null;
            prop = null;
            event = new AlertApi.AlertEvent(AlertApi.AlertEvent.EVT_CMM);
        }

        public AlertNotification(String n,
                                 String svc,
                                 String p,
                                 AlertApi.AlertEvent ev) {
            node = n;
            service = svc;
            prop = p;
            event = ev;
        }

        public String getRule() {
            return ("root." + node + "." + service + "." + prop);
        }

        public AlertApi.AlertEvent getEvent() {
            return event;
        }

        public boolean isMatch(String lNode, String lService, String lProp) {
            if (event.getType() == AlertApi.AlertEvent.EVT_CMM) {
                return false;
            }
            if (!node.equals("*") &&
                !node.equals(lNode)) {
                return false;
            }
            if (!service.equals("*") &&
                !service.equals(lService)) {
                return false;
            }
            if (!prop.equals("*") &&
                !prop.equals(lProp)) {
                return false;
            }
            return true;
        }
    }
}

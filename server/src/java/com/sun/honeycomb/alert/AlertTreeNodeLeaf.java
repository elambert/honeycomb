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

import java.util.Observer;
import java.util.Observable;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.StringTokenizer;
import java.util.HashSet;
import java.util.Iterator;


public class AlertTreeNodeLeaf
    extends AlertComponent.AlertProperty implements java.io.Serializable
{

    private static transient final Logger logger = 
        Logger.getLogger(AlertTreeNodeLeaf.class.getName());

    // Full path : root.node.svc.<>
    private String path;

    // Value (Integer, Boolean, ...)
    private Object value;
    // Parent in the tree
    private AlertTreeNode parent;

    transient private HashSet observers;

    public AlertTreeNodeLeaf(AlertTreeNode p,
                      AlertComponent.AlertProperty prop,
                      String n,
                      Object val) {
        super (prop);
        parent = p;
        path = n;
        value = val;
        initObservers();
    }

    //
    // 'observer' is transient so when the leaf is sent over the network
    // this can be set to null; the first time
    public void initObservers() {
        observers = new HashSet();
    }

    public AlertApi.AlertObject getAlertObject() {
        AlertObjectImpl obj = new  AlertObjectImpl(path, value, type);
        return obj;
    }

    public String getPath() {
        return path;
    }

    // Returns an array of string:
    // res[0] = node
    // res[1] = service
    // res[2] = property (can contain several sub component)
    public String[] parsePath() {
        String[] res = new String[3];
        StringTokenizer tok = new StringTokenizer(path, ".");
        int i = 0;
        int maxTokens = tok.countTokens();
        StringBuffer buf = new StringBuffer();
        while (tok.hasMoreTokens()) {
            switch (i) {
            case 0:
                String root = tok.nextToken();
                break;
            case 1:
            case 2:
                res[i-1] = tok.nextToken();
                break;
            default:
                buf.append(tok.nextToken());
                if (i <  (maxTokens - 1)) {
                    buf.append(".");
                }
            }
            i++;
        }
        res[2] = buf.toString();
        return res;
    }

    public AlertTreeNode getParent() {
        return parent;
    }
    
    public void addObserver(AlertObserver obs) {
        if (observers == null) {
            logger.severe(" * did not initialize observers for leaf " +
                          getPath());
            return;
        }
        observers.add(obs);
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("add observer for " + path);
        }
    }

    public void delObserver(AlertObserver obs) {
        if (observers == null) {
            logger.severe(" ** did not initialize observers for leaf " +
                          getPath());
            return;
        }
        observers.remove(obs);
    }


    public void notifyObserverIfChange(Observer obs, Object newValue) {
        LeafObservable observable = new LeafObservable();
        if (compareObjects(newValue, value) != 0) {

            if (logger.isLoggable(Level.FINE)) {
                newValue = (newValue == null) ? "undef" : newValue;
                Object oldValue = (value == null) ? "undef" : value;
                logger.fine("change in property " + getPath() +
                            ", old value = " + oldValue +
                            ", new value = " + newValue);
            }

            AlertObjectImpl arg = 
                new AlertObjectImpl(getPath(), newValue, getType());
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("leaf " +  getPath() + " notify observer");
            }
            observable.addObserver(obs);
            observable.notifyAllObservers(arg);
        }
    }


    private void notifyObservers(Object newValue) {

        LeafObservable observable = new LeafObservable();

        Iterator it = observers.iterator();
        while (it.hasNext()) {

            boolean removeCurObserver = true;
            AlertObserver curObs = (AlertObserver) it.next();

            for (int j = 0; j < curObs.getNbNotifications(); j++) {

                if (!curObs.isLeafMatchingNotification(this, j)) {
                    continue;
                }
                removeCurObserver = false;
                AlertApi.AlertEvent curEvent = curObs.getEvent(j);

                switch(curEvent.getType()) {

                case AlertApi.AlertEvent.EVT_CHANGE:
                    if (compareObjects(newValue, value) != 0) {
                        if (logger.isLoggable(Level.FINE)) {
                            newValue = (newValue == null) ?
                                "undef" : newValue;
                            Object oldValue = (value == null) ?
                                "undef" : value;
                            logger.fine("change in property" + getPath() +
                                        ", old value = " + oldValue +
                                        ", new value = " + newValue);
                        }
                        observable.addObserver(curObs.getObserver());
                    }
                    break;

                case AlertApi.AlertEvent.EVT_THRESHOLD_MAX:
                    if ((compareObjects(value, curEvent.getThreshold()) 
                         <= 0) &&
                        (compareObjects(newValue, curEvent.getThreshold()) 
                         > 0)) {
                        observable.addObserver(curObs.getObserver());
                    }
                    break;

                case AlertApi.AlertEvent.EVT_THRESHOLD_MIN:
                    if ((compareObjects(value, curEvent.getThreshold()) 
                         >= 0) &&
                        (compareObjects(newValue, curEvent.getThreshold()) 
                         < 0)) {
                        observable.addObserver(curObs.getObserver());
                    }
                    break;

                default:
                    // Ignore CMM notification
                    break;
                        
                }
            }
            if (removeCurObserver) {
                it.remove();
            }
        }
        if (observable.countObservers() > 0) {

            AlertObjectImpl arg = 
                new AlertObjectImpl(getPath(), newValue, getType());
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("leaf " +  getPath() + " notify observer");
            }
            observable.notifyAllObservers(arg);
        }
    }

    // Compares the two objects-- for a given type
    private int compareObjects(Object val1, Object val2) {

        if ((val1 == null) || (val2 == null)) {
            if ((val1 == null) && (val2 == null)) {
                return 0;
            } else {
                return -1;
            }
        }

        switch (getType()) {
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
            logger.severe("Object type undefined...");
            return -1;
        }
    }

    public Object getValue() {
        return value;
    }

    public boolean getValueBoolean(Object obj) {
        Object val = (obj == null) ? value : obj;
        return (val != null) ? ((Boolean) val).booleanValue() : false;
    }
    public int getValueInt(Object obj) {
        Object val = (obj == null) ? value : obj;
        return (val != null) ? ((Integer) val).intValue() : -1;
    }
    public long getValueLong(Object obj) {
        Object val = (obj == null) ? value : obj;
        return (val != null) ? ((Long) val).longValue() : -1;
    }
    
    public float getValueFloat(Object obj) {
        Object val = (obj == null) ? value : obj;
        return (val != null) ? ((Float) val).floatValue() : -1;
    }
    public double getValueDouble(Object obj) {
        Object val = (obj == null) ? value : obj;
        return (val != null) ? ((Double) val).doubleValue() : -1;
    }
    public String getValueString(Object obj) {
        Object val = (obj == null) ? value : obj;
        return (val != null) ? ((String) val) : null;
    }

    //
    // Update with the latest value and trigger notifications 
    //
    public void setValue(Object obj) {
        if (observers == null) {
            logger.severe(" *** did not initialize observers for leaf " +
                          getPath());
            return;
        }
        if (obj != null) {
            if (observers.size() > 0) {
                notifyObservers(obj);
            }
            value = obj;
        }
    }

    //
    // Trigger notifications for all the registered clients- does not
    // matter if the property has changed. This is called by the AdminServer
    // to notify about CLI changes.
    //
    public void notifyClients(String msg) {

        LeafObservable observable = new LeafObservable();

        Iterator it = observers.iterator();
        while (it.hasNext()) {
            AlertObserver curObs = (AlertObserver) it.next();
            observable.addObserver(curObs.getObserver());
        }

        if (observable.countObservers() > 0) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("leaf " +  getPath() + " notify observer");
            }
            observable.notifyAllObservers(msg);
        }
    }

    //
    // Need to extends Observable to be able to call the protected method
    // setChanged().
    //
    private class LeafObservable extends Observable {
        public LeafObservable() {
            super();
        }
        public void notifyAllObservers(Object arg) {
            setChanged();
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("notify observers for prop " + getPath());
            }
            notifyObservers(arg);
        }
    }

}

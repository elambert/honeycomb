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


public interface AlertApi
{ 

    /*
     * Returns a view of the properties
     */
    public AlertViewProperty getViewProperty();


    /*
     * Return a single property
     */
    public AlertObject getProperty(String property)
        throws AlertException;

    /*
     * Register for some notification when the condition
     * indicated by the rule 'rule' changes.
     * - input : rule, Observer, type of event
     * - ouput : id
     */
    public Object register(String name, String rule, Observer obs,
                           AlertEvent ev)
        throws AlertException;

    /*
     * Unregister the notification
     * - input : Observer, ID returned by the register call.
     * - ouput : id
     */
    public void unregister(Observer obs, Object notif)
        throws AlertException;


    /*
     * Notify the clients which have registered for the speicifed property
     * - input : prop, The property for which clients have registered to,
     *           msg, the preformatted string used by the Alert clients.
     * (This is used by the AdminServer when a CLI change occurs)
     */
    public void notifyClients(String prop, String msg)
        throws AlertException;

    
    public interface AlertViewProperty {

        //
        // Set the iterator to the right property
        // (first one, next one, as specified by 'property')
        // All these calls throw AlertException if not such property
        //
        // Reinitializes to the first property
        public AlertObject getFirstAlertProperty();
        // Get the next property
        public AlertObject  getNextAlertProperty();
	
	/**
	 * Retrieve <code>property</code> from the alert tree map
	 * @param property the property key to lookup in the alert tree map
	 * @return AlertObject the alert object associated with the specified
	 * property
	 * @throws AlertException if the property is not found in the alert
	 * tree map
	 */
        public AlertObject getAlertProperty(String property)
            throws AlertException;

	/**
	 * Is <code>property</code> in the alert tree map?
	 * @param property the property key to lookup in the alert tree map
	 * @return boolean true if property is present, false otherwise
	 */
	public boolean isAlertPropertyPresent(String property);
    }


    public interface AlertObject {
        public int getPropertyType();
        public String getPropertyName();
        //
        // Returns the value of the current property-- depending on its type
        //
        public boolean getPropertyValueBoolean()
            throws AlertException;
        public int getPropertyValueInt()
            throws AlertException;
        public long getPropertyValueLong()
            throws AlertException;
        public float getPropertyValueFloat()
            throws AlertException;
        public double getPropertyValueDouble()
            throws AlertException;
        public String getPropertyValueString()
            throws AlertException;
        public Object getPropertyValue()
            throws AlertException;

    }

    public class AlertEvent {

        public static final int EVT_CHANGE = 1;
        public static final int EVT_THRESHOLD_MAX = 2;
        public static final int EVT_THRESHOLD_MIN = 3;
        public static final int EVT_CMM = 4;

        private int type;
        private Object threshold;

        public AlertEvent(int ev) {
            type = ev;
            threshold = null;
        }

        public AlertEvent(int ev, Object obj) {
            type = ev;
            threshold = obj;
        }
        
        public int getType() {
            return type;
        }

        public Object getThreshold() {
            return threshold;
        }

        public boolean getThresholdBoolean() {
            return (threshold != null) ?
                ((Boolean) threshold).booleanValue() : false;
        }
        public int getThresholdInt() {
            return (threshold != null) ?
                ((Integer) threshold).intValue() : -1;
        }
        public long getThresholdLong() {
            return (threshold != null) ?
                ((Long) threshold).longValue() : -1;
        }

        public float getThresholdFloat() {
            return (threshold != null) ?
                ((Float) threshold).floatValue() : -1;
        }
        public double getThresholdDouble() {
            return (threshold != null) ?
                ((Double) threshold).doubleValue() : -1;
        }
        public String getThresholdString() {
            return (threshold != null) ? (String) threshold : null;
        }
    }
}

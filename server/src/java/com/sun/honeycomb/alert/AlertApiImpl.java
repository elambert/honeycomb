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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.Observer;


public class AlertApiImpl implements AlertApi
{
    private AlertCorrelationEngine engine;

    AlertApiImpl(AlertCorrelationEngine eng)
        throws AlertException {
        if (eng == null) {
            throw new AlertException("Alert service is not running");
        }
        engine = eng;
    }

    static public AlertApi getAlertApi() throws AlertException {
        AlertApiImpl handle = new AlertApiImpl(AlerterServer.getEngine());
        return handle;
    }

    public AlertViewProperty getViewProperty() {
        if (engine == null) {
            return(null);
        }
        AlertViewPropertyImpl view = new AlertViewPropertyImpl(engine);
        return view;
    }

    public AlertObject getProperty(String property)
        throws AlertException {
        return engine.getProperty(property);
    }

    public Object register(String name, String rule, Observer obs,
                           AlertEvent ev) 
        throws AlertException {


        if (ev.getType() == AlertEvent.EVT_CMM) {
            return engine.registerCMM(name, obs);
        }

        // rule is "root.node.svc.whatever_prop"
        StringBuffer[] tokens = new StringBuffer[4];
        for (int j = 0; j < 4; j++) {
            tokens[j] = new StringBuffer();
        }
        if (!rule.startsWith("root.")) {
            throw(new AlertException("Invalid rule"));
        }
        
        StringTokenizer tok = new StringTokenizer(rule, ".");
        int maxTokens = tok.countTokens();
        if (maxTokens < 4) {
            throw(new AlertException("Invalid rule"));
        }
 
        int i = 0;
        while (tok.hasMoreTokens()) {
            if (i < 3) {
                tokens[i].append(tok.nextToken());
            } else {
                tokens[3].append(tok.nextToken());
                if (i < (maxTokens - 1)) {
                    tokens[3].append(".");
                }
            }
            i++;
        }
        String node = tokens[1].toString();
        String svc = tokens[2].toString();
        String prop = tokens[3].toString();


        if (svc.equals("*") &&
            !prop.equals("*")) {
            throw(new AlertException("Invalid rule"));
        }
        return engine.register(name,
                               tokens[1].toString(),
                               tokens[2].toString(),
                               tokens[3].toString(),
                               obs, ev);
    }

    /*
     * Unregister the notification
     * - input : Observer, ID returned by the register call.
     * - ouput : id
     */
    public void unregister(Observer obs, Object obj) 
        throws AlertException {
        boolean res = engine.unregister(obs, obj);
        if (!res) {
            throw(new AlertException("Invalid argument"));
        }
    }

    /*
     * Notify the clients who subscribed for this property
     *
     */
    public void notifyClients(String prop, String msg)
        throws AlertException {
        engine.notifyClients(prop, msg);
    }


    //
    // Implementation of the interface AlertApi.AlertProperty
    //
    static public class AlertViewPropertyImpl 
        implements AlertApi.AlertViewProperty, java.io.Serializable {

        private HashMap map;
        private ArrayList list;
        private int currentLeaf;

        public AlertViewPropertyImpl(AlertCorrelationEngine _engine) {
            currentLeaf = 0;
            map = new HashMap();
            list = new ArrayList();
            _engine.getCurrentView(map, list);
        }

        private AlertApi.AlertObject getCurProperty() {
            AlertApi.AlertObject obj = null;            
            try {
                obj = ( AlertApi.AlertObject) list.get(currentLeaf);
            } catch (IndexOutOfBoundsException e) {
                obj = null;
            }
            return obj;
        }

        public AlertApi.AlertObject getFirstAlertProperty() {
            currentLeaf = 0;
            return getCurProperty();
        }

        // Get the next property
        public AlertApi.AlertObject getNextAlertProperty() {
            currentLeaf++;
            return getCurProperty();
        }

	/**
	 * @see AlertApi.AlertViewProperty#getAlertProperty(String)
	 */
        public AlertApi.AlertObject getAlertProperty(String property) 
            throws AlertException {
            Integer index = (Integer) map.get(property);
            if (index != null) {
                currentLeaf = index.intValue();
                return getCurProperty();
            } else {
                throw new AlertException("Failed to retrieve '" + property + "'");
            }
        }
	
	
	/**
	 * @see AlertApi.AlertViewProperty#isAlertPropertyPresent(String)
	 */
	public boolean isAlertPropertyPresent(String property) {
	    return map.containsKey(property);
	}
	
    }
}

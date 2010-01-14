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



package com.sun.honeycomb.oa;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;

/*
 * Base class from which specific faults should inherit.
 */
public class Fault {
    private static final Logger log = Logger.getLogger(Fault.class.getName());
    private final String name;

    protected FaultType faultType;

    protected final Set eventSet = new HashSet();

    public boolean removeAfterTrigger = true;

    protected boolean triggered;

    /**********************************************************************/
    public Fault(String name, FaultEvent event) {
        this.name = name;
        faultType = new FaultType(name);
        eventSet.add(event);
    }

    /**********************************************************************/
    public Fault(String name, Collection events) {
        this.name = name;
        faultType = new FaultType(name);
        eventSet.addAll(events);
    }

    /**********************************************************************/
    public Fault(String name, FaultEvent event, FaultType type) {
        this.name = name;
        this.faultType = type;
        eventSet.add(event);
    }

    /**********************************************************************/
    public Fault(String name, Collection events, FaultType type) {
        this.name = name;
        this.faultType = type;
        eventSet.addAll(events);
    }

    /**********************************************************************
     * Used by tests to verify that fault is triggered.
     */
    public void assertTriggered() {
        if (!triggered) {
            throw new IllegalStateException("didn't fire: " + this);
        }
    }

    /**********************************************************************
     * Return true if fault is triggered.
     */
    public synchronized boolean triggered() {
        log.warning("triggered " + this + ", " + triggered);
        return triggered;
    }

    /**********************************************************************
     * Trigger this fault if the specified event matches one of the
     * events associated with this fault.
     *
     * @param event FaultEvent denoting the event for which this fault is
     *              verified against.
     * @return FaultType of this fault if triggered, null otherwise.
     */
    public synchronized FaultType triggerFault(FaultEvent event) {
        return triggerFault(event, null);
    }

    /**********************************************************************
     * Trigger this fault if the specified event matches one of the
     * events associated with this fault.
     *
     * @param event FaultEvent denoting the event for which this fault is
     *              verified against.
     * @param ctx   Optional context.
     * @return FaultType of this fault if triggered, null otherwise.
     */
    public synchronized FaultType triggerFault(FaultEvent event, Object ctx) {
        if (eventSet.contains(event)) {
            triggered = true;
            return faultType;
        } else {
            return null;
        }
    }

    /**********************************************************************
     * Should this fault be removed after triggering.
     *
     * @param didTrigger  is this fault triggered?
     * @return true if this fault should be removed, false otherwise.
     */
    public synchronized boolean shouldRemove(boolean didTrigger) {
        return (didTrigger && removeAfterTrigger);
    }

    /**********************************************************************
     * Remove event from this fault.
     *
     * @param event  FaultEvent to be removed
     * @return true if event is removed.
     */
    public synchronized boolean clearEvent(FaultEvent event) {
        return eventSet.remove(event);
    }

    /**********************************************************************
     * Return true if the event set is empty.
     */
    public synchronized boolean eventsEmpty() {
        return eventSet.isEmpty();
    }

    /**********************************************************************
     * Reset the fault for reuse.
     */
    public synchronized void reset() {
        triggered = false;
    }

    /**********************************************************************
     * {@inheritDoc}
     */
    public String toString() {
        return name + "(" + eventSet + ")";
    }

    /**********************************************************************
     * This class defines the specific type of fault, and action
     * associated (throw evaluate() method) with it.
     */
    public static class FaultType {
        private final String name;
        public FaultType(String name) {
            this.name = name;
        }
        public void evaulate(String msg) throws Exception {
            throw new UnsupportedOperationException("evaluate()");
        }
        public String toString() {
            return "type: " + name;
        }
    }
}

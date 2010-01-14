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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * This acts like a framework for inserting faults in the system.
 **/
public class FaultManager {
    private static final Logger log
        = Logger.getLogger(FaultManager.class.getName());

    private static final Map faultClasses = new HashMap();

    /**********************************************************************
     * Disallow instantiation.
     **/
    private FaultManager() {
    }

    /**********************************************************************
     * Insert a fault for a fault class.
     *
     * @param clazz  Class object of the class to insert faults for.
     * @param fault  Fault to insert.
     */
    public static synchronized void insertFault(Class clazz, Fault fault) {
        insertFault(clazz.getName(), fault);
    }

    /**********************************************************************
     * Insert a fault for a fault class.
     *
     * @param clazz  Name of class to insert faults for.
     * @param fault  Fault to insert.
     */
    public static synchronized void insertFault(String clazz, Fault fault) {
        Set set = (Set) faultClasses.get(clazz);
        if (set == null) {
            set = new HashSet();
            faultClasses.put(clazz, set);
        }
        set.add(fault);
        log.info("inserted fault " + fault + " for class " + clazz);
    }

    /**********************************************************************
     * Trigger this fault if the specified event matches one of the
     * events associated with this fault.
     *
     * @param clazz Class object of the class on which this fault acts.
     * @param event FaultEvent denoting the event for which this fault is
     *              verified against.
     * @return FaultType of this fault if triggered, null otherwise.
     */
    public static Fault.FaultType triggerFault(Class clazz,
                                               FaultEvent faultEvent) {
        return triggerFault(clazz.getName(), faultEvent, null);
    }

    /**********************************************************************
     * Trigger this fault if the specified event matches one of the
     * events associated with this fault.
     *
     * @param clazz Class object of the class on which this fault acts.
     * @param event FaultEvent denoting the event for which this fault is
     *              verified against.
     * @param ctx   Optional context.
     * @return FaultType of this fault if triggered, null otherwise.
     */
    public static Fault.FaultType triggerFault(Class clazz, FaultEvent faultEvent,
                                               Object ctx) {
        return triggerFault(clazz.getName(), faultEvent, ctx);
    }

    /**********************************************************************
     * Trigger this fault if the specified event matches one of the
     * events associated with this fault.
     *
     * @param clazz Name of the class on which this fault acts.
     * @param event FaultEvent denoting the event for which this fault is
     *              verified against.
     * @param ctx   Optional context.
     * @return FaultType of this fault if triggered, null otherwise.
     */
    public static synchronized Fault.FaultType triggerFault(String className,
                                                            FaultEvent faultEvent,
                                                            Object ctx) {
        Set set = (Set) faultClasses.get(className);
        if (set != null) {
            for (Iterator i = set.iterator(); i.hasNext(); ) {
                Fault fault = (Fault) i.next();
                Fault.FaultType triggered = null;
                try {
                    triggered = fault.triggerFault(faultEvent, ctx);
                    if (triggered != null) {
                        return triggered;
                    }
                } finally {
                    if (fault.shouldRemove(triggered != null)) {
                        i.remove();
                    }
                }
            }
        }
        return null;
    }

    /**********************************************************************
     * Reset faults for reuse.
     *
     * @param className faults associated with this class name are reset.
     */
    public static synchronized void resetFaults(String className) {
        Set faults = (Set) faultClasses.get(className);
        if (faults != null) {
            for (Iterator i = faults.iterator(); i.hasNext(); ) {
                ((Fault) i.next()).reset();
            }
        }
    }

    /**********************************************************************
     * Remove all faults.
     */
    public static synchronized void removeAllFaults() {
        faultClasses.clear();
    }

    /**********************************************************************
     * Remove faults associated with a class.
     *
     * @param className faults associated with this class name are remove.
     * @return A Collection of removed faults.
     */
    public static synchronized Collection removeFaults(String className) {
        Collection faults = (Collection) faultClasses.remove(className);
        return (faults == null) ? Collections.EMPTY_LIST : faults;
    }
}

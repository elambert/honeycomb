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

import com.sun.honeycomb.oa.daal.DAALException;

import java.nio.channels.ClosedByInterruptException;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * This is a specialized Fault for injecting errors into targeted fragments.
 **/
public class FragmentFault extends TriggerAfterCountFault {
    private static final Logger log
        = Logger.getLogger(FragmentFault.class.getName());

    public static final FaultType IO_ERROR
        = new IOFaultType("IOError");
    public static final FaultType UNDECLARED_ERROR
        = new IOFaultType("Undeclared");
    public static final DelayFaultType DELAY_ERROR
        = new DelayFaultType("Delay");
    // OAThread and surrounding framework ignores
    // InterruptedException. What is tested here is the retry
    // logic in OA. Sleep before simulating interruption.
    // FileChannel.write() does not throw
    // InterruptedException. If interrupted it returns the
    // number of bytes that was written so far.
    public static final DelayFaultType INCOMPLETE_WRITE_ERROR
        = new DelayFaultType("IncompleteWrite", 100);

    private final FragmentInfo fragmentInfo;
    private int triggerCount;

    /**********************************************************************/
    public FragmentFault(String name, FaultEvent event, FaultType faultType,
                         FragmentInfo fragInfo) {
        this(name, event, faultType, fragInfo, Integer.MAX_VALUE);
    }

    /**********************************************************************/
    public FragmentFault(String name, FaultEvent event, FaultType faultType,
                         FragmentInfo fragInfo, int triggerCount) {
        super(name, event, faultType);
        this.fragmentInfo = fragInfo;
        this.triggerCount = triggerCount;
    }

    /**********************************************************************
     * {@inheritDoc}
     */
    public synchronized FaultType triggerFault(FaultEvent event, Object ctx) {
        FragmentProxy proxy = (FragmentProxy) ctx;
        FragmentInfo fi = proxy.getFragmentInfo();
        boolean shouldTrigger = fi.equals(fragmentInfo);
        if (shouldTrigger && (fragmentInfo.offset != -1)) {
            shouldTrigger = ((fi.start + fi.offset) >= fragmentInfo.start)
                && (fi.start <= (fragmentInfo.start + fragmentInfo.offset));
            if (shouldTrigger && (fi.start > fragmentInfo.start)) {
                proxy.advanceTriggerPosition((int) (fi.start - fragmentInfo.start));
            }
        }
        FaultType ft = null;
        if (shouldTrigger) {
            ft = super.triggerFault(event, ctx);
            if (ft != null) {
                triggerCount--;
            }
        }
        return ft;
    }

    /**********************************************************************
     * {@inheritDoc}
     */
    public synchronized boolean shouldRemove(boolean didTrigger) {
        return (triggerCount <= 0);
    }

    /**********************************************************************
     * {@inheritDoc}
     */
    public String toString() {
        return super.toString() + "(" + fragmentInfo
            + " " + faultType + ")";
    }

    /**********************************************************************
     * Interface for callbacks used by this fault.
     */
    public interface FragmentProxy {
        public FragmentInfo getFragmentInfo();
        public void advanceTriggerPosition(int length);
    }

    /**********************************************************************
     * Placeholder to store fragment specific information.
     */
    public static class FragmentInfo {
        private int fragNum;
        private int chunk;
        private byte objType;
        public long start = -1;
        public long offset = -1; // offset < 0 means fault region never
                                 // matches the value in fragment file.

        public FragmentInfo(int fragNum, int chunk, byte objType) {
            this.fragNum = fragNum;
            this.chunk = chunk;
            this.objType = objType;
        }
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if ((((FragmentInfo) obj).fragNum == fragNum)
                && (((FragmentInfo) obj).chunk == chunk)
                && (((FragmentInfo) obj).objType == objType)) {
                return true;
            }
            return false;
        }
        public int hashCode() {
            return fragNum * 10000 + new Byte(objType).intValue() * 1000 + chunk;
        }
        public String toString() {
            return "fragNum: " + fragNum + " chunk: " + chunk
                + " objType: " + objType + ((start != -1) ? " start: " + start : "")
                + ((offset != -1) ? " offset: " + offset : "");
        }
    }

    /**********************************************************************
     * IO specific error
     */
    public static class IOFaultType extends FaultType {
        public IOFaultType(String type) {
            super(type);
        }
        public void evaluate(String msg) throws DAALException {
            if (this == IO_ERROR) {
                throw new DAALException(msg);
            } else if (this == UNDECLARED_ERROR) {
                throw new RuntimeException(msg);
            } else {
                throw new IllegalArgumentException("IOFaultType");
            }
        }
    }

    /**********************************************************************
     * FaultType for inserting delays
     */
    public static class DelayFaultType extends IOFaultType {
        private int delay = 45 * 1000; // 45 seconds
        public DelayFaultType(String type) {
            super(type);
        }
        public DelayFaultType(String type, int delay) {
            super(type);
            this.delay = delay;
        }
        public void setDelay(int delay) {
            this.delay = delay;
        }
        public void evaluate(String msg) {
            try {
                Thread.sleep(delay);
            } catch(InterruptedException ie) {
                log.warning("Interrupted, " + ie);
            }
        }
    }
}

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



package com.sun.honeycomb.resources;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

class FlatByteBufferPool extends ByteBufferPool {

    // try to allocate enough space for 50 buffers at a time...
    private static final int BUFFER_COUNT = 50;
    // but stop at 8 MB
    private static final int MAX_MASTER_SIZE = 8 * 1024 * 1024;

    private static final boolean DEBUG_CHECKOUT = true;
    private static final boolean DEBUG_PURGE = true;
    private static final boolean FORCE_PURGE = false;

    private static Map rangesByBuffer;

    private int bufferSize;
    private List masterBuffers;
    private List freeRecords;
    private ReferenceQueue queue;
    private Probe probe;

    static {
        rangesByBuffer = new HashMap(BUFFER_COUNT * 4);
    }

    FlatByteBufferPool(int newBufferSize) {
        bufferSize = newBufferSize;

        masterBuffers = new ArrayList();
        freeRecords = new ArrayList(BUFFER_COUNT);

        probe = new Probe();
        queue = new ReferenceQueue();

        addMasterBuffer();
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public int getFreeBufferCount() {
        synchronized (rangesByBuffer) {
            return freeRecords.size();
        }
    }

    private static String getIdentityString(Object object) {
        return Integer.toHexString(System.identityHashCode(object));
    }

    public int getCheckedOutBufferCount() {
        int result = 0;

        synchronized (rangesByBuffer) {
            Iterator values = rangesByBuffer.values().iterator();

            while (values.hasNext()) {
                RangeRecord record = (RangeRecord)values.next();
                if (record.pool == this) {
                    result++;
                }
            }
        }

        return result;
    }

    private void addMasterBuffer() {
        int masterSize = bufferSize * BUFFER_COUNT;
        if (masterSize > MAX_MASTER_SIZE) {
            // adjust to fit an even multiple of buffer size
            masterSize = (MAX_MASTER_SIZE / bufferSize) * bufferSize;
        }

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("allocating " +
                        (masterSize / 1024) +
                        " KB in chunks of size " +
                        (bufferSize / 1024) +
                        " KB");
        }

        ByteBuffer buffer = null;
        try {
            buffer = ByteBuffer.allocateDirect(masterSize);
        } catch (OutOfMemoryError e) {
            System.gc();
            buffer = ByteBuffer.allocateDirect(masterSize);
        }

        masterBuffers.add(buffer);

        int bufferCount = buffer.capacity() / bufferSize;
        for (int i = 0; i < bufferCount; i++) {
            freeRecords.add(new RangeRecord(this,
                                            buffer,
                                            i * bufferSize,
                                            (i + 1) * bufferSize));
        }
    }

    private void purge() {
        BufferReference ref;
        int i = 0;

        while (i++ < 2 && (ref = (BufferReference)queue.poll()) != null) {
            boolean found = false;

            RangeRecord record;
            synchronized (rangesByBuffer) {
                record = (RangeRecord)rangesByBuffer.remove(ref);
                if (record != null) {
                    record.removeView(ref);
                    found = true;
                }
            }

            if (found) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.warning("purging collected buffer:" +
                                   " ref = " + getIdentityString(ref) +
                                   " record = " + getIdentityString(record));

                    if (DEBUG_PURGE && ref.checkOutStackTrace != null) {
                        LOGGER.warning("stack trace from check out follows" +
                                       " - not an exception");

                        StringWriter stringWriter = new StringWriter();
                        PrintWriter printWriter = new PrintWriter(stringWriter);

                        ref.checkOutStackTrace.printStackTrace(printWriter);
                        LOGGER.warning(stringWriter.toString());
                    }
                }
            }
        }
    }

    public ByteBuffer checkOutBuffer(int capacity) {
        if (capacity > bufferSize) {
            throw new IllegalArgumentException("pool supports buffers up to " +
                                               bufferSize + 
                                               " in size only");
        }

        purge();

        ByteBuffer result;
        RangeRecord record;
        BufferReference ref;

        synchronized (rangesByBuffer) {
            int size = freeRecords.size();
            if (size == 0) {
                addMasterBuffer();
                size = freeRecords.size();
            }

            if (size > 0) {
                record = (RangeRecord)freeRecords.remove(size - 1);
            } else {
                throw new IllegalStateException("failed to allocate buffer");
            }

            if (DEBUG_CHECKOUT) {
                if (record.hasViews()) {
                    freeRecords.add(record);

                    throw new IllegalStateException("attempt to return buffer" +
                                                    " that's already checked out");
                }
            }

            record.baseView.clear();
            record.baseView.position(0);
            record.baseView.limit(capacity);

            result = record.baseView.slice();
            ref = record.addView(result);
            record.isFree = false;

            if (DEBUG_PURGE) {
                ref.checkOutStackTrace = new Exception();
            }

            rangesByBuffer.put(ref, record);
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            int hash = System.identityHashCode(result);
            String id = Integer.toHexString(hash);

            LOGGER.finest("checking out buffer " + getIdentityString(result) +
                          " ref = " + getIdentityString(ref) +
                          " record = " + getIdentityString(record));
        }

        return result;
    }

    public ByteBuffer checkOutDuplicate(ByteBuffer buffer) {
        return checkOutView(buffer, false, false);
    }

    public ByteBuffer checkOutReadOnlyBuffer(ByteBuffer buffer) {
        return checkOutView(buffer, false, true);
    }

    public ByteBuffer checkOutSlice(ByteBuffer buffer) {
        return checkOutView(buffer, true, false);
    }

    private ByteBuffer checkOutView(ByteBuffer buffer,
                                    boolean slice,
                                    boolean readOnly) {
        if (buffer == null) {
            throw new IllegalArgumentException("buffer cannot be null");
        }

        purge();

        ByteBuffer result;
        if (slice) {
            result = buffer.slice();
        } else {
            result = (readOnly)
                   ? buffer.asReadOnlyBuffer()
                   : buffer.duplicate();
        }

        RangeRecord record;

        synchronized (rangesByBuffer) {
            probe.set(buffer);
            record = (RangeRecord)rangesByBuffer.get(probe);
            probe.set(null);

            if (record == null) {
                throw new IllegalArgumentException("illegal attempt to check out" +
                                                   " duplicate of buffer that isn't" +
                                                   " itself checked out");
            }

            BufferReference ref = record.addView(result);
            if (DEBUG_PURGE) {
                ref.checkOutStackTrace = new Exception();
            }

            rangesByBuffer.put(ref, record);
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("checking out view of buffer " +
                          getIdentityString(buffer) +
                          " result = " + getIdentityString(result) +
                          " record = " + getIdentityString(record));
        }

        return result;
    }

    public void checkInBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("buffer cannot be null");
        }

        if (FORCE_PURGE) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("ignoring check in to force purge");
            }

            return;
        }

        checkInBuffer(buffer, null);
    }

    private void checkInBuffer(ByteBuffer buffer, RangeRecord record) {
        purge();

        synchronized (rangesByBuffer) {
            if (record == null) {
                probe.set(buffer);
                record = (RangeRecord)rangesByBuffer.remove(probe);
                probe.set(null);
            }

            if (record == null) {
                throw new IllegalArgumentException("attempt to check in a buffer" +
                                                   " that has already been checked" +
                                                   " in or was never checked out");
            }

            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("checking in buffer " + getIdentityString(buffer) +
                              " record = " + getIdentityString(record));
            }

            record.removeView(buffer);
        }
    }

    static Probe sharedProbe = new Probe();

    static void sharedCheckInBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("buffer cannot be null");
        }

        if (FORCE_PURGE) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("ignoring check in to force purge");
            }
            
            return;
        }

        synchronized (rangesByBuffer) {
            sharedProbe.set(buffer);
            RangeRecord record = (RangeRecord)rangesByBuffer.remove(sharedProbe);
            sharedProbe.set(null);

            if (record == null) {
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest("no record found for buffer  " + 
                                  getIdentityString(buffer));
                }

                throw new IllegalArgumentException("attempt to check in a buffer" +
                                                   " that has already been checked" +
                                                   " in or was never checked out");
            }

            record.pool.checkInBuffer(buffer, record);
        }
    }

    private static class RangeRecord {

        private FlatByteBufferPool pool;
        private boolean isFree;
        private ByteBuffer baseView;
        private List views;

        private RangeRecord(FlatByteBufferPool newPool,
                            ByteBuffer master,
                            int position,
                            int limit) {
            pool = newPool;
            isFree = true;

            master.position(position);
            master.limit(limit);
            baseView = master.slice();
        }

        private BufferReference addView(ByteBuffer view) {
            if (views == null) {
                views = new ArrayList(2);
            }

            BufferReference result = new BufferReference(view, pool.queue);
            views.add(result);

            return result;
        }

        private void removeView(Object view) {
            if (views != null) {
                int size = views.size();

                boolean found = false;

                for (int i = 0; i < size; i++) {
                    Object obj = views.get(i);

                    if (view == obj ||
                        (obj instanceof Reference &&
                         view == ((Reference)obj).get())) {
                        views.remove(i);
                        found = true;
                        break;
                    }
                }
            }

            if (!isFree && !hasViews()) {
                isFree = true;
                pool.freeRecords.add(this);
            }
        }

        private boolean hasViews() {
            if (views != null) {
                int size = views.size();

                for (int i = 0; i < size; i++) {
                    Reference ref = (Reference)views.get(i);
                    if (ref.get() != null) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    private static interface ObjectContainer {
        public Object get();
    }

    private static class Probe implements ObjectContainer {

        Object object;
        int hash;

        private void set(Object newObject) {
            object = newObject;
            if (object != null) {
                hash = System.identityHashCode(object);
            }
        }

        public Object get() {
            return object;
        }

        public int hashCode() {
            return hash;
        }

        public boolean equals(Object other) {
            return (other == this ||
                    (other instanceof ObjectContainer &&
                     ((ObjectContainer)other).get() == object));
        }
    }

    private static class BufferReference extends WeakReference
        implements ObjectContainer, Cloneable {

        private int hash;
        private Exception checkOutStackTrace;

        private BufferReference(Object referent,
                                ReferenceQueue newQueue) {
            super(referent, newQueue);
            hash = System.identityHashCode(referent);
        }

        public int hashCode() {
            return hash;
        }

        public boolean equals(Object other) {
            return (other == this ||
                    (other instanceof ObjectContainer &&
                     ((ObjectContainer)other).get() == get()));
        }
    }
}

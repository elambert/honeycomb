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

import com.sun.honeycomb.resources.ByteBufferPool;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class ByteBufferList {

    private static final int DEFAULT_BUFFER_COUNT = 4;

    private ArrayList records;
    private int remaining;

    public ByteBufferList() {
        this(DEFAULT_BUFFER_COUNT);
    }

    private ByteBufferList(int bufferCount) {
        records = new ArrayList(bufferCount);
        remaining = 0;
    }

    public ByteBufferList(ByteBuffer[] buffers) {
        this((buffers != null) ? buffers.length : 0);

        if (buffers != null) {
            for (int i = 0; i < buffers.length; i++) {
                appendBuffer(buffers[i]);
            }
        }
    }

    private ByteBufferList(ByteBufferList other) {
        this(other, false);
    }

    protected ByteBufferList(ByteBufferList other, boolean readOnly) {
        this((other != null) ? other.records.size() : 0);

        if (other != null) {
            remaining = other.remaining;
            ArrayList otherRecords = other.records;

            for (int i = 0; i < otherRecords.size(); i++) {
                records.add(new BufferRecord((BufferRecord)(otherRecords.get(i)), readOnly));
            }
        }
    }

    public ByteBufferList duplicate() {
        return new ByteBufferList(this);
    }

    public ByteBufferList asReadOnlyBuffer() {
        return new ReadOnlyByteBufferList(this);
    }

    public ByteBuffer[] getBuffers() {
        int size = records.size();
        ByteBuffer[] result = new ByteBuffer[size];

        for (int i = 0; i < size; i++) {
            BufferRecord record = (BufferRecord)records.get(i);
            record.buffer.position(record.position);
            record.buffer.limit(record.limit);

            result[i] = record.buffer;
        }

        return result;
    }

    public int remaining() {
        return remaining;
    }

    public boolean hasRemaining() {
        return remaining > 0;
    }

    public void appendBuffer(ByteBuffer buffer) {
        appendBuffer(buffer, true);
    }

    private void appendBuffer(ByteBuffer buffer, boolean copy) {
        if (buffer != null) {
            records.add(new BufferRecord(buffer, copy, false));
            remaining += buffer.remaining();
        }
    }

    public void appendBufferList(ByteBufferList bufferList) {
        if (bufferList != null) {
            ByteBuffer[] buffers = bufferList.getBuffers();
            for (int i=0; i<buffers.length; i++) {
                records.add(new BufferRecord(buffers[i]));
                remaining += buffers[i].remaining();
            }
        }
    }

    public void checkInBuffers() {
        clear();
    }

    public void clear() {
        ByteBufferPool pool = ByteBufferPool.getInstance();
        int size = records.size();

        for (int i = 0; i < size; i++) {
            BufferRecord record = (BufferRecord)records.remove(size - (i + 1));
            pool.checkInBuffer(record.buffer);
        }

        remaining = 0;
    }

    public ByteBufferList slice(int offset, int length) {
        ByteBufferPool pool = ByteBufferPool.getInstance();

        if (offset < 0) {
            throw new IllegalArgumentException("offset cannot be non-negative");
        } else if (offset + length > remaining) {
            throw new IllegalArgumentException("offset + length cannot exceed remaining");
        }

        if (offset == 0 && length == remaining) {
            return duplicate();
        }

        ByteBufferList result = new ByteBufferList();
        if (length == 0) {
            return result;
        }

        int[] firstIndex = getIndexAndCapacityForOffset(offset, null);
        int[] lastIndex = getIndexAndCapacityForOffset(offset + length, firstIndex);

        BufferRecord firstRecord = (BufferRecord)records.get(firstIndex[0]);
        BufferRecord lastRecord = (BufferRecord)records.get(lastIndex[0]);

        int beginOffset = firstIndex[1] - (firstRecord.limit - firstRecord.position);
        int endOffset = lastIndex[1] - (lastRecord.limit - lastRecord.position);

        int beginPosition = firstRecord.position + (offset - beginOffset);
        int endLimit = lastRecord.position + ((offset + length) - endOffset);

        if (beginPosition < firstRecord.limit) {
            ByteBuffer buffer = pool.checkOutDuplicate(firstRecord.buffer);
            buffer.position(beginPosition);

            if (firstIndex[0] == lastIndex[0]) {
                buffer.limit(endLimit);
            }

            result.appendBuffer(buffer, false);
        }

        for (int i = firstIndex[0] + 1; i < lastIndex[0]; i++) {
            BufferRecord record = (BufferRecord)records.get(i);
            ByteBuffer buffer = pool.checkOutDuplicate(record.buffer);

            buffer.position(record.position);
            buffer.limit(record.limit);
            result.appendBuffer(buffer, false);
        }

        if (firstIndex[0] != lastIndex[0] && endLimit > lastRecord.position) {
            ByteBuffer buffer = pool.checkOutDuplicate(lastRecord.buffer);
            buffer.position(lastRecord.position);
            buffer.limit(endLimit);

            result.appendBuffer(buffer, false);
        }

        return result;
    }

    public String toString() {
        StringBuffer result = new StringBuffer("<");
        int size = records.size();

        result.append("r=");
        result.append(remaining());

        for (int i = 0; i < size; i++) {
            BufferRecord record = (BufferRecord)records.get(i);

            result.append((i > 0) ? ", " : " ");

            result.append("[");
            result.append("c=");
            result.append(record.buffer.capacity());
            result.append(" p=");
            result.append(record.position);
            result.append(" l=");
            result.append(record.limit);

            result.append("]");
        }

        result.append(">");
        return result.toString();
    }

    // This could be a binary search but then we'd have to keep an
    // up-to-date capacity for each record. Maybe later.
    private int[] getIndexAndCapacityForOffset(int offset, int[] start) {
        int startIndex = (start != null) ? start[0] : 0;
        int capacitySoFar = (start != null) ? start[1] : 0;
        int size = records.size();
        int[] result = new int[2];
        int i;

        for (i = startIndex; i < size; i++) {
            BufferRecord record = (BufferRecord)records.get(i);

            if (start == null || i > startIndex) {
                capacitySoFar += (record.limit - record.position);
            }

            if (capacitySoFar >= offset) {
                break;
            }
        }

        result[0] = (i < size) ? i : i - 1;
        result[1] = capacitySoFar;

        return result;
    }

    public ByteBufferList[] slice(int length) {
        if (remaining % length != 0) {
            throw new IllegalArgumentException("length must evenly divide capacity");
        }

        int count = remaining / length;
        ByteBufferList[] result = new ByteBufferList[count];

        for (int i = 0; i < count; i++) {
            result[i] = slice(i * length, length);
        }

        return result;
    }

    public void flip() {
        for (int i = 0; i < records.size(); i++) {
            ((BufferRecord)records.get(i)).buffer.flip();
        }
    }

    public void rewind() {
        for (int i = 0; i < records.size(); i++) {
            ((BufferRecord)records.get(i)).buffer.rewind();
        }
    }

    public void pad(int length, byte value) {
        ByteBufferPool pool = ByteBufferPool.getInstance();
        ByteBuffer buffer = pool.checkOutBuffer(length);

        for (int i = 0; i < length; i++) {
            buffer.put(value);
        }

        buffer.flip();
        appendBuffer(buffer, false);
    }

    public void writeToChannel(WritableByteChannel channel) throws IOException {
        if (channel instanceof GatheringByteChannel) {
            ((GatheringByteChannel)channel).write(getBuffers());
        } else {
            int size = records.size();
            
            for (int i = 0; i < size; i++) {
                BufferRecord record = (BufferRecord)records.get(i);
                record.buffer.position(record.position);
                record.buffer.limit(record.limit);
                
                int written;
                
                while (record.buffer.hasRemaining() &&
                       (written = (channel.write(record.buffer))) >= 0);
                
                if (record.buffer.hasRemaining()) {
                    throw new IOException("failed to write all data from buffer");
                }
            }
        }
    }

    private static class BufferRecord {

        private final ByteBuffer buffer;
        private final int position;
        private final int limit;

        private BufferRecord(ByteBuffer newBuffer) {
            this(newBuffer, true, false);
        }

        private BufferRecord(BufferRecord record, boolean readOnly) {
            this(record.buffer, true, readOnly);
        }

        private BufferRecord(ByteBuffer newBuffer, boolean copy, boolean readOnly) {
            if (!copy) {
                buffer = newBuffer;
            } else {
                ByteBufferPool pool = ByteBufferPool.getInstance();
                buffer = (readOnly)
                       ? pool.checkOutReadOnlyBuffer(newBuffer)
                       : pool.checkOutDuplicate(newBuffer);
            }

            position = buffer.position();
            limit = buffer.limit();
        }
    }

    private static class ReadOnlyByteBufferList extends ByteBufferList {

        private ReadOnlyByteBufferList(ByteBufferList other) {
            super(other, true);
        }

        public void appendBuffer(ByteBuffer buffer) {
            throw new UnsupportedOperationException("append not allowed");
        }

        public void pad(int length, byte value) {
            throw new UnsupportedOperationException("pad not allowed");
        }
    }
}

package com.sun.dtf.actions.honeycomb.channels;


public class DeadBeefReadableByteChannel extends BufferReadableByteChannel {
    public DeadBeefReadableByteChannel(long offset, long length) {
        super(offset, "DEADBEEF BYTES! ".getBytes(),length);
    }
}

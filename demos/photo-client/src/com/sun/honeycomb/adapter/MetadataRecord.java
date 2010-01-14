package com.sun.honeycomb.adapter;

import java.util.Iterator;

import java.nio.channels.WritableByteChannel;
import java.nio.channels.ReadableByteChannel;

public interface MetadataRecord{

    public Iterator getKeys();
    public long getCreationTime();
    public long getDataSize();

    public String getString(String name);
    public void put(String name, String value);

    public void read(WritableByteChannel wbc);
    public void read(WritableByteChannel wbc, long offset, long len);

    public void write(String mimeType, ReadableByteChannel wbc);

    public static String DATA_FIELD_NAME = "photo.data";
}

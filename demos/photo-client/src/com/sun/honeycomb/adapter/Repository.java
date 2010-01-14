package com.sun.honeycomb.adapter;

import java.util.Iterator;
import java.io.InputStream;
import java.nio.channels.WritableByteChannel;


public interface Repository{

    public MetadataRecord getSet(UID uid);
    public MetadataRecord createSet();

    public UIDResultSet query(String query);
    public ResultSet query(String query, String[] select);

    public static String NAMESPACE = "photo.";
}

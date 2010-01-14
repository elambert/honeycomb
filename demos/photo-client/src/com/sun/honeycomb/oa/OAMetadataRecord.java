package com.sun.honeycomb.oa;

import com.sun.honeycomb.adapter.MetadataRecord;
import com.sun.honeycomb.adapter.AdapterException;

import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.client.NameValueObjectArchive;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.io.IOException;

import java.nio.channels.WritableByteChannel;
import java.nio.channels.ReadableByteChannel;

public class OAMetadataRecord implements MetadataRecord{
    
    ObjectIdentifier oid = null;
    NameValueObjectArchive nvoa;
    NameValueRecord nvr = null;

    /**
     * Open for write
     */
    OAMetadataRecord(NameValueObjectArchive nvoa){
        this.nvoa = nvoa;
        nvr = nvoa.createRecord();
    }

    /**
     * Open for read
     */
    OAMetadataRecord(NameValueObjectArchive nvoa, ObjectIdentifier oid) throws IOException, ArchiveException{
        this.nvoa = nvoa;
        this.oid = oid;
    }

    private void fetchMetadata(){
        if (nvr == null){
            try{
                nvr = nvoa.retrieveMetadata(oid);
            }
            catch (ArchiveException e){
                throw new AdapterException(e);
            }
            catch (IOException e){
                throw new AdapterException(e);
            }
        }
    }

    public Iterator getKeys() {
        fetchMetadata();
        return new Iterator(){
                String[] keys = nvr.getKeys();
                private int i = 0;
                public void remove(){}
                public Object next(){
                    if (i == keys.length)
                        throw new NoSuchElementException();
                    else
                        return keys[i++];
                }
                public boolean hasNext(){
                    return i < keys.length;
                }
            };
    }

    public long getCreationTime(){
        fetchMetadata();
        return nvr.getSystemRecord().getCreationTime();
    }

    public long getDataSize(){
        fetchMetadata();
        return nvr.getSystemRecord().getSize();
    }

    public String getString(String name){
        return nvr.getString(name);
    }

    public void put(String name, String value){
        nvr.put(name, value);
    }

    public void read(WritableByteChannel wbc){
        try{
            nvoa.retrieveObject(oid, wbc);
        }
        catch (ArchiveException e){
            throw new AdapterException(e);
        }
        catch (IOException e){
            throw new AdapterException(e);
        }
    }

    public void read(WritableByteChannel wbc, long offset, long len){
        try{
            nvoa.retrieveObject(oid,
                                wbc,
                                offset,
                                offset+len-1);
        }
        catch (ArchiveException e){
            throw new AdapterException(e);
        }
        catch (IOException e){
            throw new AdapterException(e);
        }
    }

    public void write(String mimeType, ReadableByteChannel rbc){
        try{
            nvoa.storeObject(rbc, nvr);
        }
        catch (ArchiveException e){
            throw new AdapterException(e);
        }
        catch (IOException e){
            throw new AdapterException(e);
        }
    }

}

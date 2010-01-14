package com.sun.honeycomb.oa;


import java.io.IOException;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.client.ObjectIdentifier;

import com.sun.honeycomb.adapter.Repository;
import com.sun.honeycomb.adapter.UID;
import com.sun.honeycomb.adapter.MetadataRecord;
import com.sun.honeycomb.adapter.ResultSet;
import com.sun.honeycomb.adapter.UIDResultSet;
import com.sun.honeycomb.adapter.AdapterException;



public class OA implements Repository{


    private NameValueObjectArchive nvoa;

    public OA (String cluster) throws ArchiveException, IOException{
        nvoa = new NameValueObjectArchive(cluster);
    }

    public MetadataRecord getSet(UID uid){
        try{
            return new OAMetadataRecord(nvoa, ((OID)uid).oid);
        }
        catch (ArchiveException e){
            throw new AdapterException(e);
        }
        catch (IOException e){
            throw new AdapterException(e);
        }
    }

    public MetadataRecord createSet(){
        return new OAMetadataRecord(nvoa);
    }

    public UIDResultSet query(String query){
        try{
            return new OAResultSet(nvoa.query(query, 1000));
        }
        catch (ArchiveException e){
            throw new AdapterException(e);
        }
        catch (IOException e){
            throw new AdapterException(e);
        }
    }

    public ResultSet query(String query, String[] select){
        try{
            return new OAResultSet(nvoa.query(query, select, 1000));
        }
        catch (ArchiveException e){
            throw new AdapterException(e);
        }
        catch (IOException e){
            throw new AdapterException(e);
        }
    }

}

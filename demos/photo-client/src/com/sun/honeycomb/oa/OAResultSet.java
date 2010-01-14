package com.sun.honeycomb.oa;


import java.io.IOException;

import com.sun.honeycomb.adapter.UID;
import com.sun.honeycomb.adapter.ResultSet;
import com.sun.honeycomb.adapter.AdapterException;

import com.sun.honeycomb.client.QueryResultSet;
import com.sun.honeycomb.common.ArchiveException;


/**
 * Results of a query with a select clause 
 */



public class OAResultSet implements ResultSet {

    QueryResultSet results;

    OAResultSet(QueryResultSet results){
        this.results = results;
    }

    public String getString(String key){
        return results.getString(key);
    }

    public boolean next() {
        try{
            return results.next();
        }
        catch (ArchiveException e){
            throw new AdapterException(e);
        }
        catch (IOException e){
            throw new AdapterException(e);
        }
    }

    public UID getObjectIdentifier(){
        return new OID(results.getObjectIdentifier());
    }
}

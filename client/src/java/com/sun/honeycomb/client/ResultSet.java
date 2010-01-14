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



package com.sun.honeycomb.client;

import java.io.IOException;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.codec.EncoderException;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.ProtocolConstants;

/**
 * ResultSet class provides a common abstract superclass for 
 * different implementations for sets of results.
 *
 * @see <a href="QueryResultSet.html">QueryResultSet</a>
 */
public abstract class ResultSet /*implements Iterator*/ {

    private List list = null;
    private Iterator iterator;
    MetadataObjectArchive mdoa;
    Connection connection;

    String requestPath;
    String queryPath;
    int resultsPerFetch;
    short cellIndex = 0;
    short[] cellList;
    String cookie;
    byte[] body;
    long queryIntegrityTime;

    final boolean isSelect;

    ResultSet(PreparedStatement query,
              Connection connection,
              MetadataObjectArchive mdoa,
              String requestPath,
              String queryPath,
              int resultsPerFetch,
              short[] cellList) throws IOException, ArchiveException{
        this.connection = connection;
        this.mdoa = mdoa;
        this.requestPath = requestPath;
        this.queryPath = queryPath;
        this.resultsPerFetch = resultsPerFetch;
        this.cellList = cellList;
        queryIntegrityTime = Long.MAX_VALUE;
        isSelect = query.isSelect();
        try{
            body = query.serialize().getBytes();
            // Populate first batch
            short cellId = -2;
            if (cellList.length > 0)
                cellId = cellList[cellIndex];
            connection.query (this, requestPath, queryPath, cellId);
        }
        catch (EncoderException ee){
            throw new RuntimeException(ee);
        }
    }

    // Fetch batch of query results
    abstract void read(HttpMethod method)
        throws IOException, ArchiveException;

    HashMap currentObject = null;

    void setList(List list){
        this.list = list;    
        if (list != null) {
            iterator = list.iterator();
        }
    }


    void nextResultSetInternal() throws ArchiveException, IOException{

        //String body = query.serialize();
        short cellId = -2;
        if (cellList.length > 0)
            cellId = cellList[cellIndex];
        connection.query (this, requestPath, queryPath, cellId);
    }

    /**
     * Advances the cursor so that the typed accessors refer to the next record. 
     * Returns false if there are no more results.
     */
    public boolean next() throws ArchiveException, IOException{

        if (list == null  &&  cellIndex == cellList.length){
            return false;
        }

        //
        // Fetch next batch on the same cell.
        //
        if (list != null && !iterator.hasNext() && cookie != null){
            nextResultSetInternal();
        }

        //
        // Fetch first batch on the next cell
        //
        while (cellIndex < cellList.length  && 
               (list == null || !iterator.hasNext())) {
            cookie = null;
            cellIndex++;
            if (cellIndex < cellList.length)
                nextResultSetInternal();
        }

        if (iterator != null && iterator.hasNext()){
            currentObject = (HashMap) iterator.next();
            return true;
        }
        else{
            currentObject = null;
            return false;
        }

    }

    /**
     * Returns a time that helps get more detail on which <i>store
     * index exceptions</i> might still be unresolved.
     * (See <code>SystemRecord.isIndexed()</code> for a definition
     * of store index exceptions.) If the query integrity time is
     * non-zero, then all store index exceptions whose object creation
     * time falls before the query integrity time have been resolved.
     * Stored objects from before that time should show up in all
     * matching query result sets. Store index exceptions that 
     * occurred after that time may not yet have been resolved, and
     * hence might still be missing from a matching query result set.
     * <br>
     * If the Query Integrity Time is zero, then the set of results in
     * this ResultSet is not known to be complete. Note that
     * <code>isQueryComplete</code> will return true if and only if
     * <code>getQueryIntegrityTime</code> returns a non-zero value.
     * <br>
     * Time values from <code>getQueryIntegrityTime</code> can be
     * compared to time values returned by
     * <code>SystemRecord.getCreationTime</code> to determine if a
     * particular store operation has been resolved.
     * <br>
     * Note: the query integrity time as reported may well be earlier
     * than the actual oldest time of a still-unresolved store index
     * exception.  The query integrity time can even go backwards,
     * i.e. a later query can report an earlier query integrity time.
     * @return The query integrity time expressed as a number of
     * milliseconds since the epoch.
     * @see ResultSet#isQueryComplete
     * @see SystemRecord#isIndexed
     * @see SystemRecord#getCreationTime
     */
    public long getQueryIntegrityTime() {
        return queryIntegrityTime;
    }

    /**
     * Indicates whether results of this query are
     * complete in the sense that all objects that match the query,
     * aside from possible <i>store index exceptions</i>, are included
     * in the result set. 
     * Applications that depend on completeness of query
     * results can interrogate <code>isQueryComplete</code> after retrieving all
     * the query results that match a particular query.  
     * When <code>isQueryComplete</code> returns true, the only items that should be
     * missing from the result set are <i>store index exceptions</i>
     * that were indicated to the application by a
     * <code>SystemRecord.isIndexed</code> value of
     * <code>false</code> after the store.
     * @see SystemRecord#isIndexed
     * @see ResultSet#getQueryIntegrityTime
     * @return <code>true</code> means that
     * all objects that match the query
     * should be present in the returned result set. 
     */
    public boolean isQueryComplete() {
        return (queryIntegrityTime > 0);
    }
}

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



package com.sun.honeycomb.fs;

import com.sun.honeycomb.adapter.UID;
import com.sun.honeycomb.adapter.Repository;
import com.sun.honeycomb.adapter.MetadataRecord;
import com.sun.honeycomb.adapter.ResultSet;
import com.sun.honeycomb.adapter.UIDResultSet;
import com.sun.honeycomb.adapter.AdapterException;

import java.util.Map;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;

public class VirtualFile
    extends HCFile {

    /****************************************
     *
     * static fields and methods
     *
     ****************************************/

    public static final String FIELD_CTIME              = "system.object_ctime";
    public static final String FIELD_SIZE               = "system.object_size";
    public static final String FIELD_OBJECTID           = "system.object_id";

    
    protected static List listRoots() {
        ArrayList result = new ArrayList();

        // BROKEN
        // The client API does not provide the views for now. To be fixed.
        //         View[] views = schema.getViews();
        //         for (int i=0; i<views.length; i++) {
        //             try {
        //                 result.add(new VirtualFile(RootFile.getRootFile(),
        //                                            views[i],
        //                                            new String[0],
        //                                            null));
        //             } catch (FileNotFoundException e) {
        //             }
        //         }
        
        return result;
    }
    
    /****************************************
     *
     * abstract methods
     *
     ****************************************/

    public String getAbsolutePath() {
        String result = "/" + view.getName();
        
        for (int i=0; i<attributes.length; i++) {
            result += "/" + attributes[i];
        }

        return(result);
    }

    public String getName() {
        if (attributes.length == 0) {
            return(view.getName());
        } else {
            return(attributes[attributes.length-1]);
        }
    }

    protected void computeParent() {
        if (attributes.length == 0) {
            // We are already at the root of the filesystem
            parent = RootFile.getRootFile();
            return;
        }

        String[] newAttributes = new String[attributes.length-1];
        for (int i=0; i<newAttributes.length; i++) {
            newAttributes[i] = attributes[i];
        }
        
        try {
            parent = new VirtualFile(this,
                                     view,
                                     newAttributes,
                                     null);
        } catch (FileNotFoundException e) {
            parent = null;
        }
    }

    public long lastModified() {
        if (directory) {
            return(System.currentTimeMillis());
        }
        
        return(fileAttrs.getATime());
    }
    
    public long length() {
        if (directory) {
            return(0);
        }
        
        return(fileAttrs.getSize());
    }
    
    public boolean hasSubDirectories() {
        if (children != null) {
            if (children.length > 0) {
                return(children[0].isDirectory());
            }
            return(false);
        }
        
        // We haven't computed the children yet
        return(attributes.length < view.getAttributes().length);
    }
    

    private ArrayList runQuery(String queryString) throws AdapterException{
	
        ArrayList result = new ArrayList();
	
        UIDResultSet queryResult = null;
        UID uid = null;
        Repository repository = HCFile.getRepository();
        long start = System.currentTimeMillis();
        
        HCFile.queryLogger.logQuery(queryString);
        queryResult = HCFile.getRepository().query(queryString);

        System.err.println("Got results for " + queryString + 
                           " in " + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();

        int count = 0;
        while (queryResult.next()) {
            count++;
            uid = queryResult.getObjectIdentifier();
		
            // Get the metadata for that object
            MetadataRecord set = null;
            try {
                set = repository.getSet(uid);
            } catch (Exception e) {
                System.out.println("retrieveMetadata failed for uid "+uid+" ["+
                                   e.getMessage()+"]");
                continue;
            }
            
            HashMap map = new HashMap();
            Iterator keys = set.getKeys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                if (MetadataRecord.DATA_FIELD_NAME.equals(key))
                    continue;
                try{map.put(key, set.getString(key));}
                catch (Exception e){
                    //System.err.println("failed to retrieve " + key + " " + e);
                }
            }
            map.put(FIELD_CTIME, Long.toString(set.getCreationTime()));
            map.put(FIELD_SIZE, Long.toString(set.getDataSize()));
            map.put(FIELD_OBJECTID, uid.toString());

            result.add( new FileAttrs(uid,
                                      set.getCreationTime(),
                                      set.getDataSize(),
                                      map) );
        }

        System.err.println("Populated metadata for " + count + " rsults in " + 
                            (System.currentTimeMillis() - start));
        
        return result;
    }



    /** Filter 
     */
    private static class UniqueValues /* implements Iterator */{
        ResultSet rs;
        boolean hasNext;
        int count = 0;
        int uniquecount = 0;
        String query;
        String key;
        HashSet hs = new HashSet();
        long start = System.currentTimeMillis();

        UniqueValues(String where, String key){
            query = key + " is not NULL " + (where == null ? "" : " and " + where);
            //query =  "exists(\"" + key + "\")" + (query == null ? "" : " and " + where);
            System.err.println("query: " + query);
            System.err.println("where: " + where);
            this.key = key;
            try {
                HCFile.queryLogger.logGetUniqueValues(query, key);
                String[] keys = {key};
                rs = HCFile.getRepository().query(query, keys);

                System.err.println("Query for select unique took " + (System.currentTimeMillis() - start) + "ms");

            } catch (Exception e) {
                System.out.println("getUniqueValues failed for ["+
                                   this.query+"-"+
                                   key +
                                   "] with ["+
                                   e.getMessage()+"]");
                e.printStackTrace();
                rs = null;
            }
            hasNext = (rs == null) ? false: rs.next();
            if (hasNext)
                hs.add(rs.getString(key));
        }

        public void remove(){
        }
        public boolean hasNext(){
            return hasNext;
        }
        
        public Object next(){
            if (!hasNext)
                return null;
            String s = rs.getString(key);

            // lookahead
            hasNext = rs.next();
            while (hasNext && hs.contains(rs.getString(key))) {
                hasNext = rs.next();
                count++;
            }
            uniquecount++;
            if (hasNext)
                hs.add(rs.getString(key));
            else
                System.err.println("Got " + count + " results, " + uniquecount + " unique for " + query + 
                                   " in " + (System.currentTimeMillis() - start) + "ms");
            return s;
        }
    }


    final static HCFile[] EMPTY_LIST = new HCFile[0];

    public HCFile[] listFiles() {
        if (!directory) {
            return EMPTY_LIST;
        }

        if ( (children != null)
             && ((System.currentTimeMillis() - childrenCreationTime) < CHILDREN_CACHE_DURATION) ) {
            // We have a valid cache
            return(children);
        }

        // We have to repopulate the children cache

        childrenCreationTime = System.currentTimeMillis();

        // General computations

        StringBuffer queryString = new StringBuffer();
        String[] childAttributes = new String[attributes.length+1];
        
        System.err.println("attributes.length: " + attributes.length + 
                           " attributes: " + attributes);
        
        for (int i=0; i<attributes.length; i++) {
            childAttributes[i] = attributes[i];
        }

        if (attributes.length > 0) {
            for (int i=0; i<attributes.length; i++) {
                if (queryString.length() > 0) {
                    queryString.append(" AND ");
                }
                queryString.append(view.getAttributes()[i] + "='"+
                                   attributes[i]+"'");
            }
        }
        
        // Add the entries if we are in the select unique case
        
        ArrayList tmpfiles = new ArrayList();
        
        if (attributes.length < view.getAttributes().length) {
            // The children are still directories
            String key = view.getAttributes()[attributes.length];
            
            String query = null;
            try {
                if (queryString.length() > 0) {
                    query = queryString.toString();
                }
                HCFile.queryLogger.logGetUniqueValues(query, key);
                UniqueValues uv = new UniqueValues(query, key);
            
                //-->
                if (!uv.hasNext())
                    return EMPTY_LIST;
                
                do {
                    String value = (String) uv.next();
                    childAttributes[attributes.length] = value;
                    try {
                        tmpfiles.add(new VirtualFile(this,
                                                     view,
                                                     (String[])childAttributes.clone(),
                                                     null));
                    } catch (FileNotFoundException e) {}
                } while (uv.hasNext());
                
                children = new HCFile[tmpfiles.size()];
                tmpfiles.toArray(children);   
                return children;
                
            } catch (AdapterException e) {
                System.out.println("getUniqueValues failed for ["+
                                   query+"-"+
                                   view.getAttributes()[attributes.length] +
                                   "] with ["+
                                   e.getMessage()+"]");
                e.printStackTrace();
                return EMPTY_LIST;
            }
        }
        
        // We are printing the leaves
	
        ArrayList queryResult;
        
        long startTime = System.currentTimeMillis();
        queryResult = runQuery(queryString.toString());
        System.out.println("SACHA> Performance ["+(System.currentTimeMillis()-startTime)+"]");
        if (queryResult == null) {
            children = null;
            return(new HCFile[0]);
        }
        
        for (int i=0; i<queryResult.size(); i++) {
            FileAttrs fileAttrs = (FileAttrs)queryResult.get(i);

            String printedString = null;
            if (view.getRepresentation() != null) {
                printedString = 
                    view.getRepresentation().convert(fileAttrs.getExtraAttributes());
            }
            
            if (printedString == null) {
                printedString = fileAttrs.getOid().toString();
            }
            
            childAttributes[childAttributes.length-1] = printedString;
	    
            try {
                tmpfiles.add(new VirtualFile(this,
                                             view,
                                             (String[])childAttributes.clone(),
                                             fileAttrs));
            } catch (FileNotFoundException e) {
            }
        }
        
        children = new HCFile[tmpfiles.size()];
        tmpfiles.toArray(children);
        
        return(children);
    }
    
    public Map getInfo() {
        return(fileAttrs.getExtraAttributes());
    }
    
    public void retrieve(WritableByteChannel channel,
                         long offset,
                         long length)
        throws UnsupportedOperationException {
        boolean retrieveWholeFile = false;
        
        if (isDirectory()) {
            throw new UnsupportedOperationException();
        }

        if ((offset != 0) && (length == -1)) {
            throw new IllegalArgumentException("Invalid arguments ["
                                               +offset+"/"+length+"]");
        }

        long currentFileSize = fileAttrs.getSize();

        if (offset > currentFileSize) {
            throw new IllegalArgumentException("Cannot retrieve offset " + offset +
                                               " greater than file length " + currentFileSize);
        }
        
        if ( (length == -1) || 
             ( (offset == 0) && (length == currentFileSize) )) {
            retrieveWholeFile = true;
        }

        // Reading past the end of file
        if ((offset+length) > currentFileSize) {
            length = currentFileSize - offset;
        }

        Repository repository = HCFile.getRepository();
        MetadataRecord set = HCFile.getRepository().getSet(fileAttrs.getOid());

        if (retrieveWholeFile) {
            set.read(channel);
        }  else {
            // Do a range retrieve
            set.read(channel, offset, offset+length-1);
        }
    }
    
    /****************************************
     *
     * fields
     *
     ****************************************/

    private View view;
    private String[] attributes;
    private boolean mdEntry;
    private FileAttrs fileAttrs;

    private HCFile[] children;
    private long childrenCreationTime;
    private static final int CHILDREN_CACHE_DURATION = 60000; // 1 minute

    /****************************************
     *
     * private methods
     *
     ****************************************/
    
    private void init(View newView,
                      String[] newAttributes,
                      FileAttrs nFileAttrs)
        throws FileNotFoundException {
        
        if (newAttributes.length > newView.getAttributes().length+1) {
            throw new FileNotFoundException("Path too deep for the view");
        }
        //System.err.println("Initializing with " + newAttributes.length + " attributes");

        view = newView;
        attributes = newAttributes;
        children = null;
        mdEntry = false;

        readable = true;
        writable = false;

        if (attributes.length <= view.getAttributes().length) {
            // This is a directory
            directory = true;
            executable = true;
            fileAttrs = null;
        } else {
            // This is not a directory
            directory = false;
            executable = false;

            // Check if this is a md entry
            String name = attributes[attributes.length-1];
            if ((name.startsWith("."))
                && (name.endsWith(".md"))) {
                mdEntry = true;
            }

            // Retrieve the ObjectId and Metadata

            if (nFileAttrs != null) {
                fileAttrs = nFileAttrs;
            } else {
                HCFile[] siblings = getParentFile().listFiles();
                int i;

                for (i=0; i<siblings.length; i++) {
                    if (siblings[i].getName().equals(attributes[view.getAttributes().length])) {
                        fileAttrs = ((VirtualFile)siblings[i]).fileAttrs;
                        break;
                    }
                }

                if (i == siblings.length) {
                    throw new FileNotFoundException("Entry not found ["+attributes[view.getAttributes().length]+"]");
                }
            }
        }
    }

    public VirtualFile(HCFile parent,
                       View newView,
                       String[] newAttributes,
                       FileAttrs nFileAttrs)
        throws FileNotFoundException {
        super(parent);
        init(newView, newAttributes, nFileAttrs);
    }

    protected VirtualFile(String absolutePath) 
        throws FileNotFoundException {
        super(null);

        throw new UnsupportedOperationException();

        //         if ((absolutePath == null) || (absolutePath.length() == 0)) {
        //             throw new FileNotFoundException("<null> path specified");
        //         }

        //         String[] elems = absolutePath.substring(1).split("/");
        //         if (elems.length == 0) {
        //             throw new FileNotFoundException("Invalid path [" + absolutePath +
        //                                             "]");
        //         }

        //         NameValueSchema schema = getSchema();
        //         if (schema == null) {
        //             throw new FileNotFoundException("Couldn't get the schema");
        //         }
        
        //         try {
        //             int index = schema.getViewIndex(elems[0]);
        //             View newView = schema.getViews()[index];
        //             String[] newAttributes = new String[elems.length-1];
        //             for (int i=0; i<newAttributes.length; i++) {
        //                 newAttributes[i] = elems[i+1];
        //             }
        //             init(newView, newAttributes, null);
        //         } catch (NoSuchElementException e) {
        //             throw new FileNotFoundException("No such view "+elems[0]);
        //         }
    }
}

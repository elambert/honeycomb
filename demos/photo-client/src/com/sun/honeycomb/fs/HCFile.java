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



/*
 * This class is the top class used to browse the virtualized filesystem in
 * Honeycomb.
 */

package com.sun.honeycomb.fs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.logging.Logger;
import java.util.Map;

import com.sun.honeycomb.adapter.Repository;
import com.sun.honeycomb.adapter.AdapterFactory;
import com.sun.honeycomb.adapter.AdapterException;


public abstract class HCFile {

    /****************************************
     *
     * Public API
     *
     ****************************************/
    
    public static QueryLogger queryLogger = new StdoutQueryLogger();
    private static Repository repository = null;
    
    public static final byte ROOTFILETYPE = 1;
    public static final byte PUBFILETYPE = 2;
    public static final byte VIEWFILETYPE = 3;
	
    public static void setClusterIP(String className, String clusterIP) throws Exception{
        try {
            repository = AdapterFactory.makeAdapter(className, clusterIP);	
        } catch (Exception e) {
            repository = null;
            throw e;
        }
    }

    public static Repository getRepository() {
        if (repository == null) {
            throw new UnsupportedOperationException("The connection has not yet been created [library initialization error]");
        }
        return repository;
    }

    /*
     * The following calls copy the java.io.File calls. The meaning of the
     * APIs are strictly the same.
     */
    
    public boolean canRead() {
        return(readable);
    }
    
    public boolean canWrite() {
        return(writable);
    }

    public boolean canExecute() {
        return(executable);
    }

    public long getMode() {
        long result = 0;

        if (canRead()) {
            result |= 0444;
        }
        if (canWrite()) {
            result |= 0222;
        }
        if (canExecute()) {
            result |= 0111;
        }
        
        return(result);
    }

    public abstract String getAbsolutePath();
    public abstract String getName();

    public HCFile getParentFile() {
        if (parent == null) {
            computeParent();
        }
        
        return(parent);
    }
    
    public boolean isDirectory() {
        return(directory);
    }
    
    public boolean isFile() {
        return(!directory);
    }

    public abstract long lastModified();
    public abstract long length();
    
    public abstract boolean hasSubDirectories();
    public abstract HCFile[] listFiles();

    /**
     * Method to return a section of the file listing.
     *
     * @param startIndex the index from where the listing is to begin
     * @param numResults the number of files to list
     * @return HCFile[] the list of files or null if there is an error
     */

    public boolean delete(HCFile fileToDelete) {
        // Can only delete RegularFiles
        return (false); // by default delete is not allowed
    }

    public HCFile listFile(String name) 
        throws FileNotFoundException {
        
        // To be optimized by the subclasses ...

        HCFile[] children = listFiles();
        int i;

        for (i=0; i<children.length; i++) {
            if (children[i].getName().equals(name)) {
                break;
            }
        }

        if (i<children.length) {
            return(children[i]);
        }

        throw new FileNotFoundException("Directory entry not found ["
                                        +getAbsolutePath()
                                        +" - "+name);
    }
    
    public abstract Map getInfo();
    
    // Routines to store / retrieve files in the filesystem

    /*
     * The retrieve calls the underlying mechanism to read the content of
     * the file.
     *
     * If you wish to read the whole file, enter 0 for the offset and -1
     * for the length
     */

    public void retrieve(WritableByteChannel channel,
                         long offset,
                         long length)
        throws UnsupportedOperationException, AdapterException, IOException {
        throw new UnsupportedOperationException();
    }

    /*
     * createFile creates a new file in that directory.
     *
     * Enter a length of -1 if the file length is not known.
     */

    public void createFile(ReadableByteChannel channel,
                           long size)
        throws UnsupportedOperationException, AdapterException, IOException {
        throw new UnsupportedOperationException("Cannot create files in "+getAbsolutePath());
    }
    
    /****************************************
     *
     * Private fields and routines
     * INTERNAL USE ONLY
     *
     ****************************************/
    
    protected static final Logger LOG = Logger.getLogger(HCFile.class.getName());

    protected volatile boolean directory;
    protected volatile boolean readable;
    protected volatile boolean writable;
    protected volatile boolean executable;
    protected volatile HCFile parent;
    
    protected HCFile(HCFile newParent) {
        parent = newParent;
        directory = false;
        readable = false;
        writable = false;
        executable = false;
    }

    private HCFile() {
    }

    protected abstract void computeParent();
}

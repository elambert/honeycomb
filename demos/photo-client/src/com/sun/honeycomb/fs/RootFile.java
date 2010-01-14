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

import java.util.LinkedList;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Map;

public class RootFile
    extends HCFile {
    
    /****************************************
     *
     * Public API
     *
     ****************************************/

    public static final byte FILESYSTEM_VIRTUALIZATION   = 0x1;

    private static final byte FILESYSTEM_DEFAULT = FILESYSTEM_VIRTUALIZATION;
    private static byte mountedFilesystems = FILESYSTEM_DEFAULT;

    public static synchronized void setMountedFilesystems(byte mountedFS) {
        mountedFilesystems = mountedFS;
    }

    /*
     * The following call returns a pointer to the root entry of the whole
     * honeycomb filesystem
     */

    public static synchronized RootFile getRootFile() {
        if (singleton == null) {
            singleton = new RootFile();
        }

        return(singleton);
    }
    
    public static HCFile parseAbsolutePath(String path) 
        throws IOException {
        if (path.equals("/")) {
            return(getRootFile());
        }

        if ((mountedFilesystems & FILESYSTEM_VIRTUALIZATION) != 0) {
            return(new VirtualFile(path));
        }
        
        throw new FileNotFoundException("No such entry ["+path+"]");
    }
    
    /****************************************
     *
     * HCFile abstract methods implementation
     *
     ****************************************/

    public String getAbsolutePath() {
        return("/");
    }

    public String getName() {
        return("");
    }
    
    public void computeParent() {
        parent = null;
    }

    public long lastModified() {
        return(System.currentTimeMillis());
    }
    
    public long length() {
        return(0);
    }
   
    public void flush(long offset, int count) {
        return;
    }

    public boolean hasSubDirectories() {
        return(true);
    }

    public HCFile[] listFiles() {
        ArrayList roots = new ArrayList();

        if ((usedFileSystems & FILESYSTEM_VIRTUALIZATION) != 0) {
            roots.addAll(VirtualFile.listRoots());
        }

        HCFile[] result = new HCFile[roots.size()];
        roots.toArray(result);

        return(result);
    }
    
    public Map getInfo() {
        return(null);
    }

    /****************************************
     *
     * Private API
     *
     ****************************************/
    
    private static RootFile singleton = null;
    private byte usedFileSystems;
    
    private RootFile() {
        super(null);
        usedFileSystems = mountedFilesystems;
        directory = true;
        readable = true;
        writable = false;
        executable = true;
    }        
}

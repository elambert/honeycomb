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


package com.sun.honeycomb.admingui.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

/**
 * An Ant task which strips the signatures from a set of jars.
 */
public class StripSignatures extends Task {
    private String outputDirectory;
    private Collection<FileSet> fileSets = new ArrayList<FileSet>();
    
    private final byte[] data = new byte[4096];
    
    public void addConfiguredFileset( FileSet fs ) {
        fileSets.add( fs );
    }
    
    public void setOutputDirectory( String outputDirectory ) {
        this.outputDirectory = outputDirectory;
    }
    
    @Override
        public void execute() {
        for ( FileSet fs : fileSets ) {
            DirectoryScanner ds = fs.getDirectoryScanner( fs.getProject() );
            for ( String fileName : ds.getIncludedFiles() )
                processFile( new File( ds.getBasedir(), fileName ));
        }
    }
    
    private void processFile( File inputFile ) {
        try {
            File outputFile = new File( 
                getProject().getBaseDir().getCanonicalPath() 
                + System.getProperty("file.separator") + outputDirectory 
                + System.getProperty("file.separator") + inputFile.getName() );
            if ( outputFile.exists() && 
                outputFile.lastModified() > inputFile.lastModified() ) return;
                // Skip if already processed
            
            System.out.println( "StripSignatures: " + inputFile.getName() );
            
            JarInputStream jis = new JarInputStream( 
                new BufferedInputStream( new FileInputStream( inputFile )));
            JarOutputStream jos = new JarOutputStream( 
                new BufferedOutputStream( new FileOutputStream( outputFile )));
            while( true ) {
                JarEntry entry = jis.getNextJarEntry();
                if ( entry == null ) break;
                
                if ( entry.getName().endsWith( ".SF") 
                    || entry.getName().endsWith( ".RSA") ) continue;    
                    // Skip signature files
                
                jos.putNextEntry( entry );
                while( true ) {
                    int l = jis.read( data, 0, data.length );
                    if ( l < 1 ) break;
                    jos.write( data, 0, l );
                }
            }
            
            jis.close();
            jos.close();
        } catch ( Exception ex ) {
            ex.printStackTrace();
        }
    }
}

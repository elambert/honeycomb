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



/** Stores a FITS file into a Honeycomb archive using HTTP interface
    Only extracts metadata from first HDUnit (the prime FITS header);
*/

// org.eso.fits docs: http://www.hq.eso.org/~pgrosbol/fits_java/docs/index.html

package sdsc.store;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Enumeration;
import org.eso.fits.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;

class HCStoreFITS {
    
    public static int StoreFITS(String filename,
                                String mimeType,
                                String password,
                                String host) {
        
        FitsFile file = null;
        
        try {
            file = new FitsFile(filename);
        } catch (FitsException e) {
            System.out.println("Error: is not a FITS file >"
                               + filename + "<");
            return 2;
        } catch (IOException e) {
            System.out.println("Error: cannot open file >"
                               + filename + "<");
            return 3;
        }
        
        // Extract Metadata
        SDSCMetadata md = new SDSCMetadata(file.getFile().getName(), 
                                           mimeType, 
                                           password,
                                           file);       
        
        if(verbose) {
            System.out.println("Found " + md.fieldsFound() + " of " + 
                               md.numInterestingFields() + " of the FITS " +
                               "fields we are interested in.");
            
        }
        
        // Store file in Honeycomb
        String uri = "http://" + host + ":" + port + "/store/";
        
        if(verbose) {
            System.out.println("Attempting to post to " + uri);
        }
        
        HttpClient client = new HttpClient();
        client.setConnectionTimeout(5000);
        PostMethod method = new PostMethod(uri);
        method.addRequestHeader("emd", md.getFormURLEncodedCSV());
        
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file.getFile());
        } catch (FileNotFoundException fnfe) {
            // Should not happen
            System.out.println(file.getFile() + " disappeared.");
            return 1001;
        }
        
        method.setRequestBody(fis);
        String responseBody = "";
        
        try {
            client.executeMethod(method);
            responseBody = method.getResponseBodyAsString();
        } catch(HttpException he) {
            System.out.println("HTTP error connecting to '" + uri + "':" +
                               he.getMessage());
            return 4;
        } catch(IOException ioe) {
            System.out.println("Unable to connect to '" + uri + "': " +
                               ioe.getMessage());
            return 5;
        } finally {
            method.releaseConnection();
            method.recycle();
        }
        
        System.out.println(responseBody);

        // Return success
        return 0;
    }
    
    // Command line interface
    public static void main(String[] args) {
        if(args.length != 2) {
            System.out.println("Usage: StoreFits <FITSFile> <host>");
            System.exit(1);
        }
        
        String filename = args[0];
        String host = args[1];
        
        System.exit(StoreFITS(filename, mimeType, password, host));
    }
    
    // For now just hardcode mimType and password
    private static final String mimeType = "image/fits";
    private static final String password = "honeycomb";
   
    // Talk a lot
    private static final boolean verbose = false;

    // default port
    private static final int port = 8080;
}

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



import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class HttpClient {

    private static String url;
    private static URL target;
    private static URLConnection conn;
    private static String boundary = "--29772313742745";

    public static HashMap params; // query part of the URL: param=value
    public static ArrayList files; // list of filenames to upload
    
    public HttpClient(String url) {
	connectToUrl(url);
	params = new HashMap();
	files = new ArrayList();
    }

    public HttpClient(String url, HashMap params, ArrayList files) {
	connectToUrl(url);
	this.params = params;
	this.files = files;
    }

    // tailored constructor for tests' common use case:
    // upload a file for a given "target"
    public HttpClient(String url, String target, File infile) {
	connectToUrl(url);
	params = new HashMap();
	params.put("target", target);
	files = new ArrayList();
	files.add(infile);
    }

    private void connectToUrl(String url) {
	this.url = url;
	try {
	    target = new URL(url);
	    conn = target.openConnection();
	} catch(Exception e) {;}
    }

    // convenience method to set URL query parameter
    public void addParam(String param, String val) {
	params.put(param, val);
    }

    // convenience method to set file upload parameter
    public void addFile(File infile) {
	files.add(infile);
    }

    public static String postHttp() throws IOException {

	// set HTTP headers
	conn.setDoOutput(true);
	conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=29772313742745");
	conn.setUseCaches(false);

	StringBuffer resp = new StringBuffer(); // for the HTTP response
	DataOutputStream postFormFile =new DataOutputStream(conn.getOutputStream());
	// begin form
	StringBuffer streamSend = new StringBuffer();
	//	streamSend.append("\r\n\r\n");
	
	// query portion of the URL
	Iterator p = params.keySet().iterator();
	while (p.hasNext()) {
	    Object n = p.next();
	    String param = n.toString();
	    String val = params.get(n).toString();
	    // <input TYPE="HIDDEN" NAME="$param" VALUE="$val">
	    streamSend.append(boundary + "\r\n");
	    streamSend.append("Content-Disposition: form-data; name=\"" + param + "\"\r\n\r\n");
	    streamSend.append(val);
	}
	
	// write out param=value parts of the form
	postFormFile.write(streamSend.toString().getBytes());

	// upload files
	Iterator f = files.iterator();
	while (f.hasNext()) {
	    File infile = (File)f.next();
	    String filename = infile.getAbsolutePath();
	    // <input TYPE="FILE" NAME="$infile->name" VALUE="$infile->content">
	    streamSend = new StringBuffer();
	    streamSend.append("\r\n" + boundary + "\r\n");
	    streamSend.append("Content-Disposition: form-data; name=\"infile\"; filename=\"" + filename + "\"\r\n");
	    streamSend.append("Content-Type: text/plain\r\n\r\n");
	    // write out file opening tag
	    postFormFile.write(streamSend.toString().getBytes());
	    FileInputStream from = new FileInputStream(infile);
	    try {
		int bytesRead;
		byte[] buffer = new byte[1024];
		while((bytesRead = from.read(buffer)) != -1) {
		    // write out file content bytes
		    postFormFile.write(buffer, 0, bytesRead);
		}
	    } catch(IOException e) {
		System.out.println("HTTP Post ERROR: " + e.getMessage());
	    } finally {
		if (from != null) 
		    try {from.close(); } catch(IOException e) { ; } 
	    }
	}
		
	// write out closing tag
	streamSend = new StringBuffer();
	streamSend.append("\r\n" + boundary + "--\r\n");
	postFormFile.write(streamSend.toString().getBytes());
	postFormFile.flush();
	postFormFile.close();

	// get HTTP response
	InputStream getResponse = conn.getInputStream();
	try {
	    int bytesRead;
	    byte[] buffer = new byte[4096];
	    while ((bytesRead = getResponse.read(buffer)) != -1) {
		resp.append(new String(buffer).trim());
	    }
	} catch(IOException e) {
	    System.out.println("HTTP Response ERROR: " + e.getMessage());
	} finally {
	    if (getResponse != null) try { getResponse.close(); } catch(IOException e) { ; }
	}
    
	return resp.toString();
    }

    /*
     * Example: posting test data to QB database via frontend web server 
     */
    public static void main(String[] args) {
	String postUrl = "http://qb.sfbay.sun.com/qb_post.cgi";
	String type = args[0]; // run, result, metric, build
	String filename = args[1]; // input file
	File infile = new File(filename);
	
	HttpClient client = new HttpClient(postUrl, type, infile);
	try {
	    String response = client.postHttp();
            
            // numeric-id is the item ID in QB database; parse it out
            int starts = response.indexOf("QB POST:");
            int ends = response.indexOf(" OK");
            if ((starts != -1) && (ends != -1)) {
                String idString = response.substring(starts, ends);
                System.out.println(idString);
            } else {
                System.out.println(response);
            }
	} catch(Exception e) {
            System.out.println("ERROR: " + e);
        }
    }
}


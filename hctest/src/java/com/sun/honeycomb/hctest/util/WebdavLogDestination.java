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



package com.sun.honeycomb.hctest.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.LogDestination;

public class WebdavLogDestination extends LogDestination {
	
	private WebDAVer wd = null;
	private String path = null;
	private String url = null;
	
	public WebdavLogDestination(String url, String path) {
		try {
			this.url = url;
			wd = new WebDAVer(url);
			this.path = path;
		} catch (HoneycombTestException e) {
			Log.ERROR("Error instantiating WebdavLogDestination: " + Log.stackTrace(e));
		}
	}

	protected void store(File source) {
		Log.INFO("Logs can be found at: " + url + path);
		
		if (source.isFile()) {
			// store single file...
			putFile(source);
		} else { 
			// store directory contents...
			File[] files = source.listFiles();			
			for (int i = 0; i < files.length; i++) {
				if (files[i].isFile()) {
					putFile(files[i]);
				}
			}
		}
		
		// Now here would be a good place to add more metadata to our 
		// previously stored logs. We can query for the previously stored
		// store logs and then add additional metadata to the oids returned
		// by the query.
		
		
	}
	
	private void putFile(File file){
		try {
			FileInputStream fis  = new FileInputStream(file);
			wd.putFile(path + "/" + file.getName(), fis.getChannel());
		} catch (FileNotFoundException e) {
			Log.ERROR("Error copying file " + file.getAbsolutePath() + " error: " + Log.stackTrace(e));
		} catch (HoneycombTestException e) {
			Log.ERROR("Error copying file " + file.getAbsolutePath() + " error: " + Log.stackTrace(e));
		}
	}
}

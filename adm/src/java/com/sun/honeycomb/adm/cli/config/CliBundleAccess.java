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


package com.sun.honeycomb.adm.cli.config;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Locale;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Singleton to make it easy to get access to the resources we need
 * to do internationalization. 
 * Currently holds potentially stale copies of the resource bundles,
 * if the language has been changed. If that behaviour is bad,
 * then take the performance hit and have it grab a new bundle from the
 * adminApi each time.
 */
public class CliBundleAccess  {


    private static CliBundleAccess _instance = null;
    private ResourceBundle    _bundle;
    private Locale            _locale;

    public static synchronized CliBundleAccess getInstance() {
        if (_instance == null) {
            _instance = new CliBundleAccess();
        }

        return(_instance);
    }
    public void reset() {
        _instance=null;
    }

    public ResourceBundle getBundle() {
        return _bundle;       
    }


    public Locale getLocale() {
        return _locale;
    }

    private CliBundleAccess() {
        //
        // Set up locale
        //

        String languageString =  CliConfigProperties.getInstance().getProperty ("cli.language");
        if(null==languageString) {
            //
            // FIXME - throw something. or something.
            //
            System.out.println("Warning: using default locale.");
            _locale=Locale.getDefault();
        } else {
            _locale=new Locale(languageString);
        }        
        String bundleName=CliConfigProperties.getInstance().getProperty ("cli.bundle.name");
        try {
            _bundle=ResourceBundle.getBundle (bundleName,
                                              _locale);
        } catch (MissingResourceException e) {
            System.err.println("Unable to find resource bundle for " 
                               + bundleName 
                               + " in language " 
                               + languageString);
            //Get the System Classloader
            ClassLoader sysClassLoader = ClassLoader.getSystemClassLoader();
            
            //Get the URLs
            URL[] urls = ((URLClassLoader)sysClassLoader).getURLs();
            
            for(int i=0; i< urls.length; i++) {                
                System.err.print(urls[i].getFile()+":");
            }       
            System.err.println();
            System.exit(254);
        }

        

    }
    
}


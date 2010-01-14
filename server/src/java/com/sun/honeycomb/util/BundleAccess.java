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


package com.sun.honeycomb.util;
import java.util.Iterator;
import java.io.File;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Locale;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.cm.node_mgr.NodeMgr;
import java.util.logging.Logger;
import java.util.ArrayList;
/**
 * Singleton to make it easy to get access to the resources we need
 * to do internationalization.
 * Currently holds potentially stale copies of the resource bundles,
 * if the language has been changed. If that behaviour is bad,
 * then take the performance hit and have it grab a new bundle from the
 * adminApi each time.
 */
public class BundleAccess  {



    private static transient final Logger logger =
        Logger.getLogger(BundleAccess.class.getName());

    private static BundleAccess _instance = null;
    private ResourceBundle    _bundle;
    private Locale            _locale;
    private String[]          _languages;
    public static synchronized BundleAccess getInstance() {
        if (_instance == null) {
            _instance = new BundleAccess();
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

    private BundleAccess() {
        //
        // Set up locale
        //
        String languageString =  ClusterProperties.getInstance().getProperty ("honeycomb.language");
        if(null==languageString) {
            _locale=Locale.getDefault();
        } else {
            _locale=new Locale(languageString);
        }
        _bundle = ResourceBundle.getBundle ("AdminResources",_locale);

        //
        // set up languages
        //
        String shareDir=NodeMgr.HC_SHARE_DIR;
        //AdminResources_en.properties
        File languageDir=new File(shareDir);
        File languageFiles[] = languageDir.listFiles();
        ArrayList files= new ArrayList();
        for(int i=0;i<languageFiles.length;i++) {
            String curFile=languageFiles[i].getName();
            if(curFile.startsWith("AdminResources")) {
               files.add(curFile);

            }
        }

        _languages = new String[files.size()];
        int count=0;
        for (Iterator i = files.iterator(); i.hasNext();) {
            String fileName=(String)i.next();
            String languageName=fileName.substring(15,17);
            _languages[count]=languageName;
            count++;
        }
    }
    public String[] getAvailableLanguages() {
        return _languages;


    }


}


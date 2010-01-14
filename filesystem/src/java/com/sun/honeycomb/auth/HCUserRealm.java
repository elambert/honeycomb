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



package com.sun.honeycomb.auth;

import com.sun.honeycomb.webdav.DAVServer;

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.HttpPassword;

import org.mortbay.util.TypeUtil;
import org.mortbay.util.StringUtil;
import org.mortbay.util.Credential;

import org.mortbay.http.DigestAuthenticator;
import org.mortbay.http.UserRealm;
import org.mortbay.http.HashUserRealm;
import org.mortbay.http.UserPrincipal;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.http.HttpFields;

import java.security.MessageDigest;

import java.util.Properties;

import java.util.regex.Pattern;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

/**
 * HashUserRealm is a list of usernames and clear-text passwords. This
 * class extends it so that only hashes are stored. This means that
 * DigestAuthenticator has also to be extended so that it can handle
 * H(A1) instead of the plain-text password.
 *
 * In the Divisadero version, there is only one user; the realm,
 * username, and password are set either from the cluster_config or by
 * the admin user at the CLI.
 *
 * The hash of the password is stored in a cluster property. If its value
 * gets changed by the CLI, this class will get notified and update itself.
 */
public class HCUserRealm extends HashUserRealm
    implements PropertyChangeListener {

    private static final Logger logger =
        Logger.getLogger(HCUserRealm.class.getName());

    private static Properties config = null;

    // Singleton
    private static HCUserRealm theRealm = null;

    private HCUser theUser = null;

    private String userName = null;
    private String realmName = null;
    private byte[] hashedPasswd = null;

    private static String PROPNAME_SAVED = "honeycomb.webdav.auth.hash";
    private static String PROPNAME_DELIM = "honeycomb.webdav.auth.delimiter";

    private static String delimiter = ",";

    public static void init(Properties props) {
        if (config != null)
            throw new InternalException("Can't call init() multiple times!");
        config = props;
        getDelimiter();
    }

    public static synchronized HCUserRealm getInstance() {
        if (theRealm == null)
            theRealm = new HCUserRealm();
        return theRealm;
    }

    private HCUserRealm() {
        theUser = new HCUser();
        restorePassword();
    }

    public String getRealmName() { return realmName; }
    public String getUserName() { return userName; }
    public String getName() {
        return realmName.substring(0,1).toUpperCase() + realmName.substring(1);
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();

        sb.append("<Realm");
        sb.append(" name=\"").append(realmName);
        sb.append("\" user=\"").append(userName);
        sb.append("\" hash=\"");
        sb.append(ByteArrays.toHexString(hashedPasswd));

        return sb.append("\"/>").toString();
    }

    // If the property (that we store the password in) changes, update
    // the password
    public void propertyChange(PropertyChangeEvent event) {
        if (!event.getPropertyName().equals(PROPNAME_SAVED))
            return;

        String passwd = (String) event.getNewValue();
        try {
            setPassword(passwd);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE,
                       "Couldn't change auth info to <" + passwd + ">",
                       e);
        }
    }

    // Set the compliance auth. info (plain-text password)
    public void setUser(String realmName, String userName, String password) {
        this.realmName = realmName;
        this.userName = userName.toLowerCase();
        hashedPasswd  = HttpPassword.makeHash(realmName, userName, password);
    }

    // Set the compliance auth. info (hash of password)
    public void setUser(String realmName, String userName, byte[] hash) {
        this.realmName = realmName;
        this.userName = userName.toLowerCase();
        hashedPasswd  = hash;
    }

    public UserPrincipal authenticate(String name,
                                      HCAuthenticator.Digest digest,
                                      HttpRequest request) {
        // Check the the username and realm are correct

        if (name == null ||
            !digest.realmName().equals(realmName) ||
            !name.equalsIgnoreCase(userName))
            return null;

        // Check the password
        if (digest.check(hashedPasswd))
            return theUser;

        return null;
    }

    private void restorePassword() {
        String saved = config.getProperty(PROPNAME_SAVED);
        if (saved != null) {
            try {
                setPassword(saved);
                return;
            }
            catch (Exception e) {
                logger.log(Level.SEVERE,
                           "Couldn't set auth info to \"" + saved + "\"",
                           e);
            }
        }

        // If there is no value for the property, should we throw an
        // InternalException instead? That would get fielded somewhere
        // lower on the stack and handled appropriately. But what's
        // the appropriate behaviour? Kill the server? Too severe. Log
        // an error? We've already done that, right here. By returning
        // silently, ordinary WebDAV operations succeed; it's just the
        // compliance operations that will be broken because no
        // request can ever authenticate.

        logger.warning("No saved password; WebDAV compliance ops will fail.");

        userName = null;
        realmName = null;
        hashedPasswd = null;
    }

    /**
     * Set the saved password. Example of string:
     *     "HC WebDAV:root:6dad42d265e3b425e3892d2bf920a41d"
     */
    private void setPassword(String value) {
        logger.info("Changing authentication info to \"" + value + "\"");

        // Unpack the string
        String[] fields = value.split(delimiter);
        if (fields.length != 3)
            throw new IllegalArgumentException("Saved hash <" + value + ">");

        realmName = fields[0];
        userName = fields[1];
        hashedPasswd = ByteArrays.toByteArray(fields[2]);
    }

    private static void getDelimiter() {
        delimiter = config.getProperty(PROPNAME_DELIM, ",").trim();

        if (delimiter == null || delimiter.length() == 0)
            throw new InternalException("No " + PROPNAME_DELIM + "value!");
        if (delimiter.length() != 1)
            throw new InternalException("Bad value \"" + delimiter + 
                                        "\" for " + PROPNAME_DELIM + 
                                        " -- needs to be one character only");

        // It could be a character that's significant in a regular
        // expression; quote it
        delimiter = Pattern.quote(delimiter);
    }

    private class HCUser implements UserPrincipal {
        private UserRealm getUserRealm() { return HCUserRealm.this; }
        public String getName() { return userName; }
        public boolean isAuthenticated() { return true; }
        public boolean isUserInRole(String role) { return false; }
        public String toString() { return getName(); }
    }

    // An unprivileged user
    UserPrincipal getPlainUser() {
        return new PlainUser();
    }
    private class PlainUser implements UserPrincipal {
        private UserRealm getUserRealm() { return HCUserRealm.this; }
        public String getName() { return "nobody"; }
        public boolean isAuthenticated() { return false; }
        public boolean isUserInRole(String role) { return false; }
        public String toString() { return getName(); }
    }

}

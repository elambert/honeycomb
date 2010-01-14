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

import com.sun.honeycomb.common.ByteArrays;

import org.mortbay.util.Credential;
import org.mortbay.util.QuotedStringTokenizer;

import org.mortbay.http.SecurityConstraint;
import org.mortbay.http.UserPrincipal;
import org.mortbay.http.DigestAuthenticator;
import org.mortbay.http.UserRealm;
import org.mortbay.http.UserPrincipal;
import org.mortbay.http.HashUserRealm;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.http.HttpFields;

import java.security.MessageDigest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.util.logging.Level;
import java.util.logging.Logger;

/*
  This is DIGEST authentication:

  H1 = H(username + ":" + realm + ":" + passwd)
  H2 = H(method + ":" + uri)
  digest = H(H1 + ":" + nonce + ":" + H2)

  Example:
     Server says authentication is required:

         HTTP/1.1 401 Unauthorized
         WWW-Authenticate: Digest
                 realm="Honeycomb",
                 nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093"

    Client re-tries the request with this extra header:

          Authorization: Digest
                 username="Beulah",
                 realm="Honeycomb",
                 nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
                 uri="/dir/index.html",
                 response="6629fae49393a05397450978507c4ef1"

  This class is the same as DigestAuthenticator except that we use an
  HCDigest for the actual authentication, which works on H(A1) (aka H1)
  instead of the clear-text password.

  Before trying to understand any of this, be sure you have read
  RFC-2617 "HTTP Authentication".
 */

public class HCAuthenticator extends DigestAuthenticator {

    private static final Logger logger =
        Logger.getLogger(HCAuthenticator.class.getName());

    /**
     * This code is copied from DigestAuthenticator with the one
     * change that an HCAuthenticator.Digest is created instead of
     * Authenticator.Digest
     */
    public UserPrincipal authenticated(UserRealm realm,
                                       String pathInContext,
                                       HttpRequest request,
                                       HttpResponse response)
            throws IOException {
        UserPrincipal user = null;
        HCUserRealm hcRealm = (HCUserRealm) realm;

        if (logger.isLoggable(Level.FINE))
            logger.fine("Trying to authenticate " + request.getMethod() +
                        " \"" + pathInContext + "\" in " + hcRealm);

        if (hcRealm == null) {
            response.sendError(HttpResponse.__500_Internal_Server_Error,
                               "Realm Not Configured");
            return null;
        }

        // If the request isn't trying to write a compliance
        // attribute, allow it -- by pretending the user has
        // authenticated as an ordinary unprivileged user
        if (!request.getMethod().equalsIgnoreCase("PROPPATCH"))
            return hcRealm.getPlainUser();

        String authorization = request.getField(HttpFields.__Authorization);
        if (authorization != null) {
            // Get the digest ...
            Digest digest = getDigest(authorization, request);

            if (logger.isLoggable(Level.INFO))
                logger.info("Checking auth: " + request.getMethod() +
                            " \"" + pathInContext + "\" = " + digest);

            // ... and check it
            user = hcRealm.authenticate(digest.username, digest, request);

            if (user != null) {
                // Auth succeeded.
                request.setAuthType(SecurityConstraint.__DIGEST_AUTH);
                request.setAuthUser(digest.username);
                request.setUserPrincipal(user); 

                if (logger.isLoggable(Level.FINE))
                    logger.fine("Auth OK for " + user + ": " +
                                request.getMethod() + 
                                " \"" + pathInContext + "\"");
            }
            else
                logger.warning("AUTH FAILURE \"" + digest.username + "\"");
        }

        // Either there was no auth, or auth failed; send a 401 challenge
        if (user == null) {
            logger.warning("Authentication required for " +
                           request.getMethod() +
                           " \"" + pathInContext + "\" in " + hcRealm);

            sendChallenge(hcRealm, request, response);
        }
        
        return user;
    }

    /** Parse the Authorization: header */
    private Digest getDigest(String credentials, HttpRequest request) {
        QuotedStringTokenizer tokenizer =
            new QuotedStringTokenizer(credentials, "=, ", true, false);

        Digest digest = new Digest(request.getMethod());
        String last = null;
        String name = null;
            
        while (tokenizer.hasMoreTokens()) {
            String tok = tokenizer.nextToken();
            char c = (tok.length() == 1)? tok.charAt(0) : '\0';
 
            switch (c) {

            case '=':
                name = last;
                last = tok;
                break;

            case ',':
                name = null;
            case ' ':
                break;

            default:
                last = tok;
                if (name != null) {
                    if ("username".equalsIgnoreCase(name))
                        digest.username = tok;
                    else if ("realm".equalsIgnoreCase(name))
                        digest.realm = tok;
                    else if ("nonce".equalsIgnoreCase(name))
                        digest.nonce = tok;
                    else if ("nc".equalsIgnoreCase(name))
                        digest.nc = tok;
                    else if ("cnonce".equalsIgnoreCase(name))
                        digest.cnonce = tok;
                    else if ("qop".equalsIgnoreCase(name))
                        digest.qop = tok;
                    else if ("uri".equalsIgnoreCase(name))
                        digest.uri = tok;
                    else if ("response".equalsIgnoreCase(name))
                        digest.response = tok;
                    break;
                }
            }
        }

        return digest;
    }

    public class Digest extends Credential {
        String method=null;
        String username = null;
        String realm = null;
        String nonce = null;
        String nc = null;
        String cnonce = null;
        String qop = null;
        String uri = null;
        String response=null;
        
        Digest(String m) {
            method = m;
        }

        public String realmName() { return realm; }

        /** Compute and test digest.
         *
         * In jetty's DigestAuthenticator.Digest, this method computes
         * H(A1) from the clear-text password; HCDigest is provided
         * H(A1) instead of the clear-text password.
         */
        public boolean check(Object credentials) {
            // We only check authentication for PROPPATCH
            if (!method.equalsIgnoreCase("PROPPATCH"))
                return true;

            // The only acceptable credential is H(A1)
            if (!(credentials instanceof byte[]))
                return false;

            byte[] h1 = (byte[]) credentials;

            try {
                MessageDigest md = MessageDigest.getInstance("MD5");

                // Calculate H(A2)
                md.reset();
                md.update(getBytes(method));
                md.update((byte)':');
                md.update(getBytes(uri));
                byte[] h2 = md.digest();

                // Now calculate H( H(A1) : nonce : H(A2) )
                md.reset();
                md.update(getHexBytes(h1));
                md.update((byte)':');
                md.update(getBytes(nonce));
                md.update((byte)':');
                md.update(getHexBytes(h2));
                String hexDigest = ByteArrays.toHexString(md.digest());

                logger.info("(H(0x" + ByteArrays.toHexString(h1) + ":" +
                            nonce + ":0x" + ByteArrays.toHexString(h2) + 
                            ") = " + hexDigest + ") =?= " + response);
             
                return hexDigest.equalsIgnoreCase(response);
            }
            catch (Exception e) {
                logger.log(Level.WARNING, "Couldn't calculate digest", e);
            }
            return false;
        }

        private byte[] getBytes(String s) throws UnsupportedEncodingException {
            return s.getBytes("ISO-8859-1");
        }
        private byte[] getHexBytes(byte[] s) throws UnsupportedEncodingException {
            return ByteArrays.toHexString(s).getBytes("ISO-8859-1");
        }

        public String toString() {
            return "<Digest (" + realm + ":" + username + ") " + method +
                " \"" + uri + "\"/>";
        }

    }

    // Note: DigestAuthenticator.sendChallenge() has a bug: it doesn't
    // use commas to delimit the values. This causes some clients (but
    // not firefox) to choke.
    public void sendChallenge(UserRealm realm,
                              HttpRequest request, HttpResponse response)
            throws IOException {

        String nonce = Long.toString(request.getTimeStamp(), 27);
        String authHeader = "Digest realm=\"" + realm.getName() +
            "\", domain=\"/\", nonce=\"" +  nonce + "\"";

        response.setField(HttpFields.__WwwAuthenticate, authHeader);
        response.sendError(HttpResponse.__401_Unauthorized);
    }

}

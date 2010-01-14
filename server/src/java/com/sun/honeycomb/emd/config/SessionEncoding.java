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



package com.sun.honeycomb.emd.config;

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.common.Encoding;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ExternalObjectIdentifier;
import org.apache.commons.codec.EncoderException;


public class SessionEncoding extends Encoding{

    public static final String QUOTED_PRINTABLE_ENCODING = "QuotedPrintableEncoding";

    private static EncodingScheme defaultEncodingScheme = BASE_64;
    private static final Encoding defaultEncoding;

    static{
        ClusterProperties properties = ClusterProperties.getInstance();
        if (properties.getPropertyAsBoolean(QUOTED_PRINTABLE_ENCODING, false))
            defaultEncodingScheme = Encoding.QUOTED_PRINTABLE;
        defaultEncoding = new Encoding(defaultEncodingScheme);
    }

    private SessionEncoding(){
        this(defaultEncodingScheme);
    }

    public SessionEncoding(EncodingScheme encodingScheme){
        super(encodingScheme);
    }

    static ThreadLocal tl = new ThreadLocal();

    public static void setSessionEncoding(EncodingScheme encodingScheme){
        // Remember the session's Encoding Scheme.
        tl.set(new SessionEncoding(encodingScheme));
    }

    public String toString(){
        return encodingScheme.toString();
    }

    public static void clearEncoding(){
        tl.set(null);
    }

    public static SessionEncoding getSessionEncoding() {
        SessionEncoding encoding = (SessionEncoding) tl.get();
        assert encoding != null : encoding;
        return encoding;
    }

    public static Encoding getEncoding(){
        Encoding encoding = (Encoding) tl.get();
        if (encoding == null)
            return defaultEncoding;
        else 
            return encoding;
    }

    //OVERRIDE the method to render an ExternalObjectIdentifier as a string.
    // This way, when we send an OID back to the client, it will be in
    // Legacy 1.0.1 format.
    public String encode (ExternalObjectIdentifier oid)
        throws EncoderException {
        if (encodingScheme == LEGACY_1_0_1) {
            NewObjectIdentifier noid = 
                NewObjectIdentifier.fromExternalObjectID(oid);
            return noid.toLegacyHexString();
        } else {
            return super.encode(oid);
        }
    } // encode (ExternalObjectIdentifier)

    public NewObjectIdentifier decodeObjectIdentifierFromString(String identifierString) {
        NewObjectIdentifier result;
        if (encodingScheme == LEGACY_1_0_1) {
            result = NewObjectIdentifier.fromLegacyHexString(
                           identifierString);
        } else {
            result = NewObjectIdentifier.fromExternalHexString(
                           identifierString);
        }
        return result;
    }
}

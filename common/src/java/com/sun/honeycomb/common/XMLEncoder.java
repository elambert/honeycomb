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



package com.sun.honeycomb.common;

import java.util.Map;
import java.util.HashMap;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * A simple XML encoder.
 *
 * @author Shamim Mohamed <shamim@sun.com>
 */

public class XMLEncoder {
    // Property values when expressed in XML MUST be well formed:
    // all < and & characters MUST be quoted.
    //     http://www.w3.org/TR/2004/REC-xml-20040204/
    // Safer to encode > as well, and why not ' as &apos;
    // and " as &quot;.

    public static final String xchars = "<>&'\"";
    private static Map entities;

    static {
        entities = new HashMap();
        entities.put("amp", "&");
        entities.put("quot", "\"");
        entities.put("apos", "'");
        entities.put("lt", "<");
        entities.put("gt", ">");
    }

    static public String encode(String s) {
        if (s == null)
            return null;

        int length = s.length();

        if (length == 0)
            return "";

        StringBuffer res = new StringBuffer();

        int pos = 0;
        for (;;) {
            int c;

            // Look for a special char

            int start = pos;
            while (pos < length) {
                c = s.charAt(pos);

                if (c < 32 || c >= 127) // Black magic XXX TODO
                    break;
                if (xchars.indexOf(c) >= 0)
                    break;

                pos++;
            }

            // Put non-special chars into the buffer
            res.append(s.substring(start, pos));

            if (pos >= length)
                break;

            c = s.charAt(pos);

            switch (c) {
            case '&' : res.append("&amp;"); break;
            case '<' : res.append("&lt;"); break;
            case '>' : res.append("&gt;"); break;
            case '"' : res.append("&quot;"); break;
            case '\'': res.append("&apos;"); break;
            default  : res.append("&#" + c + ";"); break;
            }

            pos++;
        }

        return res.toString();
    }

    static public String decode(String s) throws XMLException {
        if (s == null)
            return null;

        int length = s.length();

        if (length == 0)
            return "";


        // Look for ampersands
        String[] chunks = s.split("&");
        StringBuffer res = new StringBuffer(chunks[0]);
        for (int i = 1; i < chunks.length; i++) {
            int semicolon = chunks[i].indexOf(';');
            String entity = chunks[i].substring(0, semicolon);

            // Entity names are case-significant

            if (entity.startsWith("#")) {
                try {
                    res.append((char)Integer.parseInt(entity));
                }
                catch (NumberFormatException e) {
                    throw new XMLException(e.toString());
                }
            }
            else {
                String e = (String) entities.get(entity);
                if (e == null)
                    // Write the entity undecoded into the string. This will
                    // help with debugging....
                    res.append('&').append(entity).append(';');
                else
                    res.append(e);
            }

            res.append(chunks[i].substring(semicolon + 1));
        }

        return res.toString();
    }

    public static void main(String[] args) {
        BufferedReader console =
            new BufferedReader(new InputStreamReader(System.in));
        String line;
        try {
            while ((line = console.readLine()) != null)
                if (line.startsWith("e "))
                    System.out.println(encode(line.substring(2)));
                else if (line.startsWith("d "))
                    System.out.println(decode(line.substring(2)));
                else
                    System.out.println("First char e or d");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}

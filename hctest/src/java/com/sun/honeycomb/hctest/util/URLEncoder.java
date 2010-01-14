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

/**
 * Encode a string into a URL-safe (www-form-urlencoded) form.
 */
public class URLEncoder {
    final static String[] hex = {
        "%00", "%01", "%02", "%03", "%04", "%05", "%06", "%07",
        "%08", "%09", "%0A", "%0B", "%0C", "%0D", "%0E", "%0F",
        "%10", "%11", "%12", "%13", "%14", "%15", "%16", "%17",
        "%18", "%19", "%1A", "%1B", "%1C", "%1D", "%1E", "%1F",
        "%20", "%21", "%22", "%23", "%24", "%25", "%26", "%27",
        "%28", "%29", "%2A", "%2B", "%2C", "%2D", "%2E", "%2F",
        "%30", "%31", "%32", "%33", "%34", "%35", "%36", "%37",
        "%38", "%39", "%3A", "%3B", "%3C", "%3D", "%3E", "%3F",
        "%40", "%41", "%42", "%43", "%44", "%45", "%46", "%47",
        "%48", "%49", "%4A", "%4B", "%4C", "%4D", "%4E", "%4F",
        "%50", "%51", "%52", "%53", "%54", "%55", "%56", "%57",
        "%58", "%59", "%5A", "%5B", "%5C", "%5D", "%5E", "%5F",
        "%60", "%61", "%62", "%63", "%64", "%65", "%66", "%67",
        "%68", "%69", "%6A", "%6B", "%6C", "%6D", "%6E", "%6F",
        "%70", "%71", "%72", "%73", "%74", "%75", "%76", "%77",
        "%78", "%79", "%7A", "%7B", "%7C", "%7D", "%7E", "%7F",
        "%80", "%81", "%82", "%83", "%84", "%85", "%86", "%87",
        "%88", "%89", "%8A", "%8B", "%8C", "%8D", "%8E", "%8F",
        "%90", "%91", "%92", "%93", "%94", "%95", "%96", "%97",
        "%98", "%99", "%9A", "%9B", "%9C", "%9D", "%9E", "%9F",
        "%A0", "%A1", "%A2", "%A3", "%A4", "%A5", "%A6", "%A7",
        "%A8", "%A9", "%AA", "%AB", "%AC", "%AD", "%AE", "%AF",
        "%B0", "%B1", "%B2", "%B3", "%B4", "%B5", "%B6", "%B7",
        "%B8", "%B9", "%BA", "%BB", "%BC", "%BD", "%BE", "%BF",
        "%C0", "%C1", "%C2", "%C3", "%C4", "%C5", "%C6", "%C7",
        "%C8", "%C9", "%CA", "%CB", "%CC", "%CD", "%CE", "%CF",
        "%D0", "%D1", "%D2", "%D3", "%D4", "%D5", "%D6", "%D7",
        "%D8", "%D9", "%DA", "%DB", "%DC", "%DD", "%DE", "%DF",
        "%E0", "%E1", "%E2", "%E3", "%E4", "%E5", "%E6", "%E7",
        "%E8", "%E9", "%EA", "%EB", "%EC", "%ED", "%EE", "%EF",
        "%F0", "%F1", "%F2", "%F3", "%F4", "%F5", "%F6", "%F7",
        "%F8", "%F9", "%FA", "%FB", "%FC", "%FD", "%FE", "%FF"
    };

    public static String encode(String s) {
        String plainChars = "-_.!~*'()";

        StringBuffer sbuf = new StringBuffer();
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);

            if (Character.isLetterOrDigit(c) || plainChars.indexOf(c) >= 0)
                sbuf.append(c);
            else if (c == ' ')
                sbuf.append('+');
            else if (c <= 0x007f) // low ASCII
                sbuf.append(hex[c]);
            else
                sbuf.append(encodeUTF8(c));
        }

        return sbuf.toString();
    }

    private static String encodeUTF8(char c) {
        // A non-ASCII char (16 bits) is encoded into two or three
        // octets by encoding 6 bits at a time

        if (c <= 0x07FF)
            // 00000xxxxxyyyyyy => 110xxxxx 10yyyyyy
            return hex[0xc0 | (c >> 6)] + hex[0x80 | (c & 0x3F)];

        // xxxxyyyyyyzzzzzz => 1110xxxx 10yyyyyy 10zzzzzz
        return hex[0xe0 | (c >> 12)] + hex[0x80 | ((c >> 6) & 0x3F)] +
            hex[0x80 | (c & 0x3F)];
    }

    /** Decode a url-encoded string */
    public static String decode(String s) {
        StringBuffer sbuf = new StringBuffer();

        // For multibyte characters: partial sum and how many more
        // octets to expect
        int partial = 0;
        int expect;

        for (int i = 0; i < s.length(); i++) {
            int b, c = s.charAt(i);
            expect = -1;

            switch (c) {
            case '%':
                try {
                    b = Integer.parseInt(s.substring(i+1, i+3), 16);
                }
                catch (NumberFormatException e) {
                    throw new RuntimeException(e);
                }
                i += 2;
                break;

            case '+':
                b = ' '; break;

            default:
                b = c; break;
            }

            // Now deal with UTF8

            if ((b & 0x80) == 0x00) {
                // low ASCII
                sbuf.append((char) b);
            }
            else if ((b & 0xc0) == 0x80) {
                // 10xxxxxx (continuation byte): add 6 bits to partial
                partial = (partial << 6) | (b & 0x3f);

                if (--expect == 0)
                    sbuf.append((char) partial);
            }
            else if ((b & 0xe0) == 0xc0) {
		// 110xxxxx => 5 bits, expect one more byte
                partial = b & 0x1f;
                expect = 1;
            }
            else if ((b & 0xf0) == 0xe0) {
		// 1110xxxx => 4 bits, expect two more
                partial = b & 0x0f;
                expect = 2;
            }

            else if ((b & 0xf8) == 0xf0) {
		// 11110xxx => 3 bits, expect three more
                partial = b & 0x07;
                expect = 3;
            }
            else if ((b & 0xfc) == 0xf8) {
		// 111110xx => 2 bits, expect four more
                partial = b & 0x03;
                expect = 4;
            }
            else {
                // (b & 0xfe) == 0xfc --  1111110x => 1 bit, expect 5 more
                partial = b & 0x01;
                expect = 5;
            }
        }

        return sbuf.toString();
    }
}

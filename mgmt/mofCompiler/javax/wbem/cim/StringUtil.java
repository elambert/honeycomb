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


package javax.wbem.cim;

/*package*/ class StringUtil {

    // All method are static so keep the class from being instantiated.
    private StringUtil() {
    }

    /**
     * Escapes special characters in a string
     * 
     * @param inp the string to process
     * @return The string with all of the special characters escaped.
     */
    public static String quote(String inp) {
        StringBuffer sb = new StringBuffer(inp.length());
        sb.append('\"');
        sb.append(escape(inp));
        sb.append('\"');
        return sb.toString();
    }

    /**
     * Escapes special characters in a string
     * 
     * @param str the string to process
     * @return The string with all of the special characters escaped.
     */
    public static String escape(String str) {
        int size = str.length();
        StringBuffer sb = new StringBuffer(size);
        for (int i = 0; i < size; i++) {
            char ch = str.charAt(i);
            switch (ch) {
                case 0 :
                    continue;
                case '\n': sb.append("\\n");
                    break;
                case '\t': sb.append("\\t");
                    break;
                case '\b': sb.append("\\b");
                    break;
                case '\r': sb.append("\\r");
                    break;
                case '\f': sb.append("\\f");
                    break;
                case '\\': sb.append("\\\\");
                    break;
                case '\'': sb.append("\\\'");
                    break;
                case '\"': sb.append("\\\"");
                    break;
                default :
                    if ((ch = str.charAt(i)) < 0x20 || ch > 0x7e) {
                        String s = Integer.toString(ch, 16);
                        sb.append(
                            "\\x" + "0000".substring(s.length() - 4) + s);
                    } else {
                        sb.append(ch);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Removes the first level of escapes from a string
     *
     * @param inp the string to unescape
     * @return the unescaped string 
     */
    public static String unescapeString(String inp)
    {
        StringBuffer sb = new StringBuffer();
        int size = inp.length();
        for (int i = 0; i < size; i++) {
            char ch = inp.charAt(i);
            if(ch == '\\') {
                i++;
                if(i >= size) {
                    throw new IllegalArgumentException(
                        "String ended with an escape, but there was no subsequent character to escape");
                }
                ch = inp.charAt(i);
                switch (ch) {
                    case 'n': sb.append('\n');
                        break;
                    case 't': sb.append('\t');
                        break;
                    case 'b': sb.append('\b');
                        break;
                    case 'r': sb.append('\r');
                        break;
                    case 'f': sb.append('\f');
                        break;
                    case '\\': 
                    case '\'': 
                    case '\"': sb.append(ch);
                        break;
                    case 'X': 
                    case 'x':
                        sb.append("\\x"); 
//?? Finish this
//                        int j = i;
//                        while(Character.digit(inp.charAt(j), 16) >= 0 && 
//                              j < (i + 4))
//                        {
//                            j++;
//                        }
//                        int value = Integer.parseInt(inp.substring(i, j), 16);
//                        i = j;  
//                        sb.append((char)value);
                        break;
                    default : throw new IllegalArgumentException(
                        "Invalid escape sequence '" + ch + 
                        "' (valid sequences are  \\b  \\t  \\n  \\f  \\r  \\\"  \\\'  \\\\ \\x0000 \\X0000 )");
                }
            }
            else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
    
    /**
     * Removes the first level of quotes and escapes from a string
     *
     * @param value the string to unquote
     * @return the unquoted string 
     */
    public static String unquote(String value) {
        if(value.startsWith("\"")) {
            if(value.endsWith("\"")) {
                value = unescapeString(value.substring(1, value.length() - 1));
            } else {
                throw new IllegalArgumentException("String literal " + value + " is not properly closed by a double quote.");
            }
        }
        return value;
    }
}

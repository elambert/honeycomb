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

import java.sql.Time;
import java.sql.Date;
import java.sql.Timestamp;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.net.QuotedPrintableCodec;
import org.apache.commons.codec.binary.Base64;

import java.util.Arrays;
import java.util.logging.Logger;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

/**
 * Consolidate the XML encodings used by both client and server.
 */
public class Encoding {

    private static final Logger LOG = Logger.getLogger(Encoding.class.getName());

  public Encoding(){
      this(BASE_64);
  }
  public Encoding(EncodingScheme encodingScheme){
    this.encodingScheme = encodingScheme;
  }

  //     * Long -- L followed by a signed decimal value
  //     * Double -- D followed by exactly 16 hex digits   
  //     * Char - C followed by quoted-printable encoded 8-bit character values
  //     * String - S followed by quoted-printable encoded character values that may
  //       include Unicode characters
  //     * Timestamp - T followed by a signed decimal value representing microseconds
  //       since the Epoch.
  //     * Date - d followed by a signed decimal value representing days since the Epoch
  //     * Time - t followed by an unsigned decimal value representing the number of
  //       microseconds (e.g. past midnight)
  //     * Binary - B followed by a BASE64-encoded string

  public final static char LONG_PREFIX =      'L';
  public final static char DOUBLE_PREFIX =    'D';
  public final static char CHAR_PREFIX =      'C';
  public final static char STRING_PREFIX =    'S';
  public final static char TIMESTAMP_PREFIX = 'T';
  public final static char DATE_PREFIX =      'd';
  public final static char TIME_PREFIX =      't';
  public final static char BINARY_PREFIX =    'B';
  public final static char OBJECTID_PREFIX =  'X';
  public final static char NULL_PREFIX =      'N';

  static QuotedPrintableCodec qpCodec = new QuotedPrintableCodec();
  protected static Base64 base64Codec= new Base64();

  
  protected static class EncodingScheme{
    String name;
    private EncodingScheme(String name){
      this.name = name;
   }
    public String toString(){return name;}
  }
    public static final EncodingScheme QUOTED_PRINTABLE = 
        new EncodingScheme("QuotedPrintable");
    public static final EncodingScheme BASE_64 = 
        new EncodingScheme("base64");
    public static final EncodingScheme IDENTITY = 
        new EncodingScheme("Identity");
    public static final EncodingScheme LEGACY_1_0_1 = 
        new EncodingScheme("Legacy_1.0.1");

  protected EncodingScheme encodingScheme;

  public void setEncoding(EncodingScheme encodingScheme){
    this.encodingScheme = encodingScheme;
  }

    public EncodingScheme getEncodingScheme() {
        return encodingScheme;
    }

    public boolean isLegacyEncoding() {
        return encodingScheme == LEGACY_1_0_1;
    }

  /** 
   * The subclass may want to override this method to call the appropriately 
   * typed decoder 
  */
  public Object decode(String encoded)
      throws DecoderException{

      if (encodingScheme == IDENTITY ||
          encodingScheme == LEGACY_1_0_1)
          return encoded;               // Always return as a String -- no decoding

      char typeChar = encoded.charAt(0);
      switch (typeChar) {
      case LONG_PREFIX:
          return decodeLong(encoded);
      case DOUBLE_PREFIX:
          return decodeDouble(encoded);
      case CHAR_PREFIX:
          return decodeChar(encoded);
      case STRING_PREFIX:
          return decodeString(encoded);
      case TIMESTAMP_PREFIX:
          return decodeTimestamp(encoded);
      case DATE_PREFIX:
          return decodeDate(encoded);
      case TIME_PREFIX:
          return decodeTime(encoded);
      case BINARY_PREFIX:
          return decodeBinary(encoded);
      case OBJECTID_PREFIX:
          return decodeObjectID(encoded);
      default:
          throw new DecoderException("Unrecognized prefix for encoded value string '"+encoded+"'");

      }
  }


  /** 
   * The subclass should override this methods to call the appropriately 
   * typed encoder after looking up the feild's type in the schema.
  */
  //--> Should we just examine the class of the object and cast
  //  it to get the correct encoder without consulting the schema? 
  public String encode(Object o)
      throws EncoderException {

      if (o instanceof Long)
          return encode ((Long)o);
      else if (o instanceof Double)
          return encode ((Double)o);
      else if (o instanceof String)
          return encode((String)o);
      else if (o instanceof Time)
          return encode ((Time)o);
      else if (o instanceof Timestamp)
          return encode ((Timestamp)o);
      else if (o instanceof Date)
          return encode ((Date)o);
      else if (o instanceof byte[])
          return encode ((byte[])o);
      else if (o instanceof ExternalObjectIdentifier)
          return encode((ExternalObjectIdentifier)o);
      else
          throw new IllegalArgumentException("Unsupported type " + o.getClass());
  }

  public String encodeName(String s) throws EncoderException{
    return encodeString(s);
  }


  public String decodeName(String s) throws DecoderException{
    return decodeStringNoChecking(s);
  }




    /////////////////////////
    // Helper methods
    ///////////////////////



  public String toString(){
        return encodingScheme.toString();
  }

  private String encodeString(String s)
    throws EncoderException{
    String string = s;

      if (encodingScheme.equals(QUOTED_PRINTABLE))
        return qpCodec.encode(string);
      else if (encodingScheme.equals(BASE_64)) {
          String t;
          byte[] strBytes;
          strBytes = CanonicalEncoding.strToUTF8(string); 
          t = new String(base64Codec.encode(strBytes));
          return t;
      }
      else
        return string;
  }

  public String encode (Long l)
    throws EncoderException{
    if (encodingScheme == IDENTITY ||
        encodingScheme == LEGACY_1_0_1)
      return l.toString();
    else
      return LONG_PREFIX + l.toString();
  }

  private final static String pad(String s){
        int l = s.length();
        if (l == 16)
            return s;
        char[] c = new char[16];
        int offset = 16 - l;
        Arrays.fill(c, 0, offset, '0');
        for (int i = 0; i < l; i++)
            c[offset + i] = s.charAt(i);
        return new String(c);
    }

  public String encode (Double d)
    throws EncoderException{
    if (encodingScheme == IDENTITY ||
        encodingScheme == LEGACY_1_0_1)
      return d.toString();
    else
      return DOUBLE_PREFIX + pad(Long.toHexString(Double.doubleToLongBits(d.doubleValue())));
  }

  public String encode (String s)
    throws EncoderException{
    return encode(s, true);
  }

  public String encode (String s, boolean unicode)
    throws EncoderException{
    if (encodingScheme == IDENTITY ||
        encodingScheme == LEGACY_1_0_1){
      return s;
    }
    else{
      String e = encodeString(s);
      if (unicode)
        return STRING_PREFIX + e;
      else 
        return CHAR_PREFIX + e;
    }
  }

  public String encode (Time t)
      throws EncoderException{
      String value = CanonicalEncoding.encode(t);
    if (encodingScheme == IDENTITY ||
        encodingScheme == LEGACY_1_0_1)
        return value;
    else
        return TIME_PREFIX + value;
  }
  public String encode (Timestamp ts)
    throws EncoderException{
      String value = CanonicalEncoding.encode(ts);
      if (encodingScheme == IDENTITY ||
          encodingScheme == LEGACY_1_0_1)
          return value;
      else
          return TIMESTAMP_PREFIX + value;
  }
  public String encode (Date d)
    throws EncoderException{
      String value = CanonicalEncoding.encode(d);
      if (encodingScheme == IDENTITY ||
          encodingScheme == LEGACY_1_0_1)
          return value;
      else
          return DATE_PREFIX + value;
  }
  public String encode (byte[] binary)
    throws EncoderException{
    if (encodingScheme == IDENTITY ||
        encodingScheme == LEGACY_1_0_1)
        return CanonicalEncoding.encode(binary);
    else
      return BINARY_PREFIX + new String(base64Codec.encode(binary));
  }

  public String encode (ExternalObjectIdentifier oid)
    throws EncoderException{
    if (encodingScheme == IDENTITY ||
        encodingScheme == LEGACY_1_0_1)
      return oid.toString();
    else
      return OBJECTID_PREFIX + new String(base64Codec.encode(oid.toByteArray()));
  }



  private final static void checkPrefix(String val, char required)
    throws DecoderException {
    char observed = val.charAt(0);
    if (required != observed)
      throw new DecoderException ("Bad prefix for " + val + ": " +
                                  observed + " (expected " + required + ")");
  }


  public final Long decodeLong(String encoded)
    throws DecoderException {
    checkPrefix(encoded, LONG_PREFIX);
    return new Long(encoded.substring(1));
  }


  public final Double decodeDouble(String encoded) 
      throws DecoderException {
      checkPrefix(encoded, DOUBLE_PREFIX);

      // This check is necesary because there is no built-in inverse of Long.toHexString
      // to load the binary value back in to a long; the base 16 parse will interpret the 
      // first bit as a sign. Thus we resort to bit twiddling for that case:

      long l = (encoded.length() == 17) ?
          Long.parseLong(encoded.substring(1, 9), 16) << 32 | Long.parseLong(encoded.substring(9, 17), 16) :
      Long.parseLong(encoded.substring(1), 16);
      return new Double(Double.longBitsToDouble(l));
  }


  public final String decodeChar(String encoded)
    throws DecoderException{
    checkPrefix(encoded, CHAR_PREFIX);
    return decodeStringNoChecking(encoded.substring(1));
  }

  public final String decodeString(String encoded)
    throws DecoderException{
    checkPrefix(encoded, STRING_PREFIX);
    return decodeStringNoChecking(encoded.substring(1));
  }

  public final String decodeStringNoChecking(String encoded)
    throws DecoderException{
    String s;
    if (encodingScheme == QUOTED_PRINTABLE)
      s = qpCodec.decode(encoded);
    else if (encodingScheme == BASE_64) {
        s = CanonicalEncoding.utf8ToString(base64Codec.decode(encoded.getBytes())); 
    }
    else if (encodingScheme == IDENTITY ||
             encodingScheme == LEGACY_1_0_1)
        s = encoded;
    else throw new RuntimeException("Unrecognized EncodingScheme: "+encodingScheme);

    return s;
  }

  public final Timestamp decodeTimestamp(String encoded)
    throws DecoderException {
    checkPrefix(encoded, TIMESTAMP_PREFIX);
    return CanonicalEncoding.decodeTimestamp(encoded.substring(1));
  }
  public final Date decodeDate(String encoded)
    throws DecoderException {
    checkPrefix(encoded, DATE_PREFIX);
    return CanonicalEncoding.decodeDate(encoded.substring(1));
  }
  public final Time decodeTime(String encoded)
    throws DecoderException {
    checkPrefix(encoded, TIME_PREFIX);
    return CanonicalEncoding.decodeTime(encoded.substring(1));
  }

  public final byte[] decodeBinary(String encoded)
    throws DecoderException {
    checkPrefix(encoded, BINARY_PREFIX);
    // --> Some sort of stream would be better, since strings are immutable...
    return base64Codec.decode(encoded.substring(1).getBytes());
  }

  public final ExternalObjectIdentifier decodeObjectID(String encoded)
    throws DecoderException {
    checkPrefix(encoded, OBJECTID_PREFIX);
    return new ExternalObjectIdentifier(
                  base64Codec.decode(encoded.substring(1).getBytes()));
  }
    
  private static class IdentityEncoding extends Encoding{
    private IdentityEncoding(){
      super(IDENTITY);
    }
    public String encode (Object o, String name) throws EncoderException{
        if (o == null)
            return "";
        else
            return CanonicalEncoding.encode(o);
    }

    public Object decode(String encoded) throws DecoderException{
      return encoded;
    }


    public void setEncoding(EncodingScheme encodingScheme){
        throw new RuntimeException("Cannot set encoding on static class Identity Encoder");
    }

  }

    public static char getTagChar(Object obj) {
        if (obj instanceof String) {
            return STRING_PREFIX;
        } else if (obj instanceof Long) {
            return LONG_PREFIX;
        } else if (obj instanceof Double) {
            return DOUBLE_PREFIX;
        } else if (obj instanceof Date) {
            return DATE_PREFIX;
        } else if (obj instanceof Time) {
            return TIME_PREFIX;
        } else if (obj instanceof Timestamp) {
            return TIMESTAMP_PREFIX;
        } else if (obj instanceof byte[]) {
            return BINARY_PREFIX;
        } else if (obj instanceof ExternalObjectIdentifier) {
            return OBJECTID_PREFIX;
        } else if (obj == null) {
            return NULL_PREFIX;
        } else {
            throw new IllegalArgumentException("object of unhandled type: "+
                                               obj.getClass().getCanonicalName());
        }
    }

    static class Test{

        Encoding encoding = new Encoding(){
                public String encode(Object o){
                    throw new RuntimeException();
                }
                public Object decode(String name){
                    throw new RuntimeException();
                }
            };
                
        void test (String s) throws Exception {
            PrintWriter out = 
                new PrintWriter(new OutputStreamWriter(System.err,"UTF-8"));
            out.println(s + ": " + encoding.encode(s) + " " + encoding.decodeString(encoding.encode(s)) + " " +
                               s.equals((encoding.decodeString(encoding.encode(s)))));
            out.flush();
        }

        void test (Date d) throws Exception{
            System.err.println(d + ": " + encoding.encode(d) + " " + encoding.decodeDate(encoding.encode(d)) + " " +
                               d.equals((encoding.decodeDate(encoding.encode(d)))));
        }

        void test (Time t) throws Exception{
            System.err.println(t + ": " + encoding.encode(t) + " " + encoding.decodeTime(encoding.encode(t)) + " " +
                               t.equals(encoding.decodeTime(encoding.encode(t))));
        }

        void test (Timestamp t) throws Exception{
            System.err.println(t + ": " + encoding.encode(t) + " " + encoding.decodeTimestamp(encoding.encode(t)) + " " +
                               t.equals(encoding.decodeTimestamp(encoding.encode(t))));
        }

        private void test() throws Exception{
            test (new Date(System.currentTimeMillis()));
            test (new Time(System.currentTimeMillis()));
            test (new Timestamp(System.currentTimeMillis()));
            test ("\u043c\u043d\u0435 \u043d\u0435 \u0432\u0440\u0435\u0434\u0438\u0442");

            //System.err.println(encoding.encode(new Double(1d)));

            //         System.err.println(new Long("12345"));
            //         System.err.println(encoding.encode(new Long(12345l)));
            //         System.err.println(encoding.encode(new Long("12345")));
            //         System.err.println(encoding.encode(new Double(-1.1d)));
            //         System.err.println(encoding.decodeDouble(encoding.encode(new Double(-1.1d))));
            //         System.err.println(encoding.encode(new Double(Math.PI)));
            //         System.err.println(encoding.decodeDouble(encoding.encode(new Double(Math.PI))));
            //System.err.println(encoding.decodeDouble(argv[0]));
        }
    }

  public final static IdentityEncoding identityEncoding = new IdentityEncoding();
  public final static Encoding base64Encoding = new Encoding(BASE_64);
  public final static Encoding quotedPrintableEncoding =
                      new Encoding(QUOTED_PRINTABLE);


    private void test(Object o) throws Exception{
        String encoded = encode(o);
        Object decoded = decode(encoded);
        if (!o.equals(decoded))
            throw new Exception (o + " encoded as " + encoded + ", decoded as " + decoded);
    }

    public static void main (String[] argv) throws Exception{
        new Test().test();
        Encoding e = new Encoding();
        e.test(new Long(Long.MAX_VALUE));
        e.test(new Long(Long.MIN_VALUE));
        e.test(new Double(Double.MAX_VALUE));
        e.test(new Double(Double.MIN_VALUE + 1));
        e.test(new Double(Double.MIN_VALUE));
        e.test(new Double(1));
        System.err.println(e.encode(new Long(0x123456789abcl)));
        System.err.println(e.encode(new Double(Double.longBitsToDouble(0x123456789abcl))));
        e.test(new Double(Double.longBitsToDouble(0x123456789abcl)));

    }
}

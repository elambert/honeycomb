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

import com.sun.honeycomb.common.LogEscapeFormatter;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.io.BufferedWriter;

import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.parsers.SAXParser;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.DecoderException;

public class NameValueXML {



  final static SAXParserFactory factory = SAXParserFactory.newInstance();

  /**********************************************************************
   *
   * Static methods to be used by external users
   *
   **********************************************************************/
    

  public static String escape (String s){
    if (s == null ||
        (s.indexOf("\"") == -1 && 
         s.indexOf("'") == -1 && 
         s.indexOf("&") == -1 && 
         s.indexOf("<") == -1 && 
         s.indexOf(">") == -1))
      return s;

    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < s.length(); i++){
      if (s.charAt(i) == '"')
        sb.append ("&quot;");
      else if (s.charAt(i) == '\'')
        sb.append ("&apos;");
      else if (s.charAt(i) == '&')
        sb.append ("&amp;");
      else if (s.charAt(i) == '<')
        sb.append ("&lt;");
      else if (s.charAt(i) == '>')
        sb.append ("&gt;");
      else 
        sb.append (s.charAt(i));
    }    
    return sb.toString();
  }

  private final static void writeAttribute(Writer writer,
                                           String name,
                                           String value)
    throws IOException {
    writer.write(" ");
    writer.write(name);
    writer.write("=\"");
    writer.write(value);
    writer.write("\"");
  }

  public static void createXML(Map md,
                               OutputStream output) throws IOException{
    createXML(md, output, Encoding.identityEncoding);
  }


  //--> Merge with com.sun.honeycomb.protocol.server.MetadataXML
  public static void createXML(Map md,
                               OutputStream output,
                               Encoding encoder)
    throws IOException {
    createXML(md, output, encoder, TAG_RELATIONAL);
  }


  private static interface Walker{
    boolean next();
    String name();
    String value();
  }

  private static class MapWalker implements Walker{

    private Map map;
    private Iterator keys;
    private boolean eof = false;
    private boolean qp = false;
    private String currentName;
    private String currentValue;
    String unencodedName;
    String unencodedValue;
    private Encoding encoder;

    MapWalker (Map map, Encoding encoder){
      this.map = map;
      this.encoder = encoder;
      keys = map.keySet().iterator();
    }

    public boolean next(){
      if (keys.hasNext()){
        // convert to encoded value which preserves double values and 
        // maps illegal XML characters to innocuous pritable characters.
        unencodedName = (String)keys.next();
        Object value  = map.get(unencodedName);
        if (value == null)
            throw new RuntimeException("Cannot encode null values (field "+
                                       unencodedName+")");
        unencodedValue = CanonicalEncoding.encode(value);
        try{
          currentValue = encoder.encode(value);
          currentName = encoder.encodeName(unencodedName);
        }
        catch (EncoderException e){throw new RuntimeException(e);}
        return true;
      }
      else {
        eof = true;
        return false;
      }
    } // next

    public String name(){
      if (eof)
        throw new RuntimeException ("Read past end");
      else
        return currentName;
    }

    public String value(){
      if (eof)
        throw new RuntimeException ("Read past end");
      else
        return currentValue;
    }
  } // MapWalker



  public static void createXML(Map md,
                               OutputStream output,
                               String documentName) 
    throws IOException {
    createXML(md,
              output,
              Encoding.identityEncoding,
              documentName,
              false);
  }

  public static void createXML(Map md,
                               OutputStream output,
                               Encoding encoder,
                               String documentName) 
      throws IOException {
      createXML(md,
                output,
                encoder,
                documentName,
                true);
  }

  public static void createXML(Map md,
                               OutputStream output,
                               Encoding encoder,
                               String documentName,
                               boolean includeVersion) 
    throws IOException {
    createXML(new MapWalker (md, encoder),
              output,
              documentName, 
              includeVersion);
  }


  public static void createXML(MapWalker md,
                               OutputStream output,
                               String documentName,
                               boolean includeVersion) 
    throws IOException {

    Writer writer = new BufferedWriter(new OutputStreamWriter(output));

    writer.write(XML_HEADER); 
    writer.write("\n"); 

    writer.write("<");
    writer.write(documentName);
    writer.write(">\n");

    if (includeVersion){
        writer.write("  <");
        writer.write(TAG_VERSION);
        writeAttribute(writer, ATTRIBUTE_VALUE, HONEYCOMB_VERSION);
        writer.write("/>\n");
    }

    writer.write("  <");
    writer.write(TAG_ATTRIBUTES);
    if (md.encoder.encodingScheme != Encoding.IDENTITY){
      writeAttribute(writer, ENCODING, HONEYCOMB_VERSION);
      if (md.encoder.encodingScheme == Encoding.QUOTED_PRINTABLE)
        writeAttribute(writer, QUOTED_PRINTABLE, ProtocolConstants.TRUE);
    }
    writer.write(">\n");

    while (md.next()) {
      String name = md.name();
      String value = md.value();
      if (name == null) {
        throw new IllegalStateException("Attribute name cannot be null");
      }
      else if (value == null) {
        //throw new IllegalStateException("Attribute value cannot be null for '" + name + "'");
        continue;
      }
      writer.write("    <");
      writer.write(TAG_ATTRIBUTE);
      writeAttribute(writer, ATTRIBUTE_NAME, name);
      writeAttribute(writer, ATTRIBUTE_VALUE, value);
      writer.write("/>\n");
    }

    writer.write("  </");
    writer.write(TAG_ATTRIBUTES);
    writer.write(">\n");

    writer.write("</");
    writer.write(documentName);
    writer.write(">\n");

    writer.flush();
  }

    /* If the XML is encoded, this method will fail */

   public static Map parseXML(InputStream input) throws XMLException{
     return parseXML(input, Encoding.identityEncoding);
   }

  // --> update server/src/java/com/sun/honeycomb/emd/cache/CacheUtils.java
  public static Map parseXML(InputStream input, final Encoding decoder) throws XMLException{
    final Map map = new HashMap();
    // default; overrride if version is detected in xml
    class Handler extends DefaultHandler{

      int majorVersion = -1;
      int minorVersion = 0;
      Encoding dc = Encoding.identityEncoding;

      public void startElement(String uri,
                               String localName,
                               String qName,
                               Attributes attributes)
        throws SAXException{


        if (TAG_VERSION.equals(qName)) {
          String v = attributes.getValue(ATTRIBUTE_VALUE);
          if (v != null){
            int dot = v.indexOf(".");
            if (dot == -1){
              majorVersion = Integer.parseInt(v);
            }
            else{
              majorVersion = Integer.parseInt(v.substring(0, dot));
              minorVersion = Integer.parseInt(v.substring(dot+1));
            }
            if (majorVersion > 1 || majorVersion == 1 && minorVersion >= 1){
                dc = Encoding.base64Encoding;
                String s = attributes.getValue(QUOTED_PRINTABLE);
                if (ProtocolConstants.TRUE.equalsIgnoreCase(s)) {
                    dc = Encoding.quotedPrintableEncoding;
                }

            }
          }
        }

        else if (TAG_ATTRIBUTE.equals(qName)){
          String name = attributes.getValue(ATTRIBUTE_NAME);
          String value = attributes.getValue(ATTRIBUTE_VALUE);
          try{
            name = dc.decodeName(name);
            map.put(name, dc.decode(value));
//            System.err.println("decoded " + name + " " + dc.decode(value) + " " + dc);
          }
          catch (DecoderException de){
            throw new RuntimeException (de);
          }
        }
      }
    }

    final Handler handler = new Handler();
    try{
      SAXParser parser = factory.newSAXParser();
      parser.parse(input, handler);
    }
    catch (SAXException sa){
      throw new XMLException (sa);
    }
    catch (ParserConfigurationException pce){
      throw new RuntimeException (pce);
    }
    catch (IOException sa){
      throw new XMLException (sa);
    }

//     if (handler.majorVersion == -1) {
//       throw new XMLException("The XML has an unknown version");
//     }
//     if (handler.majorVersion != 1 || (handler.minorVersion != 1 && handler.minorVersion != 0)) {
//       throw new XMLException("Unsupported XML version ["+ handler.majorVersion + "." + 
//                              handler.minorVersion+"]");
//     }
    return map;
  }


  /********************************************************************
   *
   * Public constants
   *
   ********************************************************************/
        
  public static final String HONEYCOMB_VERSION = "1.1";

  public static final String TAG_RELATIONAL  = "relationalMD";    
  public static final String TAG_SYSTEM  = "systemMD";
  public static final String TAG_VERSION     = "version";
  public static final String TAG_MINOR_VERSION     = "minor-version";
  public static final String TAG_ATTRIBUTES  = "attributes";
  public static final String TAG_ATTRIBUTE   = "attribute";
  public static final String ATTRIBUTE_NAME  = "name";
  public static final String ATTRIBUTE_VALUE = "value";

  public static final String ENCODING = "encoding";
  public static final String QUOTED_PRINTABLE = "quoted-printable";
  public static final String BASE64 = "base64";

  // override version defaults for readability
  public static final String UNICODE_ENCODING = "unicode-encoding";


  // Other XML response tags
  public static final String PREPARED_STATEMENT_TAG = "Prepared-Statement";
  public static final String SQL_NAME = "sql";
  public static final String KEY_TAG = "key";
  public static final String PARAMETER_TAG = "parameter";
  public static final String PARAMETER_VALUE = "value";
  public static final String PARAMETER_INDEX = "index";
  public static final String SELECT_TAG = "select";
  public static final String SELECT_VALUE = "value";

  public static final String QUERY_PLUS_RESULTS_TAG = "Query-Plus-Results";
  public static final String QUERY_RESULTS_TAG = "Query-Results";
  public static final String QUERY_RESULT_TAG = "Result";
  public static final String COOKIE_TAG = "Cookie";
  public static final String QUERY_INTEGRITY_TIME_TAG = "Query-Integrity-Time";

  // --> Is version 1.0 correct?
  public static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

  private static String snarf (String path) throws Exception{
    StringBuffer sb = new StringBuffer();
    InputStream fis = new java.io.FileInputStream(path);
    int c = fis.read();
    while (c != -1){
      sb.append(c);
      c = fis.read();
    }
    return sb.toString();
  }

  public static void main (String[] argv) throws Exception{
    int dot = argv[0].indexOf(".");
    int majorVersion = Integer.parseInt(argv[0].substring(0, dot));
    int minorVersion = Integer.parseInt(argv[0].substring(dot+1));
    System.out.println(majorVersion +  " " + minorVersion);
    //parseXML(new java.io.FileInputStream("/tmp/mdr"));
  }

}

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


package com.sun.honeycomb.client;

import com.sun.honeycomb.common.Encoding;
import com.sun.honeycomb.common.ExternalObjectIdentifier;
import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.client.NameValueSchema;
import com.sun.honeycomb.client.NameValueSchema.ValueType;

import org.apache.commons.codec.EncoderException;

import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;


/**
 * Used to implement queries with dynamic parameters as described 
 * in the Client API Reference guide. Dynamic parameters are the 
 * preferred way to pass typed data items to a @HoneycombProductName@ query, though you can 
 * also use typed literals as described in that same chapter.
 *
 * The number of <code>bindParameter</code> calls should match the number of 
 * question marks (<code>?</code>) in the query string in the prepared statement. 
 * Parameters are specified positionally. For example, a bindParameter call with 
 * <code>index</code> = 1 supplies a value for the first ? in the supplied query 
 * string.
 *
 * Once a value has been supplied for each of the dynamic parameters, then 
 * the PreparedStatement may be passed to the NameValueObjectArchive.query 
 * method to be executed.
 *
 * <p><code>NameValueObjectArchive archive = new NameValueObjectArchive(hostname);<br>
 * Date date_value= new java.sql.Date(); <br>
 * PreparedStatement stmt = new PreparedStatement("system.test.type_date&lt;?"); <br>
 * stmt.bindParameter(date_value,1); <br>
 * QueryResultSet qrs = archive.query(stmt); </code></p>
 */
public class PreparedStatement extends Encoding{


    private ArrayList parameters = new ArrayList();
    private ArrayList selects = new ArrayList();
    private String sql;

    public PreparedStatement (String sql){
        if (sql == null) {
            throw new IllegalArgumentException("Query cannot be null.");
        }
        this.sql = sql;
    }

    class Parameter implements Comparator{

        int index;
        Object parameter;

        Parameter(Object parameter, int index){
            this.index = index;
            this.parameter = parameter;
        }

         String serialize() throws IOException, EncoderException{
             return "  <" + NameValueXML.PARAMETER_TAG + " " +
                 NameValueXML.PARAMETER_INDEX + "=\"" + index + 
                 "\" " + NameValueXML.PARAMETER_VALUE + "=\"" + encode(parameter) + "\"/>\n";
         }

         public int compare(Object o1, Object o2){
             int i1 = ((Parameter)o1).index;
             int i2 = ((Parameter)o2).index;
             if (i1 == i2) return 0;
             else if (i1 > i2) return 1;
             else return -1;
         }
    }


    /** 
     * Supply a value of an arbitrary Java Object type for the 
     * specified dynamic parameter in the query string. Expected 
     * java types include:
     * <p>
     * <code>java.lang.String</code> (for String and Char query parameters.)  
     * <p>A string value may also be supplied for any dynamic parameter 
     * that expects a typed value. The string value is translated to 
     * a value of the appropriate type using the "canonical string format". 
     * The one exception to this rule is that a string value cannot be 
     * correctly translated to a value to be compared with the field 
     * <code>system.object_id</code>. That is why there is a separate 
     * method called <code>bindObjectID</code>.</p>
     * <code>byte[]</code> (for Binary query parameters)<br>
     * <code>java.lang.Double</code> (for Double query parameters)<br>
     * <code>java.lang.Long</code> (for Long query parameters)<br>
     * <code>java.sql.Date</code> (for Date query parameters)<br>
     * <code>java.sql.Time</code>(for Time query parameters)<br>
     * <code>java.sql.Timestamp</code> (for Timestamp query parameters)
     * </p>
     */
    public void bindParameter(Object o, int index){
        parameters.add(new Parameter(o, index));
    }

    /** 
     * Supply a value of <code>long</code> type for the specified
     * dynamic parameter specified by <code>index</code>.
     */
    public void bindParameter(long l, int index) {
        bindParameter(new Long(l),index);
    }
    /** 
     * Supply a value of <code>double</code> type for the specified
     * dynamic parameter specified by <code>index</code>.
     */
    public void bindParameter(double d, int index) {
        bindParameter(new Double(d),index);
    }
    /**
     * Supply an <code>ObjectIdentifier</code> value as the value for
     * dynamic parameter specified by <code>index</code>. This is the only
     * supported way to pass an <code>objectid</code> value as a dynamic 
     * parameter for a query that will select a value of the 
     * <code>system.object_id</code> field. In particular, it is NOT 
     * supported to use <code>bindParameter</code> to assign a value that 
     * will be compared against the field <code>system.object_id</code>.
     */
    public void bindObjectID(byte[] oidBytes, int index) {
        bindParameter(new ExternalObjectIdentifier(oidBytes),index);
    }

    /**
     * Supply an <code>ObjectIdentifier</code> value as the value for
     * dynamic parameter specified by <code>index</code>. This is the only
     * supported way to pass an <code>objectid</code> value as a dynamic 
     * parameter for a query that will select a value of the 
     * <code>system.object_id</code> field. In particular, it is NOT 
     * supported to use <code>bindParameter</code> to assign a value that 
     * will be compared against the field <code>system.object_id</code>.
     */
    public void bindObjectID(String oidStr, int index) {
        bindParameter(new ExternalObjectIdentifier(oidStr), index);
    }

    void addSelect(String select){
        selects.add(select);
    }
    boolean isSelect(){
        return selects.size() > 0;
    }

    String key = null;

    void setKey(String key){
        if (key == null || key.length() == 0)
            throw new IllegalArgumentException ("Invalid key");
        this.key = key;
    }

    public String serialize () throws IOException, EncoderException{
        StringBuffer sb = new StringBuffer();
        sb.append("<" + NameValueXML.PREPARED_STATEMENT_TAG + " " + 
                  NameValueXML.SQL_NAME + "=\"" + encode(sql) + "\">\n");
        if (selects.size() != 0){
            for (int i = 0; i < selects.size(); i++){
                sb.append("  <" + NameValueXML.SELECT_TAG + " " + 
                          NameValueXML.SELECT_VALUE + "=\"" + encodeName((String)selects.get(i)) + "\"/>\n");
            }
        }
        else if (key != null){
            sb.append("  <" + NameValueXML.KEY_TAG + " " +
                      NameValueXML.PARAMETER_VALUE + "=\"" + encodeName(key) + "\"/>\n");
        }
        for (int i = 0; i < parameters.size(); i++){
            sb.append(((Parameter)parameters.get(i)).serialize());
        }
        sb.append("</" + NameValueXML.PREPARED_STATEMENT_TAG + ">\n");
        return sb.toString();
    }

/* 
 * Commenting out main so this does not show up in the javadocs.
 *    public static void main (String[] argv) throws Exception{
 *        PreparedStatement ps = new PreparedStatement("bar.bleh\u0393 != ?");
 *        ps.bindParameter(new Double(3.14d), 1);
 *        ps.addSelect("foo");
 *        System.out.println(ps.serialize());
 *    }
 */

}

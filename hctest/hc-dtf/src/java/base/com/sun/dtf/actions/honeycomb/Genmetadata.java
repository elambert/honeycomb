package com.sun.dtf.actions.honeycomb;

import com.sun.dtf.actions.reference.Referencable;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;

/**
 * @dtf.tag genmetadata
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc The genmetadata operation is used to generate specific types of
 *               metadata records from a list of predefined types. 
 *              
 * @dtf.tag.example 
 * <hcquery datavip="mycluster.domain"
 *          port="8080">
 *     <genmetadata type="simple"
 *                  oid="${object.oid}"
 *                  fields="mp3.title">
 *           <metadata>
 *               <element value="${hc.mp3.title.regexp}"  type="string" name="mp3.title"/> 
 *               <element value="${hc.mp3.artist.regexp}" type="string" name="mp3.artist"/> 
 *               <element value="${hc.mp3.album.regexp}"  type="string" name="mp3.album"/> 
 *               <element value="${hc.mp3.date.regexp}"   type="long"   name="mp3.date"/> 
 *               <element value="${hc.mp3.type.regexp}"   type="string" name="mp3.type"/>
 *           </metadata>
 *     </genmetadata>
 * </hcquery> 
 * 
 * @dtf.tag.example 
 * <hcquery datavip="mycluster.domain"
 *          port="8080">
 *     <genmetadata type="empty"
 *                  oid="${object.oid}"
 *                  fields="na">
 *           <metadata/>
 *     </genmetadata>
 * </hcquery> 
 */
public class Genmetadata extends Referencable {
    
    public final static String SIMPLE   = "simple";
    public final static String COMPLEX2 = "complex2";
    public final static String UNIQUE   = "unique";
    public final static String EMPTY    = "empty";
   
    /**
     * @dtf.attr type 
     * @dtf.attr.desc The type defines how the generated query looks like and 
     *                has the following available types:
     *                
     *               <table border="1">
     *                   <tr>
     *                       <th>Type</th> 
     *                       <th>Description</th> 
     *                   </tr>
     *                   <tr>
     *                       <td>simple</td> 
     *                       <td>Simple query will generate a simple equality
     *                           query on the field specified with the fields
     *                           attribute.</td> 
     *                   </tr>
     *                   <tr>
     *                       <td>complex2</td> 
     *                       <td>Complex2 queries are an equality query with 1 
     *                           and of 2 fields specified with the fields 
     *                           attribute.</td> 
     *                   </tr>
     *                   <tr>
     *                       <td>unique</td> 
     *                       <td>The unique will gaurantee to return only 1 
     *                           result since it queries on the OID of the object
     *                           specified.</td> 
     *                   </tr>
     *                   <tr>
     *                       <td>empty</td> 
     *                       <td>empty query will use 1 of the fields specified
     *                           in the fields attribute and generate a query
     *                           that could never return any result. </td> 
     *                   </tr>
     *               </table>
     */
    private String type = null;

    /**
     * @dtf.attr oid
     * @dtf.attr.desc OID of the object being queried on. Only used in the 
     *                unique query generation.
     */
    private String oid = null;
    
    /**
     * @dtf.attr fields
     * @dtf.attr.desc Coma separated list of field names from the metadata to be
     *                used by the current type of meta data generator chosen with
     *                the type attribute.
     */
    private String fields = null;

    public String genQueryStr(NameValueObjectArchive nvoa) throws DTFException {
        StringBuffer result = new StringBuffer();
        String[] fields = getFields().split(",");
        
        if (fields.length == 0)
            throw new DTFException("fieldOrder needs to have at least 1 field identified.");
        
        /*
         * this stuff needs bettery query tests... only workig with Strings is 
         * not good enough or even a valid test. But this is the same as the old
         * smoke test.
         */
        if (getType().equals(SIMPLE)) { 
            Metadata metadata = (Metadata)findFirstAction(Metadata.class);
            NameValueRecord nvr = metadata.genNVR(nvoa);

            if (nvr.getKeys().length < 1) 
                throw new DTFException("Not enough fields in metadata.");
            
            String key = fields[0];
            result.append(key + "='" + nvr.getAsString(key) + "'");
        } else if (getType().equals(EMPTY)) { 
            String key = fields[0];
            result.append(key + "='Impossible Value'");
        } else if (getType().equals(COMPLEX2)) { 
            Metadata metadata = (Metadata)findFirstAction(Metadata.class);
            NameValueRecord nvr = metadata.genNVR(nvoa);
            
            if (nvr.getKeys().length < 2) 
                throw new DTFException("Not enough fields in metadata.");
            
            int count = 0;
            for(int i = 0; i < 2; i++) { 
                String key = fields[i];
                if (count == 1) {
                    result.append(key + "='" + nvr.getAsString(key) + "'");
                    break;
                } else {
                    result.append(key + "='" + nvr.getAsString(key) + "' AND ");
                    count++;
                }
            }
        } else if (getType().equals(UNIQUE)) { 
            result.append("system.object_id={objectid '" + getOID() + "'}");
        }
        
        return result.toString();
    }
    
    public String getType() throws ParseException { return replaceProperties(type); }
    public void setType(String type) { this.type = type; }

    public String getOID() throws ParseException { return replaceProperties(oid); }
    public void setOID(String oid) { this.oid = oid; }

    public String getFields() throws ParseException { return replaceProperties(fields); }
    public void setFields(String fields) { this.fields = fields; }
}

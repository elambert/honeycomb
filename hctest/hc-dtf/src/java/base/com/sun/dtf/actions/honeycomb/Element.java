package com.sun.dtf.actions.honeycomb;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.util.DTFProperty;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.range.Range;
import com.sun.dtf.range.RangeFactory;

/**
 * @dtf.tag element
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc The element tag is used within the Metadata tag, and its sole
 *               purpose is to define what each of the fields of a meta data 
 *               record contains. This definition includes type, length and name.  
 * 
 * @dtf.tag.example 
 * <element value="${hc.mp3.title.regexp}"  type="string" name="mp3.title"/> 
 * 
 * @dtf.tag.example
 * <element value="${hc.mp3.date.regexp}"   type="long"   name="mp3.date"/> 
 * 
 */
public class Element extends Action {
    
    public static final String INTEGER_TYPE     = "int";
    public static final String LONG_TYPE        = "long";
    public static final String STRING_TYPE      = "string";
    public static final String DOUBLE_TYPE      = "double";
    
    /**
     * @dtf.attr name
     * @dtf.attr.desc The name of the field that this element is representing.
     */
    private String name = null;
    
    /**
     * @dtf.attr value
     * @dtf.attr.desc The value of the field for this element.
     */
    private String value = null;
    
    /**
     * @dtf.attr type
     * @dtf.attr.desc The type of the field being represented.
     *                <br/>
     *                <dd>Currently supporting the following types</dd>
     *                
     *                <table>
     *                  <tr>
     *                      <th>Type</th> 
     *                  </tr>
     *                  <tr>
     *                      <td>int</td>
     *                  </tr>
     *                  <tr>
     *                      <td>long</td>
     *                  </tr>
     *                  <tr>
     *                      <td>string</td>
     *                  </tr> 
     *                  <tr>
     *                      <td>double</td>
     *                  </tr> 
     *                </table>
     */
    private String type  = null;

    /**
     * @dtf.attr length
     * @dtf.attr.desc The length of this the field, useful when generating 
     *                values based on the Element definition.
     */
    private String length = null;
    
    public void execute() throws DTFException { }

    // TODO: should be using one range per thread access!
    //
    private Range range = null; 
    private String retValueString() throws DTFException { 
        if (range == null || !range.hasMoreElements()) { 
            String expression = replaceProperties(value);
            range = RangeFactory.getRange(expression);
        }
        
        return range.nextElement();
    }
    
    public String getName() throws ParseException { return replaceProperties(name); }
    public void setName(String name) { this.name = name; }

    public String getType() throws ParseException { return replaceProperties(type); }
    public void setType(String type) { this.type = type; }

    public void setValue(String value) { this.value = value; }
    public String getValue() throws DTFException { return replaceProperties(value); }
    public String resolvedValue() throws DTFException { return retValueString(); }

    public int getLength() throws ParseException { 
        return toInt("length",length,DTFProperty.DEFAULT_PROPERTY_LENGTH); 
    }
    public void setLength(String length) { this.length = length; }  
}

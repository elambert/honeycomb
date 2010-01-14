package com.sun.dtf.actions.range;

import com.sun.dtf.actions.Action;
import com.sun.dtf.config.RangeProperty;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.range.Range;
import com.sun.dtf.range.RangeFactory;

/**
 * @dtf.tag createrange
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Creates a property that will iteratore through range expression
 *               on every subsequent call to resolve this property. This model 
 *               allows for you to iterate through range expressions without a 
 *               loop and introduces the ability to do so randomly using the 
 *               random() function of range expressions.
 * 
 * @dtf.tag.example 
 * <local>
 *      <createrange name="range1" value="1..100000" recycle="false"/>
 * </local>
 *
 * @dtf.tag.example 
 * <local>
 *      <createrange name="range2" value="a,b,c,d,e"/>
 * </local>
 *
 * @dtf.tag.example 
 * <local>
 *      <createrange name="range3" value="[a,b,c,d,e][0..3]"/>
 * </local>
 * 
 */
public class Createrange extends Action {

    /**
     * @dtf.attr name 
     * @dtf.attr.desc The name of the property that will be used when 
     *                referencing this newly created range expression.
     */
    private String name = null;
    
    /**
     * @dtf.attr value
     * @dtf.attr.desc The value is a Range expression and the syntax for these
     *                expressions is the following:
     *               
     *               
     *               
     *                
     */
    private String value = null;

    /**
     * @dtf.attr recycle
     * @dtf.attr.desc Defines if we want the range expression to reset upon 
     *                hitting the last element in the range.
     */
    private String recycle = null;
    
    public Createrange() { }
    
    public void execute() throws DTFException {
        Range range = RangeFactory.getRange(getValue());
        RangeProperty property = new RangeProperty(getName(), 
                                                   range, 
                                                   getRecycle());
        getConfig().put(getName(), property);
    }

    public String getName() throws ParseException { return replaceProperties(name); }
    public void setName(String name) { this.name = name; }

    public String getValue() throws ParseException { return replaceProperties(value); }
    public void setValue(String value) { this.value = value; }
    
    public boolean getRecycle() throws ParseException { return toBoolean("recycle",recycle); }
    public void setRecycle(String recycle) { this.recycle = recycle; }

}

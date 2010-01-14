package com.sun.dtf.actions.component;

import com.sun.dtf.actions.util.DTFProperty;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.util.StringUtil;

/**
 * @dtf.tag attrib
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This tag is used for defining attributes that a lockcomponent 
 *               should search for when trying to match the right type of 
 *               component.
 * 
 * @dtf.tag.example 
 * <local>
 *     <echo>Remote counter retrieval</echo>
 *     <lockcomponent id="DTFA1">
 *          <attrib name="type" value="DTFA"/>
 *     </lockcomponent>
 * </local>
 */
public class Attrib extends DTFProperty {

    private String testprop = "false";
    
    public Attrib() { } 
    
    public Attrib(String name, String value, boolean isTestProperty) { 
        setName(name);
        setValue(value);
        setTestProp(""+isTestProperty);
    }
    
    public void execute() throws DTFException { }
    
    public boolean matches(Attrib attrib) throws ParseException { 
        return (StringUtil.equalsIgnoreCase(getName(),attrib.getName()) &&
                StringUtil.equalsIgnoreCase(getValue(),attrib.getValue()));
    }
    
    public boolean equals(Object obj) {
        if (obj instanceof Attrib) { 
            Attrib attrib = (Attrib) obj;
            try { 
                return this.matches(attrib);
            } catch (ParseException e) {}
        }
        return false;
    }
    
    public void setTestProp(String testprop) { this.testprop = testprop; } 
    public boolean getTestProp() throws ParseException { 
        return toBoolean("testprop", testprop);
    } 
    public boolean isTestProp() throws ParseException { return getTestProp(); } 
}
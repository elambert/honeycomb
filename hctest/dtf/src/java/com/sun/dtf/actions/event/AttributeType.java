package com.sun.dtf.actions.event;

import com.sun.dtf.exception.ParseException;

public class AttributeType {
    
    public static AttributeType STRING_TYPE   = new AttributeType();
    public static AttributeType INT_TYPE      = new AttributeType();
    public static AttributeType LONG_TYPE     = new AttributeType();
    
    public static AttributeType getType(String type) throws ParseException { 
       
        if (type == null) 
            return STRING_TYPE;
        
        type = type.toLowerCase();
        
        if (type.equals("string")) { 
            return STRING_TYPE;
        } else if (type.equals("int")) { 
            return INT_TYPE;
        } else if (type.equals("long")) { 
            return LONG_TYPE;
        }
        
        throw new ParseException("Unable to parse type [" + type + "]");
    }
}

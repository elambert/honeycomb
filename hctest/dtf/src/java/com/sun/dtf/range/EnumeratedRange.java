package com.sun.dtf.range;

import com.sun.dtf.exception.RangeException;

public class EnumeratedRange implements Range {
    
    public static String EXPRESSION  = "([^,]*,?)*";

    private String regexp = null;
    private String original = null;
    
    private boolean _done = false;
    
    public EnumeratedRange(String expression) throws RangeException {
        if (!expression.matches(EXPRESSION)) {
            throw new RangeException("Expression [" + expression + 
                                     "] does not match " + EXPRESSION);
        }
        
        this.regexp = expression;
        this.original = regexp;
    }
    
    public boolean hasMoreElements() {
        return !_done;
    }
    
    public String nextElement() {
        int indexOfComa = regexp.indexOf(",");
        String result = null;
        
        if (indexOfComa == -1) {
            _done = true;
            result = regexp;
        } else {
            result = regexp.substring(0,indexOfComa);
            regexp = regexp.substring(result.length()+1,regexp.length());
        }
     
        return result;
    }

    public void reset() {
        regexp = original;
        _done = false;
    }
    
    public int size() {
        return original.split(",").length;
    }
}

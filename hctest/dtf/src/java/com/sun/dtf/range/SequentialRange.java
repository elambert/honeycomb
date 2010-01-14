package com.sun.dtf.range;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.dtf.exception.RangeException;



public class SequentialRange implements Range {

    public static String EXPRESSION = "([^\\.]*)\\.\\.([^\\.]*)";
    
    private String start;
    private String end;
    private String cur;
    
    public SequentialRange(String expression) throws RangeException { 
        Pattern pattern = Pattern.compile(EXPRESSION);
     
        Matcher matcher = pattern.matcher(expression); 
       
        if (matcher.matches()) { 
            start = matcher.group(1);
            end = matcher.group(2);
        } else
            throw new RangeException("Expression [" + expression + 
                                     "] does not match " + EXPRESSION);
    }
    
    public boolean hasMoreElements() {
        return (cur == null || new Long(cur).longValue() < new Long(end).longValue());
    }

    public String nextElement() {
        if (cur == null) {
            cur = start;
            return cur;
        }
        
        long curL = new Long(cur).longValue();
        cur = new Long(""+(curL+1)).toString();
        
        return cur;
    }

    public void reset() {
        cur = null;
    }
   
    //TODO: need logic to handle the usage of characters instead of
    //      numbers
    public int size() {
        return (new Integer(end).intValue() - new Integer(start).intValue()) + 1;
    }
}

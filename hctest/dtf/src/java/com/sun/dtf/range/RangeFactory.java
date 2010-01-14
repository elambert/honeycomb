package com.sun.dtf.range;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.dtf.exception.DTFException;

public class RangeFactory {
    public static Range getRange(String regexp) throws DTFException { 
        RangeJoin range = new RangeJoin();
       
        /*
         * If there are no [ ] surrounding the regexp then fix it up so we can
         * process it correctly
         */
        regexp = regexp.trim();
        if (!regexp.startsWith("["))
            regexp = "[" + regexp + "]";
        
        Pattern pattern = Pattern.compile("\\[[^\\]]*\\]");
        Matcher matcher = pattern.matcher(regexp);
        
        while (matcher.find()) { 
           Range aux = null;
           String expression = matcher.group();
           expression = expression.substring(1, expression.length()-1);
          
           // more restrictive range expression first
           if (expression.matches(RandomListRange.EXPRESSION)) { 
               aux = new RandomListRange(expression);
           } else if (expression.matches(SequentialRange.EXPRESSION)) { 
               aux = new SequentialRange(expression);
           } else if (expression.matches(EnumeratedRange.EXPRESSION)) { 
               aux = new EnumeratedRange(expression);
           } 
           
           if (aux == null) 
               throw new DTFException("Unrecognized range expression [" + expression  + "]");
            
           range.addRange(aux);
        }
        
        return range;
    }
}

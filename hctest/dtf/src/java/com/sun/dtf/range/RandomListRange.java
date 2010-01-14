package com.sun.dtf.range;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.RangeException;

public class RandomListRange implements Range {
   
    public static int MAXSIZE = 1024;
    public static String EXPRESSION  = "random\\((.*)\\)";

    private String regexp = null;
    private String[] elements = null;
    
    private Random _rand = null;
    
    public RandomListRange(String expression) throws RangeException {
        _rand = new Random(System.currentTimeMillis());
        if (!expression.matches(EXPRESSION)) {
            throw new RangeException("Expression [" + expression + 
                                     "] does not match " + EXPRESSION);
        }
       
        Pattern pattern = Pattern.compile(EXPRESSION);
        Matcher matcher = pattern.matcher(expression);
        
        if (matcher.matches()) { 
            String subrange = matcher.group(1);
            try {
                Range range = RangeFactory.getRange(subrange);
                if (range.size() < MAXSIZE) { 
                    elements = new String[range.size()];
                    int i = 0;
                    while(range.hasMoreElements()) { 
                        String element = range.nextElement();
                        elements[i++] = element;
                    }
                } else 
                    throw new RangeException("Random ranges cannot exceed " + 
                                             MAXSIZE + " elements.");
            } catch (DTFException e) {
                throw new RangeException("Error parsing range.",e);
            }
        }
        
        this.regexp = expression;
    }
    
    public boolean hasMoreElements() {
        return true;
    }
    
    public String nextElement() {
        return elements[Math.abs(_rand.nextInt() % elements.length)];
    }

    public void reset() {
    }
    
    public int size() {
        return elements.length;
    }
}

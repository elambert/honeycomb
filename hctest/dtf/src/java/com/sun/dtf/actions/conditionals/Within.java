package com.sun.dtf.actions.conditionals;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

/**
 * @dtf.tag within
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Evaluates if value is within range of a specified tolerance 
 *               value. The value itself can be any numeric value while the 
 *               tolerance can be any of the following representations:
 *          
 *               5%      - op1 is within 5% of op2
 *               [5,20]% - op1 can be lower than op2 by 5% or can be higher than 
 *                         op2 by upto 20%
 *              
 *               10      - op1 can be lower than op2 by 10x
 * 
 * @dtf.tag.example 
 * <within op1="average" op2="value" range="[5,10]%"/>

 * @dtf.tag.example 
 * <within op1="standard_deviation" op2="${myval}" range="0.1"/>
 * 
 */
public class Within extends Condition {
   
    private String tolerance = null;
    
    public Within() { }
    
    public boolean evaluate() throws DTFException {
        double op1 = toDouble("op1", getOp1());
        double op2 = toDouble("op2", getOp2());
        Tolerance tolerance = getPercentage(getTolerance());
        
        if (op1 < op2) { 
            return Math.abs(op1 - op2) / op2 <= tolerance.lower;
        } else { 
            return Math.abs(op2 - op1) / op1 <= tolerance.upper;
        }
    }
    
    private class Tolerance { 
        public double lower = 0;
        public double upper = 0;
    }
    
    private Tolerance getPercentage(String percentage) throws ParseException { 
        Tolerance result = new Tolerance();
        String perc = replaceProperties(percentage);
        
        if (perc.indexOf('%') != -1) {
            perc = perc.substring(0,perc.indexOf('%'));
           
            Pattern pattern = Pattern.compile("(\\[)([^,]*),([^]]*)(\\])");
            Matcher matcher = pattern.matcher(perc);
            
            if (matcher.matches()) { 
                String lowervalue = matcher.group(2);
                String uppervalue = matcher.group(3);
                result.lower = toDouble("lowervalue",lowervalue) / 100.0f;
                result.upper = toDouble("uppervalue",uppervalue) / 100.0f;
            } else { 
                result.lower = toDouble("tolerance", perc) /  100.0f;
                result.upper = toDouble("tolerance", perc) /  100.0f;
            }
        } else {
            result.lower = toDouble("tolerance", perc);
            result.upper = toDouble("tolerance", perc);
        }
        
        return result;
    }
    
    public String getTolerance() { return tolerance; } 
    public void setTolerance(String tolerance) { this.tolerance = tolerance; } 
}

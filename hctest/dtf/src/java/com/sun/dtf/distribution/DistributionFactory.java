package com.sun.dtf.distribution;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.dtf.exception.DistributionException;


public class DistributionFactory {

    private static DistributionFactory _instance = null;
    
    private DistributionFactory() { }
    
    public static synchronized DistributionFactory getInstance() { 
        if (_instance == null) 
            _instance = new DistributionFactory();
        
        return _instance;
    }
    
    public Distribution getDistribution(String function) throws DistributionException { 
        Distribution distribution = null;
        
        Pattern pattern = Pattern.compile("([^(]*)\\(([^)]*)\\)");
        Matcher matcher = pattern.matcher(function);
        
        if (matcher.matches()) { 
            String func = matcher.group(1).toLowerCase();
            String[] arguments = matcher.group(2).split(",");
            
            if (func.equals("const")) { 
                // Constant function
                distribution = new Constant(arguments);
            } else if (func.equals("step")) { 
                // Step function
                distribution = new Step(arguments);
            } else if (func.equals("list")) {
                distribution = new List(arguments);
            } else {
                throw new DistributionException("Unable to parse distribution [" + 
                                            function + "]");
            }
        } else 
            throw new DistributionException("Unable to parse distribution [" + 
                                            function + "]");
        
        return distribution;
    }
}

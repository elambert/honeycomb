package com.sun.dtf.distribution;

import com.sun.dtf.exception.DistributionException;

public class Constant implements Distribution {
   
    private long _constant = -1;
    
    public Constant(String[] arguments) throws NumberFormatException, DistributionException { 
        if (arguments.length != 1) 
            throw new DistributionException("Const function takes 1 argument."); 
        
        _constant = new Long(arguments[0]).longValue();
    }

    public long result(long time) {
        return _constant;
    }
}

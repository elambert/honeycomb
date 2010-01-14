package com.sun.dtf.config;

import com.sun.dtf.util.TimeUtil;

public class DTFTimeStamp implements DynamicProperty {
    
    public static final String DTF_TIMESTAMP = "dtf.timestamp";

    public String getValue() {
        return TimeUtil.getTimeStamp();
    }

    public String getName() {
        return DTF_TIMESTAMP;
    }

}

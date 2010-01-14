package com.sun.dtf.config;

import com.sun.dtf.util.TimeUtil;

public class DTFDateStamp implements DynamicProperty {
    
    public static final String DTF_DATESTAMP = "dtf.datestamp";

    public String getValue() {
        return TimeUtil.getDateStamp();
    }

    public String getName() {
        return DTF_DATESTAMP;
    }

}

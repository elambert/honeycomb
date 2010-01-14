package com.sun.dtf.config;

import java.util.Random;

public class DTFRandomLong implements DynamicProperty {
    
    public static final String DTF_RANDOMLONG = "dtf.randomLong";

    public String getValue() {
        return ""+new Random(System.currentTimeMillis()).nextLong();
    }

    public String getName() {
        return DTF_RANDOMLONG;
    }
}

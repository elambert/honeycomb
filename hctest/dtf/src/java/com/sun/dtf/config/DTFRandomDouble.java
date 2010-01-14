package com.sun.dtf.config;

import java.util.Random;

public class DTFRandomDouble implements DynamicProperty {
    
    public static final String DTF_RANDOMDOUBLE = "dtf.randomDouble";

    public String getValue() {
        return ""+new Random(System.currentTimeMillis()).nextDouble();
    }

    public String getName() {
        return DTF_RANDOMDOUBLE;
    }
}

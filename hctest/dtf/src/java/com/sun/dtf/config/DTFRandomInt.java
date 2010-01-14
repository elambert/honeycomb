package com.sun.dtf.config;

import java.util.Random;

public class DTFRandomInt implements DynamicProperty {
    
    public static final String DTF_RANDOMINT = "dtf.randomInt";

    public String getValue() {
        return ""+new Random(System.currentTimeMillis()).nextInt();
    }

    public String getName() {
        return DTF_RANDOMINT;
    }
}

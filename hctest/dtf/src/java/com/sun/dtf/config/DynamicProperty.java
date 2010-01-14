package com.sun.dtf.config;

import com.sun.dtf.exception.ParseException;

public interface DynamicProperty {
    public String getName();
    public String getValue() throws ParseException;
}

package com.sun.honeycomb.adapter;

import java.lang.reflect.Constructor;

public class AdapterFactory{

    private AdapterFactory(){}

    public static Repository makeAdapter(String className, String connectionInfo) throws Exception{
        Class[] argClasses = {String.class};
        Class c = Class.forName(className);
        Constructor constructor = c.getConstructor(argClasses);
        String[] args = {connectionInfo};
        return (Repository) constructor.newInstance(args);
    }

}

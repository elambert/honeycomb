package com.sun.dtf.comm.rpc;


import org.apache.xmlrpc.common.TypeConverter;
import org.apache.xmlrpc.common.TypeConverterFactoryImpl;

import com.sun.dtf.actions.Action;


public class DTFConverterFactory extends TypeConverterFactoryImpl {
    
    public DTFConverterFactory() { 
        
    }

    public TypeConverter getTypeConverter(Class pClass) {
        if (Action.class.isAssignableFrom(pClass)){
            return new ActionConvert();
        }

        return super.getTypeConverter(pClass);
    }
}

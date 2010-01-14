package com.sun.dtf.comm.rpc;

import org.apache.xmlrpc.common.TypeConverter;

import com.sun.dtf.actions.Action;


public class ActionConvert implements TypeConverter {

    public Object backConvert(Object arg0) {
        return arg0;
    }

    public Object convert(Object arg0) {
        return arg0;
    }

    public boolean isConvertable(Object arg0) {
        return Action.class.isAssignableFrom(arg0.getClass());
    }
}

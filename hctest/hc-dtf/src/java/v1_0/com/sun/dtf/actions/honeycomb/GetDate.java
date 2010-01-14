package com.sun.dtf.actions.honeycomb;

import java.util.Date;

import com.sun.dtf.exception.DTFException;
import com.sun.honeycomb.client.NameValueObjectArchive;

public abstract class GetDate {

    public static Date getDate(NameValueObjectArchive archive) 
           throws DTFException { 
        throw new DTFException("getDate operation not supported in honeycomb 1.0");
    }
}

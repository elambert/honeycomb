package com.sun.dtf.actions.honeycomb;

import java.io.IOException;
import java.util.Date;

import com.sun.dtf.exception.DTFException;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.common.ArchiveException;

public abstract class GetDate {

    public static Date getDate(NameValueObjectArchive archive) 
           throws DTFException { 
        try {
            return archive.getDate();
        } catch (ArchiveException e) {
            throw new DTFException("Error getting date from cluster.",e);
        } catch (IOException e) {
            throw new DTFException("Error getting date from cluster.",e);
        } 
    }
}

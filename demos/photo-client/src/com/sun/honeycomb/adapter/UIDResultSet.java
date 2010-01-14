package com.sun.honeycomb.adapter;

import java.util.Iterator;


/**
 * Results of a query with no select clause 
 */

public interface UIDResultSet {

    boolean next();

    public UID getObjectIdentifier();

}

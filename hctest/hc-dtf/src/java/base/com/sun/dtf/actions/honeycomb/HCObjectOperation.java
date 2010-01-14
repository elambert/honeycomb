package com.sun.dtf.actions.honeycomb;

import com.sun.dtf.actions.honeycomb.HCBasicOperation;
import com.sun.dtf.exception.ParseException;

public abstract class HCObjectOperation extends HCBasicOperation {

    /**
     * @dtf.attr oid
     * @dtf.attr.desc The OID to use during this operation.
     */
    private String oid = null;

    public String getOid() throws ParseException { return replaceProperties(oid); }
    public void setOid(String oid) { this.oid = oid; }
}

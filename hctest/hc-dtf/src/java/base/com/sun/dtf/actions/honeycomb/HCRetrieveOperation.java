package com.sun.dtf.actions.honeycomb;

import com.sun.dtf.exception.ParseException;

public abstract class HCRetrieveOperation extends HCObjectOperation {

    /**
     * @dtf.attr verify
     * @dtf.attr.desc boolean value that represents if the data retrieved should 
     *                be verified or not.
     */
    private String verify = null;
    
    /**
     * @dtf.attr corrupt
     * @dtf.attr.desc boolean value to corrupt data using the 
     *                {@dtf.link CorruptableByteChannel} to corrupt the retrieve
     *                stream. This is mainly used to test the framework itself 
     *                and no common usage under normal conditions.
     */
    private String corrupt = null;

    public String getVerify() throws ParseException { return replaceProperties(verify); }
    public boolean isVerify() throws ParseException { return toBoolean("verify",getVerify()); }
    public void setVerify(String verify) { this.verify = verify; }
    
    public String getCorrupt() throws ParseException { return replaceProperties(corrupt); }
    public boolean isCorrupting() throws ParseException { return toBoolean("corrupt",getCorrupt()); }
    public void setCorrupt(String corrupt) { this.corrupt = corrupt; } 
}

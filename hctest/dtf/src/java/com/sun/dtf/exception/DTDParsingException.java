package com.sun.dtf.exception;

/**
 * 
 * @author Rodney Gomes
 *
 */
public class DTDParsingException extends DTFException {

    public DTDParsingException(String msg) {
        super(msg);
    }
    
    public DTDParsingException(String msg, long line, long column) {
        super(msg + " at line,column: " + line + "," + column);
    }
    
    public DTDParsingException(String msg, long line, long column, Throwable t) {
        super(msg + " at line,column: " + line + "," + column,t);
    }
}

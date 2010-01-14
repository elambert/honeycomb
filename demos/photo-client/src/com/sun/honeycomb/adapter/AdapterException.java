package com.sun.honeycomb.adapter;



public class AdapterException extends RuntimeException{

    public AdapterException(String reason){
        super(reason);
    }
    public AdapterException(String reason, Throwable cause){
        super(reason, cause);
    }

    public AdapterException(Throwable cause){
        super(cause);
    }

}

package com.sun.dtf.range;

public interface Range {
  
    /**
     * returns a boolean that specifies if the Range has anymore 
     * elements to return or if it has reached its end. 
     * @return
     */
    public boolean hasMoreElements();

    /**
     * 
     * @return
     */
    public String nextElement();
    
    /**
     * Reset the Range back to the original state so that this range 
     * can be used as if it were a new range just created for the first
     * time. 
     */
    public void reset();
    
    /**
     * size method used to return the size of the Range. 
     * @return 
     */
    public int size();
}

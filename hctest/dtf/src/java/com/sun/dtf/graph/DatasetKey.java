package com.sun.dtf.graph;

public class DatasetKey implements Comparable { 
    private String _primary = null;
    private String _secondary = null;
   
    public DatasetKey(String primary, String secondary) { 
        _primary = primary;
        _secondary = secondary;
    }
   
    public String getPrimary() { return _primary; }
    public String getSecondar() { return _secondary; }
    
    public String toString() {
        return _primary + (_secondary == null ? "" : "-" + _secondary);
    }

    public boolean equals(Object obj) {
        return (compareTo(obj) == 0);
    }
    
    public int hashCode() {
        return toString().hashCode();
    }
    
    public int compareTo(Object o) {
        assert (o instanceof DatasetKey);
        
        DatasetKey other = (DatasetKey)o;
        return this.toString().compareTo(other.toString());
    }
}
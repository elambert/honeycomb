package com.sun.dtf.config;

import com.sun.dtf.exception.ParseException;
import com.sun.dtf.range.Range;

public class RangeProperty implements DynamicProperty {
  
    private String _name = null;
    private Range _range = null;
    private boolean _recycle = false;
    
    public RangeProperty(String name, Range range, boolean recycle) { 
        _name = name;
        _range = range;
        _recycle = recycle;
    }

    public String getValue() throws ParseException {
        if (!_range.hasMoreElements())
            if (_recycle)
                _range.reset();
            else 
                throw new ParseException("Range [" + _name + "] out of elements.");
        
        return _range.nextElement();
    }

    public String getName() { return _name; }
}

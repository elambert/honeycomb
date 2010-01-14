package com.sun.dtf.range;

import java.util.ArrayList;

import com.sun.dtf.logger.DTFLogger;


class RangeJoin implements Range {
    
    private DTFLogger _logger = DTFLogger.getLogger(RangeJoin.class);
   
    private ArrayList _ranges = null;
    private String[] elems = null;
    
    public RangeJoin() {
        _ranges = new ArrayList();
    }
    
    public synchronized void addRange(Range range)  { 
        _ranges.add(range);
    }

    public boolean hasMoreElements() {
        boolean hasNoMore = true;
       
        for (int i = 0; i < _ranges.size(); i++) 
            hasNoMore &= !((Range)_ranges.get(i)).hasMoreElements();
        
        return !hasNoMore;
    }

    /**
     * Push the range group up by 1 starting from the least significant range
     * 
     * @param index
     */
    private void nextElement(int index) { 
        Range range = (Range) _ranges.get(index);

        if (!range.hasMoreElements()) { 
            if (index != 0) {
                range.reset();
                nextElement(index-1);
            } 
        }
      
        elems[index] = range.nextElement();
    }
    
    public String nextElement() {
        if (elems == null) { 
            elems = new String[_ranges.size()];
            // initialize
            for (int i = 0; i < _ranges.size(); i++) 
                elems[i] = ((Range)_ranges.get(i)).nextElement();
        } else  {
            nextElement(_ranges.size()-1);
        }
        
        String result = "";
        for (int i = 0; i < _ranges.size(); i++) { 
            result += (String)elems[i];
        }
        
        return result;
    }

    public void reset() {
         for (int i = 0; i < _ranges.size(); i++)
            ((Range)_ranges.get(i)).reset();
    }
    
    public int size() {
        int result = 0;
        for (int i = 0; i < _ranges.size(); i++) {
            result += ((Range) _ranges.get(i)).size();
        }
        return result;
    }
}

package com.sun.dtf.actions.graph;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.util.TimeUtil;

public class Series extends Action {

    public final static String SUM_AGG_FUNC = "sum";
    public final static String AVG_AGG_FUNC = "average";
    
    /**
     * @dtf.attr cursor
     * @dtf.attr.desc 
     */
    private String cursor = null;
    
    /**
     * @dtf.attr separateby
     * @dtf.attr.desc 
     */
    private String separateby = null;
    
    /**
     * @dtf.attr aggregateby
     * @dtf.attr.desc 
     */
    private String aggregateby = null;
    
    /**
     * @dtf.attr aggfunc
     * @dtf.attr.desc 
     */
    private String aggfunc = null;
    
    /**
     * @dtf.attr lowerlimit
     * @dtf.attr.desc 
     */
    private String lowerlimit = null;
    
    /**
     * @dtf.attr upperlimit
     * @dtf.attr.desc 
     */
    private String upperlimit = null;

    /**
     * @dtf.attr name
     * @dtf.attr.desc 
     */
    private String name = null;

    /**
     * @dtf.attr mode
     * @dtf.attr.desc 
     */
    private String mode = null;

    /**
     * @dtf.attr sampleunit
     * @dtf.attr.desc 
     */
    private String sampleunit = null;
    
    public void execute() throws DTFException {  }

    public void setCursor(String cursor) { this.cursor = cursor; } 
    public String getCursor() throws ParseException { return replaceProperties(cursor); } 

    public void setSeparateBy(String separateby) { this.separateby = separateby; } 
    public String getSeparateBy() throws ParseException { return replaceProperties(separateby); } 

    public void setAggregateBy(String aggregateby) { this.aggregateby = aggregateby; }
    public String getAggregateBy() throws ParseException { return replaceProperties(aggregateby); } 

    public void setAggFunc(String aggfunc) { this.aggfunc = aggfunc; }
    public String getAggFunc() throws ParseException { return replaceProperties(aggfunc); } 

    public void setLowerLimit(String lowerlimit) { this.lowerlimit = lowerlimit; }
    public double getLowerLimit() throws ParseException { return toDouble("lowerlimit",lowerlimit,-1); } 

    public void setUpperLimit(String upperlimit) { this.upperlimit = upperlimit; }
    public double getUpperLimit() throws ParseException { return toDouble("upperlimit",upperlimit,-1); } 

    public String getName() throws ParseException { return replaceProperties(name); }
    public void setName(String name) { this.name = name; } 

    public String getMode() throws ParseException { return replaceProperties(mode); }
    public void setMode(String mode) { this.mode = mode; }

    public long getSampleunit() throws ParseException { return TimeUtil.parseTime("sampleunit", sampleunit); }
    public void setSampleunit(String sampleunit) { this.sampleunit = sampleunit; }
}

package com.sun.dtf.actions.flowcontrol;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

/**
 * @dtf.tag loop
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Loop tag is used exclusively on remote components to do 2 types
 *               of loops: parallel (execute the child tag X times in 
 *               parallel) and sequence (execute the child tag X times).
 * 
 * @dtf.tag.example
 */
public class Loop extends Action {

    private static final String PARALLEL_TYPE = "parallel";
    private static final String SEQUENCE_TYPE = "sequence";
    private static final String TIMER_TYPE    = "timer";
    
    /**
     * @dtf.attr property 
     * @dtf.attr.desc This property will contain the value of the element in the
     *                range expression at the time of execution of that specific
     *                element.
     */
    private String property = null;
    
    /**
     * @dtf.attr range
     * @dtf.attr.desc Range expression defining how this loop will behave and 
     *                over which elements it will iterate.
     */
    private String range = null;

    /**
     * @dtf.attr type
     * @dtf.attr.desc There are 3 types of loops that are currently available, 
     *                they are:
     *                
     *                <b>Loop Type</b>
     *                <table border="1">
     *                    <tr>
     *                        <th>Type</th> 
     *                        <th>Description</th> 
     *                    </tr>
     *                    <tr>
     *                        <td>parallel</td> 
     *                       <td>A loop that will take the underlying action and 
     *                            execute it in parallel as many times as there are 
     *                            elements in the specified range expression.</td> 
     *                    </tr>
     *                    <tr>
     *                        <td>sequential</td> 
     *                        <td>A loop that will take the underlying action and 
     *                            execute it in sequence as many times as there are 
     *                            elements in the specified range expression.</td> 
     *                    </tr>
     *                    <tr>
     *                        <td>timer</td> 
     *                        <td>A loop that will take the underlying action and 
     *                            execute it in sequence for as long as the 
     *                            time specified in the range attribute.</td>
     *                    </tr>
     *                </table>
     */
    private String type = null;

    public Loop() { }
   
    public void execute() throws DTFException {
        if (getType().equals(PARALLEL_TYPE)) { 
            Parallelloop parallel = new Parallelloop();
            parallel.setRange(getRange());
            parallel.setProperty(getProperty());
            parallel.addActions(children());
            parallel.execute();
        } else if (getType().equals(SEQUENCE_TYPE)) { 
            For sequence = new For();
            sequence.setRange(getRange());
            sequence.setProperty(getProperty());
            sequence.addActions(children());
            sequence.execute();
        } else if (getType().equals(TIMER_TYPE)) { 
            Timer timer = new Timer();
            timer.setInterval(getRange());
            timer.setProperty(getProperty());
            timer.addActions(children());
            timer.execute();
        } else
            throw new ParseException("Unkown loop type [" + getType() + "].");
    }
    
    public String getProperty() throws ParseException { return replaceProperties(property); }
    public void setProperty(String property) { this.property = property; }

    public String getRange() throws ParseException { return replaceProperties(range); }
    public void setRange(String range) { this.range = range; }

    public String getType() throws ParseException { return replaceProperties(type); }
    public void setType(String type) { this.type = type; }
}

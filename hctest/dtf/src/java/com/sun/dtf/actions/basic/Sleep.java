package com.sun.dtf.actions.basic;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.util.TimeUtil;

/**
 * @dtf.tag sleep
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc The sleep tag will pause the execution of the testcase at this
 *               point for the amount of time specified in the property 
 *               <code>time</code>.
 * 
 * @dtf.tag.example 
 * <sleep time="3s"/>
 * 
 * @dtf.tag.example 
 * <sleep time="2d"/>
 */
public class Sleep extends Action {

    /**
     * @dtf.attr time
     * @dtf.attr.desc Specifies the amount of time to sleep. This time can be 
     *                defined with the follow suffixes: 
     *                <ul>
     *                <table border="1">
     *                  <tr>
     *                      <th>Value</th> 
     *                      <th>Description</th> 
     *                  </tr>
     *                  <tr>
     *                      <td>s</td>
     *                      <td>Seconds</td>
     *                  </tr>
     *                  <tr>
     *                      <td>h</td>
     *                      <td>Hours</td>
     *                  </tr>
     *                  <tr>
     *                      <td>d</td>
     *                      <td>Days</td>
     *                  </tr>
     *                  <tr>
     *                      <td>m</td>
     *                      <td>Months</td>
     *                  </tr>
     *                </table>
     *                </ul>
     */
    private String time = null;
  
    public Sleep() { }
    
    public void execute() throws DTFException {
        try {
            getLogger().info("Sleeping for " + getTime());
            long value = TimeUtil.parseTime("time",getTime());
            
            if (value < 0) 
                throw new DTFException("Sleep can not be negative.");
           
            Thread.sleep(value);
        } catch (InterruptedException e) {
            throw new DTFException("Issue during sleep.",e);
        }
    }

    public String getTime() throws ParseException { return replaceProperties(time); }
    public void setTime(String time) { this.time = time; }
}

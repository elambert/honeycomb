package com.sun.dtf.actions.basic;

import com.sun.dtf.DTFConstants;
import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.results.Result;

/**
 * @dtf.tag script
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This tag is the root of all DTF testcases.
 * 
 * @dtf.tag.example 
 * <?xml version="1.0" encoding='UTF-8'?>
 * <!DOCTYPE script PUBLIC "-//DTF/DTF XML Script V1.0//EN" "dtf.dtd">
 * 
 * <script name="mytestcase">
 *      <info>
 *          <author>
 *              <name>Some Authore</name>
 *             <email>author@server.com</email>
 *         </author>
 *         <description>DTF test.</description>
 *      </info>
 * </script>
 * 
 */
public class Script extends Action {

    /**
     * @dtf.attr name
     * @dtf.attr.desc The name attribute is used to specify a unique name for 
     *                the testcase being defined.
     */
    private String name = null;

    public Script() {}
    
    public void execute() throws DTFException { 
        getConfig().setProperty(DTFConstants.SCRIPT_ID,getName());
        
        Result result = new Result(getName());
        result.setTestcase();
        result.start();
        
        try {
            getResults().setTestSuiteRecorded(false);
            executeChildren();
            result.stop();
            result.setPassResult();
        } catch (DTFException e) { 
            result.stop();
            result.setFailResult(e);
            throw e;
        } finally { 

            /*
             * Only record the test case result if there was no sub 
             * test suite recording already done.
             */
            if (!getResults().isTestSuiteRecorded())  {
                if (result.getStop() == -1)
                    result.stop();
                getResults().recordResult(result);
            }
        }
    }
   
    public String getName() throws ParseException { return replaceProperties(name); }
    public void setName(String name) { this.name = name; }
}

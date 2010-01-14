package com.sun.dtf.actions.component;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;

/**
 * @dtf.tag local
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This tag will encapsulate other actions that will occur 
 *               locally. Now by locally this means that the output and inputs 
 *               of these actions is done on the machine where you run the 
 *               testcase from. The local tag is where all of the components are 
 *               locked and properties are loaded from, etc.
 * 
 * @dtf.tag.example 
 * <local>
 *      <createstorage id="INPUT" path="${dtf.path}/tests/perf/input"/>
 *      <createstorage id="OUTPUT" path="${dtf.path}/tests/perf/output"/>
 * </local>
 * 
 * @dtf.tag.example 
 * <local>
 *      <loadproperties uri="storage://INPUT/test.properties"/>
 * </local>
 */
public class Local extends Action {
    public Local() { }
    public void execute() throws DTFException { executeChildren(); }
}

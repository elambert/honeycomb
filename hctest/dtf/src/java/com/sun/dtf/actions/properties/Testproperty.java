package com.sun.dtf.actions.properties;

import com.sun.dtf.exception.DTFException;

/**
 * @dtf.tag testproperty
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc This tag can be used the add test properties to the parent 
 *               testscript that are later recorded by the results tag and can
 *               later be processed by stylsheets.
 * 
 * @dtf.tag.example 
 * <testsuite name="testsuite1" continueOnFailure="true">
 *     <testproperty name="myproperty" value="important value"/>
 * </testsuite>
 */
public class Testproperty extends Property {
    public void execute() throws DTFException {
        getResults().recordProperty(getName(), getValue());
    }
}

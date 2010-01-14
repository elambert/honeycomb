package com.sun.dtf.actions.flowcontrol;

/**
 * @dtf.tag finally 
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Used within the try tag. This tag will define a block of xml 
 *               that will always be executed independetly of there being an 
 *               exception thrown or not.
 * 
 * @dtf.tag.example 
 * <finally>
 *     <local>
 *         <echo>This message will always be printed</echo>
 *     </local>
 * </finally>
 */
public class Finally extends Sequence { 
    public Finally() {}
}

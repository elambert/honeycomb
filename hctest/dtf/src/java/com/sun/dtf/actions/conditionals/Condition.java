package com.sun.dtf.actions.conditionals;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;

public abstract class Condition extends Action {

    /**
     * @dtf.attr op1
     * @dtf.attr.desc Identifies the first operand of a conditional tag.
     */
    private String op1      = null;
    
    /**
     * @dtf.attr op2
     * @dtf.attr.desc Identifies the second operand of a conditional tag.
     */
    private String op2      = null;
    
    /**
     * @dtf.attr type
     * @dtf.attr.desc Identifies the type of the operands being evaluated. 
     *                <b>Supported DTF types:</br>
     *                <table border="1">
     *                    <tr>
     *                        <th>Type</th> 
     *                        <th>Description</th> 
     *                    </tr>
     *                    <tr>
     *                         <td>int</td>
     *                         <td>Integer type</td>
     *                    </tr>
     *                    <tr>
     *                         <td>string</td>
     *                         <td>String type</td>
     *                    </tr>
     *               </table> 
     */
    private String type = null;
    
    /**
     * @dtf.attr nullable
     * @dtf.attr.desc If nullable is set to true then if one of the operands is
     *                equal to null the condition will still evaluate to true.
     *                When set to false the behaviour of the comparison of the 
     *                two operands is the normal conditional evaluation.
     */
    private String nullable = null;
    
    public Condition() { }

    public void execute() throws DTFException { }

    public String getOp1() throws ParseException { return replaceProperties(op1); }
    public void setOp1(String op1) { this.op1 = op1; }
    
    public String getOp2() throws ParseException { return replaceProperties(op2); }
    public void setOp2(String op2) { this.op2 = op2; }

    public String getType() throws ParseException { return replaceProperties(type); }
    public void setType(String type) { this.type = type; }
    
    public boolean getNullable() throws ParseException { return toBoolean("nullable", nullable); }
    public void setNullable(String nullable) { this.nullable = nullable; }
    
    public abstract boolean evaluate() throws DTFException;
}

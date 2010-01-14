package com.sun.dtf.actions.honeycomb;


import java.nio.channels.ReadableByteChannel;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.honeycomb.channels.DeadBeefReadableByteChannel;
import com.sun.dtf.actions.honeycomb.channels.RandomReadableByteChannel;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.recorder.Event;

/**
 * @dtf.tag file
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc The file tag is used by operations to define the type of object
 *               being stored/retrieved. This tag is able of generating data 
 *               that can later be reverified using the same data signature. In
 *               other words, if you define a 
 *               {@literal <file length="1000" type="random"/>} and store that, 
 *               later when you retrieve if you pass the option verify="true" 
 *               and you give the child tag file with the same properties as it 
 *               did have at restore time. 
 *               <br/> 
 *               The action retrieve will be able to verify the contents of the 
 *               retrieved object without having ever stored the original data. 
 *               This mechanism saves a huge amount of space and allows us to 
 *               verify data on the wire without having to use up disk IO.
 *               
 * @dtf.tag.example 
 * <component id="${client}">
 *     <store datavip="${hc.cluster.datavip}"
 *            port="${hc.cluster.dataport}">
 *         <file length="50000" type="random"/>
 *         <metadata refid="MDREF"/>
 *     </store>
 * </component> 
 * 
 * @dtf.tag.example 
 * <component id="${client}">
 *     <store datavip="${hc.cluster.datavip}"
 *            port="${hc.cluster.dataport}">
 *         <file length="1024" type="deadbeef"/>
 *     </store>
 * </component> 
 */
public class File extends Action {

    private static String RANDOM = "random";

    private static String DEADBEEF = "deadbeef";
   
    /**
     * @dtf.attr type
     * @dtf.attr.desc Currently supported types are:
     *                <b>File types:</b>
     *                <table border="1">
     *                    <tr>
     *                        <th>Type</th> 
     *                        <th>Description</th> 
     *                    </tr>
     *                    <tr>
     *                        <td>random</td>
     *                        <td></td>
     *                    </tr>
     *                    <tr>
     *                        <td>deadbeef</td>
     *                        <td></td>
     *                    </tr>
     *               </table> 
     */
    private String type = null;
    
    /**
     * @dtf.attr length
     * @dtf.attr.desc The length in bytes of the file to generate.
     */
    private String length = null;
   
    /**
     * @dtf.attr offset
     * @dtf.attr.desc Offset used internally when generating data for this File.
     */
    private String offset = "1234567890";
    
    public File() {}
    
    public void execute() throws DTFException { }
    
    public ReadableByteChannel byteChannel() throws NumberFormatException, ParseException, DTFException {
        if (getType().equals(DEADBEEF)) {
            return new DeadBeefReadableByteChannel(offsetAsLong(), lengthAsLong());
        } else if (getType().equals(RANDOM)) {
            return new RandomReadableByteChannel(offsetAsLong(), lengthAsLong());
        } else
            throw new DTFException("Unknown file type [" + getType() + "]");
    }
    
    public long lengthAsLong() throws ParseException { return toLong("length",length); } 
    public String getLength() throws ParseException { return replaceProperties(length); } 
    public void setLength(String length) { this.length = length; }

    public String getType() throws ParseException { return replaceProperties(type); }
    public void setType(String type) { this.type = type; }
    
    public long offsetAsLong() throws ParseException { return toLong("offset",offset); } 
    public String getOffset() throws ParseException { return replaceProperties(offset); } 
    public void setOffset(String offset) { this.offset = offset; }

    public void processAttributes(Event event) throws ParseException {
        event.addAttribute("file.type", getType());
        event.addAttribute("file.length", getLength());
        event.addAttribute("file.offset", getOffset());
    }
}

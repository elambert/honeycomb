package com.sun.dtf.actions.honeycomb;

import java.util.ArrayList;


import com.sun.dtf.actions.reference.Referencable;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;

/**
 * @dtf.tag metadata
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Metadata tag is used to define metadata being stored or to 
 *               verify metadata being retrieve from the system. It is used by 
 *               a few different Honeycomb DTF actions. The Element subtag is
 *               where the logic for generating metadata and metadata comparison
 *               has been coded. To better understand that read the section on 
 *               {@link Element Element} tag.
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
 *         <metadata id="MDREF">
 *             <element value="${hc.mp3.title.regexp}"  type="string" name="mp3.title"/> 
 *             <element value="${hc.mp3.artist.regexp}" type="string" name="mp3.artist"/> 
 *             <element value="${hc.mp3.album.regexp}"  type="string" name="mp3.album"/> 
 *             <element value="${hc.mp3.date.regexp}"   type="long"   name="mp3.date"/> 
 *             <element value="${hc.mp3.type.regexp}"   type="string" name="mp3.type"/>
 *         </metadata>
 *     </store>
 * </component> 
 * 
 * @dtf.tag.example 
 * <component id="${client}">
 *     <retrievemd datavip="${hc.cluster.datavip}"
 *                 port="${hc.cluster.dataport}"
 *                 oid="${obj1.oid}"
 *                 verify="true">
 *         <metadata refid="MDREF"/>
 *     </retrievemd>
 * </component>
 * 
 */
public class Metadata extends Referencable {

    public NameValueRecord genNVR(NameValueObjectArchive nvoa) 
           throws DTFException {
        ArrayList children  = findActions(Element.class);
        NameValueRecord nvr = nvoa.createRecord();
        
        for (int index = 0; index < children.size(); index++) { 
            Element element = (Element) children.get(index);

            if (element.getType().equals(Element.LONG_TYPE)) {
                try { 
	                Long l = new Long(element.resolvedValue());
	                nvr.put(element.getName(),l.longValue());
                } catch (NumberFormatException e) { 
                    throw new ParseException("Unable to parse long: " 
                                             + element.getName(), e);
                }
            } else  if (element.getType().equals(Element.INTEGER_TYPE)) {
                try { 
	                Integer i = new Integer(element.resolvedValue());
	                nvr.put(element.getName(),i.intValue());
                } catch (NumberFormatException e) { 
                    throw new ParseException("Unable to parse integer: " 
                                             + element.getName(),e);
                }
            } else if (element.getType().equals(Element.DOUBLE_TYPE)) {
                try { 
	                Double d = new Double(element.resolvedValue());
	                nvr.put(element.getName(),d.doubleValue());
                } catch (NumberFormatException e) { 
                    throw new ParseException("Unable to parse double: " 
                                             + element.getName(),e);
                }
            } else if (element.getType().equals(Element.STRING_TYPE)) {
                nvr.put(element.getName(),element.resolvedValue());
            }
            
        }
       
        return nvr;
    }
}

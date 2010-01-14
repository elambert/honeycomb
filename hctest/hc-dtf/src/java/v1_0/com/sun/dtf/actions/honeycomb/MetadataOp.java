package com.sun.dtf.actions.honeycomb;

import java.io.IOException;
import java.util.ArrayList;

import com.sun.dtf.exception.DTFException;
import com.sun.dtf.recorder.Event;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.NameValueSchema;
import com.sun.honeycomb.client.NameValueSchema.ValueType;
import com.sun.honeycomb.common.ArchiveException;

public abstract class MetadataOp {
    
    /**
     * Process the Metadata record that was associated with the current object
     * in the Event class so that this can be recorded for comparison later.
     * 
     * @param event
     * @param nvr
     * @throws IOException 
     * @throws ArchiveException 
     * @throws DTFException 
     */
    public static void processMetadata(Event event, 
                                       NameValueRecord nvr,
                                       Metadata metadata,
                                       NameValueSchema schema)
                  throws ArchiveException, IOException, DTFException { 
        // add all of the NameValueRecords that were stored
        if (nvr != null) { 
            String[] keys = nvr.getKeys(); 
            for(int index = 0; index < keys.length; index++) {
                String key = keys[index];
                ValueType type = nvr.getAttributeType(key);
                   
                /*
                 * XXX: verify types!
                 */
                if (type.equals(NameValueSchema.DOUBLE_TYPE)) {
                    event.addAttribute(keys[index], nvr.getDouble(key));
                } else if (type.equals(NameValueSchema.LONG_TYPE)) {
                    event.addAttribute(keys[index], nvr.getLong(key));
                } else if (type.equals(NameValueSchema.STRING_TYPE)) {
                    // no schema.getLength in 1.0 so we have to fish through 
                    // the metadata record :(
                    ArrayList elements = metadata.findActions(Element.class);
                    int length = 0;
                    
                    for (int i = 0; i < elements.size(); i++) { 
                        Element element = (Element) elements.get(i);
                        if (element.getName().equals(keys[index])) {
                            length = element.getLength();
                            break;
                        }
                    }
                    
                    event.addAttribute(keys[index], 
                                       nvr.getString(keys[index]),
                                       length,
                                       false);
                } else 
                    throw new DTFException("Unkown type [" + type + "]");
            }
        }
    }

    /**
     * 
     * @param nvr
     * @param nvrm
     * @throws DTFException
     */
    public static void verifyNVRs(NameValueRecord nvr, NameValueRecord nvrm) throws DTFException { 
        String[] keys = nvr.getKeys(); 
        
        if (nvr == null && nvrm != null) 
            throw new DTFException("Expected a metadata record got nothing.");
        else if (nvr != null && nvrm == null) 
            throw new DTFException("Expected no metadata record got: " + nvr);
        
        for(int index = 0; index < keys.length; index++) {
            String key = keys[index];
            ValueType typem = nvrm.getAttributeType(key);
            ValueType type = nvr.getAttributeType(key);
                            
            if (!type.equals(typem)) 
                throw new DTFException("Type mismatch for field [" + 
                                       key + ", expected [" + typem + 
                                       "] got [" + type + "]");
                          
            // XXX: I should be using NameValueRecord to compare
            //      the two nvr's but the reality is that 
            //      NameValueRecord has a horrible equlas operator.
            if (type.equals(NameValueSchema.DOUBLE_TYPE)) {
                double valuem = nvrm.getDouble(key);
                double value = nvr.getDouble(key);
                if (valuem != value)
                    throw new DTFException("Value mismatch for field [" + 
                                           key + ", expected [" + valuem + 
                                           "] got [" + value + "]");
            } else if (type.equals(NameValueSchema.LONG_TYPE)) {
                long valuem = nvrm.getLong(key);
                long value = nvr.getLong(key);
                if (valuem != value)
                    throw new DTFException("Value mismatch for field [" + 
                                           key + ", expected [" +  valuem + 
                                           "] got [" +  value + "]");
            } else if (type.equals(NameValueSchema.STRING_TYPE)) {
                String valuem = nvrm.getString(key);
                String value = nvr.getString(key);
                if (!valuem.equals(value))
                    throw new DTFException("Value mismatch for field [" + 
                                           key + ", expected [" + valuem + 
                                           "] got [" + value + "]");
            } else 
                throw new DTFException("Unkown type [" + type + "]");
        }
    }
}

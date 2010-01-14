package com.sun.dtf.actions.honeycomb;

import java.io.IOException;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

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
                    event.addAttribute(keys[index], nvr.getDouble(keys[index]));
                } else if (type.equals(NameValueSchema.LONG_TYPE)) {
                    event.addAttribute(keys[index], nvr.getLong(keys[index]));
                } else if (type.equals(NameValueSchema.STRING_TYPE)) {
                    event.addAttribute(keys[index], 
                                       nvr.getString(keys[index]),
                                       schema.getLength(keys[index]),
                                       false);
                } else 
                    throw new DTFException("Unkown type [" + type + "]");
            }
        }
    }

    public static void verifyNVRs(NameValueRecord nvr, NameValueRecord nvrm) throws DTFException { 
        String[] keys = nvr.getKeys(); 
        for(int index = 0; index < keys.length; index++) {
            String key = keys[index];
            ValueType typem = nvrm.getAttributeType(key);
            ValueType type = nvr.getAttributeType(key);
                            
            if (!type.equals(typem)) 
                throw new DTFException("Type mismatch for field [" + 
                                       key + ", expected [" + 
                                       typem + "] got [" + type + "]");
                          
            // XXX: I should be using NameValueRecord to compare
            //      the two nvr's but the reality is that 
            //      NameValueRecord has a horrible equlas operator.
                            
            if (type.equals(NameValueSchema.DOUBLE_TYPE)) {
                double valuem = nvrm.getDouble(key);
                double value = nvr.getDouble(key);
                if (valuem != value)
                    throw new DTFException("Value mismatch for field [" + 
                                       key + ", expected [" + 
                                       valuem + "] got [" + 
                                       value + "]");
            } else if (type.equals(NameValueSchema.LONG_TYPE)) {
                long valuem = nvrm.getLong(key);
                long value = nvr.getLong(key);
                if (valuem != value)
                    throw new DTFException("Value mismatch for field [" + 
                                       key + ", expected [" + 
                                       valuem + "] got [" + 
                                       value + "]");
            } else if (type.equals(NameValueSchema.STRING_TYPE)) {
                String valuem = nvrm.getString(key);
                String value = nvr.getString(key);
                if (!valuem.equals(value))
                    throw new DTFException("Value mismatch for field [" + 
                                       key + ", expected [" + 
                                       valuem + "] got [" + 
                                       value + "]");
            } else if (type.equals(NameValueSchema.BINARY_TYPE)) {
                byte[] valuem = nvrm.getBinary(key);
                byte[] value = nvr.getBinary(key);
                if (!valuem.equals(value))
                    throw new DTFException("Value mismatch for field [" + 
                                       key + ", expected [" + 
                                       valuem + "] got [" + 
                                       value + "]");
            } else if (type.equals(NameValueSchema.DATE_TYPE)) {
                Date valuem = nvrm.getDate(key);
                Date value = nvr.getDate(key);
                if (!valuem.equals(value))
                    throw new DTFException("Value mismatch for field [" + 
                                       key + ", expected [" + 
                                       valuem + "] got [" + 
                                       value + "]");
            } else if (type.equals(NameValueSchema.TIME_TYPE)) {
                Time valuem = nvrm.getTime(key);
                Time value = nvr.getTime(key);
                if (!valuem.equals(value))
                    throw new DTFException("Value mismatch for field [" + 
                                       key + ", expected [" + 
                                       valuem + "] got [" + 
                                       value + "]");
            } else if (type.equals(NameValueSchema.TIMESTAMP_TYPE)) {
                Timestamp valuem = nvrm.getTimestamp(key);
                Timestamp value = nvr.getTimestamp(key);
                if (!valuem.equals(value))
                    throw new DTFException("Value mismatch for field [" + 
                                       key + ", expected [" + 
                                       valuem + "] got [" + 
                                       value + "]");
            } else if (type.equals(NameValueSchema.CHAR_TYPE)) {
                // XXX: no getter ? wtf ? 
                throw new RuntimeException("No getter for char type");
            } else 
                throw new DTFException("Unkown type [" + type + "]");
        }
    }
}

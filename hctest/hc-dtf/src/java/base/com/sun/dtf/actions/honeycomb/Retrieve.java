package com.sun.dtf.actions.honeycomb;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;


import com.sun.dtf.actions.honeycomb.channels.CorruptingWritableChannel;
import com.sun.dtf.actions.honeycomb.channels.MonitoringWritableByteChannel;
import com.sun.dtf.actions.honeycomb.channels.NullWritableByteChannel;
import com.sun.dtf.actions.honeycomb.channels.VerifyWritableChannel;
import com.sun.dtf.actions.honeycomb.util.NVOAPool;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.recorder.Event;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;

/**
 * @dtf.tag retrieve
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Honeycomb Retrieve tag allows you to retrieve previously stored
 *               with other honeycomb operations and to be able to verify if 
 *               certain properties have not changed due to corruption or data
 *               loss.
 *               
 * @dtf.event hc.retrieve
 * @dtf.event.attr succeeded 
 * @dtf.event.attr.desc This event is used when continueOnFailure is set to true 
 *                      and the operation may have failed. In case of failure 
 *                      this will also set the exception attribute in this event.
 *                 
 * @dtf.event hc.retrieve
 * @dtf.event.attr exception
 * @dtf.event.attr.desc Contains an exception stacktrace if the succeeded is  
 *                      attribute is set to false.
 *               
 * @dtf.event hc.retrieve
 * @dtf.event.attr oid
 * @dtf.event.attr.desc The oid of the object being retrieved.
 *
 * @dtf.event hc.retrieve
 * @dtf.event.attr size
 * @dtf.event.attr.desc The sizeof the object being retrieved.
 *
 * @dtf.event hc.retrieve
 * @dtf.event.attr firstByteLatency
 * @dtf.event.attr.desc The sizeof the object being retrieved.
 *                      
 * @dtf.event hc.retrieve
 * @dtf.event.attr bandwidth
 * @dtf.event.attr.desc Bandwidth attribute holds a list of elements separated
 *                      by a coma, in which, each of the elements is the amount
 *                      of bandwidth used by this specific Thread over the last
 *                      5 seconds of retrieving from the cluster.
 *                      
 * @dtf.tag.example 
 * <component id="${client}">
 *     <retrieve datavip="${hc.cluster.datavip}"
 *               port="${hc.cluster.dataport}"
 *               oid="${obj1.oid}"
 *               verify="true">
 *         <file length="${obj1.size}" type="${hc.filetype}"/>
 *     </retrieve>
 * </component>
 * 
 * @dtf.tag.example 
 * <component id="${client}">
 *     <retrieve datavip="${hc.cluster.datavip}"
 *               port="${hc.cluster.dataport}"
 *               oid="${obj1.oid}"
 *               continueOnFailure="true"/>
 * </component>
 * 
 */
public class Retrieve extends HCRetrieveOperation {

    /** 
     * @dtf.attr firstByte
     * @dtf.attr.desc The offset of the first byte for a range retrieve operation.
     */
    private String firstByte = null;

    /** 
     * @dtf.attr lastByte
     * @dtf.attr.desc The offset of the last byte for a range retrieve operation.
     */
    private String lastByte = null;
    
    public Retrieve() { }
    
    public void execute() throws DTFException {
        connectionSetup();
        
        NameValueObjectArchive archive = 
              NVOAPool.getNVOA(getDatavip(), new Integer(getPort()).intValue());
     
        try {
            Event event = createHCOpEvent(HCEventConstants.HC_RETRIEVE_EVENT);
           
            File file = (File)findFirstAction(File.class);
            WritableByteChannel channel = null;
            MonitoringWritableByteChannel mchannel = null;
            
            if (isVerify()) { 
                if (file == null)
                    throw new DTFException("Verify option was used without an underlying <file> child tag.");
                
                getLogger().info("Verifying data on retrieve for oid [" + 
                                 getOid() + "]");
                
                channel = new VerifyWritableChannel(file.byteChannel());
            } else  {
                channel = new NullWritableByteChannel();
            }
            
            if (isMonitoring()) { 
                mchannel = new MonitoringWritableByteChannel(channel);
                channel = mchannel;
            }
            
            if (isCorrupting()) { 
                channel = new CorruptingWritableChannel(channel);
            }
                       
           
            if ((firstByte == null && lastByte != null) ||
                (firstByte != null && lastByte == null)) { 
                throw new ParseException("firstByte and lastByte must be both set or non at all.");
            }
          
            ObjectIdentifier oid = new ObjectIdentifier(getOid());
            long size = -1;
            try { 
                if (firstByteAsLong() != -1 && lastByteAsLong() != -1) {
                    event.start();
                    size = archive.retrieveObject(oid, 
                                                  channel, 
                                                  firstByteAsLong(),
                                                  lastByteAsLong());
                    event.stop();
                    channel.close();
                    getLogger().info("RangeRetrieve from " + getFirstbyte() + 
                                     " to " + getLastbyte() + " byte OID[" + 
                                     getOid() + "]");
                } else { 
                    event.start();
                    size = archive.retrieveObject(oid, channel);
                    event.stop();
                    channel.close();
                    getLogger().info("Retrieved OID:[" + getOid() + "]");
                }
                
                // DO NOT set the oid as an index because this will update 
                // all of the previous entries recorded in the DB_RECORDER
                event.addAttribute("oid", getOid());
                event.addAttribute("size", size);
                
                if (mchannel != null) {
                    /*
                     * First byte latency measurement in milliseconds
                     */
                    event.addAttribute("firstByteLatency",
                                       mchannel.getFirstByteWritten() - 
                                       event.getStart());
                    event.addAttribute("bandwidth", 
                                       mchannel.getBandWidthUsageString());
                }
            } catch (ArchiveException e) { 
                if (continueOnFailure()) { 
                    processFailure(oid, event, e);
                } else 
                    throw e;
            } catch (IOException e) { 
                if (continueOnFailure()) { 
                    processFailure(oid, event, e);
                } else 
                    throw e;
            }
            
            getRecorder().record(event);
        } catch (ArchiveException e) {
            throw new DTFException("Error retrieving eobject.", e);
        } catch (IOException e) {
            throw new DTFException("Error retrieving object.", e);
        }
    }
    
    public void setFirstbyte(String first) { firstByte = first; }
    public long firstByteAsLong() throws ParseException { return toLong("firstByte", firstByte, -1); } 
    public String getFirstbyte() throws ParseException { return replaceProperties(firstByte); } 
  
    public void setLastbyte(String last) { lastByte = last; }
    public long lastByteAsLong() throws ParseException { return toLong("lastByte", lastByte, -1); } 
    public String getLastbyte() throws ParseException { return replaceProperties(lastByte); } 
    
}
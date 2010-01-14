package com.sun.dtf.actions.honeycomb;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;


import com.sun.dtf.actions.honeycomb.channels.MonitoringReadableByteChannel;
import com.sun.dtf.actions.honeycomb.util.NVOAPool;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.recorder.Event;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.common.ArchiveException;

/**
 * @dtf.tag store
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Honeycomb Store tag allows you to store a specified file of a 
 *               certain size and type (File types covered in {@dtf.link File}).
 *               Associated with this store you can also include a meta data 
 *               child tag that will associate meta data with the stored object.
 *               
 * @dtf.event hc.object hc.store
 * @dtf.event.attr succeeded 
 * @dtf.event.attr.desc This event is used when continueOnFailure is set to true 
 *                      and the operation may have failed. In case of failure 
 *                      this will also set the exception attribute in this event.
 *                 
 * @dtf.event hc.object hc.store
 * @dtf.event.attr exception
 * @dtf.event.attr.desc Contains an exception stacktrace if the succeeded is  
 *                      attribute is set to false.
 *               
 * @dtf.event hc.object hc.store
 * @dtf.event.attr oid
 * @dtf.event.attr.desc The oid of the object being stored.
 *
 * @dtf.event hc.object hc.store
 * @dtf.event.attr link
 * @dtf.event.attr.desc The link oid to the data object of this meta data oid, 
 *                      only available on 1.0 
 * 
 * @dtf.event hc.object hc.store
 * @dtf.event.attr size
 * @dtf.event.attr.desc The size in bytes of the object being stored
 * 
 * @dtf.event hc.object hc.store
 * @dtf.event.attr ctime
 * @dtf.event.attr.desc The cluster side creation time of the object that was 
 *                      stored, this is in the Unix time stamp format.
 *
 * @dtf.event hc.object hc.store
 * @dtf.event.attr dtime
 * @dtf.event.attr.desc The cluster side deletion time of the object that was 
 *                      stored, this is in the Unix time stamp format. Should be
 *                      -1 right after the store operation.
 *
 * @dtf.event hc.object hc.store
 * @dtf.event.attr hash
 * @dtf.event.attr.desc The sha1 of this object as calculated by the cluster side 
 *                      during the store operation.
 *                 
 * @dtf.event hc.object hc.store
 * @dtf.event.attr file.type
 * @dtf.event.attr.desc The file type that was used at store time for this 
 *                      specific operation.
 *
 * @dtf.event hc.store
 * @dtf.event.attr commitLatency
 * @dtf.event.attr.desc Commit latency is the time it took from the last byte
 *                      being read from the ReadableByteChannel to the Honeycomb
 *                      API returning an OID to the Store action.
 *
 * @dtf.event hc.store
 * @dtf.event.attr bandwidth
 * @dtf.event.attr.desc Bandwidth attribute holds a list of elements separated
 *                      by a coma, in which, each of the elements is the amount
 *                      of bandwidth used by this specific Thread over the last
 *                      5 seconds of storing to the cluster.
 *                      
 * @dtf.event hc.object hc.store
 * @dtf.event.attr file.length
 * @dtf.event.attr.desc The length of the file that was specified to use.
 * 
 * @dtf.event hc.object hc.store
 * @dtf.event.attr file.offset
 * @dtf.event.attr.desc The offset used for randomizing the byte patterns used
 *                      to generate binary/deadbeef data.
 *
 * @dtf.event hc.object
 * @dtf.event.attr [MD FIELD]
 * @dtf.event.attr.desc All meta data fields are recorded as events with the name 
 *                      of the event attribute being equal to the name of the 
 *                      field.
 *
 * @dtf.event hc.object
 * @dtf.event.attr storewidthmd
 * @dtf.event.attr.desc If storewithmd is true it means that the store was done
 *                      with some valid extended meta data, otherwise no meta 
 *                      data was specified or used.
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
 * 
 */
public class Store extends HCBasicOperation {

    public Store() { }
    
    public void execute() throws DTFException {
        connectionSetup();
        
        NameValueObjectArchive archive = 
              NVOAPool.getNVOA(getDatavip(), new Integer(getPort()).intValue());

        try {
            Event objEvent = createHCOpEvent(HCEventConstants.HC_OBJECT_EVENT);
            Event storeEvent = createHCOpEvent(HCEventConstants.HC_STORE_EVENT);
            File file = (File)findFirstAction(File.class);
           
            if (file == null) 
                throw new DTFException("Unable to find child tag <file>.");
            
            // Check for metadata child
            Metadata metadata = (Metadata)findFirstAction(Metadata.class);
            NameValueRecord nvr = null;
            
            if (metadata != null) {
                nvr = metadata.genNVR(archive);
                if (nvr.getKeys().length != 0)
                    objEvent.addAttribute("storewithmd","true");
                else
                    objEvent.addAttribute("storewithmd","false");
            } else 
                objEvent.addAttribute("storewithmd","false");
           
            ReadableByteChannel channel = null;
            if (isMonitoring()) { 
                channel = new MonitoringReadableByteChannel(file.byteChannel());
            } else { 
                channel = file.byteChannel();
            }
            
            try {
                storeEvent.start();
                SystemRecord sr = archive.storeObject(channel,nvr);
                storeEvent.stop();
                
                objEvent.setStart(storeEvent.getStart());
                objEvent.setStop(storeEvent.getStop());
                
                getLogger().info("Stored object " + sr.getObjectIdentifier());
                
                // not pointing to any other object.
                processProps(ObjectIdentifier.ObjectIdentifierEOF, objEvent, sr);
                processProps(ObjectIdentifier.ObjectIdentifierEOF, storeEvent, sr);

                if (isMonitoring()) { 
                    MonitoringReadableByteChannel mChannel = 
                                        (MonitoringReadableByteChannel) channel;
                    /**
                     * This is the commit latency for an object, from the time we gave
                     * the last byte to the honeycomb client to the time it took to 
                     * end the process of storing.
                     */
                    storeEvent.addAttribute("commitLatency",
                                  storeEvent.getStop() - mChannel.getLastByteRead());
                    storeEvent.addAttribute("bandwidth", 
                                                 mChannel.getBandWidthUsageString());
                }
                
                // save any metadata relevant information
                MetadataOp.processMetadata(objEvent,
                                           nvr,
                                           metadata,
                                           archive.getSchema()); 
                
                // add any file specific attributes to the event
                file.processAttributes(storeEvent);
                file.processAttributes(objEvent);
                
                getRecorder().record(storeEvent);
                getRecorder().record(objEvent);
            } catch (ArchiveException e) { 
                if (continueOnFailure()) {
                    processFailure(ObjectIdentifier.ObjectIdentifierEOF, 
                                   storeEvent, 
                                   e);
                } else
                    throw e;
            } catch (IOException e) { 
                if (continueOnFailure()) {
                    processFailure(ObjectIdentifier.ObjectIdentifierEOF, 
                                   storeEvent, 
                                   e);
                } else
                    throw e;
            } 
        } catch (ArchiveException e) {
            throw new DTFException("Error storing object.", e);
        } catch (IOException e) {
            throw new DTFException("Error storing object.", e);
        }
    }
}

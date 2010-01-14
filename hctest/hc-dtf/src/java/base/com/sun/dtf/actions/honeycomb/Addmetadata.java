package com.sun.dtf.actions.honeycomb;

import java.io.IOException;


import com.sun.dtf.actions.honeycomb.util.NVOAPool;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.recorder.Event;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.common.ArchiveException;

/**
 * @dtf.tag addmetadata
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Honeycomb Add meta data tag allows you to add more meta data 
 *               records to an existing honeycomb object. The new meta data 
 *               fields will generate a new OID and this operation has similar 
 *               events and attributes to the store operation.
 * 
 * @dtf.event hc.addmd
 * @dtf.event.attr succeeded 
 * @dtf.event.attr.desc This event is used when continueOnFailure is set to true 
 *                      and the operation may have failed. In case of failure 
 *                      this will also set the exception attribute in this event.
 *                 
 * @dtf.event hc.addmd
 * @dtf.event.attr exception
 * @dtf.event.attr.desc Contains an exception stacktrace if the succeeded is  
 *                      attribute is set to false.
 *               
 * @dtf.event hc.object hc.addmd
 * @dtf.event.attr oid
 * @dtf.event.attr.desc The oid of the object being stored.
 * 
 * @dtf.event hc.object hc.addmd
 * @dtf.event.attr size
 * @dtf.event.attr.desc The size in bytes of the object being stored
 * 
 * @dtf.event hc.object hc.addmd
 * @dtf.event.attr ctime
 * @dtf.event.attr.desc The cluster side creation time of the object that was 
 *                      stored, this is in the Unix time stamp format.
 *
 * @dtf.event hc.object hc.addmd
 * @dtf.event.attr dtime
 * @dtf.event.attr.desc The cluster side deletion time of the object that was 
 *                      stored, this is in the Unix time stamp format. Should be
 *                      -1 right after the store operation.
 *
 * @dtf.event hc.object hc.addmd
 * @dtf.event.attr hash
 * @dtf.event.attr.desc The sha1 of this object as calculated by the cluster side 
 *                      during the store operation.
 *
 * @dtf.event hc.object 
 * @dtf.event.attr storewidthmd
 * @dtf.event.attr.desc If storewithmd is true it means that the store was done
 *                      with some valid extended meta data, otherwise no meta 
 *                      data was specified or used.
 *                      
 * @dtf.event hc.object
 * @dtf.event.attr [MD FIELD]
 * @dtf.event.attr.desc All meta data fields are recorded as events with the name 
 *                      of the event attribute being equal to the name of the 
 *                      field.
 *                      
 * @dtf.tag.example 
 * <addmetadata datavip="${hc.cluster.datavip}"
 *              port="${hc.cluster.dataport}"
 *              oid="${object.oid}"
 *              continueOnFailure="${hc.continueonfailure}">
 *       <metadata refid="MDREF" />
 * </addmetadata>
 *
 * @dtf.tag.example 
 * <addmetadata datavip="${hc.cluster.datavip}"
 *              port="${hc.cluster.dataport}"
 *              oid="${object.oid}">
 *       <metadata>
 *           <element value="${hc.mp3.title.regexp}"  type="string" name="mp3.title"/> 
 *           <element value="${hc.mp3.artist.regexp}" type="string" name="mp3.artist"/> 
 *           <element value="${hc.mp3.album.regexp}"  type="string" name="mp3.album"/> 
 *           <element value="${hc.mp3.date.regexp}"   type="long"   name="mp3.date"/> 
 *           <element value="${hc.mp3.type.regexp}"   type="string" name="mp3.type"/>
 *       </metadata>
 * </addmetadata>
 */
public class Addmetadata extends HCObjectOperation {

    public Addmetadata() { }
    
    public void execute() throws DTFException {
        connectionSetup();
        
        NameValueObjectArchive archive = 
              NVOAPool.getNVOA(getDatavip(), new Integer(getPort()).intValue());
     
        try {
            Event objEvent = createHCOpEvent(HCEventConstants.HC_OBJECT_EVENT);
            Event mdEvent = createHCOpEvent(HCEventConstants.HC_ADDMD_EVENT);

            // Check for metadata child
            Metadata metadata = (Metadata)findFirstAction(Metadata.class);
            NameValueRecord nvr = metadata.genNVR(archive);
            ObjectIdentifier oid = new ObjectIdentifier(getOid());
            
            try { 
                MetadataOp.processMetadata(objEvent,
                                           nvr,
                                           metadata,
                                           archive.getSchema());
                
                objEvent.start();
                SystemRecord sr = archive.storeMetadata(oid, nvr);
                objEvent.stop();
                
                mdEvent.setStart(objEvent.getStart());
                mdEvent.setStop(objEvent.getStop());
                mdEvent.addAttribute("result", "succeeded");
                
                getLogger().info("AddMD " + sr.getObjectIdentifier());
                processProps(oid, objEvent, sr);
                processProps(oid, mdEvent, sr);
                
                objEvent.addAttribute("storewithmd","true");
                
                getRecorder().record(objEvent);
                getRecorder().record(mdEvent);
            } catch (ArchiveException e) { 
                if (continueOnFailure()) { 
                    processFailure(oid, mdEvent, e);
                    getRecorder().record(mdEvent);
                } else 
                    throw e;
            } catch (IOException e) { 
                if (continueOnFailure()) { 
                    processFailure(oid, mdEvent, e);
                    getRecorder().record(mdEvent);
                } else 
                    throw e;
            }
        } catch (ArchiveException e) {
            throw new DTFException("Error during addmd.", e);
        } catch (IOException e) {
            throw new DTFException("Error during addmd.", e);
        }
    }
}

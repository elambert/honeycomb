package com.sun.dtf.actions.honeycomb;

import java.io.IOException;

import com.sun.dtf.actions.honeycomb.HCEventConstants;
import com.sun.dtf.actions.honeycomb.HCRetrieveOperation;
import com.sun.dtf.actions.honeycomb.Metadata;
import com.sun.dtf.actions.honeycomb.util.NVOAPool;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.recorder.Event;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;

/**
 * @dtf.tag retrievemd
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc <p> 
 *               Honeycomb Retrieve meta data tag allows you to retrieve 
 *               previously stored meta data, and validate that it has not 
 *               been affected by any sort of corruption.
 *               </p>
 *               <p>
 *               The validation itself is done on a per field basis and will 
 *               even validate that the type of fields returned with the meta
 *               data from the cluster matchup with the meta data types specified
 *               in the Metadata element.
 *               </p>
 *               
 * @dtf.event hc.retrievemd
 * @dtf.event.attr succeeded 
 * @dtf.event.attr.desc This event is used when continueOnFailure is set to true 
 *                      and the operation may have failed. In case of failure 
 *                      this will also set the exception attribute in this event.
 *                 
 * @dtf.event hc.retrievemd
 * @dtf.event.attr exception
 * @dtf.event.attr.desc Contains an exception stacktrace if the succeeded is  
 *                      attribute is set to false.
 *               
 * @dtf.event hc.retrievemd
 * @dtf.event.attr oid
 * @dtf.event.attr.desc The oid of the object being retrieved.
 *
 * @dtf.event hc.retrievemd
 * @dtf.event.attr fields
 * @dtf.event.attr.desc The number of fields returned with this retrieve meta 
 *                      data operation.
 * 
 * @dtf.tag.example 
 * <component id="${client}">
 *     <retrievemd datavip="${hc.cluster.datavip}"
 *                 port="${hc.cluster.dataport}" 
 *                 oid="${object.oid}" 
 *                 verify="${hc.data.verify}"
 *                 continueOnFailure="${hc.continueonfailure}">
 *            <metadata refid="MDCMP"/>
 *     </retrievemd>
 * </component>
 * 
 * @dtf.tag.example 
 * <component id="MYCLIENT">
 *     <retrievemd datavip="${hc.cluster.datavip}"
 *                 port="${hc.cluster.dataport}" 
 *                 oid="${object.oid}">
 *            <metadata refid="MDCMP"/>
 *     </retrievemd>
 * </component>
 * 
 */
public class Retrievemd extends HCRetrieveOperation {

    public Retrievemd() { }
    
    public void execute() throws DTFException {
        connectionSetup();
        
        NameValueObjectArchive archive =
              NVOAPool.getNVOA(getDatavip(), new Integer(getPort()).intValue());
     
        try {
            Event event = createHCOpEvent(HCEventConstants.HC_RETMD_EVENT);
            ObjectIdentifier oid = new ObjectIdentifier(getOid());
            try { 
                event.start();
                NameValueRecord nvr = archive.retrieveMetadata(oid);
                event.stop();
                
                if (isVerify()) { 
                    getLogger().info("Verifying metadata on retrieve for oid [" + 
                                     getOid() + "]");
                    
                    Metadata metadata = (Metadata) findFirstAction(Metadata.class);
                    if ((nvr == null)  && (metadata != null)) { 
                        throw new DTFException("Metadata child specified, but oid [" + 
                                           getOid() + "] has no metadata associated.");
                    } else if ((metadata == null) && (nvr.getKeys().length != 0)) {
                        throw new DTFException("No metadata child specified, but oid [" + 
                                           getOid() + "] has metadata associated.");
                    } else { 
                        /*
                         * Verify the metadata returned against the metadata child
                         * tag.
                         */
                        // add all of the NameValueRecords that were stored
                        NameValueRecord nvrm = null;
                        if (metadata != null)
                            nvrm = metadata.genNVR(archive);
                        
                        MetadataOp.verifyNVRs(nvr, nvrm);
                    }
                }
                
                getLogger().info("Retrieved Metadata for " + getOid());
                event.addAttribute("oid", getOid());
                event.addAttribute("fields",nvr.getKeys().length);

                getRecorder().record(event);
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
        } catch (ArchiveException e) {
            throw new DTFException("Error retrieving metadata.", e);
        } catch (IOException e) {
            throw new DTFException("Error retrieving metadata.", e);
        }
    }
}

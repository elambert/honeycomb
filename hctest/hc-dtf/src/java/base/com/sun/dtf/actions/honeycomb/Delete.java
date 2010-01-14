package com.sun.dtf.actions.honeycomb;

import java.io.IOException;


import com.sun.dtf.actions.honeycomb.util.NVOAPool;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.recorder.Event;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;

/**
 * @dtf.tag delete
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc Delete tag is used to delete objects from honeycomb by 
 *               referencing an OID. This tag 
 * 
 *               Events are thrown during the execution of this tag they include:
 *               
 * @dtf.tag.example 
 * 
 */
public class Delete extends HCObjectOperation {

    public Delete() { }
    
    public void execute() throws DTFException {
        connectionSetup();
        
        NameValueObjectArchive archive = 
                                       NVOAPool.getNVOA(getDatavip(),getPort());
    
        ObjectIdentifier oid = new ObjectIdentifier(getOid());
        try {
            Event objEvent = createHCOpEvent(HCEventConstants.HC_OBJECT_EVENT);
            Event delEvent = createHCOpEvent(HCEventConstants.HC_DELETE_EVENT);

            try { 
                delEvent.start();
                archive.delete(oid);
                delEvent.stop();
                
                objEvent.setStart(delEvent.getStart());
                objEvent.setStop(delEvent.getStop());
                
                getLogger().info("Deleted " + getOid());
                
                /*
                 * Update the deleted status on the object that was just 
                 * deleted.
                 */
                objEvent.addAttribute("oid", getOid(),true);
                objEvent.addAttribute("deleted", "true");
                
                delEvent.addAttribute("oid", getOid(),true);
                
                getRecorder().record(objEvent);
                getRecorder().record(delEvent);
            } catch (ArchiveException e) { 
                if (continueOnFailure()) { 
                    processFailure(oid, delEvent, e);
                    getRecorder().record(delEvent);
                } else 
                    throw e;
            } catch (IOException e) { 
                if (continueOnFailure()) { 
                    processFailure(oid, delEvent, e);
                    getRecorder().record(delEvent);
                } else 
                    throw e;
            }
        } catch (ArchiveException e) {
            throw new DTFException("Error deleting object.", e);
        } catch (IOException e) {
            throw new DTFException("Error deleting object.", e);
        }
    }
}

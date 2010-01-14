/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



package com.sun.honeycomb.emd.remote;

import com.sun.honeycomb.emd.EMDClient;

import java.util.Iterator;
import com.sun.honeycomb.emd.common.EMDException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.DataOutput;
import java.util.TreeSet;
import java.lang.Comparable;
import java.util.ArrayList;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.emd.common.EMDCommException;
import com.sun.honeycomb.emd.common.HexObjectIdentifier;

/**
 * This class is used to get one by one the objects returned from a Stream,
 * after a remote invocation.<br><br>
 *
 * This is used essentially by the Merger to merge all the results from a
 * method invocation.
 */

public class StreamHead 
    implements Comparable {

    private static final Logger LOG = Logger.getLogger("StreamHead");

    private MDInputStream input;

    private Object currentObject;
    private boolean firstObjectRead;

    public StreamHead(MDInputStream newInput) {
        input = newInput;
        currentObject = null;
        firstObjectRead = false;
    }
    
    
    private void moveToNext(boolean force) 
        throws EMDException {
        if ((!force) && (currentObject == null)) {
            // We already reached the end of the stream
            return;
        }

        currentObject = null;

        try {
            currentObject = input.getObject();
        } catch (EMDCommException e) {
        	LOG.warning("Failed to read object in the sd ["+
                        e.getMessage()+"]");
            currentObject = null;
            throw new EMDException ("exception reading from streamhead", e);
        }

        if ((currentObject != null)
            && (currentObject instanceof MDInputStream.EndOfStream)) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Reached the end of the stream");
            }
            currentObject = null;
        }
    }

    public Object current() 
        throws EMDException {
        if ((currentObject == null) 
            && (!firstObjectRead)) {
            moveToNext(true);
            firstObjectRead = true;
        }
        return(currentObject);
    }
    
    public void moveToNext() 
        throws EMDException {
        moveToNext(false);
    }

    public int compareTo(Object other) {
        if (!(other instanceof StreamHead)) {
            LOG.log(Level.SEVERE,
                    "Comparison with a non StreamHead object !");
            return(0);
        }

        Comparable o1 = null;
        Comparable o2 = null;

        try {
            o1 = (Comparable)current();
        } catch (EMDException ignored) {
        }

        try {
            o2 = (Comparable)((StreamHead)other).current();
        } catch (EMDException ignored) {
        }

        if (o1 == null) {
            if (o2 == null) {
                return(0);
            }
            return(-1);
        } else if (o2 == null) {
            return(1);
        }
        
        return(o1.compareTo(o2));
    }

    /**
     * This is the main merge operation for Metadata.
     *
     * The code merges the objects found in the input streams and send the
     * result in the output streams
     * @param inputs a <code>StreamHead[]</code> value
     * @param nbResults an <code>int</code> value
     * @return an <code>ArrayList</code> value
     * @exception EMDException if an error occurs
     */

    public static ArrayList mergeStreams(StreamHead[] inputs,
                                         int toBeSkipped,
                                         int nbResults,
                                         boolean abortOnFailure)
        throws EMDException {
        MergeEngine merger = new MergeEngine(inputs);
        Object obj = null;
        int nbSent = 0;
        int errorCount = 0;
        ArrayList result = new ArrayList();

        while ( ((nbResults == -1) || (nbSent < nbResults+toBeSkipped))) {
        	try {
        		obj = merger.getFirst();
        	} catch (EMDException me) {
        		LOG.info (" error merging the streams... (" + (errorCount+1) + ") "
        				+ me.toString());
            	if (abortOnFailure) {
            	    if ((++errorCount) > EMDClient.MAX_ERROR_COUNT) {
                        LOG.warning ("too many failures trying to merge query results ("
                                + errorCount + ")" + me.toString());
                        throw new EMDException (
                                "too many failures trying to merge query results (" 
                                + errorCount +")", me);
            	    } else {
            	        continue;
            	    } 
            	}
        	}
        	
        	if (obj == null)
        		break;
        	
            if (nbSent >= toBeSkipped) {
                result.add(obj);
            }
            nbSent++;
        }

        return(result);
    }

    public static void mergeStreams(StreamHead[] inputs,
				    MDOutputStream output,
				    int toBeSkipped,
				    int nbResults)
        throws EMDException {
        MergeEngine merger = new MergeEngine(inputs);
        Object obj;
        int nbSent = 0;
        int errorCount = 0;

        while ( ((nbResults == -1)
                 || (nbSent < nbResults+toBeSkipped))) {
          	try {
        		obj = merger.getFirst();
        	} catch (EMDException me) {
        		LOG.info (" error merging the streams... (" + (errorCount+1) + ") "
        				+ me.toString());
            	if ((++errorCount) > EMDClient.MAX_ERROR_COUNT) {
            		LOG.warning ("too many failures trying to merge query results ("
            				+ errorCount + ")" + me.toString());
            		throw new EMDException (
            				"too many failures trying to merge query results (" 
            				+ errorCount +")", me);
            	}
        		continue;
        	}
        	
        	if (obj == null)
        		break;
        	
            if (nbSent >= toBeSkipped) {
                output.sendObject(obj);
            }
            nbSent++;
        }
    }
}

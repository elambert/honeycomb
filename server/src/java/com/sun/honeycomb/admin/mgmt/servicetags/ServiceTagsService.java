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


package com.sun.honeycomb.admin.mgmt.servicetags;

import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The service tag service is a service that runs on the master
 * cell.  It sole responsiblity to ensure that the service tag
 * registry file is created when the cell is declared master and
 * to remove the service tag registry when the cell loses it's status
 * as master.   Only 1 attempt is made to create the service tag
 * registry.  If the attempt fails no other attempts are made.
 * The failure is logged.
 * <P>
 * If for some reason there's a double blind thread issue where one thread
 * is trying to do a shutdown and another thread is doing a run
 * this shouldn't cause any thing other than an error.  The service tag
 * registry lives in ramdisk and is recreated each time the honeycomb
 * reboots.   Two consecuite shutdowns aren't an issue since remove
 * simple deletes the file if it's present on the system.
 * <P>
 * 
 */
public class ServiceTagsService implements ManagedService {

    private static final Logger logger =
            Logger.getLogger(ServiceTagsService.class.getName());
    private static final int DELAY = 60000; // a minute
    volatile boolean keepRunning = true;
    volatile boolean isMasterCell = false;

    /**
     * Shutting down service tags
     */
    public void shutdown() {
        keepRunning = false;
        logger.log(Level.INFO, "Stopping service tags service.");
        removeServiceTagRegistry();
    }

    /**
     * Start up service tags here
     */
    public void syncRun() {
        logger.log(Level.INFO, "Starting service tags service.");
        removeServiceTagRegistry();
    }

    /**
     * This function is expected to not return while we remain in
     * running state. All this while loop does is maintain that.
     * <P>
     * This function looks for changes in the cell state.  If a cell
     * becomes master it then attempts to create the service tag
     * registry file.  Note, if the attempt fails no further attempts
     * are made.
     * <P>
     * If a cell loses it's state as master the service tag registry file
     * is deleted.
     */
    public void run() {
        while (keepRunning) {
            try {
                boolean isCellMaster = MultiCellLib.getInstance().isCellMaster();
                if (isCellMaster) {
                    if (isMasterCell) {
                        // I'm already the master, nothing to do
                    } else {
                        // I've become the master cell.
                        // create the service tag registry
                        createServiceTagRegistry();
                        isMasterCell = true;
                    }
                } else {
                    if (isMasterCell) {
                        // I'm no longer the master cell
                        // remove the service tag registry
                        removeServiceTagRegistry();
                        isMasterCell = false;
                    }
                }
                Thread.sleep(DELAY);
            } 
            catch (InterruptedException ignore) {}
            catch (Exception e) {
                logger.log(Level.SEVERE, "Unexpected error encountered", e);
            }
        }
    }
    
    private synchronized void removeServiceTagRegistry() {
        try {
            // We always do a remove to make sure the service tag
            // registry file isn't present.  If may be present
            // in the case of ungracefull termination.
            //
            // Under no circumstances do we want two cells answering
            // the service tag discovery request from the service
            // tag registration client.  
            ServiceTagsRegistry.remove();
        } catch (Exception e) {
            // If we can't create it then service tag registration won't
            // be possible.  Other than that, no harmfull side effects to
            // the system.
            logger.log(Level.WARNING, "Failed to remove service tag registry.", e);
        }
    }
    
    /**
     * Create the service tag registry.   If the service tag data is not
     * present or invalid the service tag registry file will not be
     * generated.  On a single cell system we check to make sure the 
     * service tag generation is not disabled first.
     */
    private synchronized void createServiceTagRegistry() {
        try {
            if (MultiCellLib.getInstance().isCellStandalone()) {
                ClusterProperties props = ClusterProperties.getInstance();
                boolean disabled = props.getPropertyAsBoolean(
                    ConfigPropertyNames.PROP_SERVICE_TAG_SERVICE_DISABLED);
                if (disabled) {
                    // See if service tag generation has been disabled for
                    // the cell.  This option is only valid on a single cell
                    // system.  This option is used to prevent duplicate 
                    // service tag registration entries from being created when
                    // two cells share the same product serial number but are
                    // not joined into a hive.   It's not valid for both
                    // cells to respond to the service tag discover request
                    return;
                }
            }
            ServiceTagsGenerator generator = new ServiceTagsGenerator();
            if (generator.isValid() == false) {
                // The service tags data stored in the system isn't valid.
                // The validation issues need to be resolved before 
                // the service tag registry can be updated.
                // The cause of the validation error has already been logged. 
                logger.log(Level.WARNING, 
                        "Service tag registry could not be created due to validation error.");
                return;
            }
            // Everything is valid update the service tag registry
            ServiceTagsRegistry.update(generator.getServiceTags());
            logger.log(Level.INFO, "Sucessfully created service tag registration file.");
        }
        catch (MgmtException me) {
            // Exception has already been logged
            // Nothing level to do
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, 
                    "Failed to create service tag registry file.", e);
        }
    }

    
    public ManagedService.ProxyObject getProxy() {
        return null;
    }
}

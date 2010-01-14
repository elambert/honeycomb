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



package com.sun.honeycomb.cm;

import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.util.Observer;
import java.io.IOException;

import com.sun.honeycomb.common.SoftwareVersion;
import com.sun.honeycomb.alert.AlertComponent;
import com.sun.honeycomb.alert.AlertException;

/**
 * A managed service is a software component that implements the 
 * cluster management interface. It is the basic entity managed in the cell. 
 * This can be a software service like a NFS server or the representation of 
 * a physical resource like a disk drive (or a FRU generally speaking). 
 * A managed service has a dynamic state, a public api and is transparently 
 * accessible cluster-wide.
 */
public interface ManagedService {



    /** 
     * The <code>shutdown()</code> method is called by the cluster 
     * management to gracefully destroy a managed service including
     * any resources allocated at run-time. The shutdown method needs
     * to finish in a reasonable amount of time (1mn in the current
     * design). Failure to do so may result in an error escalation.
     */
    void shutdown();

    /**
     * The <code>syncRun()</code> method is called by the cluster
     * management in the context of the main service thread, and
     * ensures that upon return, all the initialization for the
     * dependent services are done.
     */
    void syncRun();


    /**
     * The <code>run()</code> method is called by the cluster
     * management in the context of the main service thread,
     * but this does not return until the service is shutdown.
     */
    void run();


    /**
     * The <code>getProxy()</code> method returns the proxy object 
     * of a managed service (see {@link ManagedService#ProxyObject}). 
     * This is an optional operation and a service that does not need
     * to export a specific proxy object can return a null value
     * (in which case a default proxy object is exported).
     * This method is called periodically by the cluster management 
     * to monitor the health of a managed service.
     */
    ManagedService.ProxyObject getProxy();


    /**
     * A managed service can export a specific object in the cell that
     * contains embedded state information accessible remotely. 
     * Every managed service has the following default proxy object 
     * defined by cluster management. This object can be extended 
     * by a managed service to include any state information that the 
     * service wants to export in the cell.
     */
    public class ProxyObject implements java.io.Serializable, AlertComponent {
        /*
         * internal state of a service as defined by cluster management.
         */
        public static final int INIT     = 0x01;
        public static final int READY    = 0x02;
        public static final int RUNNING  = 0x03;
        public static final int DISABLED = 0x04;

        public int state;
        public int load;
        public int resource;
        public int thrCount;
        public transient RemoteInvocation api;

        /**
         * return true if the service is enabled and can process
         * request.
         * A managed service can override this method to
         * propagate its own internal state.
         * By default a service is enabled if it is running
         * from a management point of view.
         */
        public boolean isReady() {
            return (state == RUNNING);
        }

        /**
         * return true if this proxy object is up-to-date
         */
        public boolean isUpToDate() {
            // TODO
            return true;
        }

        /**
         * return the current management state for this service.
         * see (#INIT #READY #RUNNING #DISABLED)
         */
        final public int getState() {
            return state;
        }

        /**
         * return the public remote API for this service.
         */
        final public ManagedService.RemoteInvocation getAPI() {
            return api;
        }

        /**
         * return the current load average of the service.
         * the load average is defined as an average of the number
         * of threads ready to run and waiting in the service,
         * as sampled over the previous 30-seconds interval of
         * service operation. The load average is given as a percentage
         * with 100% meaning the service is over-loaded and 0% the
         * service is idle.
         */
        final public int getLoadAverage() {
            return load;
        }

        /**
         * return the current resources usage of the service.
         * The resource usage is defined as a percentage of the
         * node resource (memory, files, connections) used by the service 
         * with 100% meaning the service uses almost all the resources
         * of the node and 0% nothing.
         */
        final public int getResourceUsage() {
            return resource;
        }

        /**
         * return the number of threads in this service
         */
        final public int getThreadCount() {
            return thrCount;
        }
       
        /**
         * register for automatic update notification when this proxy
         * object changes in the cluster.
         */
        final public void register(Observer observer) {
            // TODO
        }

         /**
         * string for the current service state.
         * This method can be overriden.
         */
        public String toString() {
            String name = getClass().getName();
            if (name != null) {
                String[] parts = name.split("\\.");
                if (parts.length > 1) {
                    name = parts[parts.length - 1];
                }
            }
            if (isReady()) {
                name += " Ready";
            } else if (state == RUNNING) {
                name += " Running";
            } else {
                name += " Disabled";
            }
            return "[" + name +
                ((state == RUNNING)? 
                " - load avg: " + getLoadAverage() + 
                "% resource usage: " + getResourceUsage() +"%]"
                 : "]");
        }

        //
        // Default implementation for AlertComponent
        //
        public int getNbChildren() {
            return 0;
        }
        public AlertProperty getPropertyChild(int index) 
            throws AlertException {
            throw new AlertException("No children...");
        }
        public boolean getPropertyValueBoolean(String property) 
            throws AlertException {
            throw new AlertException("property " + property +
                                     " does not exist");
        }
        public int getPropertyValueInt(String property) 
            throws AlertException {
            throw new AlertException("property " + property +
                                     " does not exist");
        }
        public long getPropertyValueLong(String property) 
            throws AlertException {
            throw new AlertException("property " + property +
                                     " does not exist");
        }
        public float getPropertyValueFloat(String property) 
            throws AlertException {
            throw new AlertException("property " + property +
                                     " does not exist");
        }
        public double getPropertyValueDouble(String property) 
            throws AlertException {
            throw new AlertException("property " + property +
                                     " does not exist");
        }
        public String getPropertyValueString(String property) 
            throws AlertException {
            throw new AlertException("property " + property +
                                     " does not exist");
        }
        public AlertComponent getPropertyValueComponent(String property) 
            throws AlertException {
            throw new AlertException("property " + property +
                                     " does not exist");
        }

        // private
        private static final long serialVersionUID = 
            SoftwareVersion.serializeUID;
    }

    /**
     * A managed service can export a public API accessible remotely.
     * All interfaces implemented in the managed services that extend
     * the <code>RemoteInvocation</code> marker interface is exported 
     * in the cell with the following restrictions:
     * + The interface cannot use the overloading mechanism (two methods
     *   cannot have the same name).
     * + Every method must declare throwing the ManagedServiceException.
     * See example/ExampleManagedService for details.
     */
    public interface RemoteInvocation extends java.io.Serializable {
    }
    
    /**
     * A managed service rmi call can return to the caller an object that
     * extends <code>RemoteChannel</code>. This object has an underlying
     * socket channel that can be used to stream data in and out 
     * between two services.
     * The <code>setChannel()</code> method is called by cluster management
     * to setup the tcp connection endpoints between the client and server.
     */
    public interface RemoteChannel extends java.io.Serializable {
        public void setChannel(ServiceChannel ch) throws IOException;
    }
    
    /**
     * A channel to stream data between two managed services.
     * The underlying socket channel is created and setup by cluster
     * management through the <code>RemoteChannel.setChannel()</code> interface.
     * It is the responsibility of the managed service to close the underlying
     * socket by closing the corresponding <code>ServiceChannel.close()</code>
     */
    public interface ServiceChannel extends ByteChannel {

        public SocketChannel getChannel() throws IOException;
        
        public void flush() throws IOException;
    }
}
